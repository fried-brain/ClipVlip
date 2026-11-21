# ClipVlip

**A local-first AI video clipping studio. No login, no signup, no subscriptions, no watermark.**

ClipVlip takes a long-form video, transcribes it locally with Whisper, identifies compelling moments, and renders short MP4 clips with FFmpeg. An optional OpenAI-compatible endpoint can improve moment selection; without an API key, a local transcript heuristic is used.

## Features in this starter

- Upload video files or import a URL supported by `yt-dlp`
- Local speech transcription with `faster-whisper`
- Optional LLM-assisted moment selection via OpenAI-compatible chat completions
- Transcript-based fallback selection when no LLM key is configured
- 9:16, 16:9 and 1:1 output presets
- Configurable clip count and duration
- MP4 rendering with FFmpeg
- Word-synchronised animated karaoke captions (ASS subtitles)
- Four caption presets: Bold Creator, Clean, Neon and Minimal
- Caption geometry adapts to vertical, square and landscape exports
- Local task history and downloadable outputs
- No authentication, accounts, billing, usage metering or watermark

## Requirements

- Docker + Docker Compose (recommended), or Python 3.11+
- FFmpeg and FFprobe
- 4 GB+ RAM recommended for Whisper `small`; more for larger models
- Internet access on first run to download the Whisper model and URL sources

## Run with Docker

```bash
cp .env.example .env
docker compose up --build
```

Open http://localhost:8000. The Docker port is bound to localhost by default; do not expose this unauthenticated local-first service directly to the public internet.

## Run without Docker

```bash
cp .env.example .env
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\\Scripts\\activate
pip install -r backend/requirements.txt
# Install FFmpeg separately, then:
uvicorn backend.app:app --host 127.0.0.1 --port 8000 --reload
```

Open http://localhost:8000. Do not expose this unauthenticated local-first service directly to the public internet.

## Optional AI selection

Set `OPENAI_API_KEY`, `OPENAI_BASE_URL`, and `CLIPVLIP_LLM_MODEL` in `.env`. Compatible providers include OpenAI-compatible hosted APIs and local gateways. The provider may charge separately; ClipVlip itself has no subscription or usage fee. For fully local operation, leave the key empty and use the transcript-based selector.

## Notes and current limitations

This is ClipVlip v0.2, not a feature-complete parity clone. The caption renderer uses Whisper word timestamps and burns animated ASS karaoke captions into exported MP4 files. Face tracking, a multi-track timeline editor, B-roll, social publishing, and advanced scene-aware reframing remain planned. Face-aware crop detection is not yet implemented; current vertical output uses a centered crop. Background tasks run in-process in this MVP, so keep the service running during renders. The local database and media are stored under `data/`.

URL downloading depends on upstream sites and their terms. Only process videos you own or have permission to use. YouTube and other services may restrict automated downloading; do not bypass access controls.

## Development checks

```bash
pip install -r backend/requirements-dev.txt
python -m pytest -q
node --check frontend/app.js
```

GitHub Actions runs the same checks on pushes to `main` and pull requests.

## API

- `GET /api/health`
- `POST /api/tasks` (multipart form: `file` or `source_url`, `clip_count`, `min_duration`, `max_duration`, `aspect_ratio`, `captions`, `caption_style`)
- `GET /api/tasks`
- `GET /api/tasks/{task_id}`
- `GET /api/clips/{task_id}/{filename}`

## License

ClipVlip's original starter implementation is provided under the MIT License. It is an independent implementation and does not include SupoClip source code. Review third-party dependency licenses separately.