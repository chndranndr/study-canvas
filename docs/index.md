# Study Canvas repository map

Study Canvas is an Android-only, local-first Japanese learning workspace. This page is the shortest route to the current product, architecture, commands, and agent workflow.

## Product

- [README](../README.md) — product summary, stack, and quick start.
- [PRD](PRD.md) — product intent, UX principles, scope, and acceptance criteria.
- [Architecture](architecture.md) — runtime flow, layer boundaries, persistence, and AI contract.
- [Knowledge base](knowledge-base.md) — current decisions, quality, reliability, security, plans, and technical debt.

## Run and verify

From the repository root:

```text
python scripts/repo.py setup
python scripts/repo.py check
python scripts/repo.py eval
python scripts/repo.py doctor
```

The Android-specific helpers remain available:

- `scripts/build-apk.bat [debug|release|check]` builds or checks the APK workflow.
- `scripts/run-tablet-emulator.bat [start|install|stop|status]` manages the configured tablet emulator on Windows.

`dev` starts the tablet emulator on Windows; on other hosts it builds the debug APK through the Gradle wrapper.

`doctor` validates this map, the manifest, documentation links, architecture boundaries, startup logging, and recorded checks. `gc --dry-run` reports only known reproducible Android build/cache output.

## Agent workflow

1. Read [AGENTS.md](../AGENTS.md), this map, and the relevant source-of-truth document.
2. Reuse the existing Gradle wrapper, scripts, tests, and CI workflow.
3. Run `python scripts/repo.py doctor` before handoff.
4. Use `python scripts/repo.py eval` for the focused curriculum → checking → ink smoke path.
5. Record durable decisions or debt in [knowledge-base.md](knowledge-base.md), not in transient build output.
