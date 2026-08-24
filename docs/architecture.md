# Architecture

```text
Android tablet
Kotlin + Compose
  |
  |-- world canvas
  |-- read-only movable lesson text
  |-- exercises + progressive hints
  |-- Jetpack Ink strokes
  |-- ML Kit Digital Ink recognition
  |
  | REST / SSE
  v
Fastify + TypeScript
  |
  |-- deterministic learning services
  |   |-- probe
  |   |-- mastery
  |   |-- curriculum DAG
  |   `-- review scheduling
  |
  |-- Pi JapaneseTutorAgent
  |   |-- decide next teaching action
  |   |-- diagnose learning gaps
  |   `-- adapt explanation/exercise strategy
  |
  `-- Drizzle -> SQLite
```

## Boundaries

### Android owns
- rendering and interaction
- world/screen coordinate transforms
- stylus capture
- handwriting recognition
- presentation of AI annotations

### Backend owns
- lesson/curriculum state
- learner profile and mastery
- attempts and hint usage
- learning-path planning
- Pi tutor orchestration
- grading requests and tutor decisions

### Pi does not own
- raw database access
- mastery arithmetic
- SRS scheduling math
- curriculum truth
- direct unvalidated mutations
