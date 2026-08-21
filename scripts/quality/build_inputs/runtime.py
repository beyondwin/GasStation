from __future__ import annotations

import os
import platform
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence
from urllib.parse import urlsplit
from urllib.parse import unquote

from .archive import safe_extract_tar
from .contracts import (
    BuildInputError,
    canonical_json_bytes,
    sha256_file,
    validate_gradle_arguments,
    validate_protected_environment,
)
from .downloader import download_verified_github_release_asset
from .receipts import write_canonical_receipt


@dataclass(frozen=True)
class InstalledJdks:
    compile_home: Path
    runtime_home: Path
    output_root: Path
    roles: tuple[dict[str, Any], ...]


def _record_string(record: Mapping[str, Any], *names: str) -> str:
    for name in names:
        value = record.get(name)
        if isinstance(value, str) and value:
            return value
    raise BuildInputError(f"JDK policy is missing {names[0]}")


def _parse_release(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise BuildInputError("extracted JDK release file is unreadable") from error
    for line in lines:
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key] = value.strip().strip('"')
    return values


def _run_identity(executable: Path, *arguments: str) -> str:
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
            env={"LANG": "C", "LC_ALL": "C", "PATH": "/usr/bin:/bin"},
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise BuildInputError(f"verified JDK executable did not run: {executable.name}") from error
    if completed.returncode != 0:
        raise BuildInputError(f"verified JDK executable failed: {executable.name}")
    return completed.stdout


def _validate_extracted_jdk(
    home: Path,
    record: Mapping[str, Any],
    *,
    role: str,
) -> dict[str, Any]:
    canonical_home = home.resolve(strict=True)
    java = (home / "bin/java").resolve(strict=True)
    javac = (home / "bin/javac").resolve(strict=True)
    for executable in (java, javac):
        if not executable.is_file() or not executable.is_relative_to(canonical_home):
            raise BuildInputError(f"{role} JDK executable escapes verified root")
        if not os.access(executable, os.X_OK):
            raise BuildInputError(f"{role} JDK executable is not executable")

    release = _parse_release(home / "release")
    expected_version = _record_string(record, "version")
    raw_major = record.get("major", expected_version.split(".", 1)[0])
    expected_major = str(raw_major)
    if role == "compile" and expected_major != "17":
        raise BuildInputError("compile JDK role must be Java 17")
    if role == "runtime" and expected_major != "21":
        raise BuildInputError("runtime JDK role must be Java 21")
    if record.get("role", role) != role:
        raise BuildInputError(f"{role} JDK role is cross-wired")
    if str(record.get("packageType", "jdk")).lower() != "jdk":
        raise BuildInputError(f"{role} Java package must be a JDK")
    if str(record.get("jvm", record.get("jvmImpl", "hotspot"))).lower() != "hotspot":
        raise BuildInputError(f"{role} JDK policy VM must be HotSpot")
    release_version = release.get("JAVA_VERSION", "")
    full_version = release.get("FULL_VERSION", "")
    expected_base = expected_version.split("+", 1)[0]
    exact_full_version = full_version == expected_version or full_version.startswith(expected_version + "-")
    if release_version != expected_base or not exact_full_version:
        raise BuildInputError(f"{role} JDK release file exact version drift")
    if release.get("IMPLEMENTOR") != "Eclipse Adoptium":
        raise BuildInputError(f"{role} JDK release vendor is not Eclipse Adoptium/Temurin")
    if release.get("JVM_VARIANT", "").lower() != "hotspot":
        raise BuildInputError(f"{role} JDK release VM is not HotSpot")
    java_output = _run_identity(java, "-XshowSettings:properties", "-version")
    javac_output = _run_identity(javac, "-version")
    if expected_version not in java_output or expected_base not in javac_output:
        raise BuildInputError(f"{role} JDK runtime/compiler version mismatch")
    joined_identity = " ".join(release.values()) + " " + java_output
    if "Temurin" not in joined_identity and "Eclipse Adoptium" not in joined_identity:
        raise BuildInputError(f"{role} JDK vendor is not Eclipse Temurin")
    if "HotSpot" not in joined_identity:
        raise BuildInputError(f"{role} JDK VM is not HotSpot")
    if release.get("OS_NAME", "").lower() != "linux":
        raise BuildInputError(f"{role} JDK OS is not Linux")
    if release.get("OS_ARCH", "").lower() not in {"x86_64", "amd64"}:
        raise BuildInputError(f"{role} JDK architecture is not x64")
    return {
        "archiveSha256": _record_string(record, "archiveSha256"),
        "architecture": "x64",
        "id": f"{role}-{_record_string(record, 'archiveSha256')[:16]}",
        "major": int(expected_major),
        "os": "Linux",
        "packageType": "jdk",
        "role": role,
        "vendor": "Eclipse Temurin",
        "version": expected_version,
        "vm": "HotSpot",
    }


def install_verified_jdks(
    policy: Mapping[str, Any],
    *,
    output_root: Path,
) -> InstalledJdks:
    if output_root.exists() or output_root.is_symlink():
        raise BuildInputError("JDK output root must be a new path")
    output_root.parent.mkdir(parents=True, exist_ok=True)
    output_root.mkdir(mode=0o700)
    homes: dict[str, Path] = {}
    role_receipts: list[dict[str, Any]] = []
    try:
        for role in ("compile", "runtime"):
            record = policy["jdks"].get(role)
            if not isinstance(record, dict):
                raise BuildInputError(f"JDK policy role is missing: {role}")
            url = _record_string(record, "archiveUrl")
            filename = _record_string(record, "filename", "archiveFilename")
            if Path(filename).name != filename:
                raise BuildInputError(f"{role} JDK filename must be a basename")
            if unquote(Path(urlsplit(url).path).name) != filename:
                raise BuildInputError(f"{role} JDK URL/filename mismatch")
            archive = output_root / f"download-{role}-{filename}"
            download = download_verified_github_release_asset(
                url,
                destination=archive,
                expected_size=record["archiveSize"],
                expected_sha256=_record_string(record, "archiveSha256"),
                redirect_contract=record["releaseAssetRedirect"],
            )
            if sha256_file(archive) != _record_string(record, "archiveSha256"):
                raise BuildInputError(f"{role} JDK digest changed before extraction")
            digest = _record_string(record, "archiveSha256")
            home = output_root / f"{role}-{digest}"
            safe_extract_tar(
                archive.read_bytes(),
                destination=home,
                archive_root=_record_string(record, "archiveRoot"),
            )
            archive.unlink()
            role_receipt = _validate_extracted_jdk(home, record, role=role)
            role_receipt["download"] = download.receipt
            role_receipts.append(role_receipt)
            homes[role] = home.resolve(strict=True)
        receipt = {
            "roles": sorted(role_receipts, key=lambda row: row["role"]),
            "schemaVersion": 1,
            "status": "PASS",
        }
        write_canonical_receipt(output_root / "installation.json", receipt)
        return InstalledJdks(
            compile_home=homes["compile"],
            runtime_home=homes["runtime"],
            output_root=output_root.resolve(strict=True),
            roles=tuple(receipt["roles"]),
        )
    except Exception:
        shutil.rmtree(output_root, ignore_errors=True)
        raise


def export_github_java_environment(installed: InstalledJdks) -> None:
    github_env = os.environ.get("GITHUB_ENV")
    github_path = os.environ.get("GITHUB_PATH")
    if bool(github_env) != bool(github_path):
        raise BuildInputError("GITHUB_ENV and GITHUB_PATH must be provided together")
    if not github_env:
        return
    for path in (installed.compile_home, installed.runtime_home):
        if "\n" in str(path) or "\r" in str(path):
            raise BuildInputError("JDK installation path contains a newline")
    with Path(github_env).open("a", encoding="utf-8", newline="\n") as output:
        output.write(f"JAVA_HOME_17_X64={installed.compile_home}\n")
        output.write(f"JAVA_HOME_21_X64={installed.runtime_home}\n")
        output.write(f"JAVA_HOME={installed.runtime_home}\n")
    with Path(github_path).open("a", encoding="utf-8", newline="\n") as output:
        output.write(f"{installed.runtime_home / 'bin'}\n")


def inspect_installed_jdks(
    policy: Mapping[str, Any],
    *,
    compile_home: Path,
    runtime_home: Path,
) -> tuple[dict[str, Any], ...]:
    rows: list[dict[str, Any]] = []
    for role, home in (("compile", compile_home), ("runtime", runtime_home)):
        record = policy.get("jdks", {}).get(role)
        if not isinstance(record, dict):
            raise BuildInputError(f"JDK policy role is missing: {role}")
        expected_name = f"{role}-{_record_string(record, 'archiveSha256')}"
        if home.resolve(strict=True).name != expected_name:
            raise BuildInputError(f"{role} JDK is outside its digest-named extraction root")
        rows.append(_validate_extracted_jdk(home, record, role=role))
    return tuple(sorted(rows, key=lambda row: row["role"]))


def sealed_gradle_arguments(
    tasks_and_flags: Sequence[str],
    *,
    installed: InstalledJdks,
    metadata_write: bool = False,
) -> list[str]:
    values = list(tasks_and_flags)
    if values and Path(values[0]).name == "gradlew":
        values = values[1:]
    arguments = [
        "./gradlew",
        *values,
        "--dependency-verification",
        "strict",
        "-Dorg.gradle.java.installations.auto-detect=false",
        "-Dorg.gradle.java.installations.auto-download=false",
        f"-Dorg.gradle.java.installations.paths={installed.compile_home},{installed.runtime_home}",
    ]
    validate_gradle_arguments(arguments, allow_metadata_write=metadata_write)
    return arguments


def sanitized_environment(
    installed: InstalledJdks,
    *,
    gradle_home: Path,
    source_date_epoch: str | None = None,
) -> dict[str, str]:
    retained = (
        "ANDROID_HOME",
        "ANDROID_SDK_ROOT",
        "CI",
        "GITHUB_ACTION",
        "GITHUB_ACTIONS",
        "GITHUB_EVENT_NAME",
        "GITHUB_JOB",
        "GITHUB_REF",
        "GITHUB_RUN_ATTEMPT",
        "GITHUB_RUN_ID",
        "GITHUB_SHA",
        "GITHUB_WORKFLOW",
        "ImageOS",
        "ImageVersion",
        "RUNNER_ARCH",
        "RUNNER_OS",
    )
    environment = {name: os.environ[name] for name in retained if os.environ.get(name)}
    environment.update(
        {
            "GRADLE_USER_HOME": str(gradle_home.resolve()),
            "HOME": str((gradle_home / "home").resolve()),
            "JAVA_HOME": str(installed.runtime_home),
            "JAVA_HOME_17_X64": str(installed.compile_home),
            "JAVA_HOME_21_X64": str(installed.runtime_home),
            "LANG": "C.UTF-8",
            "LC_ALL": "C.UTF-8",
            "PATH": os.pathsep.join(
                [
                    str(installed.runtime_home / "bin"),
                    "/usr/local/bin",
                    "/usr/bin",
                    "/bin",
                ],
            ),
            "TZ": "UTC",
        },
    )
    if source_date_epoch is not None:
        if not source_date_epoch.isdigit():
            raise BuildInputError("SOURCE_DATE_EPOCH must be an integer timestamp")
        environment["SOURCE_DATE_EPOCH"] = source_date_epoch
    (gradle_home / "home").mkdir(parents=True, exist_ok=False)
    validate_protected_environment(
        environment,
        compile_home=str(installed.compile_home),
        runtime_home=str(installed.runtime_home),
        gradle_home=str(gradle_home.resolve()),
    )
    return environment


def new_session_root(parent: Path | None = None, *, prefix: str) -> Path:
    if parent is not None:
        parent.mkdir(parents=True, exist_ok=True)
    return Path(tempfile.mkdtemp(prefix=prefix, dir=parent)).resolve()


def write_evidence_arguments(path: Path, arguments: Sequence[str]) -> None:
    path.write_bytes(canonical_json_bytes(list(arguments)))


def assert_supported_host() -> None:
    if platform.system() != "Linux" or platform.machine().lower() not in {"x86_64", "amd64"}:
        raise BuildInputError("governed JDK evidence requires Linux x64")
