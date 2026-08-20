from __future__ import annotations

import hashlib
import io
import os
import shutil
import subprocess
import tarfile
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any, Mapping, Sequence

from .contracts import BuildInputError, sha256_file
from .receipts import load_canonical_receipt, write_canonical_receipt
from .runtime import InstalledJdks, sanitized_environment, sealed_gradle_arguments


_FORBIDDEN_COPY_PARTS = {".git", ".gradle", "build"}
_FORBIDDEN_COPY_NAMES = {"local.properties"}
_SIGNATURE_SUFFIXES = (".RSA", ".DSA", ".EC", ".SF")


def _git(root: Path, *arguments: str, text: bool = True) -> str | bytes:
    try:
        completed = subprocess.run(
            ["git", *arguments],
            cwd=root,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=text,
            check=False,
        )
    except OSError as error:
        raise BuildInputError("git is unavailable for reproducibility proof") from error
    if completed.returncode != 0:
        raise BuildInputError(f"git command failed: {' '.join(arguments[:2])}")
    return completed.stdout


def require_clean_source(root: Path, source_commit: str) -> int:
    if len(source_commit) != 40 or any(character not in "0123456789abcdef" for character in source_commit):
        raise BuildInputError("source commit must be a lowercase full Git SHA")
    actual = str(_git(root, "rev-parse", "HEAD")).strip()
    if actual != source_commit:
        raise BuildInputError("source commit does not match current HEAD")
    status_output = str(
        _git(root, "status", "--porcelain=v1", "--untracked-files=all"),
    )
    if status_output:
        raise BuildInputError("reproducibility proof requires a clean tracked/untracked tree")
    timestamp = str(_git(root, "show", "-s", "--format=%ct", source_commit)).strip()
    if not timestamp.isdigit():
        raise BuildInputError("source commit timestamp is invalid")
    return int(timestamp)


def export_committed_tree(root: Path, source_commit: str, destination: Path) -> None:
    if destination.exists() or destination.is_symlink():
        raise BuildInputError("source-copy destination must be new")
    archive_bytes = _git(root, "archive", "--format=tar", source_commit, text=False)
    if not isinstance(archive_bytes, bytes):
        raise BuildInputError("git archive returned unexpected text")
    destination.mkdir(parents=True, mode=0o700)
    seen: set[PurePosixPath] = set()
    try:
        with tarfile.open(fileobj=io.BytesIO(archive_bytes), mode="r:") as archive:
            for member in archive.getmembers():
                relative = PurePosixPath(member.name)
                if (
                    relative.is_absolute()
                    or not relative.parts
                    or any(part in {"", ".", ".."} for part in relative.parts)
                    or any(part in _FORBIDDEN_COPY_PARTS for part in relative.parts)
                    or relative.name in _FORBIDDEN_COPY_NAMES
                    or relative.suffix.lower() in {".jks", ".keystore", ".p12", ".pfx"}
                ):
                    raise BuildInputError(f"unsafe or contaminated source archive entry: {relative.name}")
                if relative in seen:
                    raise BuildInputError(f"duplicate source archive entry: {relative.as_posix()}")
                seen.add(relative)
                target = destination.joinpath(*relative.parts)
                if member.isdir():
                    target.mkdir(parents=True, exist_ok=True)
                    continue
                if not member.isfile():
                    raise BuildInputError(f"non-regular source archive entry: {relative.as_posix()}")
                target.parent.mkdir(parents=True, exist_ok=True)
                source = archive.extractfile(member)
                if source is None:
                    raise BuildInputError(f"source archive entry has no bytes: {relative.as_posix()}")
                with target.open("xb") as output:
                    shutil.copyfileobj(source, output)
                target.chmod(member.mode & 0o777)
    except Exception:
        shutil.rmtree(destination, ignore_errors=True)
        raise


def _find_single_apk(source: Path, output_glob: str) -> Path:
    pattern = Path(output_glob)
    if pattern.is_absolute() or ".." in pattern.parts:
        raise BuildInputError("APK output glob must be repository-relative")
    matches = sorted(path for path in source.glob(output_glob) if path.is_file() and not path.is_symlink())
    if len(matches) != 1:
        raise BuildInputError(f"expected exactly one unsigned APK, found {len(matches)}")
    return matches[0]


def _apksigner(policy: Mapping[str, Any]) -> Path:
    sdk_root = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk_root:
        raise BuildInputError("ANDROID_SDK_ROOT is required to verify APK signer absence")
    build_tools = policy.get("android", {}).get("buildTools")
    if not isinstance(build_tools, str):
        raise BuildInputError("android.buildTools is missing from policy")
    executable = (Path(sdk_root) / "build-tools" / build_tools / "apksigner").resolve()
    if not executable.is_file() or not os.access(executable, os.X_OK):
        raise BuildInputError("policy-selected apksigner is unavailable")
    return executable


def assert_unsigned_apk(path: Path, policy: Mapping[str, Any]) -> None:
    try:
        with zipfile.ZipFile(path) as apk:
            for name in apk.namelist():
                upper = name.upper()
                if upper.startswith("META-INF/") and upper.endswith(_SIGNATURE_SUFFIXES):
                    raise BuildInputError("APK contains a JAR signature entry")
    except (OSError, zipfile.BadZipFile) as error:
        raise BuildInputError("APK is not a readable ZIP archive") from error
    completed = subprocess.run(
        [str(_apksigner(policy)), "verify", "--verbose", str(path)],
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=60,
        check=False,
    )
    if completed.returncode == 0:
        raise BuildInputError("APK is signed; unsigned artifact required")
    recognized_unsigned = ("DOES NOT VERIFY", "No JAR signatures", "Missing META-INF/MANIFEST.MF")
    if not any(marker in completed.stdout for marker in recognized_unsigned):
        raise BuildInputError("apksigner could not prove signer absence")


def safe_zip_comparison(first: Path, second: Path) -> list[dict[str, Any]]:
    def inventory(path: Path) -> dict[str, dict[str, Any]]:
        rows: dict[str, dict[str, Any]] = {}
        try:
            archive = zipfile.ZipFile(path)
        except (OSError, zipfile.BadZipFile) as error:
            raise BuildInputError("APK ZIP comparison failed") from error
        with archive:
            for info in archive.infolist():
                name = PurePosixPath(info.filename)
                if name.is_absolute() or ".." in name.parts or info.filename in rows:
                    raise BuildInputError("APK contains an unsafe or duplicate ZIP entry")
                data = archive.read(info)
                rows[info.filename] = {
                    "crc32": f"{info.CRC:08x}",
                    "sha256": hashlib.sha256(data).hexdigest(),
                    "size": info.file_size,
                }
        return rows

    left = inventory(first)
    right = inventory(second)
    differences: list[dict[str, Any]] = []
    for name in sorted(set(left) | set(right)):
        if left.get(name) != right.get(name):
            differences.append({"buildA": left.get(name), "buildB": right.get(name), "entry": name})
    return differences


def reproducibility_receipt(
    *,
    source_sha: str,
    policy_sha256: str,
    task: str,
    output_identity: str,
    builds: Sequence[Mapping[str, Any]],
    status: str,
) -> dict[str, Any]:
    if len(source_sha) != 40 or any(character not in "0123456789abcdef" for character in source_sha):
        raise BuildInputError("reproducibility source SHA must be lowercase full Git SHA")
    if len(policy_sha256) != 64 or any(character not in "0123456789abcdef" for character in policy_sha256):
        raise BuildInputError("reproducibility policy hash must be lowercase SHA-256")
    if task != ":app:assembleProdRelease":
        raise BuildInputError("reproducibility task must be the closed unsigned prod release task")
    output_path = Path(output_identity)
    if output_path.is_absolute() or ".." in output_path.parts or not output_identity.endswith(".apk"):
        raise BuildInputError("reproducibility output identity must be a relative APK glob")
    if len(builds) != 2:
        raise BuildInputError("reproducibility receipt requires exactly two builds")
    normalized: list[dict[str, Any]] = []
    for row in builds:
        if set(row) != {"id", "sha256", "size"}:
            raise BuildInputError("reproducibility build row schema mismatch")
        digest = row["sha256"]
        size = row["size"]
        if (
            not isinstance(digest, str)
            or len(digest) != 64
            or any(character not in "0123456789abcdef" for character in digest)
            or type(size) is not int
            or size <= 0
        ):
            raise BuildInputError("reproducibility build byte identity is malformed")
        normalized.append({"id": row["id"], "sha256": digest, "size": size})
    if [row["id"] for row in normalized] != ["build-a", "build-b"]:
        raise BuildInputError("reproducibility build IDs must be the closed opaque pair")
    equal = normalized[0]["sha256"] == normalized[1]["sha256"] and normalized[0]["size"] == normalized[1]["size"]
    if (status == "PASS") != equal or status not in {"PASS", "FAIL"}:
        raise BuildInputError("reproducibility status does not match exact byte equality")
    return {
        "builds": normalized,
        "comparison": "exact-byte-equality",
        "outputIdentity": output_identity,
        "policySha256": policy_sha256,
        "proofScope": "same-host-workspace-independent-unsigned-apk",
        "schemaVersion": 1,
        "signerAbsent": True,
        "sourceSha": source_sha,
        "status": status,
        "task": task,
        "unsigned": True,
    }


def _run_build(
    source: Path,
    *,
    installed: InstalledJdks,
    gradle_home: Path,
    project_cache: Path,
    task: str,
    source_date_epoch: int,
) -> None:
    gradle_home.mkdir(mode=0o700)
    project_cache.mkdir(mode=0o700)
    environment = sanitized_environment(
        installed,
        gradle_home=gradle_home,
        source_date_epoch=str(source_date_epoch),
    )
    arguments = sealed_gradle_arguments(
        [
            task,
            "--no-build-cache",
            "--no-configuration-cache",
            "--rerun-tasks",
            "--project-cache-dir",
            str(project_cache),
        ],
        installed=installed,
    )
    completed = subprocess.run(
        arguments,
        cwd=source,
        env=environment,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if completed.returncode != 0:
        raise BuildInputError("isolated unsigned release build failed")


def run_reproducibility_probe(
    root: Path,
    policy: Mapping[str, Any],
    *,
    policy_path: Path,
    source_commit: str,
    output: Path,
    installed: InstalledJdks,
    work_root: Path,
) -> dict[str, Any]:
    timestamp = require_clean_source(root, source_commit)
    artifact = policy.get("reproducibleArtifact")
    if not isinstance(artifact, dict):
        raise BuildInputError("reproducibleArtifact policy is missing")
    task = artifact.get("task")
    output_glob = artifact.get("outputGlob")
    output_identity = artifact.get("outputIdentity")
    if (
        not isinstance(task, str)
        or not task.startswith(":")
        or not isinstance(output_glob, str)
        or not isinstance(output_identity, str)
    ):
        raise BuildInputError("reproducibleArtifact task/outputGlob is invalid")
    if artifact.get("unsigned") is not True:
        raise BuildInputError("reproducibility target must be unsigned")
    if work_root.exists() or work_root.is_symlink():
        raise BuildInputError("reproducibility work root must be new")
    work_root.mkdir(parents=True, mode=0o700)
    copies = [work_root / "source-a", work_root / "source-b"]
    homes = [work_root / "gradle-home-a", work_root / "gradle-home-b"]
    caches = [work_root / "project-cache-a", work_root / "project-cache-b"]
    try:
        rows: list[dict[str, Any]] = []
        apks: list[Path] = []
        for index in range(2):
            export_committed_tree(root, source_commit, copies[index])
            _run_build(
                copies[index],
                installed=installed,
                gradle_home=homes[index],
                project_cache=caches[index],
                task=task,
                source_date_epoch=timestamp,
            )
            apk = _find_single_apk(copies[index], output_glob)
            assert_unsigned_apk(apk, policy)
            apks.append(apk)
            rows.append(
                {
                    "id": "build-a" if index == 0 else "build-b",
                    "sha256": sha256_file(apk),
                    "size": apk.stat().st_size,
                },
            )
        status = "PASS" if rows[0]["sha256"] == rows[1]["sha256"] and rows[0]["size"] == rows[1]["size"] else "FAIL"
        receipt = reproducibility_receipt(
            source_sha=source_commit,
            policy_sha256=sha256_file(policy_path),
            task=task,
            output_identity=output_identity,
            builds=rows,
            status=status,
        )
        write_canonical_receipt(output, receipt)
        receipt_relative = artifact.get("receiptPath")
        if not isinstance(receipt_relative, str):
            raise BuildInputError("reproducibility receipt path is missing from policy")
        receipt_path = root / receipt_relative
        if (
            Path(receipt_relative).is_absolute()
            or ".." in Path(receipt_relative).parts
            or not receipt_path.resolve(strict=False).is_relative_to(root.resolve())
        ):
            raise BuildInputError("reproducibility receipt path escapes the repository")
        if receipt_path.resolve(strict=False) != output.resolve(strict=False):
            write_canonical_receipt(receipt_path, receipt)
        if status != "PASS":
            write_canonical_receipt(
                output.with_name("reproducibility-zip-diff.json"),
                {
                    "differences": safe_zip_comparison(apks[0], apks[1]),
                    "schemaVersion": 1,
                    "status": "FAIL",
                },
            )
            raise BuildInputError("unsigned APKs differ by exact bytes")
        return receipt
    finally:
        canonical = work_root.resolve()
        if canonical.parent == work_root.parent.resolve() and canonical.name.startswith("build-input-probe-"):
            shutil.rmtree(canonical, ignore_errors=True)


def verify_release_binding(
    root: Path,
    policy: Mapping[str, Any],
    *,
    policy_path: Path,
    receipt_path: Path,
    apk_path: Path,
    source_commit: str,
    artifact_name: str,
) -> dict[str, Any]:
    if len(source_commit) != 40 or any(character not in "0123456789abcdef" for character in source_commit):
        raise BuildInputError("release source commit must be a lowercase full Git SHA")
    actual_source = str(_git(root, "rev-parse", "HEAD")).strip()
    if actual_source != source_commit:
        raise BuildInputError("release source commit does not match current HEAD")
    artifact_policy = policy.get("reproducibleArtifact")
    if not isinstance(artifact_policy, dict):
        raise BuildInputError("reproducibleArtifact policy is missing")
    if artifact_policy.get("unsigned") is not True:
        raise BuildInputError("release binding requires the policy unsigned artifact")
    artifact_template = artifact_policy.get(
        "artifactName",
        "reproducible-prod-release-receipt-{sourceSha}",
    )
    expected_artifact_name = (
        artifact_template.replace("{sourceSha}", source_commit)
        if isinstance(artifact_template, str)
        else None
    )
    if artifact_name != expected_artifact_name:
        raise BuildInputError("source-bound reproducibility artifact name mismatch")
    receipt = load_canonical_receipt(receipt_path)
    expected_keys = {
        "builds",
        "comparison",
        "outputIdentity",
        "policySha256",
        "proofScope",
        "schemaVersion",
        "signerAbsent",
        "sourceSha",
        "status",
        "task",
        "unsigned",
    }
    if set(receipt) != expected_keys:
        raise BuildInputError("reproducibility receipt schema mismatch")
    if receipt.get("sourceSha") != source_commit or receipt.get("policySha256") != sha256_file(policy_path):
        raise BuildInputError("reproducibility receipt source/policy mismatch")
    if (
        receipt.get("schemaVersion") != 1
        or receipt.get("comparison") != "exact-byte-equality"
        or receipt.get("proofScope") != "same-host-workspace-independent-unsigned-apk"
    ):
        raise BuildInputError("reproducibility receipt proof scope mismatch")
    if (
        receipt.get("status") != "PASS"
        or receipt.get("signerAbsent") is not True
        or receipt.get("unsigned") is not True
    ):
        raise BuildInputError("reproducibility receipt did not prove unsigned equality")
    if (
        receipt.get("task") != artifact_policy.get("task")
        or receipt.get("outputIdentity") != artifact_policy.get("outputIdentity")
    ):
        raise BuildInputError("reproducibility receipt artifact identity mismatch")
    builds = receipt.get("builds")
    if not isinstance(builds, list) or len(builds) != 2:
        raise BuildInputError("reproducibility receipt build rows are malformed")
    expected_row_keys = {"id", "sha256", "size"}
    if any(not isinstance(row, dict) or set(row) != expected_row_keys for row in builds):
        raise BuildInputError("reproducibility receipt build row schema mismatch")
    if [row["id"] for row in builds] != ["build-a", "build-b"]:
        raise BuildInputError("reproducibility receipt build IDs are malformed")
    expected_sha = builds[0].get("sha256")
    expected_size = builds[0].get("size")
    if (
        not isinstance(expected_sha, str)
        or len(expected_sha) != 64
        or any(character not in "0123456789abcdef" for character in expected_sha)
        or type(expected_size) is not int
        or expected_size <= 0
    ):
        raise BuildInputError("reproducibility receipt APK byte identity is malformed")
    if any(row.get("sha256") != expected_sha or row.get("size") != expected_size for row in builds):
        raise BuildInputError("reproducibility receipt build rows differ")
    if apk_path.is_symlink() or not apk_path.is_file():
        raise BuildInputError("release APK must be one regular file")
    if sha256_file(apk_path) != expected_sha or apk_path.stat().st_size != expected_size:
        raise BuildInputError("release APK does not match reproducibility receipt")
    assert_unsigned_apk(apk_path, policy)
    return {
        "artifactName": artifact_name,
        "policySha256": sha256_file(policy_path),
        "schemaVersion": 1,
        "sourceSha": source_commit,
        "status": "PASS",
    }
