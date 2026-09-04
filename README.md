# study-canvas

Tablet-first Japanese learning workspace built around a large zoomable canvas, stylus handwriting, progressive hints, adaptive practice, and concise AI tutoring.

The product is designed as an **Android-only, local-first app** for the MVP. Learner state, lesson state, ink, attempts, mastery, mistakes, and review scheduling live on-device. A managed AI API is used only where model judgment is useful.

## Product spec

- [`docs/PRD.md`](docs/PRD.md) — product requirements, learning model, UX principles, MVP phases, and definition of done
- [`docs/architecture.md`](docs/architecture.md) — local-first Android architecture and layer boundaries

## Target architecture

```text
Android tablet
Kotlin + Jetpack Compose
  |
  |-- zoomable lesson/practice canvas
  |-- Jetpack Ink stylus capture + rendering
  |-- ML Kit Digital Ink recognition
  |-- deterministic learning domain
  |-- Room -> SQLite
  `-- AiTutorClient
       |
       v
  Managed AI API
  Primary MVP path: Firebase AI Logic -> Gemini
```

### Core stack

- **Kotlin + Jetpack Compose** — Android tablet UI
- **Jetpack Ink** — low-latency stylus capture and vector stroke rendering
- **ML Kit Digital Ink** — Japanese handwriting recognition
- **Room + SQLite** — on-device durable state
- **Coroutines / Flow** — async and reactive application state
- **AiTutorClient** — provider-independent boundary for grading, explanations, exercise generation, and next-action decisions
- **Firebase AI Logic + Gemini** — preferred managed AI path for the MVP

No custom application backend is required for the MVP.

All persistence and AI tutor integration live directly in the Android app. No backend server is required.
## Product principles

### Practice first

The canvas should maximize writable space. Avoid wrapping every exercise, recognition result, hint, and AI response in separate cards.

A learner should be able to complete many exercises on the same canvas with minimal navigation or UI chrome.

### AI chooses pedagogy; deterministic code owns state

AI may:

- semantically grade an answer;
- explain a mistake;
- generate a suitable exercise;
- choose a bounded next teaching action;
- create concise feedback annotations.

Deterministic Android code owns:

- persistence;
- mastery arithmetic;
- hint evidence;
- curriculum prerequisites;
- review scheduling;
- validation;
- local transactions.

The model must not invent or directly overwrite mastery state.

## Core learning loop

`Probe -> Plan -> Teach -> Handwrite -> Grade -> Update mastery -> Adapt -> Review`

The main interaction loop on the canvas is intentionally simpler:

`Learn -> Handwrite -> Check -> Feedback -> Adapt`

## Run Android

Open `android/` in the latest stable Android Studio, sync Gradle, then run on an Android tablet or emulator.

The current Android client targets `compileSdk 36`, Compose BOM `2026.06.01`, Room `2.6.1`, Jetpack Ink `1.0.0`, and ML Kit Digital Ink `19.0.0`.

The Japanese Digital Ink model is downloaded on demand on first use and requires network access for the initial model download. Finger gestures remain available for canvas navigation while the pen authoring layer accepts stylus input.

## Local-first architecture status

The local-first migration is complete. Lesson layouts, vector ink strokes, learner profiles, and exercise attempts are persisted on-device via Room + SQLite. Pedagogical feedback and grading operate via the on-device `AiTutorClient` boundary.
