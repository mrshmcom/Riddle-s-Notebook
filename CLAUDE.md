# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Riddle's Notebook is a native Android app (Kotlin + Jetpack Compose) that recreates Tom Riddle's magical diary from Harry Potter. Users write with a stylus on parchment-like pages; the ink fades automatically and the diary responds with AI-generated text in character as Tom Riddle's sentient diary.

## Build & Development

```bash
# One-time setup (developer only — embeds a default AI API key into the APK)
./setup.sh

# Build the APK in Docker (installs Android SDK, Gradle, dependencies, produces output/app-debug.apk)
./build.sh

# Open a shell inside the Docker build container for debugging
./build.sh shell

# Clean all build artifacts and Docker volumes
./build.sh clean
```

The Docker build uses `Dockerfile` (Ubuntu 22.04 + JDK 17 + Android SDK 34 + Gradle 8.7). `docker-compose.yml` provides a persistent shell service with cached Gradle dependencies.

There is no local dev workflow outside Docker — Android SDK is not expected on the host.

## Architecture

### Core Flow (write → vanish → recognize → AI → respond)

```
Stylus Input          ML Kit                Multi-Provider AI
┌──────────┐         ┌────────────────┐    ┌───────────────────┐
│DrawingCanvas│──→  │HandwritingRecognizer│──→│     DiaryAI       │
│ (Compose)  │ stroke │ (Digital Ink Recog.)│   │ Groq/Gemini/      │
│ tracks pen │ data   │ en-US model ~20MB  │   │ Claude/OpenAI     │
│ strokes    │         └────────────────┘    └───────────────────┘
└──────────┘                                         │
      ↑                                              ↓
      │ 1.5s pause detects                      DiaryScreen
      │ writing completion                      (shows typewriter
      │ and triggers flow                       animation of reply)
```

### State Machine (`DiaryViewModel`)

```
Idle → Writing → Fading → Recognizing → Thinking → Responding → Idle
                    ↑                                    │
                    └── Error (any phase) ←──────────────┘
```

Each state is rendered differently in `DiaryScreen`: ink opacity changes during Fading, a spinner shows during Thinking, a character-by-character typewriter effect plays during Responding.

### Key Components

**`DrawingCanvas.kt`** — Compose `Canvas` with `pointerInput` + `detectDragGestures`. Captures `TimedPoint` lists (x, y, timestamp) per stroke. Renders ink paths with a glow layer for a magical aesthetic. Observes `canvasClearToken` from ViewModel state to clear paths after a response cycle completes. Does NOT clear strokes on error — they fade back in so the user can continue editing.

**`HandwritingRecognizer.kt`** — Wraps ML Kit `DigitalInkRecognition`. Downloads ~20MB en-US model via `RemoteModelManager` on first launch. Converts `StrokeData` → ML Kit `Ink` → recognized text. Idempotent `ensureModelReady()` called from both ViewModel init and pre-recognition check.

**`DiaryAI.kt`** — Multi-provider AI service using OkHttp + org.json. Four providers defined in the `AIProvider` enum:

| Provider | API Format | Auth | Cost |
|----------|-----------|------|------|
| Groq | OpenAI-compatible (`/chat/completions`) | Bearer token | Free tier |
| Gemini | Google AI (`generateContent?key=`) | Query param key | Free tier |
| Claude | Anthropic Messages API | `x-api-key` header | Paid |
| OpenAI | OpenAI (`/chat/completions`) | Bearer token | Paid |

Groq and OpenAI share the `buildOpenAIFormatMessages()` helper (system prompt + history + current message). Gemini uses a different structure (`contents` array + `systemInstruction`). Claude uses its own Messages format with a top-level `system` field.

The system prompt (`DIARY_SYSTEM_PROMPT`) instructs the model to embody Tom Riddle's Diary — charming, Victorian prose, subtly manipulative, never breaking character.

**`DiaryViewModel.kt`** — `AndroidViewModel` that owns all state via `MutableStateFlow<DiaryUiState>`. Key behaviors:
- On init, checks `BuildConfig.DEFAULT_GROQ_API_KEY` (compiled from `app/default_api_key.properties`). If present, auto-configures Groq without user intervention.
- `onWritingPaused()` is the main orchestrator: captures strokes, fades ink visually, runs recognition, calls AI, animates the response, then clears the canvas via `canvasClearToken + 1`.
- `fadeInk()` only animates alpha to zero — it does NOT clear strokes. Strokes are cleared only after successful recognition (`strokes.clear()` after `recognizeText()` returns). On error, `showInkAgain()` restores alpha so the user's writing reappears.
- `animateTypewriter()` reveals the AI response character-by-character with variable delays (longer pauses at punctuation).
- Settings persistence: provider, model, and API key are saved per-provider in `SharedPreferences` (keyed as `apikey_{providerId}`). The compiled default only applies on first launch if no user preference exists.

**`DiaryScreen.kt`** — The main Compose UI. Three layers stacked in a `Box`:
1. `ParchmentBackground` — drawn with Canvas (vignette edges, subtle paper lines, random stains)
2. `DrawingCanvas` — captures pen input
3. Response text — appears with `AnimatedVisibility` during Responding state

The Settings dialog has two modes:
- **Simple mode** (default): Shows "AI Ready — Groq Cloud" or "AI Setup Required" with a guided flow for free API key acquisition
- **Advanced mode** (collapsed): Provider selection, model picker via `ExposedDropdownMenuBox`, API key fields for paid providers

**`Theme.kt`** — Dark color scheme (`DiaryCover` #1A0E0A) with green ink (`InkGreen` #1B3A2D), gold accents (`GoldAccent` #C9A96E), and parchment tones. Typography uses `FontFamily.Serif` for UI text and `FontFamily.Cursive` for AI responses to simulate handwriting.

### Configuration

- **`app/default_api_key.properties`** — Contains the developer's Groq API key. Gitignored. Read by `build.gradle.kts` and compiled into `BuildConfig.DEFAULT_GROQ_API_KEY`. The `setup.sh` script creates this file interactively.
- **`gradle.properties`** — Standard Android Gradle config (Xmx, AndroidX, nonTransitiveRClass)
- **`gradle/wrapper/gradle-wrapper.properties`** — Targets Gradle 8.7

### Key Dependencies

- **Compose BOM 2024.02.00** with Kotlin 1.9.22, Compose Compiler 1.5.10
- **ML Kit Digital Ink Recognition 18.1.0** — stroke-based handwriting recognition (not bitmap OCR)
- **OkHttp 4.12.0** — HTTP client for all AI API calls
- **minSdk 26** (Android 8.0), **targetSdk 34**

### Platform Constraints

- ML Kit handwriting model downloads via `RemoteModelManager` — requires Google Play Services on first launch for model download.
- The 1.5-second pause detection for writing completion is implemented via `LaunchedEffect` in `DrawingCanvas.kt` checking `lastStrokeTime` with a coroutine delay, not a timer.
- Pressure-sensitive stylus input is supported through `detectDragGestures` which captures all pointer events. The `TimedPoint` data class stores position and timestamp but not pressure — ML Kit's Digital Ink Recognition doesn't require it.
