#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_contains() {
  case "$1" in
    *"$2"*) ;;
    *) fail "expected output to contain: $2" ;;
  esac
}

setup_fixture() {
  local target=$1
  mkdir -p \
    "$target/scripts/agent" \
    "$target/core/database/schemas/com.gasstation.core.database.GasStationDatabase"
  cp "$repo_root/scripts/agent/verify-room-schemas.sh" "$target/scripts/agent/verify-room-schemas.sh"
  chmod +x "$target/scripts/agent/verify-room-schemas.sh"
  for version in 1 2 3 4 5; do
    printf '{"database":{"version":%s}}\n' "$version" > \
      "$target/core/database/schemas/com.gasstation.core.database.GasStationDatabase/$version.json"
  done
  printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$*" > gradle-invocation.txt\n' > "$target/gradlew"
  chmod +x "$target/gradlew"
  git -C "$target" init -q
  git -C "$target" config user.email agent-test@example.invalid
  git -C "$target" config user.name 'Agent Test'
  git -C "$target" add .
  git -C "$target" commit -qm 'test: add schema fixture'
}

setup_fixture "$fixture/clean"
"$fixture/clean/scripts/agent/verify-room-schemas.sh" --root "$fixture/clean"
assert_contains "$(cat "$fixture/clean/gradle-invocation.txt")" ":core:database:kspDebugKotlin"
assert_contains "$(cat "$fixture/clean/gradle-invocation.txt")" "--rerun-tasks"
assert_contains "$(cat "$fixture/clean/gradle-invocation.txt")" "--no-build-cache"

setup_fixture "$fixture/modified"
printf 'drift\n' >> \
  "$fixture/modified/core/database/schemas/com.gasstation.core.database.GasStationDatabase/5.json"
if output=$("$fixture/modified/scripts/agent/verify-room-schemas.sh" --root "$fixture/modified" 2>&1); then
  fail 'modified tracked schema was accepted'
fi
assert_contains "$output" "M core/database/schemas/com.gasstation.core.database.GasStationDatabase/5.json"

setup_fixture "$fixture/untracked"
printf '{"database":{"version":6}}\n' > \
  "$fixture/untracked/core/database/schemas/com.gasstation.core.database.GasStationDatabase/6.json"
if output=$("$fixture/untracked/scripts/agent/verify-room-schemas.sh" --root "$fixture/untracked" 2>&1); then
  fail 'untracked next-version schema was accepted'
fi
assert_contains "$output" "?? core/database/schemas/com.gasstation.core.database.GasStationDatabase/6.json"

setup_fixture "$fixture/deleted"
rm "$fixture/deleted/core/database/schemas/com.gasstation.core.database.GasStationDatabase/4.json"
if output=$("$fixture/deleted/scripts/agent/verify-room-schemas.sh" --root "$fixture/deleted" 2>&1); then
  fail 'deleted historical schema was accepted'
fi
assert_contains "$output" "schema history is missing canonical version 4"

setup_fixture "$fixture/missing"
rm -rf "$fixture/missing/core/database/schemas"
if output=$("$fixture/missing/scripts/agent/verify-room-schemas.sh" --root "$fixture/missing" 2>&1); then
  fail 'missing schema directory was accepted'
fi
assert_contains "$output" "schema history is missing"

setup_fixture "$fixture/gradle-failure"
printf '#!/usr/bin/env bash\nexit 7\n' > "$fixture/gradle-failure/gradlew"
chmod +x "$fixture/gradle-failure/gradlew"
if "$fixture/gradle-failure/scripts/agent/verify-room-schemas.sh" \
  --root "$fixture/gradle-failure" >/dev/null 2>&1; then
  fail 'Gradle schema generation failure was accepted'
fi

printf 'verify-room-schemas tests: PASS\n'
