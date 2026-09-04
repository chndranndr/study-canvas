# Architecture

Study Canvas is a **local-first Android tablet app**. The product does not require a custom application backend for the MVP.

```text
Android tablet
Kotlin + Jetpack Compose
  |
  |-- UI / canvas
  |   |-- zoomable world canvas
  |   |-- movable lesson material
  |   |-- open practice surface
  |   |-- minimal floating tools
  |   `-- inline recognition + AI annotations
  |
  |-- handwriting
  |   |-- Jetpack Ink stroke capture/rendering
  |   `-- ML Kit Digital Ink recognition
  |
  |-- deterministic learning domain
  |   |-- grading evidence
  |   |-- mastery
  |   |-- mistake memory
  |   |-- progressive hints
  |   |-- probe
  |   |-- curriculum DAG
  |   `-- review scheduling
  |
  |-- local data
  |   `-- Room -> SQLite
  |
  `-- AI boundary
      `-- AiTutorClient
           |
           | HTTPS
           v
      Managed model API
      Primary MVP path: Firebase AI Logic -> Gemini
```

## Architectural principles

### 1. Local-first

All durable product state lives on the Android device for the MVP:

- learner profile;
- curriculum/reference metadata;
- lesson state;
- canvas element positions;
- raw ink strokes;
- recognized text;
- attempts and hint usage;
- mastery and mistake history;
- review schedule;
- tutor memories that are worth persisting.

The app must remain usable for reading, writing, recognition, and deterministic review logic when the AI service is unavailable.

### 2. No custom backend for MVP

The MVP does not require:

- Node.js;
- Fastify;
- REST endpoints owned by this project;
- SSE;
- Drizzle ORM;
- a server-side SQLite database;
- Pi Agent SDK;
- an always-on server process.

If cross-device sync, multi-user accounts, or shared content become real requirements later, introduce backend infrastructure then rather than designing for it prematurely.

### 3. Deterministic code owns learning state

The model may interpret context and choose a pedagogical action, but it does not own application truth.

On-device deterministic code owns:

- mastery arithmetic;
- SRS/review scheduling;
- curriculum prerequisites;
- attempt persistence;
- hint evidence;
- validation;
- local transactions.

The AI must never invent or directly overwrite mastery values.

### 4. AI is a replaceable boundary

The Android app talks through one interface:

```text
AiTutorClient
  |
  |-- grade / explain
  |-- generate exercise
  |-- choose next action
  `-- create concise annotation
```

The domain layer must not depend on Gemini-specific types.

For the MVP, prefer **Firebase AI Logic with Gemini** because it allows a mobile app to call the model without shipping a production provider secret in the APK and without operating a custom backend.

Alternative adapters may be added later:

- user-supplied API key for personal/dev builds;
- a tiny serverless proxy for another provider;
- another managed mobile-safe AI gateway.

Do not embed a production provider API secret in the APK.

## Layer boundaries

### UI / presentation owns

- Compose screens;
- world/screen coordinate transforms;
- pan and zoom;
- element selection and movement;
- stylus tool state;
- rendering ink;
- rendering recognition results;
- rendering AI feedback and hints;
- transient UI state.

### Handwriting owns

- Jetpack Ink stroke capture;
- vector stroke persistence format;
- eraser hit testing;
- ML Kit Digital Ink recognition;
- recognition candidates and confidence.

### Domain owns

- exercise evaluation evidence;
- mastery updates;
- recurring mistake detection;
- hint progression;
- adaptive probe rules;
- curriculum traversal;
- review scheduling;
- deciding what context is safe and useful to send to AI.

### Data owns

Room/SQLite is the single durable source of truth on-device.

Suggested repositories:

```text
LessonRepository
InkRepository
AttemptRepository
MasteryRepository
CurriculumRepository
ReviewRepository
TutorMemoryRepository
```

Suggested core tables/entities:

```text
learner_profiles
knowledge_nodes
knowledge_edges
learner_mastery
lessons
lesson_elements
ink_strokes
exercise_attempts
hint_usage
mistake_memory
tutor_memories
review_schedule
```

### AI owns

AI may:

- evaluate semantic correctness and naturalness where rules alone are insufficient;
- explain a mistake;
- generate a bounded practice variation;
- choose one pedagogical next action from an allowed set;
- create concise learner-facing annotation text.

AI does not own:

- Room/SQLite access;
- local database transactions;
- mastery calculation;
- review interval math;
- canonical curriculum truth;
- arbitrary state mutation;
- application navigation.

## Tutor decision contract

Keep AI output structured and narrow.

Example:

```json
{
  "action": "RETRY",
  "message": "Use に for the destination of movement.",
  "targetConceptId": "particle-ni-destination",
  "reason": "The meaning is correct but the destination particle is wrong."
}
```

Initial allowed actions:

```text
NEXT_EXERCISE
RETRY
EXPLAIN
INSERT_PREREQUISITE
SCHEDULE_REVIEW
```

Validate the response before applying any local side effects.

## Practice canvas architecture

The lesson canvas is an **open writing workspace**, not a collection of dashboard cards.

```text
minimal top bar
floating pen tools

lesson reference material (movable / collapsible)

Practice 1 prompt
------------------------------------------------
large blank handwriting space

recognized text / concise feedback

Practice 2 prompt
------------------------------------------------
large blank handwriting space

Practice 3 ...
```

Rules:

- maximize writable area;
- keep permanent chrome small;
- do not wrap every exercise, recognition result, and feedback message in separate cards;
- use borders/background frames only when selection or hierarchy requires them;
- allow many exercises to coexist on one canvas;
- recognition and AI feedback appear inline near the relevant writing;
- lesson/reference content can be moved away or collapsed when the learner wants uninterrupted practice.

## Main runtime flow

```text
Stylus input
   |
Jetpack Ink
   |
raw vector strokes ---------------------> Room
   |
ML Kit Digital Ink
   |
recognized Japanese
   |
local attempt context
   |\
   | \--> deterministic evidence/mastery/review update
   |
   `----> AiTutorClient
              |
         managed AI API
              |
         structured result
              |
          validation
              |
       inline canvas feedback
```

AI failure must never cause raw ink or attempt data to be lost.

## Suggested Android package structure

```text
android/app/src/main/java/dev/studycanvas/app/
├── data/
│   ├── db/
│   ├── dao/
│   └── repository/
├── domain/
│   ├── grading/
│   ├── mastery/
│   ├── probe/
│   ├── review/
│   └── curriculum/
├── handwriting/
├── ai/
│   ├── AiTutorClient.kt
│   ├── TutorDecision.kt
│   └── firebase/
└── ui/
    ├── home/
    └── canvas/
```

## Deferred architecture

Do not add these until the product actually needs them:

- custom backend;
- authentication service;
- remote database;
- cross-device sync;
- multi-agent orchestration;
- vector database;
- Kubernetes/microservices;
- offline local LLM.
