بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ
“Bismillaahirrahmaanirrahiim”
“Dengan menyebut nama Allah Yang Maha Pemurah lagi Maha Penyayang.“

اللَّهُمَّ إنِّي أَسْأَلُكَ عِلْماً نَافِعاً، وَرِزْقاً طَيِّباً، وَعَمَلاً مُتَقَبَّلاً
Ya Allâh! Sesungguhnya aku memohon kepada-Mu ilmu yang bermanfaat, rezeki yang baik dan amalan yang diterima

# Project instructions

- Keep the product tablet-first and stylus-first.
- Keep canvas coordinates in world space; viewport pan/zoom is presentation state.
- Lesson material is native read-only text, never flattened into images.
- Keep SQLite as the backend source of truth for the MVP.
- Pi owns tutor orchestration, not deterministic scoring or persistence rules.
- LLM outputs that mutate learning state must be schema-validated first.
- Prefer one `JapaneseTutorAgent` with bounded tools over multi-agent orchestration.
- If a backend is introduced later, run its typecheck before committing server changes.
- Keep Android dependencies on stable releases unless a feature explicitly requires pre-release APIs.

## Repository map

- Read [`docs/index.md`](docs/index.md) first for product, architecture, commands, and verification.
- Use [`docs/PRD.md`](docs/PRD.md) for intended product behavior and [`docs/architecture.md`](docs/architecture.md) for current layer boundaries.
- Record durable decisions, reliability/security constraints, quality evidence, and debt in [`docs/knowledge-base.md`](docs/knowledge-base.md).

## Verification

- Use `python scripts/repo.py doctor` before handoff.
- Use `python scripts/repo.py eval` for the focused curriculum, deterministic checking, and ink smoke path.
- Preserve the existing Gradle wrapper, Android scripts, tests, and CI workflow; do not add a parallel backend or task runner.
