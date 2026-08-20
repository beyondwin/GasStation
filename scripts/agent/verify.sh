#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
scope=${1:-auto}
shift || true

case "$scope" in
  docs|fast|ui|data|app|release|auto) ;;
  *) echo "unknown scope: $scope" >&2; exit 64 ;;
esac

dry_run=false
changed_files=()
while (($#)); do
  case "$1" in
    --dry-run)
      dry_run=true
      shift
      ;;
    --changed-file)
      if (($# < 2)); then
        echo "missing value for --changed-file" >&2
        exit 64
      fi
      changed_files+=("$2")
      shift 2
      ;;
    *)
      echo "usage: $0 [docs|fast|ui|data|app|release|auto] [--dry-run] [--changed-file PATH]" >&2
      exit 64
      ;;
  esac
done

add_scope() {
  local candidate=$1
  local existing
  for existing in "${scopes[@]:-}"; do
    [[ "$existing" == "$candidate" ]] && return
  done
  scopes+=("$candidate")
}

scopes=()
if [[ "$scope" != auto ]]; then
  add_scope "$scope"
else
  if ((${#changed_files[@]} == 0)); then
    base_commit=
    for candidate in refs/remotes/origin/main refs/heads/main; do
      if git -C "$repo_root" show-ref --verify --quiet "$candidate"; then
        if candidate_base=$(git -C "$repo_root" merge-base "$candidate" HEAD 2>/dev/null); then
          base_commit=$candidate_base
          break
        fi
      fi
    done
    if [[ -z "$base_commit" ]]; then
      echo "verify: auto scope has no usable origin/main or local main merge base; pass an explicit scope" >&2
      exit 65
    fi
    while IFS= read -r file; do
      [[ -n "$file" ]] && changed_files+=("$file")
    done < <(
      {
        git -C "$repo_root" diff --name-only "$base_commit"...HEAD
        git -C "$repo_root" diff --name-only
        git -C "$repo_root" diff --cached --name-only
        git -C "$repo_root" ls-files --others --exclude-standard
      } | LC_ALL=C sort -u
    )
  fi
  for file in "${changed_files[@]}"; do
    case "$file" in
      docs/deployment.md|docs/release-notes/*|CHANGELOG.md)
        add_scope docs
        add_scope release
        ;;
      docs/*|README.md|CONTRIBUTING.md|AGENTS.md|.impeccable.md)
        add_scope docs
        ;;
      config/quality/mutation-*|scripts/quality/*|build-logic/convention/src/main/kotlin/quality/mutation/*|build-logic/convention/src/test/kotlin/quality/GasStationJvmMutationConventionPluginTest.kt)
        add_scope data
        add_scope app
        add_scope release
        ;;
      core/designsystem/*|feature/settings/*|feature/station-list/*|feature/watchlist/*)
        add_scope ui
        ;;
      core/model/*|core/network/*|core/observability/*|core/database/*|core/datastore/*|core/location/*|domain/*|data/*)
        add_scope data
        ;;
      app/build.gradle.kts|app/src/release/*|app/src/prodRelease/*|app/*release*|app/*signing*|app/*version*)
        add_scope app
        add_scope release
        ;;
      .github/workflows/*release*|.github/workflows/*publish*|.github/workflows/*deploy*)
        add_scope app
        add_scope release
        ;;
      app/*|benchmark/*|build-logic/*|gradle/*|build.gradle.kts|settings.gradle.kts|gradle.properties|.github/workflows/*)
        add_scope app
        ;;
      *)
        add_scope fast
        ;;
    esac
  done
  ((${#scopes[@]} > 0)) || add_scope fast
fi

echo "scopes: ${scopes[*]}"

gradle_tasks=()
add_task() {
  local candidate=$1
  local existing
  for existing in "${gradle_tasks[@]:-}"; do
    [[ "$existing" == "$candidate" ]] && return
  done
  gradle_tasks+=("$candidate")
}

for selected in "${scopes[@]}"; do
  case "$selected" in
    docs)
      ;;
    fast)
      for task in :core:model:test :core:network:test :domain:location:test :core:observability:test :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest :app:assembleDemoDebug :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :benchmark:assemble; do add_task "$task"; done
      ;;
    ui)
      for task in :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest verifyRoborazziDebug; do add_task "$task"; done
      ;;
    data)
      for task in :core:model:test :core:network:test :core:observability:test :domain:location:test :domain:settings:test :domain:station:test :core:database:testDebugUnitTest :core:datastore:testDebugUnitTest :core:location:testDebugUnitTest :data:settings:testDebugUnitTest :data:station:testDebugUnitTest verifyModuleBoundaries verifyPitestConfiguration; do add_task "$task"; done
      ;;
    app)
      for task in :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble verifyModuleBoundaries verifyNoDeprecatedComposeTestApis verifyCiRobolectricRuntime verifyPitestConfiguration; do add_task "$task"; done
      ;;
    release)
      for task in spotlessCheck lint :core:model:test :core:network:test :domain:location:test :core:observability:test :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest verifyRoborazziDebug coverageXmlReport verifyCoverageReport :app:assembleProdRelease verifyPitestConfiguration; do add_task "$task"; done
      ;;
  esac
done

if ((${#gradle_tasks[@]} == 0)); then
  if $dry_run; then
    exit 0
  fi
  "$repo_root/scripts/agent/check-contracts.sh"
  if [[ "$scope" == docs && -x "$repo_root/gradlew" ]]; then
    python3 "$repo_root/scripts/quality/build_inputs/docs_gradle_validation_bridge.py" --check-gradle-tasks
  fi
  exit 0
fi

gradle_properties=()
if [[ " ${gradle_tasks[*]} " == *" coverageXmlReport "* ]]; then
  coverage_source_commit=$(git -C "$repo_root" rev-parse --verify 'HEAD^{commit}')
  [[ "$coverage_source_commit" =~ ^[0-9a-f]{40}$ ]] || {
    echo "verify: coverage source commit is not an exact 40-hex SHA" >&2
    exit 65
  }
  gradle_properties+=(
    "-Pgasstation.coverageSourceCommit=$coverage_source_commit"
    "-Pgasstation.coverageEvent=local"
  )
  coverage_base_commit=
  for candidate in refs/remotes/origin/main refs/heads/main; do
    if git -C "$repo_root" show-ref --verify --quiet "$candidate"; then
      if candidate_base=$(git -C "$repo_root" merge-base "$candidate" HEAD 2>/dev/null); then
        coverage_base_commit=$candidate_base
        break
      fi
    fi
  done
  if [[ -n "$coverage_base_commit" && "$coverage_base_commit" != "$coverage_source_commit" ]]; then
    gradle_properties+=("-Pgasstation.coverageBaseRef=$coverage_base_commit")
  fi
fi

printf 'command: ./gradlew'
printf ' %q' "${gradle_tasks[@]}"
printf ' %q' "${gradle_properties[@]}"
printf ' --warning-mode fail\n'
if $dry_run; then
  exit 0
fi
"$repo_root/scripts/agent/check-contracts.sh"
"$repo_root/scripts/agent/preflight.sh" --require-build --hook
cd "$repo_root"
if [[ ${GASSTATION_BUILD_INPUT_EVIDENCE:-} == sealed-v1 ]]; then
  scripts/quality/build_inputs/run_gradle.sh \
    "${gradle_tasks[@]}" "${gradle_properties[@]}" --warning-mode fail
else
  ./gradlew "${gradle_tasks[@]}" "${gradle_properties[@]}" --warning-mode fail
fi
