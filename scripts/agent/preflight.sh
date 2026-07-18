#!/usr/bin/env bash
set -u

hook_mode=false
require_build=false
device_mode=false
for argument in "$@"; do
  case "$argument" in
    --hook) hook_mode=true ;;
    --require-build) require_build=true ;;
    --device) device_mode=true ;;
    *) echo "usage: $0 [--hook] [--require-build] [--device]" >&2; exit 64 ;;
  esac
done

repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || {
  echo "preflight: not inside a Git repository" >&2
  exit 2
}

git_dir=$(cd "$(git rev-parse --git-dir)" 2>/dev/null && pwd -P)
git_common=$(cd "$(git rev-parse --git-common-dir)" 2>/dev/null && pwd -P)
branch=$(git branch --show-current)
head_commit=$(git rev-parse --short HEAD)
dirty=$(git status --short)
module_count=$(grep -Eo '"(:[^"]+)"' "$repo_root/settings.gradle.kts" 2>/dev/null | wc -l | tr -d ' ')

if [[ -z "$branch" ]]; then branch=detached; fi
if [[ "$git_dir" == "$git_common" ]]; then worktree_kind=primary; else worktree_kind=linked; fi

echo "repo: $repo_root"
echo "head: $head_commit"
echo "branch: $branch"
echo "worktree: $worktree_kind"
echo "modules: ${module_count:-0}"
if [[ -z "$dirty" ]]; then
  echo "dirty: clean"
else
  echo "dirty: changes present"
  printf '%s\n' "$dirty" | sed -n '1,20p'
fi

java_line=$(java -version 2>&1 | sed -n '1p')
java_major=$(printf '%s' "$java_line" | sed -E 's/.*version "([0-9]+).*/\1/')
java_ok=true
if [[ ! "$java_major" =~ ^[0-9]+$ ]] || (( java_major < 21 )); then java_ok=false; fi
echo "java: ${java_line:-missing}"

python_line=$(python3 --version 2>&1)
python_version=$(printf '%s' "$python_line" | sed -E 's/.* ([0-9]+)\.([0-9]+).*/\1 \2/')
python_major=${python_version%% *}
python_minor=${python_version##* }
python_ok=true
if [[ ! "$python_major" =~ ^[0-9]+$ ]] || [[ ! "$python_minor" =~ ^[0-9]+$ ]] || (( python_major < 3 || (python_major == 3 && python_minor < 9) )); then python_ok=false; fi
echo "python: ${python_line:-missing}"

gradle_ok=false
if [[ -x "$repo_root/gradlew" ]]; then
  gradle_ok=true
  echo "gradle-wrapper: present"
else
  echo "gradle-wrapper: missing"
fi

sdk_ok=false
if [[ -f "$repo_root/local.properties" || -n "${ANDROID_HOME:-}" || -n "${ANDROID_SDK_ROOT:-}" ]]; then
  sdk_ok=true
  echo "android-sdk: configured"
else
  echo "android-sdk: missing"
  if [[ "$worktree_kind" == linked ]]; then
    echo "hint: $repo_root/scripts/agent/bootstrap-worktree.sh"
  fi
fi

progress_file="$repo_root/.superpowers/sdd/progress.md"
if [[ -f "$progress_file" ]]; then
  completed_count=$(grep -Ec '^Task[[:space:]]+[0-9]+:[[:space:]]+complete([[:space:]]|$)' "$progress_file" || true)
  pending=no
  if grep -qE '(^|[[:space:]])-?[[:space:]]*\[[[:space:]]\]|^Task[[:space:]]+[0-9]+:[[:space:]]+(pending|in[_ -]?progress|blocked|unfinished)([[:space:]]|$)' "$progress_file"; then
    pending=yes
  fi
  echo "ledger: .superpowers/sdd/progress.md exists completed=$completed_count pending=$pending"
else
  echo "ledger: none detected"
fi

if $device_mode; then
  if command -v adb >/dev/null 2>&1; then
    device_count=$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')
    echo "devices: $device_count"
    if (( device_count == 0 )); then exit 5; fi
    if (( device_count > 1 )) && [[ -z "${ANDROID_SERIAL:-}" ]]; then
      echo "preflight: multiple devices require ANDROID_SERIAL" >&2
      exit 6
    fi
  else
    echo "preflight: adb is unavailable" >&2
    exit 5
  fi
fi

if $require_build && { ! $java_ok || ! $python_ok || ! $gradle_ok || ! $sdk_ok; }; then
  echo "preflight: build prerequisites are incomplete" >&2
  exit 4
fi

if ! $hook_mode; then
  echo "worktrees:"
  git worktree list
fi
