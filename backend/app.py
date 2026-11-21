from __future__ import annotations

import json
import os
import sqlite3
import subprocess
import threading
import time
import uuid
from pathlib import Path
from typing import Any

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from backend.supabase_store import SupabaseStore

ROOT = Path(__file__).resolve().parent.parent
load_dotenv(ROOT / ".env")
DATA = Path(os.getenv("CLIPVLIP_DATA", ROOT / "data"))
UPLOADS, OUTPUTS = DATA / "uploads", DATA / "outputs"
for folder in (UPLOADS, OUTPUTS):
    folder.mkdir(parents=True, exist_ok=True)
DB = DATA / "clipvlip.db"
STORE = SupabaseStore()

app = FastAPI(title="ClipVlip API", version="0.3.0", description="AI video clipping studio with face-aware reframing")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


def db():
    con = sqlite3.connect(DB, timeout=30)
    con.row_factory = sqlite3.Row
    con.execute("""CREATE TABLE IF NOT EXISTS tasks (
        id TEXT PRIMARY KEY, title TEXT, source TEXT, status TEXT, progress INTEGER,
        message TEXT, created REAL, settings TEXT, clips TEXT, error TEXT
    )""")
    con.commit()
    return con


def update(task_id: str, **values):
    if STORE.enabled:
        cloud_values = {k: v for k, v in values.items() if k in {"status", "progress", "message", "error"}}
        if values.get("status") == "processing" and values.get("progress") == 5:
            cloud_values["started_at"] = "now()"
        if values.get("status") in {"completed", "failed"}:
            cloud_values["completed_at"] = "now()"
        STORE.update_job(task_id, cloud_values)
        if "clips" in values:
            clips = json.loads(values["clips"]) if isinstance(values["clips"], str) else values["clips"]
            STORE.replace_clips(task_id, clips)
        return
    con = db()
    assignments = ", ".join(f"{key}=?" for key in values)
    con.execute(f"UPDATE tasks SET {assignments} WHERE id=?", (*values.values(), task_id))
    con.commit()
    con.close()


def get_task(task_id: str):
    if STORE.enabled:
        row = STORE.get_job(task_id)
        if row is None:
            return None
        source = ("file:" + str(UPLOADS / task_id / "source.mp4")) if row["source_type"] == "upload" else (row.get("source_url") or "")
        return {"id": row["id"], "title": row["title"], "source": source,
                "source_path": row.get("source_path"), "status": row["status"],
                "progress": row["progress"], "message": row["message"],
                "created": row.get("created_at"), "settings": row.get("settings") or {},
                "clips": STORE.get_clips(task_id), "error": row.get("error")}
    con = db()
    row = con.execute("SELECT * FROM tasks WHERE id=?", (task_id,)).fetchone()
    con.close()
    if row is None:
        return None
    result = dict(row)
    result["settings"] = json.loads(result["settings"] or "{}")
    result["clips"] = json.loads(result["clips"] or "[]")
    return result


def run(command: list[str], timeout: int = 3600):
    result = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError((result.stderr or result.stdout)[-2500:])
    return result.stdout


def probe_duration(video: Path) -> float:
    output = run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                  "-of", "default=noprint_wrappers=1:nokey=1", str(video)])
    return max(0.0, float(output.strip()))


def download_source(url: str, task_id: str) -> Path:
    folder = UPLOADS / task_id
    folder.mkdir(parents=True, exist_ok=True)
    run(["yt-dlp", "--no-playlist", "-f", "bv*+ba/b", "--merge-output-format", "mp4",
         "-o", str(folder / "source.%(ext)s"), url], timeout=7200)
    files = [item for item in folder.iterdir() if item.is_file()]
    if not files:
        raise RuntimeError("The source could not be downloaded.")
    return max(files, key=lambda item: item.stat().st_size)


def transcribe(video: Path) -> list[dict[str, Any]]:
    from faster_whisper import WhisperModel
    model = WhisperModel(os.getenv("WHISPER_MODEL", "small"),
                         device=os.getenv("WHISPER_DEVICE", "cpu"),
                         compute_type=os.getenv("WHISPER_COMPUTE_TYPE", "int8"))
    segments, _ = model.transcribe(str(video), word_timestamps=True, vad_filter=True)
    result = []
    for segment in segments:
        words = [{"start": float(word.start), "end": float(word.end), "word": word.word}
                 for word in (segment.words or [])]
        result.append({"start": float(segment.start), "end": float(segment.end),
                       "text": segment.text.strip(), "words": words})
    return result


def choose_segments(transcript: list[dict], duration: float, count: int,
                    min_len: int, max_len: int) -> list[dict]:
    api_key = os.getenv("OPENAI_API_KEY")
    base = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    model = os.getenv("CLIPVLIP_LLM_MODEL", "gpt-4o-mini")
    compact = "\n".join(f"[{int(item['start'])}-{int(item['end'])}] {item['text']}" for item in transcript)
    if api_key and compact:
        prompt = (f"Select up to {count} compelling self-contained clips, each {min_len}-{max_len} seconds. "
                  "Use transcript timestamps, avoid overlap, prioritize hooks and useful insights. "
                  'Return only JSON: {"clips":[{"start":0,"end":35,"title":"Hook","reason":"Why"}]}. '
                  f"Video duration: {duration:.1f}s. Transcript:\n{compact[:90000]}")
        try:
            response = httpx.post(base + "/chat/completions",
                headers={"Authorization": f"Bearer {api_key}"},
                json={"model": model, "temperature": 0.2,
                      "messages": [{"role": "user", "content": prompt}]}, timeout=180)
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            parsed = json.loads(content[content.find("{"):content.rfind("}") + 1])
            selected = []
            for item in parsed.get("clips", []):
                start, end = max(0, float(item["start"])), min(duration, float(item["end"]))
                if min_len <= end - start <= max_len:
                    selected.append({"start": start, "end": end,
                        "title": str(item.get("title", "A great moment"))[:80],
                        "reason": str(item.get("reason", "AI-selected moment"))[:240]})
            if selected:
                return sorted(selected, key=lambda item: item["start"])[:count]
        except Exception:
            pass

    windows = []
    keywords = ("why", "how", "secret", "never", "imagine", "because", "mistake",
                "important", "actually", "believe", "truth", "problem", "change")
    for segment in transcript:
        text = segment["text"]
        score = len(text.split()) * 0.15 + sum(text.lower().count(word) for word in keywords) * 1.8
        if "?" in text:
            score += 2
        windows.append((float(segment["start"]), float(segment["end"]), score, text))
    candidates = []
    for index, (start, _, _, _) in enumerate(windows):
        for end_index in range(index, len(windows)):
            end = windows[end_index][1]
            if end - start > max_len:
                break
            if end - start >= min_len:
                group = windows[index:end_index + 1]
                score = sum(item[2] for item in group) / (max(1, len(group)) ** 0.45)
                candidates.append((score, start, end, " ".join(item[3] for item in group)))
    candidates.sort(reverse=True)
    chosen = []
    for score, start, end, text in candidates:
        if all(end <= item["start"] or start >= item["end"] for item in chosen):
            title = " ".join(text.split()[:9]).strip(" .,!?:;")
            chosen.append({"start": start, "end": end, "title": title[:64] or "A compelling moment",
                           "reason": "Selected using transcript structure and language cues",
                           "score": round(score, 2)})
            if len(chosen) >= count:
                break
    return sorted(chosen, key=lambda item: item["start"])


def _ass_timestamp(seconds: float) -> str:
    value = max(0.0, float(seconds))
    hours, remainder = divmod(value, 3600)
    minutes, remainder = divmod(remainder, 60)
    whole = int(remainder)
    centiseconds = int(round((remainder - whole) * 100))
    if centiseconds == 100:
        whole += 1
        centiseconds = 0
    if whole == 60:
        minutes += 1
        whole = 0
    if minutes == 60:
        hours += 1
        minutes = 0
    return f"{int(hours)}:{int(minutes):02d}:{whole:02d}.{centiseconds:02d}"


def _ass_escape(value: str) -> str:
    return str(value).replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("\n", " ").replace("\r", " ")


def build_ass_subtitles(transcript: list[dict[str, Any]], clip_start: float, clip_end: float,
                        style: str = "bold", ratio: str = "9:16") -> str:
    styles = {
        "bold": (68, "&H00FFFFFF", "&H0000D7FF", "1", "5", "3"),
        "clean": (58, "&H00FFFFFF", "&H0000D7FF", "0", "3", "1"),
        "neon": (64, "&H00FFFFFF", "&H00FF8AFC", "1", "5", "2"),
        "minimal": (48, "&H00FFFFFF", "&H0000D7FF", "0", "2", "0"),
    }
    size, primary, highlight, bold, outline, shadow = styles.get(style, styles["bold"])
    width, height = (1920, 1080) if ratio == "16:9" else ((1080, 1080) if ratio == "1:1" else (1080, 1920))
    margin = 72 if ratio == "16:9" else 150
    header = f"""[Script Info]
ScriptType: v4.00+
WrapStyle: 2
ScaledBorderAndShadow: yes
YCbCr Matrix: TV.709
PlayResX: {width}
PlayResY: {height}

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,{size},{primary},{highlight},&H00101018,&H80000000,{bold},0,0,0,100,100,0,0,1,{outline},{shadow},2,70,70,{margin},1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
    events = []
    for segment in transcript:
        selected = []
        for word in segment.get("words") or []:
            try:
                start = max(float(word["start"]), clip_start)
                end = min(float(word["end"]), clip_end)
                value = str(word.get("word", "")).strip()
            except (KeyError, TypeError, ValueError):
                continue
            if end > start and value:
                selected.append({"start": start, "end": end, "word": value})
        for offset in range(0, len(selected), 5):
            group = selected[offset:offset + 5]
            phrase = []
            for index, word in enumerate(group):
                next_start = group[index + 1]["start"] if index + 1 < len(group) else word["end"]
                end = min(clip_end, max(word["end"], next_start))
                duration_cs = max(1, int(round((end - word["start"]) * 100)))
                phrase.append(f"{{\\kf{duration_cs}}}{_ass_escape(word['word'])}")
            start = max(0.0, group[0]["start"] - clip_start)
            end = max(start + 0.05, min(clip_end, group[-1]["end"]) - clip_start)
            events.append(f"Dialogue: 0,{_ass_timestamp(start)},{_ass_timestamp(end)},Default,,0,0,0,,{' '.join(phrase)}")
    return header + "\n".join(events) + ("\n" if events else "")



def detect_focus_x(video: Path, start: float, end: float) -> float:
    """Estimate a stable horizontal subject centre from sampled frames."""
    try:
        import cv2
        cascade = cv2.CascadeClassifier(str(Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml"))
        if cascade.empty():
            return 0.5
        capture = cv2.VideoCapture(str(video))
        if not capture.isOpened():
            return 0.5
        centres = []
        try:
            span = max(0.0, end - start)
            for fraction in (0.15, 0.3, 0.5, 0.7, 0.85):
                capture.set(cv2.CAP_PROP_POS_MSEC, (start + span * fraction) * 1000)
                ok, frame = capture.read()
                if not ok:
                    continue
                height, width = frame.shape[:2]
                gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                faces = cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5,
                                                  minSize=(max(24, width // 30), max(24, height // 30)))
                if len(faces):
                    x, y, w, h = max(faces, key=lambda box: box[2] * box[3])
                    centres.append((x + w / 2) / max(1, width))
        finally:
            capture.release()
        if not centres:
            return 0.5
        centres.sort()
        return max(0.15, min(0.85, centres[len(centres) // 2]))
    except Exception:
        return 0.5


def crop_filter(ratio: str, focus_x: float = 0.5) -> str:
    """Build output geometry with the detected face near horizontal centre."""
    focus_x = max(0.15, min(0.85, float(focus_x)))
    if ratio == "16:9":
        return "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2"
    if ratio == "1:1":
        return f"scale=1080:1080:force_original_aspect_ratio=increase,crop=1080:1080:x='max(0,min(iw-ow,iw*{focus_x:.4f}-ow/2))':y=(ih-oh)/2"
    return f"scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920:x='max(0,min(iw-ow,iw*{focus_x:.4f}-ow/2))':y=(ih-oh)/2"


def render_clip(video: Path, clip: dict, output: Path, ratio: str, captions: bool,
                transcript: list[dict[str, Any]] | None = None, caption_style: str = "bold", focus_x: float = 0.5):
    start, end = float(clip["start"]), float(clip["end"])
    video_filter = crop_filter(ratio, focus_x)
    subtitle_file = output.with_suffix(".ass")
    if captions and transcript:
        subtitle_file.write_text(build_ass_subtitles(transcript, start, end, caption_style, ratio), encoding="utf-8")
        escaped = str(subtitle_file.resolve()).replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")
        video_filter += f",subtitles='{escaped}'"
    try:
        run(["ffmpeg", "-y", "-ss", str(start), "-i", str(video), "-t", str(end - start),
             "-vf", video_filter, "-c:v", "libx264", "-preset", "veryfast", "-crf", "21",
             "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", str(output)], timeout=7200)
    finally:
        subtitle_file.unlink(missing_ok=True)


def process(task_id: str, settings: dict):
    try:
        task = get_task(task_id)
        update(task_id, status="processing", progress=5, message="Preparing source video")
        source = task["source"]
        video = Path(source[5:]) if source.startswith("file:") else download_source(source, task_id)
        if STORE.enabled and task.get("source_path") and not video.exists():
            video.parent.mkdir(parents=True, exist_ok=True)
            STORE.download(STORE.source_bucket, task["source_path"], str(video))
        if not video.exists():
            raise RuntimeError("Source video file is missing.")
        duration = probe_duration(video)
        update(task_id, progress=18, message="Transcribing audio locally")
        transcript = transcribe(video)
        if not transcript:
            raise RuntimeError("No speech was detected. Try a video with clear spoken audio.")
        update(task_id, progress=52, message="Finding the strongest moments")
        clips = choose_segments(transcript, duration, int(settings.get("clip_count", 5)),
                                int(settings.get("min_duration", 20)), int(settings.get("max_duration", 60)))
        if not clips:
            raise RuntimeError("No suitable clips found. Try adjusting clip duration settings.")
        results = []
        folder = OUTPUTS / task_id
        folder.mkdir(parents=True, exist_ok=True)
        for index, clip in enumerate(clips, 1):
            update(task_id, progress=55 + int(40 * index / len(clips)),
                   message=f"Rendering clip {index} of {len(clips)}")
            filename = f"clip-{index:02d}.mp4"
            focus_x = detect_focus_x(video, float(clip["start"]), float(clip["end"])) if settings.get("smart_reframe", True) else 0.5
            render_clip(video, clip, folder / filename, settings.get("aspect_ratio", "9:16"),
                        bool(settings.get("captions", False)), transcript, settings.get("caption_style", "bold"), focus_x)
            storage_path = f"{task_id}/{filename}"
            if STORE.enabled:
                STORE.upload(STORE.output_bucket, storage_path, str(folder / filename), "video/mp4")
                STORE.save_asset(task_id, "clip", storage_path, filename, "video/mp4",
                                 (folder / filename).stat().st_size)
            results.append({**clip, "id": index, "filename": filename,
                            "storage_path": storage_path,
                            "url": f"/api/clips/{task_id}/{filename}"})
        update(task_id, status="completed", progress=100, message=f"{len(results)} clips are ready",
               clips=json.dumps(results))
    except Exception as error:
        update(task_id, status="failed", message="Processing failed", error=str(error)[-2000:])


@app.get("/api/health")
def health():
    return {"status": "ok", "name": "ClipVlip", "auth": "disabled", "billing": "disabled"}


@app.post("/api/tasks")
async def create_task(source_url: str = Form(""), file: UploadFile | None = File(None),
                      clip_count: int = Form(5), min_duration: int = Form(20),
                      max_duration: int = Form(60), aspect_ratio: str = Form("9:16"),
                      captions: bool = Form(False), caption_style: str = Form("bold")):
    if not source_url and not file:
        raise HTTPException(400, "Add a video URL or upload a video file.")
    if min_duration < 10 or max_duration < min_duration or max_duration > 180:
        raise HTTPException(400, "Choose a valid clip duration between 10 and 180 seconds.")
    task_id = uuid.uuid4().hex
    source = source_url.strip()
    title = source
    if file:
        extension = Path(file.filename or "video.mp4").suffix.lower() or ".mp4"
        if extension not in {".mp4", ".mov", ".mkv", ".webm", ".m4v", ".avi"}:
            raise HTTPException(400, "Unsupported video format.")
        destination = UPLOADS / f"{task_id}{extension}"
        size = 0
        with destination.open("wb") as output:
            while chunk := await file.read(1024 * 1024):
                size += len(chunk)
                if size > 4 * 1024 * 1024 * 1024:
                    destination.unlink(missing_ok=True)
                    raise HTTPException(413, "Maximum upload size is 4 GB.")
                output.write(chunk)
        source = "file:" + str(destination)
        title = file.filename or "Uploaded video"
    settings = {
        "clip_count": max(1, min(12, clip_count)),
        "min_duration": min_duration,
        "max_duration": max_duration,
        "aspect_ratio": aspect_ratio if aspect_ratio in ("9:16", "16:9", "1:1") else "9:16",
        "captions": captions,
        "smart_reframe": True,
        "caption_style": caption_style if caption_style in ("bold", "clean", "neon", "minimal") else "bold",
    }
    if STORE.enabled:
        source_path = None
        if file:
            source_path = f"{task_id}/source{destination.suffix.lower()}"
            STORE.upload(STORE.source_bucket, source_path, str(destination),
                         file.content_type or "application/octet-stream")
            STORE.save_asset(task_id, "source", source_path, destination.name,
                             file.content_type or "application/octet-stream", destination.stat().st_size)
        STORE.create_job({
            "id": task_id, "title": title, "source_type": "upload" if file else "url",
            "source_path": source_path, "source_url": None if file else source,
            "status": "queued", "progress": 0, "message": "Waiting to start",
            "settings": settings,
        })
    else:
        con = db()
        con.execute("INSERT INTO tasks VALUES (?,?,?,?,?,?,?,?,?,?)",
                    (task_id, title, source, "queued", 0, "Waiting to start",
                     time.time(), json.dumps(settings), "[]", None))
        con.commit()
        con.close()
    threading.Thread(target=process, args=(task_id, settings), daemon=True).start()
    return {"id": task_id, "status": "queued"}


@app.get("/api/tasks")
def tasks():
    if STORE.enabled:
        return [{**row, "clips": STORE.get_clips(row["id"])} for row in STORE.list_jobs()]
    con = db()
    rows = con.execute("SELECT * FROM tasks ORDER BY created DESC LIMIT 50").fetchall()
    con.close()
    result = []
    for row in rows:
        item = dict(row)
        item["clips"] = json.loads(item["clips"] or "[]")
        result.append(item)
    return result


@app.get("/api/tasks/{task_id}")
def task(task_id: str):
    result = get_task(task_id)
    if result is None:
        raise HTTPException(404, "Task not found")
    return result


@app.get("/api/clips/{task_id}/{filename}")
def clip_file(task_id: str, filename: str):
    if "/" in filename or ".." in filename:
        raise HTTPException(400, "Invalid filename")
    path = OUTPUTS / task_id / filename
    if not path.is_file() and STORE.enabled:
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            STORE.download(STORE.output_bucket, f"{task_id}/{filename}", str(path))
        except Exception:
            path.unlink(missing_ok=True)
    if not path.is_file():
        raise HTTPException(404, "Clip not found")
    return FileResponse(path, media_type="video/mp4", filename=filename)


@app.get("/api/source/{task_id}")
def source_file(task_id: str):
    task_data = get_task(task_id)
    if not task_data or not task_data["source"].startswith("file:"):
        raise HTTPException(404, "Source not found")
    path = Path(task_data["source"][5:])
    if not path.is_file() and STORE.enabled and task_data.get("source_path"):
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            STORE.download(STORE.source_bucket, task_data["source_path"], str(path))
        except Exception:
            path.unlink(missing_ok=True)
    if not path.is_file():
        raise HTTPException(404, "Source not found")
    return FileResponse(path)


app.mount("/", StaticFiles(directory=ROOT / "frontend", html=True), name="frontend")
