#!/usr/bin/env python3
"""Create immutable pre/post receipts and copy only allowlisted device outputs."""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

QUALITY = Path(__file__).resolve().parents[1]
ROOT = QUALITY.parents[1]
sys.path.insert(0, str(QUALITY))

from device_evidence import (  # noqa: E402
    DeviceEvidenceError,
    canonical_json_bytes,
    load_policy,
    sha256_file,
)

POLICY = ROOT / "config/quality/device-evidence-policy.json"
VERIFIER = QUALITY / "verify_device_evidence.py"


def git(*arguments: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(ROOT), *arguments],
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode:
        raise DeviceEvidenceError(f"git {' '.join(arguments)} failed: {result.stderr.strip()}")
    return result.stdout.rstrip("\n")


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def atomic_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    if temporary.exists() or temporary.is_symlink():
        raise DeviceEvidenceError(f"temporary receipt path already exists: {temporary}")
    temporary.write_bytes(canonical_json_bytes(value))
    temporary.replace(path)


def require_clean_checkout(expected_sha: str) -> tuple[str, str]:
    head = git("rev-parse", "HEAD")
    status = git("status", "--porcelain=v1", "--untracked-files=all")
    if status:
        raise DeviceEvidenceError("device evidence requires an exact clean checkout")
    if expected_sha and expected_sha != head:
        raise DeviceEvidenceError(f"event SHA differs from checkout HEAD: {expected_sha} != {head}")
    return head, status


def safe_segment(value: str, name: str) -> str:
    if not value or len(value) > 80 or not all(character.isalnum() or character in "._-" for character in value):
        raise DeviceEvidenceError(f"{name} is not a closed portable segment")
    return value


def prepare(arguments: argparse.Namespace) -> int:
    policy = load_policy(POLICY)
    if arguments.lane not in policy["lanes"]:
        raise DeviceEvidenceError(f"unknown lane: {arguments.lane}")
    run_id = safe_segment(arguments.run_id, "run ID")
    attempt_number = safe_segment(arguments.attempt_number, "attempt number")
    attempt_id = safe_segment(f"{run_id}-{attempt_number}", "attempt ID")
    head, status = require_clean_checkout(arguments.expected_sha)
    lane = policy["lanes"][arguments.lane]
    root = ROOT / "build/device-evidence" / arguments.lane / attempt_id
    if root.exists() or root.is_symlink():
        raise DeviceEvidenceError(f"attempt root already exists: {root}")
    root.mkdir(parents=True)
    wrapper = Path(arguments.wrapper).resolve()
    if ROOT.resolve() not in wrapper.parents or not wrapper.is_file() or wrapper.is_symlink():
        raise DeviceEvidenceError("wrapper must be a repository-owned regular file")
    receipt = {
        "schemaVersion": 1,
        "checkoutCommit": head,
        "checkoutStatus": status,
        "eventSha": arguments.expected_sha or "",
        "policySha256": sha256_file(POLICY),
        "wrapperSha256": sha256_file(wrapper),
        "verifierSha256": sha256_file(VERIFIER),
        "lane": arguments.lane,
        "runId": run_id,
        "attemptNumber": attempt_number,
        "attemptId": attempt_id,
        "filter": lane["filter"],
        "expectedCommands": lane["gradleTasks"],
        "resultRoots": lane["resultRoots"],
        "startedAt": now(),
        "toolIdentities": {
            "host": f"{platform.system()}-{platform.machine()}",
            "python": platform.python_version(),
        },
    }
    atomic_json(root / "attempt.json", receipt)
    print(root.relative_to(ROOT).as_posix())
    return 0


def safe_source(relative: str, allowlisted_roots: list[str]) -> Path:
    candidate = ROOT / relative
    if candidate.is_symlink() or not candidate.is_file():
        raise DeviceEvidenceError(f"collected source must be regular and non-symlink: {relative}")
    resolved = candidate.resolve()
    if not any(resolved == (ROOT / root).resolve() or (ROOT / root).resolve() in resolved.parents for root in allowlisted_roots):
        raise DeviceEvidenceError(f"collected source is outside lane roots: {relative}")
    return candidate


def require_attempt_root(value: str) -> Path:
    attempt_root = (ROOT / value).resolve()
    expected_parent = (ROOT / "build/device-evidence").resolve()
    if expected_parent not in attempt_root.parents or attempt_root.is_symlink():
        raise DeviceEvidenceError("attempt root is outside the canonical evidence tree")
    if not (attempt_root / "attempt.json").is_file():
        raise DeviceEvidenceError("attempt receipt is missing")
    return attempt_root


def copy_tree(source_root: Path, attempt_root: Path) -> int:
    copied = 0
    if source_root.is_symlink() or not source_root.is_dir():
        return copied
    for source in sorted(source_root.rglob("*")):
        if source.is_dir() and not source.is_symlink():
            continue
        if source.is_symlink() or not source.is_file():
            raise DeviceEvidenceError(f"generated evidence contains a non-regular entry: {source}")
        relative = source.relative_to(ROOT)
        destination = attempt_root / "collected" / relative
        if destination.exists() or destination.is_symlink():
            raise DeviceEvidenceError(f"collected destination already exists: {destination}")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination, follow_symlinks=False)
        copied += 1
    return copied


def parse_getprop(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8", errors="strict").splitlines():
        match = re.fullmatch(r"\[([^]]+)\]: \[(.*)]", line)
        if match:
            result[match.group(1)] = match.group(2)
    return result


GMD_RECEIPT_FIELDS = {"schemaVersion", "producer", "deviceSource", "teardown"}
GMD_DEVICE_SOURCE_FIELDS = {
    "schemaVersion",
    "producer",
    "abi",
    "apiLevel",
    "avdName",
    "fingerprint",
    "googleServicesRevision",
    "imagePackage",
    "imageSource",
    "locale",
    "permissionControllerPackage",
    "permissionControllerRevision",
    "profile",
    "shards",
    "task",
}
CONNECTED_TEARDOWN_FIELDS = {
    "schemaVersion",
    "kind",
    "timedOut",
    "logcatStopExitCode",
    "emulatorKillExitCode",
    "emulatorPidAlive",
    "serialPresent",
    "portsFree",
    "avdRemoved",
}
GMD_CLEANUP_FIELDS = {
    "schemaVersion",
    "kind",
    "timedOut",
    "baselinePids",
    "observedPids",
    "killedPids",
    "killFailures",
    "livePids",
    "adbExitCode",
    "adbTargets",
}


def read_json_object(path: Path, name: str) -> dict:
    if path.is_symlink() or not path.is_file():
        raise DeviceEvidenceError(f"{name} missing: {path.name}")
    try:
        value = json.loads(path.read_text(encoding="utf-8", errors="strict"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise DeviceEvidenceError(f"invalid {name}: {path.name}") from error
    if not isinstance(value, dict):
        raise DeviceEvidenceError(f"{name} must be a JSON object: {path.name}")
    return value


def parse_gmd_task_receipts(attempt_root: Path, tasks: list[str]) -> list[dict]:
    matching_lanes = [
        lane
        for lane in load_policy(POLICY)["lanes"].values()
        if lane["device"]["kind"] == "gmd" and lane["gradleTasks"] == tasks
    ]
    if len(matching_lanes) != 1:
        raise DeviceEvidenceError("GMD receipt task inventory does not identify one reviewed lane")
    result_roots = matching_lanes[0]["resultRoots"]
    paths = sorted((attempt_root / "raw").glob("gmd-task-*.json"))
    expected_paths = [attempt_root / "raw" / f"gmd-task-{index}.json" for index in range(len(tasks))]
    if paths != expected_paths:
        raise DeviceEvidenceError("GMD requires exactly one indexed raw AGP/UTP receipt per selected task")
    receipts = []
    for index, (path, task) in enumerate(zip(paths, tasks, strict=True)):
        receipt = read_json_object(path, "GMD task receipt")
        if set(receipt) != GMD_RECEIPT_FIELDS:
            raise DeviceEvidenceError(f"GMD task receipt {index} fields differ")
        if receipt["schemaVersion"] != 1 or receipt["producer"] != "gasstation-gmd-observation":
            raise DeviceEvidenceError(f"GMD task receipt {index} identity differs")
        source = receipt["deviceSource"]
        teardown = receipt["teardown"]
        if not isinstance(source, dict) or set(source) != {"path", "sha256"}:
            raise DeviceEvidenceError(f"GMD task receipt {index} source binding fields differ")
        relative = source["path"]
        expected_roots = [f"collected/{root}/" for root in result_roots[index * 3 : index * 3 + 3]]
        if (
            not isinstance(relative, str)
            or not relative.endswith("/device-evidence-device.json")
            or not any(relative.startswith(root) for root in expected_roots)
            or Path(relative).is_absolute()
            or any(part in {"", ".", ".."} for part in Path(relative).parts)
            or not re.fullmatch(r"[0-9a-f]{64}", source["sha256"] or "")
        ):
            raise DeviceEvidenceError(f"GMD task receipt {index} source binding is unsafe")
        source_path = attempt_root / relative
        if sha256_file(source_path) != source["sha256"]:
            raise DeviceEvidenceError(f"GMD task receipt {index} source hash differs")
        source_value = read_json_object(source_path, "pulled GMD device source")
        if set(source_value) != GMD_DEVICE_SOURCE_FIELDS:
            raise DeviceEvidenceError(f"GMD task receipt {index} pulled source fields differ")
        if (
            not isinstance(teardown, dict)
            or set(teardown) != {"status", "timedOut"}
            or teardown["status"] not in {"SUCCESS", "FAILED"}
            or not isinstance(teardown["timedOut"], bool)
        ):
            raise DeviceEvidenceError(f"GMD task receipt {index} teardown fields differ")
        google_revision = source_value["googleServicesRevision"]
        if (
            source_value["schemaVersion"] != 1
            or source_value["producer"] != "androidx-test-storage"
            or not isinstance(source_value["apiLevel"], int)
            or source_value["apiLevel"] <= 0
            or source_value["abi"] != "x86_64"
            or not re.fullmatch(r"gasstationPixel2Api(?:28|36)", source_value["avdName"] or "")
            or source_value["task"] != task
            or source_value["shards"] != 1
            or google_revision is not None and (not isinstance(google_revision, str) or not google_revision.isdigit())
            or any(
                not isinstance(source_value[field], str) or not source_value[field].strip()
                for field in ("fingerprint", "locale", "permissionControllerPackage", "permissionControllerRevision")
            )
            or not source_value["permissionControllerRevision"].isdigit()
        ):
            raise DeviceEvidenceError(f"GMD task receipt {index} device facts are incomplete")
        image_source = "google" if google_revision is not None else "aosp"
        expected_image_package = (
            f"system-images;android-{source_value['apiLevel']};{image_source};{source_value['abi']}"
        )
        if (
            source_value["profile"] != "Pixel 2"
            or source_value["imageSource"] != image_source
            or source_value["imagePackage"] != expected_image_package
        ):
            raise DeviceEvidenceError(f"GMD task receipt {index} raw image/profile facts conflict")
        receipt["derivedDevice"] = {
            "abi": source_value["abi"],
            "apiLevel": source_value["apiLevel"],
            "fingerprint": source_value["fingerprint"],
            "imagePackage": source_value["imagePackage"],
            "imageSource": source_value["imageSource"],
            "locale": source_value["locale"],
            "permissionControllerPackage": source_value["permissionControllerPackage"],
            "permissionControllerRevision": source_value["permissionControllerRevision"],
            "profile": source_value["profile"],
            "serial": source_value["avdName"],
        }
        receipt["derivedDeviceSourceShards"] = source_value["shards"]
        receipts.append(receipt)
    return receipts


def connected_metadata(attempt_root: Path, lane_name: str) -> dict:
    getprop_path = attempt_root / "raw/getprop.txt"
    devices_path = attempt_root / "raw/adb-devices.txt"
    avd_path = attempt_root / "raw/avd-config.ini"
    package_path = attempt_root / "raw/permission-controller-package.txt"
    revision_path = attempt_root / "raw/permission-controller-revision.txt"
    for path in (getprop_path, devices_path, avd_path, package_path, revision_path):
        if path.is_symlink() or not path.is_file():
            raise DeviceEvidenceError(f"connected raw metadata missing: {path.name}")
    properties = parse_getprop(getprop_path)
    devices = devices_path.read_text(encoding="utf-8", errors="strict")
    avd = avd_path.read_text(encoding="utf-8", errors="strict")
    device_lines = [line for line in devices.splitlines()[1:] if line.strip()]
    if len(device_lines) != 1 or not re.fullmatch(r"emulator-5554\s+device(?:\s+.*)?", device_lines[0]):
        raise DeviceEvidenceError("connected metadata requires exactly one online authorized emulator-5554 target")
    if "hw.device.name = pixel_2" not in avd or "android-24/google_apis/x86_64" not in avd:
        raise DeviceEvidenceError("AVD config does not bind Pixel 2 API-24 Google APIs x86_64")
    try:
        api = int(properties["ro.build.version.sdk"])
    except (KeyError, ValueError) as error:
        raise DeviceEvidenceError("connected getprop lacks a numeric SDK") from error
    fingerprint = properties.get("ro.build.fingerprint", "")
    abi = properties.get("ro.product.cpu.abi", "")
    locale = properties.get("persist.sys.locale") or properties.get("ro.product.locale", "")
    permission_package = package_path.read_text(encoding="utf-8", errors="strict").strip()
    permission_revision = revision_path.read_text(encoding="utf-8", errors="strict").strip()
    if not fingerprint or abi != "x86_64" or not locale or not permission_package or not permission_revision.isdigit():
        raise DeviceEvidenceError("connected raw adb facts lack exact ABI/fingerprint/locale/permission package revision")
    return {
        "schemaVersion": 1,
        "source": "adb",
        "lane": lane_name,
        "kind": "connected-avd",
        "apiLevel": api,
        "abi": abi,
        "fingerprint": fingerprint,
        "imagePackage": "system-images;android-24;google_apis;x86_64",
        "profile": "Pixel 2",
        "imageSource": "google_apis",
        "locale": locale,
        "permissionControllerPackage": permission_package,
        "permissionControllerRevision": permission_revision,
        "serial": "emulator-5554",
        "shards": 1,
    }


def gmd_metadata(attempt_root: Path, lane_name: str, lane: dict) -> dict:
    receipts = parse_gmd_task_receipts(attempt_root, lane["gradleTasks"])
    actual = receipts[0]["derivedDevice"]
    if any(receipt["derivedDevice"] != actual for receipt in receipts[1:]):
        raise DeviceEvidenceError("GMD task receipts conflict on actual device facts")
    return {
        "schemaVersion": 1,
        "source": "agp-utp",
        "lane": lane_name,
        "kind": "gmd",
        **actual,
        "shards": receipts[0]["derivedDeviceSourceShards"],
    }


def derive_cleanup_status(attempt_root: Path, kind: str, tasks: list[str]) -> str:
    if kind == "gmd":
        receipts = parse_gmd_task_receipts(attempt_root, tasks)
        cleanup = read_json_object(attempt_root / "raw/gmd-teardown.json", "GMD cleanup receipt")
        if set(cleanup) != GMD_CLEANUP_FIELDS or cleanup["schemaVersion"] != 1 or cleanup["kind"] != "gmd":
            raise DeviceEvidenceError("GMD cleanup receipt fields differ")
        list_fields = ("baselinePids", "observedPids", "killedPids", "killFailures", "livePids")
        if (
            type(cleanup["timedOut"]) is not bool
            or type(cleanup["adbExitCode"]) is not int
            or not isinstance(cleanup["adbTargets"], list)
            or any(not isinstance(value, str) or not value for value in cleanup["adbTargets"])
            or any(not isinstance(cleanup[field], list) or any(type(pid) is not int or pid <= 1 for pid in cleanup[field]) for field in list_fields)
        ):
            raise DeviceEvidenceError("GMD cleanup observations are malformed")
        baseline = set(cleanup["baselinePids"])
        observed = set(cleanup["observedPids"])
        killed = set(cleanup["killedPids"])
        cleanup_passed = (
            cleanup["timedOut"] is False
            and cleanup["adbExitCode"] == 0
            and cleanup["adbTargets"] == []
            and cleanup["killFailures"] == []
            and cleanup["livePids"] == []
            and killed == observed - baseline
        )
        tasks_passed = all(
            receipt["teardown"] == {"status": "SUCCESS", "timedOut": False}
            for receipt in receipts
        )
        return "PASS" if cleanup_passed and tasks_passed else "FAIL"
    if kind != "connected-avd":
        raise DeviceEvidenceError(f"unknown cleanup kind: {kind}")
    receipt = read_json_object(attempt_root / "raw/teardown.json", "connected teardown receipt")
    if set(receipt) != CONNECTED_TEARDOWN_FIELDS or receipt["schemaVersion"] != 1 or receipt["kind"] != kind:
        raise DeviceEvidenceError("connected teardown receipt fields differ")
    expected_types = {
        "timedOut": bool,
        "logcatStopExitCode": int,
        "emulatorKillExitCode": int,
        "emulatorPidAlive": bool,
        "serialPresent": bool,
        "portsFree": bool,
        "avdRemoved": bool,
    }
    if any(type(receipt[field]) is not expected for field, expected in expected_types.items()):
        raise DeviceEvidenceError("connected teardown observations are malformed")
    passed = (
        receipt["timedOut"] is False
        and receipt["logcatStopExitCode"] == 0
        and receipt["emulatorKillExitCode"] == 0
        and receipt["emulatorPidAlive"] is False
        and receipt["serialPresent"] is False
        and receipt["portsFree"] is True
        and receipt["avdRemoved"] is True
    )
    return "PASS" if passed else "FAIL"


def collect_lane(arguments: argparse.Namespace) -> int:
    attempt_root = require_attempt_root(arguments.attempt_root)
    attempt = json.loads((attempt_root / "attempt.json").read_text(encoding="utf-8"))
    policy = load_policy(POLICY)
    lane = policy["lanes"][attempt["lane"]]
    copied = 0
    for relative in [*lane["resultRoots"], *lane["apkRoots"]]:
        source_root = ROOT / relative
        resolved = source_root.resolve()
        if ROOT.resolve() not in resolved.parents or "build" not in Path(relative).parts:
            raise DeviceEvidenceError(f"lane output root is unsafe: {relative}")
        copied += copy_tree(source_root, attempt_root)
    metadata = (
        connected_metadata(attempt_root, attempt["lane"])
        if lane["device"]["kind"] == "connected-avd"
        else gmd_metadata(attempt_root, attempt["lane"], lane)
    )
    atomic_json(attempt_root / "raw/device-metadata.json", metadata)
    if copied == 0:
        raise DeviceEvidenceError("selected lane produced no collectable AGP evidence")
    return 0


def classify_artifact(attempt_root: Path, path: Path) -> str | None:
    relative = path.relative_to(attempt_root).as_posix()
    name = path.name.lower()
    if relative == "raw/commands.json":
        return "command-receipt"
    if relative == "raw/device-metadata.json":
        return "device-metadata"
    if relative.startswith("logs/gradle-") and name.endswith(".log"):
        return "gradle-log"
    if "logcat" in name:
        return "logcat"
    if relative.startswith("collected/") and path.name.startswith("TEST-") and name.endswith(".xml"):
        return "junit"
    if relative.startswith("collected/") and name == "index.html":
        return "html"
    if name.endswith(".apk"):
        return "app-apk" if "/app/build/outputs/apk/demo/debug/" in f"/{relative}" else "test-apk"
    if "failure" in name and name.endswith(".png"):
        return "failure-png"
    if "failure" in name and name.endswith(".txt"):
        return "failure-diagnostic"
    if relative.startswith(("raw/", "collected/")):
        return "raw-device"
    return None


def auto_artifacts(attempt_root: Path) -> list[dict]:
    artifacts: list[dict] = []
    for path in sorted(attempt_root.rglob("*")):
        if path.is_dir() and not path.is_symlink():
            continue
        if path.is_symlink() or not path.is_file():
            raise DeviceEvidenceError(f"attempt contains a non-regular artifact: {path}")
        relative = path.relative_to(attempt_root).as_posix()
        if relative in {"attempt.json", "completion.json", "terminal.json", "verification.json"} or relative.endswith(".tmp"):
            continue
        kind = classify_artifact(attempt_root, path)
        if kind:
            artifacts.append({"path": relative, "kind": kind, "sha256": sha256_file(path)})
    return artifacts


def collect(arguments: argparse.Namespace) -> int:
    attempt_root = (ROOT / arguments.attempt_root).resolve()
    attempt = json.loads((attempt_root / "attempt.json").read_text(encoding="utf-8"))
    policy = load_policy(POLICY)
    lane = policy["lanes"][attempt["lane"]]
    source = safe_source(arguments.source, lane["resultRoots"])
    destination = attempt_root / "collected" / arguments.source
    if destination.exists() or destination.is_symlink():
        raise DeviceEvidenceError(f"collected destination already exists: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination, follow_symlinks=False)
    print(destination.relative_to(attempt_root).as_posix())
    return 0


def complete(arguments: argparse.Namespace) -> int:
    attempt_root = require_attempt_root(arguments.attempt_root)
    attempt = json.loads((attempt_root / "attempt.json").read_text(encoding="utf-8"))
    head, status = require_clean_checkout(attempt["eventSha"])
    commands = json.loads((attempt_root / arguments.commands).read_text(encoding="utf-8"))
    if not isinstance(commands, list):
        raise DeviceEvidenceError("command receipt must be a JSON list")
    artifacts = auto_artifacts(attempt_root) if arguments.auto_artifacts else []
    policy = load_policy(POLICY)
    lane = policy["lanes"][attempt["lane"]]
    cleanup_status = derive_cleanup_status(
        attempt_root,
        lane["device"]["kind"],
        lane["gradleTasks"],
    )
    for item in arguments.artifact:
        kind, separator, relative = item.partition(":")
        if not separator or not kind or not relative:
            raise DeviceEvidenceError("artifact must use kind:relative-path")
        path = attempt_root / relative
        artifacts.append({"path": relative, "kind": kind, "sha256": sha256_file(path)})
    receipt = {
        "schemaVersion": 1,
        "attemptSha256": sha256_file(attempt_root / "attempt.json"),
        "policySha256": sha256_file(POLICY),
        "checkoutCommit": attempt["checkoutCommit"],
        "commands": commands,
        "artifacts": artifacts,
        "postRunHead": head,
        "postRunStatus": status,
        "cleanupStatus": cleanup_status,
        "completedAt": now(),
    }
    atomic_json(attempt_root / "completion.json", receipt)
    return 0


def check_cleanup(arguments: argparse.Namespace) -> int:
    attempt_root = require_attempt_root(arguments.attempt_root)
    attempt = json.loads((attempt_root / "attempt.json").read_text(encoding="utf-8"))
    lane = load_policy(POLICY)["lanes"][attempt["lane"]]
    status = derive_cleanup_status(attempt_root, lane["device"]["kind"], lane["gradleTasks"])
    if status != "PASS":
        raise DeviceEvidenceError("raw teardown observations do not prove cleanup")
    return 0


def terminal(arguments: argparse.Namespace) -> int:
    attempt_root = (ROOT / arguments.attempt_root).resolve()
    if ROOT.resolve() not in attempt_root.parents or not (attempt_root / "attempt.json").is_file():
        raise DeviceEvidenceError("terminal attempt root is invalid")
    atomic_json(
        attempt_root / "terminal.json",
        {
            "schemaVersion": 1,
            "attemptSha256": sha256_file(attempt_root / "attempt.json"),
            "status": "FAIL",
            "reason": arguments.reason[:500],
            "completedAt": now(),
        },
    )
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    commands = result.add_subparsers(dest="command", required=True)
    create = commands.add_parser("prepare")
    create.add_argument("--lane", required=True)
    create.add_argument("--run-id", required=True)
    create.add_argument("--attempt-number", required=True)
    create.add_argument("--expected-sha", default="")
    create.add_argument("--wrapper", required=True)
    create.set_defaults(handler=prepare)
    copy = commands.add_parser("collect")
    copy.add_argument("--attempt-root", required=True)
    copy.add_argument("--source", required=True)
    copy.set_defaults(handler=collect)
    collect_all = commands.add_parser("collect-lane")
    collect_all.add_argument("--attempt-root", required=True)
    collect_all.add_argument("--commands", required=True)
    collect_all.set_defaults(handler=collect_lane)
    finish = commands.add_parser("complete")
    finish.add_argument("--attempt-root", required=True)
    finish.add_argument("--commands", required=True)
    finish.add_argument("--artifact", action="append", default=[])
    finish.add_argument("--auto-artifacts", action="store_true")
    finish.set_defaults(handler=complete)
    cleanup = commands.add_parser("check-cleanup")
    cleanup.add_argument("--attempt-root", required=True)
    cleanup.set_defaults(handler=check_cleanup)
    fail = commands.add_parser("terminal")
    fail.add_argument("--attempt-root", required=True)
    fail.add_argument("--reason", required=True)
    fail.set_defaults(handler=terminal)
    return result


def main() -> int:
    try:
        arguments = parser().parse_args()
        return arguments.handler(arguments)
    except (DeviceEvidenceError, OSError, json.JSONDecodeError) as error:
        print(f"device-manifest: FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
