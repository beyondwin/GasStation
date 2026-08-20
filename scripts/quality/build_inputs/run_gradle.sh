#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf 'governed Gradle launcher violation: %s\n' "$1" >&2
  exit 65
}

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd -P)
: "${JAVA_HOME_17_X64:?JAVA_HOME_17_X64 is required}"
: "${JAVA_HOME_21_X64:?JAVA_HOME_21_X64 is required}"
: "${GRADLE_USER_HOME:?GRADLE_USER_HOME is required}"

[[ ${JAVA_HOME:-} == "$JAVA_HOME_21_X64" ]] || fail "JAVA_HOME must select the verified runtime role"
[[ ${PATH%%:*} == "$JAVA_HOME_21_X64/bin" ]] || fail "PATH must start with the verified runtime bin"
for name in GRADLE_OPTS JAVA_OPTS JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; do
  [[ -z ${!name:-} ]] || fail "$name must be absent"
done
while IFS='=' read -r name _; do
  [[ $name != ORG_GRADLE_PROJECT_* ]] || fail "$name must be absent"
done < <(/usr/bin/env)

for argument in "$@"; do
  case "$argument" in
    -[I]|--init-[s]cript|-[Ii]?*|--init-[s]cript=*|--dependency-verification|--dependency-verification=*|--write-verification-metadata|--write-verification-metadata=*|-Dorg.gradle.dependency.verification=*)
      fail "caller supplied a policy-owned Gradle argument: $argument"
      ;;
  esac
done

exec "$root/gradlew" "$@" \
  --dependency-verification strict \
  -Dorg.gradle.java.installations.auto-detect=false \
  -Dorg.gradle.java.installations.auto-download=false \
  "-Dorg.gradle.java.installations.paths=$JAVA_HOME_17_X64,$JAVA_HOME_21_X64"
