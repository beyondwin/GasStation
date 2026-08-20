#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 9 ]]; then
  printf '%s\n' "usage: execute_gmd_task.sh <lane> <index> <attempt-root> <attempt-id> <task> <log> <commands> <filter> <baseline-processes>" >&2
  exit 2
fi

lane=$1
index=$2
attempt_root=$3
attempt_id=$4
task=$5
log=$6
commands=$7
filter=$8
baseline_processes=$9
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
root=$(cd "$script_dir/../../.." && pwd -P)
adb="$ANDROID_SDK_ROOT/platform-tools/adb"

arguments=(
  "$task"
  --no-daemon
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
  --warning-mode fail
  --no-parallel
  --max-workers=1
  --rerun-tasks
  --configuration-cache
  --info
  "-Pandroid.testInstrumentationRunnerArguments.deviceEvidenceAttemptId=$attempt_id"
  "-Pandroid.testInstrumentationRunnerArguments.deviceEvidenceLane=$lane"
)
if [[ -n $filter ]]; then
  arguments+=("-Pandroid.testInstrumentationRunnerArguments.annotation=$filter")
fi

set +e
GASSTATION_DEVICE_OWNER_TOKEN=$attempt_id "$root/scripts/quality/build_inputs/run_gradle.sh" "${arguments[@]}" >"$log" 2>&1
gradle_status=$?
set -e
python3 "$script_dir/record_command.py" \
  --output "$commands" --task "$task" --exit-code "$gradle_status" --log "$log"

final_processes="$root/$attempt_root/raw/gmd-task-$index-processes.json"
final_devices="$root/$attempt_root/raw/gmd-task-$index-adb-devices.txt"
python3 "$script_dir/gmd_processes.py" --output "$final_processes" --owner-token "$attempt_id"
"$adb" devices -l >"$final_devices"

set +e
python3 "$script_dir/capture_gmd_receipt.py" \
  --lane "$lane" \
  --task-index "$index" \
  --attempt-root "$root/$attempt_root" \
  --exit-code "$gradle_status" \
  --baseline-processes "$baseline_processes" \
  --final-processes "$final_processes" \
  --final-adb-devices "$final_devices"
capture_status=$?
set -e
if (( capture_status != 0 )); then
  exit "$capture_status"
fi
exit "$gradle_status"
