#!/usr/bin/env python3
"""Closed Task-9 local Linux/amd64 evidence orchestrator.

This module deliberately exposes no generic command, image, profile, context,
attempt, recovery, or path parameters.  Its public helpers are small so the
ownership and all-or-nothing rules can be mutation-tested without creating a
VM.  ``main`` is the only runtime entrypoint and is implemented as a bounded
state machine over the policy-owned constants below.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any, Mapping, Sequence

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

from scripts.quality.build_inputs.contracts import (  # noqa: E402
    BuildInputError,
    canonical_json_bytes,
    load_policy,
    sha256_file,
)


ROOT = Path(__file__).resolve().parents[3]
ENTRYPOINT_PATH = "scripts/quality/build_inputs/local_colima_evidence.py"
PROFILE = "gasstation-task9-linux-amd64"
CONTEXT = "colima-gasstation-task9-linux-amd64"
CONTAINER = "gasstation-task9-evidence"
VOLUMES = ("gasstation-task9-evidence-work", "gasstation-task9-linux-sdk")
COLIMA = "/opt/homebrew/bin/colima"
DOCKER = "/opt/homebrew/bin/docker"
IMAGE = "docker.io/library/ubuntu@sha256:33ceb71981b602c1a7443a53469e4dba065f7503eab3078a2d7a57a2ab987517"
IMAGE_CONFIG = "sha256:1e0a86e57d247923571b75e0aaf48a1449cf8c543d51fb3e07a4a7d7bfa79316"
MAIN_BASE_REF = "refs/heads/main"
MAIN_BASE_COMMIT = "7b8c149c9f792aaf43cc00a94ba671929008979e"
START_ARGV = (
    COLIMA,
    "start",
    PROFILE,
    "--arch",
    "aarch64",
    "--vm-type",
    "vz",
    "--vz-rosetta",
    "--binfmt=false",
    "--template=false",
    "--ssh-config=false",
    "--ssh-agent=false",
    "--cpus",
    "8",
    "--memory",
    "16",
    "--disk",
    "120",
    "--root-disk",
    "40",
    "--runtime",
    "docker",
    "--activate=false",
    "--mount",
    "none",
)
STOP_ARGV = (COLIMA, "stop", PROFILE)
DELETE_ARGV = (COLIMA, "delete", PROFILE, "--data", "--force")
CLEANUP_PHASES = (
    "staged-package-verified",
    "container-absent-live-daemon",
    "volumes-absent-live-daemon",
    "daemon-proof-exported",
    "profile-stopped-and-data-deleted",
    "profile-context-runtime-data-absent",
    "terminal-package-verified",
)
REQUIRED_EVIDENCE_ROWS = frozenset(
    {
        "configurationCache",
        "evidenceSessions",
        "metadataCapture",
        "mutations",
        "offlineStrict",
        "onlineColdStrict",
        "productStrict",
        "releaseBinding",
        "reproducibility",
    },
)
_FULL_SHA = re.compile(r"^[0-9a-f]{40}$")
_ATTEMPT = re.compile(r"^attempt-[0-9]{6}$")
_RUNTIME_PARENT = Path("/private/tmp")
_FORBIDDEN_INHERITED_EXACT = {
    "ALL_PROXY",
    "COLIMA_HOME",
    "DOCKER_AUTH_CONFIG",
    "DOCKER_CONFIG",
    "DOCKER_CONTEXT",
    "DOCKER_HOST",
    "GIT_CONFIG_COUNT",
    "GIT_CONFIG_GLOBAL",
    "GIT_CONFIG_NOSYSTEM",
    "GIT_CONFIG_SYSTEM",
    "HTTP_PROXY",
    "HTTPS_PROXY",
    "LIMA_HOME",
    "NO_PROXY",
    "SSH_AUTH_SOCK",
    "XDG_CONFIG_HOME",
    "XDG_DATA_HOME",
    "XDG_RUNTIME_DIR",
}


def validate_cli(argv: Sequence[str]) -> tuple[str, str]:
    values = list(argv)
    if len(values) != 4 or values[0] != "--policy" or values[2] != "--source-commit":
        raise BuildInputError("local evidence accepts only exact policy and source arguments")
    policy_value, source_commit = values[1], values[3]
    if not policy_value or _FULL_SHA.fullmatch(source_commit) is None:
        raise BuildInputError("local evidence source commit must be a lowercase full Git SHA")
    return policy_value, source_commit


def _reject_inherited_host_environment(inherited: Mapping[str, str]) -> None:
    for name, value in inherited.items():
        upper = name.upper()
        forbidden = (
            name in _FORBIDDEN_INHERITED_EXACT
            or upper.startswith("COLIMA_")
            or upper.startswith("LIMA_")
            or upper.startswith("DOCKER_")
            or upper.startswith("XDG_")
            or upper.startswith("GIT_ASKPASS")
            or upper.startswith("GIT_CREDENTIAL")
        )
        if value and forbidden:
            raise BuildInputError(f"inherited host configuration is forbidden: {name}")


def sanitized_host_environment(
    runtime_root: Path,
    *,
    inherited: Mapping[str, str] | None = None,
) -> dict[str, str]:
    inherited = os.environ if inherited is None else inherited
    _reject_inherited_host_environment(inherited)
    if not runtime_root.is_absolute():
        raise BuildInputError("runtime root must be absolute")
    if runtime_root.exists() or runtime_root.is_symlink():
        raise BuildInputError("isolated runtime root must be new")
    runtime_root.mkdir(mode=0o700)
    home = runtime_root / "host-home"
    colima_home = runtime_root / "colima-home"
    docker_config = runtime_root / "docker-client"
    for path in (home, colima_home, docker_config):
        if path.exists() or path.is_symlink():
            raise BuildInputError("isolated host roots must be new")
        path.mkdir(mode=0o700)
    return {
        "COLIMA_HOME": str(colima_home),
        "DOCKER_CONFIG": str(docker_config),
        "HOME": str(home),
        "LANG": "C.UTF-8",
        "LC_ALL": "C.UTF-8",
        "PATH": "/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin",
        "TZ": "UTC",
    }


def isolated_runtime_root(source_commit: str, policy_sha256: str, attempt_id: str) -> Path:
    """Derive a short, receipt-opaque launcher root from reviewed identities."""

    if _FULL_SHA.fullmatch(source_commit) is None:
        raise BuildInputError("runtime root source identity is malformed")
    if re.fullmatch(r"[0-9a-f]{64}", policy_sha256) is None or _ATTEMPT.fullmatch(attempt_id) is None:
        raise BuildInputError("runtime root policy/attempt identity is malformed")
    token = hashlib.sha256(
        canonical_json_bytes(
            {
                "attemptId": attempt_id,
                "policySha256": policy_sha256,
                "sourceCommit": source_commit,
                "taskId": "quality-task-9-local-linux-evidence",
            },
        ),
    ).hexdigest()[:24]
    root = _RUNTIME_PARENT / f"gst9-{token}"
    # Darwin sockaddr_un.sun_path is 104 bytes including the NUL terminator.
    longest_socket = root / "colima-home/_lima/colima-gasstation-task9-linux-amd64/sock"
    if len(os.fsencode(longest_socket)) >= 104:
        raise BuildInputError("derived runtime root exceeds the Darwin Unix socket boundary")
    return root


def next_attempt(source_root: Path) -> Path:
    if source_root.is_symlink():
        raise BuildInputError("attempt source root may not be a symlink")
    source_root.mkdir(parents=True, exist_ok=True)
    maximum = 0
    for child in source_root.iterdir():
        if not child.is_dir() or child.is_symlink() or _ATTEMPT.fullmatch(child.name) is None:
            raise BuildInputError(f"foreign attempt entry: {child.name}")
        maximum = max(maximum, int(child.name.rsplit("-", 1)[1]))
    if maximum >= 999999:
        raise BuildInputError("attempt sequence exhausted")
    return source_root / f"attempt-{maximum + 1:06d}"


def _marker_body(
    *,
    source_commit: str,
    policy_sha256: str,
    attempt_id: str,
    main_base_commit: str,
    runtime_data_id: str,
) -> dict[str, Any]:
    if _FULL_SHA.fullmatch(source_commit) is None or _FULL_SHA.fullmatch(main_base_commit) is None:
        raise BuildInputError("ownership marker Git identity is malformed")
    if main_base_commit != MAIN_BASE_COMMIT:
        raise BuildInputError("ownership marker main base differs from reviewed literal")
    if re.fullmatch(r"[0-9a-f]{64}", policy_sha256) is None or re.fullmatch(r"[0-9a-f]{64}", runtime_data_id) is None:
        raise BuildInputError("ownership marker digest identity is malformed")
    if _ATTEMPT.fullmatch(attempt_id) is None:
        raise BuildInputError("ownership marker attempt identity is malformed")
    return {
        "attemptId": attempt_id,
        "container": CONTAINER,
        "context": CONTEXT,
        "mainBaseCommit": main_base_commit,
        "mainBaseRef": MAIN_BASE_REF,
        "policySha256": policy_sha256,
        "profile": PROFILE,
        "runtimeDataId": runtime_data_id,
        "schemaVersion": 1,
        "sourceCommit": source_commit,
        "taskId": "quality-task-9-local-linux-evidence",
        "volumes": list(VOLUMES),
    }


def ownership_marker(
    *,
    source_commit: str | None = None,
    policy_sha256: str | None = None,
    attempt_id: str | None = None,
    main_base_commit: str | None = None,
    runtime_data_id: str | None = None,
    existing: Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    if existing is not None:
        if set(existing) != {
            "attemptId", "container", "context", "mainBaseCommit", "mainBaseRef",
            "markerSha256", "policySha256", "profile", "runtimeDataId", "schemaVersion",
            "sourceCommit", "taskId", "volumes",
        }:
            raise BuildInputError("ownership marker schema mismatch")
        body = {key: value for key, value in existing.items() if key != "markerSha256"}
        expected = _marker_body(
            source_commit=str(body["sourceCommit"]),
            policy_sha256=str(body["policySha256"]),
            attempt_id=str(body["attemptId"]),
            main_base_commit=str(body["mainBaseCommit"]),
            runtime_data_id=str(body["runtimeDataId"]),
        )
        if body != expected or existing["markerSha256"] != hashlib.sha256(canonical_json_bytes(body)).hexdigest():
            raise BuildInputError("ownership marker bytes or digest mismatch")
        return dict(existing)
    if None in (source_commit, policy_sha256, attempt_id, main_base_commit, runtime_data_id):
        raise BuildInputError("ownership marker inputs are incomplete")
    body = _marker_body(
        source_commit=str(source_commit),
        policy_sha256=str(policy_sha256),
        attempt_id=str(attempt_id),
        main_base_commit=str(main_base_commit),
        runtime_data_id=str(runtime_data_id),
    )
    return {**body, "markerSha256": hashlib.sha256(canonical_json_bytes(body)).hexdigest()}


def validate_bundle_heads(
    output: str,
    *,
    source_commit: str,
    main_base_commit: str,
) -> dict[str, str]:
    rows: dict[str, str] = {}
    for line in output.splitlines():
        fields = line.split()
        if len(fields) != 2 or _FULL_SHA.fullmatch(fields[0]) is None or fields[1] in rows:
            raise BuildInputError("bundle head inventory is malformed or duplicate")
        rows[fields[1]] = fields[0]
    expected = {"HEAD": source_commit, MAIN_BASE_REF: main_base_commit}
    if rows != expected or main_base_commit != MAIN_BASE_COMMIT:
        raise BuildInputError("bundle heads differ from exact source and reviewed main")
    return rows


def docker_argv(config_root: Path, *arguments: str) -> list[str]:
    if not config_root.is_absolute() or not arguments or any(not value or "\x00" in value for value in arguments):
        raise BuildInputError("Docker argv/config is not closed")
    joined = " ".join(arguments).lower()
    if "system prune" in joined or "volume prune" in joined or "container prune" in joined:
        raise BuildInputError("broad Docker cleanup is forbidden")
    return [DOCKER, "--config", str(config_root), "--context", CONTEXT, *arguments]


def _yaml_scalar(value: str) -> Any:
    if value == "null":
        return None
    if value == "true":
        return True
    if value == "false":
        return False
    if value == "[]":
        return []
    if value == "{}":
        return {}
    if re.fullmatch(r"-?[0-9]+", value):
        return int(value)
    if re.fullmatch(r"-?[0-9]+\.[0-9]+", value):
        return float(value)
    if value.startswith('"') and value.endswith('"'):
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError as error:
            raise BuildInputError("persisted Colima config has an invalid quoted scalar") from error
        if not isinstance(parsed, str):
            raise BuildInputError("persisted Colima quoted scalar is not a string")
        return parsed
    if value.startswith("[") and value.endswith("]"):
        body = value[1:-1].strip()
        if not body:
            return []
        rows = [item.strip() for item in body.split(",")]
        if any(not item or any(character in item for character in "{}[]") for item in rows):
            raise BuildInputError("persisted Colima inline list is unsupported")
        return [_yaml_scalar(item) for item in rows]
    if not value or value[0] in "&*!|>@`" or " #" in value:
        raise BuildInputError("persisted Colima scalar is outside the closed YAML subset")
    return value


def validate_effective_config(text: str, expected: Mapping[str, Any]) -> dict[str, Any]:
    """Parse the complete persisted Colima mapping with a closed YAML subset."""

    root: dict[str, Any] = {}
    stack: list[tuple[int, dict[str, Any] | list[Any], dict[str, Any] | None, str | None]] = [
        (-2, root, None, None),
    ]
    for line_number, raw in enumerate(text.splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        if "\t" in raw or raw.rstrip() != raw:
            raise BuildInputError(f"persisted Colima config whitespace drift at line {line_number}")
        indent = len(raw) - len(raw.lstrip(" "))
        if indent % 2:
            raise BuildInputError(f"persisted Colima config syntax drift at line {line_number}")
        stripped = raw.strip()
        if stripped.startswith("- "):
            while stack and indent <= stack[-1][0]:
                stack.pop()
            if not stack or indent != stack[-1][0] + 2:
                raise BuildInputError("persisted Colima block-list indentation drift")
            _, container, owner, owner_key = stack[-1]
            if isinstance(container, dict):
                if container or owner is None or owner_key is None:
                    raise BuildInputError("persisted Colima block list has no closed list owner")
                replacement: list[Any] = []
                owner[owner_key] = replacement
                stack[-1] = (stack[-1][0], replacement, owner, owner_key)
                container = replacement
            if not isinstance(container, list):
                raise BuildInputError("persisted Colima block list container drift")
            item = stripped[2:].strip()
            if not item or ":" in item:
                raise BuildInputError("persisted Colima block-list item is outside the closed scalar subset")
            container.append(_yaml_scalar(item))
            continue
        if ":" not in raw:
            raise BuildInputError(f"persisted Colima config syntax drift at line {line_number}")
        key, raw_value = raw.strip().split(":", 1)
        if re.fullmatch(r"[A-Za-z][A-Za-z0-9.]*", key) is None:
            raise BuildInputError("persisted Colima config key is outside the closed schema")
        while stack and indent <= stack[-1][0]:
            stack.pop()
        if not stack or indent != stack[-1][0] + 2:
            raise BuildInputError("persisted Colima config indentation drift")
        parent = stack[-1][1]
        if not isinstance(parent, dict):
            raise BuildInputError("persisted Colima list may contain only closed scalar items")
        if key in parent:
            raise BuildInputError(f"duplicate persisted Colima config key: {key}")
        value_text = raw_value.strip()
        if not value_text:
            value: Any = {}
            parent[key] = value
            stack.append((indent, value, parent, key))
        else:
            parent[key] = _yaml_scalar(value_text)
    if root != dict(expected):
        raise BuildInputError("persisted Colima effective config differs from complete policy map")
    return root


def validate_cleanup_proof(proof: Mapping[str, Any]) -> None:
    if proof.get("phases") != list(CLEANUP_PHASES):
        raise BuildInputError("cleanup phase order is incomplete or changed")
    if proof.get("deleteArgv") != list(DELETE_ARGV):
        raise BuildInputError("cleanup must use the literal data-deleting Colima argv")
    for name in (
        "containerAbsentWhileDaemonLive",
        "volumesAbsentWhileDaemonLive",
        "profileAbsent",
        "contextAbsent",
        "runtimeDataAbsent",
    ):
        if proof.get(name) is not True:
            raise BuildInputError(f"cleanup proof is incomplete: {name}")


def aggregate_receipt(
    *,
    source_commit: str,
    policy_sha256: str,
    attempt_id: str,
    rows: Mapping[str, Mapping[str, Any]],
    cleanup_status: str,
) -> dict[str, Any]:
    if set(rows) != REQUIRED_EVIDENCE_ROWS:
        raise BuildInputError("aggregate requires the exact required evidence rows")
    for name, row in rows.items():
        if (
            row.get("status") != "PASS"
            or row.get("sourceCommit") != source_commit
            or row.get("policySha256") != policy_sha256
        ):
            raise BuildInputError(f"mixed evidence identity or non-PASS row: {name}")
    if cleanup_status != "PASS":
        raise BuildInputError("aggregate cleanup is not terminal PASS")
    return {
        "attemptId": attempt_id,
        "boundary": "local-colima-vz-rosetta-emulated-linux-amd64-not-native-x64",
        "cleanupStatus": cleanup_status,
        "hostedEvidence": "NOT RUN",
        "policySha256": policy_sha256,
        "rows": {name: dict(rows[name]) for name in sorted(rows)},
        "schemaVersion": 1,
        "sourceCommit": source_commit,
        "status": "PASS",
        "task8DeviceRuntime": "NOT RUN",
    }


def _run(
    argv: Sequence[str],
    *,
    cwd: Path | None = None,
    env: Mapping[str, str] | None = None,
    timeout: int | None = None,
    stdin: bytes | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[bytes]:
    try:
        completed = subprocess.run(
            list(argv),
            cwd=cwd,
            env=dict(env) if env is not None else None,
            input=stdin,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise BuildInputError(f"closed command failed to execute: {Path(argv[0]).name}") from error
    if check and completed.returncode != 0:
        tail = completed.stdout.decode("utf-8", "replace")[-4096:].strip()
        detail = f"; output={tail}" if tail else ""
        raise BuildInputError(
            f"closed command failed ({completed.returncode}): {Path(argv[0]).name}{detail}",
        )
    return completed


def _git(*arguments: str, check: bool = True) -> str:
    return _run(["git", "-C", str(ROOT), *arguments], check=check).stdout.decode("utf-8", "replace").strip()


def _safe_policy_path(value: str) -> Path:
    path = Path(value)
    candidate = path if path.is_absolute() else ROOT / path
    if candidate.is_symlink():
        raise BuildInputError("local evidence policy may not be a symlink")
    resolved = candidate.resolve(strict=True)
    if not resolved.is_relative_to(ROOT.resolve()):
        raise BuildInputError("local evidence policy escapes the repository")
    return resolved


def _preflight(policy: Mapping[str, Any], source_commit: str) -> tuple[str, str]:
    if _git("rev-parse", "HEAD^{commit}") != source_commit:
        raise BuildInputError("local evidence source does not equal exact HEAD commit")
    if _git("status", "--porcelain=v1", "--untracked-files=all"):
        raise BuildInputError("local evidence requires a clean tracked/untracked worktree")
    main_ref = policy.get("localEvidenceHost", {}).get("mainBaseRef")
    main_commit = policy.get("localEvidenceHost", {}).get("mainBaseCommit")
    if main_ref != MAIN_BASE_REF or main_commit != MAIN_BASE_COMMIT:
        raise BuildInputError("local evidence main-base policy drift")
    if _git("rev-parse", f"{MAIN_BASE_REF}^{{commit}}") != MAIN_BASE_COMMIT:
        raise BuildInputError("original main ref moved from the reviewed literal commit")
    if _git("cat-file", "-t", source_commit) != "commit" or _git("cat-file", "-t", MAIN_BASE_COMMIT) != "commit":
        raise BuildInputError("source/main object type is not commit")
    if _run(["git", "-C", str(ROOT), "merge-base", "--is-ancestor", MAIN_BASE_COMMIT, source_commit], check=False).returncode != 0:
        raise BuildInputError("reviewed main base is not an ancestor of source")
    if _git("replace", "-l"):
        raise BuildInputError("Git replace refs are forbidden")
    return source_commit, MAIN_BASE_COMMIT


def _write_new(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() or path.is_symlink():
        raise BuildInputError(f"immutable evidence output already exists: {path.name}")
    with path.open("xb") as output:
        output.write(canonical_json_bytes(value))


def _write_bytes_new(path: Path, value: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("xb") as output:
        output.write(value)


def _runtime_data_identity(colima_home: Path, source_commit: str, policy_sha: str, attempt_id: str) -> str:
    # Receipt-safe opaque binding for the exact isolated home selected before VM creation.
    payload = {
        "attemptId": attempt_id,
        "colimaHomeOpaque": hashlib.sha256(str(colima_home).encode()).hexdigest(),
        "policySha256": policy_sha,
        "profile": PROFILE,
        "sourceCommit": source_commit,
    }
    return hashlib.sha256(canonical_json_bytes(payload)).hexdigest()


def _existing_environment(attempt: Path, marker: Mapping[str, Any]) -> dict[str, str]:
    legacy = attempt / "colima-home"
    runtime_root = attempt if legacy.exists() else isolated_runtime_root(
        str(marker["sourceCommit"]), str(marker["policySha256"]), str(marker["attemptId"]),
    )
    roots = {
        "HOME": runtime_root / "host-home",
        "COLIMA_HOME": runtime_root / "colima-home",
        "DOCKER_CONFIG": runtime_root / "docker-client",
    }
    for path in roots.values():
        if path.is_symlink() or (path.exists() and not path.is_dir()):
            raise BuildInputError("prior attempt isolated root ownership drift")
    return {
        **{name: str(path) for name, path in roots.items()},
        "LANG": "C.UTF-8",
        "LC_ALL": "C.UTF-8",
        "PATH": "/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin",
        "TZ": "UTC",
    }


def _validate_runtime_marker(runtime_root: Path, marker: Mapping[str, Any]) -> None:
    marker_path = runtime_root / "ownership-marker.json"
    if not marker_path.is_file() or marker_path.is_symlink():
        raise BuildInputError("isolated runtime root is missing its ownership marker")
    if ownership_marker(existing=_load_json(marker_path)) != dict(marker):
        raise BuildInputError("isolated runtime ownership marker differs from attempt marker")
    expected_runtime_id = _runtime_data_identity(
        runtime_root / "colima-home",
        str(marker["sourceCommit"]),
        str(marker["policySha256"]),
        str(marker["attemptId"]),
    )
    if marker["runtimeDataId"] != expected_runtime_id:
        raise BuildInputError("isolated runtime-data identity differs from owned launcher root")


def _owned_labels(marker: Mapping[str, Any]) -> dict[str, str]:
    return {
        "io.gasstation.attempt": str(marker["attemptId"]),
        "io.gasstation.main-base": str(marker["mainBaseCommit"]),
        "io.gasstation.marker-sha256": str(marker["markerSha256"]),
        "io.gasstation.policy-sha256": str(marker["policySha256"]),
        "io.gasstation.source-commit": str(marker["sourceCommit"]),
        "io.gasstation.task": str(marker["taskId"]),
    }


def _load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise BuildInputError(f"prior attempt JSON is unreadable: {path.name}") from error
    if not isinstance(value, dict):
        raise BuildInputError(f"prior attempt JSON is not an object: {path.name}")
    return value


def _recover_prior_attempts(
    attempts_root: Path,
    *,
    source_commit: str,
    policy_sha256: str,
    host_policy: Mapping[str, Any],
) -> None:
    if not attempts_root.exists():
        return
    for attempt in sorted(attempts_root.iterdir()):
        if not attempt.is_dir() or attempt.is_symlink() or _ATTEMPT.fullmatch(attempt.name) is None:
            raise BuildInputError("foreign entry blocks bounded attempt recovery")
        marker_path = attempt / "ownership-marker.json"
        if not marker_path.is_file() or marker_path.is_symlink():
            raise BuildInputError("prior attempt is missing its ownership marker")
        marker = ownership_marker(existing=_load_json(marker_path))
        if (
            marker["sourceCommit"] != source_commit
            or marker["policySha256"] != policy_sha256
            or marker["mainBaseCommit"] != MAIN_BASE_COMMIT
        ):
            raise BuildInputError("mixed source/policy/base attempt blocks recovery")
        environment = _existing_environment(attempt, marker)
        colima_home = Path(environment["COLIMA_HOME"])
        docker_config = Path(environment["DOCKER_CONFIG"])
        runtime_root = colima_home.parent
        _validate_runtime_marker(runtime_root, marker)
        config_candidates = [
            path for path in colima_home.rglob("colima.yaml")
            if path.is_file() and not path.is_symlink()
        ] if colima_home.exists() else []
        aggregate_path = attempt / "package/local-linux-evidence-package.json"
        if aggregate_path.is_file():
            aggregate = _load_json(aggregate_path)
            if aggregate.get("status") == "PASS" and config_candidates:
                raise BuildInputError("stale PASS retains live dedicated runtime data")
        cleanup_path = attempt / "package/cleanup-proof.json"
        if cleanup_path.is_file() and not config_candidates:
            validate_cleanup_proof(_load_json(cleanup_path))
            continue
        if not config_candidates:
            retained_profile_data = any(path.name in {PROFILE, f"colima-{PROFILE}"} for path in colima_home.rglob("*"))
            retained_context = any(
                CONTEXT in path.read_text(encoding="utf-8", errors="ignore")
                for path in docker_config.rglob("meta.json")
            )
            if retained_profile_data or retained_context:
                raise BuildInputError("prior attempt retains unvalidated profile data or Docker context")
            if not (attempt / "recovery.json").exists():
                _write_new(
                    attempt / "recovery.json",
                    {"reason": "no-runtime-created", "schemaVersion": 1, "status": "PASS"},
                )
            continue
        if len(config_candidates) != 1:
            raise BuildInputError("prior attempt has ambiguous persisted profile configs")
        validate_effective_config(
            config_candidates[0].read_text(encoding="utf-8"),
            host_policy.get("effectiveConfig") if isinstance(host_policy.get("effectiveConfig"), dict) else {},
        )
        _run(START_ARGV, env=environment, timeout=1800)
        _verify_docker_client_root(docker_config)
        expected_labels = _owned_labels(marker)

        container_inspect = _docker(docker_config, "inspect", CONTAINER, check=False)
        if container_inspect.returncode == 0:
            values = json.loads(container_inspect.stdout.decode("utf-8"))
            labels = values[0].get("Config", {}).get("Labels", {}) if len(values) == 1 else {}
            if labels != expected_labels:
                raise BuildInputError("foreign or mixed container labels block recovery")
            _docker(docker_config, "stop", CONTAINER, timeout=300, check=False)
            _docker(docker_config, "rm", CONTAINER)
        if _docker(docker_config, "inspect", CONTAINER, check=False).returncode == 0:
            raise BuildInputError("recovery could not prove container absence live")

        for volume in VOLUMES:
            inspected = _docker(docker_config, "volume", "inspect", volume, check=False)
            if inspected.returncode == 0:
                values = json.loads(inspected.stdout.decode("utf-8"))
                labels = values[0].get("Labels", {}) if len(values) == 1 else {}
                if labels != expected_labels:
                    raise BuildInputError("foreign or mixed volume labels block recovery")
                _docker(docker_config, "volume", "rm", volume)
            if _docker(docker_config, "volume", "inspect", volume, check=False).returncode == 0:
                raise BuildInputError("recovery could not prove volume absence live")
        _run(STOP_ARGV, env=environment, timeout=900)
        _run(DELETE_ARGV, env=environment, timeout=900)
        if any(path.name == PROFILE for path in colima_home.rglob("*")):
            raise BuildInputError("recovery retained attempt-owned runtime data")
        if any(CONTEXT in path.read_text(encoding="utf-8", errors="ignore") for path in docker_config.rglob("meta.json")):
            raise BuildInputError("recovery retained dedicated Docker context")
        _write_new(
            attempt / "recovery.json",
            {
                "containerAbsentWhileDaemonLive": True,
                "deleteArgv": list(DELETE_ARGV),
                "runtimeDataAbsent": True,
                "schemaVersion": 1,
                "status": "PASS",
                "volumesAbsentWhileDaemonLive": True,
            },
        )


def _label_arguments(marker: Mapping[str, Any]) -> list[str]:
    return [
        "--label", f"io.gasstation.task={marker['taskId']}",
        "--label", f"io.gasstation.attempt={marker['attemptId']}",
        "--label", f"io.gasstation.marker-sha256={marker['markerSha256']}",
        "--label", f"io.gasstation.policy-sha256={marker['policySha256']}",
        "--label", f"io.gasstation.source-commit={marker['sourceCommit']}",
        "--label", f"io.gasstation.main-base={marker['mainBaseCommit']}",
    ]


def _docker(config: Path, *arguments: str, timeout: int | None = None, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    return _run(docker_argv(config, *arguments), timeout=timeout, check=check)


def _docker_text(config: Path, *arguments: str, timeout: int | None = None, check: bool = True) -> str:
    return _docker(config, *arguments, timeout=timeout, check=check).stdout.decode("utf-8", "replace")


def _container_exec(config: Path, command: str, *, timeout: int | None = None) -> str:
    # The command is selected only by this module; no CLI/policy/caller string reaches it.
    return _docker_text(
        config,
        "exec",
        "--env", "ANDROID_HOME=/opt/android-sdk",
        "--env", "ANDROID_SDK_ROOT=/opt/android-sdk",
        "--env", "CI=true",
        "--env", "RUNNER_ARCH=X64",
        "--env", "RUNNER_OS=Linux",
        "--env", "LANG=C.UTF-8",
        "--env", "LC_ALL=C.UTF-8",
        "--env", "TZ=UTC",
        CONTAINER,
        "/bin/bash",
        "-lc",
        command,
        timeout=timeout,
    )


def _manifest(directory: Path, *, exclude: set[str] | None = None) -> list[dict[str, Any]]:
    exclude = exclude or set()
    rows: list[dict[str, Any]] = []
    for path in sorted(directory.rglob("*")):
        relative = path.relative_to(directory).as_posix()
        if relative in exclude or path.is_dir():
            continue
        if path.is_symlink() or not path.is_file():
            raise BuildInputError("evidence package contains a non-regular path")
        rows.append({"path": relative, "sha256": sha256_file(path), "size": path.stat().st_size})
    return rows


def _verify_docker_client_root(config: Path) -> list[dict[str, Any]]:
    if config.is_symlink() or stat.S_IMODE(config.stat().st_mode) != 0o700:
        raise BuildInputError("Docker client root must remain mode-0700 and nonsymlink")
    rows: list[dict[str, Any]] = []
    for path in sorted(config.rglob("*")):
        relative = path.relative_to(config).as_posix()
        if path.is_symlink():
            raise BuildInputError("Docker client root contains a symlink")
        if path.is_file():
            if path.name == "config.json":
                try:
                    value = json.loads(path.read_text(encoding="utf-8"))
                except (OSError, json.JSONDecodeError) as error:
                    raise BuildInputError("Docker client config.json is invalid") from error
                if value not in ({}, {"currentContext": CONTEXT}):
                    raise BuildInputError("Docker client config contains forbidden state")
            elif not relative.startswith("contexts/"):
                raise BuildInputError("Docker client root contains an unreviewed file")
            rows.append({"path": relative, "sha256": sha256_file(path), "size": path.stat().st_size})
    if not any(row["path"].endswith("meta.json") for row in rows):
        raise BuildInputError("dedicated Docker context metadata is missing")
    return rows


def _bundle(attempt: Path, source_commit: str) -> tuple[Path, dict[str, str]]:
    export = attempt / "export"
    export.mkdir(mode=0o700)
    bundle = export / "source.bundle"
    _run(["git", "-C", str(ROOT), "bundle", "create", str(bundle), "HEAD", MAIN_BASE_REF])
    _run(["git", "-C", str(ROOT), "bundle", "verify", str(bundle)])
    heads = validate_bundle_heads(
        _run(["git", "-C", str(ROOT), "bundle", "list-heads", str(bundle)]).stdout.decode("utf-8", "replace"),
        source_commit=source_commit,
        main_base_commit=MAIN_BASE_COMMIT,
    )
    return bundle, heads


def _row(source: str, policy_sha: str, **extra: Any) -> dict[str, Any]:
    return {"policySha256": policy_sha, "sourceCommit": source, "status": "PASS", **extra}


def _run_evidence(config: Path, source: str, policy_sha: str, attempt: Path) -> tuple[dict[str, dict[str, Any]], dict[str, Any]]:
    repo = "/evidence-work/repository"
    cli = "python3 scripts/quality/verify_build_inputs.py"
    policy = "config/quality/build-inputs.json"
    logs = attempt / "logs"
    logs.mkdir(mode=0o700)

    def command(name: str, shell: str, timeout: int = 14400) -> str:
        output = _container_exec(config, f"cd {repo} && {shell}", timeout=timeout)
        _write_bytes_new(logs / f"{name}.log", output.encode("utf-8", "replace"))
        return output

    metadata_before = command("metadata-capture-1", f"{cli} metadata-capture --policy {policy}")
    metadata_hash_one = command("metadata-hash-1", "sha256sum gradle/verification-metadata.xml").split()[0]
    command("metadata-capture-2", f"{cli} metadata-capture --policy {policy}")
    metadata_hash_two = command("metadata-hash-2", "sha256sum gradle/verification-metadata.xml").split()[0]
    if metadata_hash_one != metadata_hash_two:
        raise BuildInputError("metadata replay changed reviewed bytes")

    strict_output = command("strict-complete", f"{cli} strict-matrix --policy {policy} --group complete")
    if "offline representative: PASS" not in strict_output:
        raise BuildInputError("complete strict matrix omitted same-home offline proof")
    command("strict-product", f"{cli} strict-matrix --policy {policy} --group product-regressions")
    command("configuration-cache", f"{cli} configuration-cache --policy {policy}")

    evidence_commands = (
        "python3 scripts/quality/build_inputs/docs_gradle_validation_bridge.py --check-gradle-tasks",
        "scripts/agent/verify-room-schemas.sh",
        "scripts/agent/verify.sh auto",
        "scripts/agent/verify.sh docs",
    )
    for index, governed in enumerate(evidence_commands, 1):
        command(
            f"evidence-session-{index}",
            f"{cli} evidence-session --policy {policy} -- {governed}",
        )

    reproduction_path = "build/reports/build-inputs/local-reproducibility.json"
    command(
        "reproducibility",
        f"{cli} reproduce --policy {policy} --source-commit {source} --output {reproduction_path}",
    )

    # Third build: separate fixed JDK/Gradle home and cache, no signing/cache reuse.
    command("third-jdks", f"{cli} install-jdks --policy {policy} --output-root /evidence-work/third-jdks")
    third = (
        "export JAVA_HOME=/evidence-work/third-jdks/runtime-ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94 "
        + "JAVA_HOME_17_X64=/evidence-work/third-jdks/compile-be7668bc030d578b83d6d5ef9221d6d6729bbbca8cf94a7d52e16ac68b5a5a35 "
        + "JAVA_HOME_21_X64=/evidence-work/third-jdks/runtime-ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94 "
        + "GRADLE_USER_HOME=/evidence-work/third-gradle-home; "
        + "export PATH=$JAVA_HOME/bin:/usr/local/bin:/usr/bin:/bin; "
        + "rm -rf app/build /evidence-work/third-project-cache; "
        + "./gradlew :app:assembleProdRelease --no-build-cache --no-configuration-cache --rerun-tasks "
        + "--project-cache-dir /evidence-work/third-project-cache --dependency-verification strict "
        + "-Dorg.gradle.java.installations.auto-detect=false "
        + "-Dorg.gradle.java.installations.auto-download=false "
        + "-Dorg.gradle.java.installations.paths=$JAVA_HOME_17_X64,$JAVA_HOME_21_X64"
    )
    command("third-release", third)
    apk = command(
        "third-apk",
        "python3 -c \"import pathlib; p=list(pathlib.Path('app/build/outputs/apk/prod/release').glob('*.apk')); assert len(p)==1; print(p[0])\"",
    ).strip()
    artifact_name = f"reproducible-prod-release-receipt-{source}"
    binding_path = "build/reports/build-inputs/local-release-binding.json"
    command(
        "release-binding",
        f"{cli} release-bind --policy {policy} --receipt {reproduction_path} --apk {apk} "
        f"--source-commit {source} --artifact-name {artifact_name} --output {binding_path}",
    )

    # The required negative suite is committed and run in the same clean clone.
    command(
        "mutations",
        "PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.quality.tests.test_local_colima_evidence -v",
        timeout=600,
    )
    clean = command("post-evidence-clean", "git status --porcelain=v1 --untracked-files=all")
    # Only ignored build evidence may exist.  Any tracked or unignored output is fatal.
    if clean.strip():
        raise BuildInputError("evidence commands contaminated the clean bundle clone")

    rows = {
        "configurationCache": _row(source, policy_sha),
        "evidenceSessions": _row(source, policy_sha, count=4),
        "metadataCapture": _row(source, policy_sha, replaySha256=metadata_hash_two),
        "mutations": _row(source, policy_sha, suite="local-colima-contract"),
        "offlineStrict": _row(source, policy_sha, sameHome=True),
        "onlineColdStrict": _row(source, policy_sha),
        "productStrict": _row(source, policy_sha),
        "releaseBinding": _row(source, policy_sha, receipt=binding_path),
        "reproducibility": _row(source, policy_sha, receipt=reproduction_path),
    }
    details = {
        "releaseBindingReceipt": binding_path,
        "reproducibilityReceipt": reproduction_path,
        "thirdApk": apk,
    }
    return rows, details


def _safe_error(error: BaseException) -> str:
    message = str(error).replace(str(ROOT), "<repository>")
    message = re.sub(r"(?:/Users/|/private/var/|/tmp/)[^\s'\"]+", "<opaque-path>", message)
    return message


def main(argv: Sequence[str] | None = None) -> int:
    attempt: Path | None = None
    resources_created = False
    environment: dict[str, str] | None = None
    docker_config: Path | None = None
    marker: dict[str, Any] | None = None
    rows: dict[str, dict[str, Any]] | None = None
    try:
        policy_value, source_commit = validate_cli(sys.argv[1:] if argv is None else argv)
        policy_path = _safe_policy_path(policy_value)
        policy = load_policy(policy_path, root=ROOT)
        host = policy.get("localEvidenceHost")
        if not isinstance(host, dict):
            raise BuildInputError("localEvidenceHost policy is missing")
        _preflight(policy, source_commit)
        policy_sha = sha256_file(policy_path)
        colima_identity = _run([COLIMA, "version"]).stdout.decode("utf-8", "replace")
        lima_identity = _run(["/opt/homebrew/bin/limactl", "--version"]).stdout.decode("utf-8", "replace")
        if "colima version 0.10.1" not in colima_identity or "ed905203afdbc6fd4eae6cc301918099ff31e86e" not in colima_identity:
            raise BuildInputError("Colima executable identity differs from reviewed 0.10.1")
        if "2.1.1" not in lima_identity:
            raise BuildInputError("Lima executable identity differs from reviewed 2.1.1")

        attempts_root = ROOT / ".codex/task-cache" / PROFILE / source_commit
        _recover_prior_attempts(
            attempts_root,
            source_commit=source_commit,
            policy_sha256=policy_sha,
            host_policy=host,
        )
        _reject_inherited_host_environment(os.environ)
        attempt = next_attempt(attempts_root)
        attempt.mkdir(mode=0o700)
        runtime_root = isolated_runtime_root(source_commit, policy_sha, attempt.name)
        environment = sanitized_host_environment(runtime_root, inherited={})
        docker_config = Path(environment["DOCKER_CONFIG"])
        runtime_data_id = _runtime_data_identity(
            Path(environment["COLIMA_HOME"]), source_commit, policy_sha, attempt.name,
        )
        marker = ownership_marker(
            source_commit=source_commit,
            policy_sha256=policy_sha,
            attempt_id=attempt.name,
            main_base_commit=MAIN_BASE_COMMIT,
            runtime_data_id=runtime_data_id,
        )
        _write_new(attempt / "ownership-marker.json", marker)
        _write_new(runtime_root / "ownership-marker.json", marker)
        bundle, bundle_heads = _bundle(attempt, source_commit)

        if tuple(host.get("startArgv", ())) != START_ARGV or tuple(host.get("stopArgv", ())) != STOP_ARGV or tuple(host.get("deleteArgv", ())) != DELETE_ARGV:
            raise BuildInputError("localEvidenceHost literal Colima argv drift")
        resources_created = True
        _run(START_ARGV, env=environment, timeout=1800)
        config_candidates = [
            path
            for path in Path(environment["COLIMA_HOME"]).rglob("colima.yaml")
            if path.is_file() and not path.is_symlink()
        ]
        if len(config_candidates) != 1:
            raise BuildInputError("dedicated profile did not produce one regular persisted config")
        effective_config = validate_effective_config(
            config_candidates[0].read_text(encoding="utf-8"),
            host.get("effectiveConfig") if isinstance(host.get("effectiveConfig"), dict) else {},
        )
        client_inventory = _verify_docker_client_root(docker_config)

        version_json = _docker_text(docker_config, "version", "--format", "{{json .}}", timeout=120)
        info_json = _docker_text(docker_config, "info", "--format", "{{json .}}", timeout=120)
        try:
            version_value = json.loads(version_json)
            info_value = json.loads(info_json)
        except json.JSONDecodeError as error:
            raise BuildInputError("Docker runtime identity JSON is malformed") from error
        if (
            version_value.get("Client", {}).get("Version") != "29.4.0"
            or version_value.get("Client", {}).get("Arch") != "arm64"
            or version_value.get("Server", {}).get("Version") != "29.2.1"
            or version_value.get("Server", {}).get("Arch") != "arm64"
            or info_value.get("Architecture") != "aarch64"
            or info_value.get("OSType") != "linux"
        ):
            raise BuildInputError("dedicated Docker daemon/client transport identity drift")
        contexts = _docker_text(docker_config, "context", "ls", "--format", "{{.Name}}")
        if contexts.splitlines() != [CONTEXT]:
            raise BuildInputError("Docker client root contains another or missing context")

        _docker(docker_config, "pull", "--platform", "linux/amd64", IMAGE, timeout=1800)
        image_json = _docker_text(docker_config, "image", "inspect", IMAGE)
        parsed_image = json.loads(image_json)
        if len(parsed_image) != 1 or parsed_image[0].get("Architecture") != "amd64" or parsed_image[0].get("Id") != IMAGE_CONFIG:
            raise BuildInputError("reviewed Ubuntu amd64 configuration identity mismatch")
        image_manifest = _docker_text(docker_config, "manifest", "inspect", "--verbose", IMAGE, timeout=300)

        labels = _label_arguments(marker)
        for volume in VOLUMES:
            _docker(docker_config, "volume", "create", *labels, volume)
        _docker(
            docker_config,
            "create",
            "--platform", "linux/amd64",
            "--name", CONTAINER,
            *labels,
            "--mount", f"type=volume,source={VOLUMES[0]},target=/evidence-work",
            "--mount", f"type=volume,source={VOLUMES[1]},target=/opt/android-sdk",
            IMAGE,
            "/bin/bash", "-lc", "trap : TERM INT; sleep infinity & wait",
        )
        _docker(docker_config, "start", CONTAINER)
        _container_exec(docker_config, "mkdir -p /input-repository")
        _docker(docker_config, "cp", str(bundle), f"{CONTAINER}:/input-repository/source.bundle")

        bootstrap = (
            "set -euo pipefail; export DEBIAN_FRONTEND=noninteractive; "
            "apt-get update; apt-get install -y --no-install-recommends "
            "'ca-certificates=20260601~24.04.1' 'curl=8.5.0-2ubuntu10.12' "
            "'git=1:2.43.0-1ubuntu7.3' 'locales=2.39-0ubuntu8.8' "
            "'python3=3.12.3-0ubuntu2.1' 'unzip=6.0-28ubuntu4.1' "
            "'xz-utils=5.6.1+really5.4.5-1ubuntu0.3'; "
            "mkdir -p /evidence-work/downloads/apt; cd /evidence-work/downloads/apt; "
            "apt-get download 'ca-certificates=20260601~24.04.1' 'curl=8.5.0-2ubuntu10.12' "
            "'git=1:2.43.0-1ubuntu7.3' 'locales=2.39-0ubuntu8.8' "
            "'python3=3.12.3-0ubuntu2.1' 'unzip=6.0-28ubuntu4.1' "
            "'xz-utils=5.6.1+really5.4.5-1ubuntu0.3'; "
            "printf '%s  %s\\n' "
            "6bac2a01979e210d9eac1d4d56747ec709ea60654744d66705dc3c36e7629e50 'ca-certificates_20260601~24.04.1_all.deb' "
            "dd809918a149964c9d248662a6937082ca46f8ed76bd6d875928566035e0342f 'curl_8.5.0-2ubuntu10.12_amd64.deb' "
            "099bb129f543adc4c14203334b0fa0a909f8bf038c4d56bc9cc7c774ebf78f87 'git_1%3a2.43.0-1ubuntu7.3_amd64.deb' "
            "cdd2d347a357da6b9b1f2bd9e08c10a2a3a4686fad050791d30915d0ce0bb506 'locales_2.39-0ubuntu8.8_all.deb' "
            "e691b9cc40841c41bbdc50bd794c876cb1b1801306ea27b06e9a1458180df1e9 'python3_3.12.3-0ubuntu2.1_amd64.deb' "
            "a505b9d491386167bd8e14e3383315a4a7d6539e4406745901ccf009a7988271 'unzip_6.0-28ubuntu4.1_amd64.deb' "
            "778edae086bc8f34d80f36f301bc8fb3eff2d906c146dfb533ea6840b6d64e00 'xz-utils_5.6.1+really5.4.5-1ubuntu0.3_amd64.deb' | sha256sum -c -; "
            "cd /; rm -rf /var/lib/apt/lists/*; "
            "git clone --no-local /input-repository/source.bundle /evidence-work/repository; "
            f"cd /evidence-work/repository; git checkout --detach {source_commit}; "
            f"git update-ref refs/heads/main {MAIN_BASE_COMMIT}; git remote remove origin; "
            f"test \"$(git rev-parse HEAD)\" = {source_commit}; "
            f"test \"$(git rev-parse refs/heads/main)\" = {MAIN_BASE_COMMIT}; "
            f"git merge-base --is-ancestor {MAIN_BASE_COMMIT} {source_commit}; "
            "test -z \"$(git status --porcelain=v1 --untracked-files=all)\"; test -z \"$(git remote)\"; "
            "test ! -e .git/objects/info/alternates; test -z \"$(git replace -l)\"; "
            "mkdir -p /opt/android-sdk/cmdline-tools /evidence-work/downloads; "
            "python3 -c \"import hashlib,pathlib,urllib.request; u='https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip'; p=pathlib.Path('/evidence-work/downloads/cmdline.zip'); d=urllib.request.urlopen(u,timeout=120).read(); assert len(d)==181833628; assert hashlib.sha256(d).hexdigest()=='4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583'; p.write_bytes(d)\"; "
            "python3 -m zipfile -e /evidence-work/downloads/cmdline.zip /evidence-work/downloads/cmdline; "
            "mv /evidence-work/downloads/cmdline/cmdline-tools /opt/android-sdk/cmdline-tools/latest; "
            "yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk --licenses >/dev/null; "
            "/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk 'build-tools;36.0.0' 'platforms;android-37' 'platform-tools'; "
            "test -f /opt/android-sdk/cmdline-tools/latest/package.xml; "
            "test -f /opt/android-sdk/build-tools/36.0.0/package.xml; "
            "test -f /opt/android-sdk/platforms/android-37/package.xml; "
            "test -f /opt/android-sdk/platform-tools/package.xml; "
            "test \"$(find /opt/android-sdk -name package.xml -type f | wc -l | tr -d ' ')\" = 4; "
            "test ! -d /opt/android-sdk/emulator; test -z \"$(find /opt/android-sdk/system-images -mindepth 1 -print -quit 2>/dev/null || true)\"; "
            "uname -m; python3 -c 'import platform; assert platform.machine() in {\"x86_64\",\"amd64\"}'"
        )
        bootstrap_output = _container_exec(docker_config, bootstrap, timeout=3600)
        if "x86_64" not in bootstrap_output:
            raise BuildInputError("inner container did not prove Linux x86_64")

        rows, details = _run_evidence(docker_config, source_commit, policy_sha, attempt)
        runtime_facts = _container_exec(
            docker_config,
            "set -euo pipefail; "
            "dpkg-query -W -f='${Package}=${Version}\\n' ca-certificates curl git locales python3 unzip xz-utils | sort; "
            "sha256sum /bin/bash /bin/tar /usr/bin/curl /usr/bin/git /usr/bin/python3.12 /usr/bin/unzip /usr/bin/xz; "
            "/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk --list_installed; "
            "find /opt/android-sdk -name package.xml -type f -print0 | sort -z | xargs -0 sha256sum; "
            "sha256sum /opt/android-sdk/build-tools/36.0.0/aapt2 "
            "/opt/android-sdk/build-tools/36.0.0/apksigner "
            "/opt/android-sdk/build-tools/36.0.0/zipalign /opt/android-sdk/platform-tools/adb; "
            "/opt/android-sdk/platform-tools/adb version",
            timeout=600,
        )
        _write_bytes_new(attempt / "logs/bootstrap-runtime-facts.log", runtime_facts.encode("utf-8"))
        container_facts = {
            "bootstrapLogSha256": hashlib.sha256(bootstrap_output.encode()).hexdigest(),
            "bootstrapRuntimeFactsSha256": hashlib.sha256(runtime_facts.encode()).hexdigest(),
            "bundleHeads": bundle_heads,
            "bundleSha256": sha256_file(bundle),
            "dockerInfoSha256": hashlib.sha256(info_json.encode()).hexdigest(),
            "dockerVersionSha256": hashlib.sha256(version_json.encode()).hexdigest(),
            "imageConfig": IMAGE_CONFIG,
            "imageIndex": IMAGE,
            "imageInspectSha256": hashlib.sha256(image_json.encode()).hexdigest(),
            "imageManifestSha256": hashlib.sha256(image_manifest.encode()).hexdigest(),
        }
        host_receipt = {
            "architectureBoundary": "macos-aarch64-vz-rosetta-to-linux-amd64",
            "attemptId": attempt.name,
            "clientInventory": client_inventory,
            "colimaVersion": "0.10.1",
            "container": CONTAINER,
            "context": CONTEXT,
            "details": details,
            "dockerClientVersion": "29.4.0",
            "dockerServerVersion": "29.2.1",
            "emptyHostMounts": True,
            "effectiveConfig": effective_config,
            "effectiveConfigSha256": sha256_file(config_candidates[0]),
            "facts": container_facts,
            "mainBaseCommit": MAIN_BASE_COMMIT,
            "mainBaseRef": MAIN_BASE_REF,
            "markerSha256": marker["markerSha256"],
            "policySha256": policy_sha,
            "profile": PROFILE,
            "runtimeDataId": runtime_data_id,
            "schemaVersion": 1,
            "sourceCommit": source_commit,
            "startArgv": list(START_ARGV),
            "status": "PENDING",
            "volumes": list(VOLUMES),
            "unreachedAndroidPackages": [
                {"coordinate": "emulator", "runtimeEvidence": "NOT RUN"},
                {"coordinate": "system-images;android-24;google_apis;x86_64", "runtimeEvidence": "NOT RUN"},
                {"coordinate": "system-images;android-28;default;x86_64", "runtimeEvidence": "NOT RUN"},
                {"coordinate": "system-images;android-36;google_apis;x86_64", "runtimeEvidence": "NOT RUN"},
            ],
        }
        staged = attempt / "package"
        staged.mkdir(mode=0o700)
        _write_new(staged / "local-linux-host-pending.json", host_receipt)
        _write_new(staged / "evidence-rows.json", {"rows": rows, "schemaVersion": 1, "status": "PASS"})
        shutil.copytree(attempt / "logs", staged / "logs")
        _docker(
            docker_config,
            "cp",
            f"{CONTAINER}:/evidence-work/repository/build/reports/build-inputs",
            str(staged / "container-build-inputs"),
        )
        _write_new(staged / "staged-manifest.json", {"files": _manifest(staged), "schemaVersion": 1, "status": "PENDING"})

        # Ordered, exact, ownership-revalidated cleanup through the live daemon.
        owned_labels = _owned_labels(marker)
        container_value = json.loads(_docker_text(docker_config, "inspect", CONTAINER))
        if len(container_value) != 1 or container_value[0].get("Config", {}).get("Labels", {}) != owned_labels:
            raise BuildInputError("owned evidence container label drift before cleanup")
        _docker(docker_config, "stop", CONTAINER, timeout=300)
        _docker(docker_config, "rm", CONTAINER)
        container_absent = _docker(docker_config, "inspect", CONTAINER, check=False).returncode != 0
        if not container_absent:
            raise BuildInputError("owned evidence container remained after removal")
        for volume in VOLUMES:
            volume_value = json.loads(_docker_text(docker_config, "volume", "inspect", volume))
            if len(volume_value) != 1 or volume_value[0].get("Labels", {}) != owned_labels:
                raise BuildInputError("owned evidence volume label drift before cleanup")
            _docker(docker_config, "volume", "rm", volume)
        volumes_absent = all(
            _docker(docker_config, "volume", "inspect", volume, check=False).returncode != 0
            for volume in VOLUMES
        )
        if not volumes_absent:
            raise BuildInputError("owned evidence volumes remained after removal")
        daemon_proof = {
            "containerAbsentWhileDaemonLive": True,
            "dockerInfoSha256": hashlib.sha256(_docker_text(docker_config, "info", "--format", "{{json .}}").encode()).hexdigest(),
            "dockerVersionSha256": hashlib.sha256(_docker_text(docker_config, "version", "--format", "{{json .}}").encode()).hexdigest(),
            "runtimeDataId": runtime_data_id,
            "schemaVersion": 1,
            "volumesAbsentWhileDaemonLive": True,
        }
        _write_new(staged / "daemon-proof.json", daemon_proof)
        _run(STOP_ARGV, env=environment, timeout=900)
        _run(DELETE_ARGV, env=environment, timeout=900)
        profile_root = Path(environment["COLIMA_HOME"]) / "_profiles" / PROFILE
        profile_absent = not profile_root.exists()
        context_absent = not any(CONTEXT in path.read_text(encoding="utf-8", errors="ignore") for path in docker_config.rglob("meta.json"))
        # --data deletion must leave no profile-owned data below the isolated home.
        runtime_absent = not any(path.name == PROFILE for path in Path(environment["COLIMA_HOME"]).rglob("*"))
        cleanup = {
            "containerAbsentWhileDaemonLive": container_absent,
            "contextAbsent": context_absent,
            "deleteArgv": list(DELETE_ARGV),
            "phases": list(CLEANUP_PHASES),
            "profileAbsent": profile_absent,
            "runtimeDataAbsent": runtime_absent,
            "volumesAbsentWhileDaemonLive": volumes_absent,
        }
        validate_cleanup_proof(cleanup)
        aggregate = aggregate_receipt(
            source_commit=source_commit,
            policy_sha256=policy_sha,
            attempt_id=attempt.name,
            rows=rows,
            cleanup_status="PASS",
        )
        terminal_host_receipt = {
            **{key: value for key, value in host_receipt.items() if key != "status"},
            "cleanup": cleanup,
            "status": "PASS",
        }
        _write_new(staged / "local-linux-host.json", terminal_host_receipt)
        _write_new(staged / "cleanup-proof.json", cleanup)
        _write_new(staged / "local-linux-evidence-package.json", aggregate)
        _write_new(staged / "terminal-manifest.json", {"files": _manifest(staged), "schemaVersion": 1, "status": "PASS"})

        output_root = ROOT / "build/reports/build-inputs"
        output_root.mkdir(parents=True, exist_ok=True)
        for name in ("local-linux-host.json", "local-linux-evidence-package.json"):
            target = output_root / name
            if target.exists():
                target.unlink()
            shutil.copyfile(staged / name, target)
        print(
            f"local Linux evidence: PASS attempt={attempt.name} "
            f"aggregateSha256={sha256_file(staged / 'local-linux-evidence-package.json')}",
        )
        return 0
    except (BuildInputError, OSError, ValueError, subprocess.SubprocessError) as error:
        if attempt is not None:
            failure = {
                "error": _safe_error(error),
                "resourcesCreated": resources_created,
                "schemaVersion": 1,
                "status": "FAIL",
            }
            try:
                _write_new(attempt / "failure.json", failure)
            except (BuildInputError, OSError):
                pass
        print(f"local Linux evidence failed: {_safe_error(error)}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
