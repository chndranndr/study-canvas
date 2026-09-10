# Knowledge base

This is the current-state index for agents. The PRD and architecture document product intent and system boundaries; this page records the decisions and operational evidence that should stay easy to find.

## Product specification

- Product intent and MVP scope: [PRD](PRD.md).
- Current implementation summary and quick start: [README](../README.md).
- Curated curriculum facts are mirrored in root `data/*.json` and bundled under `android/app/src/main/assets/content/*.json`; runtime loads the bundled assets, and tests validate the canonical dataset shape.

## Design decisions

- Android tablet is the only MVP product surface; no custom backend is required.
- Compose owns presentation, viewport pan/zoom, and world-to-screen rendering. Stored lesson and ink coordinates remain in world/local space; viewport state is presentation state.
- Jetpack Ink owns stylus stroke capture/rendering. Pointer events are mapped through the inverse viewport scale before authoring so wet and persisted strokes share the same coordinate space.
- Room/SQLite is the durable source of truth. AI generation is optional and bounded; deterministic local code owns recognition candidates, answer checking, and persistence rules.
- Lesson material remains native, read-only text rather than flattened images.

See [architecture.md](architecture.md) for the full boundary and runtime-flow contract.

## Execution plans and completed work

There is no active repository-local migration plan. Small changes use the task checklist and the review loop below; complex migrations should create a plan using the structure in the Kuskus workflow guidance before implementation.

Completed milestones are represented by executable tests, CI, and the current source. Historical phase-gate files are ignored workspace artifacts and are not the current command or architecture source of truth.

## Quality

- `python scripts/repo.py format` checks repository text hygiene without rewriting files.
- `python scripts/repo.py check` runs the complete Android JVM test task through the Gradle wrapper.
- `python scripts/repo.py eval` runs the representative lesson → ink persistence → deterministic grading → attempt persistence path, plus focused dataset, checker, and ink geometry tests.
- `python scripts/repo.py doctor` validates manifest/artifact/link/architecture evidence and then runs the recorded checks.
- `.github/workflows/android-ci.yml` runs the same repository doctor on pushes and pull requests to `main`.

The focused evaluation is intentionally behavior-oriented: it loads a canonical lesson, builds an exercise canvas, round-trips stylus ink through the local repository, grades a recognized answer deterministically, and persists the attempt. Device rendering and multi-touch remain an adapter-level manual check.

- Device smoke evidence (2026-09-10, Galaxy Tab SM-X700): APK installed with `adb install -r`; `MainActivity` launched in foreground with no crash; toolbar taps and finger pan remained stable; the user manually verified pinch. Palm-first stylus input remains unverified.

## Reliability

- Learner state, lesson state, ink, attempts, and review state persist locally through Room/SQLite.
- Reading, writing, recognition, and deterministic checking remain usable when the optional AI service is unavailable.
- AI output must be schema-validated before it can be rendered or persisted; AI cannot grade answers or mutate learning state directly.
- Startup emits a structured `StudyCanvas` log event with `event`, `status`, and `surface` fields. Capture it with Android `logcat` when diagnosing a device run.

## Security

- Never embed a production provider API secret in the APK.
- `ApiKeyStorage` owns the local API-key boundary; generated content is bounded by the `GrammarLessonGenerator` contract.
- Deterministic grading and persistence stay outside the AI boundary.
- Keep local database and learner data on-device unless a future product requirement explicitly adds a reviewed sync path.
- Do not read or commit `.env`, credentials, tokens, private keys, or production data.

## Technical debt

| Item | Evidence | Smallest next action |
| --- | --- | --- |
| No maintained Android instrumented smoke suite | `android/app/src/androidTest` is empty; device QA uses the documented ADB/emulator adapter. | Add a focused `androidTest` only when a repeatable interactive regression needs automation. |
| Palm-first stylus input lacks a repeatable device check | ADB can verify finger pan but cannot reproduce a resting palm plus real stylus contact; low-level multi-touch injection is permission-denied. | On an uncompleted writing card, place the palm/finger first, touch the stylus down and draw, then lift stylus before palm; verify only the stylus produces/persists ink and the viewport does not pan. |
| No Kotlin formatter/linter dependency | The repository uses Gradle/JVM tests plus the repository text-hygiene check. | Add one stable formatter/linter only when style drift becomes a recurring failure. |
| Optional Gemini enrichment needs network/model availability | The local-first path is deterministic, while model download/API calls are optional. | Keep offline behavior authoritative; add provider-specific checks only with a real integration environment. |
| Curriculum copies lack a documented regeneration/sync command | Root `data/*.json` and bundled `android/app/src/main/assets/content/*.json` are both present; runtime reads the bundled assets. | Establish one canonical generation or sync check before changing curriculum ingestion. |
| Historical ignored gate notes mention a removed server surface | `gates/` is ignored and contains stale server/npm commands. | Reconcile or remove those artifacts during an explicitly scoped maintenance task; do not use them as current checks. |

## Review loop

For non-trivial changes:

```text
implementation
→ repository doctor and focused evaluation
→ independent or primary-agent review
→ resolve findings
→ final doctor and relevant device/manual check
```

A finding is actionable only when it has repository evidence, a smallest remediation, and a verification path. Preserve user-owned work and avoid adding a parallel task runner or backend surface.
