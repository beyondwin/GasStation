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
  mkdir -p "$target/gradle/wrapper"
  printf 'distributionUrl=https\\://services.gradle.org/distributions/gradle-9.6.1-bin.zip\n' > "$target/gradle/wrapper/gradle-wrapper.properties"
  git -C "$target" add settings.gradle.kts gradlew gradle/wrapper/gradle-wrapper.properties
  git -C "$target" commit -qm "test: seed fixture"
}
