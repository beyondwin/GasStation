#!/bin/bash
set -euo pipefail

fail() {
  printf 'mutation runner violation: %s\n' "$1" >&2
  exit 1
}

[[ "${GASSTATION_PITEST_BOOTSTRAP:-}" == "sealed-v1" ]] ||
  fail "use the documented absolute env -i/Bash boundary"

case "$-" in *e*) ;; *) fail "required Bash option is disabled: errexit" ;; esac
case "$-" in *u*) ;; *) fail "required Bash option is disabled: nounset" ;; esac
[[ -o pipefail ]] || fail "required Bash option is disabled: pipefail"

while IFS='=' read -r name _; do
  case "$name" in
    GASSTATION_PITEST_BOOTSTRAP|LANG|LC_ALL|TZ|TERM|CI|PWD|SHLVL|_) ;;
    *) fail "unexpected exported bootstrap name: $name" ;;
  esac
done < <(/usr/bin/env)

[[ "$LANG" == "C" && "$LC_ALL" == "C" && "$TZ" == "UTC" && "$TERM" == "dumb" && "$CI" == "true" ]] ||
  fail "fixed bootstrap literals differ"

event=""
base=""
java_home=""
java_home_file=""
capture_kind=""

if [[ "$#" -ge 4 && "$1" == "--event" ]]; then
  event="$2"
else
  fail "closed argument order must begin with --event EVENT"
fi

case "$event" in
  local-all)
    [[ "$3" == "--java-home" ]] || fail "local-all requires direct --java-home"
    java_home="$4"
    if [[ "$#" -eq 6 ]]; then
      [[ "$5" == "--capture-kind" ]] || fail "local capture argument order differs"
      capture_kind="$6"
      case "$capture_kind" in initial|ordinary|recapture-transition) ;; *) fail "capture kind is invalid" ;; esac
    else
      [[ "$#" -eq 4 ]] || fail "local-all received an extra argument"
    fi
    ;;
  pull-request)
    [[ "$#" -eq 6 && "$3" == "--base" && "$4" =~ ^[0-9a-f]{40}$ && "$5" == "--java-home-file" ]] ||
      fail "pull-request requires exact base and fixed Java selector arguments"
    base="$4"
    java_home_file="$6"
    ;;
  main|tag|schedule)
    [[ "$#" -eq 4 && "$3" == "--java-home-file" ]] ||
      fail "CI event requires the fixed Java selector argument"
    java_home_file="$4"
    ;;
  *) fail "unsupported event" ;;
esac

[[ -z "$capture_kind" || "$event" == "local-all" ]] || fail "capture kind is forbidden in CI"
[[ -z "$java_home_file" || "$java_home_file" == "build/quality/pitest-runtime/bootstrap/java-home.selector" ]] ||
  fail "Java selector path differs from the fixed carrier"

script_dir="$(cd "$(dirname "$0")" && pwd -P)"
repository_root="$(cd "$script_dir/../.." && pwd -P)"
[[ -f "$repository_root/settings.gradle.kts" && -x "$repository_root/gradlew" ]] ||
  fail "runner did not resolve a GasStation repository root"

if [[ -L /usr/bin/python3 ]]; then
  python_path="/usr/bin/python3.12"
else
  python_path="/usr/bin/python3"
fi
[[ -x "$python_path" ]] || fail "canonical policy interpreter is missing"

runtime_root="$repository_root/build/quality/pitest-runtime"
evidence_home="$runtime_root/evidence-home"
evidence_tmp="$runtime_root/evidence-tmp"
runner_home="$runtime_root/runner-home"
runner_tmp="$runtime_root/runner-tmp"
gradle_home="$runtime_root/gradle-user-home"
pit_child_home="$runtime_root/pit-child-home"
pit_child_tmp="$runtime_root/pit-child-tmp"

run_bootstrap_policy() {
  /usr/bin/env -i \
    GASSTATION_PITEST_BOOTSTRAP=sealed-v1 \
    LANG=C LC_ALL=C TZ=UTC TERM=dumb CI=true PYTHONDONTWRITEBYTECODE=1 \
    HOME="$evidence_home" TMPDIR="$evidence_tmp" \
    GIT_ATTR_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 \
    GIT_CONFIG_SYSTEM=/dev/null GIT_NO_REPLACE_OBJECTS=1 GIT_OPTIONAL_LOCKS=0 \
    GIT_PAGER= GIT_TERMINAL_PROMPT=0 \
    "$python_path" -I -S -B "$repository_root/scripts/quality/verify_pitest.py" "$@"
}

if [[ -n "$java_home_file" ]]; then
  java_home="$(run_bootstrap_policy consume-java-selector --path "$java_home_file")"
else
  run_bootstrap_policy bootstrap
fi

[[ "$java_home" == /* && ! -L "$java_home" && -d "$java_home" ]] ||
  fail "Java home must be an absolute non-symlink directory"

for directory in "$runtime_root" "$evidence_home" "$evidence_tmp" "$runner_home" "$runner_tmp" "$gradle_home" "$pit_child_home" "$pit_child_tmp"; do
  [[ ! -L "$directory" ]] || fail "dedicated runtime path is symlinked"
  /bin/mkdir -p "$directory"
  [[ -d "$directory" ]] || fail "dedicated runtime directory is missing"
done
for prohibited in \
  "$runner_home/.gradle/init.gradle" \
  "$runner_home/.gradle/init.gradle.kts" \
  "$runner_home/.gradle/gradle.properties" \
  "$gradle_home/init.gradle" \
  "$gradle_home/init.gradle.kts" \
  "$gradle_home/gradle.properties"; do
  [[ ! -e "$prohibited" && ! -L "$prohibited" ]] ||
    fail "dedicated Gradle home contains prohibited configuration"
done

run_policy() {
  run_bootstrap_policy "$@" --java-home "$java_home"
}

cd "$repository_root"
if [[ -n "$base" ]]; then
  run_policy route --event "$event" --base "$base"
else
  run_policy route --event "$event"
fi
run_policy validate-route

pitest_tasks=()
while IFS= read -r task; do
  [[ -z "$task" || "$task" =~ ^:domain:(station|location|settings):pitestVerified$ ]] ||
    fail "route emitted an unsupported task"
  [[ -z "$task" ]] || pitest_tasks+=("$task")
done < "$repository_root/build/reports/pitest/tasks.txt"

if [[ "${#pitest_tasks[@]}" -eq 0 ]]; then
  [[ -z "$capture_kind" ]] || fail "a capture cannot use a not-applicable route"
  run_policy verify
  run_policy seal-verification
  exit 0
fi

run_policy attempt
/usr/bin/env -i \
  JAVA_HOME="$java_home" \
  PATH="$java_home/bin:/usr/bin:/bin:/usr/sbin:/sbin" \
  LANG=C LC_ALL=C TZ=UTC TERM=dumb CI=true PYTHONDONTWRITEBYTECODE=1 \
  HOME="$runner_home" GRADLE_USER_HOME="$gradle_home" TMPDIR="$runner_tmp" \
  ./gradlew verifyPitestConfiguration "${pitest_tasks[@]}" \
  --configuration-cache \
  --configuration-cache-problems=fail \
  --no-build-cache \
  --rerun-tasks \
  --no-parallel \
  --warning-mode fail
run_policy complete

if [[ -n "$capture_kind" ]]; then
  [[ "$capture_kind" == "initial" ]] || fail "no reviewed successor capture transition is installed"
  run_policy capture
else
  run_policy verify
  run_policy seal-verification
fi
