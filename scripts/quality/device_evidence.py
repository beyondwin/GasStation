#!/usr/bin/env python3
"""Closed policy and receipt verifier for bounded Android device evidence."""

from __future__ import annotations

import hashlib
import json
import os
import re
from datetime import date, datetime
from pathlib import Path
from xml.etree import ElementTree


class DeviceEvidenceError(ValueError):
    pass


SHA256 = re.compile(r"[0-9a-f]{64}")
COMMIT = re.compile(r"[0-9a-f]{40}")
IDENTITY = re.compile(r"[A-Za-z_][A-Za-z0-9_.$]*#[A-Za-z_][A-Za-z0-9_$]*")
ATTEMPT_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
MAX_JSON_BYTES = 2 * 1024 * 1024
MAX_XML_BYTES = 8 * 1024 * 1024
MAX_TEXT_BYTES = 8 * 1024 * 1024

POLICY_FIELDS = {
    "artifactRoots",
    "inventories",
    "lanes",
    "noRepositoryRetry",
    "noShards",
    "quarantineOverlay",
    "schemaVersion",
}
PHASE_FIELDS = {"boot", "cleanup", "collection", "completion", "hostPreflight", "prepare", "provision", "receipt", "verify"}
LANE_FIELDS = {
    "appFlavor",
    "apkRoots",
    "budgets",
    "device",
    "filter",
    "gradleTasks",
    "inventories",
    "phaseSeconds",
    "reportOnly",
    "resultRoots",
    "retentionDays",
    "uiFailureArtifactsRequired",
}
BUDGET_FIELDS = {
    "appMinutes",
    "completionMinutes",
    "locationMinutes",
    "outerMinutes",
    "preflightMinutes",
    "reserveMinutes",
    "roomMinutes",
    "setupMinutes",
    "uploadMinutes",
}
DEVICE_FIELDS = {"apiLevel", "imagePackage", "imageSource", "kind", "name", "profile"}
CONNECTED_DEVICE_FIELDS = DEVICE_FIELDS | {"serial"}
QUARANTINE_FIELDS = {"test", "owner", "issue", "reason", "created", "expires"}
ATTEMPT_FIELDS = {
    "schemaVersion",
    "checkoutCommit",
    "checkoutStatus",
    "eventSha",
    "policySha256",
    "wrapperSha256",
    "verifierSha256",
    "lane",
    "runId",
    "attemptNumber",
    "attemptId",
    "filter",
    "expectedCommands",
    "resultRoots",
    "startedAt",
    "toolIdentities",
}
COMPLETION_FIELDS = {
    "schemaVersion",
    "attemptSha256",
    "policySha256",
    "checkoutCommit",
    "commands",
    "artifacts",
    "postRunHead",
    "postRunStatus",
    "cleanupStatus",
    "completedAt",
}
FACT_FIELDS = {
    "schemaVersion",
    "source",
    "lane",
    "kind",
    "abi",
    "apiLevel",
    "fingerprint",
    "imagePackage",
    "profile",
    "imageSource",
    "locale",
    "permissionControllerPackage",
    "permissionControllerRevision",
    "serial",
    "shards",
}

EXPECTED_LANES = {
    "api24-scheduled": {
        "kind": "connected-avd",
        "api": 24,
        "profile": "Pixel 2",
        "image": "google_apis",
        "tasks": [":app:connectedDemoDebugAndroidTest", ":core:database:connectedDebugAndroidTest"],
        "inventories": ["appFull", "roomMigrations"],
        "filter": None,
        "reportOnly": False,
        "retention": 30,
        "budget": [8, 17, 20, 15, 0, 7, 4, 9, 80],
        "phases": [300, 120, 120, 60, 30, 30, 660, 30, 90],
        "roots": [
            "app/build/outputs/androidTest-results/connected/debug/flavors/demo",
            "app/build/outputs/connected_android_test_additional_output/demoDebugAndroidTest/connected",
            "app/build/reports/androidTests/connected/debug/flavors/demo",
            "core/database/build/outputs/androidTest-results/connected/debug",
            "core/database/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected",
            "core/database/build/reports/androidTests/connected/debug",
        ],
    },
    "api28-pr-smoke": {
        "kind": "gmd",
        "api": 28,
        "profile": "Pixel 2",
        "image": "aosp",
        "tasks": [":app:gasstationPixel2Api28DemoDebugAndroidTest"],
        "inventories": ["appPrSmoke"],
        "filter": "com.gasstation.test.DevicePrSmoke",
        "reportOnly": True,
        "retention": 14,
        "budget": [8, 3, 25, 0, 0, 6, 4, 9, 55],
        "phases": [0, 60, 120, 60, 150, 30, 0, 30, 90],
        "roots": [
            "app/build/outputs/androidTest-results/managedDevice/debug/flavors/demo/gasstationPixel2Api28",
            "app/build/outputs/managed_device_android_test_additional_output/debug/flavors/demo/gasstationPixel2Api36",
            "app/build/reports/androidTests/managedDevice/debug/flavors/demo/gasstationPixel2Api28",
        ],
    },
    "api28-scheduled": {
        "kind": "gmd",
        "api": 28,
        "profile": "Pixel 2",
        "image": "aosp",
        "tasks": [
            ":app:gasstationPixel2Api28DemoDebugAndroidTest",
            ":core:database:gasstationPixel2Api28DebugAndroidTest",
        ],
        "inventories": ["appFull", "roomMigrations"],
        "filter": None,
        "reportOnly": False,
        "retention": 30,
        "budget": [8, 3, 28, 20, 0, 7, 4, 10, 80],
        "phases": [0, 60, 150, 60, 150, 30, 0, 30, 120],
        "roots": [
            "app/build/outputs/androidTest-results/managedDevice/debug/flavors/demo/gasstationPixel2Api28",
            "app/build/outputs/managed_device_android_test_additional_output/debug/flavors/demo/gasstationPixel2Api36",
            "app/build/reports/androidTests/managedDevice/debug/flavors/demo/gasstationPixel2Api28",
            "core/database/build/outputs/androidTest-results/managedDevice/debug/gasstationPixel2Api28",
            "core/database/build/outputs/managed_device_android_test_additional_output/debug/gasstationPixel2Api36",
            "core/database/build/reports/androidTests/managedDevice/debug/gasstationPixel2Api28",
        ],
    },
    "api36-scheduled": {
        "kind": "gmd",
        "api": 36,
        "profile": "Pixel 2",
        "image": "google",
        "tasks": [
            ":app:gasstationPixel2Api36DemoDebugAndroidTest",
            ":core:database:gasstationPixel2Api36DebugAndroidTest",
            ":core:location:gasstationPixel2Api36DebugAndroidTest",
        ],
        "inventories": ["appFull", "roomMigrations", "locationGeocoder"],
        "filter": None,
        "reportOnly": False,
        "retention": 30,
        "budget": [8, 3, 28, 20, 15, 8, 4, 14, 100],
        "phases": [0, 60, 180, 60, 150, 30, 0, 30, 150],
        "roots": [
            "app/build/outputs/androidTest-results/managedDevice/debug/flavors/demo/gasstationPixel2Api36",
            "app/build/outputs/managed_device_android_test_additional_output/debug/flavors/demo/gasstationPixel2Api36",
            "app/build/reports/androidTests/managedDevice/debug/flavors/demo/gasstationPixel2Api36",
            "core/database/build/outputs/androidTest-results/managedDevice/debug/gasstationPixel2Api36",
            "core/database/build/outputs/managed_device_android_test_additional_output/debug/gasstationPixel2Api36",
            "core/database/build/reports/androidTests/managedDevice/debug/gasstationPixel2Api36",
            "core/location/build/outputs/androidTest-results/managedDevice/debug/gasstationPixel2Api36",
            "core/location/build/outputs/managed_device_android_test_additional_output/debug/gasstationPixel2Api36",
            "core/location/build/reports/androidTests/managedDevice/debug/gasstationPixel2Api36",
        ],
    },
}

INSTRUMENTATION_LANE_IDENTITIES = {
    "api24-scheduled": (24, "gasstation_api24", False, {"app", "core:database"}),
    "api28-pr-smoke": (28, "gasstationPixel2Api28", True, {"app"}),
    "api28-scheduled": (28, "gasstationPixel2Api28", True, {"app", "core:database"}),
    "api36-scheduled": (36, "gasstationPixel2Api36", True, {"app", "core:database", "core:location"}),
}


def instrumentation_receipt_required(lane: str, module: str, api: int, avd_name: str) -> bool:
    """Host-testable closed identity contract mirrored by Android TestWatcher rules."""
    expected = INSTRUMENTATION_LANE_IDENTITIES.get(lane)
    if expected is None:
        raise DeviceEvidenceError(f"unreviewed instrumentation lane: {lane}")
    expected_api, expected_avd, receipt_required, modules = expected
    if module not in modules or api != expected_api or avd_name != expected_avd:
        raise DeviceEvidenceError(f"instrumentation device identity differs for {lane}/{module}")
    return receipt_required


def canonical_json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def sha256_file(path: Path) -> str:
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise DeviceEvidenceError(f"evidence path must be a regular non-symlink file: {path}")
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_bytes(path: Path, *, name: str, max_bytes: int = MAX_TEXT_BYTES) -> bytes:
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise DeviceEvidenceError(f"{name} must be a regular non-symlink file: {path}")
    size = path.stat().st_size
    if size <= 0 or size > max_bytes:
        raise DeviceEvidenceError(f"{name} size is invalid: {path}")
    with path.open("rb") as source:
        raw = source.read(max_bytes + 1)
    if not raw or len(raw) > max_bytes:
        raise DeviceEvidenceError(f"{name} size is invalid: {path}")
    return raw


def read_text(path: Path, *, name: str, max_bytes: int = MAX_TEXT_BYTES) -> str:
    raw = read_bytes(path, name=name, max_bytes=max_bytes)
    try:
        return raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        raise DeviceEvidenceError(f"{name} is not UTF-8: {path}") from error


def read_json_value(path: Path, *, name: str = "JSON evidence", max_bytes: int = MAX_JSON_BYTES) -> object:
    try:
        return json.loads(read_text(path, name=name, max_bytes=max_bytes))
    except json.JSONDecodeError as error:
        raise DeviceEvidenceError(f"invalid {name} {path}: {error}") from error


def _read_json(path: Path, *, max_bytes: int = MAX_JSON_BYTES) -> dict:
    value = read_json_value(path, max_bytes=max_bytes)
    if not isinstance(value, dict):
        raise DeviceEvidenceError(f"JSON evidence must be an object: {path}")
    return value


def _closed(value: dict, expected: set[str], name: str) -> None:
    if set(value) != expected:
        raise DeviceEvidenceError(
            f"{name} fields differ: missing={sorted(expected - set(value))}, unknown={sorted(set(value) - expected)}"
        )


def _safe_relative(value: str, name: str) -> None:
    if not isinstance(value, str) or not value or "\\" in value:
        raise DeviceEvidenceError(f"{name} must be a non-empty portable relative path")
    path = Path(value)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise DeviceEvidenceError(f"{name} is unsafe: {value}")


def _date(value: str, name: str) -> date:
    try:
        return date.fromisoformat(value)
    except (TypeError, ValueError) as error:
        raise DeviceEvidenceError(f"{name} must be YYYY-MM-DD") from error


def _instant(value: str, name: str) -> None:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (TypeError, ValueError) as error:
        raise DeviceEvidenceError(f"{name} must be an ISO-8601 instant") from error
    if parsed.tzinfo is None:
        raise DeviceEvidenceError(f"{name} must include a timezone")


def load_policy(path: Path, *, today: str | None = None) -> dict:
    policy = _read_json(Path(path))
    validate_policy(policy)
    load_quarantine(path, policy, today=today)
    return policy


def validate_policy(policy: dict, *, today: str | None = None) -> None:
    _closed(policy, POLICY_FIELDS, "device policy")
    if policy["schemaVersion"] != 1 or policy["noRepositoryRetry"] is not True or policy["noShards"] is not True:
        raise DeviceEvidenceError("device policy schema/no-retry/no-shard contract drifted")
    if set(policy["lanes"]) != set(EXPECTED_LANES):
        raise DeviceEvidenceError("device lane inventory drifted")
    if set(policy["inventories"]) != {"appFull", "appPrSmoke", "roomMigrations", "locationGeocoder"}:
        raise DeviceEvidenceError("canonical test inventory names drifted")

    all_identities: set[str] = set()
    for name, identities in policy["inventories"].items():
        if not isinstance(identities, list) or identities != sorted(identities) or len(identities) != len(set(identities)):
            raise DeviceEvidenceError(f"inventory {name} must be sorted and duplicate-free")
        if not identities or any(not isinstance(identity, str) or not IDENTITY.fullmatch(identity) for identity in identities):
            raise DeviceEvidenceError(f"inventory {name} has malformed identities")
        all_identities.update(identities)

    for lane_name, expected in EXPECTED_LANES.items():
        lane = policy["lanes"][lane_name]
        _closed(lane, LANE_FIELDS, f"lane {lane_name}")
        _closed(lane["budgets"], BUDGET_FIELDS, f"lane {lane_name} budgets")
        _closed(lane["phaseSeconds"], PHASE_FIELDS, f"lane {lane_name} executable phases")
        device_fields = CONNECTED_DEVICE_FIELDS if expected["kind"] == "connected-avd" else DEVICE_FIELDS
        _closed(lane["device"], device_fields, f"lane {lane_name} device")
        device = lane["device"]
        if (
            device["kind"] != expected["kind"]
            or device["apiLevel"] != expected["api"]
            or device["profile"] != expected["profile"]
            or device["imageSource"] != expected["image"]
            or lane["gradleTasks"] != expected["tasks"]
            or lane["inventories"] != expected["inventories"]
            or lane["filter"] != expected["filter"]
            or lane["reportOnly"] is not expected["reportOnly"]
            or lane["retentionDays"] != expected["retention"]
            or lane["appFlavor"] != "demo"
            or lane["uiFailureArtifactsRequired"] is not True
            or lane["resultRoots"] != expected["roots"]
        ):
            raise DeviceEvidenceError(f"lane {lane_name} contract drifted")
        budget = lane["budgets"]
        actual_budget = [
            budget["setupMinutes"],
            budget["preflightMinutes"],
            budget["appMinutes"],
            budget["roomMinutes"],
            budget["locationMinutes"],
            budget["completionMinutes"],
            budget["uploadMinutes"],
            budget["reserveMinutes"],
            budget["outerMinutes"],
        ]
        if actual_budget != expected["budget"]:
            raise DeviceEvidenceError(f"lane {lane_name} timeout budget drifted")
        phases = lane["phaseSeconds"]
        actual_phases = [phases[key] for key in sorted(PHASE_FIELDS)]
        if actual_phases != expected["phases"]:
            raise DeviceEvidenceError(f"lane {lane_name} executable phase budget drifted")
        if any(type(value) is not int or value < 0 for value in phases.values()):
            raise DeviceEvidenceError(f"lane {lane_name} executable phase budget is malformed")
        if any(phases[key] <= 5 for key in ("prepare", "hostPreflight", "collection", "cleanup", "completion", "verify", "receipt")):
            raise DeviceEvidenceError(f"lane {lane_name} has an unbounded active phase")
        if phases["prepare"] + phases["hostPreflight"] + phases["provision"] + phases["boot"] != budget["preflightMinutes"] * 60:
            raise DeviceEvidenceError(f"lane {lane_name} preflight phases do not close")
        if sum(phases[key] for key in ("collection", "cleanup", "completion", "verify", "receipt")) != budget["completionMinutes"] * 60:
            raise DeviceEvidenceError(f"lane {lane_name} completion phases do not close")
        if expected["kind"] == "connected-avd":
            if phases["provision"] <= 5 or phases["boot"] <= 5:
                raise DeviceEvidenceError(f"lane {lane_name} connected provisioning is unbounded")
        elif phases["provision"] != 0 or phases["boot"] != 0:
            raise DeviceEvidenceError(f"lane {lane_name} GMD setup must remain inside its task bound")
        active = sum(actual_budget[:7])
        if active >= budget["outerMinutes"] or budget["reserveMinutes"] <= 0:
            raise DeviceEvidenceError(f"lane {lane_name} has no positive outer-timeout reserve")
        if active + budget["reserveMinutes"] != budget["outerMinutes"]:
            raise DeviceEvidenceError(f"lane {lane_name} budget is not closed")
        for key in ("resultRoots", "apkRoots"):
            roots = lane[key]
            if not isinstance(roots, list) or not roots or len(roots) != len(set(roots)):
                raise DeviceEvidenceError(f"lane {lane_name} {key} missing or duplicate")
            for root in roots:
                _safe_relative(root, f"lane {lane_name} {key}")
                if "build" not in Path(root).parts:
                    raise DeviceEvidenceError(f"lane {lane_name} {key} must remain under build")

    if policy["inventories"]["appPrSmoke"] != [
        identity for identity in policy["inventories"]["appFull"] if identity in set(policy["inventories"]["appPrSmoke"])
    ]:
        raise DeviceEvidenceError("PR smoke inventory must be an ordered app-full subset")

    if policy["quarantineOverlay"] != "config/quality/device-evidence-quarantine.json":
        raise DeviceEvidenceError("quarantine overlay path drifted")

    roots = policy["artifactRoots"]
    if set(roots) != {"attempt", "connectedHtml", "connectedResults", "managedHtml", "managedResults"}:
        raise DeviceEvidenceError("artifact root inventory drifted")
    for name, root in roots.items():
        _safe_relative(root, f"artifact root {name}")


def load_quarantine(policy_path: Path, policy: dict, *, today: str | None = None) -> list[dict]:
    policy_path = Path(policy_path).resolve()
    if policy_path.parent.name == "quality" and policy_path.parent.parent.name == "config":
        repository_root = policy_path.parents[2]
    else:
        repository_root = policy_path.parent
    overlay_path = repository_root / policy["quarantineOverlay"]
    overlay = _read_json(overlay_path)
    _closed(overlay, {"entries", "schemaVersion"}, "device quarantine overlay")
    if overlay["schemaVersion"] != 1 or not isinstance(overlay["entries"], list):
        raise DeviceEvidenceError("device quarantine overlay schema drifted")

    all_identities = {
        identity
        for identities in policy["inventories"].values()
        for identity in identities
    }
    current = _date(today, "today") if today else date.today()
    seen: set[str] = set()
    for entry in overlay["entries"]:
        if not isinstance(entry, dict):
            raise DeviceEvidenceError("quarantine entry must be an object")
        _closed(entry, QUARANTINE_FIELDS, "quarantine entry")
        identity = entry["test"]
        if identity not in all_identities or identity in seen:
            raise DeviceEvidenceError("quarantine must name one unique canonical test")
        seen.add(identity)
        created = _date(entry["created"], "quarantine created")
        expires = _date(entry["expires"], "quarantine expires")
        if expires < current or (expires - created).days > 7 or expires < created:
            raise DeviceEvidenceError("quarantine is expired or exceeds seven days")
        if not all(isinstance(entry[field], str) and entry[field].strip() for field in ("owner", "issue", "reason")):
            raise DeviceEvidenceError("quarantine owner/issue/reason is required")
        if not re.fullmatch(r"https://[^\s]+", entry["issue"]):
            raise DeviceEvidenceError("quarantine issue must be an HTTPS URL")
    return overlay["entries"]


def _artifact_path(attempt_root: Path, relative: str) -> Path:
    _safe_relative(relative, "artifact path")
    root = attempt_root.resolve()
    candidate = attempt_root / relative
    if candidate.is_symlink() or not candidate.is_file():
        raise DeviceEvidenceError(f"artifact is not a regular non-symlink file: {relative}")
    resolved = candidate.resolve()
    if root not in resolved.parents:
        raise DeviceEvidenceError(f"artifact escapes attempt root: {relative}")
    return candidate


def _parse_junit(path: Path) -> tuple[list[str], set[str], set[str], set[str]]:
    raw = read_bytes(path, name="JUnit XML", max_bytes=MAX_XML_BYTES)
    try:
        raw.decode("utf-8", errors="strict")
        root = ElementTree.fromstring(raw)
    except (UnicodeDecodeError, ElementTree.ParseError) as error:
        raise DeviceEvidenceError(f"invalid JUnit XML {path}: {error}") from error
    if root.tag not in {"testsuite", "testsuites"}:
        raise DeviceEvidenceError(f"unexpected JUnit root: {root.tag}")
    tests: list[str] = []
    failed: set[str] = set()
    errors: set[str] = set()
    skipped: set[str] = set()
    for case in root.iter("testcase"):
        class_name = case.attrib.get("classname", "")
        method = case.attrib.get("name", "")
        identity = f"{class_name}#{method}"
        if not IDENTITY.fullmatch(identity):
            raise DeviceEvidenceError(f"malformed JUnit identity: {identity}")
        tests.append(identity)
        if case.find("failure") is not None:
            failed.add(identity)
        if case.find("error") is not None:
            errors.add(identity)
        if case.find("skipped") is not None:
            skipped.add(identity)
    if not tests:
        raise DeviceEvidenceError("JUnit suite executed zero tests")
    return tests, failed, errors, skipped


def _failure_artifact_names(identity: str, attempt_id: str, api: int) -> tuple[str, str]:
    class_name, method = identity.split("#", 1)
    safe_class = re.sub(r"[^A-Za-z0-9_-]", "_", class_name)
    safe_method = re.sub(r"[^A-Za-z0-9_-]", "_", method)
    stem = f"failure-{attempt_id}-{safe_class}-{safe_method}-api{api}"
    return f"{stem}.png", f"{stem}.txt"


def _under(relative: str, root: str) -> bool:
    return relative == root or relative.startswith(root.rstrip("/") + "/")


def classify_lane_artifact(lane: dict, relative: str) -> str | None:
    """Return the policy-derived kind for one canonical attempt-relative path."""
    _safe_relative(relative, "artifact path")
    if relative == "raw/commands.json":
        return "command-receipt"
    if relative == "raw/device-metadata.json":
        return "device-metadata"
    if relative == "logs/logcat.txt" and lane["device"]["kind"] == "connected-avd":
        return "logcat"
    if relative in {f"logs/gradle-{index}.log" for index in range(len(lane["gradleTasks"]))}:
        return "gradle-log"
    if lane["device"]["kind"] == "connected-avd" and relative in {
        "logs/sdkmanager.log", "logs/avdmanager.log", "logs/emulator.log"
    }:
        return "setup-log"

    connected_raw = {
        "raw/adb-devices.txt", "raw/getprop.txt", "raw/avd-config.ini",
        "raw/permission-controller-package.txt", "raw/permission-controller-revision.txt",
        "raw/teardown.json", "raw/cleanup-adb-devices.txt", "raw/disk.txt", "raw/meminfo.txt",
        "raw/emulator.pid", "raw/logcat.pid", "raw/final-failure.png", "raw/bugreport.zip",
        "raw/collection-failures.json",
    }
    if lane["device"]["kind"] == "connected-avd" and relative in connected_raw:
        return "raw-device"
    if lane["device"]["kind"] == "gmd":
        gmd_raw = {"raw/gmd-baseline-processes.json", "raw/gmd-teardown.json"}
        for index in range(len(lane["gradleTasks"])):
            gmd_raw.update({
                f"raw/gmd-task-{index}.json",
                f"raw/gmd-task-{index}-processes.json",
                f"raw/gmd-task-{index}-adb-devices.txt",
            })
        if relative in gmd_raw:
            return "raw-device"

    for index in range(len(lane["gradleTasks"])):
        result_root, additional_root, report_root = lane["resultRoots"][index * 3:index * 3 + 3]
        result_prefix = f"collected/{result_root}"
        additional_prefix = f"collected/{additional_root}"
        report_prefix = f"collected/{report_root}"
        name = Path(relative).name
        lower = name.lower()
        if _under(relative, result_prefix):
            if name.startswith("TEST-") and lower.endswith(".xml"):
                return "junit"
            return "test-result"
        if _under(relative, additional_prefix):
            if name.startswith("failure-") and lower.endswith(".png"):
                return "failure-png"
            if name.startswith("failure-") and lower.endswith(".txt"):
                return "failure-diagnostic"
            return "raw-device"
        if _under(relative, report_prefix):
            return "html" if lower.endswith(".html") else "report-asset"

    for index, apk_root in enumerate(lane["apkRoots"]):
        if _under(relative, f"collected/{apk_root}") and relative.lower().endswith(".apk"):
            return "app-apk" if index == 0 else "test-apk"
    return None


def _repository_root(policy_path: Path) -> Path:
    resolved = policy_path.resolve()
    if resolved.parent.name != "quality" or resolved.parent.parent.name != "config":
        raise DeviceEvidenceError("policy must use the canonical config/quality path")
    return resolved.parents[2]


def _validate_artifact_content(path: Path, kind: str) -> None:
    if kind in {"app-apk", "test-apk"}:
        size = path.stat().st_size
        if size <= 4 or size > 1024 * 1024 * 1024:
            raise DeviceEvidenceError(f"APK artifact size is invalid: {path}")
        with path.open("rb") as source:
            magic = source.read(4)
        if magic not in {b"PK\x03\x04", b"PK\x05\x06", b"PK\x07\x08"}:
            raise DeviceEvidenceError(f"APK artifact has invalid ZIP magic: {path}")
        return
    if kind == "failure-png" or path.name == "final-failure.png":
        if not read_bytes(path, name="PNG artifact", max_bytes=64 * 1024 * 1024).startswith(b"\x89PNG\r\n\x1a\n"):
            raise DeviceEvidenceError(f"PNG artifact has invalid magic: {path}")
        return
    if path.name == "bugreport.zip":
        if not 2 < path.stat().st_size <= 1024 * 1024 * 1024:
            raise DeviceEvidenceError("bugreport ZIP size is invalid")
        with path.open("rb") as source:
            magic = source.read(2)
        if magic != b"PK":
            raise DeviceEvidenceError("bugreport ZIP has invalid magic")
        return
    if kind in {"gradle-log", "logcat", "setup-log", "html", "report-asset", "test-result"}:
        raw = read_bytes(path, name=f"{kind} artifact")
        if kind == "html" and path.suffix.lower() == ".html" and b"<html" not in raw.lower():
            raise DeviceEvidenceError(f"HTML artifact is malformed: {path}")
        return
    if kind in {"raw-device", "failure-diagnostic", "command-receipt", "device-metadata"}:
        if path.suffix.lower() == ".json" or kind in {"failure-diagnostic", "command-receipt", "device-metadata"}:
            read_json_value(path, name=f"{kind} JSON")
        else:
            read_bytes(path, name=f"{kind} artifact")


def _required_task_artifacts(attempt_root: Path, lane: dict, policy: dict, seen_paths: set[str]) -> None:
    for index, inventory in enumerate(lane["inventories"]):
        result_root, _additional_root, report_root = lane["resultRoots"][index * 3:index * 3 + 3]
        junit_prefix = f"collected/{result_root}/"
        junit_paths = sorted(path for path in seen_paths if path.startswith(junit_prefix) and path.endswith(".xml") and Path(path).name.startswith("TEST-"))
        if not junit_paths:
            raise DeviceEvidenceError(f"task {index} is missing its own JUnit artifact")
        task_tests: list[str] = []
        for relative in junit_paths:
            tests, _, _, _ = _parse_junit(attempt_root / relative)
            task_tests.extend(tests)
        if sorted(task_tests) != sorted(policy["inventories"][inventory]):
            raise DeviceEvidenceError(f"task {index} JUnit inventory differs")
        html_prefix = f"collected/{report_root}/"
        if f"{html_prefix}index.html" not in seen_paths:
            raise DeviceEvidenceError(f"task {index} is missing its own HTML report")
        if f"logs/gradle-{index}.log" not in seen_paths:
            raise DeviceEvidenceError(f"task {index} is missing its own Gradle log")
        if lane["device"]["kind"] == "gmd" and f"raw/gmd-task-{index}.json" not in seen_paths:
            raise DeviceEvidenceError(f"task {index} is missing its own raw GMD receipt")
    for index, apk_root in enumerate(lane["apkRoots"]):
        prefix = f"collected/{apk_root}/"
        if not any(path.startswith(prefix) and path.endswith(".apk") for path in seen_paths):
            raise DeviceEvidenceError(f"APK root {index} is missing its own APK artifact")
    if lane["device"]["kind"] == "gmd":
        required_raw = {"raw/gmd-baseline-processes.json", "raw/gmd-teardown.json"}
        for index in range(len(lane["gradleTasks"])):
            required_raw.update({
                f"raw/gmd-task-{index}.json",
                f"raw/gmd-task-{index}-processes.json",
                f"raw/gmd-task-{index}-adb-devices.txt",
            })
    else:
        required_raw = {
            "raw/adb-devices.txt", "raw/getprop.txt", "raw/avd-config.ini",
            "raw/permission-controller-package.txt", "raw/permission-controller-revision.txt",
            "raw/teardown.json", "raw/cleanup-adb-devices.txt", "raw/disk.txt", "raw/meminfo.txt",
            "raw/emulator.pid", "raw/logcat.pid",
        }
    missing_raw = required_raw - seen_paths
    if missing_raw:
        raise DeviceEvidenceError(f"selected lane raw artifact inventory is incomplete: {sorted(missing_raw)}")


def verify_attempt(policy_path: Path, attempt_root: Path, *, today: str | None = None) -> dict:
    policy_path = Path(policy_path)
    attempt_root = Path(attempt_root)
    if attempt_root.is_symlink() or not attempt_root.is_dir():
        raise DeviceEvidenceError("attempt root must be a real directory")
    policy = load_policy(policy_path, today=today)
    quarantine = load_quarantine(policy_path, policy, today=today)
    policy_hash = sha256_file(policy_path)
    attempt_path = attempt_root / "attempt.json"
    completion_path = attempt_root / "completion.json"
    attempt = _read_json(attempt_path)
    completion = _read_json(completion_path)
    _closed(attempt, ATTEMPT_FIELDS, "attempt receipt")
    _closed(completion, COMPLETION_FIELDS, "completion receipt")
    if attempt["schemaVersion"] != 1 or completion["schemaVersion"] != 1:
        raise DeviceEvidenceError("receipt schema drifted")
    lane_name = attempt["lane"]
    if lane_name not in policy["lanes"]:
        raise DeviceEvidenceError("attempt lane is not canonical")
    lane = policy["lanes"][lane_name]
    if not COMMIT.fullmatch(attempt["checkoutCommit"] or "") or attempt["checkoutStatus"] != "":
        raise DeviceEvidenceError("attempt checkout must bind a clean exact commit")
    if attempt["eventSha"] not in {"", attempt["checkoutCommit"]}:
        raise DeviceEvidenceError("event SHA differs from checkout HEAD")
    if (
        attempt["policySha256"] != policy_hash
        or completion["policySha256"] != policy_hash
        or completion["attemptSha256"] != sha256_file(attempt_path)
        or completion["checkoutCommit"] != attempt["checkoutCommit"]
        or completion["postRunHead"] != attempt["checkoutCommit"]
        or completion["postRunStatus"] != ""
    ):
        raise DeviceEvidenceError("checkout/policy/attempt completion binding differs")
    repository_root = _repository_root(policy_path)
    wrapper_name = "run_api24_avd.sh" if lane["device"]["kind"] == "connected-avd" else "run_gmd_lane.sh"
    expected_wrapper_hash = sha256_file(repository_root / "scripts/quality/device" / wrapper_name)
    expected_verifier_hash = sha256_file(repository_root / "scripts/quality/verify_device_evidence.py")
    if attempt["wrapperSha256"] != expected_wrapper_hash or attempt["verifierSha256"] != expected_verifier_hash:
        raise DeviceEvidenceError("wrapper/verifier hash differs from the current selected implementation")
    if not ATTEMPT_ID.fullmatch(attempt["attemptId"] or ""):
        raise DeviceEvidenceError("attempt ID is malformed")
    if (
        not ATTEMPT_ID.fullmatch(str(attempt["runId"]) or "")
        or not ATTEMPT_ID.fullmatch(str(attempt["attemptNumber"]) or "")
        or attempt["attemptId"] != f"{attempt['runId']}-{attempt['attemptNumber']}"
    ):
        raise DeviceEvidenceError("run/attempt identity is malformed")
    if not isinstance(attempt["toolIdentities"], dict) or not attempt["toolIdentities"]:
        raise DeviceEvidenceError("pre-run tool identities are missing")
    if attempt["filter"] != lane["filter"] or attempt["expectedCommands"] != lane["gradleTasks"]:
        raise DeviceEvidenceError("attempt filter/command contract differs")
    if attempt["resultRoots"] != lane["resultRoots"]:
        raise DeviceEvidenceError("attempt result roots differ")
    _instant(attempt["startedAt"], "attempt start")
    _instant(completion["completedAt"], "completion time")
    commands = completion["commands"]
    if not isinstance(commands, list) or [entry.get("task") for entry in commands if isinstance(entry, dict)] != lane["gradleTasks"]:
        raise DeviceEvidenceError("completion command order differs")
    for entry in commands:
        if (
            set(entry) != {"task", "exitCode", "outcome"}
            or type(entry["exitCode"]) is not int
            or entry["exitCode"] < 0
            or entry["outcome"] != "EXECUTED"
        ):
            raise DeviceEvidenceError("command was cached, skipped, or malformed")
    command_failed = any(entry["exitCode"] != 0 for entry in commands)

    artifacts = completion["artifacts"]
    if not isinstance(artifacts, list) or not artifacts:
        raise DeviceEvidenceError("completion artifacts missing")
    seen_paths: set[str] = set()
    kinds: dict[str, list[Path]] = {}
    artifact_records = []
    for entry in artifacts:
        if not isinstance(entry, dict) or set(entry) != {"path", "kind", "sha256"}:
            raise DeviceEvidenceError("artifact record malformed")
        relative = entry["path"]
        if relative in seen_paths:
            raise DeviceEvidenceError("duplicate artifact path")
        seen_paths.add(relative)
        path = _artifact_path(attempt_root, relative)
        derived_kind = classify_lane_artifact(lane, relative)
        if derived_kind is None or entry["kind"] != derived_kind:
            raise DeviceEvidenceError(f"artifact path/kind is outside the selected lane contract: {relative}")
        actual_hash = sha256_file(path)
        if entry["sha256"] != actual_hash:
            raise DeviceEvidenceError(f"completion-bound artifact changed: {relative}")
        _validate_artifact_content(path, derived_kind)
        kinds.setdefault(derived_kind, []).append(path)
        artifact_records.append({"path": relative, "kind": entry["kind"], "sha256": actual_hash})
    for required in (
        "junit",
        "device-metadata",
        "html",
        "gradle-log",
        "app-apk",
        "test-apk",
        "command-receipt",
        "raw-device",
    ):
        if required not in kinds:
            raise DeviceEvidenceError(f"required artifact kind missing: {required}")
    if lane["device"]["kind"] == "connected-avd" and "logcat" not in kinds:
        raise DeviceEvidenceError("connected lane logcat artifact missing")
    evidence_files = {
        path.relative_to(attempt_root).as_posix()
        for path in attempt_root.rglob("*")
        if path.is_file() and path.name not in {"attempt.json", "completion.json", "terminal.json", "verification.json"}
    }
    if evidence_files != seen_paths:
        raise DeviceEvidenceError(
            f"completion artifact inventory differs: missing={sorted(evidence_files - seen_paths)}, stale={sorted(seen_paths - evidence_files)}"
        )
    _required_task_artifacts(attempt_root, lane, policy, seen_paths)
    if (attempt_root / "raw/collection-failures.json").exists():
        raise DeviceEvidenceError("device failure diagnostic collection was incomplete")
    if len(kinds["device-metadata"]) != 1 or len(kinds["command-receipt"]) != 1:
        raise DeviceEvidenceError("device metadata and command receipt must each be unique")
    try:
        command_receipt = read_json_value(kinds["command-receipt"][0], name="command receipt")
    except DeviceEvidenceError as error:
        raise DeviceEvidenceError(f"invalid command receipt: {error}") from error
    if command_receipt != commands:
        raise DeviceEvidenceError("raw command receipt differs from completion commands")

    platform_markers = (
        "INSTRUMENTATION_FAILED",
        "INSTALL_FAILED",
        "Process crashed while executing tests",
        "Unified Test Platform error",
        "Emulator terminated before test completion",
    )
    for log in [*kinds["gradle-log"], *kinds.get("logcat", [])]:
        text = read_text(log, name="platform diagnostic log")
        if any(marker in text for marker in platform_markers):
            raise DeviceEvidenceError(f"platform/UTP failure marker in {log.name}")

    metadata = _read_json(kinds["device-metadata"][0])
    _closed(metadata, FACT_FIELDS, "device metadata")
    device = lane["device"]
    expected_source = "agp-utp" if device["kind"] == "gmd" else "adb"
    # Reparse the completion-bound raw receipts. Metadata is only a derived
    # convenience record and is never authoritative on its own.
    from device.write_manifest import connected_metadata, derive_cleanup_status, gmd_metadata

    raw_metadata = (
        connected_metadata(attempt_root, lane_name)
        if device["kind"] == "connected-avd"
        else gmd_metadata(attempt_root, lane_name, lane)
    )
    if metadata != raw_metadata:
        raise DeviceEvidenceError("derived device metadata differs from raw device receipts")
    cleanup_status = derive_cleanup_status(attempt_root, device["kind"], lane["gradleTasks"])
    if completion["cleanupStatus"] != cleanup_status or cleanup_status != "PASS":
        raise DeviceEvidenceError("bounded cleanup status differs from raw teardown observations")
    if (
        metadata["schemaVersion"] != 1
        or metadata["source"] != expected_source
        or metadata["lane"] != lane_name
        or metadata["kind"] != device["kind"]
        or metadata["apiLevel"] != device["apiLevel"]
        or metadata["abi"] != "x86_64"
        or metadata["imagePackage"] != device["imagePackage"]
        or metadata["profile"] != device["profile"]
        or metadata["imageSource"] != device["imageSource"]
        or metadata["serial"] != device.get("serial", device["name"])
        or metadata["shards"] != 1
    ):
        raise DeviceEvidenceError("raw-derived device facts differ from policy")

    expected_tests = [identity for name in lane["inventories"] for identity in policy["inventories"][name]]
    actual_tests: list[str] = []
    failures: set[str] = set()
    errors: set[str] = set()
    skipped: set[str] = set()
    for junit_path in kinds["junit"]:
        tests, test_failures, test_errors, test_skipped = _parse_junit(junit_path)
        actual_tests.extend(tests)
        failures.update(test_failures)
        errors.update(test_errors)
        skipped.update(test_skipped)
    if len(actual_tests) != len(set(actual_tests)):
        raise DeviceEvidenceError("duplicate test identity across JUnit reports")
    if sorted(actual_tests) != sorted(expected_tests):
        raise DeviceEvidenceError("required test identity set differs")
    if skipped:
        raise DeviceEvidenceError(f"required tests skipped: {sorted(skipped)}")
    test_problems = failures | errors
    if test_problems:
        failed_app = [
            identity
            for identity in test_problems
            if identity.startswith("com.gasstation.") and ".core." not in identity
        ]
        png_names = {path.name for path in kinds.get("failure-png", [])}
        diagnostics = {path.name for path in kinds.get("failure-diagnostic", [])}
        for identity in failed_app:
            png_name, diagnostic_name = _failure_artifact_names(
                identity,
                attempt["attemptId"],
                metadata["apiLevel"],
            )
            if png_name not in png_names or diagnostic_name not in diagnostics:
                raise DeviceEvidenceError(f"failed app test lacks exact pulled PNG/diagnostic: {identity}")
            diagnostic = _read_json(next(path for path in kinds["failure-diagnostic"] if path.name == diagnostic_name))
            if set(diagnostic) != {"apiLevel", "attemptId", "className", "methodName", "permissionSelection"}:
                raise DeviceEvidenceError("failure diagnostic fields differ")
            class_name, method = identity.split("#", 1)
            if (
                diagnostic["apiLevel"] != metadata["apiLevel"]
                or diagnostic["attemptId"] != attempt["attemptId"]
                or diagnostic["className"] != class_name
                or diagnostic["methodName"] != method
                or diagnostic["permissionSelection"] is not None
                and not isinstance(diagnostic["permissionSelection"], dict)
            ):
                raise DeviceEvidenceError("failure diagnostic attempt/test identity differs")
            selection = diagnostic["permissionSelection"]
            if selection is not None:
                if set(selection) != {"packageName", "resourceName"}:
                    raise DeviceEvidenceError("permission selection diagnostic fields differ")
                if metadata["apiLevel"] <= 28:
                    packages = {
                        "com.android.packageinstaller",
                        "com.google.android.packageinstaller",
                    }
                    resources = {"permission_allow_button", "permission_deny_button"}
                else:
                    packages = {
                        "com.android.permissioncontroller",
                        "com.google.android.permissioncontroller",
                    }
                    resources = {
                        "permission_allow_foreground_only_button",
                        "permission_deny_button",
                    }
                if selection["packageName"] not in packages or selection["resourceName"] not in resources:
                    raise DeviceEvidenceError("permission selection is outside the closed SDK table")
        if not command_failed:
            raise DeviceEvidenceError("JUnit failures/errors are not bound to a nonzero test command")
        status = "FAIL"
    elif command_failed:
        raise DeviceEvidenceError("nonzero test command lacks matching collected JUnit failure")
    elif quarantine:
        status = "QUARANTINED"
    else:
        if kinds.get("failure-png") or kinds.get("failure-diagnostic"):
            raise DeviceEvidenceError("successful lane contains stale failure artifacts")
        status = "PASS"

    return {
        "schemaVersion": 1,
        "checkoutCommit": attempt["checkoutCommit"],
        "attemptSha256": sha256_file(attempt_path),
        "completionSha256": sha256_file(completion_path),
        "policySha256": policy_hash,
        "lane": lane_name,
        "device": metadata,
        "tests": sorted(actual_tests),
        "counters": {
            "tests": len(actual_tests),
            "failures": len(failures),
            "errors": len(errors),
            "skipped": len(skipped),
        },
        "artifacts": sorted(artifact_records, key=lambda entry: entry["path"]),
        "quarantine": quarantine,
        "status": status,
    }
