#!/usr/bin/env python3
"""Route, bind, parse, capture, and verify deterministic JVM PIT evidence."""

from __future__ import annotations

import argparse
import hashlib
import os
import platform
import re
import shutil
import stat
import subprocess
import sys
from pathlib import Path
from typing import Any, Iterable

QUALITY_ROOT = Path(__file__).resolve().parent
if str(QUALITY_ROOT) not in sys.path:
    sys.path.insert(0, str(QUALITY_ROOT))

from pitest_policy import (  # noqa: E402
    GitCommand,
    GitExecutor,
    MutationPolicyError,
    canonical_json_bytes,
    compare_floor,
    compare_no_coverage,
    parse_pitest_xml,
    read_strict_json,
    receipt,
    route_changed_paths,
)
from pitest_policy.contracts import (  # noqa: E402
    build_capture_candidate,
    build_capture_evidence_manifest,
    build_capture_receipt,
    validate_linux_profile,
)
from verify_coverage import parse_package_declaration  # noqa: E402


REPOSITORY_ROOT = QUALITY_ROOT.parents[1]
POLICY_PATH = REPOSITORY_ROOT / "config/quality/mutation-policy.json"
BASELINE_PATH = REPOSITORY_ROOT / "config/quality/mutation-baseline.json"
REPORT_ROOT = REPOSITORY_ROOT / "build/reports/pitest"
ROUTE_PATH = REPORT_ROOT / "route.json"
TASKS_PATH = REPORT_ROOT / "tasks.txt"
ROUTE_RECEIPT_PATH = REPORT_ROOT / "route-receipt.json"
ATTEMPT_PATH = REPORT_ROOT / "attempt.json"
COMPLETION_PATH = REPORT_ROOT / "completion.json"
MEASUREMENT_PATH = REPORT_ROOT / "measurement.json"
SUMMARY_PATH = REPORT_ROOT / "verification-summary.json"
FINAL_RECEIPT_PATH = REPORT_ROOT / "verification-receipt.json"
CAPTURE_RECEIPT_ROOT = REPOSITORY_ROOT / "config/quality/mutation-captures"
BOOTSTRAP_MARKER = "sealed-v1"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_bytes(path: Path) -> bytes:
    try:
        return path.read_bytes()
    except OSError as error:
        raise MutationPolicyError(f"required evidence is missing: {relative(path)}") from error


def relative(path: Path) -> str:
    resolved = path.resolve(strict=False)
    try:
        return resolved.relative_to(REPOSITORY_ROOT).as_posix()
    except ValueError as error:
        raise MutationPolicyError("evidence path escapes repository") from error


def write_atomic(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = canonical_json_bytes(payload)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_bytes(data)
    os.replace(temporary, path)


def load_policy() -> tuple[dict[str, Any], bytes, str]:
    raw = read_bytes(POLICY_PATH)
    value = read_strict_json(raw)
    if not isinstance(value, dict) or value.get("schemaVersion") != 1:
        raise MutationPolicyError("mutation policy schemaVersion must be integer 1")
    required = {
        "acceptedStatuses", "bootstrapProfiles", "canonicalGradleFlags", "enforcementPhase",
        "capturePolicy", "executionEnvironmentPolicy", "gitObjectViewPolicy", "identitySchemas",
        "linuxHistoricalComparator", "modules", "noCoverageMode", "pitest", "rejectedStatuses",
        "routing", "schemaVersion",
    }
    if set(value) != required:
        raise MutationPolicyError("mutation policy keys differ from the closed schema")
    if sorted(value["modules"]) != ["location", "settings", "station"]:
        raise MutationPolicyError("mutation policy module set differs from exact three")
    if value["enforcementPhase"] not in {"observe", "blocking"}:
        raise MutationPolicyError("mutation policy enforcementPhase is invalid")
    if value["acceptedStatuses"] != ["KILLED", "NO_COVERAGE", "SURVIVED"]:
        raise MutationPolicyError("mutation policy accepted status set differs")
    if value["rejectedStatuses"] != [
        "EQUIVALENT", "MEMORY_ERROR", "NON_VIABLE", "NOT_STARTED",
        "RUN_ERROR", "STARTED", "TIMED_OUT",
    ]:
        raise MutationPolicyError("mutation policy rejected status set differs")
    validate_linux_profile(value["bootstrapProfiles"].get("linux-x86_64", {}))
    if value["linuxHistoricalComparator"] != {
        "establishmentMode": "reviewed-recapture-transition-only",
        "initialState": "NOT_ESTABLISHED",
        "states": ["ESTABLISHED", "NOT_ESTABLISHED"],
    }:
        raise MutationPolicyError("Linux historical comparator policy differs")
    if value["capturePolicy"].get("schema") != "acyclic-candidate-and-separate-receipt-v1":
        raise MutationPolicyError("mutation capture policy is not acyclic")
    return value, raw, sha256(raw)


def validate_tool_profile(policy: dict[str, Any]) -> tuple[dict[str, Any], Path]:
    if os.environ.get("GASSTATION_PITEST_BOOTSTRAP") != BOOTSTRAP_MARKER:
        raise MutationPolicyError("canonical PIT evidence requires the sealed absolute env/Bash entry")
    system = platform.system()
    machine = platform.machine()
    if (system, machine) == ("Darwin", "arm64"):
        # Apple's fixed /usr/bin/python3 launcher adds these toolchain variables
        # after env -i. They are deleted before any policy/Git operation and are
        # never admitted to an evidence or child-process allowlist.
        for name in ("CPATH", "LIBRARY_PATH", "MANPATH", "SDKROOT", "__CF_USER_TEXT_ENCODING"):
            os.environ.pop(name, None)
    environment_policy = policy["executionEnvironmentPolicy"]
    allowed = set(environment_policy["allowedEvidenceNames"]) | {"GASSTATION_PITEST_BOOTSTRAP"}
    unexpected = sorted(name for name in os.environ if name not in allowed)
    if unexpected:
        raise MutationPolicyError(f"sealed evidence environment contains unexpected names: {','.join(unexpected)}")
    fixed = {"LANG": "C", "LC_ALL": "C", "TZ": "UTC", "TERM": "dumb", "CI": "true", "PYTHONDONTWRITEBYTECODE": "1"}
    for name, expected in fixed.items():
        if os.environ.get(name) != expected:
            raise MutationPolicyError(f"sealed evidence environment literal differs: {name}")
    profile_name = {
        ("Darwin", "arm64"): "darwin-arm64",
        ("Linux", "x86_64"): "linux-x86_64",
    }.get((system, machine))
    if profile_name is None:
        raise MutationPolicyError(f"no reviewed bootstrap profile for {system}/{machine}")
    profile = policy["bootstrapProfiles"].get(profile_name)
    if not isinstance(profile, dict):
        raise MutationPolicyError(f"no reviewed bootstrap profile for {profile_name}")
    tools: dict[str, dict[str, Any]] = profile["tools"]
    image_identity: dict[str, str] | None = None
    if profile_name == "linux-x86_64":
        validate_linux_profile(profile)
        image_identity = _linux_image_identity(profile)
    observations = {
        name: _observe_bootstrap_tool(name, spec, fixed_profile=profile_name == "darwin-arm64")
        for name, spec in sorted(tools.items())
    }
    evidence = {
        "profile": profile_name,
        "profileSha256": sha256(canonical_json_bytes(profile)),
        "imageIdentity": image_identity,
        "observedTools": observations,
        "observedToolBundleSha256": sha256(canonical_json_bytes(observations)),
        "environmentPolicy": environment_policy["policyVersion"],
    }
    return evidence, Path(tools["git"]["path"])


def validate_bootstrap(policy: dict[str, Any], java_home: str) -> tuple[dict[str, Any], GitExecutor]:
    evidence, git_path = validate_tool_profile(policy)
    java = _observe_java_home(java_home)
    git = GitExecutor(
        REPOSITORY_ROOT,
        git_path=git_path,
        home=Path(os.environ["HOME"]),
        tmpdir=Path(os.environ["TMPDIR"]),
    )
    object_view = git.assert_original_full_history()
    return {
        **evidence,
        "java": java,
        "gitObjectView": object_view,
    }, git


def consume_java_selector(policy: dict[str, Any], selector: str) -> str:
    validate_tool_profile(policy)
    if selector != "build/quality/pitest-runtime/bootstrap/java-home.selector":
        raise MutationPolicyError("CI Java selector path differs from the fixed carrier")
    path = REPOSITORY_ROOT / selector
    if path.is_symlink():
        raise MutationPolicyError("CI Java selector must not be a symlink")
    try:
        metadata = path.stat()
        data = path.read_bytes()
    except OSError as error:
        raise MutationPolicyError("CI Java selector is missing") from error
    mode = stat.S_IMODE(metadata.st_mode)
    if not stat.S_ISREG(metadata.st_mode) or mode & 0o077 != 0 or mode & 0o600 == 0:
        raise MutationPolicyError("CI Java selector must be regular and 0600-or-stricter")
    if data.count(b"\n") != 1 or not data.endswith(b"\n"):
        raise MutationPolicyError("CI Java selector must contain exactly one newline-terminated line")
    raw = data[:-1]
    if not raw or any(byte <= 0x20 or byte == 0x7F for byte in raw):
        raise MutationPolicyError("CI Java selector contains whitespace/control or is empty")
    try:
        java_home = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        raise MutationPolicyError("CI Java selector is not UTF-8") from error
    if not java_home.startswith("/"):
        raise MutationPolicyError("CI Java selector must contain one absolute path")
    _observe_java_home(java_home)
    path.unlink()
    if path.exists() or path.is_symlink():
        raise MutationPolicyError("CI Java selector was not deleted after one read")
    return java_home


def _observe_java_home(raw_java_home: str) -> dict[str, Any]:
    if not isinstance(raw_java_home, str) or not raw_java_home.startswith("/"):
        raise MutationPolicyError("mutation Java home must be an absolute selector")
    home = Path(raw_java_home)
    if home.is_symlink() or not home.is_dir():
        raise MutationPolicyError("mutation Java home must be a non-symlink directory")
    executable = home / "bin/java"
    if executable.is_symlink() or not executable.is_file() or not os.access(executable, os.X_OK):
        raise MutationPolicyError("mutation Java executable must be a regular non-symlink executable")
    completed = subprocess.run(
        [str(executable), "-XshowSettings:properties", "-version"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        env={"LANG": "C", "LC_ALL": "C", "TZ": "UTC", "TERM": "dumb"},
        check=False,
    )
    if completed.returncode != 0:
        raise MutationPolicyError("mutation Java identity command failed")
    text = completed.stdout.decode("utf-8", errors="strict")
    version_match = re.search(r"^\s*java\.version = ([^\s]+)\s*$", text, re.MULTILINE)
    vendor_match = re.search(r"^\s*java\.vendor = (.+?)\s*$", text, re.MULTILINE)
    if version_match is None or vendor_match is None:
        raise MutationPolicyError("mutation Java identity output is malformed")
    version = version_match.group(1)
    vendor = vendor_match.group(1)
    if not (version == "21" or version.startswith("21.")):
        raise MutationPolicyError("mutation Java major must be 21")
    if vendor not in {"Eclipse Adoptium", "Temurin", "Adoptium"}:
        raise MutationPolicyError("mutation Java vendor must normalize to Eclipse Adoptium/Temurin")
    return {
        "major": 21,
        "vendorFamily": "Eclipse Adoptium/Temurin",
        "toolchainRole": "mutation-runtime",
        "runtimeVersion": version,
        "executableSha256": sha256(executable.read_bytes()),
    }


def host_neutral_mutation_identity(policy: dict[str, Any]) -> dict[str, Any]:
    modules = policy["modules"]
    return {
        "schema": "host-neutral-mutation-identity-v1",
        "pitestPlugin": policy["pitest"]["pluginVersion"],
        "pitestEngine": policy["pitest"]["pitestVersion"],
        "java": {
            "major": 21,
            "vendorFamily": "Eclipse Adoptium/Temurin",
            "toolchainRole": "mutation-runtime",
        },
        "targets": {
            name: {
                "targetClasses": module["targetClasses"],
                "targetTests": module["targetTests"],
                "sourceSets": ["main", "test"],
            }
            for name, module in sorted(modules.items())
        },
        "reportGeneration": policy["pitest"],
    }


def route_per_run_provenance(policy: dict[str, Any], bootstrap: dict[str, Any]) -> dict[str, Any]:
    profile = bootstrap["profile"]
    return {
        "schema": "per-run-execution-provenance-route-v1",
        "selectedProfile": profile,
        "profileDefinitionSha256": bootstrap["profileSha256"],
        "imageIdentity": bootstrap["imageIdentity"],
        "observedToolBundleSha256": bootstrap["observedToolBundleSha256"],
        "javaExecutableSha256": bootstrap["java"]["executableSha256"],
        "javaRuntimeVersion": bootstrap["java"]["runtimeVersion"],
    }


def _linux_image_identity(profile: dict[str, Any]) -> dict[str, str]:
    metadata = Path("/etc/environment")
    try:
        assignments = metadata.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise MutationPolicyError("Linux runner image metadata is missing") from error
    values: dict[str, str] = {}
    for line in assignments:
        match = re.fullmatch(r"(ImageOS|ImageVersion)=([^\s]+)", line)
        if match:
            name, value = match.groups()
            if name in values:
                raise MutationPolicyError(f"Linux runner image metadata is duplicated: {name}")
            values[name] = value.strip('"')
    expected = profile["image"]
    for name in ("ImageOS", "ImageVersion"):
        if values.get(name) != expected[name]:
            raise MutationPolicyError(f"Linux runner image metadata differs: {name}")
    return dict(expected)


def _observe_bootstrap_tool(name: str, spec: dict[str, Any], *, fixed_profile: bool) -> dict[str, Any]:
    entry = Path(spec["locator"] if name == "python" and "locator" in spec else spec["path"])
    if name == "python" and "locator" in spec:
        if not entry.is_symlink() or os.readlink(entry) != spec["linkTarget"]:
            raise MutationPolicyError("Linux Python locator must be the exact one-hop python3.12 symlink")
        path = Path(spec["path"])
    else:
        if entry.is_symlink():
            raise MutationPolicyError(f"bootstrap tool is unexpectedly symlinked: {name}")
        path = entry
    try:
        metadata = path.lstat()
    except OSError as error:
        raise MutationPolicyError(f"bootstrap tool is missing: {name}") from error
    if not stat.S_ISREG(metadata.st_mode) or path.is_symlink():
        raise MutationPolicyError(f"bootstrap tool must be a regular non-symlink: {name}")
    numeric_mode = stat.S_IMODE(metadata.st_mode)
    if numeric_mode & 0o111 == 0:
        raise MutationPolicyError(f"bootstrap tool has no executable bit: {name}")
    command = {
        "env": [str(path), "--version"],
        "bash": [str(path), "--version"],
        "python": [str(path), "--version"],
        "git": [str(path), "--version"],
    }[name]
    completed = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        env={"LANG": "C", "LC_ALL": "C", "TZ": "UTC", "TERM": "dumb"},
        check=False,
    )
    if completed.returncode != 0 and not (fixed_profile and name == "env"):
        raise MutationPolicyError(f"bootstrap tool version command failed: {name}")
    observation = {
        "entryPath": str(entry),
        "resolvedPath": str(path),
        "entryType": "symlink" if entry.is_symlink() else "regular",
        "fileType": "regular",
        "mode": f"{numeric_mode:04o}",
        "sha256": sha256(path.read_bytes()),
        "versionSha256": sha256(completed.stdout),
    }
    if fixed_profile:
        expected = {
            "fileType": spec["fileType"],
            "mode": spec["mode"],
            "sha256": spec["sha256"],
            "versionSha256": spec["versionSha256"],
        }
        actual = {key: observation[key] for key in expected}
        if actual != expected:
            raise MutationPolicyError(f"fixed Darwin bootstrap identity differs: {name}")
    return observation


def assert_clean_relevant_worktree(git: GitExecutor) -> None:
    status = git.bytes(GitCommand.STATUS, "--porcelain=v1", "-z", "--untracked-files=all")
    relevant: list[str] = []
    for record in status.split(b"\0"):
        if not record:
            continue
        text = record.decode("utf-8", errors="strict")
        path = text[3:].split(" -> ")[-1]
        if path.startswith(("build/", ".gradle/", ".superpowers/")) or "/build/" in path:
            continue
        relevant.append(path)
    if relevant:
        raise MutationPolicyError(f"mutation evidence requires a clean relevant worktree: {','.join(sorted(relevant))}")


def parse_name_status(data: bytes) -> list[tuple[str, str | None, str | None]]:
    tokens = data.split(b"\0")
    if tokens and tokens[-1] == b"":
        tokens.pop()
    result: list[tuple[str, str | None, str | None]] = []
    index = 0
    while index < len(tokens):
        status_token = tokens[index].decode("ascii", errors="strict")
        index += 1
        status = status_token[0]
        if status in {"R", "C"}:
            if index + 1 >= len(tokens):
                raise MutationPolicyError("truncated rename/copy name-status record")
            old = tokens[index].decode("utf-8", errors="strict")
            new = tokens[index + 1].decode("utf-8", errors="strict")
            index += 2
            result.append((status, old, new))
        else:
            if index >= len(tokens):
                raise MutationPolicyError("truncated name-status record")
            path = tokens[index].decode("utf-8", errors="strict")
            index += 1
            result.append((status, None if status == "A" else path, None if status == "D" else path))
    return result


def route(event: str, base: str | None, java_home: str) -> dict[str, Any]:
    policy, policy_raw, policy_hash = load_policy()
    bootstrap, git = validate_bootstrap(policy, java_home)
    assert_clean_relevant_worktree(git)
    for stale in (ROUTE_PATH, TASKS_PATH, ROUTE_RECEIPT_PATH):
        safe_remove(stale)
    source_commit = git.text(GitCommand.REV_PARSE, "--verify", "HEAD^{commit}")
    if not re.fullmatch(r"[0-9a-f]{40}", source_commit):
        raise MutationPolicyError("HEAD is not an exact commit")
    merge_base: str | None = None
    changes: list[tuple[str, str | None, str | None]] = []
    if event == "pull-request":
        if base is None or not re.fullmatch(r"[0-9a-f]{40}", base) or base == "0" * 40:
            raise MutationPolicyError("pull-request routing requires an exact nonzero base SHA")
        merge_base = git.text(GitCommand.MERGE_BASE, base, source_commit)
        if not re.fullmatch(r"[0-9a-f]{40}", merge_base):
            raise MutationPolicyError("pull-request merge base is invalid or unrelated")
        raw = git.bytes(
            GitCommand.DIFF,
            "--name-status", "-z", "--find-renames=50%", "--find-copies=50%",
            "--no-ext-diff", "--no-textconv", f"{merge_base}...{source_commit}",
        )
        changes = parse_name_status(raw)
        selected = route_changed_paths(changes)
    elif event in {"local-all", "main", "tag", "schedule"}:
        selected = sorted(policy["modules"])
    else:
        raise MutationPolicyError(f"unsupported mutation event: {event}")
    tasks = [policy["modules"][name]["pitestTask"] for name in selected]
    neutral = host_neutral_mutation_identity(policy)
    per_run = route_per_run_provenance(policy, bootstrap)
    value = {
        "schemaVersion": 1,
        "event": event,
        "sourceCommit": source_commit,
        "baseCommit": base,
        "mergeBase": merge_base,
        "policySha256": policy_hash,
        "environmentPolicy": policy["executionEnvironmentPolicy"]["policyVersion"],
        "gitObjectViewPolicy": policy["gitObjectViewPolicy"]["policyVersion"],
        "bootstrap": bootstrap,
        "hostNeutralMutationIdentity": neutral,
        "hostNeutralMutationIdentitySha256": sha256(canonical_json_bytes(neutral)),
        "perRunExecutionProvenance": per_run,
        "perRunExecutionProvenanceSha256": sha256(canonical_json_bytes(per_run)),
        "changes": [
            {"status": status, "oldPath": old, "newPath": new}
            for status, old, new in changes
        ],
        "selectedModules": selected,
        "selectedTasks": tasks,
        "status": "selected" if selected else "not-applicable",
    }
    REPORT_ROOT.mkdir(parents=True, exist_ok=True)
    write_atomic(ROUTE_PATH, value)
    TASKS_PATH.write_text("".join(f"{task}\n" for task in tasks), encoding="utf-8")
    route_raw = read_bytes(ROUTE_PATH)
    tasks_raw = read_bytes(TASKS_PATH)
    write_atomic(
        ROUTE_RECEIPT_PATH,
        receipt(
            "pitest-route-receipt-v1",
            {"policy": policy_raw, "route": route_raw, "tasks": tasks_raw},
            sourceCommit=source_commit,
            status=value["status"],
            bootstrap=bootstrap,
        ),
    )
    return value


def validate_route(java_home: str) -> tuple[dict[str, Any], dict[str, Any], bytes, str]:
    policy, policy_raw, policy_hash = load_policy()
    bootstrap, git = validate_bootstrap(policy, java_home)
    route_raw = read_bytes(ROUTE_PATH)
    tasks_raw = read_bytes(TASKS_PATH)
    route_value = read_strict_json(route_raw)
    receipt_value = read_strict_json(read_bytes(ROUTE_RECEIPT_PATH))
    if not isinstance(route_value, dict) or not isinstance(receipt_value, dict):
        raise MutationPolicyError("route evidence must be JSON objects")
    expected = receipt(
        "pitest-route-receipt-v1",
        {"policy": policy_raw, "route": route_raw, "tasks": tasks_raw},
        sourceCommit=route_value.get("sourceCommit"),
        status=route_value.get("status"),
        bootstrap=route_value.get("bootstrap"),
    )
    if receipt_value != expected or route_value.get("bootstrap") != bootstrap:
        raise MutationPolicyError("route receipt differs from current route/tool/object view")
    if git.text(GitCommand.REV_PARSE, "--verify", "HEAD^{commit}") != route_value.get("sourceCommit"):
        raise MutationPolicyError("route source commit is stale")
    expected_tasks = [policy["modules"][name]["pitestTask"] for name in route_value.get("selectedModules", [])]
    if tasks_raw.decode("utf-8", errors="strict") != "".join(f"{task}\n" for task in expected_tasks):
        raise MutationPolicyError("tasks projection differs from route")
    return policy, route_value, policy_raw, policy_hash


def safe_remove(path: Path, *, allow_directory: bool = False) -> None:
    resolved_parent = path.parent.resolve(strict=False)
    if not resolved_parent.is_relative_to(REPOSITORY_ROOT):
        raise MutationPolicyError("cleanup path escapes repository")
    if path.is_symlink():
        raise MutationPolicyError(f"cleanup path is symlinked: {relative(path)}")
    if path.is_dir():
        if not allow_directory:
            raise MutationPolicyError(f"cleanup directory is not allowlisted: {relative(path)}")
        shutil.rmtree(path)
    elif path.exists():
        path.unlink()


def attempt(java_home: str) -> dict[str, Any]:
    policy, route_value, policy_raw, policy_hash = validate_route(java_home)
    if route_value["status"] != "selected":
        raise MutationPolicyError("not-applicable route must not create an attempt")
    for name in route_value["selectedModules"]:
        module = policy["modules"][name]
        safe_remove(REPOSITORY_ROOT / module["reportPath"], allow_directory=False)
        report_dir = (REPOSITORY_ROOT / module["reportPath"]).parent
        if report_dir.exists():
            safe_remove(report_dir, allow_directory=True)
        safe_remove(REPOSITORY_ROOT / module["configurationPath"])
    for path in (ATTEMPT_PATH, COMPLETION_PATH, MEASUREMENT_PATH, SUMMARY_PATH, FINAL_RECEIPT_PATH):
        safe_remove(path)
    route_raw = read_bytes(ROUTE_PATH)
    route_receipt_raw = read_bytes(ROUTE_RECEIPT_PATH)
    tasks_raw = read_bytes(TASKS_PATH)
    per_run = {
        **route_value["perRunExecutionProvenance"],
        "schema": "per-run-execution-provenance-attempt-v1",
        "routeReceiptSha256": sha256(route_receipt_raw),
    }
    value = receipt(
        "pitest-attempt-v1",
        {"policy": policy_raw, "route": route_raw, "routeReceipt": route_receipt_raw, "tasks": tasks_raw},
        sourceCommit=route_value["sourceCommit"],
        selectedTasks=route_value["selectedTasks"],
        gradleFlags=policy["canonicalGradleFlags"],
        environmentPolicy=policy["executionEnvironmentPolicy"]["policyVersion"],
        gitObjectViewPolicy=policy["gitObjectViewPolicy"]["policyVersion"],
        bootstrap=route_value["bootstrap"],
        hostNeutralMutationIdentity=route_value["hostNeutralMutationIdentity"],
        hostNeutralMutationIdentitySha256=route_value["hostNeutralMutationIdentitySha256"],
        perRunExecutionProvenance=per_run,
        perRunExecutionProvenanceSha256=sha256(canonical_json_bytes(per_run)),
    )
    write_atomic(ATTEMPT_PATH, value)
    return value


def source_lookup(module: dict[str, Any], class_name: str, source_file: str) -> str:
    source_root = REPOSITORY_ROOT / module["sourceRoot"]
    package = class_name.rsplit(".", 1)[0]
    matches: list[Path] = []
    for candidate in source_root.rglob(source_file):
        if candidate.is_symlink() or not candidate.is_file():
            continue
        if parse_package_declaration(candidate.read_bytes(), candidate.suffix) == package:
            matches.append(candidate)
    if len(matches) != 1:
        raise MutationPolicyError(f"PIT sourceFile resolution is not exact: {class_name}/{source_file}")
    return relative(matches[0])


def class_lookup(module: dict[str, Any], class_name: str) -> tuple[str, bytes]:
    internal = class_name.replace(".", "/") + ".class"
    roots = [
        REPOSITORY_ROOT / module["modulePath"].removeprefix(":").replace(":", "/") / "build/classes/kotlin/main",
        REPOSITORY_ROOT / module["modulePath"].removeprefix(":").replace(":", "/") / "build/classes/java/main",
    ]
    matches = [root / internal for root in roots if (root / internal).is_file() and not (root / internal).is_symlink()]
    if len(matches) != 1:
        raise MutationPolicyError(f"compiled mutatedClass resolution is not exact: {class_name}")
    return relative(matches[0]), matches[0].read_bytes()


def module_report(policy: dict[str, Any], name: str, route_value: dict[str, Any]) -> dict[str, Any]:
    module = policy["modules"][name]
    report_path = REPOSITORY_ROOT / module["reportPath"]
    report = parse_pitest_xml(
        read_bytes(report_path),
        module=name,
        package_root=module["packageRoot"],
        source_lookup=lambda class_name, source_file: source_lookup(module, class_name, source_file),
        class_lookup=lambda class_name: class_lookup(module, class_name),
    )
    html_root = report_path.parent
    html_records = []
    for path in sorted(html_root.rglob("*.html")):
        if path.is_symlink() or not path.is_file():
            raise MutationPolicyError("PIT HTML report contains a symlink or non-file")
        html_records.append({"path": relative(path), "sha256": sha256(path.read_bytes())})
    if not html_records:
        raise MutationPolicyError(f"PIT HTML report is missing for {name}")
    configuration_path = REPOSITORY_ROOT / module["configurationPath"]
    configuration_raw = read_bytes(configuration_path)
    configuration = read_strict_json(configuration_raw)
    if not isinstance(configuration, dict):
        raise MutationPolicyError("PIT configuration evidence must be an object")
    if configuration.get("projectPath") != module["modulePath"] or configuration.get("enforcementPhase") != policy["enforcementPhase"]:
        raise MutationPolicyError(f"PIT configuration identity differs for {name}")
    if configuration.get("policySha256") != route_value["policySha256"]:
        raise MutationPolicyError(f"PIT configuration policy identity differs for {name}")
    if configuration.get("hostNeutralMutationIdentity") != route_value["hostNeutralMutationIdentity"]:
        raise MutationPolicyError(f"PIT configuration host-neutral identity differs for {name}")
    per_run = configuration.get("perRunExecutionProvenance")
    route_per_run = route_value["perRunExecutionProvenance"]
    if not isinstance(per_run, dict):
        raise MutationPolicyError(f"PIT configuration per-run provenance is missing for {name}")
    for key in (
        "selectedProfile", "profileDefinitionSha256", "imageIdentity",
        "observedToolBundleSha256", "javaExecutableSha256", "javaRuntimeVersion",
    ):
        if per_run.get(key) != route_per_run.get(key):
            raise MutationPolicyError(f"PIT configuration per-run provenance differs for {name}: {key}")
    if per_run.get("routeReceiptSha256") != sha256(read_bytes(ROUTE_RECEIPT_PATH)):
        raise MutationPolicyError(f"PIT configuration route receipt identity differs for {name}")
    return {
        "module": name,
        "configurationPath": module["configurationPath"],
        "configurationSha256": sha256(configuration_raw),
        "reportPath": module["reportPath"],
        "rawSha256": report.raw_sha256,
        "semanticSha256": report.semantic_sha256,
        "counters": report.counters,
        "packages": report.package_counters,
        "classes": report.class_counters,
        "rationals": report.rational_summary(),
        "records": [
            {
                "sourcePath": item.source_path,
                "classPath": item.class_path,
                "classSha256": item.class_sha256,
                "mutatedClass": item.mutated_class,
                "mutatedMethod": item.mutated_method,
                "methodDescription": item.method_description,
                "mutator": item.mutator,
                "indexes": list(item.indexes),
                "status": item.status,
            }
            for item in report.records
        ],
        "html": html_records,
    }


def complete(java_home: str) -> dict[str, Any]:
    policy, route_value, policy_raw, policy_hash = validate_route(java_home)
    attempt_raw = read_bytes(ATTEMPT_PATH)
    attempt_value = read_strict_json(attempt_raw)
    if not isinstance(attempt_value, dict) or attempt_value.get("sourceCommit") != route_value["sourceCommit"]:
        raise MutationPolicyError("attempt differs from route source")
    reports = [module_report(policy, name, route_value) for name in route_value["selectedModules"]]
    neutral = route_value["hostNeutralMutationIdentity"]
    configuration_hashes = {
        report["module"]: report["configurationSha256"] for report in reports
    }
    route_per_run = route_value["perRunExecutionProvenance"]
    per_run = {
        "schema": "per-run-execution-provenance-completion-v1",
        **{key: value for key, value in route_per_run.items() if key != "schema"},
        "routeReceiptSha256": sha256(read_bytes(ROUTE_RECEIPT_PATH)),
        "configurationSha256ByModule": configuration_hashes,
    }
    value = receipt(
        "pitest-completion-v1",
        {
            "attempt": attempt_raw,
            "policy": policy_raw,
            "route": read_bytes(ROUTE_PATH),
            "routeReceipt": read_bytes(ROUTE_RECEIPT_PATH),
            "tasks": read_bytes(TASKS_PATH),
        },
        sourceCommit=route_value["sourceCommit"],
        selectedTasks=route_value["selectedTasks"],
        gradleExit=0,
        hostNeutralMutationIdentity=neutral,
        hostNeutralMutationIdentitySha256=sha256(canonical_json_bytes(neutral)),
        perRunExecutionProvenance=per_run,
        perRunExecutionProvenanceSha256=sha256(canonical_json_bytes(per_run)),
        reports=reports,
    )
    write_atomic(COMPLETION_PATH, value)
    return value


def git_configuration_identity(git: GitExecutor) -> dict[str, Any]:
    prefixes = [
        "build-logic/convention/src/main", "build-logic/convention/build.gradle.kts",
        "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/libs.versions.toml",
        "gradlew", "gradlew.bat", "gradle/wrapper", "gradle/verification-metadata.xml",
        "domain/station/build.gradle.kts", "domain/location/build.gradle.kts", "domain/settings/build.gradle.kts",
        "scripts/quality/run_pitest.sh",
    ]
    raw = git.bytes(GitCommand.LS_TREE, "-rz", "HEAD", "--", *prefixes)
    records: list[dict[str, str]] = []
    for entry in raw.split(b"\0"):
        if not entry:
            continue
        metadata, path_raw = entry.split(b"\t", 1)
        mode, object_type, object_id = metadata.decode("ascii").split(" ")
        if object_type != "blob":
            continue
        records.append({"path": path_raw.decode("utf-8"), "mode": mode, "blob": object_id})
    records.sort(key=lambda item: item["path"])
    return {"records": records, "sha256": sha256(canonical_json_bytes(records))}


def measure(java_home: str) -> dict[str, Any]:
    policy, route_value, policy_raw, policy_hash = validate_route(java_home)
    completion = read_strict_json(read_bytes(COMPLETION_PATH))
    if not isinstance(completion, dict) or completion.get("sourceCommit") != route_value["sourceCommit"]:
        raise MutationPolicyError("completion differs from current route")
    _, git = validate_bootstrap(policy, java_home)
    value = {
        "schemaVersion": 1,
        "sourceCommit": route_value["sourceCommit"],
        "policySha256": policy_hash,
        "enforcementPhase": policy["enforcementPhase"],
        "observationGitConfig": git_configuration_identity(git),
        "completionSha256": sha256(read_bytes(COMPLETION_PATH)),
        "routeSha256": sha256(read_bytes(ROUTE_PATH)),
        "routeReceiptSha256": sha256(read_bytes(ROUTE_RECEIPT_PATH)),
        "hostNeutralMutationIdentity": completion["hostNeutralMutationIdentity"],
        "hostNeutralMutationIdentitySha256": completion["hostNeutralMutationIdentitySha256"],
        "perRunExecutionProvenance": completion["perRunExecutionProvenance"],
        "perRunExecutionProvenanceSha256": completion["perRunExecutionProvenanceSha256"],
        "reports": completion["reports"],
    }
    write_atomic(MEASUREMENT_PATH, value)
    return value


def _initial_capture_summary(policy: dict[str, Any], measurement: dict[str, Any]) -> dict[str, Any]:
    violations: list[str] = []
    for report in measurement["reports"]:
        floor = policy["modules"][report["module"]]["floorPercent"]
        if floor is not None and not compare_floor(
            report["counters"]["KILLED"], report["counters"]["total"], floor,
        ):
            violations.append(f"{report['module']} mutation score is below exact floor {floor}")
    summary = {
        "schemaVersion": 1,
        "verificationMode": "initial-capture",
        "status": "pass" if not violations else "fail",
        "sourceCommit": measurement["sourceCommit"],
        "policySha256": measurement["policySha256"],
        "predecessorBaselineHash": None,
        "predecessorVerificationReceiptHash": None,
        "routeSha256": measurement["routeSha256"],
        "routeReceiptSha256": measurement["routeReceiptSha256"],
        "completionSha256": measurement["completionSha256"],
        "hostNeutralMutationIdentity": measurement["hostNeutralMutationIdentity"],
        "hostNeutralMutationIdentitySha256": measurement["hostNeutralMutationIdentitySha256"],
        "perRunExecutionProvenance": measurement["perRunExecutionProvenance"],
        "perRunExecutionProvenanceSha256": measurement["perRunExecutionProvenanceSha256"],
        "historicalLinuxComparison": (
            "NOT_ESTABLISHED"
            if measurement["perRunExecutionProvenance"]["selectedProfile"] == "linux-x86_64"
            else None
        ),
        "reports": measurement["reports"],
        "violations": sorted(violations),
    }
    write_atomic(SUMMARY_PATH, summary)
    if violations:
        raise MutationPolicyError("; ".join(sorted(violations)))
    return summary


def capture(java_home: str) -> dict[str, Any]:
    policy, route_value, policy_raw, policy_hash = validate_route(java_home)
    measurement = measure(java_home)
    if policy["enforcementPhase"] != "observe":
        raise MutationPolicyError("initial mutation baseline capture requires observation phase")
    if BASELINE_PATH.exists():
        raise MutationPolicyError("initial capture requires no checked mutation baseline")
    summary = _initial_capture_summary(policy, measurement)
    components: dict[str, bytes] = {
        "policy": policy_raw,
        "route": read_bytes(ROUTE_PATH),
        "tasks": read_bytes(TASKS_PATH),
        "routeReceipt": read_bytes(ROUTE_RECEIPT_PATH),
        "attempt": read_bytes(ATTEMPT_PATH),
        "completion": read_bytes(COMPLETION_PATH),
        "measurement": read_bytes(MEASUREMENT_PATH),
        "verificationSummary": read_bytes(SUMMARY_PATH),
    }
    for name, module in sorted(policy["modules"].items()):
        components[f"configuration:{name}"] = read_bytes(REPOSITORY_ROOT / module["configurationPath"])
        components[f"xml:{name}"] = read_bytes(REPOSITORY_ROOT / module["reportPath"])
        report = next(item for item in measurement["reports"] if item["module"] == name)
        components[f"semantic:{name}"] = canonical_json_bytes(
            {"semanticSha256": report["semanticSha256"], "html": report["html"]},
        )
    manifest = build_capture_evidence_manifest(
        components=components,
        policy_sha256=policy_hash,
        predecessor_baseline_sha256=None,
        predecessor_verification_receipt_sha256=None,
        source_commit=route_value["sourceCommit"],
        host_neutral_identity_sha256=measurement["hostNeutralMutationIdentitySha256"],
        per_run_provenance_sha256=measurement["perRunExecutionProvenanceSha256"],
    )
    evidence_digest = sha256(canonical_json_bytes(manifest))
    payload = {
        "schemaVersion": 1,
        "sourceCommit": measurement["sourceCommit"],
        "policySha256": policy_hash,
        "observationGitConfig": measurement["observationGitConfig"],
        "pluginVersion": policy["pitest"]["pluginVersion"],
        "engineVersion": policy["pitest"]["pitestVersion"],
        "hostNeutralMutationIdentity": measurement["hostNeutralMutationIdentity"],
        "hostNeutralMutationIdentitySha256": measurement["hostNeutralMutationIdentitySha256"],
        "captureProfile": measurement["perRunExecutionProvenance"]["selectedProfile"],
        "profileHistory": {
            "linux-x86_64": {"state": "NOT_ESTABLISHED"},
        },
        "reports": measurement["reports"],
    }
    candidate = build_capture_candidate(
        payload=payload,
        predecessor_baseline_sha256=None,
        capture_evidence_digest=evidence_digest,
    )
    write_atomic(BASELINE_PATH, candidate)
    candidate_raw = read_bytes(BASELINE_PATH)
    capture_receipt = build_capture_receipt(
        candidate_baseline=candidate_raw,
        evidence_manifest=manifest,
    )
    receipt_path = CAPTURE_RECEIPT_ROOT / f"{sha256(candidate_raw)}.json"
    if receipt_path.exists():
        raise MutationPolicyError("initial capture receipt path already exists")
    write_atomic(receipt_path, capture_receipt)
    if read_strict_json(read_bytes(BASELINE_PATH)) != candidate:
        raise MutationPolicyError("candidate baseline re-read differs")
    if read_strict_json(read_bytes(receipt_path)) != capture_receipt:
        raise MutationPolicyError("separate capture receipt re-read differs")
    return candidate


def validate_baseline_capture_receipt(baseline_raw: bytes, baseline: dict[str, Any]) -> dict[str, Any]:
    candidate_hash = sha256(baseline_raw)
    receipt_path = CAPTURE_RECEIPT_ROOT / f"{candidate_hash}.json"
    value = read_strict_json(read_bytes(receipt_path))
    if not isinstance(value, dict):
        raise MutationPolicyError("mutation capture receipt must be an object")
    expected_keys = {
        "candidateBaselineSha256",
        "captureEvidenceDigest",
        "components",
        "predecessorBaselineHash",
        "schema",
    }
    if set(value) != expected_keys or value.get("schema") != "pitest-capture-receipt-v1":
        raise MutationPolicyError("mutation capture receipt keys differ from the closed schema")
    if value["candidateBaselineSha256"] != candidate_hash:
        raise MutationPolicyError("mutation capture receipt candidate hash differs")
    if value["captureEvidenceDigest"] != baseline.get("captureEvidenceDigest"):
        raise MutationPolicyError("mutation capture receipt evidence digest differs")
    if value["predecessorBaselineHash"] != baseline.get("predecessorBaselineHash"):
        raise MutationPolicyError("mutation capture receipt predecessor differs")
    forbidden = {
        "captureReceiptSha256", "verificationReceiptHash", "successorHash", "baselineSha256",
    }
    if set(value) & forbidden or set(baseline) & forbidden:
        raise MutationPolicyError("mutation baseline/capture receipt contains a self or successor hash")
    return value


def verify(observation: bool, java_home: str) -> dict[str, Any]:
    policy, route_value, policy_raw, policy_hash = validate_route(java_home)
    if observation and policy["enforcementPhase"] != "observe":
        raise MutationPolicyError("observe rejects blocking-phase effective configuration")
    if route_value["status"] == "not-applicable":
        value = {
            "schemaVersion": 1,
            "status": "not-applicable",
            "sourceCommit": route_value["sourceCommit"],
            "policySha256": policy_hash,
            "routeSha256": sha256(read_bytes(ROUTE_PATH)),
            "routeReceiptSha256": sha256(read_bytes(ROUTE_RECEIPT_PATH)),
            "hostNeutralMutationIdentity": route_value["hostNeutralMutationIdentity"],
            "hostNeutralMutationIdentitySha256": route_value["hostNeutralMutationIdentitySha256"],
            "perRunExecutionProvenance": route_value["perRunExecutionProvenance"],
            "perRunExecutionProvenanceSha256": route_value["perRunExecutionProvenanceSha256"],
            "historicalLinuxComparison": (
                "NOT_ESTABLISHED"
                if route_value["perRunExecutionProvenance"]["selectedProfile"] == "linux-x86_64"
                else None
            ),
            "violations": [],
        }
        write_atomic(SUMMARY_PATH, value)
        return value
    completion_raw = read_bytes(COMPLETION_PATH)
    completion = read_strict_json(completion_raw)
    baseline_raw = read_bytes(BASELINE_PATH) if BASELINE_PATH.exists() else None
    baseline = read_strict_json(baseline_raw) if baseline_raw is not None else None
    if not isinstance(completion, dict):
        raise MutationPolicyError("completion is malformed")
    violations: list[str] = []
    if baseline is not None:
        if not isinstance(baseline, dict) or baseline.get("schemaVersion") != 1:
            raise MutationPolicyError("mutation baseline is malformed")
        validate_baseline_capture_receipt(baseline_raw, baseline)
        _, git = validate_bootstrap(policy, java_home)
        if git.text(GitCommand.MERGE_BASE, "--is-ancestor", baseline["sourceCommit"], route_value["sourceCommit"]) != "":
            raise MutationPolicyError("mutation baseline source is not an ancestor")
        if baseline.get("policySha256") != policy_hash:
            raise MutationPolicyError("mutation baseline policy identity differs")
        if baseline.get("hostNeutralMutationIdentitySha256") != completion.get("hostNeutralMutationIdentitySha256"):
            raise MutationPolicyError("host-neutral mutation identity differs; reviewed recapture required")
        if baseline.get("hostNeutralMutationIdentity") != completion.get("hostNeutralMutationIdentity"):
            raise MutationPolicyError("host-neutral mutation identity payload differs")
        if observation and baseline.get("observationGitConfig") != git_configuration_identity(git):
            raise MutationPolicyError("observe requires exact Commit-A mutation-producing Git configuration")
        baseline_by_module = {report["module"]: report for report in baseline["reports"]}
        for report in completion["reports"]:
            name = report["module"]
            module = policy["modules"][name]
            floor = module["floorPercent"]
            if not observation and floor is not None and not compare_floor(report["counters"]["KILLED"], report["counters"]["total"], floor):
                violations.append(f"{name} mutation score is below exact floor {floor}")
            baseline_packages = {package: counters["NO_COVERAGE"] for package, counters in baseline_by_module[name]["packages"].items()}
            current_packages = {package: counters["NO_COVERAGE"] for package, counters in report["packages"].items()}
            if not observation:
                violations.extend(compare_no_coverage(baseline_packages, current_packages, set(baseline_packages) | set(current_packages)))
    elif not observation:
        raise MutationPolicyError("blocking verification requires mutation baseline")
    value = {
        "schemaVersion": 1,
        "status": "pass" if not violations else "fail",
        "sourceCommit": route_value["sourceCommit"],
        "policySha256": policy_hash,
        "routeSha256": sha256(read_bytes(ROUTE_PATH)),
        "routeReceiptSha256": sha256(read_bytes(ROUTE_RECEIPT_PATH)),
        "attemptSha256": sha256(read_bytes(ATTEMPT_PATH)),
        "completionSha256": sha256(completion_raw),
        "hostNeutralMutationIdentity": completion["hostNeutralMutationIdentity"],
        "hostNeutralMutationIdentitySha256": completion["hostNeutralMutationIdentitySha256"],
        "perRunExecutionProvenance": completion["perRunExecutionProvenance"],
        "perRunExecutionProvenanceSha256": completion["perRunExecutionProvenanceSha256"],
        "historicalLinuxComparison": (
            baseline.get("profileHistory", {}).get("linux-x86_64", {}).get("state")
            if isinstance(baseline, dict) and completion["perRunExecutionProvenance"]["selectedProfile"] == "linux-x86_64"
            else None
        ),
        "reports": completion["reports"],
        "violations": sorted(violations),
        "finalReceiptPath": relative(FINAL_RECEIPT_PATH),
    }
    write_atomic(SUMMARY_PATH, value)
    if violations:
        raise MutationPolicyError("; ".join(sorted(violations)))
    return value


def seal_verification(java_home: str) -> dict[str, Any]:
    policy, route_value, policy_raw, policy_hash = validate_route(java_home)
    summary_raw = read_bytes(SUMMARY_PATH)
    summary = read_strict_json(summary_raw)
    if not isinstance(summary, dict) or summary.get("status") not in {"pass", "not-applicable"}:
        raise MutationPolicyError("only a successful summary can be sealed")
    if summary.get("verificationMode") == "initial-capture":
        raise MutationPolicyError("initial-capture summary cannot be sealed as an ordinary receipt")
    selected = route_value["status"] == "selected"
    predecessors = {
        "policy": policy_raw,
        "route": read_bytes(ROUTE_PATH),
        "routeReceipt": read_bytes(ROUTE_RECEIPT_PATH),
        "summary": summary_raw,
        "tasks": read_bytes(TASKS_PATH),
    }
    if selected:
        predecessors["attempt"] = read_bytes(ATTEMPT_PATH)
        predecessors["completion"] = read_bytes(COMPLETION_PATH)
        predecessors["baseline"] = read_bytes(BASELINE_PATH) if BASELINE_PATH.exists() else b""
    value = receipt(
        "pitest-verification-receipt-v1",
        predecessors,
        sourceCommit=route_value["sourceCommit"],
        status="selected-verified" if selected else "not-applicable-verified",
        policySha256=policy_hash,
        hostNeutralMutationIdentitySha256=summary.get("hostNeutralMutationIdentitySha256"),
        perRunExecutionProvenanceSha256=summary.get("perRunExecutionProvenanceSha256"),
        historicalLinuxComparison=summary.get("historicalLinuxComparison"),
    )
    write_atomic(FINAL_RECEIPT_PATH, value)
    if read_strict_json(read_bytes(FINAL_RECEIPT_PATH)) != value:
        raise MutationPolicyError("final verification receipt re-read differs")
    return value


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser()
    subcommands = value.add_subparsers(dest="command", required=True)
    route_parser = subcommands.add_parser("route")
    route_parser.add_argument("--event", required=True, choices=["pull-request", "main", "tag", "schedule", "local-all"])
    route_parser.add_argument("--base")
    route_parser.add_argument("--java-home", required=True)
    selector_parser = subcommands.add_parser("consume-java-selector")
    selector_parser.add_argument(
        "--path",
        required=True,
        choices=["build/quality/pitest-runtime/bootstrap/java-home.selector"],
    )
    subcommands.add_parser("bootstrap")
    for name in ("validate-route", "attempt", "complete", "measure", "capture", "observe", "verify", "seal-verification", "recapture-transition"):
        subcommands.add_parser(name).add_argument("--java-home", required=True)
    return value


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        if args.command == "bootstrap":
            policy, _, _ = load_policy()
            validate_tool_profile(policy)
        elif args.command == "consume-java-selector":
            policy, _, _ = load_policy()
            print(consume_java_selector(policy, args.path))
        elif args.command == "route":
            route(args.event, args.base, args.java_home)
        elif args.command == "validate-route":
            validate_route(args.java_home)
        elif args.command == "attempt":
            attempt(args.java_home)
        elif args.command == "complete":
            complete(args.java_home)
        elif args.command == "measure":
            measure(args.java_home)
        elif args.command == "capture":
            capture(args.java_home)
        elif args.command == "observe":
            verify(observation=True, java_home=args.java_home)
        elif args.command == "verify":
            verify(observation=False, java_home=args.java_home)
        elif args.command == "seal-verification":
            seal_verification(args.java_home)
        elif args.command == "recapture-transition":
            raise MutationPolicyError("no reviewed mutation recapture transition is declared")
        return 0
    except MutationPolicyError as error:
        diagnostic = str(error).replace(str(REPOSITORY_ROOT), "<repository-root>")
        if args.command not in {"bootstrap", "consume-java-selector"} and (args.command != "route" or ROUTE_PATH.exists()):
            REPORT_ROOT.mkdir(parents=True, exist_ok=True)
            write_atomic(
                SUMMARY_PATH,
                {"schemaVersion": 1, "status": "fail", "violations": [diagnostic]},
            )
        print(f"mutation policy violation: {diagnostic}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
