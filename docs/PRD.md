# Study Canvas — Product Requirements Document

**Status:** Draft v2  
**Repository:** `chndranndr/study-canvas`  
**Primary platform:** Android tablet  
**Architecture:** Android-only, local-first, managed AI API  
**Product type:** AI-assisted Japanese learning workspace

---

## 1. Product Summary

Study Canvas is a tablet-first Japanese learning application built around one primary interaction model: **one learning topic lives on one large, zoomable canvas**.

Instead of presenting Japanese lessons as linear screens, flashcards, or chat messages, Study Canvas treats each lesson as a spatial notebook. The learner can pan and zoom, move reference material, write directly with a stylus, reveal progressive hints, complete many production exercises on the same canvas, and receive concise feedback near their writing.

The product is intentionally **local-first**. Durable learner state lives on the Android device. The app does not require a custom Node/Fastify backend for the MVP.

> Curated JSON owns curriculum facts and existing reviewed lesson content. AI only adds optional grammar enrichment and generates sentence-production practice. Recognition and grading are deterministic/local.

The bundled JSON file (`grammar_n5.json`) is the curriculum source of truth for all 72 N5 lessons. AI is only an optional bounded generator for supplemental explanation notes and exactly 10 sentence-production handwriting exercises. Handwriting recognition and answer checking are completely local and deterministic.

Core learning loop:

`Learn (Canonical JSON) -> Review Quizzes -> Handwrite -> Deterministic Check -> Concise Feedback`
---

## 2. Problem Statement

Most Japanese learning apps optimize for one or more of the following:

- recognition through multiple-choice questions;
- memorization through flashcards;
- fixed linear courses;
- generic AI chat;
- handwriting practice without adaptive teaching.

These approaches often fail to combine **active production**, **handwriting**, **personalized tutoring**, and **long-term learner memory** in one workspace.

Study Canvas focuses on repeated handwritten production. The learner should be able to keep writing and practicing without being interrupted by excessive navigation, card chrome, chat interfaces, or dashboard-style framing.

---

## 3. Product Vision

Create a Japanese learning environment that feels like a living notebook: the learner writes naturally, the tutor understands recurring weaknesses, and the lesson evolves around the learner's needs.

The product should feel closer to **a personal tutor annotating a notebook** than to a quiz app, LMS, or chatbot.

---

## 4. Product Principles

### 4.1 Production over recognition

Prefer tasks where the learner must produce Japanese from memory rather than select an answer from a list.

### 4.2 Handwriting is first-class input

Stylus writing is not decoration. It is a primary learning interaction and a measurable learning signal.

### 4.3 Practice surface over UI chrome

The lesson canvas should maximize writable space. Permanent controls stay small and unobtrusive.

Do not wrap every exercise, recognition result, hint, and AI response in a separate card. Use framing only when it improves hierarchy or selection.

### 4.4 Many exercises on one canvas

A learner should be able to work through several exercises without leaving the canvas.

The default practice experience should resemble:

```text
Practice 1
Saya ingin pergi ke Jepang.
-----------------------------------------
[ large handwriting area ]

recognized text / concise feedback

Practice 2
Saya ingin makan sushi.
-----------------------------------------
[ large handwriting area ]

Practice 3
...
```

### 4.5 Progressive assistance

Hints reveal information gradually instead of immediately exposing the answer.

### 4.6 Personalization optimizes learning, not comfort

If a learner frequently requests romaji, the tutor should not simply provide more romaji. It should infer possible kana weakness and adapt practice accordingly.

### 4.7 Curated JSON owns curriculum; AI is generation-only; checking is deterministic

Curated JSON owns the 72 canonical N5 lessons, reviewed explanations, examples, and curated quizzes.

AI is generation-only:
- produces optional supplemental enrichment notes grounded in the selected `GrammarEntry`;
- generates exactly 10 sentence-production handwriting exercises with accepted Japanese variants.

Deterministic application logic owns:
- ML Kit candidate recognition;
- `DeterministicAnswerChecker` (Unicode NFKC, whitespace stripping, punctuation stripping);
- curated quiz validation against `answer_raw`;
- attempt persistence;
- Room transactions.
### 4.8 The canvas is structured, not a rendered image

Lesson content, exercises, user ink, and AI annotations are native elements in world coordinates. They remain crisp at any zoom level and can be repositioned independently.

### 4.9 Local-first and YAGNI

For the MVP, keep product state on-device and avoid infrastructure that is not yet required.

Do not add a custom backend, remote database, authentication service, or multi-agent orchestration until a real requirement exists.

### 4.10 Curated knowledge over unconstrained generation

Grammar rules, vocabulary definitions, conjugation rules, and canonical curriculum dependencies should come from validated sources. AI teaches from those sources rather than inventing foundational knowledge.

---

## 5. Target Users

### Primary persona

A self-directed learner who:

- is learning Japanese from beginner to intermediate level;
- uses an Android tablet with a stylus;
- wants to practice writing kana and Japanese sentences;
- prefers active production over multiple-choice exercises;
- wants feedback tailored to recurring mistakes;
- wants a learning path that adapts over weeks or months.

### Initial target range

MVP content targets approximately JLPT N5–N4 while keeping the content model expandable toward N3 and beyond.

---

## 6. MVP Goals

1. Deliver a smooth zoomable and pannable lesson canvas on Android tablet.
2. Support movable read-only lesson/reference elements.
3. Support low-latency stylus handwriting.
4. Recognize Japanese handwriting from stroke data.
5. Let the learner complete many sentence-production exercises on one open canvas.
6. Provide progressive hints without covering the writing area.
7. Grade recognized Japanese answers and explain mistakes.
8. Maintain persistent learner mastery and mistake history locally.
9. Run an adaptive probe to estimate the learner's knowledge frontier.
10. Let the AI tutor choose a bounded next pedagogical action.
11. Persist durable product state in on-device Room/SQLite.
12. Keep the app usable for reading, writing, recognition, and deterministic review when AI is unavailable.

---

## 7. Long-Term Goals

- personalized curriculum generation;
- spaced review driven by mastery evidence;
- kanji handwriting analysis;
- listening exercises;
- voice and speaking practice;
- adaptive lesson composition;
- richer AI annotations and diagrams on the canvas;
- cross-device synchronization;
- multi-user accounts.

These goals do not justify backend infrastructure in the MVP.

---

## 8. Non-Goals for MVP

The MVP will not include:

- social features;
- teacher/admin dashboards;
- leaderboards;
- complete N5–N1 content coverage;
- real-time collaboration;
- multi-agent orchestration;
- model fine-tuning per user;
- vector database or generic RAG framework;
- Kubernetes or microservices;
- PostgreSQL;
- a custom application backend;
- cross-device synchronization;
- offline local LLM inference;
- speech recognition;
- advanced calligraphy scoring.

---

## 9. Core User Experience

### 9.1 Home / Learning Hub

The home screen gives a compact view of:

- Continue Learning;
- Recommended Next;
- Today's Review;
- a small learning snapshot.

Analytics must remain secondary to learning actions.

### 9.2 One topic = one canvas

Examples:

- `～たいです`
- `に vs で`
- `て-form`
- katakana `シ / ツ / ソ / ン`

A lesson canvas can contain:

- lesson title;
- concise grammar explanation;
- examples;
- warnings and common mistakes;
- many exercises;
- user ink;
- recognition results;
- AI corrections;
- optional personal notes.

### 9.3 Canvas visual direction

The canvas background should be plain, quiet, and low-contrast.

Preferred characteristics:

- off-white or light neutral background;
- subtle optional dot/grid texture;
- minimal permanent chrome;
- floating pen/eraser/select/pan tools;
- minimal top bar;
- large uninterrupted writing areas;
- inline text instead of nested cards where possible.

Avoid:

- dashboard-like card grids inside the lesson;
- thick containers around every exercise;
- large AI panels;
- chatbot bubbles;
- decorative elements that compete with writing;
- modal transitions for routine practice actions.

### 9.4 Canvas interaction

The learner can:

- pinch to zoom;
- pan freely;
- select a movable element;
- drag lesson material to a new position;
- collapse or move reference material away;
- write with a stylus;
- erase strokes;
- complete multiple exercises on the same canvas;
- preserve layout and viewport state;
- reopen the lesson at the same layout.

Material text content is read-only, but its container can be repositioned.

### 9.5 World coordinates

All elements use world coordinates independent of screen pixels.

```text
screenX = worldX * scale + offsetX
screenY = worldY * scale + offsetY
```

Zooming must not mutate logical element coordinates or stored ink geometry.

---

## 10. Canvas Element Model

Initial element types:

```text
LessonTextElement
ExampleElement
ExerciseElement
InkElement
RecognitionElement
AiAnnotationElement
UserTextElement
```

Shared properties:

```text
id
lessonId
x
y
width
height
zIndex
type
movable
editable
selectable
locked
```

### LessonTextElement

- native rendered text;
- read-only content;
- movable;
- collapsible;
- selectable;
- persisted coordinates and width.

### ExerciseElement

Contains:

- prompt;
- target concept(s);
- semantic intent;
- vocabulary context;
- progressive hints;
- answer criteria;
- associated learner writing region.

The element does not need a visible card border in its normal state.

### InkElement

Contains vector stylus data, not a rendered PNG.

### RecognitionElement

Displays recognized Japanese text near the associated writing. It should be visually secondary to the handwriting.

### AiAnnotationElement

Contains concise AI feedback associated with an exercise, text range, or writing region.

---

## 11. Lesson Exercise Flow

Example prompt:

> Saya ingin pergi ke Jepang.

Initial state:

```text
Practice 1
Saya ingin pergi ke Jepang.

------------------------------------------------
large open handwriting space
------------------------------------------------

Hint
```

Learner writes:

```text
わたしは日本に行きたいです。
```

End-to-end flow:

1. Android captures raw ink strokes with Jetpack Ink.
2. Raw strokes are persisted locally.
3. ML Kit Digital Ink produces Japanese candidates.
4. Recognized text is stored as an attempt draft.
5. Local deterministic logic calculates immediately available evidence.
6. The app sends only the required attempt context to `AiTutorClient` when semantic grading or explanation is needed.
7. The AI returns structured grading/feedback or a bounded next action.
8. The app validates the result.
9. Local deterministic engines update mastery, mistakes, and review state.
10. Feedback appears inline near the writing.
11. The learner continues to the next exercise on the same canvas.

AI failure must not lose raw ink or prevent the learner from continuing local practice.

---

## 12. Progressive Hint System

### Hint 0 — Prompt only

```text
Saya ingin pergi ke Jepang.
```

### Hint 1 — Vocabulary

```text
Jepang -> 日本
pergi -> 行く
ingin melakukan -> ～たい
```

### Hint 2 — Grammar pattern

```text
N は Place に Vたいです
```

or:

```text
____ は ____ に ____たいです。
```

### Hint 3 — Reading aid

Romaji appears only as a late fallback and should preferably be token-level rather than a full answer.

```text
行く
iku

たいです
tai desu
```

### Final fallback — Show answer

Only after explicit learner action.

### Hint presentation

Hints should appear inline or in a small anchored panel beside the current exercise. They must not cover the handwriting region.

### Hint usage as evidence

Each attempt records:

- hints opened;
- hint order;
- time before each hint;
- correctness after each hint;
- whether the learner later succeeded without the hint.

Correctness without hints contributes stronger mastery evidence than correctness after maximum assistance.

---

## 13. Grading Model

Grading separates several dimensions.

### 13.1 Handwriting recognition confidence

How confident is the recognition pipeline that it correctly interpreted the strokes?

### 13.2 Meaning correctness

Does the answer express the requested meaning?

### 13.3 Grammar correctness

Are particles, conjugations, grammar, and word order acceptable?

### 13.4 Naturalness

Is the answer idiomatic or awkward while still understandable?

### 13.5 Target concept mastery

Did the learner demonstrate the specific lesson concept?

Example structured grade:

```json
{
  "correct": false,
  "meaningScore": 0.95,
  "grammarScore": 0.62,
  "naturalnessScore": 0.80,
  "errors": [
    {
      "type": "PARTICLE",
      "actual": "学校で行きます",
      "expected": "学校に行きます",
      "conceptId": "particle-ni-destination",
      "explanation": "Use に to mark the destination of movement."
    }
  ]
}
```

Feedback should remain concise by default. Deeper explanation appears only after the learner asks for it.

---

## 14. Probe System

The probe is an adaptive diagnostic, not a fixed placement test.

Its purpose is to identify the learner's **knowledge frontier**: what is reliable, what is fragile, and where performance begins to break down.

### MVP probe domains

1. kana recognition;
2. kana production/handwriting;
3. basic vocabulary;
4. particles;
5. verb conjugation;
6. sentence production.

### Adaptive behavior

The probe should not test every concept exhaustively.

```text
medium difficulty
      |
  correct?
  /     \
yes     no
 |       |
harder  easier
  \     /
   frontier
```

Probe scoring remains deterministic. AI may help choose a useful next question or explanation, but it must not invent mastery values.

---

## 15. Learner Model

### LearnerProfile

```text
nativeLanguage
targetLevel
learningGoal
studyPreferences
teachingPreferences
```

### KnowledgeState

```text
kana mastery
vocabulary mastery
grammar mastery
kanji mastery
production skill
```

### MistakeMemory

Examples:

```text
に vs で
は vs が
シ vs ツ
いる vs ある
```

### TeachingPolicy

Stores observed teaching preferences such as:

- preferred explanation depth;
- example-first vs rule-first preference;
- tolerance for romaji;
- useful topic domains;
- preferred exercise count.

These are observations, not hard constraints.

### SessionMemory

Stores recent learning activity, exercise history, and validated tutor decisions that are useful to retain.

---

## 16. Mastery Model

Mastery is deterministic and evidence-based.

A concept score may consider:

- answer correctness;
- attempts;
- hint level;
- response time;
- self-correction;
- retention over time;
- repeated error type;
- previous mastery;
- production vs recognition task type.

Example state:

```text
Concept: particle-ni
Mastery: 0.62
Attempts: 18
No-hint accuracy: 0.54
Hint-assisted accuracy: 0.81
Recent confusion: に vs で
```

AI may interpret these values but must not write arbitrary mastery numbers.

---

## 17. Curriculum Knowledge Graph

The curriculum is a canonical DAG stored locally in Room/SQLite.

### Node types

```text
kana
vocabulary
grammar
kanji
production
```

### Edge types

```text
PREREQUISITE
RELATED
CONFUSABLE_WITH
```

Example:

```text
basic particles
     |\
     | \
     v  v
     に  で
      \ /
       v
    に vs で
       |
       v
   movement verbs
       |
       v
     ～たい
```

The canonical graph defines valid learning dependencies. The personalized path is an overlay based on mastery, goals, mistakes, and review needs.

AI must not regenerate the canonical curriculum from scratch each session.

---

## 18. Personalized Learning Path

Path node statuses:

```text
LOCKED
READY
LEARNING
REVIEW
MASTERED
REMEDIAL
```

Possible tutor actions:

```text
NEXT_EXERCISE
RETRY
EXPLAIN
INSERT_PREREQUISITE
SCHEDULE_REVIEW
```

The set stays intentionally small for the MVP.

---

## 19. Learning Method / Tutor Loop

### Step 1 — Probe

Identify the learner's current frontier.

### Step 2 — Plan

Select relevant concepts based on:

- learner goal;
- prerequisites;
- mastery;
- mistakes;
- review schedule.

### Step 3 — Teach

Present a concise explanation grounded in curated data.

### Step 4 — Practice

Prefer active handwritten production and allow repeated exercises on the same canvas.

### Step 5 — Grade

Combine handwriting recognition, deterministic evidence, and AI semantic judgment where useful.

### Step 6 — Update learner state

Deterministic engines update mastery, mistakes, and review scheduling locally.

### Step 7 — Adapt

AI may choose one bounded next pedagogical action.

### Step 8 — Review

Weak or decaying concepts return to the learning queue.

---

## 20. AI Tutor Boundary

The project does not use Pi Agent SDK in the MVP.

The Android app depends on a small provider-neutral interface such as:

```text
AiTutorClient
```

### AI responsibilities

AI may:

- evaluate semantic correctness and naturalness;
- explain a mistake;
- generate a bounded exercise variation;
- choose what to practice next from allowed actions;
- diagnose unusual mistakes;
- choose a useful hint style;
- create concise annotation content.

### AI must not own

AI must not directly own:

- Room/SQLite access;
- persistence semantics;
- mastery calculation;
- SRS interval calculation;
- canonical dependency rules;
- arbitrary state mutation;
- application navigation.

### Primary MVP provider path

Preferred:

```text
Android
  |
Firebase AI Logic
  |
Gemini
```

Reason: the app can use a managed mobile-oriented AI path without operating a custom backend or embedding a production provider secret directly in the APK.

### Alternative adapters

Future adapters may include:

- user-supplied API key for personal/dev use;
- a tiny serverless proxy for another model provider;
- another managed mobile-safe AI gateway.

The domain layer must not depend on provider-specific response types.

---

## 21. Tutor Decision Contract

Keep model output narrow and structured.

Example:

```json
{
  "action": "RETRY",
  "message": "Use に for the destination of movement.",
  "targetConceptId": "particle-ni-destination",
  "reason": "The intended meaning is correct but the destination particle is wrong."
}
```

Allowed actions:

```text
NEXT_EXERCISE
RETRY
EXPLAIN
INSERT_PREREQUISITE
SCHEDULE_REVIEW
```

The response must be validated before local state changes occur.

---

## 22. Handwriting Architecture

### Android stack

- Jetpack Ink for low-latency stylus capture and vector strokes;
- ML Kit Digital Ink Recognition for Japanese handwriting recognition.

### Recognition pipeline

```text
Stylus
  |
Jetpack Ink
  |
vector strokes
  |
Room persistence
  |
ML Kit Digital Ink
  |
Japanese candidates
  |
recognized answer
```

Raw strokes should be preserved so recognition can be retried later with better context or models.

---

## 23. Android Responsibilities

The Android app owns both presentation and product state for the MVP.

Responsibilities:

- canvas rendering;
- viewport transformation;
- element selection;
- element movement;
- stylus capture;
- erasing;
- handwriting recognition;
- hint interaction;
- structured grading state;
- AI annotations;
- mastery engine;
- mistake memory;
- probe engine;
- curriculum graph;
- personalized learning path;
- review scheduling;
- local persistence;
- AI request orchestration.

Stack:

```text
Kotlin
Jetpack Compose
Material 3
Jetpack Ink
ML Kit Digital Ink
Coroutines / Flow
Room / SQLite
Firebase AI Logic / Gemini
kotlinx.serialization or equivalent validation layer
```

---

## 24. Local Persistence

Room/SQLite is the single durable source of truth for the MVP.

Core entities/tables:

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

### Ink storage

Ink should not be stored as one giant canvas JSON object.

Store strokes independently and associate them with the lesson and exercise/writing region.

Stroke data may use a compact serialized vector representation.

### Repository boundaries

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

---

## 25. No Internal REST API

The Android app should not call a project-owned REST API for normal MVP operations.

Replace previous endpoints such as:

```text
/api/lessons
/api/ink
/api/tutor
/api/probe
```

with local repository/domain calls.

Only the external managed AI provider requires network access.

---

## 26. Lesson Content Strategy

Lesson content is structured, not stored as rendered images.

Material sections may include:

```text
title
shortExplanation
formationRule
examples
warnings
commonMistakes
relatedConcepts
```

Reference content may appear as movable/collapsible canvas elements.

Practice content should use as little framing as possible so the learner can dedicate most of the screen to handwriting.

AI may adapt examples and explanations, but canonical grammar facts come from validated reference data.

---

## 27. Personalization Signals

The system should collect locally:

- correctness;
- response time;
- hint level;
- retries;
- self-correction;
- handwriting recognition confidence;
- concept type;
- exercise type;
- recurring error category;
- retention after delay;
- learner-initiated review;
- skipped exercise;
- explanation requests.

These signals feed deterministic mastery and selected AI tutor context.

Do not send the entire local database to the model. Build the smallest context required for the current decision.

---

## 28. Spaced Review

The initial scheduler remains deterministic and local.

The scheduler decides **when** a concept is due. AI may decide **how** to review it.

Example:

```text
Scheduler:
particle-de is due today

AI tutor:
learner repeatedly confuses に and で
-> generate a contrastive sentence-production exercise
```

A future implementation may use FSRS.

---

## 29. Success Metrics

### Product metrics

- lesson completion rate;
- exercise completion rate;
- exercises completed per canvas session;
- handwriting recognition correction rate;
- average hint level per concept;
- no-hint accuracy;
- retry rate;
- mastery improvement over repeated sessions;
- review retention rate;
- tutor action completion rate;
- time from writing completion to visible feedback.

### UX quality metrics

- stylus latency feels natural;
- zoom/pan remains smooth with realistic lesson complexity;
- no accidental canvas movement while writing;
- learner can continue through several exercises without leaving the canvas;
- writable area dominates the practice screen;
- feedback appears near relevant writing;
- reopening a lesson restores expected spatial layout.

---

## 30. Performance Requirements

Initial expectations:

- stylus ink appears visually immediate;
- pan/zoom stays near display refresh rate under normal lesson complexity;
- handwriting recognition never blocks drawing UI;
- Room work does not block the main thread;
- AI requests never block local drawing or navigation;
- a normal lesson supports hundreds of elements/strokes without obvious degradation.

Exact budgets should be measured after the first working vertical slice.

---

## 31. Reliability Requirements

- raw learner ink must not be lost when AI grading fails;
- AI requests are retryable without duplicating local attempts;
- deterministic learner state updates are transactional;
- invalid AI responses fail validation instead of mutating storage;
- lesson material remains readable if AI is unavailable;
- handwriting recognition and local practice remain usable without AI;
- local database migration failures are surfaced clearly and do not silently discard data.

---

## 32. Privacy and Security

For MVP:

- do not embed production model-provider secrets in the APK;
- prefer Firebase AI Logic/App Check or another mobile-safe managed gateway;
- BYOK is acceptable only as an explicit personal/dev option;
- Room database files are not committed;
- logs avoid unnecessary learner content;
- only minimal context required for the current AI operation is transmitted;
- no account/auth system is required while the product remains single-user and local-first.

---

## 33. Observability

Keep observability lightweight for the MVP.

Useful local diagnostic events:

- session ID;
- lesson ID;
- attempt ID;
- recognition latency/confidence;
- AI request latency and result type;
- tutor action;
- mastery update result;
- persistence errors.

Never log provider secrets or full learner history unnecessarily.

---

## 34. MVP Vertical Slice

The first complete slice uses **～たいです**.

Required flow:

1. Open lesson canvas.
2. Display movable/collapsible reference material.
3. Pan and zoom.
4. Show several Indonesian sentence-production prompts on the same open canvas.
5. Write answers with a stylus.
6. Capture vector strokes with Jetpack Ink.
7. Persist strokes locally.
8. Recognize Japanese using ML Kit Digital Ink.
9. Show recognized text inline.
10. Grade the answer using deterministic logic plus AI where required.
11. Update attempt, mistake, mastery, and review state in Room.
12. Render concise feedback near the learner's answer.
13. Allow retry or continue directly to the next exercise on the same canvas.
14. Restore lesson state after reopening.

Initial exercise set: at least five `～たいです` exercises with particle variation.

---

## 35. MVP Implementation Phases

### Phase 1 — Canvas foundation — complete

- world coordinate model;
- pan/zoom;
- element selection;
- movable lesson text;
- persisted element layout.

### Phase 2 — Handwriting — complete

- Jetpack Ink stroke capture;
- eraser;
- InkElement persistence;
- ML Kit Japanese recognition;
- recognition debug/candidate view.

### Phase 2.5 — Local-first migration

Do this before building the remaining learning features.

- add Room entities/DAOs/repositories on Android;
- move lesson/layout persistence from server SQLite to Room;
- move ink persistence from REST to Room;
- remove `API_BASE_URL` dependency;
- add provider-neutral `AiTutorClient`;
- remove Pi SDK usage;
- remove Node/Fastify/Drizzle runtime from the target architecture;
- keep existing server code only temporarily until equivalent Android paths are working, then delete it.

### Phase 3 — Practice loop

- open low-frame exercise layout;
- multiple exercises on one canvas;
- progressive hints;
- attempt persistence;
- structured grade contract;
- inline recognition state;
- inline AI annotation rendering.

### Phase 4 — Learner model

- mastery engine;
- mistake memory;
- hint evidence;
- review schedule.

### Phase 5 — AI tutor

- managed AI provider integration;
- structured output validation;
- adaptive next-action decision;
- concise remediation/explanations;
- bounded exercise generation.

### Phase 6 — Probe

- adaptive probe session;
- six MVP probe domains;
- learner frontier output;
- initial personalized learning path.

### Phase 7 — Hardening

- error handling;
- Room migrations;
- AI retry behavior;
- persistence recovery;
- performance profiling;
- test coverage;
- release build.

---

## 36. Testing Strategy

### Android / domain

- coordinate transform unit tests;
- canvas state tests;
- stylus/gesture conflict tests;
- handwriting recognition adapter tests;
- hint progression UI tests;
- restore-state tests;
- Room DAO/repository tests;
- mastery engine unit tests;
- probe engine unit tests;
- curriculum traversal tests;
- tutor decision validation tests.

### AI regression evaluation

Maintain a small evaluation dataset containing:

- correct alternatives;
- particle mistakes;
- conjugation mistakes;
- unnatural but acceptable sentences;
- meaning-correct grammar-wrong answers;
- recognition/OCR-like errors.

AI provider or prompt changes should be checked against this set before release.

---

## 37. Target Repository Structure

```text
study-canvas/
├── android/
│   └── app/
│       └── src/main/java/dev/studycanvas/app/
│           ├── data/
│           │   ├── db/
│           │   ├── dao/
│           │   └── repository/
│           ├── domain/
│           │   ├── grading/
│           │   ├── mastery/
│           │   ├── probe/
│           │   ├── review/
│           │   └── curriculum/
│           ├── handwriting/
│           ├── ai/
│           │   ├── AiTutorClient.kt
│           │   ├── TutorDecision.kt
│           │   └── firebase/
│           └── ui/
│               ├── home/
│               └── canvas/
├── docs/
│   ├── PRD.md
│   └── architecture.md
└── README.md
```

The existing `server/` directory is transitional and should be removed after the Android local-first migration is complete.

---

## 38. Open Product Decisions

These do not block the next vertical slice:

1. Whether user-created notes become first-class canvas elements.
2. Exact handwriting quality scoring beyond recognition confidence.
3. Whether full-sentence romaji is ever allowed.
4. Whether element movement is always enabled or uses layout/edit mode.
5. Final mastery formula and calibration strategy.
6. Exact SRS algorithm.
7. Curated grammar content source and licensing.
8. Whether lesson explanations are generated, templated, or hybrid.
9. Which managed AI provider remains the default after MVP evaluation.
10. Whether cross-device sync is ever necessary.

---

## 39. Definition of MVP Done

The MVP is done when a learner can install the Android app on a tablet and complete this sequence without developer intervention:

1. run an initial adaptive probe;
2. receive an initial learning recommendation;
3. open a `～たいです` lesson canvas;
4. move or collapse lesson reference material;
5. zoom and pan naturally;
6. complete several handwritten exercises on the same uncluttered canvas;
7. use progressive hints;
8. receive recognized Japanese text and concise structured AI corrections inline;
9. have mastery, mistakes, ink, attempts, and review state persisted locally;
10. receive a personalized next exercise or remediation from the AI tutor;
11. continue practicing even if the AI service temporarily fails;
12. close and reopen the lesson with local state preserved.

At that point the core product hypothesis has been validated:

> **A stylus-first open practice canvas, local learner memory, and a bounded AI tutor can provide a more focused Japanese production-learning experience than a conventional linear quiz or chat interface.**
