#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 5 ]]; then
  printf '%s\n' "usage: cleanup_connected_avd.sh <attempt-root> <adb> <avd-home> <emulator-pid> <logcat-pid>" >&2
  exit 2
fi
attempt_root=$1
adb=$2
avd_home=$3
emulator_pid=$4
logcat_pid=$5
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)

logcat_stop_status=1
emulator_kill_status=1
if [[ $logcat_pid =~ ^[0-9]+$ ]] && (( logcat_pid > 1 )); then
  set +e
  kill "$logcat_pid" 2>/dev/null
  logcat_stop_status=$?
  wait "$logcat_pid" 2>/dev/null
  set -e
fi
if [[ $logcat_pid == 0 ]]; then
  logcat_stop_status=0
fi
if [[ $emulator_pid =~ ^[0-9]+$ ]] && (( emulator_pid > 1 )); then
  set +e
  "$adb" -s emulator-5554 emu kill >/dev/null 2>&1
  emulator_kill_status=$?
  set -e
  for _ in {1..20}; do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  if kill -0 "$emulator_pid" 2>/dev/null; then
    set +e
    kill "$emulator_pid" 2>/dev/null
    wait "$emulator_pid" 2>/dev/null
    set -e
  fi
fi
if [[ $emulator_pid == 0 ]]; then
  emulator_kill_status=0
fi

if [[ -d $avd_home && ! -L $avd_home && $avd_home == "${RUNNER_TEMP:?}"/gasstation-device-* ]]; then
  rm -rf -- "$avd_home"
fi
final_devices="$attempt_root/raw/cleanup-adb-devices.txt"
"$adb" devices -l >"$final_devices"
python3 "$script_dir/record_connected_teardown.py" \
  --output "$attempt_root/raw/teardown.json" \
  --adb-devices "$final_devices" \
  --avd-home "$avd_home" \
  --emulator-pid "$emulator_pid" \
  --logcat-stop-exit "$logcat_stop_status" \
  --emulator-kill-exit "$emulator_kill_status"
