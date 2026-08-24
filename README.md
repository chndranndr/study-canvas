# study-canvas

Tablet-first Japanese learning workspace: one lesson per zoomable canvas, handwriting exercises with stylus input, progressive hints, adaptive probing, and a personal AI tutor orchestrated by Pi.

## Architecture

- `android/` — Kotlin + Jetpack Compose tablet client
- `server/` — Node.js + TypeScript + Fastify + Pi Agent SDK
- SQLite — single backend source of truth via Drizzle ORM
- ML Kit Digital Ink — Japanese handwriting recognition on Android
- Jetpack Ink — low-latency stylus foundation

## Core learning loop

`Probe -> Plan -> Teach -> Handwrite -> Grade -> Update mastery -> Adapt`

The tutor is agentic only where judgment is useful. Mastery, curriculum dependencies, hint usage, and review scheduling remain deterministic application state.

## Current scaffold

**Phase 1 — Canvas foundation: complete** (`world coordinates`, centroid-aware pan/zoom, selection, movable lesson text, SQLite-backed layout persistence).

- [x] Android Compose app shell
- [x] Zoom/pan world canvas
- [x] Movable, read-only lesson text element
- [x] Exercise/hint UI placeholder
- [x] Jetpack Ink and ML Kit dependencies wired
- [x] Fastify API shell
- [x] SQLite/Drizzle schema
- [x] Pi tutor session wrapper
- [x] Japanese tutor Pi skill
- [x] Persist element layout to backend SQLite
- [ ] Jetpack Ink stroke capture
- [ ] ML Kit recognition pipeline
- [x] Android <-> lesson/layout API client
- [ ] Probe engine
- [ ] Mastery/SRS engine
- [ ] Structured grading
- [ ] Pi tools backed by SQLite repositories

## Run backend

```bash
cd server
npm install
cp .env.example .env
npm run db:push
npm run dev
```

Pi credentials can be configured through Pi itself (`pi`, then `/login`) or provider environment variables supported by Pi.

## Run Android

Open `android/` in the latest stable Android Studio, sync Gradle, then run on an Android tablet/emulator.

The scaffold targets `compileSdk 37`, Compose BOM `2026.08.00`, Jetpack Ink `1.0.0`, and ML Kit Digital Ink `19.0.0`.
