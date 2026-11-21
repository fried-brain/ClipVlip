# ClipVlip

**An AI video clipping studio with face-aware reframing. No login, no signup, no subscriptions, no watermark.**

ClipVlip takes a long-form video, transcribes it locally with Whisper, identifies compelling moments, and renders short MP4 clips with FFmpeg. An optional OpenAI-compatible endpoint can improve moment selection; without an API key, a local transcript heuristic is used.

## Features in this starter

- Upload video files or import a URL supported by `yt-dlp`
- Local speech transcription with `faster-whisper`
- Optional LLM-assisted moment selection via OpenAI-compatible chat completions
- Transcript-based fallback selection when no LLM key is configured
- 9:16, 16:9 and 1:1 output presets
- Face-aware crop positioning for vertical and square exports, with centre-crop fallback
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

Open http://localhost:8000. The Docker port is bound to localhost by default; do not expose this unauthenticated service directly to the public internet. For the Vercel-hosted interface, deploy the `frontend/` directory and use the **Connect engine** control to enter the separately hosted worker API URL. The processing API needs a persistent container host with FFmpeg, OpenCV and writable storage; Vercel Functions are not used for video rendering.

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

This is ClipVlip v0.3, not a feature-complete parity clone. The caption renderer uses Whisper word timestamps and burns animated ASS karaoke captions into exported MP4 files. Face-aware reframing samples several frames within each selected clip, detects faces with OpenCV, and places the median detected face centre near the output crop centre. If no face is detected, it safely falls back to centred framing. This is a stable clip-level crop, not continuous per-frame camera tracking. A multi-track timeline editor, B-roll, and social publishing remain planned. Background tasks run in-process in this MVP, so keep the service running during renders. The local database and media are stored under `data/`.

URL downloading depends on upstream sites and their terms. Only process videos you own or have permission to use. YouTube and other services may restrict automated downloading; do not bypass access controls.

## Vercel frontend deployment

The Vercel project uses `frontend/` as its root directory and serves the static creator interface. The **Connect engine** control stores the URL of your separately hosted ClipVlip worker API in browser local storage. Configure the worker with HTTPS and CORS for your Vercel domain before connecting it. Add authentication and upload limits before opening the worker to public traffic.

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

## Supabase backend foundation

ClipVlip uses a dedicated Supabase project for persistent job metadata and private media storage.

- Database tables: `video_jobs`, `video_assets`, and `video_clips`
- Private Storage buckets: `clipvlip-sources` (4 GiB object limit) and `clipvlip-outputs` (1 GiB object limit)
- Row Level Security is enabled on all application tables. No anon/authenticated policies are created; access is reserved for the trusted server-side worker using the Supabase secret key.
- The worker must never expose `SUPABASE_SECRET_KEY` to the browser or commit it to source control.
- Supabase Edge Functions are not used to run FFmpeg/Whisper jobs. Video rendering requires a separate persistent compute worker.

Apply database changes with the SQL migration in `supabase/migrations/`. Configure `SUPABASE_URL` and `SUPABASE_SECRET_KEY` in the worker's private environment before enabling Supabase persistence.


## Free worker deployment (Render)

A free-tier deployment blueprint is included in `render.yaml`. It creates a Docker web service from this repository, uses the lightweight Whisper `tiny` model, and configures the Supabase credentials as private environment variables.

1. Sign in to [Render](https://dashboard.render.com/).
2. Choose **New → Blueprint** and connect the public `fried-brain/ClipVlip` GitHub repository.
3. Select the `main` branch and review the `render.yaml` blueprint. Confirm the service plan is **Free** before creating it.
4. In the service's Environment settings, add:
   - `SUPABASE_URL`: the project URL from Supabase → Project Settings → API.
   - `SUPABASE_SECRET_KEY`: the server-side secret key from Supabase → Project Settings → API Keys. Keep it private; never use a publishable key here.
5. Wait for the first deploy to finish. Open `https://YOUR-SERVICE.onrender.com/api/health`; expect JSON with `status: ok`.
6. Open the ClipVlip website, choose **Connect engine**, and enter `https://YOUR-SERVICE.onrender.com`.

### Free-tier caveats

Render's free web services have 512 MB RAM, spin down after 15 minutes without inbound traffic, may restart, and have ephemeral local filesystems. The blueprint uses Whisper `tiny` and Supabase for persistent source/output storage, but video processing will be slow and large uploads or long renders may fail under the free memory/CPU limits. Keep the project page open while processing so it continues polling the worker. A free Render web service is a prototype/testing option, not reliable production compute. Check the current [Render free service limitations](https://render.com/docs/free) before deploying. The free plan is not a dedicated background-worker plan.
