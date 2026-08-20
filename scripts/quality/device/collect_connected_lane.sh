#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
  printf '%s\n' "usage: collect_connected_lane.sh <attempt-root> <adb> <command-status> <commands>" >&2
  exit 2
fi
attempt_root=$1
adb=$2
command_status=$3
commands=$4
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
root=$(cd "$script_dir/../../.." && pwd -P)
relative_attempt=${attempt_root#"$root/"}
collection_status=0
if (( command_status != 0 )); then
  set +e
  "$adb" -s emulator-5554 exec-out screencap -p >"$attempt_root/raw/final-failure.png" 2>/dev/null
  screenshot_status=$?
  "$adb" -s emulator-5554 bugreport "$attempt_root/raw/bugreport.zip" >/dev/null 2>&1
  bugreport_status=$?
  set -e
  if (( screenshot_status != 0 || bugreport_status != 0 )); then
    printf '{"bugreportExitCode":%d,"screenshotExitCode":%d}\n' \
      "$bugreport_status" "$screenshot_status" >"$attempt_root/raw/collection-failures.json"
    collection_status=1
  fi
fi
set +e
python3 "$script_dir/write_manifest.py" collect-lane \
  --attempt-root "$relative_attempt" \
  --commands "$commands"
manifest_status=$?
set -e
if (( manifest_status != 0 || collection_status != 0 )); then
  exit 1
fi
