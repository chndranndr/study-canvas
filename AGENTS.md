# Project instructions

- Keep the product tablet-first and stylus-first.
- Keep canvas coordinates in world space; viewport pan/zoom is presentation state.
- Lesson material is native read-only text, never flattened into images.
- Keep SQLite as the backend source of truth for the MVP.
- Pi owns tutor orchestration, not deterministic scoring or persistence rules.
- LLM outputs that mutate learning state must be schema-validated first.
- Prefer one `JapaneseTutorAgent` with bounded tools over multi-agent orchestration.
- Run backend typecheck before committing server changes.
- Keep Android dependencies on stable releases unless a feature explicitly requires pre-release APIs.
