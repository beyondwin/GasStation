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
from dataclasses import dataclass
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
SUCCESSOR_IMAGE_PATH = REPOSITORY_ROOT / "config/quality/linux-runner-image-successors.json"
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
TRANSITION_ROOT = REPOSITORY_ROOT / "config/quality/mutation-transitions"
EVIDENCE_ARCHIVE_ROOT = REPOSITORY_ROOT / "config/quality/mutation-evidence"
REVIEWED_TRANSITION_AXIS = "task7-spec-review-round3-corrections"
INITIAL_BASELINE_CANDIDATE_SHA256 = "0839a284eb2e6b1617969d3427ba1b4d1ac7c7bf81441d12801eebba127be201"
INITIAL_CAPTURE_RECEIPT_PATH = (
    "config/quality/mutation-captures/"
    f"{INITIAL_BASELINE_CANDIDATE_SHA256}.json"
)
INITIAL_CAPTURE_RECEIPT_SHA256 = "4da85a7c98dda7c26287d215b6b59c37c68a448e9d3e19b28916283615a68219"
INITIAL_CAPTURE_INTRODUCTION_COMMIT = "4a958727679440a53657a0adcf1a7017cedcb804"
LEGACY_CAPTURE_CHECKPOINT_COMMIT = "dfb5439c76bb860ca545e4744dca937057c3b85c"
LEGACY_CAPTURE_PATH_ORDER = (
    INITIAL_CAPTURE_RECEIPT_PATH,
    "config/quality/mutation-captures/b5d344f5754eb0fa4ab2cbfd6ce460b54befcb062d477ed4397379a48202f4c8.json",
    "config/quality/mutation-captures/0af6ea8f8de37a79d498414be858dc86978b3cc37d4baa2efbaf3aac39511a62.json",
    "config/quality/mutation-captures/24aa14a0e2941d673d6aa3b370a268fbb2fe4633b6ef863a1ed736af41a0336a.json",
)
LEGACY_TRANSITION_PATH_ORDER = (
    "config/quality/mutation-transitions/0839a284eb2e6b1617969d3427ba1b4d1ac7c7bf81441d12801eebba127be201-to-99360ac88c51e0027c544e2b1dd1c02fb1bfe8671e2a9354127f5511b08e8168.json",
    "config/quality/mutation-transitions/b5d344f5754eb0fa4ab2cbfd6ce460b54befcb062d477ed4397379a48202f4c8-to-69331d2eb42cb734716b1886397b2aa1fd61bf141cee919ab29a2496e7d404bb.json",
    "config/quality/mutation-transitions/0af6ea8f8de37a79d498414be858dc86978b3cc37d4baa2efbaf3aac39511a62-to-adf64ef12bc9352b2f74df93c366d9799e668a61827c4430b933cd024b8c81a1.json",
)
LEGACY_ARCHIVED_CANDIDATES_WITHOUT_BASELINE_CAPTURE_RECEIPT = frozenset({
    "3f5d529acbcfe1a7c6e6da3d123c0afd4a8d16666fab71a98716a100821b28ea",
    "2000cdf63d960a707cc610917b254950070f403a7bd2d755f10d3678de589caa",
    "015ed5a495a958029ba02cf8775edf198ef2d4842d9e9d6c3c8489e10707d905",
})
BOOTSTRAP_MARKER = "sealed-v1"


@dataclass(frozen=True)
class BaselineSnapshot:
    raw: bytes
    value: dict[str, Any]
    sha256: str
    capture_receipt_raw: bytes | None = None
    capture_receipt_sha256: str | None = None


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_bytes(path: Path) -> bytes:
    try:
        return path.read_bytes()
    except OSError as error:
        raise MutationPolicyError(f"required evidence is missing: {relative(path)}") from error


def read_baseline_snapshot() -> BaselineSnapshot | None:
    try:
        raw = BASELINE_PATH.read_bytes()
    except FileNotFoundError:
        return None
    except OSError as error:
        raise MutationPolicyError(f"required evidence is unreadable: {relative(BASELINE_PATH)}") from error
    value = read_strict_json(raw)
    if not isinstance(value, dict):
        raise MutationPolicyError("mutation baseline is malformed")
    baseline_sha256 = sha256(raw)
    capture_receipt_raw = read_bytes(CAPTURE_RECEIPT_ROOT / f"{baseline_sha256}.json")
    return BaselineSnapshot(
        raw=raw,
        value=value,
        sha256=baseline_sha256,
        capture_receipt_raw=capture_receipt_raw,
        capture_receipt_sha256=sha256(capture_receipt_raw),
    )


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
    capture_policy = value["capturePolicy"]
    if capture_policy.get("schema") != "acyclic-candidate-and-separate-receipt-v1":
        raise MutationPolicyError("mutation capture policy is not acyclic")
    expected_anchor = {
        "baselineCandidateSha256": INITIAL_BASELINE_CANDIDATE_SHA256,
        "captureReceiptPath": INITIAL_CAPTURE_RECEIPT_PATH,
        "captureReceiptSha256": INITIAL_CAPTURE_RECEIPT_SHA256,
        "introductionCommit": INITIAL_CAPTURE_INTRODUCTION_COMMIT,
    }
    if capture_policy.get("initialAnchor") != expected_anchor:
        raise MutationPolicyError("mutation capture initial trust anchor differs")
    if capture_policy.get("evidenceArchiveDirectory") != "config/quality/mutation-evidence":
        raise MutationPolicyError("mutation capture archive directory differs")
    checkpoint = capture_policy.get("legacyCheckpoint")
    if not isinstance(checkpoint, dict) or checkpoint.get("commit") != LEGACY_CAPTURE_CHECKPOINT_COMMIT:
        raise MutationPolicyError("mutation capture legacy checkpoint differs")
    if capture_policy.get("reviewedTransitionAxes") != [REVIEWED_TRANSITION_AXIS]:
        raise MutationPolicyError("mutation capture reviewed transition axis differs")
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


def _accepted_linux_image_versions(expected: dict[str, str]) -> frozenset[str]:
    accepted = {expected["ImageVersion"]}
    if not SUCCESSOR_IMAGE_PATH.is_file():
        return frozenset(accepted)
    payload = read_strict_json(SUCCESSOR_IMAGE_PATH.read_bytes())
    if not isinstance(payload, dict) or payload.get("ImageOS") != expected["ImageOS"]:
        raise MutationPolicyError("Linux runner image successor ImageOS differs")
    versions = payload.get("acceptedImageVersions")
    if not isinstance(versions, list) or not all(isinstance(item, str) and item for item in versions):
        raise MutationPolicyError("Linux runner image successor versions differ")
    accepted.update(versions)
    return frozenset(accepted)


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
    if values.get("ImageOS") != expected["ImageOS"]:
        raise MutationPolicyError("Linux runner image metadata differs: ImageOS")
    if values.get("ImageVersion") not in _accepted_linux_image_versions(expected):
        raise MutationPolicyError("Linux runner image metadata differs: ImageVersion")
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
    value = build_attempt_value(policy, route_value, policy_raw)
    write_atomic(ATTEMPT_PATH, value)
    return value


def build_attempt_value(
    policy: dict[str, Any],
    route_value: dict[str, Any],
    policy_raw: bytes,
) -> dict[str, Any]:
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
    return value


def validate_attempt_value(
    policy: dict[str, Any],
    route_value: dict[str, Any],
    policy_raw: bytes,
) -> tuple[dict[str, Any], bytes]:
    raw = read_bytes(ATTEMPT_PATH)
    value = read_strict_json(raw)
    expected = build_attempt_value(policy, route_value, policy_raw)
    if not isinstance(value, dict) or value != expected:
        raise MutationPolicyError("attempt differs from the typed current route/task/policy contract")
    return value, raw


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
    html_records.sort(key=lambda item: item["path"])
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
    _, attempt_raw = validate_attempt_value(policy, route_value, policy_raw)
    value = build_completion_value(policy, route_value, policy_raw, attempt_raw)
    write_atomic(COMPLETION_PATH, value)
    return value


def build_completion_value(
    policy: dict[str, Any],
    route_value: dict[str, Any],
    policy_raw: bytes,
    attempt_raw: bytes,
) -> dict[str, Any]:
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
    return value


def validate_completion_value(
    policy: dict[str, Any],
    route_value: dict[str, Any],
    policy_raw: bytes,
) -> tuple[dict[str, Any], bytes]:
    _, attempt_raw = validate_attempt_value(policy, route_value, policy_raw)
    raw = read_bytes(COMPLETION_PATH)
    value = read_strict_json(raw)
    expected = build_completion_value(policy, route_value, policy_raw, attempt_raw)
    if not isinstance(value, dict) or value != expected:
        raise MutationPolicyError("completion differs from current configuration/XML/HTML/semantic artifacts")
    return value, raw


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


def git_path_inventory(git: GitExecutor, *paths: str, commit: str = "HEAD") -> dict[str, Any]:
    raw = git.bytes(GitCommand.LS_TREE, "-rz", commit, "--", *paths)
    records: list[dict[str, str]] = []
    for entry in raw.split(b"\0"):
        if not entry:
            continue
        metadata, path_raw = entry.split(b"\t", 1)
        mode, object_type, object_id = metadata.decode("ascii").split(" ")
        if object_type != "blob":
            raise MutationPolicyError("mutation Git inventory must contain blobs only")
        records.append({"path": path_raw.decode("utf-8", errors="strict"), "mode": mode, "blob": object_id})
    records.sort(key=lambda item: item["path"])
    return {"count": len(records), "records": records, "sha256": sha256(canonical_json_bytes(records))}


def filesystem_inventory(*roots: Path) -> dict[str, Any]:
    records: list[dict[str, str]] = []
    for root in roots:
        if root.is_symlink():
            raise MutationPolicyError("compiled inventory root must not be a symlink")
        if not root.exists():
            continue
        if not root.is_dir():
            raise MutationPolicyError("compiled inventory root must be a directory")
        for path in sorted(root.rglob("*.class")):
            if path.is_symlink() or not path.is_file():
                raise MutationPolicyError("compiled inventory contains a symlink or non-file")
            records.append({"path": relative(path), "sha256": sha256(path.read_bytes())})
    records.sort(key=lambda item: item["path"])
    return {"count": len(records), "records": records, "sha256": sha256(canonical_json_bytes(records))}


def module_inventory(policy: dict[str, Any], name: str, git: GitExecutor) -> dict[str, Any]:
    module = policy["modules"][name]
    module_dir = REPOSITORY_ROOT / module["modulePath"].removeprefix(":").replace(":", "/")
    configuration = read_strict_json(read_bytes(REPOSITORY_ROOT / module["configurationPath"]))
    if not isinstance(configuration, dict) or not isinstance(configuration.get("values"), dict):
        raise MutationPolicyError(f"effective PIT surface is missing for {name}")
    excluded_per_run = {"java.executable"}
    comparable_surface = {
        key: value for key, value in configuration["values"].items()
        if key not in excluded_per_run
    }
    return {
        "authoredMain": git_path_inventory(git, f"{module_dir.relative_to(REPOSITORY_ROOT).as_posix()}/src/main"),
        "authoredTest": git_path_inventory(git, f"{module_dir.relative_to(REPOSITORY_ROOT).as_posix()}/src/test"),
        "compiledMain": filesystem_inventory(module_dir / "build/classes/kotlin/main", module_dir / "build/classes/java/main"),
        "compiledTest": filesystem_inventory(module_dir / "build/classes/kotlin/test", module_dir / "build/classes/java/test"),
        "effectiveSurface": {
            "fields": comparable_surface,
            "sha256": sha256(canonical_json_bytes(comparable_surface)),
        },
        "sourceDirs": configuration["values"].get("pit.sourceDirs"),
        "mutableCodePaths": configuration["values"].get("pit.mutableCodePaths"),
        "additionalClasspath": configuration["values"].get("pit.additionalClasspath"),
        "launchClasspath": configuration["values"].get("pit.launchClasspath"),
    }


def mutation_input_identity(
    policy: dict[str, Any],
    git: GitExecutor,
    measurement: dict[str, Any],
) -> dict[str, Any]:
    modules = {
        name: module_inventory(policy, name, git)
        for name in sorted(policy["modules"])
    }
    version_catalog = read_bytes(REPOSITORY_ROOT / "gradle/libs.versions.toml").decode("utf-8", errors="strict")
    kotlin_match = re.search(r'^kotlin\s*=\s*"([^"]+)"$', version_catalog, re.MULTILINE)
    if kotlin_match is None:
        raise MutationPolicyError("Kotlin version is missing from the version catalog")
    toolchain = {
        "pitestPlugin": policy["pitest"]["pluginVersion"],
        "pitestEngine": policy["pitest"]["pitestVersion"],
        "mutationEngine": policy["pitest"]["mutationEngine"],
        "kotlin": kotlin_match.group(1),
        "kotlinTest": kotlin_match.group(1),
        "productionJvmTarget": 17,
        "mutationRuntimeJava": measurement["hostNeutralMutationIdentity"]["java"],
    }
    wrapper_records = git_path_inventory(git, "gradlew", "gradlew.bat", "gradle/wrapper")
    wrapper_properties = read_bytes(REPOSITORY_ROOT / "gradle/wrapper/gradle-wrapper.properties").decode("utf-8", errors="strict")
    distribution = re.search(r"^distributionUrl=(.+)$", wrapper_properties, re.MULTILINE)
    if distribution is None or "gradle-9.6.1-bin.zip" not in distribution.group(1):
        raise MutationPolicyError("Gradle wrapper distribution identity differs")
    wrapper = {
        "gitInventory": wrapper_records,
        "distributionUrl": distribution.group(1),
        "distributionType": "bin",
        "gradleVersion": "9.6.1",
    }
    environment = {
        "policyVersion": policy["executionEnvironmentPolicy"]["policyVersion"],
        "policySha256": sha256(canonical_json_bytes(policy["executionEnvironmentPolicy"])),
        "forbiddenNamesSha256": sha256(canonical_json_bytes(policy["executionEnvironmentPolicy"]["forbiddenExactNames"])),
        "forbiddenPrefixesSha256": sha256(canonical_json_bytes(policy["executionEnvironmentPolicy"]["forbiddenPrefixes"])),
    }
    git_view = {
        "policyVersion": policy["gitObjectViewPolicy"]["policyVersion"],
        "policySha256": sha256(canonical_json_bytes(policy["gitObjectViewPolicy"])),
        "captureInventory": measurement["gitObjectViewIdentity"],
    }
    command_plan = {
        "canonicalGradleFlags": policy["canonicalGradleFlags"],
        "defaultCharacterEncoding": "UTF-8",
        "managedEncodingArguments": ["-Dfile.encoding=UTF-8"],
        "moduleSurfaceSha256": {name: value["effectiveSurface"]["sha256"] for name, value in modules.items()},
    }
    identity = {
        "observationGitConfig": measurement["observationGitConfig"],
        "toolchainIdentity": toolchain,
        "effectiveCommandPlan": command_plan,
        "executionEnvironmentIdentity": environment,
        "gitObjectViewIdentity": git_view,
        "wrapperIdentity": wrapper,
        "moduleInventories": modules,
    }
    return {**identity, "sha256": sha256(canonical_json_bytes(identity))}


def measure(java_home: str) -> dict[str, Any]:
    policy, route_value, policy_raw, policy_hash = validate_route(java_home)
    completion, completion_raw = validate_completion_value(policy, route_value, policy_raw)
    _, git = validate_bootstrap(policy, java_home)
    value = {
        "schemaVersion": 1,
        "sourceCommit": route_value["sourceCommit"],
        "policySha256": policy_hash,
        "enforcementPhase": policy["enforcementPhase"],
        "observationGitConfig": git_configuration_identity(git),
        "gitObjectViewIdentity": route_value["bootstrap"]["gitObjectView"],
        "completionSha256": sha256(completion_raw),
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
    _, git = validate_bootstrap(policy, java_home)
    input_identity = mutation_input_identity(policy, git, measurement)
    components: dict[str, bytes] = {
        "policy": policy_raw,
        "sourceCommit": (route_value["sourceCommit"] + "\n").encode("ascii"),
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
            {"semanticSha256": report["semanticSha256"]},
        )
        components[f"html:{name}"] = canonical_json_bytes(report["html"])
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
        "schemaVersion": 2,
        "sourceCommit": measurement["sourceCommit"],
        "policySha256": policy_hash,
        "observationGitConfig": measurement["observationGitConfig"],
        "toolchainIdentity": input_identity["toolchainIdentity"],
        "hostNeutralMutationIdentity": measurement["hostNeutralMutationIdentity"],
        "hostNeutralMutationIdentitySha256": measurement["hostNeutralMutationIdentitySha256"],
        "effectiveCommandPlan": input_identity["effectiveCommandPlan"],
        "executionEnvironmentIdentity": input_identity["executionEnvironmentIdentity"],
        "gitObjectViewIdentity": input_identity["gitObjectViewIdentity"],
        "wrapperIdentity": input_identity["wrapperIdentity"],
        "moduleInventories": input_identity["moduleInventories"],
        "mutationInputIdentitySha256": input_identity["sha256"],
        "captureProfile": measurement["perRunExecutionProvenance"]["selectedProfile"],
        "profileHistory": {
            "linux-x86_64": {"state": "NOT_ESTABLISHED"},
        },
        "reports": measurement["reports"],
        "predecessorVerificationReceiptHash": None,
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
    validate_baseline_capture_receipt(
        candidate_raw,
        candidate,
        trusted_components=components,
        validate_transition_chain=False,
    )
    return candidate


def _capture_trust_git() -> GitExecutor:
    policy, _, _ = load_policy()
    profile_name = {
        ("Darwin", "arm64"): "darwin-arm64",
        ("Linux", "x86_64"): "linux-x86_64",
    }.get((platform.system(), platform.machine()))
    profile = policy["bootstrapProfiles"].get(profile_name) if profile_name is not None else None
    if not isinstance(profile, dict):
        raise MutationPolicyError("trusted capture has no checked Git profile")
    git = GitExecutor(
        REPOSITORY_ROOT,
        git_path=Path(profile["tools"]["git"]["path"]),
        home=Path(os.environ.get("HOME", str(REPOSITORY_ROOT))),
        tmpdir=Path(os.environ.get("TMPDIR", "/tmp")),
    )
    git.assert_original_full_history()
    return git


def _checked_tree_paths(git: GitExecutor, root: Path, label: str) -> list[str]:
    try:
        root_name = relative(root)
    except MutationPolicyError as error:
        raise MutationPolicyError(f"checked {label} root is not repository-owned") from error
    raw = git.bytes(GitCommand.LS_TREE, "-rz", "--name-only", "HEAD", "--", root_name)
    try:
        paths = [item.decode("utf-8", errors="strict") for item in raw.split(b"\0") if item]
    except UnicodeDecodeError as error:
        raise MutationPolicyError(f"checked {label} path is not UTF-8") from error
    return sorted(paths)


def _checked_blob_info(git: GitExecutor, path: Path, label: str) -> tuple[bytes, str]:
    try:
        name = relative(path)
    except MutationPolicyError as error:
        raise MutationPolicyError(f"trusted {label} must be repository-owned") from error
    history_raw = git.bytes(GitCommand.LOG, "--format=%H", "--", name)
    try:
        history = [line.decode("ascii", errors="strict") for line in history_raw.splitlines() if line]
    except UnicodeDecodeError as error:
        raise MutationPolicyError(f"trusted {label} append-only history is not ASCII") from error
    if len(history) != 1 or re.fullmatch(r"[0-9a-f]{40}", history[0]) is None:
        raise MutationPolicyError(f"trusted {label} must have one append-only history introduction")
    introduced = git.bytes(GitCommand.SHOW, f"{history[0]}:{name}")
    checked = git.bytes(GitCommand.SHOW, f"HEAD:{name}")
    if introduced != checked:
        raise MutationPolicyError(f"trusted {label} differs from its append-only history introduction")
    working = read_bytes(path)
    if working != checked:
        raise MutationPolicyError(f"trusted {label} differs from its checked Git blob")
    return checked, history[0]


def _checked_blob(git: GitExecutor, path: Path, label: str) -> bytes:
    return _checked_blob_info(git, path, label)[0]


def _history_additions(
    git: GitExecutor,
    *,
    roots: tuple[str, ...],
) -> list[tuple[str, str]]:
    history_raw = git.bytes(
        GitCommand.LOG,
        "--reverse",
        "--format=%H",
        f"{INITIAL_CAPTURE_INTRODUCTION_COMMIT}^..HEAD",
        "--",
        *roots,
    )
    try:
        commits = [line.decode("ascii", errors="strict") for line in history_raw.splitlines() if line]
    except UnicodeDecodeError as error:
        raise MutationPolicyError("capture history commit inventory is not ASCII") from error
    if not commits or commits[0] != INITIAL_CAPTURE_INTRODUCTION_COMMIT:
        raise MutationPolicyError("capture history does not start at the reviewed Commit-B anchor")
    additions: list[tuple[str, str]] = []
    for commit in commits:
        if re.fullmatch(r"[0-9a-f]{40}", commit) is None:
            raise MutationPolicyError("capture history contains a malformed commit")
        raw = git.bytes(
            GitCommand.SHOW,
            "--format=",
            "--name-status",
            "-z",
            "--find-renames=50%",
            commit,
            "--",
            *roots,
        )
        for status, old, new in parse_name_status(raw):
            path = new if new is not None else old
            if status != "A" or path is None:
                raise MutationPolicyError(
                    f"capture history path was deleted, replaced, renamed, or modified: {path or '<unknown>'}"
                )
            additions.append((commit, path))
    paths = [path for _, path in additions]
    if len(paths) != len(set(paths)):
        raise MutationPolicyError("capture history contains a replaced append-only path")
    return additions


def _validate_initial_anchor_and_history(
    git: GitExecutor,
    policy: dict[str, Any],
) -> tuple[list[str], list[str], list[str]]:
    if git.text(GitCommand.MERGE_BASE, INITIAL_CAPTURE_INTRODUCTION_COMMIT, "HEAD") != INITIAL_CAPTURE_INTRODUCTION_COMMIT:
        raise MutationPolicyError("reviewed Commit-B capture anchor is not an ancestor of HEAD")
    if git.text(GitCommand.MERGE_BASE, LEGACY_CAPTURE_CHECKPOINT_COMMIT, "HEAD") != LEGACY_CAPTURE_CHECKPOINT_COMMIT:
        raise MutationPolicyError("reviewed legacy capture checkpoint is not an ancestor of HEAD")
    anchored_receipt = git.bytes(GitCommand.SHOW, f"{INITIAL_CAPTURE_INTRODUCTION_COMMIT}:{INITIAL_CAPTURE_RECEIPT_PATH}")
    if sha256(anchored_receipt) != INITIAL_CAPTURE_RECEIPT_SHA256:
        raise MutationPolicyError("reviewed initial capture receipt blob differs at Commit B")
    anchored_baseline = git.bytes(
        GitCommand.SHOW,
        f"{INITIAL_CAPTURE_INTRODUCTION_COMMIT}:config/quality/mutation-baseline.json",
    )
    if sha256(anchored_baseline) != INITIAL_BASELINE_CANDIDATE_SHA256:
        raise MutationPolicyError("reviewed initial baseline candidate differs at Commit B")
    initial_candidate, initial_predecessor, _ = _capture_receipt_link(
        anchored_receipt,
        INITIAL_CAPTURE_RECEIPT_PATH,
    )
    if initial_candidate != INITIAL_BASELINE_CANDIDATE_SHA256 or initial_predecessor is not None:
        raise MutationPolicyError("reviewed initial capture receipt root differs")

    roots = (
        "config/quality/mutation-captures",
        "config/quality/mutation-transitions",
        "config/quality/mutation-evidence",
    )
    additions = _history_additions(git, roots=roots)
    capture_paths = [path for _, path in additions if path.startswith("config/quality/mutation-captures/")]
    transition_paths = [path for _, path in additions if path.startswith("config/quality/mutation-transitions/")]
    archive_paths = [path for _, path in additions if path.startswith("config/quality/mutation-evidence/")]
    if not capture_paths or capture_paths[0] != INITIAL_CAPTURE_RECEIPT_PATH:
        raise MutationPolicyError("capture history initial receipt order differs")

    checkpoint = policy["capturePolicy"]["legacyCheckpoint"]
    checkpoint_groups = (
        ("captureReceiptSha256ByPath", capture_paths, LEGACY_CAPTURE_PATH_ORDER),
        ("transitionSha256ByPath", transition_paths, LEGACY_TRANSITION_PATH_ORDER),
    )
    for field, historical_paths, expected_order in checkpoint_groups:
        expected = checkpoint.get(field)
        if not isinstance(expected, dict) or any(
            not isinstance(path, str)
            or not isinstance(digest, str)
            or re.fullmatch(r"[0-9a-f]{64}", digest) is None
            for path, digest in expected.items()
        ):
            raise MutationPolicyError(f"legacy capture checkpoint schema differs: {field}")
        if set(expected) != set(expected_order) or historical_paths[:len(expected_order)] != list(expected_order):
            raise MutationPolicyError(f"legacy capture checkpoint path order differs: {field}")
        for path, expected_digest in expected.items():
            raw = git.bytes(GitCommand.SHOW, f"{LEGACY_CAPTURE_CHECKPOINT_COMMIT}:{path}")
            if sha256(raw) != expected_digest:
                raise MutationPolicyError(f"legacy capture checkpoint blob differs: {path}")
            current = _checked_blob(git, REPOSITORY_ROOT / path, "legacy capture checkpoint")
            if current != raw:
                raise MutationPolicyError(f"legacy capture checkpoint path was replaced: {path}")
    return capture_paths, transition_paths, archive_paths


def _capture_receipt_link(raw: bytes, path: str) -> tuple[str, str | None, dict[str, Any]]:
    value = read_strict_json(raw)
    if not isinstance(value, dict) or value.get("schema") not in {
        "pitest-capture-receipt-v1", "pitest-capture-receipt-v2",
    }:
        raise MutationPolicyError(f"transition chain capture receipt schema differs: {path}")
    candidate = value.get("candidateBaselineSha256")
    predecessor = value.get("predecessorBaselineHash")
    if not isinstance(candidate, str) or re.fullmatch(r"[0-9a-f]{64}", candidate) is None:
        raise MutationPolicyError(f"transition chain candidate hash differs: {path}")
    if predecessor is not None and (not isinstance(predecessor, str) or re.fullmatch(r"[0-9a-f]{64}", predecessor) is None):
        raise MutationPolicyError(f"transition chain predecessor hash differs: {path}")
    if Path(path).name != f"{candidate}.json":
        raise MutationPolicyError(f"transition chain capture filename differs: {path}")
    receipt_keys = {
        "schema", "candidateBaselineSha256", "predecessorBaselineHash",
        "captureEvidenceDigest", "evidenceManifest",
    }
    archived_receipt_keys = receipt_keys | {"evidenceArchive"}
    legacy_keys = (receipt_keys - {"evidenceManifest"}) | {"components"}
    evidence_digest = value.get("captureEvidenceDigest")
    if not isinstance(evidence_digest, str) or re.fullmatch(r"[0-9a-f]{64}", evidence_digest) is None:
        raise MutationPolicyError(f"transition chain capture evidence digest differs: {path}")
    if set(value) == legacy_keys:
        if predecessor is not None:
            raise MutationPolicyError(f"transition chain legacy capture predecessor differs: {path}")
        components = value.get("components")
        expected_components = {
            "policy", "route", "tasks", "routeReceipt", "attempt", "completion", "measurement",
            "configuration:location", "configuration:settings", "configuration:station",
            "xml:location", "xml:settings", "xml:station",
            "semantic:location", "semantic:settings", "semantic:station", "verificationSummary",
        }
        if not isinstance(components, dict) or set(components) != expected_components:
            raise MutationPolicyError(f"transition chain legacy component set differs: {path}")
    elif frozenset(value) in {frozenset(receipt_keys), frozenset(archived_receipt_keys)}:
        manifest = value.get("evidenceManifest")
        manifest_keys = {
            "schema", "sourceCommit", "policySha256", "predecessorBaselineHash",
            "predecessorVerificationReceiptHash", "hostNeutralMutationIdentitySha256",
            "perRunExecutionProvenanceSha256", "components",
        }
        if (
            not isinstance(manifest, dict)
            or set(manifest) != manifest_keys
            or manifest.get("schema") != "pitest-capture-evidence-manifest-v1"
            or manifest.get("predecessorBaselineHash") != predecessor
            or sha256(canonical_json_bytes(manifest)) != evidence_digest
        ):
            raise MutationPolicyError(f"transition chain capture manifest differs: {path}")
        components = manifest.get("components")
        expected_components = {
            "policy", "sourceCommit", "route", "tasks", "routeReceipt", "attempt", "completion",
            "measurement", "configuration:location", "configuration:settings", "configuration:station",
            "xml:location", "xml:settings", "xml:station", "semantic:location", "semantic:settings",
            "semantic:station", "html:location", "html:settings", "html:station", "verificationSummary",
        }
        if predecessor is not None:
            expected_components.add("predecessorVerificationReceipt")
        if not isinstance(components, dict) or set(components) != expected_components:
            raise MutationPolicyError(f"transition chain capture component set differs: {path}")
        if set(value) == archived_receipt_keys:
            archive = value.get("evidenceArchive")
            if (
                value.get("schema") != "pitest-capture-receipt-v2"
                or not isinstance(archive, dict)
                or set(archive) != {"path", "sha256"}
                or archive.get("path") != f"config/quality/mutation-evidence/{candidate}/archive.json"
                or not isinstance(archive.get("sha256"), str)
                or re.fullmatch(r"[0-9a-f]{64}", archive["sha256"]) is None
            ):
                raise MutationPolicyError(f"transition chain capture archive reference differs: {path}")
        elif value.get("schema") != "pitest-capture-receipt-v1":
            raise MutationPolicyError(f"transition chain unarchived receipt schema differs: {path}")
    else:
        raise MutationPolicyError(f"transition chain capture receipt closed schema differs: {path}")
    if any(not isinstance(item, str) or re.fullmatch(r"[0-9a-f]{64}", item) is None for item in components.values()):
        raise MutationPolicyError(f"transition chain capture component digest differs: {path}")
    return candidate, predecessor, value


_ARCHIVED_CONFIGURATION_VALUE_KEYS_SHA256 = (
    "3a6779a27d03e6acfef9517a784145dcc787b658c8c87a5a01e69f915e9203cd"
)


def _archived_json(
    raw: bytes,
    label: str,
    *,
    canonical: bool = True,
) -> dict[str, Any] | list[Any]:
    value = read_strict_json(raw)
    if (
        not isinstance(value, (dict, list))
        or (canonical and canonical_json_bytes(value) != raw)
    ):
        raise MutationPolicyError(f"archived {label} schema differs")
    return value


def _archived_digest(value: object, label: str) -> str:
    if not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{64}", value) is None:
        raise MutationPolicyError(f"archived {label} digest differs")
    return value


def _gradle_canonical_digest(value: object) -> str:
    """Match build-logic canonicalCoverageJson, which has no trailing newline."""
    encoded = canonical_json_bytes(value)
    if not encoded.endswith(b"\n"):
        raise MutationPolicyError("canonical JSON encoding lost its required line ending")
    return sha256(encoded[:-1])


def _validate_archived_path_digest_inventory(
    value: object,
    label: str,
    *,
    path_prefix: str,
    path_suffix: str,
) -> list[dict[str, str]]:
    if not isinstance(value, list):
        raise MutationPolicyError(f"archived {label} schema differs")
    paths: list[str] = []
    for record in value:
        if not isinstance(record, dict) or set(record) != {"path", "sha256"}:
            raise MutationPolicyError(f"archived {label} record schema differs")
        path = record.get("path")
        if (
            not isinstance(path, str)
            or not path.startswith(path_prefix)
            or not path.endswith(path_suffix)
            or path.startswith("/")
            or ".." in Path(path).parts
        ):
            raise MutationPolicyError(f"archived {label} path differs")
        _archived_digest(record.get("sha256"), f"{label} record")
        paths.append(path)
    if paths != sorted(paths) or len(paths) != len(set(paths)):
        raise MutationPolicyError(f"archived {label} inventory differs")
    return value


def _validate_archived_inventory_delta(value: object, label: str) -> None:
    module_keys = {
        "authoredMain", "authoredTest", "compiledMain", "compiledTest", "sourceFiles",
        "mutants", "classes", "effectiveSurface", "classpaths",
    }
    if not isinstance(value, dict) or set(value) != module_keys:
        raise MutationPolicyError(f"archived {label} schema differs")
    for name in ("authoredMain", "authoredTest", "compiledMain", "compiledTest"):
        delta = value[name]
        if not isinstance(delta, dict) or set(delta) != {"added", "removed", "changed"}:
            raise MutationPolicyError(f"archived {label} {name} schema differs")
        if any(not isinstance(delta[field], list) for field in delta):
            raise MutationPolicyError(f"archived {label} {name} values differ")
        record_keys = {"path", "blob", "mode"} if name.startswith("authored") else {"path", "sha256"}

        def validate_inventory_record(record: object) -> bool:
            if not isinstance(record, dict) or set(record) != record_keys:
                return False
            if not isinstance(record.get("path"), str):
                return False
            digest_key = "blob" if "blob" in record_keys else "sha256"
            digest_pattern = r"[0-9a-f]{40}" if digest_key == "blob" else r"[0-9a-f]{64}"
            if not isinstance(record.get(digest_key), str) or re.fullmatch(digest_pattern, record[digest_key]) is None:
                return False
            return "mode" not in record_keys or (
                isinstance(record.get("mode"), str)
                and re.fullmatch(r"[0-7]{6}", record["mode"]) is not None
            )

        if any(
            not validate_inventory_record(record)
            for field in ("added", "removed")
            for record in delta[field]
        ):
            raise MutationPolicyError(f"archived {label} {name} record schema differs")
        for change in delta["changed"]:
            if (
                not isinstance(change, dict)
                or set(change) != {"path", "before", "after"}
                or not isinstance(change["path"], str)
                or not validate_inventory_record(change["before"])
                or not validate_inventory_record(change["after"])
                or change["before"]["path"] != change["path"]
                or change["after"]["path"] != change["path"]
            ):
                raise MutationPolicyError(f"archived {label} {name} change schema differs")
    for name in ("sourceFiles",):
        delta = value[name]
        if (
            not isinstance(delta, dict)
            or set(delta) != {"added", "removed"}
            or any(
                not isinstance(items, list)
                or items != sorted(items)
                or len(items) != len(set(items))
                or any(not isinstance(item, str) for item in items)
                for items in delta.values()
            )
        ):
            raise MutationPolicyError(f"archived {label} {name} schema differs")
    mutants = value["mutants"]
    if (
        not isinstance(mutants, dict)
        or set(mutants) != {"added", "removed", "changedStatuses"}
        or any(not isinstance(items, list) for items in mutants.values())
    ):
        raise MutationPolicyError(f"archived {label} mutants schema differs")
    mutant_keys = {"mutatedClass", "mutatedMethod", "methodDescription", "mutator", "indexes"}
    for field, records in mutants.items():
        expected = mutant_keys | ({"before", "after"} if field == "changedStatuses" else set())
        for record in records:
            if (
                not isinstance(record, dict)
                or set(record) != expected
                or any(not isinstance(record[key], str) for key in expected - {"indexes"})
                or not isinstance(record["indexes"], list)
                or not record["indexes"]
                or any(type(index) is not int or index < 0 for index in record["indexes"])
            ):
                raise MutationPolicyError(f"archived {label} mutant record schema differs")
    classes = value["classes"]
    if (
        not isinstance(classes, dict)
        or set(classes) != {"added", "removed", "changedContent"}
        or any(not isinstance(items, list) for items in classes.values())
        or any(not isinstance(path, str) for field in ("added", "removed") for path in classes[field])
    ):
        raise MutationPolicyError(f"archived {label} classes schema differs")
    for record in classes["changedContent"]:
        if not isinstance(record, dict) or set(record) != {"path", "beforeSha256", "afterSha256"}:
            raise MutationPolicyError(f"archived {label} class change schema differs")
        if not isinstance(record["path"], str):
            raise MutationPolicyError(f"archived {label} class change path differs")
        _archived_digest(record["beforeSha256"], f"{label} class before")
        _archived_digest(record["afterSha256"], f"{label} class after")
    surface = value["effectiveSurface"]
    if not isinstance(surface, dict) or set(surface) != {"changedFields"} or not isinstance(surface["changedFields"], list):
        raise MutationPolicyError(f"archived {label} effective-surface schema differs")
    for record in surface["changedFields"]:
        if (
            not isinstance(record, dict)
            or set(record) != {"field", "before", "after"}
            or not isinstance(record["field"], str)
            or any(record[key] is not None and not isinstance(record[key], str) for key in ("before", "after"))
        ):
            raise MutationPolicyError(f"archived {label} effective-surface record differs")
    classpaths = value["classpaths"]
    allowed_classpaths = {"sourceDirs", "mutableCodePaths", "additionalClasspath", "launchClasspath"}
    if not isinstance(classpaths, dict) or not set(classpaths).issubset(allowed_classpaths):
        raise MutationPolicyError(f"archived {label} classpath schema differs")
    for record in classpaths.values():
        if (
            not isinstance(record, dict)
            or set(record) != {"before", "after"}
            or any(not isinstance(item, str) for item in record.values())
        ):
            raise MutationPolicyError(f"archived {label} classpath record differs")


def _validate_archived_bootstrap(
    bootstrap: object,
    policy: dict[str, Any],
) -> dict[str, Any]:
    keys = {
        "environmentPolicy", "gitObjectView", "imageIdentity", "java",
        "observedToolBundleSha256", "observedTools", "profile", "profileSha256",
    }
    if not isinstance(bootstrap, dict) or set(bootstrap) != keys:
        raise MutationPolicyError("archived route bootstrap schema differs")
    profile_name = bootstrap.get("profile")
    profile = policy["bootstrapProfiles"].get(profile_name)
    if (
        not isinstance(profile, dict)
        or bootstrap.get("profileSha256") != sha256(canonical_json_bytes(profile))
        or bootstrap.get("environmentPolicy")
        != policy["executionEnvironmentPolicy"]["policyVersion"]
    ):
        raise MutationPolicyError("archived route bootstrap profile differs")
    image = profile.get("image") if profile_name == "linux-x86_64" else None
    if bootstrap.get("imageIdentity") != image:
        raise MutationPolicyError("archived route bootstrap image identity differs")
    observed = bootstrap.get("observedTools")
    if not isinstance(observed, dict) or set(observed) != {"bash", "env", "git", "python"}:
        raise MutationPolicyError("archived route observed-tool schema differs")
    tool_keys = {
        "entryPath", "resolvedPath", "entryType", "fileType", "mode", "sha256",
        "versionSha256",
    }
    for name, tool in observed.items():
        if not isinstance(tool, dict) or set(tool) != tool_keys:
            raise MutationPolicyError(f"archived route observed-tool schema differs: {name}")
        for digest_name in ("sha256", "versionSha256"):
            _archived_digest(tool.get(digest_name), f"route observed-tool {name} {digest_name}")
        if (
            tool.get("fileType") != "regular"
            or tool.get("entryType") not in {"regular", "symlink"}
            or re.fullmatch(r"[0-7]{4}", str(tool.get("mode"))) is None
            or not isinstance(tool.get("entryPath"), str)
            or not str(tool["entryPath"]).startswith("/")
            or not isinstance(tool.get("resolvedPath"), str)
            or not str(tool["resolvedPath"]).startswith("/")
        ):
            raise MutationPolicyError(f"archived route observed-tool value differs: {name}")
    if bootstrap.get("observedToolBundleSha256") != sha256(canonical_json_bytes(observed)):
        raise MutationPolicyError("archived route observed-tool bundle differs")
    java = bootstrap.get("java")
    if not isinstance(java, dict) or set(java) != {
        "major", "vendorFamily", "toolchainRole", "runtimeVersion", "executableSha256",
    }:
        raise MutationPolicyError("archived route Java identity schema differs")
    _archived_digest(java.get("executableSha256"), "route Java executable")
    if (
        java.get("major") != 21
        or java.get("vendorFamily") != "Eclipse Adoptium/Temurin"
        or java.get("toolchainRole") != "mutation-runtime"
        or not isinstance(java.get("runtimeVersion"), str)
        or not str(java["runtimeVersion"]).startswith("21")
    ):
        raise MutationPolicyError("archived route Java identity differs")
    git_view = bootstrap.get("gitObjectView")
    if not isinstance(git_view, dict) or set(git_view) != {
        "inventorySha256", "policy", "prefixSha256",
    }:
        raise MutationPolicyError("archived route Git object-view schema differs")
    _archived_digest(git_view.get("inventorySha256"), "route Git inventory")
    _archived_digest(git_view.get("prefixSha256"), "route Git prefix")
    if git_view.get("policy") != policy["gitObjectViewPolicy"]["policyVersion"]:
        raise MutationPolicyError("archived route Git object-view policy differs")
    return bootstrap


def _archived_route_per_run(
    bootstrap: dict[str, Any],
    *,
    schema: str,
    route_receipt_sha256: str | None = None,
    configuration_sha256_by_module: dict[str, str] | None = None,
) -> dict[str, Any]:
    value: dict[str, Any] = {
        "schema": schema,
        "selectedProfile": bootstrap["profile"],
        "profileDefinitionSha256": bootstrap["profileSha256"],
        "imageIdentity": bootstrap["imageIdentity"],
        "observedToolBundleSha256": bootstrap["observedToolBundleSha256"],
        "javaExecutableSha256": bootstrap["java"]["executableSha256"],
        "javaRuntimeVersion": bootstrap["java"]["runtimeVersion"],
    }
    if route_receipt_sha256 is not None:
        value["routeReceiptSha256"] = route_receipt_sha256
    if configuration_sha256_by_module is not None:
        value["configurationSha256ByModule"] = configuration_sha256_by_module
    return value


def _validate_archived_components(
    raw_components: dict[str, bytes],
    *,
    candidate: str,
    receipt_value: dict[str, Any],
    transition: dict[str, Any],
    predecessor_capture_receipt_raw: bytes | None = None,
) -> dict[str, Any]:
    """Decode the complete archived run and reconstruct every typed predecessor edge."""
    policy, checked_policy_raw, checked_policy_hash = load_policy()
    policy_value = _archived_json(
        raw_components["policy"],
        "policy",
        canonical=False,
    )
    if not isinstance(policy_value, dict) or raw_components["policy"] != checked_policy_raw:
        raise MutationPolicyError("archived policy differs from the checked reviewed policy")

    source_raw = raw_components["sourceCommit"]
    try:
        source_commit = source_raw.decode("ascii").removesuffix("\n")
    except UnicodeDecodeError as error:
        raise MutationPolicyError("archived source commit schema differs") from error
    if source_raw != (source_commit + "\n").encode("ascii") or re.fullmatch(r"[0-9a-f]{40}", source_commit) is None:
        raise MutationPolicyError("archived source commit schema differs")

    route = _archived_json(raw_components["route"], "route")
    route_keys = {
        "schemaVersion", "event", "sourceCommit", "baseCommit", "mergeBase",
        "policySha256", "environmentPolicy", "gitObjectViewPolicy", "bootstrap",
        "hostNeutralMutationIdentity", "hostNeutralMutationIdentitySha256",
        "perRunExecutionProvenance", "perRunExecutionProvenanceSha256", "changes",
        "selectedModules", "selectedTasks", "status",
    }
    if not isinstance(route, dict) or set(route) != route_keys or route.get("schemaVersion") != 1:
        raise MutationPolicyError("archived route schema differs")
    bootstrap = _validate_archived_bootstrap(route.get("bootstrap"), policy)
    neutral = host_neutral_mutation_identity(policy)
    neutral_hash = sha256(canonical_json_bytes(neutral))
    selected_modules = sorted(policy["modules"])
    selected_tasks = [policy["modules"][name]["pitestTask"] for name in selected_modules]
    expected_route_per_run = _archived_route_per_run(
        bootstrap,
        schema="per-run-execution-provenance-route-v1",
    )
    if (
        route.get("event") != "local-all"
        or route.get("sourceCommit") != source_commit
        or route.get("baseCommit") is not None
        or route.get("mergeBase") is not None
        or route.get("policySha256") != checked_policy_hash
        or route.get("environmentPolicy")
        != policy["executionEnvironmentPolicy"]["policyVersion"]
        or route.get("gitObjectViewPolicy") != policy["gitObjectViewPolicy"]["policyVersion"]
        or route.get("hostNeutralMutationIdentity") != neutral
        or route.get("hostNeutralMutationIdentitySha256") != neutral_hash
        or route.get("perRunExecutionProvenance") != expected_route_per_run
        or route.get("perRunExecutionProvenanceSha256")
        != sha256(canonical_json_bytes(expected_route_per_run))
        or route.get("changes") != []
        or route.get("selectedModules") != selected_modules
        or route.get("selectedTasks") != selected_tasks
        or route.get("status") != "selected"
    ):
        raise MutationPolicyError("archived route typed values differ")

    tasks_raw = raw_components["tasks"]
    if tasks_raw != "".join(f"{task}\n" for task in selected_tasks).encode("utf-8"):
        raise MutationPolicyError("archived tasks projection differs")
    expected_route_receipt = receipt(
        "pitest-route-receipt-v1",
        {"policy": checked_policy_raw, "route": raw_components["route"], "tasks": tasks_raw},
        sourceCommit=source_commit,
        status="selected",
        bootstrap=bootstrap,
    )
    route_receipt = _archived_json(raw_components["routeReceipt"], "route receipt")
    if route_receipt != expected_route_receipt:
        raise MutationPolicyError("archived route receipt typed links differ")
    route_receipt_hash = sha256(raw_components["routeReceipt"])

    attempt = _archived_json(raw_components["attempt"], "attempt")
    attempt_per_run = _archived_route_per_run(
        bootstrap,
        schema="per-run-execution-provenance-attempt-v1",
        route_receipt_sha256=route_receipt_hash,
    )
    expected_attempt = receipt(
        "pitest-attempt-v1",
        {
            "policy": checked_policy_raw,
            "route": raw_components["route"],
            "routeReceipt": raw_components["routeReceipt"],
            "tasks": tasks_raw,
        },
        sourceCommit=source_commit,
        selectedTasks=selected_tasks,
        gradleFlags=policy["canonicalGradleFlags"],
        environmentPolicy=policy["executionEnvironmentPolicy"]["policyVersion"],
        gitObjectViewPolicy=policy["gitObjectViewPolicy"]["policyVersion"],
        bootstrap=bootstrap,
        hostNeutralMutationIdentity=neutral,
        hostNeutralMutationIdentitySha256=neutral_hash,
        perRunExecutionProvenance=attempt_per_run,
        perRunExecutionProvenanceSha256=sha256(canonical_json_bytes(attempt_per_run)),
    )
    if attempt != expected_attempt:
        raise MutationPolicyError("archived attempt schema differs from typed route links")

    configurations: dict[str, dict[str, Any]] = {}
    configuration_hashes: dict[str, str] = {}
    configuration_outer_keys = {
        "schemaVersion", "projectPath", "pluginVersion", "pitestVersion", "threads",
        "mutationThreshold", "targetClasses", "targetTests", "addJUnitPlatformLauncher",
        "defaultCharacterEncoding", "directPitestGuard", "enforcementPhase",
        "environmentPolicy", "policySha256", "routeReceiptSha256",
        "hostNeutralMutationIdentity", "hostNeutralMutationIdentitySha256",
        "perRunExecutionProvenance", "perRunExecutionProvenanceSha256", "values",
    }
    for name in selected_modules:
        configuration_raw = raw_components[f"configuration:{name}"]
        configuration = _archived_json(configuration_raw, f"configuration {name}")
        if not isinstance(configuration, dict) or set(configuration) != configuration_outer_keys:
            raise MutationPolicyError(f"archived configuration schema differs: {name}")
        values = configuration.get("values")
        if (
            not isinstance(values, dict)
            or any(not isinstance(key, str) or not isinstance(item, str) for key, item in values.items())
            or sha256(canonical_json_bytes(sorted(values)))
            != _ARCHIVED_CONFIGURATION_VALUE_KEYS_SHA256
        ):
            raise MutationPolicyError(f"archived configuration effective surface schema differs: {name}")
        module = policy["modules"][name]
        expected_threshold = module["floorPercent"]
        if (
            configuration.get("schemaVersion") != 1
            or configuration.get("projectPath") != module["modulePath"]
            or configuration.get("pluginVersion") != policy["pitest"]["pluginVersion"]
            or configuration.get("pitestVersion") != policy["pitest"]["pitestVersion"]
            or configuration.get("threads") != 2
            or configuration.get("mutationThreshold") != expected_threshold
            or configuration.get("targetClasses") != module["targetClasses"]
            or configuration.get("targetTests") != module["targetTests"]
            or configuration.get("addJUnitPlatformLauncher") is not False
            or configuration.get("defaultCharacterEncoding") != "UTF-8"
            or configuration.get("directPitestGuard") != "RejectDirectPitestAction:first"
            or configuration.get("enforcementPhase") != policy["enforcementPhase"]
            or configuration.get("environmentPolicy")
            != policy["executionEnvironmentPolicy"]["policyVersion"]
            or configuration.get("policySha256") != checked_policy_hash
            or configuration.get("routeReceiptSha256") != route_receipt_hash
            or configuration.get("hostNeutralMutationIdentity") != neutral
            or configuration.get("hostNeutralMutationIdentitySha256")
            != _gradle_canonical_digest(neutral)
            or values.get("pit.mutators") != "DEFAULTS"
            or values.get("pit.targetClasses") != "\u001f".join(module["targetClasses"])
            or values.get("pit.targetTests") != "\u001f".join(module["targetTests"])
            or values.get("pit.threads") != "2"
            or values.get("pit.mutationThreshold")
            != ("<null>" if expected_threshold is None else str(expected_threshold))
            or values.get("pit.outputFormats") != "HTML\u001fXML"
            or values.get("java.defaultCharacterEncoding") != "UTF-8"
            or values.get("command.managedEncodingArguments") != "-Dfile.encoding=UTF-8"
        ):
            raise MutationPolicyError(f"archived configuration typed values differ: {name}")
        expected_configuration_per_run = _archived_route_per_run(
            bootstrap,
            schema="per-run-execution-provenance-configuration-v1",
            route_receipt_sha256=route_receipt_hash,
        )
        if configuration.get("perRunExecutionProvenance") != expected_configuration_per_run:
            raise MutationPolicyError(f"archived configuration per-run links differ: {name}")
        if configuration.get("perRunExecutionProvenanceSha256") != _gradle_canonical_digest(
            expected_configuration_per_run
        ):
            raise MutationPolicyError(f"archived configuration per-run digest differs: {name}")
        configurations[name] = configuration
        configuration_hashes[name] = sha256(configuration_raw)

    completion = _archived_json(raw_components["completion"], "completion")
    completion_keys = {
        "schema", "predecessors", "sourceCommit", "selectedTasks", "gradleExit",
        "hostNeutralMutationIdentity", "hostNeutralMutationIdentitySha256",
        "perRunExecutionProvenance", "perRunExecutionProvenanceSha256", "reports",
    }
    if not isinstance(completion, dict) or set(completion) != completion_keys:
        raise MutationPolicyError("archived completion schema differs")
    reports = completion.get("reports")
    if not isinstance(reports, list):
        raise MutationPolicyError("archived completion reports schema differs")
    _validate_baseline_reports(reports)
    reports_by_module = {
        report["module"]: report for report in reports if isinstance(report, dict)
    }
    if set(reports_by_module) != set(selected_modules):
        raise MutationPolicyError("archived completion report module set differs")

    decoded_reports: list[dict[str, Any]] = []
    for name in selected_modules:
        expected_report = reports_by_module[name]
        stored_records = expected_report["records"]
        provenance_by_identity = {
            (
                item["mutatedClass"], item["mutatedMethod"], item["methodDescription"],
                item["mutator"], tuple(item["indexes"]),
            ): item
            for item in stored_records
        }
        if len(provenance_by_identity) != len(stored_records):
            raise MutationPolicyError(f"archived completion duplicate report identity: {name}")

        def archived_provenance(
            identity: tuple[object, ...],
            source_file: str,
        ) -> tuple[str, str, str]:
            item = provenance_by_identity.get(identity)
            if item is None:
                raise MutationPolicyError(f"archived XML mutation is absent from completion: {name}")
            if Path(item["sourcePath"]).name != source_file:
                raise MutationPolicyError(f"archived XML SourceFile link differs: {name}")
            return item["sourcePath"], item["classPath"], item["classSha256"]

        parsed = parse_pitest_xml(
            raw_components[f"xml:{name}"],
            module=name,
            package_root=policy["modules"][name]["packageRoot"],
            archived_provenance_lookup=archived_provenance,
        )
        html = _validate_archived_path_digest_inventory(
            _archived_json(raw_components[f"html:{name}"], f"HTML inventory {name}"),
            f"HTML inventory {name}",
            path_prefix=(
                policy["modules"][name]["modulePath"].removeprefix(":").replace(":", "/")
                + "/build/reports/pitest/"
            ),
            path_suffix=".html",
        )
        semantic = _archived_json(raw_components[f"semantic:{name}"], f"semantic report {name}")
        if semantic != {"semanticSha256": parsed.semantic_sha256}:
            raise MutationPolicyError(f"archived semantic report link differs: {name}")
        decoded = {
            "module": name,
            "configurationPath": policy["modules"][name]["configurationPath"],
            "configurationSha256": configuration_hashes[name],
            "reportPath": policy["modules"][name]["reportPath"],
            "rawSha256": parsed.raw_sha256,
            "semanticSha256": parsed.semantic_sha256,
            "counters": parsed.counters,
            "packages": parsed.package_counters,
            "classes": parsed.class_counters,
            "rationals": parsed.rational_summary(),
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
                for item in parsed.records
            ],
            "html": html,
        }
        if decoded != expected_report:
            raise MutationPolicyError(f"archived XML/semantic/HTML completion links differ: {name}")
        decoded_reports.append(decoded)

    completion_per_run = _archived_route_per_run(
        bootstrap,
        schema="per-run-execution-provenance-completion-v1",
        route_receipt_sha256=route_receipt_hash,
        configuration_sha256_by_module=configuration_hashes,
    )
    expected_completion = receipt(
        "pitest-completion-v1",
        {
            "attempt": raw_components["attempt"],
            "policy": checked_policy_raw,
            "route": raw_components["route"],
            "routeReceipt": raw_components["routeReceipt"],
            "tasks": tasks_raw,
        },
        sourceCommit=source_commit,
        selectedTasks=selected_tasks,
        gradleExit=0,
        hostNeutralMutationIdentity=neutral,
        hostNeutralMutationIdentitySha256=neutral_hash,
        perRunExecutionProvenance=completion_per_run,
        perRunExecutionProvenanceSha256=sha256(canonical_json_bytes(completion_per_run)),
        reports=decoded_reports,
    )
    if completion != expected_completion:
        raise MutationPolicyError("archived completion typed links differ")

    observation_git = None
    measurement = _archived_json(raw_components["measurement"], "measurement")
    measurement_keys = {
        "schemaVersion", "sourceCommit", "policySha256", "enforcementPhase",
        "observationGitConfig", "gitObjectViewIdentity", "completionSha256",
        "routeSha256", "routeReceiptSha256", "hostNeutralMutationIdentity",
        "hostNeutralMutationIdentitySha256", "perRunExecutionProvenance",
        "perRunExecutionProvenanceSha256", "reports",
    }
    if not isinstance(measurement, dict) or set(measurement) != measurement_keys:
        raise MutationPolicyError("archived measurement schema differs")
    observation_git = measurement.get("observationGitConfig")
    if not isinstance(observation_git, dict) or set(observation_git) != {"records", "sha256"}:
        raise MutationPolicyError("archived measurement Git configuration schema differs")
    records = observation_git.get("records")
    if (
        not isinstance(records, list)
        or records != sorted(records, key=lambda item: item.get("path", "") if isinstance(item, dict) else "")
        or len(records) != len({item.get("path") for item in records if isinstance(item, dict)})
        or any(
            not isinstance(item, dict)
            or set(item) != {"path", "blob", "mode"}
            or not isinstance(item.get("path"), str)
            or item["path"].startswith("/")
            or ".." in Path(item["path"]).parts
            or not isinstance(item.get("blob"), str)
            or re.fullmatch(r"[0-9a-f]{40}", item["blob"]) is None
            or not isinstance(item.get("mode"), str)
            or re.fullmatch(r"[0-7]{6}", item["mode"]) is None
            for item in records
        )
        or observation_git.get("sha256") != sha256(canonical_json_bytes(records))
    ):
        raise MutationPolicyError("archived measurement Git configuration differs")
    expected_measurement = {
        **measurement,
        "schemaVersion": 1,
        "sourceCommit": source_commit,
        "policySha256": checked_policy_hash,
        "enforcementPhase": policy["enforcementPhase"],
        "gitObjectViewIdentity": bootstrap["gitObjectView"],
        "completionSha256": sha256(raw_components["completion"]),
        "routeSha256": sha256(raw_components["route"]),
        "routeReceiptSha256": route_receipt_hash,
        "hostNeutralMutationIdentity": neutral,
        "hostNeutralMutationIdentitySha256": neutral_hash,
        "perRunExecutionProvenance": completion_per_run,
        "perRunExecutionProvenanceSha256": sha256(canonical_json_bytes(completion_per_run)),
        "reports": decoded_reports,
    }
    if measurement != expected_measurement:
        raise MutationPolicyError("archived measurement typed links differ")

    summary = _archived_json(raw_components["verificationSummary"], "verification summary")
    legacy_without_capture_receipt = (
        candidate in LEGACY_ARCHIVED_CANDIDATES_WITHOUT_BASELINE_CAPTURE_RECEIPT
    )
    summary_keys = {
        "schemaVersion", "verificationMode", "transitionAxis", "status", "sourceCommit",
        "policySha256", "predecessorBaselineHash", "routeSha256", "routeReceiptSha256",
        "attemptSha256", "completionSha256", "hostNeutralMutationIdentity",
        "hostNeutralMutationIdentitySha256", "perRunExecutionProvenance",
        "perRunExecutionProvenanceSha256", "historicalLinuxComparison", "reports",
        "inventoryDelta", "violations",
    }
    if not legacy_without_capture_receipt:
        summary_keys.add("predecessorCaptureReceiptSha256")
    predecessor_capture_receipt: dict[str, Any] | None = None
    predecessor_capture_receipt_hash: str | None = None
    if not legacy_without_capture_receipt:
        if predecessor_capture_receipt_raw is None:
            raise MutationPolicyError(
                "archived predecessor baseline capture receipt is missing",
            )
        predecessor_capture_receipt_hash = sha256(predecessor_capture_receipt_raw)
        predecessor_capture_receipt = read_strict_json(predecessor_capture_receipt_raw)
        predecessor_baseline_hash = transition.get("predecessorBaselineSha256")
        predecessor_capture_path = (
            f"config/quality/mutation-captures/{predecessor_baseline_hash}.json"
        )
        linked_candidate, _, linked_receipt = _capture_receipt_link(
            predecessor_capture_receipt_raw,
            predecessor_capture_path,
        )
        if (
            linked_candidate != predecessor_baseline_hash
            or predecessor_capture_receipt != linked_receipt
            or canonical_json_bytes(predecessor_capture_receipt)
            != predecessor_capture_receipt_raw
        ):
            raise MutationPolicyError(
                "archived predecessor baseline capture receipt identity differs",
            )
    if (
        not isinstance(summary, dict)
        or set(summary) != summary_keys
        or summary.get("schemaVersion") != 2
        or summary.get("verificationMode") != "recapture-transition"
        or summary.get("transitionAxis") != transition.get("axis")
        or summary.get("status") != "pass"
        or summary.get("sourceCommit") != source_commit
        or summary.get("policySha256") != checked_policy_hash
        or summary.get("predecessorBaselineHash")
        != transition.get("predecessorBaselineSha256")
        or (
            not legacy_without_capture_receipt
            and summary.get("predecessorCaptureReceiptSha256")
            != predecessor_capture_receipt_hash
        )
        or summary.get("routeSha256") != sha256(raw_components["route"])
        or summary.get("routeReceiptSha256") != route_receipt_hash
        or summary.get("attemptSha256") != sha256(raw_components["attempt"])
        or summary.get("completionSha256") != sha256(raw_components["completion"])
        or summary.get("hostNeutralMutationIdentity") != neutral
        or summary.get("hostNeutralMutationIdentitySha256") != neutral_hash
        or summary.get("perRunExecutionProvenance") != completion_per_run
        or summary.get("perRunExecutionProvenanceSha256")
        != sha256(canonical_json_bytes(completion_per_run))
        or summary.get("reports") != decoded_reports
        or summary.get("inventoryDelta") != transition.get("inventoryDelta")
        or summary.get("violations") != []
    ):
        raise MutationPolicyError("archived verification summary typed links differ")
    if summary.get("historicalLinuxComparison") is not None and bootstrap["profile"] != "linux-x86_64":
        raise MutationPolicyError("archived verification summary Linux comparison differs")
    inventory_delta = summary["inventoryDelta"]
    if not isinstance(inventory_delta, dict) or set(inventory_delta) != set(selected_modules):
        raise MutationPolicyError("archived verification summary inventory delta differs")
    for name in selected_modules:
        _validate_archived_inventory_delta(inventory_delta[name], f"verification summary {name} delta")

    predecessor_receipt = _archived_json(
        raw_components["predecessorVerificationReceipt"],
        "predecessor verification receipt",
    )
    predecessor_receipt_components = {
        "policy": checked_policy_raw,
        "route": raw_components["route"],
        "routeReceipt": raw_components["routeReceipt"],
        "tasks": tasks_raw,
        "attempt": raw_components["attempt"],
        "completion": raw_components["completion"],
        "baseline": transition["predecessorBaselineSha256"].encode("ascii"),
        "summary": raw_components["verificationSummary"],
        **{
            f"configuration:{name}": raw_components[f"configuration:{name}"]
            for name in selected_modules
        },
        **{f"xml:{name}": raw_components[f"xml:{name}"] for name in selected_modules},
        **{
            f"semantic:{name}": canonical_json_bytes({
                "semanticSha256": reports_by_module[name]["semanticSha256"],
                "records": reports_by_module[name]["records"],
            })
            for name in selected_modules
        },
        **{f"html:{name}": raw_components[f"html:{name}"] for name in selected_modules},
    }
    if not legacy_without_capture_receipt:
        if predecessor_capture_receipt_raw is None:
            raise MutationPolicyError(
                "archived predecessor baseline capture receipt snapshot was lost",
            )
        predecessor_receipt_components["baselineCaptureReceipt"] = (
            predecessor_capture_receipt_raw
        )
    expected_predecessor_receipt = receipt(
        "pitest-verification-receipt-v1",
        predecessor_receipt_components,
        sourceCommit=source_commit,
        status="selected-transition-verified",
        policySha256=checked_policy_hash,
        hostNeutralMutationIdentitySha256=neutral_hash,
        perRunExecutionProvenanceSha256=sha256(canonical_json_bytes(completion_per_run)),
        historicalLinuxComparison=summary.get("historicalLinuxComparison"),
    )
    # The baseline predecessor is represented by its immutable raw SHA identity in this
    # transition receipt; it is not a second copy of the candidate baseline bytes.
    expected_predecessor_receipt["predecessors"]["baseline"] = transition[
        "predecessorBaselineSha256"
    ]
    if predecessor_receipt != expected_predecessor_receipt:
        raise MutationPolicyError("archived predecessor verification receipt typed links differ")

    manifest = receipt_value["evidenceManifest"]
    if (
        manifest.get("sourceCommit") != source_commit
        or manifest.get("policySha256") != checked_policy_hash
        or manifest.get("hostNeutralMutationIdentitySha256") != neutral_hash
        or manifest.get("perRunExecutionProvenanceSha256")
        != sha256(canonical_json_bytes(completion_per_run))
        or manifest.get("predecessorVerificationReceiptHash")
        != sha256(raw_components["predecessorVerificationReceipt"])
        or transition.get("candidateBaselineSha256") != candidate
        or transition.get("sourceCommit") != source_commit
        or transition.get("policySha256") != checked_policy_hash
        or transition.get("routeSha256") != sha256(raw_components["route"])
        or transition.get("completionSha256") != sha256(raw_components["completion"])
        or transition.get("predecessorVerificationReceiptSha256")
        != sha256(raw_components["predecessorVerificationReceipt"])
        or transition.get("newInputIdentity", {}).get("hostNeutralMutationIdentitySha256")
        != neutral_hash
    ):
        raise MutationPolicyError("archived receipt/transition typed links differ")
    return {
        "sourceCommit": source_commit,
        "policySha256": checked_policy_hash,
        "hostNeutralMutationIdentitySha256": neutral_hash,
        "perRunExecutionProvenanceSha256": sha256(canonical_json_bytes(completion_per_run)),
        "mutationInputIdentitySha256": transition["newInputIdentity"][
            "mutationInputIdentitySha256"
        ],
    }


def _validate_component_archive(
    git: GitExecutor,
    *,
    candidate: str,
    receipt: dict[str, Any],
    components: dict[str, str],
) -> dict[str, bytes]:
    reference = receipt.get("evidenceArchive")
    if not isinstance(reference, dict):
        raise MutationPolicyError("post-checkpoint successor has no independent component archive")
    archive_path = f"config/quality/mutation-evidence/{candidate}/archive.json"
    if reference.get("path") != archive_path:
        raise MutationPolicyError("capture archive path differs from the candidate")
    archive_raw, introduction = _checked_blob_info(
        git,
        REPOSITORY_ROOT / archive_path,
        "capture evidence archive",
    )
    if sha256(archive_raw) != reference.get("sha256"):
        raise MutationPolicyError("capture archive checked blob hash differs")
    archive = read_strict_json(archive_raw)
    expected_keys = {"schema", "candidateBaselineSha256", "components"}
    if (
        not isinstance(archive, dict)
        or set(archive) != expected_keys
        or archive.get("schema") != "pitest-capture-evidence-archive-v1"
        or archive.get("candidateBaselineSha256") != candidate
    ):
        raise MutationPolicyError("capture archive closed schema differs")
    records = archive.get("components")
    if not isinstance(records, dict) or set(records) != set(components):
        raise MutationPolicyError("capture archive component set differs")
    expected_paths = {archive_path}
    component_bytes: dict[str, bytes] = {}
    for index, name in enumerate(sorted(components)):
        record = records.get(name)
        component_path = f"config/quality/mutation-evidence/{candidate}/components/{index:02d}.bin"
        if (
            not isinstance(record, dict)
            or set(record) != {"path", "sha256"}
            or record.get("path") != component_path
            or record.get("sha256") != components[name]
        ):
            raise MutationPolicyError(f"capture archive component record differs: {name}")
        component_raw, component_introduction = _checked_blob_info(
            git,
            REPOSITORY_ROOT / component_path,
            f"capture component bytes {name}",
        )
        if component_introduction != introduction:
            raise MutationPolicyError("capture archive and component bytes must share one introduction commit")
        if sha256(component_raw) != components[name]:
            raise MutationPolicyError(f"capture archive component bytes differ: {name}")
        private_root = str(REPOSITORY_ROOT).encode("utf-8")
        if private_root in component_raw or b"SECRET_TOKEN" in component_raw:
            raise MutationPolicyError(f"capture archive contains a host-private path or secret: {name}")
        component_bytes[name] = component_raw
        expected_paths.add(component_path)
    actual_paths = set(_checked_tree_paths(
        git,
        EVIDENCE_ARCHIVE_ROOT / candidate,
        "capture evidence archive",
    ))
    if actual_paths != expected_paths:
        raise MutationPolicyError("capture archive file inventory differs")
    return component_bytes


def validate_capture_transition_chain(
    current_baseline_sha256: str,
    git: GitExecutor,
    *,
    current_baseline: dict[str, Any] | None = None,
) -> None:
    policy, _, _ = load_policy()
    historical_captures, historical_transitions, historical_archives = _validate_initial_anchor_and_history(
        git,
        policy,
    )
    capture_paths = _checked_tree_paths(git, CAPTURE_RECEIPT_ROOT, "capture receipt")
    transition_paths = _checked_tree_paths(git, TRANSITION_ROOT, "transition chain")
    archive_paths = _checked_tree_paths(git, EVIDENCE_ARCHIVE_ROOT, "capture evidence archive")
    try:
        actual_captures = sorted(relative(path) for path in CAPTURE_RECEIPT_ROOT.glob("*.json"))
        actual_transitions = sorted(relative(path) for path in TRANSITION_ROOT.glob("*.json"))
    except MutationPolicyError as error:
        raise MutationPolicyError("checked transition chain root is not repository-owned") from error
    if (
        actual_captures != capture_paths
        or actual_transitions != transition_paths
        or capture_paths != sorted(historical_captures)
        or transition_paths != sorted(historical_transitions)
        or archive_paths != sorted(historical_archives)
    ):
        raise MutationPolicyError("checked transition chain file inventory differs")
    if not capture_paths:
        raise MutationPolicyError("checked transition chain has no capture root")

    receipts: dict[str, tuple[str | None, dict[str, Any], bytes]] = {}
    for name in capture_paths:
        path = REPOSITORY_ROOT / name
        raw = _checked_blob(git, path, "capture receipt")
        candidate, predecessor, value = _capture_receipt_link(raw, name)
        if candidate in receipts:
            raise MutationPolicyError("checked transition chain has duplicate capture candidates")
        receipts[candidate] = (predecessor, value, raw)

    transition_keys = {
        "schema", "axis", "sourceCommit", "policySha256", "predecessorBaselineSha256",
        "candidateBaselineSha256", "captureReceiptSha256", "predecessorVerificationReceiptSha256",
        "routeSha256", "completionSha256", "oldInputIdentity", "newInputIdentity", "inventoryDelta",
    }
    archived_transition_keys = transition_keys | {"evidenceArchive"}
    transitions: dict[str, tuple[str, dict[str, Any]]] = {}
    for name in transition_paths:
        raw = _checked_blob(git, REPOSITORY_ROOT / name, "transition record")
        value = read_strict_json(raw)
        if (
            not isinstance(value, dict)
            or frozenset(value) not in {frozenset(transition_keys), frozenset(archived_transition_keys)}
            or value.get("schema") not in {"pitest-recapture-transition-v1", "pitest-recapture-transition-v2"}
        ):
            raise MutationPolicyError(f"checked transition chain record schema differs: {name}")
        predecessor = value.get("predecessorBaselineSha256")
        candidate = value.get("candidateBaselineSha256")
        old_identity = value.get("oldInputIdentity")
        new_identity = value.get("newInputIdentity")
        digest_fields = (
            "policySha256", "predecessorBaselineSha256", "candidateBaselineSha256",
            "captureReceiptSha256", "predecessorVerificationReceiptSha256",
            "routeSha256", "completionSha256",
        )
        if any(
            not isinstance(value.get(field), str)
            or re.fullmatch(r"[0-9a-f]{64}", value[field]) is None
            for field in digest_fields
        ):
            raise MutationPolicyError(f"checked transition chain digest differs: {name}")
        if (
            not isinstance(value.get("sourceCommit"), str)
            or re.fullmatch(r"[0-9a-f]{40}", value["sourceCommit"]) is None
            or value.get("axis") not in {
                "task7-spec-review-round1-corrections",
                "task7-spec-review-round2-corrections",
                REVIEWED_TRANSITION_AXIS,
            }
            or not isinstance(old_identity, dict)
            or not isinstance(new_identity, dict)
            or not isinstance(value.get("inventoryDelta"), dict)
            or set(value["inventoryDelta"]) != {"location", "settings", "station"}
        ):
            raise MutationPolicyError(f"checked transition chain link differs: {name}")
        old_schema = old_identity.get("schemaVersion")
        expected_old_keys = {"schemaVersion", "hostNeutralMutationIdentitySha256"}
        if old_schema == 2:
            expected_old_keys.add("mutationInputIdentitySha256")
        if old_schema not in {1, 2} or set(old_identity) != expected_old_keys:
            raise MutationPolicyError(f"checked transition chain old input identity differs: {name}")
        if set(new_identity) != {
            "schemaVersion", "mutationInputIdentitySha256", "hostNeutralMutationIdentitySha256",
        } or new_identity.get("schemaVersion") != 2:
            raise MutationPolicyError(f"checked transition chain new input identity differs: {name}")
        for identity, fields in (
            (old_identity, set(old_identity) - {"schemaVersion"}),
            (new_identity, set(new_identity) - {"schemaVersion"}),
        ):
            if any(
                not isinstance(identity.get(field), str)
                or re.fullmatch(r"[0-9a-f]{64}", identity[field]) is None
                for field in fields
            ):
                raise MutationPolicyError(f"checked transition chain input identity digest differs: {name}")
        input_identity = new_identity.get("mutationInputIdentitySha256")
        if Path(name).name != f"{predecessor}-to-{input_identity}.json":
            raise MutationPolicyError(f"checked transition chain filename differs: {name}")
        if set(value) == archived_transition_keys:
            archive = value.get("evidenceArchive")
            if (
                value.get("schema") != "pitest-recapture-transition-v2"
                or not isinstance(archive, dict)
                or set(archive) != {"path", "sha256"}
                or archive.get("path") != f"config/quality/mutation-evidence/{candidate}/archive.json"
                or not isinstance(archive.get("sha256"), str)
                or re.fullmatch(r"[0-9a-f]{64}", archive["sha256"]) is None
            ):
                raise MutationPolicyError(f"checked transition archive reference differs: {name}")
        elif value.get("schema") != "pitest-recapture-transition-v1":
            raise MutationPolicyError(f"checked transition unarchived schema differs: {name}")
        if predecessor in transitions:
            raise MutationPolicyError("checked transition chain branches from one predecessor")
        transitions[predecessor] = (candidate, value)

    roots = sorted(candidate for candidate, (predecessor, _, _) in receipts.items() if predecessor is None)
    if len(roots) != 1:
        raise MutationPolicyError("checked transition chain must have one null-predecessor root")
    cursor = roots[0]
    visited_receipts = {cursor}
    visited_transitions: set[str] = set()
    prior_new_identity: dict[str, Any] | None = None
    final_transition: dict[str, Any] | None = None
    while cursor != current_baseline_sha256:
        edge = transitions.get(cursor)
        if edge is None:
            raise MutationPolicyError("checked transition chain is disconnected from current baseline")
        candidate, transition = edge
        candidate_receipt = receipts.get(candidate)
        if candidate_receipt is None:
            raise MutationPolicyError("checked transition chain candidate receipt is missing")
        predecessor, receipt_value, receipt_raw = candidate_receipt
        if predecessor != cursor:
            raise MutationPolicyError("checked transition chain receipt predecessor differs")
        if transition["captureReceiptSha256"] != sha256(receipt_raw):
            raise MutationPolicyError("checked transition chain capture receipt hash differs")
        manifest = receipt_value.get("evidenceManifest")
        if not isinstance(manifest, dict):
            raise MutationPolicyError("checked transition chain successor manifest is missing")
        if manifest.get("predecessorBaselineHash") != cursor:
            raise MutationPolicyError("checked transition chain manifest predecessor differs")
        if manifest.get("predecessorVerificationReceiptHash") != transition["predecessorVerificationReceiptSha256"]:
            raise MutationPolicyError("checked transition chain predecessor receipt link differs")
        components = manifest.get("components")
        if not isinstance(components, dict):
            raise MutationPolicyError("checked transition chain successor components are missing")
        if (
            transition["candidateBaselineSha256"] != candidate
            or transition["sourceCommit"] != manifest.get("sourceCommit")
            or transition["policySha256"] != manifest.get("policySha256")
            or transition["routeSha256"] != components.get("route")
            or transition["completionSha256"] != components.get("completion")
            or transition["predecessorVerificationReceiptSha256"]
            != components.get("predecessorVerificationReceipt")
            or transition["newInputIdentity"]["hostNeutralMutationIdentitySha256"]
            != manifest.get("hostNeutralMutationIdentitySha256")
        ):
            raise MutationPolicyError("checked transition chain typed successor link differs")
        legacy_candidates = {
            Path(path).stem
            for path in policy["capturePolicy"]["legacyCheckpoint"]["captureReceiptSha256ByPath"]
        }
        if candidate not in legacy_candidates:
            archived_components = _validate_component_archive(
                git,
                candidate=candidate,
                receipt=receipt_value,
                components=components,
            )
            _validate_archived_components(
                archived_components,
                candidate=candidate,
                receipt_value=receipt_value,
                transition=transition,
                predecessor_capture_receipt_raw=receipts[cursor][2],
            )
            archive_reference = receipt_value["evidenceArchive"]
            if transition.get("evidenceArchive") != archive_reference:
                raise MutationPolicyError("transition and capture receipt archive links differ")
        if prior_new_identity is not None and transition["oldInputIdentity"] != prior_new_identity:
            raise MutationPolicyError("checked transition chain consecutive input identity differs")
        old_receipt = receipts[cursor][1]
        old_manifest = old_receipt.get("evidenceManifest")
        if isinstance(old_manifest, dict) and (
            transition["oldInputIdentity"]["hostNeutralMutationIdentitySha256"]
            != old_manifest.get("hostNeutralMutationIdentitySha256")
        ):
            raise MutationPolicyError("checked transition chain typed predecessor link differs")
        if candidate in visited_receipts:
            raise MutationPolicyError("checked transition chain contains a cycle")
        visited_transitions.add(cursor)
        visited_receipts.add(candidate)
        prior_new_identity = transition["newInputIdentity"]
        final_transition = transition
        cursor = candidate
    if visited_receipts != set(receipts) or visited_transitions != set(transitions):
        raise MutationPolicyError("checked transition chain contains disconnected or successor evidence")
    if current_baseline is not None:
        if final_transition is None:
            raise MutationPolicyError("current mutation baseline has no typed successor transition")
        if (
            final_transition["candidateBaselineSha256"] != current_baseline_sha256
            or final_transition["sourceCommit"] != current_baseline.get("sourceCommit")
            or final_transition["policySha256"] != current_baseline.get("policySha256")
            or final_transition["predecessorBaselineSha256"]
            != current_baseline.get("predecessorBaselineHash")
            or final_transition["predecessorVerificationReceiptSha256"]
            != current_baseline.get("predecessorVerificationReceiptHash")
            or final_transition["newInputIdentity"].get("mutationInputIdentitySha256")
            != current_baseline.get("mutationInputIdentitySha256")
            or final_transition["newInputIdentity"].get("hostNeutralMutationIdentitySha256")
            != current_baseline.get("hostNeutralMutationIdentitySha256")
        ):
            raise MutationPolicyError("current mutation baseline differs from typed successor transition")


def validate_baseline_capture_receipt(
    baseline_raw: bytes,
    baseline: dict[str, Any],
    *,
    git: GitExecutor | None = None,
    trusted_components: dict[str, bytes] | None = None,
    capture_receipt_raw: bytes | None = None,
    validate_transition_chain: bool = True,
) -> dict[str, Any]:
    validate_baseline_schema(baseline)
    if canonical_json_bytes(baseline) != baseline_raw:
        raise MutationPolicyError("mutation baseline bytes are not canonical JSON")
    candidate_hash = sha256(baseline_raw)
    receipt_path = CAPTURE_RECEIPT_ROOT / f"{candidate_hash}.json"
    receipt_raw = (
        capture_receipt_raw
        if capture_receipt_raw is not None
        else read_bytes(receipt_path)
    )
    value = read_strict_json(receipt_raw)
    if not isinstance(value, dict):
        raise MutationPolicyError("mutation capture receipt must be an object")
    if canonical_json_bytes(value) != receipt_raw:
        raise MutationPolicyError("mutation capture receipt bytes are not canonical JSON")
    expected_keys = {
        "candidateBaselineSha256",
        "captureEvidenceDigest",
        "evidenceManifest",
        "predecessorBaselineHash",
        "schema",
    }
    archived_expected_keys = expected_keys | {"evidenceArchive"}
    if (
        frozenset(value) not in {frozenset(expected_keys), frozenset(archived_expected_keys)}
        or value.get("schema") not in {"pitest-capture-receipt-v1", "pitest-capture-receipt-v2"}
        or (set(value) == expected_keys and value.get("schema") != "pitest-capture-receipt-v1")
        or (set(value) == archived_expected_keys and value.get("schema") != "pitest-capture-receipt-v2")
    ):
        raise MutationPolicyError("mutation capture receipt keys differ from the closed schema")
    if value["candidateBaselineSha256"] != candidate_hash:
        raise MutationPolicyError("mutation capture receipt candidate hash differs")
    if value["captureEvidenceDigest"] != baseline.get("captureEvidenceDigest"):
        raise MutationPolicyError("mutation capture receipt evidence digest differs")
    if value["predecessorBaselineHash"] != baseline.get("predecessorBaselineHash"):
        raise MutationPolicyError("mutation capture receipt predecessor differs")
    manifest = value.get("evidenceManifest")
    if not isinstance(manifest, dict) or manifest.get("schema") != "pitest-capture-evidence-manifest-v1":
        raise MutationPolicyError("mutation capture receipt evidence manifest is missing")
    manifest_keys = {
        "schema", "sourceCommit", "policySha256", "predecessorBaselineHash",
        "predecessorVerificationReceiptHash", "hostNeutralMutationIdentitySha256",
        "perRunExecutionProvenanceSha256", "components",
    }
    if set(manifest) != manifest_keys:
        raise MutationPolicyError("mutation capture receipt evidence manifest keys differ")
    components = manifest.get("components")
    expected_components = {
        "policy", "sourceCommit", "route", "tasks", "routeReceipt", "attempt",
        "configuration:location", "configuration:settings", "configuration:station",
        "completion", "measurement", "xml:location", "xml:settings", "xml:station",
        "semantic:location", "semantic:settings", "semantic:station",
        "html:location", "html:settings", "html:station", "verificationSummary",
    }
    if baseline["predecessorBaselineHash"] is not None:
        expected_components.add("predecessorVerificationReceipt")
    if not isinstance(components, dict) or set(components) != expected_components:
        raise MutationPolicyError("mutation capture receipt evidence manifest component set differs")
    for name, digest in components.items():
        if not isinstance(digest, str) or re.fullmatch(r"[0-9a-f]{64}", digest) is None:
            raise MutationPolicyError(f"mutation capture component hash is invalid: {name}")
    baseline_component_bindings = {
        "policy": baseline["policySha256"],
        "sourceCommit": sha256((baseline["sourceCommit"] + "\n").encode("ascii")),
    }
    for report in baseline["reports"]:
        name = report["module"]
        baseline_component_bindings[f"configuration:{name}"] = report["configurationSha256"]
        baseline_component_bindings[f"xml:{name}"] = report["rawSha256"]
        baseline_component_bindings[f"semantic:{name}"] = sha256(
            canonical_json_bytes({"semanticSha256": report["semanticSha256"]}),
        )
        baseline_component_bindings[f"html:{name}"] = sha256(canonical_json_bytes(report["html"]))
    if baseline["predecessorVerificationReceiptHash"] is not None:
        baseline_component_bindings["predecessorVerificationReceipt"] = baseline[
            "predecessorVerificationReceiptHash"
        ]
    for name, digest in baseline_component_bindings.items():
        if components.get(name) != digest:
            raise MutationPolicyError(f"mutation capture component differs from typed baseline: {name}")
    trust_git = git
    if trusted_components is not None:
        if set(trusted_components) != set(components):
            raise MutationPolicyError("trusted capture component byte set differs")
        for name, digest in components.items():
            if sha256(trusted_components[name]) != digest:
                raise MutationPolicyError(f"trusted capture component bytes differ: {name}")
    else:
        trust_git = trust_git or _capture_trust_git()
        checked_receipt_raw = _checked_blob(trust_git, receipt_path, "capture receipt")
        checked_receipt = read_strict_json(checked_receipt_raw)
        checked_manifest = checked_receipt.get("evidenceManifest") if isinstance(checked_receipt, dict) else None
        checked_components = checked_manifest.get("components") if isinstance(checked_manifest, dict) else None
        if not isinstance(checked_components, dict):
            raise MutationPolicyError("trusted capture receipt has no checked component manifest")
        for name, digest in components.items():
            if checked_components.get(name) != digest:
                raise MutationPolicyError(f"trusted capture component differs from checked Git: {name}")
    if manifest["sourceCommit"] != baseline["sourceCommit"]:
        raise MutationPolicyError("mutation capture evidence source commit differs")
    if manifest["policySha256"] != baseline["policySha256"]:
        raise MutationPolicyError("mutation capture evidence policy differs")
    if manifest["predecessorBaselineHash"] != baseline["predecessorBaselineHash"]:
        raise MutationPolicyError("mutation capture evidence predecessor differs")
    if manifest["predecessorVerificationReceiptHash"] != baseline["predecessorVerificationReceiptHash"]:
        raise MutationPolicyError("mutation capture evidence predecessor receipt differs")
    if manifest["hostNeutralMutationIdentitySha256"] != baseline["hostNeutralMutationIdentitySha256"]:
        raise MutationPolicyError("mutation capture evidence neutral identity differs")
    if not isinstance(manifest["perRunExecutionProvenanceSha256"], str) or re.fullmatch(r"[0-9a-f]{64}", manifest["perRunExecutionProvenanceSha256"]) is None:
        raise MutationPolicyError("mutation capture evidence per-run provenance digest is invalid")
    manifest_digest = sha256(canonical_json_bytes(manifest))
    if manifest_digest != baseline["captureEvidenceDigest"] or manifest_digest != value["captureEvidenceDigest"]:
        raise MutationPolicyError("mutation capture evidence manifest digest differs")
    forbidden = {
        "captureReceiptSha256", "verificationReceiptHash", "successorHash", "baselineSha256",
    }
    if set(value) & forbidden or set(baseline) & forbidden:
        raise MutationPolicyError("mutation baseline/capture receipt contains a self or successor hash")
    if validate_transition_chain:
        trust_git = trust_git or _capture_trust_git()
        validate_capture_transition_chain(
            candidate_hash,
            trust_git,
            current_baseline=baseline,
        )
    return value


def _validate_baseline_reports(reports: list[object]) -> None:
    counter_keys = {"KILLED", "NO_COVERAGE", "SURVIVED", "total"}

    def counters(value: object, label: str) -> dict[str, int]:
        if not isinstance(value, dict) or set(value) != counter_keys:
            raise MutationPolicyError(f"mutation baseline {label} counters schema differs")
        if any(type(item) is not int or item < 0 for item in value.values()):
            raise MutationPolicyError(f"mutation baseline {label} counters must be nonnegative integers")
        if value["total"] != value["KILLED"] + value["NO_COVERAGE"] + value["SURVIVED"]:
            raise MutationPolicyError(f"mutation baseline {label} counters do not sum")
        return value

    def rational(numerator: int, denominator: int) -> dict[str, object]:
        return {
            "state": "applicable",
            "numerator": numerator,
            "denominator": denominator,
            "value": f"{numerator}/{denominator}",
        }

    report_keys = {
        "module", "configurationPath", "configurationSha256", "reportPath", "rawSha256",
        "semanticSha256", "counters", "packages", "classes", "rationals", "records", "html",
    }
    record_keys = {
        "sourcePath", "classPath", "classSha256", "mutatedClass", "mutatedMethod",
        "methodDescription", "mutator", "indexes", "status",
    }
    for raw_report in reports:
        if not isinstance(raw_report, dict) or set(raw_report) != report_keys:
            raise MutationPolicyError("mutation baseline report schema differs")
        module = raw_report["module"]
        if not isinstance(module, str):
            raise MutationPolicyError("mutation baseline report module differs")
        for name in ("configurationSha256", "rawSha256", "semanticSha256"):
            if not isinstance(raw_report[name], str) or re.fullmatch(r"[0-9a-f]{64}", raw_report[name]) is None:
                raise MutationPolicyError(f"mutation baseline {module} {name} is invalid")
        for name in ("configurationPath", "reportPath"):
            if not isinstance(raw_report[name], str) or not raw_report[name] or raw_report[name].startswith("/") or ".." in Path(raw_report[name]).parts:
                raise MutationPolicyError(f"mutation baseline {module} {name} differs")
        records = raw_report["records"]
        if not isinstance(records, list) or not records:
            raise MutationPolicyError(f"mutation baseline {module} records differ")
        identities: list[tuple[object, ...]] = []
        semantic: list[dict[str, object]] = []
        counted: dict[str, int] = {"KILLED": 0, "NO_COVERAGE": 0, "SURVIVED": 0}
        package_counts: dict[str, dict[str, int]] = {}
        class_counts: dict[str, dict[str, int]] = {}
        for raw_record in records:
            if not isinstance(raw_record, dict) or set(raw_record) != record_keys:
                raise MutationPolicyError(f"mutation baseline {module} mutation record schema differs")
            if raw_record["status"] not in counted:
                raise MutationPolicyError(f"mutation baseline {module} mutation status differs")
            for name in ("sourcePath", "classPath", "mutatedClass", "mutatedMethod", "methodDescription", "mutator"):
                if not isinstance(raw_record[name], str) or not raw_record[name]:
                    raise MutationPolicyError(f"mutation baseline {module} mutation {name} differs")
            if raw_record["sourcePath"].startswith("/") or raw_record["classPath"].startswith("/"):
                raise MutationPolicyError(f"mutation baseline {module} mutation path is absolute")
            if not isinstance(raw_record["classSha256"], str) or re.fullmatch(r"[0-9a-f]{64}", raw_record["classSha256"]) is None:
                raise MutationPolicyError(f"mutation baseline {module} class digest differs")
            indexes = raw_record["indexes"]
            if not isinstance(indexes, list) or not indexes or any(type(index) is not int or index < 0 for index in indexes) or indexes != sorted(set(indexes)):
                raise MutationPolicyError(f"mutation baseline {module} mutation indexes differ")
            identity = (
                raw_record["mutatedClass"], raw_record["mutatedMethod"], raw_record["methodDescription"],
                raw_record["mutator"], tuple(indexes),
            )
            identities.append(identity)
            semantic.append({
                "mutatedClass": raw_record["mutatedClass"],
                "mutatedMethod": raw_record["mutatedMethod"],
                "methodDescription": raw_record["methodDescription"],
                "mutator": raw_record["mutator"],
                "indexes": indexes,
                "status": raw_record["status"],
            })
            status = raw_record["status"]
            counted[status] += 1
            package = raw_record["mutatedClass"].rsplit(".", 1)[0]
            for target, key in ((package_counts, package), (class_counts, raw_record["mutatedClass"])):
                bucket = target.setdefault(key, {"KILLED": 0, "NO_COVERAGE": 0, "SURVIVED": 0})
                bucket[status] += 1
        if identities != sorted(identities, key=lambda identity: tuple(str(part) for part in identity)) or len(set(identities)) != len(identities):
            raise MutationPolicyError(f"mutation baseline {module} mutation identities differ")
        total = {**counted, "total": len(records)}
        if counters(raw_report["counters"], module) != total:
            raise MutationPolicyError(f"mutation baseline {module} counters differ from records")
        expected_grouped = []
        for label, actual, grouped in (("packages", raw_report["packages"], package_counts), ("classes", raw_report["classes"], class_counts)):
            if not isinstance(actual, dict) or list(actual) != sorted(actual):
                raise MutationPolicyError(f"mutation baseline {module} {label} ordering differs")
            expected = {name: {**values, "total": sum(values.values())} for name, values in sorted(grouped.items())}
            for name, value in actual.items():
                counters(value, f"{module} {label} {name}")
            if actual != expected:
                raise MutationPolicyError(f"mutation baseline {module} {label} differ from records")
            expected_grouped.append(expected)
        if raw_report["semanticSha256"] != sha256(canonical_json_bytes(semantic)):
            raise MutationPolicyError(f"mutation baseline {module} semantic digest differs from records")
        strength_denominator = total["KILLED"] + total["SURVIVED"]
        expected_rationals = {
            "mutationScore": rational(total["KILLED"], total["total"]),
            "testStrength": (
                {"state": "not-applicable", "numerator": 0, "denominator": 0, "value": None}
                if strength_denominator == 0 else rational(total["KILLED"], strength_denominator)
            ),
            "noCoverageRate": rational(total["NO_COVERAGE"], total["total"]),
        }
        if raw_report["rationals"] != expected_rationals:
            raise MutationPolicyError(f"mutation baseline {module} rational counters differ")
        html = raw_report["html"]
        if not isinstance(html, list) or not html:
            raise MutationPolicyError(f"mutation baseline {module} HTML inventory differs")
        previous = ""
        for raw_item in html:
            if not isinstance(raw_item, dict) or set(raw_item) != {"path", "sha256"}:
                raise MutationPolicyError(f"mutation baseline {module} HTML record schema differs")
            if not isinstance(raw_item["path"], str) or raw_item["path"] <= previous or raw_item["path"].startswith("/"):
                raise MutationPolicyError(f"mutation baseline {module} HTML paths differ")
            previous = raw_item["path"]
            if not isinstance(raw_item["sha256"], str) or re.fullmatch(r"[0-9a-f]{64}", raw_item["sha256"]) is None:
                raise MutationPolicyError(f"mutation baseline {module} HTML digest differs")


def validate_baseline_schema(baseline: dict[str, Any]) -> None:
    expected_keys = {
        "schemaVersion", "sourceCommit", "policySha256", "observationGitConfig",
        "toolchainIdentity", "hostNeutralMutationIdentity", "hostNeutralMutationIdentitySha256",
        "effectiveCommandPlan", "executionEnvironmentIdentity", "gitObjectViewIdentity",
        "wrapperIdentity", "moduleInventories", "mutationInputIdentitySha256",
        "captureProfile", "profileHistory", "reports", "predecessorBaselineHash",
        "predecessorVerificationReceiptHash", "captureEvidenceDigest",
    }
    if set(baseline) != expected_keys or baseline.get("schemaVersion") != 2:
        raise MutationPolicyError("mutation baseline schema differs from strict version 2")
    for name in (
        "policySha256", "hostNeutralMutationIdentitySha256", "mutationInputIdentitySha256",
        "captureEvidenceDigest",
    ):
        if not isinstance(baseline.get(name), str) or re.fullmatch(r"[0-9a-f]{64}", baseline[name]) is None:
            raise MutationPolicyError(f"mutation baseline {name} is invalid")
    if not isinstance(baseline.get("sourceCommit"), str) or re.fullmatch(r"[0-9a-f]{40}", baseline["sourceCommit"]) is None:
        raise MutationPolicyError("mutation baseline sourceCommit is invalid")
    for name in ("predecessorBaselineHash", "predecessorVerificationReceiptHash"):
        value = baseline.get(name)
        if value is not None and (not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{64}", value) is None):
            raise MutationPolicyError(f"mutation baseline {name} is invalid")
    if (baseline["predecessorBaselineHash"] is None) != (baseline["predecessorVerificationReceiptHash"] is None):
        raise MutationPolicyError("mutation baseline predecessor pair differs")
    def exact(value: object, keys: set[str], label: str) -> dict[str, Any]:
        if not isinstance(value, dict) or set(value) != keys:
            raise MutationPolicyError(f"mutation baseline {label} schema differs")
        return value

    def digest(value: object, label: str) -> str:
        if not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{64}", value) is None:
            raise MutationPolicyError(f"mutation baseline {label} is invalid")
        return value

    def validate_git_inventory(value: object, label: str) -> None:
        inventory = exact(value, {"count", "records", "sha256"}, label)
        records = inventory["records"]
        if type(inventory["count"]) is not int or not isinstance(records, list) or inventory["count"] != len(records):
            raise MutationPolicyError(f"mutation baseline {label} count differs")
        previous = ""
        for record in records:
            item = exact(record, {"path", "mode", "blob"}, f"{label} record")
            if (
                not isinstance(item["path"], str) or not item["path"] or item["path"] <= previous
                or re.fullmatch(r"[0-7]{6}", item["mode"]) is None
                or re.fullmatch(r"[0-9a-f]{40,64}", item["blob"]) is None
            ):
                raise MutationPolicyError(f"mutation baseline {label} record differs")
            previous = item["path"]
        if digest(inventory["sha256"], f"{label} digest") != sha256(canonical_json_bytes(records)):
            raise MutationPolicyError(f"mutation baseline {label} digest differs")

    def validate_compiled_inventory(value: object, label: str) -> None:
        inventory = exact(value, {"count", "records", "sha256"}, label)
        records = inventory["records"]
        if type(inventory["count"]) is not int or not isinstance(records, list) or inventory["count"] != len(records):
            raise MutationPolicyError(f"mutation baseline {label} count differs")
        previous = ""
        for record in records:
            item = exact(record, {"path", "sha256"}, f"{label} record")
            if not isinstance(item["path"], str) or not item["path"] or item["path"] <= previous:
                raise MutationPolicyError(f"mutation baseline {label} paths differ")
            previous = item["path"]
            digest(item["sha256"], f"{label} class digest")
        if digest(inventory["sha256"], f"{label} digest") != sha256(canonical_json_bytes(records)):
            raise MutationPolicyError(f"mutation baseline {label} digest differs")

    observation = exact(baseline["observationGitConfig"], {"records", "sha256"}, "observation Git config")
    observation_records = observation["records"]
    if not isinstance(observation_records, list):
        raise MutationPolicyError("mutation baseline observation Git records differ")
    previous = ""
    for record in observation_records:
        item = exact(record, {"path", "mode", "blob"}, "observation Git record")
        if (
            not isinstance(item["path"], str) or not item["path"] or item["path"] <= previous
            or re.fullmatch(r"[0-7]{6}", item["mode"]) is None
            or re.fullmatch(r"[0-9a-f]{40,64}", item["blob"]) is None
        ):
            raise MutationPolicyError("mutation baseline observation Git record differs")
        previous = item["path"]
    if digest(observation["sha256"], "observation Git digest") != sha256(canonical_json_bytes(observation_records)):
        raise MutationPolicyError("mutation baseline observation Git digest differs")

    neutral = baseline["hostNeutralMutationIdentity"]
    if not isinstance(neutral, dict) or neutral.get("schema") != "host-neutral-mutation-identity-v1":
        raise MutationPolicyError("mutation baseline host-neutral identity differs")
    if digest(baseline["hostNeutralMutationIdentitySha256"], "host-neutral digest") != sha256(canonical_json_bytes(neutral)):
        raise MutationPolicyError("mutation baseline host-neutral identity digest differs")

    toolchain = exact(
        baseline["toolchainIdentity"],
        {"pitestPlugin", "pitestEngine", "mutationEngine", "kotlin", "kotlinTest", "productionJvmTarget", "mutationRuntimeJava"},
        "toolchain identity",
    )
    if type(toolchain["productionJvmTarget"]) is not int or toolchain["mutationRuntimeJava"] != neutral.get("java"):
        raise MutationPolicyError("mutation baseline toolchain identity values differ")
    command = exact(
        baseline["effectiveCommandPlan"],
        {"canonicalGradleFlags", "defaultCharacterEncoding", "managedEncodingArguments", "moduleSurfaceSha256"},
        "effective command plan",
    )
    if (
        command["defaultCharacterEncoding"] != "UTF-8"
        or command["managedEncodingArguments"] != ["-Dfile.encoding=UTF-8"]
        or not isinstance(command["canonicalGradleFlags"], list)
        or not isinstance(command["moduleSurfaceSha256"], dict)
        or sorted(command["moduleSurfaceSha256"]) != ["location", "settings", "station"]
    ):
        raise MutationPolicyError("mutation baseline effective command plan values differ")
    for value in command["moduleSurfaceSha256"].values():
        digest(value, "module surface digest")
    environment = exact(
        baseline["executionEnvironmentIdentity"],
        {"policyVersion", "policySha256", "forbiddenNamesSha256", "forbiddenPrefixesSha256"},
        "execution environment identity",
    )
    for name in ("policySha256", "forbiddenNamesSha256", "forbiddenPrefixesSha256"):
        digest(environment[name], f"execution environment {name}")
    git_view = exact(baseline["gitObjectViewIdentity"], {"policyVersion", "policySha256", "captureInventory"}, "Git object view identity")
    digest(git_view["policySha256"], "Git object view policy digest")
    exact(git_view["captureInventory"], {"inventorySha256", "policy", "prefixSha256"}, "Git object view capture inventory")
    for name in ("inventorySha256", "prefixSha256"):
        digest(git_view["captureInventory"][name], f"Git object view {name}")
    wrapper = exact(baseline["wrapperIdentity"], {"gitInventory", "distributionUrl", "distributionType", "gradleVersion"}, "wrapper identity")
    validate_git_inventory(wrapper["gitInventory"], "wrapper Git inventory")
    if wrapper["distributionType"] != "bin" or wrapper["gradleVersion"] != "9.6.1" or not isinstance(wrapper["distributionUrl"], str):
        raise MutationPolicyError("mutation baseline wrapper identity values differ")

    inventories = baseline.get("moduleInventories")
    if not isinstance(inventories, dict) or sorted(inventories) != ["location", "settings", "station"]:
        raise MutationPolicyError("mutation baseline module inventory set differs")
    for name, raw_inventory in sorted(inventories.items()):
        inventory = exact(
            raw_inventory,
            {"authoredMain", "authoredTest", "compiledMain", "compiledTest", "effectiveSurface", "sourceDirs", "mutableCodePaths", "additionalClasspath", "launchClasspath"},
            f"{name} module inventory",
        )
        validate_git_inventory(inventory["authoredMain"], f"{name} authored main")
        validate_git_inventory(inventory["authoredTest"], f"{name} authored test")
        validate_compiled_inventory(inventory["compiledMain"], f"{name} compiled main")
        validate_compiled_inventory(inventory["compiledTest"], f"{name} compiled test")
        surface = exact(inventory["effectiveSurface"], {"fields", "sha256"}, f"{name} effective surface")
        if not isinstance(surface["fields"], dict) or any(not isinstance(key, str) or not isinstance(value, str) for key, value in surface["fields"].items()):
            raise MutationPolicyError(f"mutation baseline {name} effective surface fields differ")
        if digest(surface["sha256"], f"{name} effective surface digest") != sha256(canonical_json_bytes(surface["fields"])):
            raise MutationPolicyError(f"mutation baseline {name} effective surface digest differs")
        if command["moduleSurfaceSha256"].get(name) != surface["sha256"]:
            raise MutationPolicyError(f"mutation baseline {name} command-plan surface digest differs")
        for field in ("sourceDirs", "mutableCodePaths", "additionalClasspath", "launchClasspath"):
            if not isinstance(inventory[field], str):
                raise MutationPolicyError(f"mutation baseline {name} {field} identity differs")

    reports = baseline.get("reports")
    if not isinstance(reports, list) or [item.get("module") for item in reports if isinstance(item, dict)] != ["location", "settings", "station"]:
        raise MutationPolicyError("mutation baseline report module set differs")
    _validate_baseline_reports(reports)

    history = exact(baseline["profileHistory"], {"linux-x86_64"}, "profile history")
    linux = history["linux-x86_64"]
    if not isinstance(linux, dict) or set(linux) not in ({"state"}, {"state", "observedBundleSha256"}):
        raise MutationPolicyError("mutation baseline Linux history schema differs")
    if linux.get("state") == "NOT_ESTABLISHED":
        if set(linux) != {"state"}:
            raise MutationPolicyError("mutation baseline NOT_ESTABLISHED history differs")
    elif linux.get("state") == "ESTABLISHED":
        digest(linux.get("observedBundleSha256"), "Linux historical observed bundle")
    else:
        raise MutationPolicyError("mutation baseline Linux history state differs")
    if baseline["captureProfile"] not in {"darwin-arm64", "linux-x86_64"}:
        raise MutationPolicyError("mutation baseline capture profile differs")

    input_value = {
        "observationGitConfig": observation,
        "toolchainIdentity": toolchain,
        "effectiveCommandPlan": command,
        "executionEnvironmentIdentity": environment,
        "gitObjectViewIdentity": git_view,
        "wrapperIdentity": wrapper,
        "moduleInventories": inventories,
    }
    if baseline["mutationInputIdentitySha256"] != sha256(canonical_json_bytes(input_value)):
        raise MutationPolicyError("mutation baseline input identity digest differs")


def validate_legacy_predecessor(baseline_raw: bytes, baseline: dict[str, Any]) -> dict[str, Any]:
    expected_keys = {
        "schemaVersion", "sourceCommit", "policySha256", "observationGitConfig",
        "pluginVersion", "engineVersion", "hostNeutralMutationIdentity",
        "hostNeutralMutationIdentitySha256", "captureProfile", "profileHistory", "reports",
        "predecessorBaselineHash", "captureEvidenceDigest",
    }
    if set(baseline) != expected_keys or baseline.get("schemaVersion") != 1:
        raise MutationPolicyError("legacy predecessor baseline schema differs")
    if baseline.get("predecessorBaselineHash") is not None:
        raise MutationPolicyError("legacy predecessor must be the reviewed initial baseline")
    candidate_hash = sha256(baseline_raw)
    receipt_path = CAPTURE_RECEIPT_ROOT / f"{candidate_hash}.json"
    value = read_strict_json(read_bytes(receipt_path))
    expected_receipt_keys = {
        "schema", "candidateBaselineSha256", "predecessorBaselineHash",
        "captureEvidenceDigest", "components",
    }
    expected_components = {
        "policy", "route", "tasks", "routeReceipt", "attempt", "completion", "measurement",
        "configuration:location", "configuration:settings", "configuration:station",
        "xml:location", "xml:settings", "xml:station",
        "semantic:location", "semantic:settings", "semantic:station", "verificationSummary",
    }
    if not isinstance(value, dict) or set(value) != expected_receipt_keys:
        raise MutationPolicyError("legacy predecessor capture receipt schema differs")
    if (
        value.get("schema") != "pitest-capture-receipt-v1"
        or value.get("candidateBaselineSha256") != candidate_hash
        or value.get("predecessorBaselineHash") is not None
        or value.get("captureEvidenceDigest") != baseline.get("captureEvidenceDigest")
        or not isinstance(value.get("components"), dict)
        or set(value["components"]) != expected_components
    ):
        raise MutationPolicyError("legacy predecessor capture receipt binding differs")
    for digest in value["components"].values():
        if not isinstance(digest, str) or re.fullmatch(r"[0-9a-f]{64}", digest) is None:
            raise MutationPolicyError("legacy predecessor component digest is invalid")
    return value


def verify(
    observation: bool,
    java_home: str,
    baseline_snapshot: BaselineSnapshot | None = None,
) -> dict[str, Any]:
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
    completion, completion_raw = validate_completion_value(policy, route_value, policy_raw)
    snapshot = baseline_snapshot if baseline_snapshot is not None else read_baseline_snapshot()
    baseline_raw = snapshot.raw if snapshot is not None else None
    baseline = snapshot.value if snapshot is not None else None
    if not isinstance(completion, dict):
        raise MutationPolicyError("completion is malformed")
    violations: list[str] = []
    inventory_deltas: dict[str, Any] = {}
    if baseline is not None:
        if not isinstance(baseline, dict):
            raise MutationPolicyError("mutation baseline is malformed")
        _, git = validate_bootstrap(policy, java_home)
        validate_baseline_capture_receipt(
            baseline_raw,
            baseline,
            git=git,
            capture_receipt_raw=snapshot.capture_receipt_raw,
        )
        if git.text(GitCommand.MERGE_BASE, "--is-ancestor", baseline["sourceCommit"], route_value["sourceCommit"]) != "":
            raise MutationPolicyError("mutation baseline source is not an ancestor")
        if not baseline_policy_identity_matches(baseline.get("policySha256"), policy_raw, policy["enforcementPhase"]):
            raise MutationPolicyError("mutation baseline policy identity differs")
        if baseline.get("hostNeutralMutationIdentitySha256") != completion.get("hostNeutralMutationIdentitySha256"):
            raise MutationPolicyError("host-neutral mutation identity differs; reviewed recapture required")
        if baseline.get("hostNeutralMutationIdentity") != completion.get("hostNeutralMutationIdentity"):
            raise MutationPolicyError("host-neutral mutation identity payload differs")
        if observation and baseline.get("observationGitConfig") != git_configuration_identity(git):
            raise MutationPolicyError("observe requires exact Commit-A mutation-producing Git configuration")
        current_measurement = {
            "observationGitConfig": git_configuration_identity(git),
            "gitObjectViewIdentity": route_value["bootstrap"]["gitObjectView"],
            "hostNeutralMutationIdentity": completion["hostNeutralMutationIdentity"],
        }
        current_input = mutation_input_identity(policy, git, current_measurement)
        for name in sorted(policy["modules"]):
            old_inventory = baseline["moduleInventories"][name]
            new_inventory = current_input["moduleInventories"][name]
            old_report = next(item for item in baseline["reports"] if item["module"] == name)
            new_report = next(item for item in completion["reports"] if item["module"] == name)
            inventory_deltas[name] = build_inventory_delta(old_inventory, new_inventory, old_report, new_report)
            global_inputs_unchanged = all(
                baseline[field] == current_input[field]
                for field in (
                    "toolchainIdentity", "effectiveCommandPlan", "executionEnvironmentIdentity",
                    "gitObjectViewIdentity", "wrapperIdentity",
                )
            )
            semantic_inputs_unchanged = validate_module_inventory_evolution(
                name,
                old_inventory,
                new_inventory,
                global_inputs_unchanged=global_inputs_unchanged,
                toolchain_unchanged=baseline["toolchainIdentity"] == current_input["toolchainIdentity"],
            )
            if semantic_inputs_unchanged:
                if (
                    old_report["semanticSha256"] != new_report["semanticSha256"]
                    or old_report["records"] != new_report["records"]
                    or old_report["classes"] != new_report["classes"]
                ):
                    raise MutationPolicyError(
                        f"unchanged mutation inputs changed mutant/class/status identity for {name}"
                    )
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
                compared_packages = changed_packages_for_module(
                    route_value,
                    name,
                    set(baseline_packages) | set(current_packages),
                    lambda commit, path: git.bytes(GitCommand.SHOW, f"{commit}:{path}"),
                )
                violations.extend(compare_no_coverage(baseline_packages, current_packages, compared_packages))
    elif not observation:
        raise MutationPolicyError("blocking verification requires mutation baseline")
    value = {
        "schemaVersion": 2,
        "status": "pass" if not violations else "fail",
        "sourceCommit": route_value["sourceCommit"],
        "policySha256": policy_hash,
        "routeSha256": sha256(read_bytes(ROUTE_PATH)),
        "routeReceiptSha256": sha256(read_bytes(ROUTE_RECEIPT_PATH)),
        "attemptSha256": sha256(read_bytes(ATTEMPT_PATH)),
        "completionSha256": sha256(completion_raw),
        "predecessorBaselineSha256": snapshot.sha256 if snapshot is not None else None,
        "predecessorCaptureReceiptSha256": (
            snapshot.capture_receipt_sha256 if snapshot is not None else None
        ),
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
        "inventoryDelta": inventory_deltas,
        "violations": sorted(violations),
        "finalReceiptPath": relative(FINAL_RECEIPT_PATH),
    }
    write_atomic(SUMMARY_PATH, value)
    if violations:
        raise MutationPolicyError("; ".join(sorted(violations)))
    return value


def baseline_policy_identity_matches(baseline_hash: object, current_raw: bytes, phase: str) -> bool:
    if not isinstance(baseline_hash, str):
        return False
    if sha256(current_raw) == baseline_hash:
        return True
    marker = b'"enforcementPhase": "blocking"'
    if phase != "blocking" or current_raw.count(marker) != 1:
        marker = b'"enforcementPhase":"blocking"'
        if phase != "blocking" or current_raw.count(marker) != 1:
            return False
        observation_raw = current_raw.replace(marker, b'"enforcementPhase":"observe"', 1)
    else:
        observation_raw = current_raw.replace(marker, b'"enforcementPhase": "observe"', 1)
    return sha256(observation_raw) == baseline_hash


def changed_packages_for_module(
    route_value: dict[str, Any],
    module: str,
    known_packages: set[str],
    blob_loader: Any,
) -> set[str]:
    """Return the exact old/new package set whose NO_COVERAGE may change."""
    if route_value.get("event") != "pull-request":
        return set(known_packages)
    old_commit = route_value.get("mergeBase")
    new_commit = route_value.get("sourceCommit")
    if not isinstance(old_commit, str) or not isinstance(new_commit, str):
        raise MutationPolicyError("pull-request changed-package comparison requires exact commits")
    own_main = f"domain/{module}/src/main/"
    own_test = f"domain/{module}/src/test/"
    own_build = f"domain/{module}/build.gradle.kts"
    changed: set[str] = set()
    fallback_all = False
    changes = route_value.get("changes")
    if not isinstance(changes, list):
        raise MutationPolicyError("route changes must be a list")
    for raw_change in changes:
        if not isinstance(raw_change, dict) or set(raw_change) != {"status", "oldPath", "newPath"}:
            raise MutationPolicyError("route change schema differs")
        paths = [raw_change["oldPath"], raw_change["newPath"]]
        for path in paths:
            if path is None:
                continue
            if not isinstance(path, str):
                raise MutationPolicyError("route changed path must be a string or null")
            if path.startswith(own_test) or path == own_build:
                fallback_all = True
            elif (
                path in {
                    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
                    "gradle/libs.versions.toml", "gradlew", "gradlew.bat",
                    "gradle/verification-metadata.xml", ".github/workflows/android.yml",
                    ".github/workflows/mutation-schedule.yml", "config/quality/mutation-policy.json",
                    "config/quality/mutation-baseline.json",
                }
                or path.startswith(("core/model/", "build-logic/", "gradle/wrapper/", "scripts/quality/", "config/quality/mutation-transitions/"))
            ):
                fallback_all = True
        for side, commit in (("oldPath", old_commit), ("newPath", new_commit)):
            path = raw_change[side]
            if path is None or not path.startswith(own_main):
                continue
            suffix = Path(path).suffix
            if suffix not in {".kt", ".java"}:
                fallback_all = True
                continue
            source = blob_loader(commit, path)
            changed.add(parse_package_declaration(source, suffix))
    return set(known_packages) if fallback_all else changed


def build_inventory_delta(
    old_inventory: dict[str, Any],
    new_inventory: dict[str, Any],
    old_report: dict[str, Any],
    new_report: dict[str, Any],
) -> dict[str, Any]:
    def record_delta(old_records: list[dict[str, Any]], new_records: list[dict[str, Any]]) -> dict[str, Any]:
        old_by_path = {item["path"]: item for item in old_records}
        new_by_path = {item["path"]: item for item in new_records}
        common = sorted(set(old_by_path) & set(new_by_path))
        return {
            "added": [new_by_path[path] for path in sorted(set(new_by_path) - set(old_by_path))],
            "removed": [old_by_path[path] for path in sorted(set(old_by_path) - set(new_by_path))],
            "changed": [
                {"path": path, "before": old_by_path[path], "after": new_by_path[path]}
                for path in common if old_by_path[path] != new_by_path[path]
            ],
        }

    def mutant_key(item: dict[str, Any]) -> tuple[object, ...]:
        return (
            item["mutatedClass"], item["mutatedMethod"], item["methodDescription"],
            item["mutator"], tuple(item["indexes"]),
        )

    def mutant_identity(item: dict[str, Any]) -> dict[str, Any]:
        return {
            "mutatedClass": item["mutatedClass"],
            "mutatedMethod": item["mutatedMethod"],
            "methodDescription": item["methodDescription"],
            "mutator": item["mutator"],
            "indexes": item["indexes"],
        }

    old_mutants = {mutant_key(item): item for item in old_report["records"]}
    new_mutants = {mutant_key(item): item for item in new_report["records"]}
    common_mutants = sorted(set(old_mutants) & set(new_mutants), key=lambda item: tuple(str(part) for part in item))
    old_classes = {item["path"]: item["sha256"] for item in old_inventory["compiledMain"]["records"]}
    new_classes = {item["path"]: item["sha256"] for item in new_inventory["compiledMain"]["records"]}
    class_common = sorted(set(old_classes) & set(new_classes))
    old_fields = old_inventory["effectiveSurface"]["fields"]
    new_fields = new_inventory["effectiveSurface"]["fields"]
    field_names = sorted(set(old_fields) | set(new_fields))
    classpath_fields = ("sourceDirs", "mutableCodePaths", "additionalClasspath", "launchClasspath")
    return {
        "authoredMain": record_delta(old_inventory["authoredMain"]["records"], new_inventory["authoredMain"]["records"]),
        "authoredTest": record_delta(old_inventory["authoredTest"]["records"], new_inventory["authoredTest"]["records"]),
        "compiledMain": record_delta(old_inventory["compiledMain"]["records"], new_inventory["compiledMain"]["records"]),
        "compiledTest": record_delta(old_inventory["compiledTest"]["records"], new_inventory["compiledTest"]["records"]),
        "sourceFiles": {
            "added": sorted({item["sourcePath"] for item in new_report["records"]} - {item["sourcePath"] for item in old_report["records"]}),
            "removed": sorted({item["sourcePath"] for item in old_report["records"]} - {item["sourcePath"] for item in new_report["records"]}),
        },
        "mutants": {
            "added": [mutant_identity(new_mutants[key]) for key in sorted(set(new_mutants) - set(old_mutants), key=lambda item: tuple(str(part) for part in item))],
            "removed": [mutant_identity(old_mutants[key]) for key in sorted(set(old_mutants) - set(new_mutants), key=lambda item: tuple(str(part) for part in item))],
            "changedStatuses": [
                {**mutant_identity(old_mutants[key]), "before": old_mutants[key]["status"], "after": new_mutants[key]["status"]}
                for key in common_mutants if old_mutants[key]["status"] != new_mutants[key]["status"]
            ],
        },
        "classes": {
            "added": sorted(set(new_classes) - set(old_classes)),
            "removed": sorted(set(old_classes) - set(new_classes)),
            "changedContent": [
                {"path": path, "beforeSha256": old_classes[path], "afterSha256": new_classes[path]}
                for path in class_common if old_classes[path] != new_classes[path]
            ],
        },
        "effectiveSurface": {
            "changedFields": [
                {"field": field, "before": old_fields.get(field), "after": new_fields.get(field)}
                for field in field_names if old_fields.get(field) != new_fields.get(field)
            ],
        },
        "classpaths": {
            field: {"before": old_inventory[field], "after": new_inventory[field]}
            for field in classpath_fields if old_inventory[field] != new_inventory[field]
        },
    }


def build_legacy_transition_delta(
    old_report: dict[str, Any],
    new_report: dict[str, Any],
    new_inventory: dict[str, Any],
    git: GitExecutor,
    old_commit: str,
    module_path: str,
) -> dict[str, Any]:
    old_main = git_path_inventory(git, f"{module_path}/src/main", commit=old_commit)
    old_test = git_path_inventory(git, f"{module_path}/src/test", commit=old_commit)
    old_class_records = sorted(
        {
            (record["classPath"], record["classSha256"])
            for record in old_report["records"]
        },
    )
    old_compiled = {
        "count": len(old_class_records),
        "records": [{"path": path, "sha256": digest} for path, digest in old_class_records],
    }
    old_compiled["sha256"] = sha256(canonical_json_bytes(old_compiled["records"]))
    synthetic_old = {
        "authoredMain": old_main,
        "authoredTest": old_test,
        "compiledMain": old_compiled,
        # Schema-v1 did not capture test-class or effective-surface inventories.
        # Keep that absence explicit instead of manufacturing predecessor bytes.
        "compiledTest": {"count": 0, "records": [], "sha256": sha256(canonical_json_bytes([]))},
        "effectiveSurface": {"fields": {}, "sha256": sha256(canonical_json_bytes({}))},
        "sourceDirs": "<not-captured-v1>",
        "mutableCodePaths": "<not-captured-v1>",
        "additionalClasspath": "<not-captured-v1>",
        "launchClasspath": "<not-captured-v1>",
    }
    delta = build_inventory_delta(synthetic_old, new_inventory, old_report, new_report)
    delta["legacyEvidenceAvailability"] = {
        "schemaVersion": 1,
        "compiledMain": "reconstructed-from-record-class-path-and-sha256",
        "compiledTest": "not-captured-v1",
        "effectiveSurface": "configuration-sha256-only",
        "classpaths": "not-captured-v1",
        "predecessorConfigurationSha256": old_report["configurationSha256"],
        "candidateConfigurationSha256": new_report["configurationSha256"],
    }
    delta["candidateInventory"] = new_inventory
    return delta


def validate_module_inventory_evolution(
    name: str,
    old_inventory: dict[str, Any],
    new_inventory: dict[str, Any],
    *,
    global_inputs_unchanged: bool,
    toolchain_unchanged: bool,
) -> bool:
    old_classes = {item["path"] for item in old_inventory["compiledMain"]["records"]}
    new_classes = {item["path"] for item in new_inventory["compiledMain"]["records"]}
    lost_classes = sorted(old_classes - new_classes)
    authored_main_unchanged = old_inventory["authoredMain"] == new_inventory["authoredMain"]
    if authored_main_unchanged and lost_classes:
        raise MutationPolicyError(
            f"reviewed-recapture-required: unchanged authored source lost compiled classes in {name}: {lost_classes}"
        )
    semantic_inputs_unchanged = global_inputs_unchanged and all(
        old_inventory[field] == new_inventory[field]
        for field in (
            "authoredMain", "authoredTest", "compiledMain", "compiledTest",
            "effectiveSurface", "sourceDirs", "mutableCodePaths",
            "additionalClasspath", "launchClasspath",
        )
    )
    if not semantic_inputs_unchanged and authored_main_unchanged and (
        old_inventory["effectiveSurface"] != new_inventory["effectiveSurface"]
        or not toolchain_unchanged
    ):
        raise MutationPolicyError(
            f"reviewed-recapture-required: mutation-producing tool/config surface changed for {name}"
        )
    return semantic_inputs_unchanged


def seal_verification(java_home: str) -> dict[str, Any]:
    policy, route_value, policy_raw, policy_hash = validate_route(java_home)
    summary_raw = read_bytes(SUMMARY_PATH)
    summary = read_strict_json(summary_raw)
    if not isinstance(summary, dict) or summary.get("status") not in {"pass", "not-applicable"}:
        raise MutationPolicyError("only a successful summary can be sealed")
    if summary.get("verificationMode") == "initial-capture":
        raise MutationPolicyError("initial-capture summary cannot be sealed as an ordinary receipt")
    selected = route_value["status"] == "selected"
    baseline_snapshot = read_baseline_snapshot() if selected else None
    if selected and baseline_snapshot is None:
        raise MutationPolicyError("blocking verification requires mutation baseline")
    if selected and (
        summary.get("predecessorBaselineSha256") != baseline_snapshot.sha256
        or baseline_snapshot.capture_receipt_raw is None
        or summary.get("predecessorCaptureReceiptSha256")
        != baseline_snapshot.capture_receipt_sha256
    ):
        raise MutationPolicyError("verification summary baseline identity differs")
    revalidated = verify(
        observation=False,
        java_home=java_home,
        baseline_snapshot=baseline_snapshot,
    )
    current_summary_raw = read_bytes(SUMMARY_PATH)
    if summary != revalidated or summary_raw != current_summary_raw:
        raise MutationPolicyError("verification summary differs after typed predecessor revalidation")
    predecessors = {
        "policy": policy_raw,
        "route": read_bytes(ROUTE_PATH),
        "routeReceipt": read_bytes(ROUTE_RECEIPT_PATH),
        "summary": summary_raw,
        "tasks": read_bytes(TASKS_PATH),
    }
    if selected:
        completion, completion_raw = validate_completion_value(policy, route_value, policy_raw)
        predecessors["attempt"] = read_bytes(ATTEMPT_PATH)
        predecessors["completion"] = completion_raw
        if baseline_snapshot is None:
            raise MutationPolicyError("selected verification lost its baseline snapshot")
        predecessors["baseline"] = baseline_snapshot.raw
        if baseline_snapshot.capture_receipt_raw is None:
            raise MutationPolicyError("selected verification lost its capture receipt snapshot")
        predecessors["baselineCaptureReceipt"] = baseline_snapshot.capture_receipt_raw
        for report in completion["reports"]:
            name = report["module"]
            module = policy["modules"][name]
            predecessors[f"configuration:{name}"] = read_bytes(REPOSITORY_ROOT / module["configurationPath"])
            predecessors[f"xml:{name}"] = read_bytes(REPOSITORY_ROOT / module["reportPath"])
            predecessors[f"semantic:{name}"] = canonical_json_bytes({
                "semanticSha256": report["semanticSha256"],
                "records": report["records"],
            })
            predecessors[f"html:{name}"] = canonical_json_bytes(report["html"])
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


def install_successor_atomically(
    *,
    baseline_path: Path,
    predecessor_raw: bytes,
    candidate_raw: bytes,
    receipt_path: Path,
    receipt_raw: bytes,
    transition_path: Path,
    transition_raw: bytes,
    archive_files: dict[Path, bytes] | None = None,
) -> None:
    if receipt_path.exists() or transition_path.exists():
        raise MutationPolicyError("recapture-transition append-only output already exists")
    archive_files = archive_files or {}
    if any(path.exists() for path in archive_files):
        raise MutationPolicyError("recapture-transition append-only archive output already exists")
    receipt_path.parent.mkdir(parents=True, exist_ok=True)
    transition_path.parent.mkdir(parents=True, exist_ok=True)
    receipt_tmp = receipt_path.with_suffix(receipt_path.suffix + ".tmp")
    transition_tmp = transition_path.with_suffix(transition_path.suffix + ".tmp")
    baseline_tmp = baseline_path.with_suffix(baseline_path.suffix + ".tmp")
    restore_tmp = baseline_path.with_suffix(baseline_path.suffix + ".restore.tmp")
    installed: list[Path] = []
    archive_temporaries: list[Path] = []
    try:
        for path, raw in sorted(archive_files.items(), key=lambda item: str(item[0])):
            if not path.resolve(strict=False).is_relative_to(EVIDENCE_ARCHIVE_ROOT.resolve(strict=False)):
                raise MutationPolicyError("recapture-transition archive path escapes the checked root")
            path.parent.mkdir(parents=True, exist_ok=True)
            temporary = path.with_suffix(path.suffix + ".tmp")
            archive_temporaries.append(temporary)
            temporary.write_bytes(raw)
            if temporary.read_bytes() != raw:
                raise MutationPolicyError("staged capture archive differs")
            os.replace(temporary, path)
            installed.append(path)
        receipt_tmp.write_bytes(receipt_raw)
        transition_tmp.write_bytes(transition_raw)
        baseline_tmp.write_bytes(candidate_raw)
        if receipt_tmp.read_bytes() != receipt_raw or transition_tmp.read_bytes() != transition_raw:
            raise MutationPolicyError("staged successor evidence differs")
        if baseline_tmp.read_bytes() != candidate_raw:
            raise MutationPolicyError("staged candidate baseline differs")
        os.replace(receipt_tmp, receipt_path)
        installed.append(receipt_path)
        os.replace(transition_tmp, transition_path)
        installed.append(transition_path)
        os.replace(baseline_tmp, baseline_path)
    except BaseException:
        if baseline_path.exists() and baseline_path.read_bytes() != predecessor_raw:
            restore_tmp.write_bytes(predecessor_raw)
            os.replace(restore_tmp, baseline_path)
        for created in reversed(installed):
            if created.exists():
                created.unlink()
        raise
    finally:
        for temporary in (receipt_tmp, transition_tmp, baseline_tmp, restore_tmp):
            if temporary.exists():
                temporary.unlink()
        for temporary in archive_temporaries:
            if temporary.exists():
                temporary.unlink()
        for directory in sorted(
            {path.parent for path in archive_files} | {path.parent.parent for path in archive_files},
            key=lambda path: len(path.parts),
            reverse=True,
        ):
            if directory != EVIDENCE_ARCHIVE_ROOT and directory.exists() and not any(directory.iterdir()):
                directory.rmdir()


def build_capture_archive_files(
    candidate_sha256: str,
    components: dict[str, bytes],
) -> tuple[dict[str, str], dict[Path, bytes]]:
    if re.fullmatch(r"[0-9a-f]{64}", candidate_sha256) is None:
        raise MutationPolicyError("capture archive candidate hash differs")
    candidate_root = EVIDENCE_ARCHIVE_ROOT / candidate_sha256
    records: dict[str, dict[str, str]] = {}
    files: dict[Path, bytes] = {}
    private_root = str(REPOSITORY_ROOT).encode("utf-8")
    for index, (name, raw) in enumerate(sorted(components.items())):
        if private_root in raw or b"SECRET_TOKEN" in raw:
            raise MutationPolicyError(f"capture archive contains a host-private path or secret: {name}")
        path = candidate_root / "components" / f"{index:02d}.bin"
        files[path] = raw
        records[name] = {"path": relative(path), "sha256": sha256(raw)}
    archive = {
        "schema": "pitest-capture-evidence-archive-v1",
        "candidateBaselineSha256": candidate_sha256,
        "components": records,
    }
    archive_path = candidate_root / "archive.json"
    archive_raw = canonical_json_bytes(archive)
    files[archive_path] = archive_raw
    return {"path": relative(archive_path), "sha256": sha256(archive_raw)}, files


def recapture_transition(java_home: str) -> dict[str, Any]:
    policy, route_value, policy_raw, policy_hash = validate_route(java_home)
    transition_axes = policy.get("capturePolicy", {}).get("reviewedTransitionAxes")
    if transition_axes != [REVIEWED_TRANSITION_AXIS]:
        raise MutationPolicyError("recapture-transition reviewed axis differs")
    if route_value.get("event") != "local-all" or route_value.get("selectedModules") != ["location", "settings", "station"]:
        raise MutationPolicyError("recapture-transition requires a local-all three-module route")
    completion, completion_raw = validate_completion_value(policy, route_value, policy_raw)
    predecessor_raw = read_bytes(BASELINE_PATH)
    predecessor = read_strict_json(predecessor_raw)
    if not isinstance(predecessor, dict):
        raise MutationPolicyError("recapture-transition predecessor must be an object")
    predecessor_hash = sha256(predecessor_raw)
    predecessor_capture_receipt_raw = read_bytes(
        CAPTURE_RECEIPT_ROOT / f"{predecessor_hash}.json",
    )
    predecessor_capture_receipt_sha256 = sha256(predecessor_capture_receipt_raw)
    predecessor_schema = predecessor.get("schemaVersion")
    if predecessor_schema == 1:
        validate_legacy_predecessor(predecessor_raw, predecessor)
    elif predecessor_schema == 2:
        validate_baseline_capture_receipt(
            predecessor_raw,
            predecessor,
            capture_receipt_raw=predecessor_capture_receipt_raw,
        )
    else:
        raise MutationPolicyError("recapture-transition predecessor schema differs")
    measurement = measure(java_home)
    _, git = validate_bootstrap(policy, java_home)
    input_identity = mutation_input_identity(policy, git, measurement)
    violations: list[str] = []
    predecessor_reports = {item["module"]: item for item in predecessor["reports"]}
    inventory_delta: dict[str, Any] = {}
    for report in completion["reports"]:
        name = report["module"]
        floor = policy["modules"][name]["floorPercent"]
        if floor is not None and not compare_floor(report["counters"]["KILLED"], report["counters"]["total"], floor):
            violations.append(f"{name} mutation score is below exact floor {floor}")
        old = predecessor_reports[name]
        old_packages = {package: counters["NO_COVERAGE"] for package, counters in old["packages"].items()}
        new_packages = {package: counters["NO_COVERAGE"] for package, counters in report["packages"].items()}
        violations.extend(compare_no_coverage(old_packages, new_packages, set(old_packages) | set(new_packages)))
        if old["semanticSha256"] != report["semanticSha256"] or old["records"] != report["records"] or old["classes"] != report["classes"]:
            violations.append(f"{name} evidence-schema transition changed mutant/class/status identity")
        if predecessor_schema == 1:
            module_path = policy["modules"][name]["modulePath"].removeprefix(":").replace(":", "/")
            inventory_delta[name] = build_legacy_transition_delta(
                old,
                report,
                input_identity["moduleInventories"][name],
                git,
                predecessor["sourceCommit"],
                module_path,
            )
        else:
            inventory_delta[name] = build_inventory_delta(
                predecessor["moduleInventories"][name],
                input_identity["moduleInventories"][name],
                old,
                report,
            )
    summary = {
        "schemaVersion": 2,
        "verificationMode": "recapture-transition",
        "transitionAxis": REVIEWED_TRANSITION_AXIS,
        "status": "pass" if not violations else "fail",
        "sourceCommit": route_value["sourceCommit"],
        "policySha256": policy_hash,
        "predecessorBaselineHash": predecessor_hash,
        "predecessorCaptureReceiptSha256": predecessor_capture_receipt_sha256,
        "routeSha256": sha256(read_bytes(ROUTE_PATH)),
        "routeReceiptSha256": sha256(read_bytes(ROUTE_RECEIPT_PATH)),
        "attemptSha256": sha256(read_bytes(ATTEMPT_PATH)),
        "completionSha256": sha256(completion_raw),
        "hostNeutralMutationIdentity": completion["hostNeutralMutationIdentity"],
        "hostNeutralMutationIdentitySha256": completion["hostNeutralMutationIdentitySha256"],
        "perRunExecutionProvenance": completion["perRunExecutionProvenance"],
        "perRunExecutionProvenanceSha256": completion["perRunExecutionProvenanceSha256"],
        "historicalLinuxComparison": (
            predecessor.get("profileHistory", {}).get("linux-x86_64", {}).get("state")
            if completion["perRunExecutionProvenance"]["selectedProfile"] == "linux-x86_64"
            else None
        ),
        "reports": completion["reports"],
        "inventoryDelta": inventory_delta,
        "violations": sorted(violations),
    }
    write_atomic(SUMMARY_PATH, summary)
    if violations:
        raise MutationPolicyError("; ".join(sorted(violations)))
    final_receipt = _seal_typed_summary(
        policy,
        route_value,
        policy_raw,
        policy_hash,
        summary,
        predecessor_raw,
        predecessor_capture_receipt_raw,
    )
    final_receipt_raw = read_bytes(FINAL_RECEIPT_PATH)
    components = _capture_components(policy, route_value, measurement)
    components["predecessorVerificationReceipt"] = final_receipt_raw
    manifest = build_capture_evidence_manifest(
        components=components,
        policy_sha256=policy_hash,
        predecessor_baseline_sha256=predecessor_hash,
        predecessor_verification_receipt_sha256=sha256(final_receipt_raw),
        source_commit=route_value["sourceCommit"],
        host_neutral_identity_sha256=measurement["hostNeutralMutationIdentitySha256"],
        per_run_provenance_sha256=measurement["perRunExecutionProvenanceSha256"],
    )
    evidence_digest = sha256(canonical_json_bytes(manifest))
    payload = {
        "schemaVersion": 2,
        "sourceCommit": measurement["sourceCommit"],
        "policySha256": policy_hash,
        "observationGitConfig": measurement["observationGitConfig"],
        "toolchainIdentity": input_identity["toolchainIdentity"],
        "hostNeutralMutationIdentity": measurement["hostNeutralMutationIdentity"],
        "hostNeutralMutationIdentitySha256": measurement["hostNeutralMutationIdentitySha256"],
        "effectiveCommandPlan": input_identity["effectiveCommandPlan"],
        "executionEnvironmentIdentity": input_identity["executionEnvironmentIdentity"],
        "gitObjectViewIdentity": input_identity["gitObjectViewIdentity"],
        "wrapperIdentity": input_identity["wrapperIdentity"],
        "moduleInventories": input_identity["moduleInventories"],
        "mutationInputIdentitySha256": input_identity["sha256"],
        "captureProfile": measurement["perRunExecutionProvenance"]["selectedProfile"],
        "profileHistory": predecessor["profileHistory"],
        "reports": measurement["reports"],
        "predecessorVerificationReceiptHash": sha256(final_receipt_raw),
    }
    candidate = build_capture_candidate(
        payload=payload,
        predecessor_baseline_sha256=predecessor_hash,
        capture_evidence_digest=evidence_digest,
    )
    candidate_raw = canonical_json_bytes(candidate)
    candidate_hash = sha256(candidate_raw)
    archive_reference, archive_files = build_capture_archive_files(candidate_hash, components)
    capture_receipt = build_capture_receipt(
        candidate_baseline=candidate_raw,
        evidence_manifest=manifest,
        evidence_archive=archive_reference,
    )
    capture_receipt_raw = canonical_json_bytes(capture_receipt)
    receipt_path = CAPTURE_RECEIPT_ROOT / f"{candidate_hash}.json"
    transition_path = TRANSITION_ROOT / f"{predecessor_hash}-to-{input_identity['sha256']}.json"
    transition = {
        "schema": "pitest-recapture-transition-v2",
        "axis": REVIEWED_TRANSITION_AXIS,
        "sourceCommit": route_value["sourceCommit"],
        "policySha256": policy_hash,
        "predecessorBaselineSha256": predecessor_hash,
        "candidateBaselineSha256": candidate_hash,
        "captureReceiptSha256": sha256(capture_receipt_raw),
        "predecessorVerificationReceiptSha256": sha256(final_receipt_raw),
        "routeSha256": sha256(read_bytes(ROUTE_PATH)),
        "completionSha256": sha256(completion_raw),
        "oldInputIdentity": {
            "schemaVersion": predecessor["schemaVersion"],
            "hostNeutralMutationIdentitySha256": predecessor["hostNeutralMutationIdentitySha256"],
            **(
                {"mutationInputIdentitySha256": predecessor["mutationInputIdentitySha256"]}
                if predecessor_schema == 2 else {}
            ),
        },
        "newInputIdentity": {
            "schemaVersion": 2,
            "mutationInputIdentitySha256": input_identity["sha256"],
            "hostNeutralMutationIdentitySha256": measurement["hostNeutralMutationIdentitySha256"],
        },
        "inventoryDelta": inventory_delta,
        "evidenceArchive": archive_reference,
    }
    validate_baseline_schema(candidate)
    install_successor_atomically(
        baseline_path=BASELINE_PATH,
        predecessor_raw=predecessor_raw,
        candidate_raw=candidate_raw,
        receipt_path=receipt_path,
        receipt_raw=capture_receipt_raw,
        transition_path=transition_path,
        transition_raw=canonical_json_bytes(transition),
        archive_files=archive_files,
    )
    validate_baseline_capture_receipt(
        read_bytes(BASELINE_PATH),
        candidate,
        trusted_components=components,
        validate_transition_chain=False,
    )
    return candidate


def _capture_components(
    policy: dict[str, Any],
    route_value: dict[str, Any],
    measurement: dict[str, Any],
) -> dict[str, bytes]:
    components: dict[str, bytes] = {
        "policy": read_bytes(POLICY_PATH),
        "sourceCommit": (route_value["sourceCommit"] + "\n").encode("ascii"),
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
        components[f"semantic:{name}"] = canonical_json_bytes({"semanticSha256": report["semanticSha256"]})
        components[f"html:{name}"] = canonical_json_bytes(report["html"])
    return components


def _seal_typed_summary(
    policy: dict[str, Any],
    route_value: dict[str, Any],
    policy_raw: bytes,
    policy_hash: str,
    summary: dict[str, Any],
    baseline_raw: bytes,
    baseline_capture_receipt_raw: bytes,
) -> dict[str, Any]:
    completion, completion_raw = validate_completion_value(policy, route_value, policy_raw)
    summary_raw = read_bytes(SUMMARY_PATH)
    if read_strict_json(summary_raw) != summary:
        raise MutationPolicyError("transition summary re-read differs")
    if summary.get("predecessorCaptureReceiptSha256") != sha256(
        baseline_capture_receipt_raw,
    ):
        raise MutationPolicyError("transition summary baseline capture receipt differs")
    predecessors = {
        "policy": policy_raw, "route": read_bytes(ROUTE_PATH),
        "routeReceipt": read_bytes(ROUTE_RECEIPT_PATH), "tasks": read_bytes(TASKS_PATH),
        "attempt": read_bytes(ATTEMPT_PATH), "completion": completion_raw,
        "baseline": baseline_raw,
        "baselineCaptureReceipt": baseline_capture_receipt_raw,
        "summary": summary_raw,
    }
    for report in completion["reports"]:
        name = report["module"]
        module = policy["modules"][name]
        predecessors[f"configuration:{name}"] = read_bytes(REPOSITORY_ROOT / module["configurationPath"])
        predecessors[f"xml:{name}"] = read_bytes(REPOSITORY_ROOT / module["reportPath"])
        predecessors[f"semantic:{name}"] = canonical_json_bytes({"semanticSha256": report["semanticSha256"], "records": report["records"]})
        predecessors[f"html:{name}"] = canonical_json_bytes(report["html"])
    value = receipt(
        "pitest-verification-receipt-v1", predecessors,
        sourceCommit=route_value["sourceCommit"], status="selected-transition-verified",
        policySha256=policy_hash,
        hostNeutralMutationIdentitySha256=summary["hostNeutralMutationIdentitySha256"],
        perRunExecutionProvenanceSha256=summary["perRunExecutionProvenanceSha256"],
        historicalLinuxComparison=summary.get("historicalLinuxComparison"),
    )
    write_atomic(FINAL_RECEIPT_PATH, value)
    if read_strict_json(read_bytes(FINAL_RECEIPT_PATH)) != value:
        raise MutationPolicyError("transition final receipt re-read differs")
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
            recapture_transition(args.java_home)
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
