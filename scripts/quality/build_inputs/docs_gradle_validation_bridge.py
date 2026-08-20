#!/usr/bin/env python3
"""Run documentation Gradle-task validation through one source-bound bridge."""

from __future__ import annotations

import hashlib
import builtins
import contextlib
import importlib
import importlib.util
import json
import os
import re
import subprocess
import sys
import sysconfig
from pathlib import Path
from types import ModuleType
from typing import Any


ROOT = Path(__file__).resolve().parents[3]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.quality.build_inputs.contracts import (  # noqa: E402
    BuildInputError,
    canonical_json_bytes,
    load_policy,
    sha256_file,
    validate_gradle_arguments,
    validate_protected_environment,
)


POLICY_PATH = ROOT / "config/quality/build-inputs.json"
FACADE_PATH = ROOT / "scripts/docs/validate.py"
RECEIPT_PATH = ROOT / "build/reports/build-inputs/docs-gradle-validation.json"
TASK_LINE = re.compile(r"^\s*(:?[A-Za-z0-9_-]+(?::[A-Za-z0-9_-]+)*)\s*(?:-\s.*)?$")
STDLIB_ROOT = Path(sysconfig.get_paths()["stdlib"]).resolve()


class BridgeError(RuntimeError):
    """The governed docs validation bridge cannot establish its closed evidence."""


def _git(*arguments: str) -> str:
    return subprocess.check_output(
        ["git", "-c", "core.hooksPath=/dev/null", *arguments],
        cwd=ROOT,
        text=True,
        stderr=subprocess.DEVNULL,
    ).strip()


def _require_clean_source() -> str:
    status = _git("status", "--short", "--untracked-files=all")
    if status:
        raise BridgeError("governed docs validation requires a clean tracked/untracked source tree")
    return _git("rev-parse", "HEAD")


def _parse_tasks(output: str) -> frozenset[str]:
    discovered: set[str] = set()
    for line in output.splitlines():
        match = TASK_LINE.fullmatch(line)
        if match is None:
            continue
        task = match.group(1)
        discovered.update((task, task.lstrip(":"), task.rsplit(":", 1)[-1]))
    if not discovered:
        raise BridgeError("Gradle task discovery returned no parseable task identities")
    return frozenset(discovered)


def _load_facade() -> ModuleType:
    spec = importlib.util.spec_from_file_location("gasstation_docs_validator_facade", FACADE_PATH)
    if spec is None or spec.loader is None:
        raise BridgeError("documentation facade cannot be loaded")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def _module_source(module: ModuleType) -> Path | None:
    raw = getattr(module, "__file__", None)
    if not isinstance(raw, str):
        return None
    path = Path(raw)
    if path.suffix in {".pyc", ".pyo"}:
        path = path.with_suffix(".py")
    try:
        return path.resolve(strict=True)
    except OSError:
        return None


def _require_docs_or_standard_library(module: ModuleType, name: str) -> None:
    path = _module_source(module)
    if path is None:
        return
    docs_root = (ROOT / "scripts/docs").resolve()
    try:
        path.relative_to(docs_root)
        return
    except ValueError:
        pass
    if "site-packages" in path.parts or "dist-packages" in path.parts:
        raise BridgeError(f"third-party module loaded by docs validator: {name}")
    try:
        path.relative_to(STDLIB_ROOT)
    except ValueError as error:
        raise BridgeError(f"non-docs module loaded by docs validator: {name} ({path})") from error


@contextlib.contextmanager
def _guarded_docs_runtime():
    """Expose only standard-library and repository docs modules during validation."""

    docs_root = (ROOT / "scripts/docs").resolve()
    removed: dict[str, ModuleType] = {}
    removed_attributes: list[tuple[ModuleType, str, ModuleType]] = []
    for name, module in list(sys.modules.items()):
        if not isinstance(module, ModuleType) or name == "__main__":
            continue
        path = _module_source(module)
        if path is None:
            continue
        repository_owned = path.is_relative_to(ROOT.resolve()) and not path.is_relative_to(docs_root)
        third_party = "site-packages" in path.parts or "dist-packages" in path.parts
        if repository_owned or third_party:
            removed[name] = module
            sys.modules.pop(name, None)
            parent_name, separator, attribute = name.rpartition(".")
            parent = sys.modules.get(parent_name) if separator else None
            if isinstance(parent, ModuleType) and getattr(parent, attribute, None) is module:
                removed_attributes.append((parent, attribute, module))
                delattr(parent, attribute)

    original_import = builtins.__import__
    original_import_module = importlib.import_module

    def guarded_import(name, globals=None, locals=None, fromlist=(), level=0):
        module = original_import(name, globals, locals, fromlist, level)
        _require_docs_or_standard_library(module, getattr(module, "__name__", name))
        if level:
            package = globals.get("__package__") if isinstance(globals, dict) else None
            absolute_name = importlib.util.resolve_name("." * level + name, package)
        else:
            absolute_name = name
        requested = sys.modules.get(absolute_name)
        if isinstance(requested, ModuleType):
            _require_docs_or_standard_library(requested, absolute_name)
        if fromlist:
            prefix = absolute_name
            for item in fromlist:
                candidate = sys.modules.get(f"{prefix}.{item}")
                if isinstance(candidate, ModuleType):
                    _require_docs_or_standard_library(candidate, f"{prefix}.{item}")
        return module

    def guarded_import_module(name, package=None):
        module = original_import_module(name, package)
        _require_docs_or_standard_library(module, getattr(module, "__name__", name))
        return module

    builtins.__import__ = guarded_import
    importlib.import_module = guarded_import_module
    try:
        yield
    finally:
        builtins.__import__ = original_import
        importlib.import_module = original_import_module
        for name, module in removed.items():
            sys.modules.setdefault(name, module)
        for parent, attribute, module in removed_attributes:
            if not hasattr(parent, attribute):
                setattr(parent, attribute, module)


def _closure_rows(before: set[str]) -> list[dict[str, Any]]:
    docs_root = (ROOT / "scripts/docs").resolve()
    repository = ROOT.resolve()
    candidates: dict[Path, ModuleType] = {}
    for name, module in list(sys.modules.items()):
        if not isinstance(module, ModuleType):
            continue
        path = _module_source(module)
        if path is None:
            continue
        try:
            path.relative_to(repository)
        except ValueError:
            if name not in before and "site-packages" in path.parts:
                raise BridgeError(f"third-party module loaded by docs validator: {name}")
            continue
        try:
            path.relative_to(docs_root)
        except ValueError as error:
            if name not in before and path != Path(__file__).resolve():
                raise BridgeError(f"repository module outside scripts/docs loaded by validator: {path.relative_to(repository)}") from error
            continue
        candidates[path] = module
    facade = FACADE_PATH.resolve()
    candidates.setdefault(facade, sys.modules["gasstation_docs_validator_facade"])
    rows: list[dict[str, Any]] = []
    inodes: set[tuple[int, int]] = set()
    for path in sorted(candidates, key=lambda value: value.relative_to(repository).as_posix()):
        relative = path.relative_to(repository).as_posix()
        if "/tests/" in f"/{relative}/" or "__pycache__" in path.parts:
            continue
        cursor = repository
        for part in path.relative_to(repository).parts:
            cursor = cursor / part
            if cursor.is_symlink():
                raise BridgeError(f"docs source path contains a symlink: {relative}")
        stat = path.stat()
        identity = (stat.st_dev, stat.st_ino)
        if identity in inodes:
            raise BridgeError(f"duplicate docs source inode: {relative}")
        inodes.add(identity)
        tracked = subprocess.run(
            ["git", "cat-file", "-e", f"HEAD:{relative}"],
            cwd=ROOT,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        if tracked.returncode:
            raise BridgeError(f"docs source is not tracked at HEAD: {relative}")
        data = path.read_bytes()
        rows.append({"path": relative, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)})
    discovered = sorted((ROOT / "scripts/docs/extensions").glob("*.py"))
    expected = {
        path.resolve()
        for path in discovered
        if path.name != "__init__.py" and path.is_file() and not path.is_symlink()
    }
    actual = {ROOT / row["path"] for row in rows if row["path"].startswith("scripts/docs/extensions/")}
    if expected != actual:
        raise BridgeError("extension discovery and executed source closure differ")
    return rows


def _aggregate(rows: list[dict[str, Any]]) -> str:
    return hashlib.sha256(canonical_json_bytes(rows)).hexdigest()


def run() -> dict[str, Any]:
    source_commit = _require_clean_source()
    policy = load_policy(POLICY_PATH, root=ROOT)
    policy_hash = sha256_file(POLICY_PATH)
    bridge_relative = Path(__file__).resolve().relative_to(ROOT).as_posix()
    bridge_hash = sha256_file(Path(__file__).resolve())
    static = {row["path"]: row["sha256"] for row in policy["staticSourceHashes"]}
    if static.get(bridge_relative) != bridge_hash:
        raise BridgeError("stable docs bridge hash differs from policy")
    docs = policy["docsValidation"]
    if docs["bridgePath"] != bridge_relative or docs["facadePath"] != "scripts/docs/validate.py":
        raise BridgeError("docs bridge/facade path differs from policy")

    compile_home = os.environ.get("JAVA_HOME_17_X64", "")
    runtime_home = os.environ.get("JAVA_HOME_21_X64", "")
    gradle_home = os.environ.get("GRADLE_USER_HOME", "")
    if not all((compile_home, runtime_home, gradle_home)):
        raise BridgeError("governed docs validation requires installer-owned Java and Gradle homes")
    validate_protected_environment(
        os.environ,
        compile_home=compile_home,
        runtime_home=runtime_home,
        gradle_home=gradle_home,
    )
    argv = [
        "./gradlew",
        "tasks",
        "--all",
        "--dependency-verification",
        "strict",
        "--no-configuration-cache",
        "--no-build-cache",
        "--warning-mode",
        "fail",
        "-Dorg.gradle.java.installations.auto-detect=false",
        "-Dorg.gradle.java.installations.auto-download=false",
        f"-Dorg.gradle.java.installations.paths={compile_home},{runtime_home}",
    ]
    validate_gradle_arguments(argv)
    result = subprocess.run(argv, cwd=ROOT, text=True, capture_output=True, timeout=120)
    if result.returncode:
        raise BridgeError("governed Gradle task discovery failed")
    tasks = _parse_tasks(result.stdout)

    before = set(sys.modules)
    with _guarded_docs_runtime():
        facade = _load_facade()
        validate_repository = getattr(facade, "validate_repository", None)
        if not callable(validate_repository):
            raise BridgeError("fixed validate_repository callable is missing")
        issues = validate_repository(ROOT, discovered_gradle_tasks=tasks)
        if not isinstance(issues, list) or any(not isinstance(item, str) for item in issues):
            raise BridgeError("validate_repository returned an invalid issue list")
        rows = _closure_rows(before)
    extension_order = [row["path"] for row in rows if row["path"].startswith("scripts/docs/extensions/")]
    receipt = {
        "argv": ["python3", bridge_relative, "--check-gradle-tasks"],
        "bridge": {"path": bridge_relative, "sha256": bridge_hash},
        "dynamicSources": {
            "aggregateSha256": _aggregate(rows),
            "count": len(rows),
            "extensions": extension_order,
            "manifest": rows,
        },
        "facade": {
            "callable": "validate_repository(root: pathlib.Path, *, discovered_gradle_tasks: frozenset[str] | None) -> list[str]",
            "path": "scripts/docs/validate.py",
        },
        "gradle": {
            "argv": argv,
            "discoveredTaskCount": len(tasks),
            "exitCode": result.returncode,
        },
        "policySha256": policy_hash,
        "schemaVersion": 1,
        "sourceCommit": source_commit,
        "validation": {"issueCount": len(issues), "result": "PASS" if not issues else "FAIL"},
    }
    RECEIPT_PATH.parent.mkdir(parents=True, exist_ok=True)
    RECEIPT_PATH.write_bytes(canonical_json_bytes(receipt))
    if issues:
        raise BridgeError("documentation validation failed:\n" + "\n".join(issues))
    return receipt


def main(argv: list[str] | None = None) -> int:
    arguments = sys.argv[1:] if argv is None else argv
    if arguments != ["--check-gradle-tasks"]:
        print("ERROR: bridge accepts only --check-gradle-tasks", file=sys.stderr)
        return 2
    try:
        receipt = run()
    except (BridgeError, BuildInputError, OSError, subprocess.SubprocessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(
        "docs-validation: PASS "
        f"sources={receipt['dynamicSources']['count']} "
        f"closure={receipt['dynamicSources']['aggregateSha256']}",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
