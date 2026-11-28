# ClipVlip Android

Native Android companion for the ClipVlip creator studio.

## First milestone

- Kotlin and Jetpack Compose application shell.
- Separate downloadable on-device language models.
- SHA-256 verification before a model is accepted as installed.
- LiteRT-LM runtime dependency and background-thread model loader.
- Model binaries stay in app-private internal storage and are never uploaded to Supabase.

## Model catalogue

| Model | App task | Artifact | Approx. download |
|---|---|---|---:|
| Qwen 2.5 Instruct 1.5B | Transcript refinement and captions | LiteRT-LM Q8, 4096 KV | 1.60 GB |
| DeepSeek-R1-Distill-Qwen 1.5B | Highlight reasoning and hook generation | LiteRT-LM Q8, 4096 KV | 1.83 GB |

The catalogue pins Hugging Face artifact URLs, exact byte lengths and LFS SHA-256 digests. Downloads are streamed to a temporary file and only moved into the installed model directory after both size and checksum match.

The 0.5B Qwen option and Whisper Tiny INT8/Sherpa-ONNX speech pack are not shown as downloadable yet. Their final Android artifact variants and checksums must be pinned before enabling installation.

## Build

Open this android directory in Android Studio with JDK 17 and Android SDK 36 installed. Gradle sync downloads the declared dependencies.

The first build uses LiteRT-LM 0.18.0 or newer. Media3, Sherpa-ONNX, Silero VAD and yt-dlp integration are separate follow-up milestones so the model manager remains independently testable.

## Architecture boundaries

- On device: source media selection, speech processing, local LLM inference, clip editing and export.
- Supabase: account identity and optional job/asset metadata. Never store model binaries or expose a Supabase secret key in the APK.
- Vercel: web dashboard and preview deployments, not Android video rendering.
- TinyFish: development research and explicitly requested browser automation; not a dependency in the mobile runtime.

## Model sources and licenses

- Qwen conversion: https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct (Apache-2.0)
- DeepSeek conversion: https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B (MIT)
- LiteRT-LM Android guide: https://ai.google.dev/edge/litert-lm/android
