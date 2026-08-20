#!/usr/bin/env python3
"""Structural checks for the bounded Android device-evidence workflow."""

from __future__ import annotations

import re
from pathlib import Path

from device_evidence import DeviceEvidenceError, load_policy


EXPECTED_JOBS = {
    "device-pr-api28": {
        "lane": "api28-pr-smoke",
        "timeout": "55",
        "step_timeout": "34",
        "retention": "14",
        "artifact": "device-api28-pr-",
        "report_only": True,
    },
    "device-scheduled-api24": {
        "lane": "api24-scheduled",
        "timeout": "80",
        "step_timeout": "59",
        "retention": "30",
        "artifact": "device-api24-scheduled-",
        "report_only": False,
    },
    "device-scheduled-api28": {
        "lane": "api28-scheduled",
        "timeout": "80",
        "step_timeout": "58",
        "retention": "30",
        "artifact": "device-api28-scheduled-",
        "report_only": False,
    },
    "device-scheduled-api36": {
        "lane": "api36-scheduled",
        "timeout": "100",
        "step_timeout": "74",
        "retention": "30",
        "artifact": "device-api36-scheduled-",
        "report_only": False,
    },
}


def _job_blocks(text: str) -> dict[str, str]:
    marker = "\njobs:\n"
    if marker not in text:
        return {}
    jobs_text = text.split(marker, 1)[1]
    matches = list(re.finditer(r"(?m)^  ([A-Za-z0-9_-]+):\s*$", jobs_text))
    jobs: dict[str, str] = {}
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(jobs_text)
        jobs[match.group(1)] = jobs_text[match.end() : end]
    return jobs


def _active_scalar(block: str, key: str, indent: int) -> str | None:
    match = re.search(rf"(?m)^{' ' * indent}{re.escape(key)}:\s*([^#\n]*?)\s*$", block)
    if not match:
        return None
    return match.group(1).strip().strip('"\'')


def _step_blocks(job: str) -> list[str]:
    starts = list(re.finditer(r"(?m)^      - (?:name|uses):", job))
    result = []
    for index, match in enumerate(starts):
        end = starts[index + 1].start() if index + 1 < len(starts) else len(job)
        result.append(job[match.start() : end])
    return result


def _step_value(step: str, key: str) -> str | None:
    match = re.search(rf"(?m)^      - {re.escape(key)}:\s*([^#\n]*?)\s*(?:#.*)?$", step)
    if not match:
        match = re.search(rf"(?m)^        {re.escape(key)}:\s*([^#\n]*?)\s*(?:#.*)?$", step)
    return match.group(1).strip().strip('"\'') if match else None


def _with_value(step: str, key: str) -> str | None:
    match = re.search(rf"(?m)^          {re.escape(key)}:\s*([^#\n]*?)\s*$", step)
    return match.group(1).strip().strip('"\'') if match else None


def check_device_contracts(root: Path) -> list[str]:
    root = Path(root)
    workflow_path = root / ".github/workflows/device-evidence.yml"
    if not workflow_path.is_file():
        return [".github/workflows/device-evidence.yml:1: device evidence workflow missing"]
    text = workflow_path.read_text(encoding="utf-8", errors="strict")
    active_text = "\n".join(
        line for line in text.splitlines() if not line.lstrip().startswith("#")
    ) + "\n"
    issues: list[str] = []
    policy_path = root / "config/quality/device-evidence-policy.json"
    try:
        policy = load_policy(policy_path)
    except (DeviceEvidenceError, OSError) as error:
        policy = None
        issues.append(f"config/quality/device-evidence-policy.json:1: invalid device policy: {error}")

    for pattern, message in (
        (r"(?m)^  pull_request:\n    paths:$", "pull-request path filter missing"),
        (r'(?m)^  schedule:\n    - cron: "0 19 \* \* 0"$', "weekly schedule drifted"),
        (r"(?m)^  workflow_dispatch:$", "manual trigger missing"),
        (r"(?m)^permissions:\n  contents: read$", "read-only contents permission missing"),
        (r"(?m)^  cancel-in-progress: false$", "evidence attempts must not be cancelled"),
    ):
        if not re.search(pattern, active_text):
            issues.append(f".github/workflows/device-evidence.yml:1: {message}")

    jobs = _job_blocks(active_text)
    if set(jobs) != set(EXPECTED_JOBS):
        issues.append(".github/workflows/device-evidence.yml:1: device job inventory differs")
    forbidden_text = (
        "numManagedDeviceShards",
        "--dry-run",
        "--exclude-task",
        " -x ",
        "strategy:\n      matrix:",
        "release-publish",
    )
    for token in forbidden_text:
        if token in active_text:
            issues.append(f".github/workflows/device-evidence.yml:1: forbidden workflow surface: {token.strip()}")

    for name, expected in EXPECTED_JOBS.items():
        body = jobs.get(name)
        if body is None:
            continue
        if policy is not None:
            budget = policy["lanes"][expected["lane"]]["budgets"]
            if str(budget["outerMinutes"]) != expected["timeout"]:
                issues.append(f"config/quality/device-evidence-policy.json:1: {name} outer budget differs")
            active = sum(
                budget[key]
                for key in (
                    "setupMinutes",
                    "preflightMinutes",
                    "appMinutes",
                    "roomMinutes",
                    "locationMinutes",
                    "completionMinutes",
                    "uploadMinutes",
                )
            )
            if active >= budget["outerMinutes"] or active + budget["reserveMinutes"] != budget["outerMinutes"]:
                issues.append(f"config/quality/device-evidence-policy.json:1: {name} active budget is unbounded")
            wrapper_minutes = budget["preflightMinutes"] + budget["appMinutes"] + budget["roomMinutes"] + budget["locationMinutes"] + budget["completionMinutes"]
            if str(wrapper_minutes) != expected["step_timeout"]:
                issues.append(f"config/quality/device-evidence-policy.json:1: {name} wrapper phase sum differs")
        if _active_scalar(body, "runs-on", 4) != "ubuntu-24.04":
            issues.append(f".github/workflows/device-evidence.yml:1: {name} runner drifted")
        if _active_scalar(body, "timeout-minutes", 4) != expected["timeout"]:
            issues.append(f".github/workflows/device-evidence.yml:1: {name} timeout drifted")
        report_only = _active_scalar(body, "continue-on-error", 4) == "true"
        if report_only is not expected["report_only"]:
            issues.append(f".github/workflows/device-evidence.yml:1: {name} report-only role drifted")
        condition = _active_scalar(body, "if", 4) or ""
        if name == "device-pr-api28":
            if "github.event_name == 'pull_request'" not in condition:
                issues.append(f".github/workflows/device-evidence.yml:1: {name} event routing drifted")
        elif "github.event_name == 'schedule'" not in condition or "github.event_name == 'workflow_dispatch'" not in condition:
            issues.append(f".github/workflows/device-evidence.yml:1: {name} event routing drifted")

        steps = _step_blocks(body)
        if any(_step_value(step, "continue-on-error") is not None for step in steps):
            issues.append(f".github/workflows/device-evidence.yml:1: {name} has step-level error suppression")
        checkout = [step for step in steps if (_step_value(step, "uses") or "").startswith("actions/checkout@")]
        setup = [step for step in steps if _step_value(step, "uses") == "./.github/actions/setup-build-inputs"]
        if len(checkout) != 1 or len(setup) != 1:
            issues.append(f".github/workflows/device-evidence.yml:1: {name} setup action family differs")
        if checkout and _step_value(checkout[0], "timeout-minutes") != "2":
            issues.append(f".github/workflows/device-evidence.yml:1: {name} checkout action is unbounded")
        if setup and _step_value(setup[0], "timeout-minutes") != "8":
            issues.append(f".github/workflows/device-evidence.yml:1: {name} verified setup action is unbounded")
        run_steps = [step for step in steps if _step_value(step, "run") is not None or "run: |" in step]
        active = "\n".join(run_steps)
        if "|| true" in active:
            issues.append(f".github/workflows/device-evidence.yml:1: {name} erases a command failure")
        wrapper = "run_api24_avd.sh" if expected["lane"] == "api24-scheduled" else "run_gmd_lane.sh"
        expected_command = f"scripts/quality/device/{wrapper} --lane {expected['lane']}"
        if active.count(expected_command) != 1:
            issues.append(f".github/workflows/device-evidence.yml:1: {name} canonical wrapper call differs")
        wrapper_steps = [step for step in run_steps if _step_value(step, "run") == expected_command]
        if len(wrapper_steps) != 1 or _step_value(wrapper_steps[0], "timeout-minutes") != expected["step_timeout"]:
            issues.append(f".github/workflows/device-evidence.yml:1: {name} wrapper timeout drifted")
        for required in ("test -c /dev/kvm", "test -r /dev/kvm", "test -w /dev/kvm"):
            if not re.search(rf"(?m)^          {re.escape(required)}$", active):
                issues.append(f".github/workflows/device-evidence.yml:1: {name} KVM proof missing")
        kvm_steps = [step for step in run_steps if "test -c /dev/kvm" in step]
        if len(kvm_steps) != 1 or _step_value(kvm_steps[0], "timeout-minutes") != "2":
            issues.append(f".github/workflows/device-evidence.yml:1: {name} KVM phase is unbounded")
        verifier_mentions = active.count("verify_device_evidence.py")
        if verifier_mentions != 0:
            issues.append(f".github/workflows/device-evidence.yml:1: {name} bypasses wrapper-owned verifier chain")

        uploads = [step for step in steps if (_step_value(step, "uses") or "").startswith("actions/upload-artifact@")]
        if len(uploads) != 1:
            issues.append(f".github/workflows/device-evidence.yml:1: {name} upload step missing")
        else:
            upload = uploads[0]
            if (
                _step_value(upload, "id") != "upload"
                or _step_value(upload, "if") != "always()"
                or _step_value(upload, "timeout-minutes") != "3"
                or _with_value(upload, "if-no-files-found") != "error"
                or _with_value(upload, "retention-days") != expected["retention"]
                or not (_with_value(upload, "name") or "").startswith(expected["artifact"])
            ):
                issues.append(f".github/workflows/device-evidence.yml:1: {name} upload contract differs")
        if "steps.upload.outputs.artifact-id" not in active or "steps.upload.outputs.artifact-url" not in active or "steps.upload.outputs.artifact-digest" not in active:
            issues.append(f".github/workflows/device-evidence.yml:1: {name} upload identity summary missing")
        summary_steps = [step for step in run_steps if "steps.upload.outputs.artifact-digest" in step]
        if len(summary_steps) != 1 or _step_value(summary_steps[0], "timeout-minutes") != "1":
            issues.append(f".github/workflows/device-evidence.yml:1: {name} upload summary is unbounded")

    android = (root / ".github/workflows/android.yml").read_text(encoding="utf-8", errors="strict")
    release = re.search(r"(?ms)^  release-publish:\s*\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:\s*$|\Z)", android)
    if release and any(job in release.group("body") for job in EXPECTED_JOBS):
        issues.append(".github/workflows/android.yml:1: device evidence must not gate release-publish")

    properties = (root / "gradle.properties").read_text(encoding="utf-8", errors="strict")
    if re.search(r"(?m)^android\.enableAdditionalTestOutput\s*=\s*false\s*$", properties):
        issues.append("gradle.properties:1: Android additional test output is disabled")

    device_dir = root / "scripts/quality/device"
    wrapper_contracts = {
        "run_gmd_lane.sh": (
            'run_device_phase "$lane" hostPreflight',
            'run_device_seconds "$seconds"',
            'run_device_phase "$lane" collection',
            'run_device_phase "$lane" cleanup',
            'run_device_phase "$lane" completion',
            'run_device_phase "$lane" verify',
        ),
        "run_api24_avd.sh": (
            'run_device_phase "$lane" hostPreflight',
            'run_device_phase "$lane" provision',
            'run_device_phase "$lane" boot',
            'run_device_seconds "${seconds[$index]}"',
            'run_device_phase "$lane" collection',
            'run_device_phase "$lane" cleanup',
            'run_device_phase "$lane" completion',
            'run_device_phase "$lane" verify',
        ),
    }
    for filename, anchors in wrapper_contracts.items():
        path = device_dir / filename
        if not path.is_file():
            issues.append(f"scripts/quality/device/{filename}:1: canonical wrapper missing")
            continue
        wrapper_text = "\n".join(
            line for line in path.read_text(encoding="utf-8", errors="strict").splitlines()
            if not line.lstrip().startswith("#")
        )
        for anchor in anchors:
            if wrapper_text.count(anchor) != 1:
                issues.append(f"scripts/quality/device/{filename}:1: executable phase bound differs: {anchor}")
        if "--cleanup-status" in wrapper_text:
            issues.append(f"scripts/quality/device/{filename}:1: caller asserts cleanup status")

    common_path = device_dir / "common.sh"
    if not common_path.is_file():
        issues.append("scripts/quality/device/common.sh:1: bounded timeout helper missing")
    else:
        common_text = "\n".join(
            line for line in common_path.read_text(encoding="utf-8", errors="strict").splitlines()
            if not line.lstrip().startswith("#")
        )
        bounded_timeout = (
            '"$timeout_command" --signal=TERM --kill-after="${grace}s" '
            '"$((seconds - grace))s" "$@"'
        )
        if common_text.count("local grace=5") != 2 or common_text.count(bounded_timeout) != 2:
            issues.append("scripts/quality/device/common.sh:1: timeout termination grace escapes declared phase")
        if common_text.count("if (( seconds <= grace )); then") != 2:
            issues.append("scripts/quality/device/common.sh:1: timeout phase lacks positive command window")
    for helper in (
        "execute_gmd_task.sh",
        "gmd_processes.py",
        "capture_gmd_receipt.py",
        "cleanup_gmd_lane.py",
        "cleanup_connected_avd.sh",
        "record_connected_teardown.py",
    ):
        if not (device_dir / helper).is_file():
            issues.append(f"scripts/quality/device/{helper}:1: raw device phase helper missing")
    lifecycle_paths = {
        name: device_dir / name
        for name in ("run_gmd_lane.sh", "execute_gmd_task.sh", "cleanup_gmd_lane.py", "run_api24_avd.sh")
    }
    if not all(path.is_file() for path in lifecycle_paths.values()):
        return issues
    gmd_wrapper = lifecycle_paths["run_gmd_lane.sh"].read_text(encoding="utf-8", errors="strict")
    gmd_task = lifecycle_paths["execute_gmd_task.sh"].read_text(encoding="utf-8", errors="strict")
    gmd_cleanup = lifecycle_paths["cleanup_gmd_lane.py"].read_text(encoding="utf-8", errors="strict")
    gmd_processes = (device_dir / "gmd_processes.py").read_text(encoding="utf-8", errors="strict")
    connected_wrapper = lifecycle_paths["run_api24_avd.sh"].read_text(encoding="utf-8", errors="strict")
    if gmd_wrapper.count('gmd_processes.py" --output "$baseline_processes"') != 1 or "pgrep" in gmd_wrapper:
        issues.append("scripts/quality/device/run_gmd_lane.sh:1: shared GMD baseline discovery differs")
    if gmd_task.count('gmd_processes.py" --output "$final_processes"') != 1 or "pgrep" in gmd_task:
        issues.append("scripts/quality/device/execute_gmd_task.sh:1: shared GMD task discovery differs")
    if not all(
        anchor in gmd_cleanup
        for anchor in (
            "from device.gmd_processes import (",
            "discover_processes,",
            "read_snapshot,",
            "validate_processes,",
        )
    ):
        issues.append("scripts/quality/device/cleanup_gmd_lane.py:1: cleanup bypasses shared process identity")
    if (
        gmd_wrapper.count('  --lane "$lane" \\') != 1
        or gmd_wrapper.count('  --attempt-root "$root/$attempt_root" \\') != 1
        or gmd_cleanup.count("owner_token=owner_token") != 5
        or 'load_policy(ROOT / "config/quality/device-evidence-policy.json")' not in gmd_cleanup
        or 'read_json_value(attempt_root / "attempt.json"' not in gmd_cleanup
        or "OWNER_TOKEN_ENV = b\"GASSTATION_DEVICE_OWNER_TOKEN=\"" not in gmd_processes
    ):
        issues.append("scripts/quality/device/cleanup_gmd_lane.py:1: cleanup process ownership is not attempt-bound")
    if (
        gmd_task.count("GASSTATION_DEVICE_OWNER_TOKEN=$attempt_id") != 1
        or gmd_task.count('--owner-token "$attempt_id"') != 1
        or gmd_task.count("  --no-daemon") != 1
    ):
        issues.append("scripts/quality/device/execute_gmd_task.sh:1: GMD process owner token is not causal")
    if gmd_task.count("android.testInstrumentationRunnerArguments.deviceEvidenceLane=$lane") != 1:
        issues.append("scripts/quality/device/execute_gmd_task.sh:1: GMD instrumentation lane identity missing")
    if connected_wrapper.count("android.testInstrumentationRunnerArguments.deviceEvidenceLane=$lane") != 1:
        issues.append("scripts/quality/device/run_api24_avd.sh:1: connected instrumentation lane identity missing")
    cleanup_guard = '[[ -n ${adb:-} && -n ${avd_home:-} && ${emulator_pid:-} =~ ^[0-9]+$ ]]'
    if connected_wrapper.count(f"  if {cleanup_guard}; then") != 1:
        issues.append("scripts/quality/device/run_api24_avd.sh:1: PID-independent AVD cleanup guard differs")
    return issues
