#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, Mapping, Sequence
from urllib.parse import urlsplit

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.quality.build_inputs.contracts import (  # noqa: E402
    BuildInputError,
    load_policy,
    scan_dependency_verification_bypasses,
    scan_dynamic_dependency_selectors,
    sha256_file,
    validate_protected_environment,
    verify_wrapper,
)
from scripts.quality.build_inputs.archive import ArchiveError  # noqa: E402
from scripts.quality.build_inputs.downloader import DownloadError, download_verified  # noqa: E402
from scripts.quality.build_inputs.receipts import (  # noqa: E402
    canonical_receipt,
    parse_os_release,
    relative_evidence_rows,
    write_canonical_receipt,
)
from scripts.quality.build_inputs.reproducibility import (  # noqa: E402
    run_reproducibility_probe,
    verify_release_binding,
)
from scripts.quality.build_inputs.runtime import (  # noqa: E402
    InstalledJdks,
    assert_supported_host,
    export_github_java_environment,
    inspect_installed_jdks,
    install_verified_jdks,
    sanitized_environment,
    sealed_gradle_arguments,
    write_evidence_arguments,
)
from scripts.quality.build_inputs.workflow import (  # noqa: E402
    build_inputs_is_promoted,
    verify_repository_workflows,
)
from scripts.quality.build_inputs.generate_policy import (  # noqa: E402
    docs_parent_edges,
    evidence_entrypoints,
)
from scripts.quality.build_inputs.testkit_failure import export_testkit_failure_evidence  # noqa: E402


ROOT = Path(__file__).resolve().parents[2]
_STRICT_GROUPS = {"complete", "product-regressions"}
_SENSITIVE_DIAGNOSTIC = re.compile(
    r"(?:github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9]+|\bBearer\s+\S+|\bsk-[A-Za-z0-9_-]+)",
    re.IGNORECASE,
)
_GOVERNED_OUTPUT_LIMIT = 65536
_SENSITIVE_ASSIGNMENT = re.compile(
    r"(?i)\b(token|secret|password|credential|cookie|authorization)(\s*[=:]\s*)([^\s&]+)",
)
_ABSOLUTE_DIAGNOSTIC_PATH = re.compile(r"(?<![A-Za-z0-9:/])/(?:[^\s'\"]+)")
_TESTKIT_FAILURE_OUTPUT = re.compile(r"/evidence-work/testkit-failures/metadata-capture-[12]")


def exact_evidence_command(policy: Mapping[str, Any], command: Sequence[str]) -> tuple[str, ...]:
    candidate = list(command)
    allowed = policy.get("evidenceSessionCommands")
    if not isinstance(allowed, list) or candidate not in allowed:
        raise BuildInputError("evidence-session command is not in the exact four-command allowlist")
    if len(allowed) != 4 or len({tuple(row) for row in allowed if isinstance(row, list)}) != 4:
        raise BuildInputError("evidence-session policy must contain exactly four unique commands")
    return tuple(candidate)


def _policy_path(value: str) -> Path:
    path = Path(value)
    if not path.is_absolute():
        path = ROOT / path
    if path.is_symlink():
        raise BuildInputError("policy may not be a symlink")
    try:
        resolved = path.resolve(strict=True)
    except OSError as error:
        raise BuildInputError("policy file is missing") from error
    if not resolved.is_relative_to(ROOT.resolve()):
        raise BuildInputError("policy must be inside the repository")
    return resolved


def _load(value: str) -> tuple[Path, dict[str, Any]]:
    path = _policy_path(value)
    return path, load_policy(path, root=ROOT)


def _verify_static_hashes(policy: Mapping[str, Any]) -> None:
    for row in policy["staticSourceHashes"]:
        target = ROOT / row["path"]
        if sha256_file(target) != row["sha256"]:
            raise BuildInputError(f"static source SHA-256 mismatch: {row['path']}")


def _metadata_counts(policy: Mapping[str, Any]) -> dict[str, int | str]:
    dependency = policy.get("dependencyVerification")
    if not isinstance(dependency, dict):
        raise BuildInputError("dependencyVerification policy is missing")
    metadata_path = dependency.get("metadataPath")
    if not isinstance(metadata_path, str):
        raise BuildInputError("dependency verification metadata path is missing")
    path = ROOT / metadata_path
    if not path.is_file() or path.is_symlink():
        raise BuildInputError("dependency verification metadata is missing")
    try:
        tree = ET.parse(path)
    except (ET.ParseError, OSError) as error:
        raise BuildInputError("dependency verification metadata is malformed") from error
    root = tree.getroot()
    verify_metadata = root.findtext("./{*}configuration/{*}verify-metadata")
    if dependency.get("verifyMetadata", True) is True and verify_metadata != "true":
        raise BuildInputError("dependency verification metadata must verify module metadata")
    trusted = root.findall("./{*}configuration/{*}trusted-artifacts/{*}trust")
    if trusted:
        raise BuildInputError("broad trusted artifacts are forbidden")
    ignored = root.findall(".//{*}ignored-key") + root.findall(".//{*}ignored-artifact")
    if ignored:
        raise BuildInputError("ignored dependency verification entries are forbidden")
    components = root.findall("./{*}components/{*}component")
    artifacts = root.findall("./{*}components/{*}component/{*}artifact")
    checksums = root.findall("./{*}components/{*}component/{*}artifact/{*}sha256")
    if not components or not artifacts or not checksums:
        raise BuildInputError("dependency verification metadata is incomplete")
    for artifact in artifacts:
        if not artifact.findall("{*}sha256"):
            raise BuildInputError("every dependency artifact must have a SHA-256")
        if artifact.findall("{*}md5") or artifact.findall("{*}sha1"):
            raise BuildInputError("weak dependency checksums are forbidden")
    return {
        "artifacts": len(artifacts),
        "checksums": len(checksums),
        "components": len(components),
        "sha256": sha256_file(path),
    }


def verify_repository(policy: Mapping[str, Any]) -> dict[str, Any]:
    verify_wrapper(ROOT, policy)
    _verify_static_hashes(policy)
    actual_entrypoints = policy.get("evidenceGradleEntrypoints")
    expected_entrypoints = evidence_entrypoints()
    if actual_entrypoints != expected_entrypoints:
        actual_ids = {
            row.get("id") for row in actual_entrypoints or [] if isinstance(row, dict)
        }
        expected_ids = {row["id"] for row in expected_entrypoints}
        raise BuildInputError(
            "entrypoint inventory mismatch: "
            f"missing={sorted(expected_ids - actual_ids)} "
            f"extra={sorted(actual_ids - expected_ids)}",
        )
    for row in expected_entrypoints:
        source = (ROOT / str(row["owner"])).read_text(encoding="utf-8")
        argv = row["argv"]
        assert isinstance(argv, list)
        executable = argv[0]
        if executable == "python3":
            signature = argv[1]
        elif executable == "GradleRunner.withArguments":
            signature = (
                ".adversarialRunner"
                if row["id"] == "testkit/adversarial"
                else ".withArguments"
            )
        elif executable in {"./gradlew", "verify_build_inputs.py"}:
            signature = argv[1]
        else:
            signature = executable
        if signature not in source:
            raise BuildInputError(
                f"entrypoint inventory source signature missing: {row['id']} ({signature})",
            )
    docs = policy.get("docsValidation")
    if not isinstance(docs, dict) or docs.get("parentEdges") != docs_parent_edges():
        raise BuildInputError("docs bridge parent inventory mismatch")
    bypasses = scan_dependency_verification_bypasses(ROOT)
    if bypasses:
        raise BuildInputError(bypasses[0])
    dynamic = scan_dynamic_dependency_selectors(ROOT)
    if dynamic:
        raise BuildInputError(dynamic[0])
    workflow_text = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
    promoted = build_inputs_is_promoted(workflow_text)
    verify_repository_workflows(ROOT, policy, promoted=promoted)
    metadata = _metadata_counts(policy)
    return {
        "dependencyVerification": metadata,
        "dynamicDependencySelectors": 0,
        "schemaVersion": 1,
        "status": "PASS",
    }


def _row_argv(row: Any, *, context: str) -> list[str]:
    if isinstance(row, list) and row and all(isinstance(item, str) and item for item in row):
        return _validate_closed_argv(list(row), context=context)
    if isinstance(row, dict):
        unknown = set(row) - {"argv", "command", "id", "offline", "timeoutSeconds"}
        if unknown:
            raise BuildInputError(f"{context} has unknown command fields: {sorted(unknown)}")
        value = row.get("argv", row.get("command"))
        if isinstance(value, list) and value and all(isinstance(item, str) and item for item in value):
            return _validate_closed_argv(list(value), context=context)
    raise BuildInputError(f"{context} must be a closed argv array")


def _validate_closed_argv(argv: list[str], *, context: str) -> list[str]:
    if any("\x00" in token or "\n" in token or "\r" in token for token in argv):
        raise BuildInputError(f"{context} contains a control character")
    if any(token in {"&&", "||", "|", ";", "<", ">"} for token in argv):
        raise BuildInputError(f"{context} contains shell control syntax")
    for index, token in enumerate(argv):
        if token in {"-" + "I", "--init-" + "script"} or token.startswith("-" + "I") or token.startswith("--init-" + "script="):
            raise BuildInputError(f"{context} contains a forbidden init script")
        lowered = token.lower()
        weak_property = (
            "org.gradle.dependency.verification=" + "off" in lowered
            or "org.gradle.dependency.verification=" + "lenient" in lowered
        )
        if weak_property:
            raise BuildInputError(f"{context} weakens dependency verification")
        if token.startswith("--dependency-verification=") and token != "--dependency-verification=strict":
            raise BuildInputError(f"{context} weakens dependency verification")
        if token == "--dependency-verification":
            if index + 1 >= len(argv) or argv[index + 1] != "strict":
                raise BuildInputError(f"{context} weakens dependency verification")
        if token == "--write-verification-metadata" or token.startswith("--write-verification-metadata="):
            raise BuildInputError(f"{context} may not embed a metadata write flag")
    first = argv[0]
    if first == "./gradlew":
        return argv
    if first == "python3":
        if argv != [
            "python3",
            "scripts/quality/build_inputs/docs_gradle_validation_bridge.py",
            "--check-gradle-tasks",
        ]:
            raise BuildInputError(f"{context} Python entrypoint is not the stable docs bridge")
        return argv
    if first.startswith(("scripts/agent/", "scripts/quality/")):
        path = ROOT / first
        if path.is_symlink() or not path.is_file():
            raise BuildInputError(f"{context} repository script is missing")
        return argv
    raise BuildInputError(f"{context} executable is outside the closed repository entrypoints")


def closed_group_commands(policy: Mapping[str, Any], group: str) -> list[list[str]]:
    if group not in _STRICT_GROUPS:
        raise BuildInputError(f"unknown strict-matrix group: {group}")
    dependency = policy.get("dependencyVerification")
    if not isinstance(dependency, dict):
        raise BuildInputError("dependencyVerification policy is missing")
    groups = dependency.get("strictGroups")
    if not isinstance(groups, dict) or set(groups) != _STRICT_GROUPS:
        raise BuildInputError("dependencyVerification.strictGroups must define the exact closed groups")
    rows = groups.get(group)
    if not isinstance(rows, list) or not rows:
        raise BuildInputError(f"strict-matrix group is empty: {group}")
    commands = [_row_argv(row, context=f"strictGroups.{group}[{index}]") for index, row in enumerate(rows)]
    if len(commands) != len({tuple(command) for command in commands}):
        raise BuildInputError(f"strict-matrix group contains duplicate commands: {group}")
    return commands


def _configuration_cache_commands(policy: Mapping[str, Any]) -> list[list[str]]:
    dependency = policy.get("dependencyVerification")
    if not isinstance(dependency, dict):
        raise BuildInputError("dependencyVerification policy is missing")
    rows = dependency.get("configurationCache")
    if not isinstance(rows, list) or not rows:
        raise BuildInputError("dependencyVerification.configurationCache must be a nonempty closed group")
    commands = [_row_argv(row, context=f"configurationCache[{index}]") for index, row in enumerate(rows)]
    if len(commands) != len({tuple(command) for command in commands}):
        raise BuildInputError("configuration-cache group contains duplicate commands")
    return commands


def _metadata_capture_commands(policy: Mapping[str, Any]) -> list[list[str]]:
    dependency = policy.get("dependencyVerification")
    if not isinstance(dependency, dict):
        raise BuildInputError("dependencyVerification policy is missing")
    rows = dependency.get("generationMatrix")
    if not isinstance(rows, list) or not rows:
        raise BuildInputError("dependencyVerification.generationMatrix must be nonempty")
    return [_row_argv(row, context=f"generationMatrix[{index}]") for index, row in enumerate(rows)]


def _prepare_session(policy: Mapping[str, Any], *, prefix: str) -> tuple[Path, InstalledJdks, dict[str, str]]:
    assert_supported_host()
    session = Path(tempfile.mkdtemp(prefix=prefix)).resolve()
    try:
        installed = install_verified_jdks(policy, output_root=session / "jdks")
        gradle_home = session / "gradle-home"
        environment = sanitized_environment(installed, gradle_home=gradle_home)
        evidence_arguments = sealed_gradle_arguments([], installed=installed)[1:]
        arguments_path = session / "governed-gradle-arguments.json"
        write_evidence_arguments(arguments_path, evidence_arguments)
        environment["GASSTATION_GRADLE_EVIDENCE_ARGS_FILE"] = str(arguments_path)
        environment["GASSTATION_BUILD_INPUT_EVIDENCE"] = "sealed-v1"
        return session, installed, environment
    except Exception:
        shutil.rmtree(session, ignore_errors=True)
        raise


def _run_closed_command(
    command: Sequence[str],
    *,
    installed: InstalledJdks,
    environment: Mapping[str, str],
    cwd: Path,
    metadata_write: bool = False,
) -> str:
    if not command:
        raise BuildInputError("closed command may not be empty")
    executable = Path(command[0]).name
    if executable == "gradlew":
        argv = sealed_gradle_arguments(command, installed=installed, metadata_write=metadata_write)
    else:
        argv = list(command)
    try:
        completed = subprocess.run(
            argv,
            cwd=cwd,
            env=dict(environment),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )
    except OSError as error:
        raise BuildInputError(f"governed command could not start: {Path(argv[0]).name}") from error
    if completed.returncode != 0:
        diagnostic = _safe_diagnostic(BuildInputError(completed.stdout or "<no-output>"))
        diagnostic = _SENSITIVE_ASSIGNMENT.sub(r"\1\2<redacted-secret>", diagnostic)
        diagnostic = _ABSOLUTE_DIAGNOSTIC_PATH.sub("<redacted-path>", diagnostic)
        encoded = diagnostic.encode("utf-8", "replace")
        if len(encoded) > _GOVERNED_OUTPUT_LIMIT:
            tail = encoded[-(_GOVERNED_OUTPUT_LIMIT - len(b"[truncated]\n")):]
            diagnostic = "[truncated]\n" + tail.decode("utf-8", "replace")
        raise BuildInputError(
            f"governed command failed: {Path(argv[0]).name}; output={diagnostic}",
        )
    return completed.stdout


def _run_group(
    policy: Mapping[str, Any],
    commands: Sequence[Sequence[str]],
    *,
    label: str,
    metadata_write: bool = False,
) -> None:
    session, installed, environment = _prepare_session(policy, prefix=f"gasstation-{label}-")
    try:
        for command in commands:
            source_commit = subprocess.run(
                ["git", "rev-parse", "HEAD"],
                cwd=ROOT,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                check=True,
            ).stdout.strip()
            materialized = [token.replace("{sourceCommit}", source_commit) for token in command]
            _run_closed_command(
                materialized,
                installed=installed,
                environment=environment,
                cwd=ROOT,
                metadata_write=metadata_write,
            )
        if label == "strict-complete":
            dependency = policy.get("dependencyVerification")
            representative = (
                dependency.get("offlineRepresentative")
                if isinstance(dependency, dict)
                else None
            )
            offline = _row_argv(representative, context="offlineRepresentative")
            if "--offline" in offline:
                raise BuildInputError("offlineRepresentative must not pre-embed --offline")
            _run_closed_command(
                [*offline, "--offline"],
                installed=installed,
                environment=environment,
                cwd=ROOT,
            )
            print("offline representative: PASS")
    finally:
        shutil.rmtree(session, ignore_errors=True)


def _copy_capture_source(destination: Path) -> None:
    if destination.exists() or destination.is_symlink():
        raise BuildInputError("metadata capture source root must be new")
    completed = subprocess.run(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        cwd=ROOT,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if completed.returncode != 0:
        raise BuildInputError("metadata capture could not inventory repository source")
    destination.mkdir(parents=True, mode=0o700)
    for raw_relative in completed.stdout.split(b"\0"):
        if not raw_relative:
            continue
        try:
            relative = Path(raw_relative.decode("utf-8"))
        except UnicodeDecodeError as error:
            raise BuildInputError("metadata capture source path is not UTF-8") from error
        if relative.is_absolute() or ".." in relative.parts or not relative.parts:
            raise BuildInputError("metadata capture source inventory contains an unsafe path")
        source = ROOT / relative
        if source.is_symlink() or not source.is_file():
            raise BuildInputError(f"metadata capture source must be a regular file: {relative.as_posix()}")
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)


def _metadata_artifact_inventory(path: Path) -> dict[tuple[str, str, str, str], tuple[str, ...]]:
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as error:
        raise BuildInputError("metadata capture XML is malformed") from error
    rows: dict[tuple[str, str, str, str], tuple[str, ...]] = {}
    for component in root.findall("./{*}components/{*}component"):
        identity = (
            component.get("group", ""),
            component.get("name", ""),
            component.get("version", ""),
        )
        if not all(identity):
            raise BuildInputError("metadata capture component identity is incomplete")
        for artifact in component.findall("./{*}artifact"):
            name = artifact.get("name", "")
            checksums = tuple(sorted(node.get("value", "") for node in artifact.findall("./{*}sha256")))
            key = (*identity, name)
            if not name or not checksums or any(re.fullmatch(r"[0-9a-f]{64}", value) is None for value in checksums):
                raise BuildInputError("metadata capture artifact SHA-256 is malformed")
            if key in rows:
                raise BuildInputError("metadata capture contains a duplicate artifact record")
            rows[key] = checksums
    return rows


def _apply_reviewed_metadata_superset(candidate: Path, destination: Path) -> tuple[int, int]:
    baseline = _metadata_artifact_inventory(destination)
    captured = _metadata_artifact_inventory(candidate)
    missing = sorted(set(baseline) - set(captured))
    changed = sorted(key for key in set(baseline) & set(captured) if baseline[key] != captured[key])
    if missing or changed:
        raise BuildInputError("metadata capture did not preserve the reviewed candidate records")
    additions = sorted(set(captured) - set(baseline))
    if any(len(captured[key]) != 1 for key in additions):
        raise BuildInputError("metadata capture introduced an alternate checksum")
    if candidate.read_bytes() != destination.read_bytes():
        shutil.copyfile(candidate, destination)
    component_additions = len({key[:3] for key in additions} - {key[:3] for key in baseline})
    return component_additions, len(additions)


def _testkit_failure_output_path(value: str) -> Path:
    if _TESTKIT_FAILURE_OUTPUT.fullmatch(value) is None:
        raise BuildInputError("TestKit failure output is outside the exact evidence location")
    return Path(value)


def _capture_metadata(policy: Mapping[str, Any], commands: Sequence[Sequence[str]]) -> None:
    session, installed, environment = _prepare_session(policy, prefix="gasstation-metadata-capture-")
    capture_source = session / "source"
    requested_failure_output = os.environ.get("GASSTATION_TESTKIT_FAILURE_OUTPUT")
    failure_output = (
        _testkit_failure_output_path(requested_failure_output)
        if requested_failure_output is not None
        else None
    )
    worker_trace = session / "testkit-worker-events.tsv"
    if failure_output is not None:
        environment["GASSTATION_TESTKIT_WORKER_TRACE"] = str(worker_trace)
    try:
        _copy_capture_source(capture_source)
        source_commit = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=ROOT,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            check=True,
        ).stdout.strip()
        for command in commands:
            materialized = [token.replace("{sourceCommit}", source_commit) for token in command]
            _run_closed_command(
                materialized,
                installed=installed,
                environment=environment,
                cwd=capture_source,
                metadata_write=True,
            )
        metadata_relative = policy["dependencyVerification"]["metadataPath"]
        component_count, artifact_count = _apply_reviewed_metadata_superset(
            capture_source / metadata_relative,
            ROOT / metadata_relative,
        )
        print(
            "metadata capture: PASS "
            f"new-components={component_count} new-artifacts={artifact_count}",
        )
    except Exception as error:
        if failure_output is not None:
            try:
                export_testkit_failure_evidence(
                    capture_source / "build-logic/convention/build/test-results/test",
                    worker_trace,
                    failure_output,
                )
            except Exception as export_error:
                raise BuildInputError(
                    "metadata capture failed and TestKit failure evidence could not be sealed; "
                    f"original={_safe_diagnostic(error)}; export={_safe_diagnostic(export_error)}",
                ) from export_error
        raise
    finally:
        shutil.rmtree(session, ignore_errors=True)


def _capture_receipt(
    policy_path: Path,
    policy: Mapping[str, Any],
    *,
    output: Path,
    source_commit: str | None,
    evidence_paths: Sequence[str],
) -> dict[str, Any]:
    head = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=True,
    ).stdout.strip()
    expected_source = source_commit or os.environ.get("GITHUB_SHA") or head
    if expected_source != head:
        raise BuildInputError("capture source/event SHA does not match current HEAD")
    status = subprocess.run(
        ["git", "status", "--porcelain=v1", "--untracked-files=all"],
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=True,
    ).stdout
    if status:
        raise BuildInputError("capture requires a clean tracked/untracked source tree")
    if os.environ.get("GITHUB_ACTIONS") == "true" and not os.environ.get("ImageVersion"):
        raise BuildInputError("hosted runner receipt requires actual ImageVersion")
    if os.environ.get("GITHUB_ACTIONS") == "true":
        if not os.environ.get("ImageOS"):
            raise BuildInputError("hosted runner receipt requires actual ImageOS")
        if os.environ.get("RUNNER_OS") != "Linux" or os.environ.get("RUNNER_ARCH") not in {"X64", "x64"}:
            raise BuildInputError("hosted runner OS/architecture differs from policy")
    metadata = _metadata_counts(policy)
    compile_home_value = os.environ.get("JAVA_HOME_17_X64")
    runtime_home_value = os.environ.get("JAVA_HOME_21_X64")
    gradle_home_value = os.environ.get("GRADLE_USER_HOME")
    if not compile_home_value or not runtime_home_value or not gradle_home_value:
        raise BuildInputError("capture requires installer-owned Java and fresh Gradle homes")
    validate_protected_environment(
        os.environ,
        compile_home=compile_home_value,
        runtime_home=runtime_home_value,
        gradle_home=gradle_home_value,
    )
    jdk_rows = inspect_installed_jdks(
        policy,
        compile_home=Path(compile_home_value),
        runtime_home=Path(runtime_home_value),
    )
    attempt = {
        "eventName": os.environ.get("GITHUB_EVENT_NAME"),
        "job": os.environ.get("GITHUB_JOB"),
        "runAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "runId": os.environ.get("GITHUB_RUN_ID"),
        "workflow": os.environ.get("GITHUB_WORKFLOW"),
    }
    runner = {
        "architecture": os.environ.get("RUNNER_ARCH") or platform.machine(),
        "imageOs": os.environ.get("ImageOS"),
        "imageVersion": os.environ.get("ImageVersion"),
        "kernelRelease": platform.release(),
        "os": os.environ.get("RUNNER_OS") or platform.system(),
        "osRelease": parse_os_release(),
    }
    receipt = {
        "android": {},
        "attempt": attempt,
        "dependencyVerification": metadata,
        "evidenceFiles": relative_evidence_rows(ROOT, [Path(value) for value in evidence_paths]),
        "gradle": {
            "sourceInputs": _source_input_hashes(),
            "version": policy["gradleWrapper"]["version"],
        },
        "jdks": list(jdk_rows),
        "policySha256": sha256_file(policy_path),
        "runner": runner,
        "schemaVersion": 1,
        "sourceCommit": head,
        "wrapper": {
            "distributionSha256": policy["gradleWrapper"]["distributionSha256"],
            "version": policy["gradleWrapper"]["version"],
            "wrapperJarSha256": policy["gradleWrapper"]["wrapperJarSha256"],
        },
    }
    android_sdk = _capture_android_sdk(policy)
    if android_sdk is None:
        raise BuildInputError("capture requires the policy-selected Android SDK")
    receipt["android"] = android_sdk
    field_allowlist = policy.get("evidence", {}).get("fieldAllowlist")
    if not isinstance(field_allowlist, list) or set(receipt) != set(field_allowlist):
        raise BuildInputError("capture receipt fields differ from the policy allowlist")
    write_canonical_receipt(output, receipt)
    return receipt


def _source_input_hashes() -> list[dict[str, Any]]:
    candidates = (
        "gradle/libs.versions.toml",
        "build-logic/gradle/libs.versions.toml",
        "settings.gradle.kts",
        "build-logic/settings.gradle.kts",
    )
    rows: list[dict[str, Any]] = []
    for relative in candidates:
        path = ROOT / relative
        if path.is_file() and not path.is_symlink():
            rows.append({"path": relative, "sha256": sha256_file(path), "size": path.stat().st_size})
    return rows


def _sdk_package_root(sdk_root: Path, coordinate: str) -> Path:
    parts = coordinate.split(";")
    if not parts or any(not part or part in {".", ".."} for part in parts):
        raise BuildInputError("Android SDK package coordinate is malformed")
    direct = sdk_root.joinpath(*parts)
    if (direct / "package.xml").is_file():
        return direct
    matches: list[Path] = []
    for package_xml in sdk_root.rglob("package.xml"):
        if package_xml.is_symlink():
            continue
        try:
            local_package = ET.parse(package_xml).getroot().find("localPackage")
        except (ET.ParseError, OSError):
            continue
        if local_package is not None and local_package.get("path") == coordinate:
            matches.append(package_xml.parent)
    if len(matches) != 1:
        raise BuildInputError(f"Android SDK package coordinate resolved {len(matches)} roots")
    return matches[0]


def _sdk_revision(local_package: ET.Element) -> str:
    revision = local_package.find("revision")
    if revision is None:
        raise BuildInputError("Android SDK package revision is missing")
    values: list[str] = []
    for name in ("major", "minor", "micro", "preview"):
        node = revision.find(name)
        if node is not None and node.text is not None:
            values.append(node.text.strip())
    if not values or any(not value.isdigit() for value in values):
        raise BuildInputError("Android SDK package revision is malformed")
    return ".".join(values)


def _tool_version(executable: Path, arguments: Sequence[str]) -> str:
    try:
        completed = subprocess.run(
            [str(executable), *arguments],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=30,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise BuildInputError(f"Android SDK tool did not run: {executable.name}") from error
    if completed.returncode != 0:
        raise BuildInputError(f"Android SDK tool failed: {executable.name}")
    lines = [line.strip() for line in completed.stdout.splitlines() if line.strip()]
    if not lines:
        raise BuildInputError(f"Android SDK tool version is empty: {executable.name}")
    return lines[0]


def _capture_android_sdk(policy: Mapping[str, Any]) -> dict[str, Any] | None:
    sdk_value = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk_value:
        return None
    sdk_root = Path(sdk_value).resolve(strict=True)
    android = policy.get("android")
    if not isinstance(android, dict):
        raise BuildInputError("android policy is missing")
    raw_packages = android.get("requiredPackages", android.get("packages"))
    if not isinstance(raw_packages, list) or not raw_packages:
        raise BuildInputError("android required package inventory is missing")
    coordinates: list[str] = []
    package_policy: dict[str, Mapping[str, Any]] = {}
    for row in raw_packages:
        if isinstance(row, str):
            coordinate = row
        elif isinstance(row, dict):
            coordinate = row.get("coordinate", row.get("path"))
        else:
            coordinate = None
        if not isinstance(coordinate, str):
            raise BuildInputError("Android SDK package record is malformed")
        coordinates.append(coordinate)
        package_policy[coordinate] = row if isinstance(row, dict) else {"coordinate": coordinate}
    if coordinates != sorted(set(coordinates)):
        raise BuildInputError("Android SDK package coordinates must be sorted and unique")
    packages: list[dict[str, Any]] = []
    image_files: list[dict[str, Any]] = []
    for coordinate in coordinates:
        package_root = _sdk_package_root(sdk_root, coordinate)
        package_xml = package_root / "package.xml"
        if package_root.is_symlink() or package_xml.is_symlink():
            raise BuildInputError("Android SDK package evidence may not use symlinks")
        resolved_xml = package_xml.resolve(strict=True)
        if not resolved_xml.is_relative_to(sdk_root) or not resolved_xml.is_file():
            raise BuildInputError("Android SDK package metadata escapes SDK root")
        try:
            local_package = ET.parse(resolved_xml).getroot().find("localPackage")
        except (ET.ParseError, OSError) as error:
            raise BuildInputError("Android SDK package.xml is malformed") from error
        if local_package is None or local_package.get("path") != coordinate:
            raise BuildInputError("Android SDK package.xml coordinate mismatch")
        display = local_package.findtext("display-name")
        if not display:
            raise BuildInputError("Android SDK package display name is missing")
        channel = local_package.find("channelRef")
        actual_revision = _sdk_revision(local_package)
        expected_revision = package_policy[coordinate].get("revision")
        fixed_revision_drift = (
            isinstance(expected_revision, str)
            and expected_revision != "NOT RUN"
            and actual_revision != expected_revision
        )
        if fixed_revision_drift:
            raise BuildInputError("Android SDK package revision differs from policy")
        package_row = {
            "channel": channel.get("ref") if channel is not None else None,
            "coordinate": coordinate,
            "displayName": display,
            "obsolete": local_package.get("obsolete") == "true",
            "packageXmlSha256": sha256_file(resolved_xml),
            "revision": actual_revision,
        }
        logical_identity = package_policy[coordinate].get("logicalIdentity")
        if isinstance(logical_identity, str):
            package_row["logicalIdentity"] = logical_identity
        packages.append(package_row)
        if coordinate.startswith("system-images;"):
            for filename in ("build.prop", "kernel-qemu", "kernel-ranchu", "kernel-ranchu-64"):
                candidate = package_root / filename
                if candidate.is_file() and not candidate.is_symlink():
                    image_files.append(
                        {
                            "coordinate": coordinate,
                            "file": filename,
                            "sha256": sha256_file(candidate),
                            "size": candidate.stat().st_size,
                        },
                    )
    build_tools = android.get("buildTools")
    if not isinstance(build_tools, str):
        raise BuildInputError("android.buildTools is missing")
    tool_specs: tuple[tuple[str, Path, list[str] | None], ...] = (
        ("aapt2", sdk_root / "build-tools" / build_tools / "aapt2", ["version"]),
        ("adb", sdk_root / "platform-tools" / "adb", ["version"]),
        ("emulator", sdk_root / "emulator" / "emulator", ["-version"]),
        ("zipalign", sdk_root / "build-tools" / build_tools / "zipalign", None),
    )
    tools: list[dict[str, Any]] = []
    for name, executable, version_args in tool_specs:
        resolved = executable.resolve(strict=True)
        if not resolved.is_relative_to(sdk_root) or not resolved.is_file():
            raise BuildInputError(f"Android SDK tool escapes SDK root: {name}")
        tools.append(
            {
                "name": name,
                "sha256": sha256_file(resolved),
                "size": resolved.stat().st_size,
                "version": _tool_version(resolved, version_args) if version_args is not None else None,
            },
        )
    return {
        "imageFiles": sorted(image_files, key=lambda row: (row["coordinate"], row["file"])),
        "packages": packages,
        "tools": tools,
    }


def _download_codecov(policy: Mapping[str, Any], output: Path) -> None:
    record = policy.get("codecovCli")
    if not isinstance(record, dict):
        raise BuildInputError("codecovCli policy is missing")
    url = record.get("binaryUrl", record.get("url"))
    size = record.get("binarySize", record.get("size"))
    digest = record.get("binarySha256", record.get("sha256"))
    if not isinstance(url, str) or type(size) is not int or not isinstance(digest, str):
        raise BuildInputError("codecovCli payload identity is incomplete")
    host = urlsplit(url).hostname
    if host is None:
        raise BuildInputError("Codecov CLI URL has no host")
    allowed_hosts = {host, "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"}
    extra_hosts = record.get("allowedRedirectHosts")
    if isinstance(extra_hosts, list):
        allowed_hosts.update(value for value in extra_hosts if isinstance(value, str))
    download_verified(
        url,
        destination=output,
        expected_size=size,
        expected_sha256=digest,
        allowed_hosts=allowed_hosts,
    )
    output.chmod(0o755)


def _default_output(policy: Mapping[str, Any], key: str, fallback: str) -> Path:
    evidence = policy.get("evidence")
    value = evidence.get(key) if isinstance(evidence, dict) else None
    path = Path(value if isinstance(value, str) else fallback)
    if path.is_absolute() or ".." in path.parts:
        raise BuildInputError(f"evidence.{key} must be repository-relative")
    return _evidence_output(policy, path)


def _evidence_output(policy: Mapping[str, Any], value: Path) -> Path:
    evidence = policy.get("evidence")
    configured_root = (
        evidence.get("root", "build/reports/build-inputs")
        if isinstance(evidence, dict)
        else "build/reports/build-inputs"
    )
    if not isinstance(configured_root, str):
        raise BuildInputError("evidence.root must be repository-relative")
    allowed = Path(configured_root)
    if allowed.is_absolute() or ".." in allowed.parts:
        raise BuildInputError("evidence.root must be repository-relative")
    candidate = value if value.is_absolute() else ROOT / value
    resolved_allowed = (ROOT / allowed).resolve()
    if not resolved_allowed.is_relative_to(ROOT.resolve()):
        raise BuildInputError("evidence.root escapes the repository")
    resolved_candidate = candidate.resolve(strict=False)
    if not resolved_candidate.is_relative_to(resolved_allowed) or resolved_candidate == resolved_allowed:
        raise BuildInputError("receipt output must be a file under the closed evidence root")
    return resolved_candidate


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Verify GasStation build-input provenance")
    subparsers = parser.add_subparsers(dest="command", required=True)

    verify = subparsers.add_parser("verify")
    verify.add_argument("--policy", required=True)

    install = subparsers.add_parser("install-jdks")
    install.add_argument("--policy", required=True)
    install.add_argument("--output-root", required=True)

    codecov = subparsers.add_parser("download-codecov")
    codecov.add_argument("--policy", required=True)
    codecov.add_argument("--output", required=True)

    evidence = subparsers.add_parser("evidence-session")
    evidence.add_argument("--policy", required=True)
    evidence.add_argument("argv", nargs=argparse.REMAINDER)

    strict = subparsers.add_parser("strict-matrix")
    strict.add_argument("--policy", required=True)
    strict.add_argument("--group", required=True, choices=sorted(_STRICT_GROUPS))

    configuration = subparsers.add_parser("configuration-cache")
    configuration.add_argument("--policy", required=True)

    metadata = subparsers.add_parser("metadata-capture")
    metadata.add_argument("--policy", required=True)

    capture = subparsers.add_parser("capture")
    capture.add_argument("--policy", required=True)
    capture.add_argument("--output")
    capture.add_argument("--source-commit")
    capture.add_argument("--evidence", action="append", default=[])

    reproduce = subparsers.add_parser("reproduce")
    reproduce.add_argument("--policy", required=True)
    reproduce.add_argument("--source-commit", required=True)
    reproduce.add_argument("--output", required=True)

    release = subparsers.add_parser("release-bind")
    release.add_argument("--policy", required=True)
    release.add_argument("--receipt", required=True)
    release.add_argument("--apk", required=True)
    release.add_argument("--source-commit", required=True)
    release.add_argument("--artifact-name", required=True)
    release.add_argument("--output")
    return parser


def _safe_diagnostic(error: BaseException) -> str:
    message = str(error).replace(str(ROOT), "<repository>")
    message = _SENSITIVE_DIAGNOSTIC.sub("<redacted-secret>", message)
    for name, value in os.environ.items():
        protected_fragments = ("TOKEN", "SECRET", "PASSWORD", "CREDENTIAL", "COOKIE")
        if value and any(fragment in name.upper() for fragment in protected_fragments):
            message = message.replace(value, "<redacted-secret>")
    message = re.sub(r"(?:/Users/|/home/|/tmp/|/private/var/)[^\s'\"]+", "<redacted-path>", message)
    return message


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _parser().parse_args(argv)
    try:
        policy_path, policy = _load(arguments.policy)
        if arguments.command == "verify":
            verify_repository(policy)
        elif arguments.command == "install-jdks":
            assert_supported_host()
            installed = install_verified_jdks(policy, output_root=Path(arguments.output_root))
            export_github_java_environment(installed)
        elif arguments.command == "download-codecov":
            _download_codecov(policy, Path(arguments.output))
        elif arguments.command == "evidence-session":
            command = list(arguments.argv)
            if command and command[0] == "--":
                command = command[1:]
            exact = exact_evidence_command(policy, command)
            session, installed, environment = _prepare_session(policy, prefix="gasstation-evidence-session-")
            environment["GASSTATION_BUILD_INPUT_POLICY"] = str(policy_path)
            try:
                _run_closed_command(exact, installed=installed, environment=environment, cwd=ROOT)
            finally:
                shutil.rmtree(session, ignore_errors=True)
        elif arguments.command == "strict-matrix":
            _run_group(policy, closed_group_commands(policy, arguments.group), label=f"strict-{arguments.group}")
        elif arguments.command == "configuration-cache":
            commands = _configuration_cache_commands(policy)
            session, installed, environment = _prepare_session(policy, prefix="gasstation-configuration-cache-")
            try:
                for command in commands:
                    first = list(command)
                    if "--configuration-cache" not in first:
                        first.append("--configuration-cache")
                    if "--configuration-cache-problems=fail" not in first:
                        first.append("--configuration-cache-problems=fail")
                    _run_closed_command(first, installed=installed, environment=environment, cwd=ROOT)
                    second_output = _run_closed_command(first, installed=installed, environment=environment, cwd=ROOT)
                    if "Reusing configuration cache." not in second_output:
                        raise BuildInputError("second configuration-cache run did not report reuse")
            finally:
                shutil.rmtree(session, ignore_errors=True)
        elif arguments.command == "metadata-capture":
            commands = [
                command + ["--write-verification-metadata", "sha256"]
                for command in _metadata_capture_commands(policy)
            ]
            _capture_metadata(policy, commands)
        elif arguments.command == "capture":
            output = (
                _evidence_output(policy, Path(arguments.output))
                if arguments.output
                else _default_output(policy, "receiptPath", "build/reports/build-inputs/build-input-receipt.json")
            )
            _capture_receipt(
                policy_path,
                policy,
                output=output,
                source_commit=arguments.source_commit,
                evidence_paths=arguments.evidence,
            )
        elif arguments.command == "reproduce":
            reproduction_output = _evidence_output(policy, Path(arguments.output))
            session, installed, _ = _prepare_session(policy, prefix="gasstation-reproduce-jdks-")
            probe_parent = Path(tempfile.mkdtemp(prefix="gasstation-probe-parent-"))
            work_root = probe_parent / "build-input-probe-work"
            try:
                run_reproducibility_probe(
                    ROOT,
                    policy,
                    policy_path=policy_path,
                    source_commit=arguments.source_commit,
                    output=reproduction_output,
                    installed=installed,
                    work_root=work_root,
                )
            finally:
                shutil.rmtree(session, ignore_errors=True)
                shutil.rmtree(probe_parent, ignore_errors=True)
        elif arguments.command == "release-bind":
            result = verify_release_binding(
                ROOT,
                policy,
                policy_path=policy_path,
                receipt_path=Path(arguments.receipt),
                apk_path=Path(arguments.apk),
                source_commit=arguments.source_commit,
                artifact_name=arguments.artifact_name,
            )
            if arguments.output:
                write_canonical_receipt(_evidence_output(policy, Path(arguments.output)), result)
        else:
            raise BuildInputError("unsupported build-input command")
    except (ArchiveError, BuildInputError, DownloadError, OSError, subprocess.SubprocessError) as error:
        # Error messages are deliberately contract-level and never echo environment or subprocess output.
        print(f"build-input verification failed: {_safe_diagnostic(error)}", file=sys.stderr)
        return 2
    print(f"build-input {arguments.command}: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
