#!/usr/bin/env python3
"""Small, dependency-free command surface for Study Canvas agents."""

from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android"
MANIFEST = ROOT / ".harness" / "manifest.json"
COMMAND_SLOTS = ("setup", "dev", "format", "check", "test", "eval", "doctor", "gc")
INSPECTIONS = {
    "inspect: adapter exposes the critical surface",
    "inspect: findings have evidence and remediation",
    "inspect: generated paths have regeneration evidence",
    "inspect: links resolve",
    "inspect: manifest matches observed artifacts",
    "inspect: project has no interactive surface",
}
TEXT_SUFFIXES = {".bat", ".json", ".kt", ".kts", ".md", ".py", ".txt", ".xml", ".yml", ".yaml"}
LINK_RE = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")


def _run(args: list[str], cwd: Path = ROOT) -> int:
    print("$ " + " ".join(args))
    return subprocess.run(args, cwd=cwd, check=False).returncode


def _run_gradle(args: list[str]) -> int:
    wrapper = ANDROID / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.exists():
        print(f"ERROR: Gradle wrapper not found: {wrapper}", file=sys.stderr)
        return 1
    if os.name == "nt":
        command = ["cmd.exe", "/c", str(wrapper), *args]
    else:
        command = ["bash", str(wrapper), *args]
    return _run(command, cwd=ANDROID)


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def _source_files() -> list[Path]:
    roots = [
        ROOT / "README.md",
        ROOT / "AGENTS.md",
        ROOT / ".github",
        ROOT / ".harness",
        ROOT / "docs",
        ROOT / "scripts",
        ANDROID / "app" / "src",
    ]
    result: set[Path] = set()
    for root in roots:
        if root.is_file():
            result.add(root)
            continue
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
                continue
            if "build" in path.parts or ".gradle" in path.parts:
                continue
            result.add(path)
    return sorted(result)


def _format_errors() -> list[str]:
    errors: list[str] = []
    for path in _source_files():
        raw = path.read_bytes()
        if b"\x00" in raw:
            continue
        text = raw.decode("utf-8")
        for number, line in enumerate(text.splitlines(), start=1):
            markdown_hard_break = (
                path.suffix.lower() == ".md"
                and line.endswith("  ")
                and not line.endswith("   ")
            )
            if line.endswith((" ", "\t")) and not markdown_hard_break:
                errors.append(f"{path.relative_to(ROOT)}:{number}: trailing whitespace")
        if raw and not raw.endswith(b"\n"):
            errors.append(f"{path.relative_to(ROOT)}: missing final newline")
    return errors


def _link_errors() -> list[str]:
    errors: list[str] = []
    documents = [ROOT / "README.md", ROOT / "AGENTS.md", *sorted((ROOT / "docs").glob("*.md"))]
    for document in documents:
        text = _read(document)
        for match in LINK_RE.finditer(text):
            target = match.group(1).strip().strip("<>")
            if not target or target.startswith(("http://", "https://", "mailto:")):
                continue
            target = unquote(target.split("#", 1)[0].split("?", 1)[0])
            if not target:
                continue
            resolved = (document.parent / target).resolve()
            try:
                resolved.relative_to(ROOT.resolve())
            except ValueError:
                errors.append(f"{document.relative_to(ROOT)}: link escapes repository: {target}")
                continue
            if not resolved.exists():
                errors.append(f"{document.relative_to(ROOT)}: broken link: {target}")
    return errors


def _architecture_errors() -> list[str]:
    errors: list[str] = []
    source_root = ANDROID / "app" / "src" / "main" / "java"
    for path in source_root.rglob("*.kt"):
        relative = path.relative_to(source_root).as_posix()
        if "/data/" in f"/{relative}" or relative.startswith("data/"):
            continue
        if "import androidx.room." in _read(path):
            errors.append(f"{relative}: Room imports must stay inside the data boundary")
    return errors


def _knowledge_errors() -> list[str]:
    required = (
        "# Knowledge base",
        "## Product specification",
        "## Design decisions",
        "## Execution plans and completed work",
        "## Quality",
        "## Reliability",
        "## Security",
        "## Technical debt",
        "## Review loop",
    )
    path = ROOT / "docs" / "knowledge-base.md"
    if not path.exists():
        return ["docs/knowledge-base.md: missing knowledge base"]
    text = _read(path)
    return [f"docs/knowledge-base.md: missing heading {heading}" for heading in required if heading not in text]


def _ci_errors() -> list[str]:
    path = ROOT / ".github" / "workflows" / "android-ci.yml"
    if not path.exists():
        return [".github/workflows/android-ci.yml: missing CI workflow"]
    text = _read(path)
    required = ("actions/checkout@v4", "actions/setup-java@v4", "python scripts/repo.py doctor")
    return [f".github/workflows/android-ci.yml: missing {value}" for value in required if value not in text]


def _observability_errors() -> list[str]:
    path = ROOT / "android" / "app" / "src" / "main" / "java" / "dev" / "studycanvas" / "app" / "MainActivity.kt"
    text = _read(path)
    required = ('Log.i', '"event":"startup"', '"status":"ready"')
    return [f"MainActivity.kt: missing startup signal fragment {value}" for value in required if value not in text]


def _artifact_errors(value: object, label: str) -> list[str]:
    if not isinstance(value, str) or not value:
        return [f".harness/manifest.json: {label} must be a repository-relative path"]
    candidate = Path(value)
    if (
        candidate.is_absolute()
        or value.startswith(("/", "\\"))
        or re.match(r"^[A-Za-z]:[\\/]", value)
        or ".." in candidate.parts
    ):
        return [f".harness/manifest.json: {label} must stay inside the repository"]
    resolved = (ROOT / candidate).resolve()
    try:
        resolved.relative_to(ROOT.resolve())
    except ValueError:
        return [f".harness/manifest.json: {label} escapes the repository"]
    if not resolved.exists():
        return [f".harness/manifest.json: missing artifact {value}"]
    return []


def _validate_manifest() -> tuple[dict | None, list[str]]:
    if not MANIFEST.exists():
        return None, [".harness/manifest.json: missing manifest"]
    try:
        manifest = json.loads(_read(MANIFEST))
    except json.JSONDecodeError as error:
        return None, [f".harness/manifest.json: invalid JSON: {error}"]

    errors: list[str] = []
    if manifest.get("schema_version") != 2:
        errors.append(".harness/manifest.json: schema_version must be 2")
    if manifest.get("profile") != "standard":
        errors.append(".harness/manifest.json: profile must be standard")
    project = manifest.get("project")
    if not isinstance(project, dict) or not project.get("kind") or not isinstance(project.get("stacks"), list):
        errors.append(".harness/manifest.json: project kind/stacks are required")

    commands = manifest.get("commands")
    if not isinstance(commands, dict) or tuple(commands) != COMMAND_SLOTS:
        errors.append(".harness/manifest.json: commands must use the stable slot order")
        commands = commands if isinstance(commands, dict) else {}
    command_values = {value for value in commands.values() if isinstance(value, str)}
    token_pattern = re.compile(r"^[A-Za-z0-9_./:@%+=,-]+$")
    for slot in COMMAND_SLOTS:
        value = commands.get(slot)
        if value is not None and not isinstance(value, str):
            errors.append(f".harness/manifest.json: command {slot} must be a string or null")
        if not isinstance(value, str):
            continue
        if any(character in value for character in ('"', "'", "\n", "\r", ";", "|", ">", "<", "&")):
            errors.append(f".harness/manifest.json: command {slot} contains shell syntax")
        if value.startswith(("/", "\\")) or re.match(r"^[A-Za-z]:[\\/]", value) or ".." in value:
            errors.append(f".harness/manifest.json: command {slot} contains an absolute/parent path")
        if any(not token_pattern.fullmatch(token) for token in value.split(" ")):
            errors.append(f".harness/manifest.json: command {slot} has malformed tokens")

    managed = manifest.get("managed_artifacts")
    if (
        not isinstance(managed, list)
        or any(not isinstance(artifact, str) for artifact in managed)
        or managed != sorted(set(managed))
    ):
        errors.append(".harness/manifest.json: managed_artifacts must be sorted unique relative paths")
    else:
        for index, artifact in enumerate(managed):
            errors.extend(_artifact_errors(artifact, f"managed_artifacts[{index}]"))

    capabilities = manifest.get("capabilities")
    if not isinstance(capabilities, dict):
        errors.append(".harness/manifest.json: capabilities must be an object")
        capabilities = {}
    statuses = {"implemented", "partial", "deferred", "not_applicable"}
    for name, capability in capabilities.items():
        if not isinstance(capability, dict) or capability.get("status") not in statuses:
            errors.append(f".harness/manifest.json: invalid status for {name}")
            continue
        artifacts = capability.get("artifacts", [])
        if not isinstance(artifacts, list):
            errors.append(f".harness/manifest.json: artifacts must be a list for {name}")
        else:
            for index, artifact in enumerate(artifacts):
                errors.extend(_artifact_errors(artifact, f"{name}.artifacts[{index}]"))
        verify = capability.get("verify", [])
        if not isinstance(verify, list) or not verify:
            errors.append(f".harness/manifest.json: verify evidence missing for {name}")
            continue
        for entry in verify:
            if not isinstance(entry, str) or not entry:
                errors.append(f".harness/manifest.json: invalid verify entry for {name}")
            elif entry.startswith("inspect: "):
                if entry not in INSPECTIONS:
                    errors.append(f".harness/manifest.json: unsupported inspection {entry}")
            elif entry not in command_values:
                errors.append(f".harness/manifest.json: unresolved command evidence {entry}")

    deferred = manifest.get("deferred", [])
    if not isinstance(deferred, list):
        errors.append(".harness/manifest.json: deferred must be a list")
    else:
        for item in deferred:
            if (
                not isinstance(item, dict)
                or set(item) != {"capability", "reason", "next_step"}
                or any(not isinstance(item[field], str) or not item[field] for field in ("capability", "reason", "next_step"))
            ):
                errors.append(".harness/manifest.json: deferred entries need capability/reason/next_step")
    gaps = manifest.get("evidence_gaps")
    if not isinstance(gaps, list):
        errors.append(".harness/manifest.json: evidence_gaps must be a list")
    else:
        for index, item in enumerate(gaps):
            if not isinstance(item, dict) or set(item) != {"source_capability", "artifacts", "reason", "next_step"}:
                errors.append(f".harness/manifest.json: malformed evidence_gaps[{index}]")
                continue
            if any(not isinstance(item[field], str) or not item[field] for field in ("source_capability", "reason", "next_step")):
                errors.append(f".harness/manifest.json: malformed evidence_gaps[{index}] text")
            if not isinstance(item["artifacts"], list):
                errors.append(f".harness/manifest.json: malformed evidence_gaps[{index}] artifacts")
            else:
                for artifact_index, artifact in enumerate(item["artifacts"]):
                    errors.extend(_artifact_errors(artifact, f"evidence_gaps[{index}].artifacts[{artifact_index}]"))
    return manifest, errors




def _manifest_evidence(manifest: dict) -> list[str]:
    evidence: set[str] = set()
    for capability in manifest.get("capabilities", {}).values():
        for entry in capability.get("verify", []):
            if isinstance(entry, str):
                evidence.add(entry)
    return sorted(evidence)


def _inspection_errors(entry: str) -> list[str]:
    if entry == "inspect: links resolve":
        return _link_errors()
    if entry == "inspect: manifest matches observed artifacts":
        _, errors = _validate_manifest()
        return errors
    if entry == "inspect: adapter exposes the critical surface":
        required = (
            ROOT / "docs" / "index.md",
            ROOT / "scripts" / "run-tablet-emulator.bat",
        )
        return [f"interactive adapter artifact missing: {path.relative_to(ROOT)}" for path in required if not path.exists()]
    if entry == "inspect: findings have evidence and remediation":
        return _knowledge_errors()
    if entry == "inspect: generated paths have regeneration evidence":
        ignore = _read(ROOT / ".gitignore")
        required = ("android/**/build/", "android/.gradle/")
        return [f".gitignore: missing generated-path rule {rule}" for rule in required if rule not in ignore]
    if entry == "inspect: project has no interactive surface":
        return []
    return [f"unsupported inspection: {entry}"]



def _run_manifest_command(command: str) -> int:
    if command == "python scripts/repo.py doctor":
        print("SKIP: recursive doctor evidence")
        return 0
    parts = shlex.split(command)
    if parts and parts[0] == "python":
        parts[0] = sys.executable
    return _run(parts)


def command_setup() -> int:
    required = (
        ANDROID / "gradlew",
        ANDROID / "gradlew.bat",
        ANDROID / "gradle" / "wrapper" / "gradle-wrapper.jar",
        ROOT / "data" / "grammar_n5.json",
    )
    missing = [str(path.relative_to(ROOT)) for path in required if not path.exists()]
    if missing:
        print("Missing setup inputs: " + ", ".join(missing), file=sys.stderr)
        return 1
    print("Repository setup inputs are present; Gradle resolves dependencies on first build.")
    return 0


def command_dev() -> int:
    if os.name == "nt":
        return _run(["cmd.exe", "/c", str(ROOT / "scripts" / "run-tablet-emulator.bat"), "start"])
    print("Non-Windows host: building the debug APK as the Android development surface.")
    return _run_gradle([":app:assembleDebug", "--console=plain"])


def command_format() -> int:
    errors = _format_errors()
    if errors:
        print("Format check failed:")
        print("\n".join(errors))
        return 1
    print("Format check passed.")
    return 0


def command_check() -> int:
    return _run_gradle(["test", "--console=plain"])


def command_test() -> int:
    return command_check()


def command_eval() -> int:
    return _run_gradle(
        [
            ":app:testDebugUnitTest",
            "--tests",
            "dev.studycanvas.app.SmokePathTest",
            "--tests",
            "dev.studycanvas.app.grammar.GrammarDatasetTest",
            "--tests",
            "dev.studycanvas.app.checker.AnswerCheckerTest",
            "--tests",
            "dev.studycanvas.app.ink.InkGeometryTest",
            "--console=plain",
        ]
    )


def _gc_targets() -> list[Path]:
    return [
        ANDROID / ".gradle",
        ANDROID / ".kotlin",
        ANDROID / "build",
        ANDROID / "app" / "build",
        ROOT / "scripts" / "__pycache__",
    ]


def command_gc(apply: bool) -> int:
    targets = [path for path in _gc_targets() if path.exists()]
    if not targets:
        print("GC: no known generated workspace targets found.")
        return 0
    for path in targets:
        relative = path.relative_to(ROOT)
        if apply:
            if path.is_dir():
                shutil.rmtree(path)
            else:
                path.unlink()
            print(f"GC removed reproducible workspace output: {relative}")
        else:
            print(f"GC candidate (reproducible workspace output): {relative}")
    if not apply:
        print("Dry run only; pass --apply to remove these known generated targets.")
    return 0


def command_doctor() -> int:
    manifest, errors = _validate_manifest()
    errors.extend(_link_errors())
    errors.extend(_architecture_errors())
    errors.extend(_knowledge_errors())
    errors.extend(_ci_errors())
    errors.extend(_observability_errors())
    errors.extend(_format_errors())
    if errors:
        print("Doctor static checks failed:")
        print("\n".join(errors))
        return 1

    print("Doctor static checks passed.")
    failures = 0
    assert manifest is not None
    for evidence in _manifest_evidence(manifest):
        if evidence.startswith("inspect: "):
            inspection_errors = _inspection_errors(evidence)
            if inspection_errors:
                failures += 1
                print(f"FAIL: {evidence}")
                print("\n".join(inspection_errors))
            else:
                print(f"PASS: {evidence}")
            continue
        result = _run_manifest_command(evidence)
        if result != 0:
            failures += 1
            print(f"FAIL: {evidence} (exit {result})")
        else:
            print(f"PASS: {evidence}")
    return 1 if failures else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=COMMAND_SLOTS)
    parser.add_argument("--dry-run", action="store_true", help="report GC candidates without removing them")
    parser.add_argument("--apply", action="store_true", help="remove only known GC targets")
    args = parser.parse_args(argv)
    if args.dry_run and args.apply:
        parser.error("--dry-run and --apply are mutually exclusive")
    if args.command == "setup":
        return command_setup()
    if args.command == "dev":
        return command_dev()
    if args.command == "format":
        return command_format()
    if args.command == "check":
        return command_check()
    if args.command == "test":
        return command_test()
    if args.command == "eval":
        return command_eval()
    if args.command == "doctor":
        return command_doctor()
    return command_gc(apply=args.apply)


if __name__ == "__main__":
    raise SystemExit(main())
