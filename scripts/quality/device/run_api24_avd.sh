#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
source "$script_dir/common.sh"

if [[ ${1:-} != --lane || ${2:-} != api24-scheduled || $# -ne 2 ]]; then
  printf '%s\n' "usage: run_api24_avd.sh --lane api24-scheduled" >&2
  exit 2
fi
lane=api24-scheduled
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
adb=
sdkmanager=
avdmanager=
emulator=
avd_home=
emulator_pid=0
logcat_pid=0
cleanup_done=0
cleanup_status=1
finalized=0

cleanup() {
  if (( cleanup_done == 1 )); then
    return
  fi
  cleanup_status=1
  if [[ -n ${adb:-} && -n ${avd_home:-} && ${emulator_pid:-} =~ ^[0-9]+$ ]]; then
    set +e
    run_device_phase "$lane" cleanup "$script_dir/cleanup_connected_avd.sh" \
      "$root/$attempt_root" "$adb" "$avd_home" "$emulator_pid" "$logcat_pid"
    cleanup_status=$?
    set -e
  fi
  cleanup_done=1
}

on_exit() {
  local saved=$?
  cleanup
  if (( finalized == 0 )); then
    set +e
    write_terminal_receipt "$attempt_root" "API 24 attempt terminated before completion" "$lane"
    set -e
  fi
  exit "$saved"
}
trap on_exit EXIT

require_device_environment >/dev/null
mkdir -p "$root/$attempt_root/logs" "$root/$attempt_root/raw"
run_device_phase "$lane" hostPreflight bash -euo pipefail -c '
  script_dir=$1
  lane=$2
  source "$script_dir/common.sh"
  "$script_dir/verify_host.sh" --lane "$lane"
  clear_lane_result_roots "$lane"
' _ "$script_dir" "$lane"
commands="$root/$attempt_root/raw/commands.json"
printf '[]\n' > "$commands"

adb="$ANDROID_SDK_ROOT/platform-tools/adb"
sdkmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
avdmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
emulator="$ANDROID_SDK_ROOT/emulator/emulator"
avd_home="$RUNNER_TEMP/gasstation-device-${GITHUB_RUN_ID:-${GASSTATION_DEVICE_RUN_ID}}-${GITHUB_RUN_ATTEMPT:-${GASSTATION_DEVICE_ATTEMPT}}"
test ! -e "$avd_home"
mkdir -m 700 "$avd_home"

export ANDROID_AVD_HOME="$avd_home"
set +e
run_device_phase "$lane" provision bash -euo pipefail -c '
  sdkmanager=$1
  avdmanager=$2
  attempt_root=$3
  "$sdkmanager" "platform-tools" "emulator" "system-images;android-24;google_apis;x86_64" \
    >"$attempt_root/logs/sdkmanager.log" 2>&1
  printf "no\n" | "$avdmanager" create avd \
    --force --name gasstation_api24 \
    --package "system-images;android-24;google_apis;x86_64" \
    --device pixel_2 >"$attempt_root/logs/avdmanager.log" 2>&1
  cp "$ANDROID_AVD_HOME/gasstation_api24.avd/config.ini" "$attempt_root/raw/avd-config.ini"
' _ "$sdkmanager" "$avdmanager" "$root/$attempt_root"
provision_status=$?
set -e
if (( provision_status != 0 )); then
  exit "$provision_status"
fi

set +e
run_device_phase "$lane" boot bash -euo pipefail -c '
  emulator=$1
  adb=$2
  attempt_root=$3
  "$emulator" -avd gasstation_api24 -port 5554 -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect -no-snapshot -wipe-data -camera-back none -camera-front none \
    >"$attempt_root/logs/emulator.log" 2>&1 &
  echo $! >"$attempt_root/raw/emulator.pid"
  "$adb" -s emulator-5554 wait-for-device
  while true; do
    boot=$("$adb" -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")
    animation=$("$adb" -s emulator-5554 shell getprop init.svc.bootanim 2>/dev/null | tr -d "\r")
    if [[ $boot == 1 && $animation == stopped ]] && "$adb" -s emulator-5554 shell pm path android >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
  [[ $("$adb" -s emulator-5554 shell getprop ro.build.version.sdk | tr -d "\r") == 24 ]]
  "$adb" -s emulator-5554 shell input keyevent 82
  "$adb" devices -l >"$attempt_root/raw/adb-devices.txt"
  "$adb" -s emulator-5554 shell getprop >"$attempt_root/raw/getprop.txt"
  "$adb" -s emulator-5554 shell df -h >"$attempt_root/raw/disk.txt"
  "$adb" -s emulator-5554 shell cat /proc/meminfo >"$attempt_root/raw/meminfo.txt"
  permission_package=
  for candidate in com.google.android.packageinstaller com.android.packageinstaller; do
    if "$adb" -s emulator-5554 shell pm path "$candidate" >/dev/null 2>&1; then
      permission_package=$candidate
      break
    fi
  done
  [[ -n $permission_package ]]
  printf "%s\n" "$permission_package" >"$attempt_root/raw/permission-controller-package.txt"
  "$adb" -s emulator-5554 shell dumpsys package "$permission_package" \
    | sed -n "s/.*versionCode=\\([0-9][0-9]*\\).*/\\1/p" \
    | head -n 1 >"$attempt_root/raw/permission-controller-revision.txt"
  test -s "$attempt_root/raw/permission-controller-revision.txt"
  "$adb" -s emulator-5554 logcat -c
  "$adb" -s emulator-5554 logcat -v threadtime >"$attempt_root/logs/logcat.txt" 2>&1 &
  echo $! >"$attempt_root/raw/logcat.pid"
' _ "$emulator" "$adb" "$root/$attempt_root"
boot_status=$?
set -e
if [[ -f $root/$attempt_root/raw/emulator.pid ]]; then
  emulator_pid=$(<"$root/$attempt_root/raw/emulator.pid")
fi
if [[ -f $root/$attempt_root/raw/logcat.pid ]]; then
  logcat_pid=$(<"$root/$attempt_root/raw/logcat.pid")
fi
if (( boot_status != 0 )); then
  exit "$boot_status"
fi

mapfile -t values < <(device_lane_values "$lane")
tasks=("${values[@]:4}")
seconds=("${values[0]}" "${values[1]}")
overall_status=0
for index in "${!tasks[@]}"; do
  task=${tasks[$index]}
  log="$root/$attempt_root/logs/gradle-$index.log"
  set +e
  ANDROID_SERIAL=emulator-5554 run_device_seconds "${seconds[$index]}" \
    "$root/gradlew" "$task" --warning-mode fail --no-parallel --max-workers=1 \
    --rerun-tasks --configuration-cache \
    "-Pandroid.testInstrumentationRunnerArguments.deviceEvidenceAttemptId=$attempt_id" \
    "-Pandroid.testInstrumentationRunnerArguments.deviceEvidenceLane=$lane" \
    >"$log" 2>&1
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
run_device_phase "$lane" collection "$script_dir/collect_connected_lane.sh" \
  "$root/$attempt_root" "$adb" "$overall_status" "raw/commands.json"
collection_status=$?
cleanup
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
  write_terminal_receipt "$attempt_root" "API 24 evidence collection, cleanup, or completion failed" "$lane"
  finalized=1
else
  finalized=1
fi
trap - EXIT
if (( overall_status != 0 )); then
  exit "$overall_status"
fi
exit "$verifier_status"
