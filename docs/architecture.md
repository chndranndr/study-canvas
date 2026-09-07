# Architecture

Study Canvas is a **local-first Android tablet app**. The product does not require a custom application backend for the MVP.

> Curated JSON owns curriculum facts and existing reviewed lesson content. AI only adds optional grammar enrichment and generates sentence-production practice. Recognition and grading are deterministic/local.
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
  |   |-- GrammarContentRepository (bundled grammar_n5.json)
  |   |-- Kana/Vocabulary/Kanji writing repositories
  |   |-- DeterministicAnswerChecker (local NFKC & exact matching)
  |   |-- progressive hints
  |   `-- attempt evidence
  |
  |-- local data
  |   `-- Room -> SQLite (persists layouts, ink strokes, attempts, generated snapshots)
  |
  `-- AI generation boundary (optional)
      `-- GrammarLessonGenerator
           |
           | HTTPS (Gemini) — optional generation only
           v
      Managed model API
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

### 3. Deterministic code owns checking and curriculum

The bundled JSON curriculum is the source of truth for lessons and curated quizzes.

On-device deterministic code owns:

- canonical grammar entries (72 N5 lessons);
- curated quiz validation against `answer_raw`;
- handwriting answer checking via `DeterministicAnswerChecker`;
- attempt persistence;
- hint evidence;
- Room transactions.

Answer checking never calls Gemini or any other model.

### 4. AI is generation-only

AI does not grade answers, make curriculum decisions, or control navigation. It only:

- produces optional supplemental enrichment notes grounded in the selected `GrammarEntry`;
- generates exactly 10 sentence-production handwriting exercises with accepted Japanese variants.

The domain layer does not depend on Gemini-specific types.

For the MVP, Gemini is accessed via an API key or managed boundary. Alternative generators (including deterministic local generator for offline use) implement `GrammarLessonGenerator`.
```text
GrammarLessonGenerator
  |
  `-- generate(grammar: GrammarEntry, exerciseCount = 10): Result<GeneratedGrammarLesson>
```

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

AI may only:

- generate optional supplemental grammar enrichment grounded in the selected `GrammarEntry`;
- generate exactly 10 new sentence-production handwriting exercises with hints and accepted Japanese answers.

AI does not own:

- answer grading or correctness checking;
- Room/SQLite access;
- curriculum source of truth;
- mastery or review state;
- application navigation.

## Grammar generation contract

Keep AI output structured and narrow:

```json
{
  "grammarId": "54",
  "enrichment": {
    "summary": "concise explanation",
    "formation": "pattern formation rule",
    "commonMistakes": ["mistake 1"],
    "notes": ["note 1"]
  },
  "exercises": [
    {
      "id": "generated-1",
      "promptEn": "I want to go to Japan.",
      "hints": {
        "vocabulary": "Japan = 日本, go = 行く",
        "pattern": "Place に/へ Vたいです",
        "readingFallback": "日本 = にほん, 行く = いく"
      },
      "acceptedAnswers": [
        "日本に行きたいです",
        "日本へ行きたいです",
        "にほんにいきたいです",
        "にほんへいきたいです"
      ]
    }
  ]
}
```

Validate the response before persisting or rendering.

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
bounded recognition candidates
   |
DeterministicAnswerChecker (local NFKC & exact match)
   |
acceptedAnswers from generated exercise or curated quiz
   |
local correct / incorrect feedback
   |
attempt persistence in Room
```

Checking an answer performs zero AI / network calls.
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
