#!/usr/bin/env bash
set -euo pipefail

device_repo_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd -P
}

require_device_environment() {
  : "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT is required}"
  : "${RUNNER_TEMP:?RUNNER_TEMP is required}"
  local run_id=${GITHUB_RUN_ID:-${GASSTATION_DEVICE_RUN_ID:-}}
  local attempt=${GITHUB_RUN_ATTEMPT:-${GASSTATION_DEVICE_ATTEMPT:-}}
  test -n "$run_id"
  test -n "$attempt"
  printf '%s\n%s\n' "$run_id" "$attempt"
}

device_timeout_command() {
  if command -v timeout >/dev/null 2>&1; then
    command -v timeout
    return
  fi
  if command -v gtimeout >/dev/null 2>&1; then
    command -v gtimeout
    return
  fi
  printf '%s\n' "A GNU-compatible timeout command is required" >&2
  return 1
}

device_phase_seconds() {
  local lane=$1
  local phase=$2
  local root
  root=$(device_repo_root)
  python3 - "$root/config/quality/device-evidence-policy.json" "$lane" "$phase" <<'PY'
import json
import sys
from pathlib import Path

policy = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
value = policy["lanes"][sys.argv[2]]["phaseSeconds"][sys.argv[3]]
if not isinstance(value, int) or value <= 0:
    raise SystemExit(f"phase {sys.argv[3]} is not active for lane {sys.argv[2]}")
print(value)
PY
}

run_device_phase() {
  local lane=$1
  local phase=$2
  shift 2
  local seconds timeout_command
  seconds=$(device_phase_seconds "$lane" "$phase")
  timeout_command=$(device_timeout_command)
  "$timeout_command" --signal=TERM --kill-after=30s "${seconds}s" "$@"
}

run_device_seconds() {
  local seconds=$1
  shift
  local timeout_command
  timeout_command=$(device_timeout_command)
  if (( seconds <= 0 )); then
    printf '%s\n' "device command has no positive timeout" >&2
    return 2
  fi
  "$timeout_command" --signal=TERM --kill-after=30s "${seconds}s" "$@"
}

require_regular_executable() {
  local path=$1
  test -f "$path"
  test ! -L "$path"
  test -x "$path"
}

require_free_emulator_5554() {
  local adb=$1
  local devices
  devices=$($adb devices)
  if printf '%s\n' "$devices" | grep -Eq '^emulator-5554[[:space:]]'; then
    printf '%s\n' "emulator-5554 is already occupied" >&2
    return 1
  fi
  python3 - <<'PY'
import socket

for port in (5554, 5555):
    probe = socket.socket()
    try:
        probe.bind(("127.0.0.1", port))
    except OSError as error:
        raise SystemExit(f"reserved emulator port {port} is occupied: {error}")
    finally:
        probe.close()
PY
}

prepare_device_attempt() {
  local lane=$1
  local wrapper=$2
  local run_id=${GITHUB_RUN_ID:-${GASSTATION_DEVICE_RUN_ID:-}}
  local attempt=${GITHUB_RUN_ATTEMPT:-${GASSTATION_DEVICE_ATTEMPT:-}}
  local expected_sha=${GITHUB_SHA:-${GASSTATION_DEVICE_EXPECTED_SHA:-}}
  local root
  root=$(device_repo_root)
  run_device_phase "$lane" prepare python3 "$root/scripts/quality/device/write_manifest.py" prepare \
    --lane "$lane" \
    --run-id "$run_id" \
    --attempt-number "$attempt" \
    --expected-sha "$expected_sha" \
    --wrapper "$wrapper"
}

clear_lane_result_roots() {
  local lane=$1
  local root
  root=$(device_repo_root)
  python3 - "$root" "$lane" <<'PY'
import json
import shutil
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
lane = sys.argv[2]
policy = json.loads((root / "config/quality/device-evidence-policy.json").read_text(encoding="utf-8"))
for relative in [
    *policy["lanes"][lane]["resultRoots"],
    *policy["lanes"][lane]["apkRoots"],
]:
    candidate = root / relative
    if candidate.is_symlink():
        raise SystemExit(f"refusing symlink result root: {relative}")
    resolved = candidate.resolve()
    if root not in resolved.parents or "build" not in Path(relative).parts:
        raise SystemExit(f"refusing unsafe result root: {relative}")
    if candidate.exists():
        shutil.rmtree(candidate)
PY
}

device_lane_values() {
  local lane=$1
  local root
  root=$(device_repo_root)
  python3 - "$root/config/quality/device-evidence-policy.json" "$lane" <<'PY'
import json
import sys
from pathlib import Path

policy = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
lane = policy["lanes"][sys.argv[2]]
print(lane["budgets"]["appMinutes"] * 60)
print(lane["budgets"]["roomMinutes"] * 60)
print(lane["budgets"]["locationMinutes"] * 60)
print(lane["filter"] or "")
for task in lane["gradleTasks"]:
    print(task)
PY
}

write_terminal_receipt() {
  local attempt_root=$1
  local reason=$2
  local lane=$3
  local root
  root=$(device_repo_root)
  run_device_phase "$lane" receipt python3 "$root/scripts/quality/device/write_manifest.py" terminal \
    --attempt-root "$attempt_root" \
    --reason "$reason"
}
