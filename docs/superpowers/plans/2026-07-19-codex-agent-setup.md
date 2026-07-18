# GasStation Codex Agent Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add portable repository-local agent guidance, preflight and verification tooling, Codex/Claude hooks, and CI enforcement so GasStation agents preserve existing work and finish with reproducible evidence.

**Architecture:** Keep prose policy in a short root contract plus three high-risk nested `AGENTS.md` files. Put deterministic behavior in small Bash/Python-standard-library tools under `scripts/agent/`, wire those tools into trusted project hooks, and run a fast `agent-contracts` CI job before Android compilation. Existing Gradle, Roborazzi, connected-test, coverage, and release tasks remain the execution authority.

**Tech Stack:** Bash 3.2-compatible shell, Python 3.9+ standard library, Git, Gradle 9.6.1, Java 21+, GitHub Actions, Codex `hooks.json`, Markdown.

## Global Constraints

- Design authority: `docs/superpowers/specs/2026-07-19-codex-agent-setup-design.md`.
- Active modules come only from `settings.gradle.kts`; the current count is 18.
- Gradle and Robolectric run on Java 21 or newer; production bytecode remains JVM 17; Android compilation uses compile SDK 37.
- Repository agent scripts and hooks require Python 3.9 or newer and no third-party Python package.
- Do not add Bats, ShellCheck, `jq`, a Python package, a Gradle plugin, an MCP server, or another external dependency.
- Do not pin Codex model, reasoning effort, provider, sandbox, approvals, authentication, telemetry, notification, MCP, or global settings.
- Do not print `local.properties`, keystore, environment-file, or API-key values.
- No script may stash, reset, clean, delete, overwrite local configuration, switch branches, push, tag, release, publish, or deploy.
- Hooks run only fast checks; no hook may run an Android Gradle test, assemble, screenshot, connected-test, coverage, or benchmark task.
- Historical documents under `docs/superpowers/`, `docs/history/`, `docs/improvements/`, `docs/compose-metrics/`, and past release notes are not current-contract inputs.
- Preserve `demo` and `prod` as official paths, price-first UI hierarchy, accessibility semantics, stable test tags, cache snapshot meaning, and module boundaries.
- Every task follows RED/GREEN where executable behavior changes and ends with a focused commit.
- Before execution, run `git status --short`; preserve unrelated user changes and do not create a new worktree if an applicable worktree or progress ledger already exists.

## File Structure

| Path | Responsibility |
| --- | --- |
| `scripts/agent/preflight.sh` | Read-only repository, worktree, toolchain, SDK, ledger, and optional device discovery |
| `scripts/agent/bootstrap-worktree.sh` | Non-overwriting `local.properties` link for an existing linked worktree |
| `scripts/agent/check_contracts.py` | Live-link, toolchain/version/module, portable-path, secret-pattern, and tracked-artifact checks |
| `scripts/agent/check-contracts.sh` | Stable shell entry point for the Python contract checker |
| `scripts/agent/verify.sh` | Explicit verification scopes and conservative changed-file classification |
| `scripts/agent/pre_tool_policy.py` | Codex/Claude-compatible destructive shell-command blocker |
| `scripts/agent/stop_check.py` | Codex Stop JSON adapter for cheap warnings without continuation loops |
| `scripts/agent/test.sh` | Runs all agent-tool regression tests |
| `scripts/agent/tests/test_helpers.sh` | Temporary-repository and assertion helpers |
| `scripts/agent/tests/*_test.sh` | Bash integration tests for preflight, bootstrap, checker, and verification routing |
| `scripts/agent/tests/test_pre_tool_policy.py` | Standard-library unit tests for hook input and deny output |
| `.codex/config.toml` | Enables stable hooks only |
| `.codex/hooks.json` | SessionStart, PreToolUse, and Stop wiring through Git-root-resolved commands |
| `.claude/settings.json` | Claude event adapter that calls the same repository policy scripts |
| `docs/AGENTS.md` | Documentation-local live/history and verification contract |
| `core/database/AGENTS.md` | Database-local schema, migration, snapshot, and fallback contract |
| `benchmark/AGENTS.md` | Benchmark-local physical-device and evidence contract |

---

### Task 1: Read-Only Agent Preflight

**Files:**
- Create: `scripts/agent/test.sh`
- Create: `scripts/agent/tests/test_helpers.sh`
- Create: `scripts/agent/tests/preflight_test.sh`
- Create: `scripts/agent/preflight.sh`

**Interfaces:**
- Consumes: Git repository state, `settings.gradle.kts`, `java`, `gradlew`, `local.properties`, Android SDK environment variables, optional `adb`.
- Produces: `scripts/agent/preflight.sh [--hook] [--require-build] [--device]` with exit `0` for warnings and non-zero only for requested build/device prerequisites.

- [ ] **Step 1: Write the shared test helpers and failing preflight integration test**

Create `scripts/agent/tests/test_helpers.sh` with these exact helpers:

```bash
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
```

Create `scripts/agent/tests/preflight_test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$test_dir/../../.." && pwd)
source "$test_dir/test_helpers.sh"

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
make_git_repo "$fixture/repo"

clean_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$clean_output" "branch:"
assert_contains "$clean_output" "worktree: primary"
assert_contains "$clean_output" "dirty: clean"
assert_contains "$clean_output" "modules: 1"
assert_contains "$clean_output" "python:"

printf 'user change\n' > "$fixture/repo/user-change.txt"
dirty_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$dirty_output" "dirty: changes present"
assert_contains "$dirty_output" "user-change.txt"

git -C "$fixture/repo" checkout -q --detach
detached_output=$(cd "$fixture/repo" && "$repo_root/scripts/agent/preflight.sh" --hook)
assert_contains "$detached_output" "branch: detached"

echo "preflight_test: PASS"
```

Create `scripts/agent/test.sh` initially as:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
"$repo_root/scripts/agent/tests/preflight_test.sh"
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```bash
chmod +x scripts/agent/test.sh scripts/agent/tests/preflight_test.sh
scripts/agent/test.sh
```

Expected: FAIL because `scripts/agent/preflight.sh` does not exist.

- [ ] **Step 3: Implement the minimal read-only preflight**

Create `scripts/agent/preflight.sh` with:

```bash
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
if [[ -f "$progress_file" ]] && grep -qE '(^|[[:space:]])- \[ \]' "$progress_file"; then
  echo "ledger: unfinished .superpowers/sdd/progress.md"
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
```

- [ ] **Step 4: Run GREEN verification**

Run:

```bash
chmod +x scripts/agent/preflight.sh
scripts/agent/test.sh
scripts/agent/preflight.sh
```

Expected: `preflight_test: PASS`; the real-repository command reports `main`, 18 modules, Java 21+, Python 3.9+, configured Android SDK, and the current dirty state without printing `local.properties` contents.

- [ ] **Step 5: Commit Task 1**

```bash
git add scripts/agent/preflight.sh scripts/agent/test.sh scripts/agent/tests/test_helpers.sh scripts/agent/tests/preflight_test.sh
git commit -m "build: add agent preflight"
```

---

### Task 2: Safe Linked-Worktree Bootstrap

**Files:**
- Create: `scripts/agent/tests/bootstrap_worktree_test.sh`
- Create: `scripts/agent/bootstrap-worktree.sh`
- Modify: `scripts/agent/test.sh`

**Interfaces:**
- Consumes: Existing linked worktree plus the first `git worktree list --porcelain` entry as primary worktree.
- Produces: `scripts/agent/bootstrap-worktree.sh`, which creates only a missing `local.properties` symlink and refuses every occupied target.

- [ ] **Step 1: Write the failing bootstrap integration test**

Create `scripts/agent/tests/bootstrap_worktree_test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$test_dir/../../.." && pwd)
source "$test_dir/test_helpers.sh"

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
make_git_repo "$fixture/main"
printf 'sdk.dir=/safe/test/sdk\n' > "$fixture/main/local.properties"
git -C "$fixture/main" worktree add -qb agent-test "$fixture/linked"

(cd "$fixture/linked" && "$repo_root/scripts/agent/bootstrap-worktree.sh")
[[ -L "$fixture/linked/local.properties" ]] || fail "local.properties was not linked"
[[ $(cat "$fixture/linked/local.properties") == 'sdk.dir=/safe/test/sdk' ]] || fail "linked content mismatch"

rm "$fixture/linked/local.properties"
printf 'keep-me\n' > "$fixture/linked/local.properties"
if (cd "$fixture/linked" && "$repo_root/scripts/agent/bootstrap-worktree.sh"); then
  fail "bootstrap overwrote an occupied target"
fi
[[ $(cat "$fixture/linked/local.properties") == 'keep-me' ]] || fail "occupied target changed"

if (cd "$fixture/main" && "$repo_root/scripts/agent/bootstrap-worktree.sh"); then
  fail "bootstrap accepted the primary worktree"
fi

echo "bootstrap_worktree_test: PASS"
```

Append this exact line to `scripts/agent/test.sh`:

```bash
"$repo_root/scripts/agent/tests/bootstrap_worktree_test.sh"
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```bash
chmod +x scripts/agent/tests/bootstrap_worktree_test.sh
scripts/agent/test.sh
```

Expected: preflight test passes, then bootstrap test fails because `bootstrap-worktree.sh` does not exist.

- [ ] **Step 3: Implement the non-overwriting bootstrap**

Create `scripts/agent/bootstrap-worktree.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || {
  echo "bootstrap: not inside a Git repository" >&2
  exit 2
}
git_dir=$(cd "$(git rev-parse --git-dir)" && pwd -P)
git_common=$(cd "$(git rev-parse --git-common-dir)" && pwd -P)
if [[ "$git_dir" == "$git_common" ]]; then
  echo "bootstrap: current checkout is the primary worktree" >&2
  exit 3
fi

primary=$(git worktree list --porcelain | awk '/^worktree / { print substr($0, 10); exit }')
source_file="$primary/local.properties"
target_file="$repo_root/local.properties"

if [[ ! -f "$source_file" ]]; then
  echo "bootstrap: primary worktree has no local.properties" >&2
  exit 4
fi
if [[ -e "$target_file" || -L "$target_file" ]]; then
  echo "bootstrap: refusing to overwrite $target_file" >&2
  exit 5
fi

ln -s "$source_file" "$target_file"
echo "bootstrap: linked local.properties for this worktree"
"$repo_root/scripts/agent/preflight.sh" --hook
```

- [ ] **Step 4: Run GREEN verification**

Run:

```bash
chmod +x scripts/agent/bootstrap-worktree.sh
scripts/agent/test.sh
```

Expected: both `preflight_test: PASS` and `bootstrap_worktree_test: PASS`.

- [ ] **Step 5: Commit Task 2**

```bash
git add scripts/agent/bootstrap-worktree.sh scripts/agent/test.sh scripts/agent/tests/bootstrap_worktree_test.sh
git commit -m "build: bootstrap linked Android worktrees"
```

---

### Task 3: Executable Repository Contract Checker

**Files:**
- Create: `scripts/agent/check_contracts.py`
- Create: `scripts/agent/check-contracts.sh`
- Create: `scripts/agent/tests/check_contracts_test.sh`
- Modify: `scripts/agent/test.sh`

**Interfaces:**
- Consumes: `--root PATH`, optional `--quick`, or the current Git root; tracked files only for secret/artifact checks.
- Produces: deterministic `PASS`/file-and-line failure output and non-zero exit when current contracts drift.

- [ ] **Step 1: Write failing fixture tests for live links, portable paths, module/version drift, and secrets**

Create `scripts/agent/tests/check_contracts_test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$test_dir/../../.." && pwd)
source "$test_dir/test_helpers.sh"

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
make_git_repo "$fixture/repo"
mkdir -p "$fixture/repo/app" "$fixture/repo/docs" "$fixture/repo/.codex"
cat > "$fixture/repo/app/build.gradle.kts" <<'EOF'
android {
    compileSdk = 37
    defaultConfig {
        versionCode = 8
        versionName = "1.2.0"
    }
}
EOF
cat > "$fixture/repo/README.md" <<'EOF'
The fixture ships a 1-module setup.
Current version: `1.2.0` (`versionCode` 8).
[Guide](docs/guide.md)
EOF
printf '# Guide\n' > "$fixture/repo/docs/guide.md"
printf 'Java 21+, Android SDK 37, Python 3.9+.\n' > "$fixture/repo/CONTRIBUTING.md"
printf '[features]\nhooks = true\n' > "$fixture/repo/.codex/config.toml"
git -C "$fixture/repo" add .
git -C "$fixture/repo" commit -qm "test: add contract fixture"

"$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo"

printf '[Broken](docs/missing.md)\n' >> "$fixture/repo/README.md"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/broken.out" 2>&1; then
  fail "broken link was accepted"
fi
assert_contains "$(cat "$fixture/broken.out")" "missing.md"
git -C "$fixture/repo" checkout -q -- README.md

printf 'command = "/Users/example/private/hook.sh"\n' >> "$fixture/repo/.codex/config.toml"
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/path.out" 2>&1; then
  fail "personal path was accepted"
fi
assert_contains "$(cat "$fixture/path.out")" "personal absolute path"
git -C "$fixture/repo" checkout -q -- .codex/config.toml

printf 'opinet.apikey=real-secret\n' > "$fixture/repo/gradle.properties"
git -C "$fixture/repo" add gradle.properties
if "$repo_root/scripts/agent/check-contracts.sh" --root "$fixture/repo" > "$fixture/secret.out" 2>&1; then
  fail "non-empty tracked secret was accepted"
fi
assert_contains "$(cat "$fixture/secret.out")" "non-empty secret assignment"

echo "check_contracts_test: PASS"
```

Append to `scripts/agent/test.sh`:

```bash
"$repo_root/scripts/agent/tests/check_contracts_test.sh"
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```bash
chmod +x scripts/agent/tests/check_contracts_test.sh
scripts/agent/test.sh
```

Expected: earlier tests pass; checker test fails because `check-contracts.sh` is missing.

- [ ] **Step 3: Implement the Python standard-library checker and stable shell wrapper**

Create `scripts/agent/check-contracts.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
exec python3 "$repo_root/scripts/agent/check_contracts.py" "$@"
```

Create `scripts/agent/check_contracts.py` with these public functions and CLI behavior:

```python
#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT_LIVE_MARKDOWN = [
    "AGENTS.md", "README.md", "CONTRIBUTING.md", "CHANGELOG.md", ".impeccable.md",
]
DOC_LIVE_MARKDOWN = [
    "agent-workflow.md", "architecture.md", "build-velocity.md", "deployment.md",
    "module-contracts.md", "offline-strategy.md", "performance.md",
    "project-reading-guide.md", "security-trade-offs.md", "state-model.md",
    "test-strategy.md", "verification-matrix.md",
]
PERSONAL_PATH = re.compile(r"(?:/Users/[^/$\s]+/|/home/[^/$\s]+/|[A-Za-z]:\\\\Users\\\\[^\\\\\s]+\\\\)")
MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")


def tracked_files(root: Path) -> list[Path]:
    output = subprocess.check_output(["git", "-C", str(root), "ls-files", "-co", "--exclude-standard"], text=True)
    return [root / line for line in output.splitlines() if line]


def check_live_links(root: Path) -> list[str]:
    issues: list[str] = []
    files = [root / name for name in ROOT_LIVE_MARKDOWN]
    files += [root / "docs" / name for name in DOC_LIVE_MARKDOWN]
    files += sorted((root / "docs" / "adr").glob("*.md")) if (root / "docs" / "adr").exists() else []
    files += sorted((root / "docs" / "onboarding").glob("*.md")) if (root / "docs" / "onboarding").exists() else []
    for file in files:
        if not file.exists():
            continue
        for line_number, line in enumerate(file.read_text(errors="replace").splitlines(), 1):
            for raw in MARKDOWN_LINK.findall(line):
                target = raw.split("#", 1)[0].strip().strip("<>")
                if not target or target.startswith(("http://", "https://", "mailto:")):
                    continue
                if not (file.parent / target).resolve().exists():
                    issues.append(f"{file.relative_to(root)}:{line_number}: missing link target {target}")
    return issues


def check_build_contract(root: Path) -> list[str]:
    issues: list[str] = []
    settings = (root / "settings.gradle.kts").read_text()
    modules = re.findall(r'"(:[^"]+)"', settings)
    readme = (root / "README.md").read_text() if (root / "README.md").exists() else ""
    app_build = (root / "app" / "build.gradle.kts").read_text() if (root / "app" / "build.gradle.kts").exists() else ""
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', app_build)
    version_code = re.search(r"versionCode\s*=\s*(\d+)", app_build)
    compile_sdk = re.search(r"compileSdk\s*=\s*(\d+)", app_build)
    if not compile_sdk and (root / "gradle" / "libs.versions.toml").exists():
        compile_sdk = re.search(
            r'(?m)^compileSdk\s*=\s*"(\d+)"',
            (root / "gradle" / "libs.versions.toml").read_text(),
        )
    if readme and not re.search(rf"\b{len(modules)}-module\b", readme):
        issues.append(f"README.md: active module count is {len(modules)}")
    module_contract_file = root / "docs" / "module-contracts.md"
    if module_contract_file.exists():
        module_contract = module_contract_file.read_text()
        for module in modules:
            documented = module[1:] if module.startswith(":") else module
            if f"`{documented}`" not in module_contract:
                issues.append(f"docs/module-contracts.md: active module missing: {module}")
    if version_name and version_code and readme:
        expected = f"`{version_name.group(1)}` (`versionCode` {version_code.group(1)})"
        if expected not in readme:
            issues.append(f"README.md: expected current version {expected}")
    contributing = (root / "CONTRIBUTING.md").read_text() if (root / "CONTRIBUTING.md").exists() else ""
    if contributing and "Java 21" not in contributing:
        issues.append("CONTRIBUTING.md: Java 21+ contract missing")
    if contributing and "Python 3.9" not in contributing:
        issues.append("CONTRIBUTING.md: Python 3.9+ agent-tool contract missing")
    if contributing and compile_sdk and f"SDK {compile_sdk.group(1)}" not in contributing:
        issues.append(f"CONTRIBUTING.md: Android SDK {compile_sdk.group(1)} contract missing")
    return issues


def check_portable_agent_paths(root: Path) -> list[str]:
    issues: list[str] = []
    candidates = [path for path in tracked_files(root) if path.is_file() and path.name != "check_contracts.py" and "tests" not in path.parts and (
        ".codex" in path.parts
        or ".claude" in path.parts
        or ("scripts" in path.parts and "agent" in path.parts)
    )]
    for file in candidates:
        for line_number, line in enumerate(file.read_text(errors="replace").splitlines(), 1):
            if PERSONAL_PATH.search(line):
                issues.append(f"{file.relative_to(root)}:{line_number}: personal absolute path")
    return issues


def check_secrets_and_artifacts(root: Path) -> list[str]:
    issues: list[str] = []
    artifact_names = {"local.properties", "keystore.properties"}
    artifact_suffixes = {".jks", ".keystore", ".p12", ".pem", ".hprof", ".log", ".apk", ".aab"}
    for file in tracked_files(root):
        relative = file.relative_to(root)
        if file.name in artifact_names or file.suffix in artifact_suffixes or any(part in {".worktrees", ".superpowers", ".gstack"} for part in relative.parts):
            issues.append(f"{relative}: tracked local/generated artifact")
        if file.name in {"gradle.properties", "keystore.properties"} or file.name.startswith(".env"):
            for line_number, line in enumerate(file.read_text(errors="replace").splitlines(), 1):
                if re.match(r"\s*(?:[A-Za-z0-9_.-]*(?:api.?key|password|secret|token)[A-Za-z0-9_.-]*)\s*=\s*[^\s#<]+", line, re.I):
                    issues.append(f"{relative}:{line_number}: non-empty secret assignment")
    return issues


def check_shell_syntax(root: Path) -> list[str]:
    issues: list[str] = []
    for script in sorted((root / "scripts" / "agent").glob("*.sh")):
        result = subprocess.run(["bash", "-n", str(script)], text=True, capture_output=True)
        if result.returncode:
            issues.append(f"{script.relative_to(root)}: shell syntax error: {result.stderr.strip()}")
    return issues


def check_diff(root: Path) -> list[str]:
    result = subprocess.run(["git", "-C", str(root), "diff", "--check"], text=True, capture_output=True)
    return [result.stdout.strip()] if result.returncode and result.stdout.strip() else []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path)
    parser.add_argument("--quick", action="store_true")
    args = parser.parse_args()
    root = (args.root or Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())).resolve()
    issues = check_portable_agent_paths(root) + check_secrets_and_artifacts(root)
    if not args.quick:
        issues += check_live_links(root) + check_build_contract(root) + check_shell_syntax(root) + check_diff(root)
    if issues:
        for issue in sorted(set(issues)):
            print(f"ERROR: {issue}", file=sys.stderr)
        return 1
    print("agent-contracts: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 4: Run fixture GREEN and record the known repository RED**

Run:

```bash
chmod +x scripts/agent/check-contracts.sh
scripts/agent/test.sh
scripts/agent/check-contracts.sh
```

Expected: `check_contracts_test: PASS`. The real-repository check must fail only on the already-confirmed `CONTRIBUTING.md` Java/SDK drift and `.claude/settings.json` personal absolute hook paths; record those exact failures for Tasks 5 and 6.

- [ ] **Step 5: Commit Task 3**

```bash
git add scripts/agent/check_contracts.py scripts/agent/check-contracts.sh scripts/agent/test.sh scripts/agent/tests/check_contracts_test.sh
git commit -m "build: add agent contract checks"
```

---

### Task 4: Verification Scope Router

**Files:**
- Create: `scripts/agent/tests/verify_test.sh`
- Create: `scripts/agent/verify.sh`
- Modify: `scripts/agent/test.sh`

**Interfaces:**
- Consumes: `docs|fast|ui|data|app|release|auto`, optional `--dry-run`, and repeatable `--changed-file PATH` test/override inputs.
- Produces: printed selected scopes and exact existing Gradle commands; non-dry runs first require `preflight.sh --require-build` except `docs`.

- [ ] **Step 1: Write failing classification tests**

Create `scripts/agent/tests/verify_test.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$test_dir/../../.." && pwd)
source "$test_dir/test_helpers.sh"

docs=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file docs/architecture.md)
assert_contains "$docs" "scopes: docs"
assert_not_contains "$docs" "verifyRoborazziDebug"

ui=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file feature/station-list/src/main/kotlin/Screen.kt)
assert_contains "$ui" "scopes: ui"
assert_contains "$ui" "verifyRoborazziDebug"

data=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file core/database/src/main/kotlin/Db.kt)
assert_contains "$data" "scopes: data"
assert_contains "$data" ":core:database:testDebugUnitTest"

app=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file app/build.gradle.kts)
assert_contains "$app" "scopes: app"
assert_contains "$app" ":app:assembleProdDebug"

release=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file docs/deployment.md)
assert_contains "$release" "scopes: docs release"
assert_contains "$release" ":app:assembleProdRelease"

unknown=$($repo_root/scripts/agent/verify.sh auto --dry-run --changed-file tools/new-path/file.kt)
assert_contains "$unknown" "scopes: fast"

echo "verify_test: PASS"
```

Append to `scripts/agent/test.sh`:

```bash
"$repo_root/scripts/agent/tests/verify_test.sh"
```

- [ ] **Step 2: Run the test to verify RED**

Run `scripts/agent/test.sh`.

Expected: earlier tests pass; verify test fails because `verify.sh` is missing.

- [ ] **Step 3: Implement explicit scopes and conservative auto classification**

Create `scripts/agent/verify.sh`. Use Bash indexed arrays only so it remains compatible with macOS Bash 3.2. The script must implement these exact task sets:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
scope=${1:-auto}
shift || true
dry_run=false
changed_files=()
while (($#)); do
  case "$1" in
    --dry-run) dry_run=true; shift ;;
    --changed-file) changed_files+=("$2"); shift 2 ;;
    *) echo "usage: $0 [docs|fast|ui|data|app|release|auto] [--dry-run] [--changed-file PATH]" >&2; exit 64 ;;
  esac
done

add_scope() {
  local candidate=$1 existing
  for existing in "${scopes[@]:-}"; do [[ "$existing" == "$candidate" ]] && return; done
  scopes+=("$candidate")
}

scopes=()
if [[ "$scope" != auto ]]; then
  add_scope "$scope"
else
  if ((${#changed_files[@]} == 0)); then
    while IFS= read -r file; do [[ -n "$file" ]] && changed_files+=("$file"); done < <(
      { git -C "$repo_root" diff --name-only HEAD; git -C "$repo_root" ls-files --others --exclude-standard; } | sort -u
    )
  fi
  for file in "${changed_files[@]}"; do
    case "$file" in
      docs/deployment.md|docs/release-notes/*|CHANGELOG.md) add_scope docs; add_scope release ;;
      docs/*|README.md|CONTRIBUTING.md|AGENTS.md|.impeccable.md) add_scope docs ;;
      core/designsystem/*|feature/settings/*|feature/station-list/*|feature/watchlist/*) add_scope ui ;;
      core/model/*|core/network/*|core/observability/*|core/database/*|core/datastore/*|core/location/*|domain/*|data/*) add_scope data ;;
      app/*|benchmark/*|build-logic/*|gradle/*|build.gradle.kts|settings.gradle.kts|gradle.properties|.github/workflows/*) add_scope app ;;
      *) add_scope fast ;;
    esac
  done
  ((${#scopes[@]} > 0)) || add_scope fast
fi

echo "scopes: ${scopes[*]}"

gradle_tasks=()
add_task() {
  local candidate=$1 existing
  for existing in "${gradle_tasks[@]:-}"; do [[ "$existing" == "$candidate" ]] && return; done
  gradle_tasks+=("$candidate")
}

for selected in "${scopes[@]}"; do
  case "$selected" in
    docs) ;;
    fast)
      for task in :core:model:test :core:network:test :domain:location:test :core:observability:test :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest :app:assembleDemoDebug :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :benchmark:assemble; do add_task "$task"; done
      ;;
    ui)
      for task in :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest verifyRoborazziDebug; do add_task "$task"; done
      ;;
    data)
      for task in :core:model:test :core:network:test :core:observability:test :domain:location:test :domain:settings:test :domain:station:test :core:database:testDebugUnitTest :core:datastore:testDebugUnitTest :core:location:testDebugUnitTest :data:settings:testDebugUnitTest :data:station:testDebugUnitTest verifyModuleBoundaries; do add_task "$task"; done
      ;;
    app)
      for task in :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble verifyModuleBoundaries verifyNoDeprecatedComposeTestApis verifyCiRobolectricRuntime; do add_task "$task"; done
      ;;
    release)
      for task in spotlessCheck lint :core:model:test :core:network:test :domain:location:test :core:observability:test :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest verifyRoborazziDebug coverageXmlReport :app:assembleProdRelease; do add_task "$task"; done
      ;;
    *) echo "unknown scope: $selected" >&2; exit 64 ;;
  esac
done

if ((${#gradle_tasks[@]} == 0)); then
  if $dry_run; then exit 0; fi
  "$repo_root/scripts/agent/check-contracts.sh"
  exit 0
fi
printf 'command: ./gradlew'
printf ' %q' "${gradle_tasks[@]}"
printf ' --warning-mode fail\n'
if $dry_run; then exit 0; fi
"$repo_root/scripts/agent/check-contracts.sh"
"$repo_root/scripts/agent/preflight.sh" --require-build --hook
cd "$repo_root"
./gradlew "${gradle_tasks[@]}" --warning-mode fail
```

- [ ] **Step 4: Run GREEN classification verification**

Run:

```bash
chmod +x scripts/agent/verify.sh
scripts/agent/test.sh
scripts/agent/verify.sh auto --dry-run --changed-file app/build.gradle.kts
```

Expected: all tests pass; dry run prints `scopes: app` and the app task set without starting Gradle.

- [ ] **Step 5: Commit Task 4**

```bash
git add scripts/agent/verify.sh scripts/agent/test.sh scripts/agent/tests/verify_test.sh
git commit -m "build: route agent verification scopes"
```

---

### Task 5: Portable Codex and Claude Hooks

**Files:**
- Create: `scripts/agent/pre_tool_policy.py`
- Create: `scripts/agent/stop_check.py`
- Create: `scripts/agent/tests/test_pre_tool_policy.py`
- Create: `.codex/config.toml`
- Create: `.codex/hooks.json`
- Modify: `.claude/settings.json`
- Modify: `.gitignore`
- Modify: `scripts/agent/test.sh`

**Interfaces:**
- Consumes: Codex hook JSON on stdin with `tool_name` and `tool_input.command`; Claude JSON from stdin or `CLAUDE_TOOL_INPUT`.
- Produces: official Codex `permissionDecision: deny` JSON, Claude-compatible exit code `2`, and Stop-event JSON that never starts a continuation loop.

- [ ] **Step 1: Write failing standard-library policy tests**

Create `scripts/agent/tests/test_pre_tool_policy.py`:

```python
#!/usr/bin/env python3
import json
import os
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
POLICY = ROOT / "scripts" / "agent" / "pre_tool_policy.py"


class PreToolPolicyTest(unittest.TestCase):
    def run_policy(self, command: str, surface: str = "codex"):
        payload = {"hook_event_name": "PreToolUse", "tool_name": "Bash", "tool_input": {"command": command}}
        env = os.environ.copy()
        if surface == "claude":
            env["GASSTATION_HOOK_SURFACE"] = "claude"
        return subprocess.run([sys.executable, str(POLICY)], input=json.dumps(payload), text=True, capture_output=True, env=env)

    def test_allows_normal_build(self):
        result = self.run_policy("./gradlew :app:testDemoDebugUnitTest")
        self.assertEqual(0, result.returncode)
        self.assertEqual("", result.stdout)

    def test_denies_hard_reset(self):
        result = self.run_policy("git reset --hard HEAD~1")
        self.assertEqual(0, result.returncode)
        output = json.loads(result.stdout)
        self.assertEqual("deny", output["hookSpecificOutput"]["permissionDecision"])

    def test_denies_force_push_to_main(self):
        result = self.run_policy("git push origin main --force")
        self.assertEqual(0, result.returncode)
        self.assertIn("main", json.loads(result.stdout)["hookSpecificOutput"]["permissionDecisionReason"])

    def test_denies_secret_file_print(self):
        result = self.run_policy("cat local.properties")
        self.assertEqual(0, result.returncode)
        self.assertEqual("deny", json.loads(result.stdout)["hookSpecificOutput"]["permissionDecision"])

    def test_claude_denial_uses_exit_two(self):
        result = self.run_policy("git reset --hard HEAD~1", surface="claude")
        self.assertEqual(2, result.returncode)
        self.assertIn("destructive git reset", result.stderr)


if __name__ == "__main__":
    unittest.main()
```

Append to `scripts/agent/test.sh`:

```bash
python3 "$repo_root/scripts/agent/tests/test_pre_tool_policy.py"
```

- [ ] **Step 2: Run the test to verify RED**

Run `scripts/agent/test.sh`.

Expected: shell tests pass; Python tests fail because `pre_tool_policy.py` is missing.

- [ ] **Step 3: Implement command policy and Stop JSON adapter**

Create `scripts/agent/pre_tool_policy.py`:

```python
#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import sys
from typing import Optional

RULES = [
    (re.compile(r"\bgit\s+reset\s+--hard\b", re.I), "destructive git reset is blocked"),
    (re.compile(r"\bgit\s+clean\s+-[^\s]*f", re.I), "destructive git clean is blocked"),
    (re.compile(r"\brm\s+-[^\s]*(?:r[^\s]*f|f[^\s]*r)[^\s]*\s+(?:/|~|\$HOME|\"\$HOME\"|'\$HOME')(?:\s|$)", re.I), "broad recursive deletion is blocked"),
    (re.compile(r"\b(?:psql|mysql|sqlite3)\b[^\n;&|]*\bDROP\s+(?:TABLE|DATABASE|SCHEMA)\b", re.I), "destructive database command is blocked"),
    (re.compile(r"\b(?:cat|head|tail|less|more|sed)\b[^\n;&|]*(?:local\.properties|keystore\.properties|(?:^|/)\.env(?:\.|\s|$))", re.I), "printing local secret files is blocked"),
]


def read_payload() -> dict:
    raw = sys.stdin.read().strip()
    if not raw:
        raw = os.environ.get("CLAUDE_TOOL_INPUT", "")
    if not raw:
        return {}
    try:
        value = json.loads(raw)
        return value if isinstance(value, dict) else {}
    except json.JSONDecodeError:
        return {"tool_input": {"command": raw}}


def denial_reason(command: str) -> Optional[str]:
    force_push = re.search(r"\bgit\s+push\b[^\n;&|]*(?:--force(?:-with-lease)?|-f)\b[^\n;&|]*\b(main|master|trunk)\b", command, re.I)
    reverse_force_push = re.search(r"\bgit\s+push\b[^\n;&|]*\b(main|master|trunk)\b[^\n;&|]*(?:--force(?:-with-lease)?|-f)\b", command, re.I)
    match = force_push or reverse_force_push
    if match:
        return f"force-push to {match.group(1)} is blocked"
    for pattern, reason in RULES:
        if pattern.search(command):
            return reason
    return None


def main() -> int:
    payload = read_payload()
    command = str(payload.get("tool_input", {}).get("command", ""))
    reason = denial_reason(command)
    if not reason:
        return 0
    if os.environ.get("GASSTATION_HOOK_SURFACE") != "claude" and payload.get("hook_event_name") == "PreToolUse":
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }}))
        return 0
    print(reason, file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
```

Create `scripts/agent/stop_check.py`:

```python
#!/usr/bin/env python3
import json
import subprocess
import sys
from pathlib import Path


def main() -> int:
    payload = json.load(sys.stdin)
    if payload.get("stop_hook_active"):
        print("{}")
        return 0
    root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
    check = subprocess.run([str(root / "scripts/agent/check-contracts.sh"), "--quick"], text=True, capture_output=True)
    dirty = subprocess.check_output(["git", "-C", str(root), "status", "--short"], text=True).strip()
    warnings = []
    if check.returncode:
        warnings.append(check.stderr.strip())
    if dirty:
        warnings.append("Working tree has changes; run scripts/agent/verify.sh auto before claiming completion.")
    if warnings:
        print(json.dumps({"continue": True, "systemMessage": "\n".join(warnings)[:2400]}))
    else:
        print("{}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 4: Add trusted-project Codex config, portable hooks, Claude adapters, and ignore allowlist**

Create `.codex/config.toml`:

```toml
[features]
hooks = true
```

Create `.codex/hooks.json`:

```json
{
  "description": "GasStation repository safety and context hooks.",
  "hooks": {
    "SessionStart": [
      {
        "matcher": "startup|resume",
        "hooks": [
          {
            "type": "command",
            "command": "/bin/bash \"$(git rev-parse --show-toplevel)/scripts/agent/preflight.sh\" --hook",
            "timeout": 15,
            "statusMessage": "Checking GasStation workspace state"
          }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "/usr/bin/env python3 \"$(git rev-parse --show-toplevel)/scripts/agent/pre_tool_policy.py\"",
            "timeout": 10,
            "statusMessage": "Checking GasStation command policy"
          }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "/usr/bin/env python3 \"$(git rev-parse --show-toplevel)/scripts/agent/stop_check.py\"",
            "timeout": 15,
            "statusMessage": "Checking GasStation completion evidence"
          }
        ]
      }
    ]
  }
}
```

Replace `.claude/settings.json` with:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "GASSTATION_HOOK_SURFACE=claude /usr/bin/env python3 \"$(git rev-parse --show-toplevel)/scripts/agent/pre_tool_policy.py\""
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "/bin/bash \"$(git rev-parse --show-toplevel)/scripts/agent/check-contracts.sh\" --quick"
          }
        ]
      }
    ],
    "SubagentStop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "/bin/bash \"$(git rev-parse --show-toplevel)/scripts/agent/check-contracts.sh\" --quick"
          }
        ]
      }
    ]
  }
}
```

Replace the `.gitignore` line `.codex/` with:

```gitignore
# Tracked Codex project policy lives in .codex/config.toml and .codex/hooks.json.
.codex/*
!.codex/config.toml
!.codex/hooks.json
```

- [ ] **Step 5: Run GREEN hook and portability verification**

Run:

```bash
chmod +x scripts/agent/pre_tool_policy.py scripts/agent/stop_check.py
scripts/agent/test.sh
python3 -m json.tool .codex/hooks.json >/dev/null
python3 -m json.tool .claude/settings.json >/dev/null
printf '%s' '{"stop_hook_active":true}' | python3 scripts/agent/stop_check.py | grep -Fx '{}'
scripts/agent/check-contracts.sh --quick
rg -n '/Users/|/home/[^$]' .codex .claude scripts/agent --glob '!scripts/agent/check_contracts.py' --glob '!scripts/agent/tests/**' && exit 1 || true
```

Expected: policy tests pass; both JSON files parse; quick checker reports `agent-contracts: PASS`; `rg -n '/Users/|/home/[^$]' .codex .claude scripts/agent` returns no personal path.

- [ ] **Step 6: Commit Task 5**

```bash
git add .codex/config.toml .codex/hooks.json .claude/settings.json .gitignore scripts/agent/pre_tool_policy.py scripts/agent/stop_check.py scripts/agent/test.sh scripts/agent/tests/test_pre_tool_policy.py
git commit -m "build: add portable agent hooks"
```

---

### Task 6: Durable Root and Nested Agent Contracts

**Files:**
- Modify: `AGENTS.md`
- Create: `docs/AGENTS.md`
- Create: `core/database/AGENTS.md`
- Create: `benchmark/AGENTS.md`
- Modify: `CONTRIBUTING.md`
- Modify: `docs/agent-workflow.md`
- Modify: `docs/project-reading-guide.md`

**Interfaces:**
- Consumes: approved design, existing documentation ownership, new script entry points.
- Produces: concise always-on guidance plus local rules that apply only inside docs, database, and benchmark subtrees.

- [ ] **Step 1: Capture the expected contract phrases as a failing check**

Extend `scripts/agent/tests/check_contracts_test.sh` with a real-repository assertion block that runs only when `GASSTATION_CHECK_REAL_REPO=1` and asserts these paths exist:

```bash
if [[ "${GASSTATION_CHECK_REAL_REPO:-0}" == 1 ]]; then
  for required in docs/AGENTS.md core/database/AGENTS.md benchmark/AGENTS.md; do
    [[ -f "$repo_root/$required" ]] || fail "missing nested contract: $required"
  done
  "$repo_root/scripts/agent/check-contracts.sh"
fi
```

Run:

```bash
GASSTATION_CHECK_REAL_REPO=1 scripts/agent/tests/check_contracts_test.sh
```

Expected: FAIL because the nested contracts are missing and `CONTRIBUTING.md` still advertises JDK 17/SDK 35.

- [ ] **Step 2: Add only globally applicable rules to root `AGENTS.md`**

Under `Operating Contract`, add bullets that say:

```markdown
- 판단 우선순위는 실제 코드와 `settings.gradle.kts` -> live 계약 문서 -> `docs/superpowers/`, `docs/history/`, `docs/improvements/` 이력 문서 순서다.
- 중단된 작업을 재개할 때는 새 branch/worktree를 만들기 전에 `git worktree list`, `git status --short`, 관련 diff, 기존 `.superpowers/sdd/progress.md`를 확인한다.
- 비사소한 변경 전 `scripts/agent/preflight.sh`, 완료 주장 전 `scripts/agent/verify.sh auto`를 기본 진입점으로 사용한다.
```

Under `Change Guardrails`, add:

```markdown
- 진단, 리뷰, 설명 요청은 사용자가 구현까지 요청하지 않았다면 읽기 전용 범위로 다룬다.
- Graphify 같은 생성형 분석은 live 문서 -> focused `rg` -> 실제 코드/테스트 추적으로 관계를 확인하기 어려울 때만 사용한다.
- push, PR, tag, release, publish, deploy는 명시된 작업 범위에 포함될 때만 수행한다.
- 완료 보고에는 변경 파일, 실행 명령과 결과, 미검증 영역, local/remote 상태를 포함한다.
```

Do not duplicate the nested database or benchmark details in the root file.

- [ ] **Step 3: Create the three focused nested contracts**

Create `docs/AGENTS.md`:

```markdown
# Documentation Agent Contract

This file supplements the root `AGENTS.md` for changes under `docs/`.

## Scope

- Live contracts are `README.md`, root contributor files, `docs/agent-workflow.md`, `docs/project-reading-guide.md`, `docs/architecture.md`, `docs/module-contracts.md`, `docs/state-model.md`, `docs/offline-strategy.md`, `docs/test-strategy.md`, `docs/verification-matrix.md`, `docs/security-trade-offs.md`, `docs/deployment.md`, `docs/performance.md`, and current ADRs.
- `docs/superpowers/`, `docs/history/`, `docs/improvements/`, `docs/compose-metrics/`, and past release notes are historical evidence unless the task explicitly targets them.

## Authority

- Validate current claims against actual code, `settings.gradle.kts`, Gradle tasks, and `.github/workflows/android.yml`.
- Do not promote an approved plan or historical report to implementation evidence without checking the current repository.

## Verification

- Run `scripts/agent/verify.sh docs` for live-document changes.
- For history-only changes, run `git diff --check -- <changed files>` and inspect only the changed evidence unless a new current claim was added.
- When a document names a file, module, command, version, or CI job, verify that surface exists.

## Do Not

- Do not rewrite old plans, measurements, paths, or completed results merely to match the present repository.
- Do not run unrelated Android suites for history-only wording changes.
```

Create `core/database/AGENTS.md`:

```markdown
# Database Agent Contract

This file supplements the root `AGENTS.md` for `core:database`.

## Schema Contract

- Entity, column, index, or table changes require an explicit Room schema-version decision, exported-schema review, and migration coverage.
- Do not add `fallbackToDestructiveMigration` or another data-loss shortcut unless an approved product decision changes the persistence contract.
- Preserve Android backup restrictions and never inspect or publish user database contents as test evidence.

## Snapshot Contract

- Preserve the distinction between a successful empty snapshot and no cached snapshot.
- `StationSearchResult.hasCachedSnapshot` is the cache-presence meaning; do not replace it with a `fetchedAt != null` shortcut.
- Changes to snapshot replacement or pruning must preserve atomic bucket replacement, current rows, empty snapshot markers, history, and watchlist fallback behavior.

## Required Verification

- Read `StationCacheDaoTest` and `GasStationDatabaseMigrationTest` before changing schema or DAO behavior.
- Run `./gradlew :core:database:testDebugUnitTest` for database changes.
- Add `./gradlew :data:station:testDebugUnitTest` when repository assembly, pruning, cache, or watchlist fallback can change.
- Run `./gradlew verifyModuleBoundaries` when dependencies change.
```

Create `benchmark/AGENTS.md`:

```markdown
# Benchmark Agent Contract

This file supplements the root `AGENTS.md` for `benchmark`.

## Evidence Boundary

- Emulator runs are smoke evidence only. Committed performance numbers require a physical device and the `demoBenchmark` target.
- When multiple devices are connected, set `ANDROID_SERIAL` explicitly before a connected benchmark command.
- A failed, partial, warm-state-only, or emulator run must not replace numbers in `docs/performance.md` or `README.md`.

## Selector Contract

- Preserve the resource-exposed selectors `station-list-watch-toggle`, `bottom-nav-watchlist`, and `watchlist-card`.
- Treat selector failure as a benchmark contract regression before changing production UI copy or semantics.
- `benchmark` consumes the app runtime; it is not an alternate implementation path for product behavior.

## Required Metadata

- Record device model, Android/API version, build variant, measurement date, scenario, and benchmark JSON/trace artifact paths with committed evidence.
- Run `./gradlew :app:assembleDemoBenchmark :benchmark:assembleBenchmark` before connected evidence collection.
- Follow `docs/verification-matrix.md` and `docs/performance.md` for the exact physical-device command and reporting boundary.
```

- [ ] **Step 4: Fix onboarding/toolchain drift and document continuation flow**

Change `CONTRIBUTING.md` setup line to:

```markdown
1. Java 21 이상, Android SDK 37. 앱의 Java/Kotlin bytecode target은 JVM 17입니다.
2. 저장소의 agent script와 Codex/Claude hook은 Python 3.9 이상 표준 라이브러리만 사용합니다.
```

Add this section after `Before Any Change` in `docs/agent-workflow.md`:

```markdown
## Continuation And Worktrees

비사소한 변경은 먼저 `scripts/agent/preflight.sh`로 branch, linked worktree, dirty path, Java, Android SDK, 기존 progress ledger를 확인합니다.

이전 작업을 이어갈 때는 다음 순서를 지킵니다.

1. `git worktree list`에서 이미 만든 작업 공간이 있는지 확인합니다.
2. 대상 worktree의 `git status --short`, 관련 diff, `.superpowers/sdd/progress.md`를 읽습니다.
3. 미커밋 변경과 마지막으로 통과한 검증을 확인한 뒤 같은 작업을 이어갑니다.
4. linked worktree에 `local.properties`만 없다면 `scripts/agent/bootstrap-worktree.sh`를 사용합니다.

기존 변경을 자동 stash/reset/clean하지 않으며, 같은 목적의 branch나 worktree를 중복 생성하지 않습니다.
```

Replace the `에이전트 Fast Path` numbered list in `docs/project-reading-guide.md` with:

```markdown
1. `scripts/agent/preflight.sh`로 branch, worktree, dirty state, toolchain, 기존 ledger를 확인합니다.
2. 루트 `AGENTS.md`와 현재 경로에 더 가까운 중첩 `AGENTS.md`를 읽습니다.
3. `settings.gradle.kts`에서 활성 모듈을 확인합니다.
4. 이 문서의 "변경 목적별 바로 열 파일"과 "질문별 가장 빠른 진입점"에서 목적에 맞는 현재 계약 문서를 고릅니다.
5. 관련 테스트 파일을 먼저 읽고 현재 계약을 확인합니다.
6. `docs/superpowers/`, `docs/history/`, `docs/improvements/`는 사용자가 이력 분석을 요청했거나 현재 판단의 배경이 필요할 때만 근거로 봅니다.
```

- [ ] **Step 5: Run GREEN contract verification**

Run:

```bash
GASSTATION_CHECK_REAL_REPO=1 scripts/agent/tests/check_contracts_test.sh
scripts/agent/check-contracts.sh
git diff --check -- AGENTS.md CONTRIBUTING.md docs/AGENTS.md core/database/AGENTS.md benchmark/AGENTS.md docs/agent-workflow.md docs/project-reading-guide.md
```

Expected: all commands pass; live relative links remain at zero broken targets; checker reports 18 modules, version 1.2.0/code 8, Java 21+, SDK 37, and no personal hook path.

- [ ] **Step 6: Commit Task 6**

```bash
git add AGENTS.md CONTRIBUTING.md docs/AGENTS.md core/database/AGENTS.md benchmark/AGENTS.md docs/agent-workflow.md docs/project-reading-guide.md scripts/agent/tests/check_contracts_test.sh
git commit -m "docs: add repository agent contracts"
```

---

### Task 7: Agent Contracts CI and PR Evidence Template

**Files:**
- Modify: `.github/workflows/android.yml`
- Modify: `.github/PULL_REQUEST_TEMPLATE.md`
- Modify: `docs/verification-matrix.md`

**Interfaces:**
- Consumes: `scripts/agent/test.sh`, `scripts/agent/check-contracts.sh --ci` where `--ci` aliases full checks, existing CI Java version.
- Produces: fast `agent-contracts` job and a PR template that records change class, risk surfaces, commands, skipped checks, and remote status.

- [ ] **Step 1: Add a failing CI-presence assertion**

Extend `scripts/agent/check_contracts.py` `check_build_contract` with:

```python
workflow = (root / ".github" / "workflows" / "android.yml")
if workflow.exists() and not re.search(r"(?m)^  agent-contracts:\s*$", workflow.read_text()):
    issues.append(".github/workflows/android.yml: agent-contracts job missing")
```

Make argparse accept `--ci` as a no-op full-check flag. Run `scripts/agent/check-contracts.sh --ci`.

Expected: FAIL with `agent-contracts job missing`.

- [ ] **Step 2: Add the fast CI job before Android compilation jobs**

Insert this job before `static-analysis` in `.github/workflows/android.yml`:

```yaml
  agent-contracts:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: ${{ env.CI_JAVA_VERSION }}
      - name: Agent contract tests
        run: |
          scripts/agent/test.sh
          scripts/agent/check-contracts.sh --ci
```

Do not add `needs` edges from every Android job; GitHub can run the fast job in parallel while still surfacing its independent failure clearly.

- [ ] **Step 3: Replace the PR template with evidence-oriented sections**

Replace `.github/PULL_REQUEST_TEMPLATE.md` with:

````markdown
## 요약

<!-- 변경 목적과 사용자/개발자 영향을 적습니다. -->

## 변경 범위

- 영향 모듈:
- [ ] 사용자 노출 문자열 변경 및 `strings.xml` 반영
- [ ] 새 의존성 및 version catalog 반영
- [ ] 새 모듈 또는 모듈 경계 변경
- [ ] `demo` 경로 영향 확인
- [ ] `prod` 경로 영향 확인
- [ ] DB schema/migration 변경
- [ ] UI semantics/accessibility/test tag 변경
- [ ] Roborazzi snapshot 변경 및 생성 이미지 직접 확인

## 검증 증거

```bash
# 실제 실행한 scripts/agent/verify.sh scope와 추가 명령
```

실행하지 못한 검증과 이유:

## 문서

- [ ] live 계약 문서 갱신
- [ ] `CHANGELOG.md` 또는 release note 갱신
- [ ] 이력 문서만 변경
- [ ] 해당 없음

## 스크린샷 / 영상

<!-- UI 변경 시 첨부하고 확인한 viewport/font scale을 적습니다. -->

## 원격 상태

- push: 수행하지 않음 / 수행한 branch
- PR: 생성하지 않음 / URL
- release/tag/deploy: 수행하지 않음 / 수행 내역
````

- [ ] **Step 4: Document CI and verification entry points**

In `docs/verification-matrix.md`:

- add `scripts/agent/check-contracts.sh` for live contract documents;
- add the scope table for `verify.sh docs|fast|ui|data|app|release|auto`;
- add `agent-contracts` to every trigger row because the job runs on all current workflow triggers;
- state that hooks never run Gradle and that `verify.sh` is the explicit heavy-work owner.

Add this scope table near the existing quick-local section:

```markdown
| Scope | 실행 범위 |
| --- | --- |
| `docs` | live 문서 링크, 경로, toolchain/version/module 계약 |
| `fast` | 가벼운 host-side 회귀와 demo assemble |
| `ui` | designsystem/feature UI test와 Roborazzi |
| `data` | model/domain/data/database 회귀와 module boundary |
| `app` | demo/prod app test와 debug assemble, benchmark assemble |
| `release` | 기존 머지 전 회귀와 prod release assemble |
| `auto` | changed path를 보수적으로 위 scope에 매핑 |
```

Add this boundary sentence verbatim:

```markdown
Codex/Claude hook은 Gradle을 실행하지 않습니다. 무거운 테스트와 assemble은 명시적인 `scripts/agent/verify.sh <scope>` 호출이 소유합니다.
```

- [ ] **Step 5: Run GREEN CI/document verification**

Run:

```bash
scripts/agent/test.sh
scripts/agent/check-contracts.sh --ci
python3 - <<'PY'
from pathlib import Path
text = Path('.github/workflows/android.yml').read_text()
assert text.count('  agent-contracts:') == 1
assert 'scripts/agent/test.sh' in text
assert 'scripts/agent/check-contracts.sh --ci' in text
print('workflow agent-contracts: PASS')
PY
git diff --check -- .github/workflows/android.yml .github/PULL_REQUEST_TEMPLATE.md docs/verification-matrix.md
```

Expected: all commands pass and existing Android job command blocks remain unchanged.

- [ ] **Step 6: Commit Task 7**

```bash
git add .github/workflows/android.yml .github/PULL_REQUEST_TEMPLATE.md docs/verification-matrix.md scripts/agent/check_contracts.py
git commit -m "ci: verify repository agent contracts"
```

---

### Task 8: Full Setup Verification and Documentation Closeout

**Files:**
- Modify if verification finds drift: `README.md`
- Modify if verification finds drift: `docs/test-strategy.md`
- Modify if verification finds drift: `docs/verification-matrix.md`
- Verify: every file created or modified in Tasks 1-7

**Interfaces:**
- Consumes: complete agent setup and current Android verification contracts.
- Produces: a clean, reviewed branch whose hooks are portable, scripts are tested, docs are current, and existing Gradle guards pass.

- [ ] **Step 1: Run the complete agent-tool regression suite**

Run:

```bash
scripts/agent/test.sh
scripts/agent/check-contracts.sh --ci
scripts/agent/preflight.sh
scripts/agent/verify.sh auto --dry-run
```

Expected: all tests and checks pass; preflight reports the actual branch/worktree/dirty state; dry-run prints the conservative scopes implied by the implementation diff.

- [ ] **Step 2: Verify hook schemas, outputs, and trust-safe paths**

Run:

```bash
python3 -m json.tool .codex/hooks.json >/dev/null
python3 -m json.tool .claude/settings.json >/dev/null
printf '%s' '{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"git reset --hard HEAD~1"}}' | python3 scripts/agent/pre_tool_policy.py
printf '%s' '{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"./gradlew :app:testDemoDebugUnitTest"}}' | python3 scripts/agent/pre_tool_policy.py
rg -n '/Users/[^$]|/home/[^$]|[A-Za-z]:\\Users\\' .codex .claude scripts/agent --glob '!scripts/agent/check_contracts.py' --glob '!scripts/agent/tests/**' && exit 1 || true
```

Expected: first policy invocation returns Codex deny JSON; second prints nothing and exits zero; no personal path is found.

- [ ] **Step 3: Run existing Gradle contract gates**

Run:

```bash
./gradlew verifyModuleBoundaries verifyNoDeprecatedComposeTestApis verifyCiRobolectricRuntime --warning-mode fail
```

Expected: `BUILD SUCCESSFUL`; module boundary, Compose test API, and CI/Robolectric runtime guards all report success.

- [ ] **Step 4: Run final diff and secret/artifact review**

Run:

```bash
git diff --check
git status --short
git diff --stat origin/main...HEAD
git diff origin/main...HEAD -- . ':!docs/superpowers/specs/2026-07-19-codex-agent-setup-design.md' ':!docs/superpowers/plans/2026-07-19-codex-agent-setup.md'
git ls-files local.properties '*.jks' '*.keystore' '*.p12' '*.pem' '*.log' '*.apk' '*.aab' .superpowers .worktrees .gstack
```

Expected: diff check passes; artifact command prints nothing; review confirms no automatic push/release behavior, no heavy hook commands, and no unrelated Android production changes.

- [ ] **Step 5: Run the proportional final verification scope**

Because this change modifies CI, Gradle-adjacent tooling, root contracts, and repository hooks, run:

```bash
scripts/agent/verify.sh app
```

Expected: contract checker and build preflight pass, followed by demo/prod unit tests, demo/prod debug assemble, benchmark assemble, and the three existing root contract guards. If the final diff includes only shell/Python/docs/CI files and Task 3's Gradle guards already prove configuration, document why `release` was not run; do not claim release verification.

- [ ] **Step 6: Commit closeout-only corrections if needed**

If Steps 1-5 require documentation or script corrections, apply them and commit only those corrections:

```bash
git add README.md docs/test-strategy.md docs/verification-matrix.md scripts/agent .codex .claude .github AGENTS.md CONTRIBUTING.md core/database/AGENTS.md benchmark/AGENTS.md docs/AGENTS.md docs/agent-workflow.md docs/project-reading-guide.md
git commit -m "docs: close agent setup verification gaps"
```

If no corrections are needed, do not create an empty commit.

- [ ] **Step 7: Produce the final implementation report**

The report must include:

```text
Implemented: root/nested contracts, preflight, worktree bootstrap, contract checker, verification router, Codex/Claude hooks, CI, PR template, live docs.
Verification: list every command actually run and PASS/FAIL result.
Unverified: explicitly name connected-device, physical benchmark, release, push, PR, tag, and deploy status.
Git: branch, final commits, clean/dirty status, origin/main comparison, and whether any remote action occurred.
Hook activation: project hooks require trust review in Codex after first add or hash change.
```

Do not report completion until the working tree is clean or every remaining path is identified as pre-existing user work.
