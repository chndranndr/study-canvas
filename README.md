# study-canvas

Tablet-first Japanese learning workspace built around a large zoomable canvas, stylus handwriting, progressive hints, deterministic local checking, and optional generation-only AI enrichment.

> Curated JSON owns curriculum facts and existing reviewed lesson content. AI only adds optional grammar enrichment and generates sentence-production practice. Recognition and grading are deterministic/local.

The product is designed as an **Android-only, local-first app** for the MVP. Learner state, lesson state, ink, attempts, and review scheduling live on-device. The bundled curriculum JSON file is the curriculum source of truth.
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
  |-- ML Kit Digital Ink recognition (local)
  |-- DeterministicAnswerChecker (local NFKC & exact matching)
  |-- Room -> SQLite (persists ink, attempts, generated snapshots)
  `-- GrammarLessonGenerator (optional generation-only boundary)
       |
       v
  Managed AI API (Gemini) — optional supplemental generation only
```

### Core stack

- **Kotlin + Jetpack Compose** — Android tablet UI
- **Jetpack Ink** — low-latency stylus capture and vector stroke rendering
- **ML Kit Digital Ink** — Japanese handwriting recognition
- **Room + SQLite** — on-device durable state
- **Coroutines / Flow** — async and reactive application state
- **GrammarLessonGenerator** — optional generation-only boundary for supplemental enrichment notes and 10 sentence-production exercises
- **DeterministicAnswerChecker** — local, deterministic answer evaluation with zero model calls

No custom application backend is required for the MVP.

All persistence and AI tutor integration live directly in the Android app. No backend server is required.
## Product principles

### Practice first

The canvas should maximize writable space. Avoid wrapping every exercise, recognition result, hint, and AI response in separate cards.

A learner should be able to complete many exercises on the same canvas with minimal navigation or UI chrome.

### Curated JSON owns curriculum facts; AI is generation-only

Curated JSON owns:

- 72 canonical N5 lesson entries (`GrammarEntry`);
- canonical titles, categories, patterns, base explanations, and 4 reviewed examples per lesson;
- 3 reviewed curated quizzes per lesson (`choices_raw` and `answer_raw`).

AI is generation-only:

- produces optional supplemental enrichment notes grounded in the selected `GrammarEntry`;
- generates exactly 10 new sentence-production handwriting exercises with accepted Japanese variants;
- must not grade learner answers;
- must not decide next actions or control navigation;
- must not mutate curriculum or database state directly.

Local deterministic code owns:

- ML Kit handwriting recognition candidate collection;
- `DeterministicAnswerChecker` (Unicode NFKC, whitespace removal, terminal punctuation stripping);
- curated quiz validation against `answer_raw`;
- Room persistence of layouts, ink strokes, attempts, and generated snapshots.
## Core learning loop

`Probe -> Plan -> Teach -> Handwrite -> Grade -> Update mastery -> Adapt -> Review`

The main interaction loop on the canvas is intentionally simpler:

`Learn (Canonical JSON) -> Review Quizzes -> Handwrite -> Deterministic Check -> Concise Feedback`
## Run Android

Open `android/` in the latest stable Android Studio, sync Gradle, then run on an Android tablet or emulator.

The current Android client targets `compileSdk 36`, Compose BOM `2026.06.01`, Room `2.6.1`, Jetpack Ink `1.0.0`, and ML Kit Digital Ink `19.0.0`.

The Japanese Digital Ink model is downloaded on demand on first use and requires network access for the initial model download. Finger gestures remain available for canvas navigation while the pen authoring layer accepts stylus input.

## Agent commands

From the repository root, use the single dependency-free command surface:

```text
python scripts/repo.py setup
python scripts/repo.py dev
python scripts/repo.py format
python scripts/repo.py check
python scripts/repo.py test
python scripts/repo.py eval
python scripts/repo.py doctor
python scripts/repo.py gc --dry-run
```

See [`docs/index.md`](docs/index.md) for the repository map, source-of-truth links, smoke path, and maintenance workflow. `doctor` is the required pre-handoff check; `gc --dry-run` reports only reproducible Android build/cache output.

On Windows, `dev` starts the configured tablet emulator; on other hosts it builds the debug APK through the Gradle wrapper.

## Local-first architecture status

The local-first migration is complete. Lesson layouts, vector ink strokes, learner profiles, and exercise attempts are persisted on-device via Room + SQLite. Pedagogical feedback and grading operate via the on-device `AiTutorClient` boundary.
