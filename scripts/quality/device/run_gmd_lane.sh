#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
source "$script_dir/common.sh"

lane=
if [[ ${1:-} == --lane && -n ${2:-} && $# -eq 2 ]]; then
  lane=$2
else
  printf '%s\n' "usage: run_gmd_lane.sh --lane <api28-pr-smoke|api28-scheduled|api36-scheduled>" >&2
  exit 2
fi
case "$lane" in
  api28-pr-smoke|api28-scheduled|api36-scheduled) ;;
  *) printf '%s\n' "GMD wrapper does not own lane: $lane" >&2; exit 2 ;;
esac

root=$(device_repo_root)
attempt_root=$(prepare_device_attempt "$lane" "$0")
attempt_id=$(python3 - "$root/$attempt_root/attempt.json" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
if not 0 < path.stat().st_size <= 2 * 1024 * 1024:
    raise SystemExit("attempt receipt size invalid")
print(json.loads(path.read_text(encoding="utf-8"))["attemptId"])
PY
)
finalized=0
on_exit() {
  local saved=$?
  if (( finalized == 0 )); then
    set +e
    write_terminal_receipt "$attempt_root" "GMD attempt terminated before completion" "$lane"
    set -e
  fi
  exit "$saved"
}
trap on_exit EXIT
require_device_environment >/dev/null
mkdir -p "$root/$attempt_root/logs" "$root/$attempt_root/raw"
baseline_processes="$root/$attempt_root/raw/gmd-baseline-processes.json"
run_device_phase "$lane" hostPreflight bash -euo pipefail -c '
  script_dir=$1
  lane=$2
  baseline_processes=$3
  source "$script_dir/common.sh"
  "$script_dir/verify_host.sh" --lane "$lane"
  clear_lane_result_roots "$lane"
  python3 "$script_dir/gmd_processes.py" --output "$baseline_processes"
' _ "$script_dir" "$lane" "$baseline_processes"
commands="$root/$attempt_root/raw/commands.json"
printf '[]\n' > "$commands"

mapfile -t values < <(device_lane_values "$lane")
app_seconds=${values[0]}
room_seconds=${values[1]}
location_seconds=${values[2]}
filter=${values[3]}
tasks=("${values[@]:4}")
overall_status=0

for index in "${!tasks[@]}"; do
  task=${tasks[$index]}
  case "$index" in
    0) seconds=$app_seconds ;;
    1) seconds=$room_seconds ;;
    *) seconds=$location_seconds ;;
  esac
  if (( seconds <= 0 )); then
    printf '%s\n' "selected task has no positive timeout: $task" >&2
    overall_status=1
    break
  fi
  log="$root/$attempt_root/logs/gradle-$index.log"
  set +e
  run_device_seconds "$seconds" "$script_dir/execute_gmd_task.sh" \
    "$lane" "$index" "$attempt_root" "$attempt_id" "$task" "$log" "$commands" "$filter" "$baseline_processes"
  status=$?
  set -e
  if (( status != 0 )); then
    overall_status=$status
    break
  fi
done

set +e
run_device_phase "$lane" collection python3 "$script_dir/write_manifest.py" \
  collect-lane --attempt-root "$attempt_root" --commands "raw/commands.json"
collection_status=$?
run_device_phase "$lane" cleanup python3 "$script_dir/cleanup_gmd_lane.py" \
  --baseline-processes "$baseline_processes" \
  --adb "$ANDROID_SDK_ROOT/platform-tools/adb" \
  --output "$root/$attempt_root/raw/gmd-teardown.json"
cleanup_status=$?
if (( collection_status == 0 && cleanup_status == 0 )); then
  run_device_phase "$lane" completion python3 "$script_dir/write_manifest.py" complete \
    --attempt-root "$attempt_root" --commands "raw/commands.json" --auto-artifacts
  completion_status=$?
else
  completion_status=1
fi
if (( completion_status == 0 )); then
  run_device_phase "$lane" verify python3 "$root/scripts/quality/verify_device_evidence.py" \
    --policy "$root/config/quality/device-evidence-policy.json" \
    --attempt-root "$root/$attempt_root"
  verifier_status=$?
else
  verifier_status=$completion_status
fi
set -e

if (( collection_status != 0 || cleanup_status != 0 || completion_status != 0 )); then
  write_terminal_receipt "$attempt_root" "GMD evidence collection, cleanup, or completion failed" "$lane"
fi
finalized=1
trap - EXIT
if (( overall_status != 0 )); then
  exit "$overall_status"
fi
exit "$verifier_status"
