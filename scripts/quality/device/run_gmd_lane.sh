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
require_device_environment >/dev/null
"$script_dir/verify_host.sh" --lane "$lane"
attempt_root=$(prepare_device_attempt "$lane" "$0")
finalized=0
on_exit() {
  local saved=$?
  if (( finalized == 0 )); then
    set +e
    write_terminal_receipt "$attempt_root" "GMD attempt terminated before completion"
    set -e
  fi
  exit "$saved"
}
trap on_exit EXIT
clear_lane_result_roots "$lane"
mkdir -p "$root/$attempt_root/logs" "$root/$attempt_root/raw"
commands="$root/$attempt_root/raw/commands.json"
printf '[]\n' > "$commands"

mapfile -t values < <(device_lane_values "$lane")
app_seconds=${values[0]}
room_seconds=${values[1]}
location_seconds=${values[2]}
filter=${values[3]}
tasks=("${values[@]:4}")
timeout_command=$(device_timeout_command)
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
  arguments=(
    "$task"
    -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
    --warning-mode fail
    --no-parallel
    --max-workers=1
    --rerun-tasks
    --configuration-cache
    --info
  )
  if [[ -n $filter ]]; then
    arguments+=("-Pandroid.testInstrumentationRunnerArguments.annotation=$filter")
  fi
  set +e
  "$timeout_command" --signal=TERM --kill-after=30s "${seconds}s" \
    "$root/gradlew" "${arguments[@]}" >"$log" 2>&1
  status=$?
  set -e
  python3 "$script_dir/record_command.py" \
    --output "$commands" --task "$task" --exit-code "$status" --log "$log"
  if (( status != 0 )); then
    overall_status=$status
    break
  fi
done

set +e
python3 "$script_dir/write_manifest.py" collect-lane --attempt-root "$attempt_root" --commands "raw/commands.json"
collection_status=$?
if (( collection_status == 0 )); then
  python3 "$script_dir/write_manifest.py" complete \
    --attempt-root "$attempt_root" --commands "raw/commands.json" --cleanup-status PASS --auto-artifacts
  completion_status=$?
else
  completion_status=$collection_status
fi
if (( completion_status == 0 )); then
  python3 "$root/scripts/quality/verify_device_evidence.py" \
    --policy "$root/config/quality/device-evidence-policy.json" \
    --attempt-root "$root/$attempt_root"
  verifier_status=$?
else
  verifier_status=$completion_status
fi
set -e

if (( collection_status != 0 || completion_status != 0 )); then
  write_terminal_receipt "$attempt_root" "GMD evidence collection or completion failed"
fi
finalized=1
trap - EXIT
if (( overall_status != 0 )); then
  exit "$overall_status"
fi
exit "$verifier_status"
