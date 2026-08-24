# Study Canvas — Product Requirements Document

**Status:** Draft v1  
**Repository:** `chndranndr/study-canvas`  
**Primary platform:** Android tablet  
**Architecture:** Android frontend + TypeScript/Pi backend + SQLite  
**Product type:** AI-assisted Japanese learning workspace

---

## 1. Product Summary

Study Canvas is a tablet-first Japanese learning application built around one primary interaction model: **one learning topic lives on one large, zoomable canvas**.

Instead of presenting Japanese lessons as linear screens, flashcards, or chat messages, Study Canvas treats each lesson as a spatial notebook page. The learner can pan and zoom across the canvas, move read-only lesson elements, write directly with a stylus, request progressive hints, and receive AI feedback directly beside their writing.

The AI tutor is not intended to behave like a generic chatbot. It maintains a persistent learner model, probes the learner's knowledge frontier, plans a personalized learning path, adapts explanations and exercises, diagnoses recurring mistakes, and decides what the learner should study next.

Core learning loop:

`Probe -> Plan -> Teach -> Practice -> Grade -> Update mastery -> Adapt -> Review`

---

## 2. Problem Statement

Most Japanese learning apps optimize for one or more of the following:

- recognition through multiple-choice questions;
- memorization through flashcards;
- fixed linear courses;
- generic AI chat;
- handwriting practice without adaptive teaching.

These approaches often fail to combine **active production**, **handwriting**, **personalized tutoring**, and **long-term learner memory** in one experience.

A learner may recognize a grammar pattern but still be unable to produce it from memory. Another learner may answer correctly only after several hints. Another may repeatedly confuse similar particles or kana. A static course usually treats these learners as equivalent.

Study Canvas models those differences explicitly and uses them to change what happens next.

---

## 3. Product Vision

Create a Japanese learning environment that feels like a living notebook: the learner writes naturally, the tutor understands the learner over time, and the lesson evolves around the learner's needs.

The product should feel closer to **a personal tutor living inside a notebook** than to a quiz app or chat interface.

---

## 4. Product Principles

### 4.1 Production over recognition

Prefer tasks where the learner must produce Japanese from memory rather than select an answer from a list.

### 4.2 Handwriting is first-class input

Stylus writing is not decoration. It is a primary learning interaction and a measurable learning signal.

### 4.3 Progressive assistance

Hints reveal information gradually instead of immediately exposing the answer.

### 4.4 Personalization optimizes learning, not comfort

If a learner frequently requests romaji, the tutor should not simply provide more romaji. It should infer possible kana weakness and adapt practice accordingly.

### 4.5 AI chooses pedagogy; deterministic code owns state

AI decides what explanation, remediation, or exercise is pedagogically useful. Deterministic application logic owns mastery scores, review schedules, curriculum dependencies, validation, and persistence.

### 4.6 The canvas is structured, not a rendered image

Lesson content, exercises, user ink, and AI annotations are native elements in world coordinates. They remain crisp at any zoom level and can be repositioned independently.

### 4.7 Curated knowledge over unconstrained generation

Grammar rules, vocabulary definitions, conjugation rules, and canonical curriculum dependencies should come from validated sources. The AI tutor teaches from those sources rather than inventing foundational knowledge.

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

MVP content targets approximately JLPT N5–N4 skills while keeping the architecture expandable toward N3 and beyond.

---

## 6. MVP Goals

1. Deliver a smooth zoomable and pannable lesson canvas on Android tablet.
2. Support read-only but movable lesson text elements.
3. Support low-latency stylus handwriting.
4. Recognize Japanese handwriting from stroke data.
5. Present sentence-production exercises with progressive hints.
6. Grade recognized Japanese answers and explain mistakes.
7. Maintain persistent learner mastery and mistake history.
8. Run an adaptive probe to estimate the learner's knowledge frontier.
9. Use Pi as the central AI tutor harness.
10. Persist backend product state in SQLite.
11. Let the tutor choose the next learning action based on learner state.

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
- offline local LLM inference;
- speech recognition;
- advanced calligraphy scoring.

---

## 9. Core User Experience

### 9.1 One topic = one canvas

Examples:

- `～たいです`
- `に vs で`
- `て-form`
- katakana `シ / ツ / ソ / ン`

A lesson canvas can contain:

- lesson title;
- grammar explanation;
- examples;
- warnings and common mistakes;
- exercises;
- user ink;
- AI corrections;
- optional personal notes.

### 9.2 Canvas interaction

The learner can:

- pinch to zoom;
- pan freely;
- select a movable element;
- drag lesson material to a new position;
- write with a stylus;
- erase strokes;
- preserve layout and viewport state;
- reopen the lesson at the same layout.

Material text content is read-only, but its container can be repositioned.

### 9.3 World coordinates

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
- selectable;
- persisted coordinates and width.

### ExerciseElement

Contains:

- Indonesian prompt;
- target concept(s);
- semantic intent;
- vocabulary context;
- progressive hints;
- answer criteria;
- associated learner writing area.

### InkElement

Contains vector stylus data, not a rendered PNG.

### AiAnnotationElement

Contains AI feedback associated with another element, stroke region, or exercise.

---

## 11. Lesson Exercise Flow

Example prompt:

> Saya ingin pergi ke Jepang.

Initial state:

```text
Saya ingin pergi ke Jepang.

[ handwriting area ]

Hint
```

Learner writes:

```text
わたしは日本に行きたいです。
```

End-to-end flow:

1. frontend collects ink strokes;
2. handwriting recognition produces Japanese candidates;
3. recognized text is submitted as an attempt;
4. backend grading evaluates meaning and grammar;
5. deterministic learning engines update evidence;
6. Pi tutor reads learner context and grading result;
7. Pi returns a pedagogical next action;
8. frontend renders feedback directly on the canvas.

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

The learner must still construct the grammar independently.

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

The frontend should be able to anchor feedback near the relevant handwriting.

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

### Future domains

- adjective conjugation;
- broader grammar patterns;
- reading comprehension;
- kanji reading;
- kanji writing;
- listening;
- speaking.

### 14.1 Kana recognition

Track hiragana and katakana separately, including confusion pairs such as:

- シ / ツ;
- ソ / ン;
- ぬ / め;
- れ / わ.

### 14.2 Kana production

Prompt from a sound, romaji token, or known word and require handwritten kana. Recognition and production are separate mastery dimensions.

### 14.3 Vocabulary

Test both directions:

```text
Japanese -> Indonesian
Indonesian -> Japanese
```

Production direction carries more weight for active recall.

### 14.4 Particles

Initial set:

```text
は
が
を
に
で
へ
と
の
も
から
まで
```

Use both focused contrast questions and free sentence production.

### 14.5 Verb conjugation

Initial forms:

```text
dictionary
ます
て
た
ない
たい
```

### 14.6 Sentence production

Increase complexity until performance becomes unstable.

Example sequence:

1. Saya makan sushi.
2. Saya ingin makan sushi.
3. Kemarin saya makan sushi dengan teman.
4. Karena hujan, saya tidak pergi ke sekolah.

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

Probe scoring is deterministic. Pi may decide which domain requires more probing, but Pi must not invent mastery values.

---

## 15. Learner Model

The AI tutor requires persistent structured learner state.

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

Stores recurring errors and confusion patterns.

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
- preferred exercise count;
- fatigue signals.

These are observations, not hard constraints. The tutor may deliberately use a less comfortable approach if it improves learning.

### SessionMemory

Stores recent learning activity, exercise history, and tutor decisions.

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

Pi may interpret these values but must not write arbitrary mastery numbers.

---

## 17. Curriculum Knowledge Graph

The curriculum is a canonical DAG stored in SQLite.

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

Pi must not regenerate the canonical curriculum from scratch each session.

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
NEXT_CONCEPT
RETRY
SHOW_EXPLANATION
GENERATE_EXTRA_EXERCISE
INSERT_PREREQUISITE
CONTRAST_CONFUSION_PAIR
SCHEDULE_REVIEW
```

---

## 19. Learning Method / Tutor Loop

### Step 1 — Probe

Identify the learner's current frontier.

### Step 2 — Plan

Select relevant nodes based on:

- learner goal;
- prerequisites;
- mastery;
- mistakes;
- review schedule.

### Step 3 — Teach

Compose an explanation appropriate to the learner while grounding rules in curated data.

### Step 4 — Practice

Prefer active production, especially handwritten sentence construction.

### Step 5 — Grade

Return structured grading.

### Step 6 — Update learner state

Deterministic engines update mastery, mistakes, and review scheduling.

### Step 7 — Adapt

Pi chooses the next pedagogical action.

### Step 8 — Review

Weak or decaying concepts return to the learning queue.

---

## 20. Pi Tutor Harness

Pi is the primary AI orchestration layer on the backend.

The initial architecture uses **one JapaneseTutorAgent**, not multiple autonomous agents.

### Pi responsibilities

Pi may:

- choose what to teach next;
- interpret learner history;
- decide whether remediation is needed;
- produce personalized explanations;
- generate appropriate exercises;
- diagnose unusual mistakes;
- choose a useful hint style;
- insert prerequisite or contrast lessons;
- generate canvas annotation content.

### Pi must not own

Pi must not directly own:

- arbitrary SQL;
- persistence semantics;
- mastery calculation;
- SRS interval calculation;
- canonical dependency rules;
- input validation;
- authentication or authorization.

### Initial Pi tools

```text
learner.getProfile
learner.getMastery
learner.getMistakes
learner.getHintHistory

curriculum.getNode
curriculum.getPrerequisites
curriculum.getConfusableConcepts

lesson.getCurrent
lesson.getReference

exercise.generate
exercise.getContext

canvas.createAnnotation

review.getDueConcepts
```

Mutation tools must be narrow and validated.

---

## 21. Pi Skill: Japanese Learning

Suggested structure:

```text
server/src/agent/skills/japanese-learning/
├── SKILL.md
├── probe.md
├── teaching-policy.md
├── hint-policy.md
├── grading-policy.md
└── adaptation-policy.md
```

Core policy:

```text
PROBE
-> identify frontier

PLAN
-> choose dependency-aware path

TEACH
-> one concept at a time

PRACTICE
-> prefer active handwritten production

EVALUATE
-> structured grade + learning evidence

ADAPT
-> advance, retry, remediate, or review
```

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
ML Kit Digital Ink
  |
Japanese candidates
  |
exercise context / pre-context
  |
recognized answer
```

Raw strokes should be preserved so recognition can be retried later with better context or models.

---

## 23. Frontend Responsibilities

The Android client owns interaction and rendering.

Responsibilities:

- canvas rendering;
- viewport transformation;
- element selection;
- element movement;
- stylus capture;
- erasing;
- handwriting recognition;
- hint interaction;
- displaying grades;
- displaying AI annotations;
- streaming tutor responses;
- local transient UI state.

Frontend stack:

```text
Kotlin
Jetpack Compose
Material 3
Jetpack Ink
ML Kit Digital Ink
Coroutines / Flow
Ktor Client or Retrofit
```

---

## 24. Backend Responsibilities

The backend owns learning intelligence and persistent state.

Responsibilities:

- Pi tutor runtime;
- grading orchestration;
- learner profile;
- mastery engine;
- mistake memory;
- probe engine;
- curriculum graph;
- personalized learning path;
- lesson generation;
- exercise generation;
- review scheduling;
- tutor memory;
- SQLite persistence.

Backend stack:

```text
Node.js
TypeScript
Fastify
Pi Agent SDK
Zod
Drizzle ORM
SQLite
```

---

## 25. Database

SQLite is the single backend source of truth for MVP.

Core tables:

```text
learner_profiles
knowledge_nodes
knowledge_edges
learner_mastery
lessons
lesson_elements
exercises
exercise_attempts
hint_usage
mistake_memory
tutor_memories
learning_paths
learning_path_nodes
review_schedule
```

### Ink storage

Ink should not be stored as one giant canvas JSON object.

Suggested model:

```text
canvas_elements
ink_strokes
```

Stroke data may use compact binary/blob representation or structured serialized vectors.

---

## 26. Initial API Surface

Exact routes may evolve.

### Learner

```text
GET  /api/learner/profile
PUT  /api/learner/profile
GET  /api/learner/mastery
GET  /api/learner/mistakes
```

### Probe

```text
POST /api/probe/start
POST /api/probe/:sessionId/answer
GET  /api/probe/:sessionId
```

### Lessons

```text
GET  /api/lessons/:id
GET  /api/lessons/next
POST /api/lessons/:id/elements/layout
```

### Exercises

```text
POST /api/exercises/:id/attempts
POST /api/exercises/:id/hints/:level
```

### Tutor

```text
POST /api/tutor/next-action
GET  /api/tutor/stream
```

Backend responses should prefer structured state changes over raw free-form chat.

---

## 27. Tutor Action Contract

Example:

```json
{
  "type": "ANNOTATE_AND_RETRY",
  "annotation": {
    "message": "Use に for the destination of movement.",
    "anchor": {
      "elementId": "ink-123"
    }
  },
  "nextExerciseId": "exercise-456"
}
```

Possible action types:

```text
NEXT_EXERCISE
RETRY
SHOW_EXPLANATION
SHOW_CONTRAST
INSERT_REMEDIAL_LESSON
SCHEDULE_REVIEW
LESSON_COMPLETE
```

---

## 28. Lesson Content Strategy

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

The frontend renders these as movable read-only canvas elements.

AI may adapt presentation and examples, but canonical grammar facts come from validated reference data.

---

## 29. Personalization Signals

The system should collect:

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

These signals feed deterministic mastery and Pi tutor context.

---

## 30. Spaced Review

The initial scheduler remains deterministic.

The scheduler decides **when** a concept is due. Pi decides **how** to review it.

Example:

```text
Scheduler:
particle-de is due today

Pi:
learner repeatedly confuses に and で
-> generate contrastive sentence-production exercise
```

A future implementation may use FSRS.

---

## 31. Success Metrics

### Product metrics

- lesson completion rate;
- exercise completion rate;
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
- annotations appear near relevant content;
- reopening a lesson restores expected spatial layout.

---

## 32. Performance Requirements

Initial expectations:

- stylus ink appears visually immediate;
- pan/zoom stays near display refresh rate under normal lesson complexity;
- handwriting recognition never blocks drawing UI;
- grading exposes visible progress or streaming state;
- SQLite work does not block the event loop unnecessarily;
- a normal lesson supports hundreds of elements/strokes without obvious degradation.

Exact budgets should be measured after the first working vertical slice.

---

## 33. Reliability Requirements

- raw learner ink must not be lost when AI grading fails;
- failed AI requests are retryable without duplicate attempts;
- attempt submission should support idempotent attempt IDs;
- deterministic learner state updates are transactional;
- Pi failure must not corrupt mastery state;
- invalid AI tool calls fail validation instead of mutating storage;
- lesson material remains readable if AI is unavailable.

---

## 34. Privacy and Security

For MVP:

- provider secrets live only on the backend;
- Android never contains production provider API keys;
- Pi tools do not expose arbitrary SQL;
- input and tool outputs are validated with Zod;
- SQLite database files are not committed;
- logs avoid unnecessary sensitive learner notes;
- auth may be deferred for single-user development, but route design must allow auth later.

---

## 35. Observability

MVP logging should capture:

- request ID;
- learner/session ID when appropriate;
- attempt ID;
- Pi session/tool usage;
- grading latency;
- recognition confidence received from client;
- tutor action;
- mastery update result;
- errors.

Never log provider secrets.

---

## 36. MVP Vertical Slice

The first complete slice uses **～たいです**.

Required flow:

1. Open lesson canvas.
2. Display movable read-only explanation elements.
3. Pan and zoom.
4. Show one Indonesian sentence-production prompt.
5. Write answer with stylus.
6. Capture vector strokes with Jetpack Ink.
7. Recognize Japanese using ML Kit Digital Ink.
8. Send recognized answer to backend.
9. Grade answer.
10. Update attempt, mistake, and mastery state in SQLite.
11. Let Pi choose the next tutor action.
12. Render feedback near the learner's answer.
13. Allow retry or next exercise.
14. Restore lesson state after reopening.

Initial exercise set: at least five `～たいです` exercises with particle variation.

---

## 37. MVP Implementation Phases

### Phase 1 — Canvas foundation

- world coordinate model;
- pan/zoom;
- element selection;
- movable lesson text;
- persisted element layout.

### Phase 2 — Handwriting

- Jetpack Ink stroke capture;
- eraser;
- InkElement persistence;
- ML Kit Japanese recognition;
- recognition debug/candidate view.

### Phase 3 — Exercise loop

- exercise model;
- progressive hints;
- attempt submission;
- structured grade contract;
- canvas annotation rendering.

### Phase 4 — Learner model

- mastery engine;
- mistake memory;
- hint evidence;
- review schedule.

### Phase 5 — Pi tutor

- Japanese learning skill;
- read-only learner tools;
- bounded tutor action tools;
- adaptive next-action decision;
- remedial exercise generation.

### Phase 6 — Probe

- adaptive probe session;
- six MVP probe domains;
- learner frontier output;
- initial personalized learning path.

### Phase 7 — Hardening

- error handling;
- idempotency;
- persistence recovery;
- performance profiling;
- test coverage;
- release build.

---

## 38. Testing Strategy

### Android

- coordinate transform unit tests;
- canvas state tests;
- stylus/gesture conflict tests;
- handwriting recognition adapter tests;
- hint progression UI tests;
- restore-state tests.

### Backend

- repository tests against temporary SQLite;
- mastery engine unit tests;
- probe engine unit tests;
- grading schema validation tests;
- curriculum traversal tests;
- Pi tool contract tests;
- tutor action validation tests;
- API integration tests.

### AI regression evaluation

Maintain a dataset containing:

- correct alternatives;
- particle mistakes;
- conjugation mistakes;
- unnatural but acceptable sentences;
- meaning-correct grammar-wrong answers;
- OCR-like recognition errors.

AI changes should be evaluated against this set before release.

---

## 39. Target Repository Structure

```text
study-canvas/
├── android/
│   ├── app/
│   └── ...
│
├── server/
│   ├── src/
│   │   ├── api/
│   │   ├── agent/
│   │   │   ├── tools/
│   │   │   └── skills/
│   │   ├── db/
│   │   ├── learning/
│   │   │   ├── probe/
│   │   │   ├── mastery/
│   │   │   ├── review/
│   │   │   ├── grading/
│   │   │   └── curriculum/
│   │   └── app.ts
│   └── data/
│
├── docs/
│   └── PRD.md
│
└── README.md
```

---

## 40. Open Product Decisions

These do not block the first vertical slice:

1. Whether user-created notes sync as first-class canvas elements.
2. Exact handwriting quality scoring beyond recognition confidence.
3. Whether full-sentence romaji is ever allowed.
4. Whether element movement is always enabled or uses layout/edit mode.
5. Final mastery formula and calibration strategy.
6. Exact SRS algorithm.
7. Curated grammar content source and licensing.
8. Whether lesson explanations are generated, templated, or hybrid.
9. Authentication strategy for multi-user support.
10. Cross-device sync architecture.

---

## 41. Definition of MVP Done

The MVP is done when a learner can install the Android app on a tablet and complete this sequence without developer intervention:

1. run an initial adaptive probe;
2. receive an initial learning recommendation;
3. open a `～たいです` lesson canvas;
4. move lesson material around the canvas;
5. zoom and pan naturally;
6. answer exercises by handwriting Japanese;
7. use progressive hints;
8. receive structured AI corrections on the canvas;
9. have mastery and mistakes persisted;
10. receive a personalized next exercise or remediation from Pi;
11. close and reopen the lesson with state preserved.

At that point the core product hypothesis has been validated:

> **A stylus-first spatial canvas combined with a persistent AI tutor can provide a more personal Japanese production-learning experience than a conventional linear quiz or chat interface.**
