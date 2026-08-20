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
require_device_environment >/dev/null
"$script_dir/verify_host.sh" --lane "$lane"
attempt_root=$(prepare_device_attempt "$lane" "$0")
clear_lane_result_roots "$lane"
mkdir -p "$root/$attempt_root/logs" "$root/$attempt_root/raw"
commands="$root/$attempt_root/raw/commands.json"
printf '[]\n' > "$commands"

adb="$ANDROID_SDK_ROOT/platform-tools/adb"
sdkmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
avdmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
emulator="$ANDROID_SDK_ROOT/emulator/emulator"
timeout_command=$(device_timeout_command)
avd_home="$RUNNER_TEMP/gasstation-device-${GITHUB_RUN_ID:-${GASSTATION_DEVICE_RUN_ID}}-${GITHUB_RUN_ATTEMPT:-${GASSTATION_DEVICE_ATTEMPT}}"
test ! -e "$avd_home"
mkdir -m 700 "$avd_home"
emulator_pid=
logcat_pid=
cleanup_status=PASS
cleanup_done=0
finalized=0

cleanup() {
  if (( cleanup_done == 1 )); then
    return
  fi
  set +e
  if [[ -n ${logcat_pid:-} ]]; then
    kill "$logcat_pid" 2>/dev/null
    wait "$logcat_pid" 2>/dev/null
  fi
  "$adb" -s emulator-5554 emu kill >/dev/null 2>&1
  for _ in {1..30}; do
    kill -0 "${emulator_pid:-0}" 2>/dev/null || break
    sleep 1
  done
  if [[ -n ${emulator_pid:-} ]] && kill -0 "$emulator_pid" 2>/dev/null; then
    kill "$emulator_pid" 2>/dev/null
    wait "$emulator_pid" 2>/dev/null
  fi
  if [[ -d $avd_home && ! -L $avd_home && $avd_home == "$RUNNER_TEMP"/gasstation-device-* ]]; then
    rm -rf -- "$avd_home"
  else
    cleanup_status=FAIL
  fi
  cleanup_done=1
  set -e
}

on_exit() {
  local saved=$?
  cleanup
  if (( finalized == 0 )); then
    set +e
    write_terminal_receipt "$attempt_root" "API 24 attempt terminated before completion"
    set -e
  fi
  exit "$saved"
}
trap on_exit EXIT

set +e
"$timeout_command" --signal=TERM --kill-after=30s 720s \
  "$sdkmanager" "platform-tools" "emulator" "system-images;android-24;google_apis;x86_64" \
  >"$root/$attempt_root/logs/sdkmanager.log" 2>&1
provision_status=$?
set -e
if (( provision_status != 0 )); then
  exit "$provision_status"
fi

export ANDROID_AVD_HOME="$avd_home"
printf 'no\n' | "$avdmanager" create avd \
  --force --name gasstation_api24 \
  --package "system-images;android-24;google_apis;x86_64" \
  --device pixel_2 >"$root/$attempt_root/logs/avdmanager.log" 2>&1
cp "$ANDROID_AVD_HOME/gasstation_api24.avd/config.ini" "$root/$attempt_root/raw/avd-config.ini"

"$emulator" -avd gasstation_api24 -port 5554 -no-window -no-audio -no-boot-anim \
  -gpu swiftshader_indirect -no-snapshot -wipe-data -camera-back none -camera-front none \
  >"$root/$attempt_root/logs/emulator.log" 2>&1 &
emulator_pid=$!

"$timeout_command" --signal=TERM --kill-after=10s 300s "$adb" -s emulator-5554 wait-for-device
deadline=$((SECONDS + 300))
while (( SECONDS < deadline )); do
  boot=$($adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  animation=$($adb -s emulator-5554 shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')
  if [[ $boot == 1 && $animation == stopped ]] && \
    "$adb" -s emulator-5554 shell pm path android >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
[[ $boot == 1 && $animation == stopped ]]
[[ $($adb -s emulator-5554 shell getprop ro.build.version.sdk | tr -d '\r') == 24 ]]
"$adb" -s emulator-5554 shell input keyevent 82
"$adb" devices -l >"$root/$attempt_root/raw/adb-devices.txt"
"$adb" -s emulator-5554 shell getprop >"$root/$attempt_root/raw/getprop.txt"
"$adb" -s emulator-5554 shell df -h >"$root/$attempt_root/raw/disk.txt"
"$adb" -s emulator-5554 shell cat /proc/meminfo >"$root/$attempt_root/raw/meminfo.txt"
"$adb" -s emulator-5554 logcat -c
"$adb" -s emulator-5554 logcat -v threadtime >"$root/$attempt_root/logs/logcat.txt" 2>&1 &
logcat_pid=$!

mapfile -t values < <(device_lane_values "$lane")
tasks=("${values[@]:4}")
seconds=("${values[0]}" "${values[1]}")
overall_status=0
for index in "${!tasks[@]}"; do
  task=${tasks[$index]}
  log="$root/$attempt_root/logs/gradle-$index.log"
  set +e
  ANDROID_SERIAL=emulator-5554 "$timeout_command" --signal=TERM --kill-after=30s "${seconds[$index]}s" \
    "$root/gradlew" "$task" --warning-mode fail --no-parallel --max-workers=1 \
    --rerun-tasks --configuration-cache >"$log" 2>&1
  status=$?
  set -e
  python3 "$script_dir/record_command.py" \
    --output "$commands" --task "$task" --exit-code "$status" --log "$log"
  if (( status != 0 )); then
    overall_status=$status
    set +e
    "$adb" -s emulator-5554 exec-out screencap -p >"$root/$attempt_root/raw/final-failure.png" 2>/dev/null
    screenshot_status=$?
    "$timeout_command" --signal=TERM --kill-after=10s 120s "$adb" -s emulator-5554 bugreport \
      "$root/$attempt_root/raw/bugreport.zip" >/dev/null 2>&1
    bugreport_status=$?
    set -e
    if (( screenshot_status != 0 || bugreport_status != 0 )); then
      printf '{"bugreportExitCode":%d,"screenshotExitCode":%d}\n' \
        "$bugreport_status" "$screenshot_status" >"$root/$attempt_root/raw/collection-failures.json"
    fi
    break
  fi
done

cleanup
set +e
python3 "$script_dir/write_manifest.py" collect-lane --attempt-root "$attempt_root" --commands "raw/commands.json"
collection_status=$?
if (( collection_status == 0 )); then
  python3 "$script_dir/write_manifest.py" complete \
    --attempt-root "$attempt_root" --commands "raw/commands.json" --cleanup-status "$cleanup_status" --auto-artifacts
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
  write_terminal_receipt "$attempt_root" "API 24 evidence collection or completion failed"
  finalized=1
else
  finalized=1
fi
trap - EXIT
if (( overall_status != 0 )); then
  exit "$overall_status"
fi
exit "$verifier_status"
