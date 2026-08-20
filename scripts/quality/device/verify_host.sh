#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
source "$script_dir/common.sh"

lane=
if [[ ${1:-} == --lane && -n ${2:-} && $# -eq 2 ]]; then
  lane=$2
else
  printf '%s\n' "usage: verify_host.sh --lane <lane>" >&2
  exit 2
fi

case "$lane" in
  api24-scheduled|api28-pr-smoke|api28-scheduled|api36-scheduled) ;;
  *) printf '%s\n' "unknown device lane: $lane" >&2; exit 2 ;;
esac

require_device_environment >/dev/null
test -d "$RUNNER_TEMP"
test ! -L "$RUNNER_TEMP"
require_regular_executable "$ANDROID_SDK_ROOT/platform-tools/adb"
device_timeout_command >/dev/null

if [[ $lane == api24-scheduled ]]; then
  [[ $(uname -s) == Linux ]]
  [[ $(uname -m) == x86_64 ]]
  require_regular_executable "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  require_regular_executable "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
  require_regular_executable "$ANDROID_SDK_ROOT/emulator/emulator"
  require_free_emulator_5554 "$ANDROID_SDK_ROOT/platform-tools/adb"
else
  [[ $(uname -s) == Linux ]]
  [[ $(uname -m) == x86_64 ]]
  test -c /dev/kvm
  test -r /dev/kvm
  test -w /dev/kvm
fi

python3 - "$RUNNER_TEMP" <<'PY'
import shutil
import sys

free = shutil.disk_usage(sys.argv[1]).free
if free < 10 * 1024 * 1024 * 1024:
    raise SystemExit("device evidence requires at least 10 GiB free")
PY

printf '%s\n' "device-host: PASS lane=$lane"
