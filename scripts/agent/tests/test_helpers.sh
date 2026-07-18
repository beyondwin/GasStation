#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local haystack=$1
  local needle=$2
  [[ "$haystack" == *"$needle"* ]] || fail "expected output to contain: $needle"
}

assert_not_contains() {
  local haystack=$1
  local needle=$2
  [[ "$haystack" != *"$needle"* ]] || fail "expected output not to contain: $needle"
}

make_git_repo() {
  local target=$1
  mkdir -p "$target"
  git -C "$target" init -q
  git -C "$target" config user.name "Agent Test"
  git -C "$target" config user.email "agent-test@example.invalid"
  printf 'rootProject.name = "Fixture"\ninclude(":app")\n' > "$target/settings.gradle.kts"
  printf '#!/usr/bin/env bash\necho "Gradle 9.6.1"\n' > "$target/gradlew"
  chmod +x "$target/gradlew"
  git -C "$target" add settings.gradle.kts gradlew
  git -C "$target" commit -qm "test: seed fixture"
}
