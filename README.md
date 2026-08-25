# study-canvas

Tablet-first Japanese learning workspace: one lesson per zoomable canvas, handwriting exercises with stylus input, progressive hints, adaptive probing, and a personal AI tutor orchestrated by Pi.

## Product spec

See [`docs/PRD.md`](docs/PRD.md) for the complete product requirements, learning model, architecture, API boundaries, MVP phases, and definition of done.

## Architecture

- `android/` — Kotlin + Jetpack Compose tablet client
- `server/` — Node.js + TypeScript + Fastify + Pi Agent SDK
- SQLite — single backend source of truth via Drizzle ORM
- ML Kit Digital Ink — Japanese handwriting recognition on Android
- Jetpack Ink — low-latency stylus capture and vector stroke rendering

## Core learning loop

`Probe -> Plan -> Teach -> Handwrite -> Grade -> Update mastery -> Adapt`

The tutor is agentic only where judgment is useful. Mastery, curriculum dependencies, hint usage, and review scheduling remain deterministic application state.

## Implementation status

**Phase 1 — Canvas foundation: complete**

- [x] Density-independent world coordinates
- [x] Centroid-aware pan/zoom
- [x] Element selection
- [x] Movable read-only lesson text
- [x] SQLite-backed element layout persistence

**Phase 2 — Handwriting: complete**

- [x] Jetpack Ink pressure-pen stroke capture for stylus input
- [x] Dry vector-stroke rendering
- [x] Whole-stroke eraser with hit testing
- [x] `ink_strokes` persistence in SQLite
- [x] Idempotent stroke save/delete API
- [x] ML Kit Japanese Digital Ink recognition
- [x] On-demand Japanese model download
- [x] Writing-area recognition context
- [x] Recognition candidate/debug view

Still pending:

- [ ] Progressive hint behavior
- [ ] Attempt submission and structured grading
- [ ] Canvas AI annotation rendering
- [ ] Probe engine
- [ ] Mastery/SRS engine
- [ ] Pi tools backed by SQLite repositories

## Run backend

```bash
cd server
npm install
cp .env.example .env
npm run db:push
npm run dev
```

The ink API also creates its table defensively on startup for local development, but `npm run db:push` remains the canonical schema sync path.

Pi credentials can be configured through Pi itself (`pi`, then `/login`) or provider environment variables supported by Pi.

## Run Android

Open `android/` in the latest stable Android Studio, sync Gradle, then run on an Android tablet or emulator.

The Android client targets `compileSdk 37`, Compose BOM `2026.08.00`, Jetpack Ink `1.0.0`, and ML Kit Digital Ink `19.0.0`.

The Japanese Digital Ink model is downloaded on demand on first use and requires network access for that initial model download. Finger gestures remain available for canvas navigation; the pen authoring layer accepts stylus input.
