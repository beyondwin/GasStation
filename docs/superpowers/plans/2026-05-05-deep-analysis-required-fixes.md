# Deep Analysis Required Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement only the fixes that the deep analysis review judged necessary now: `proj4j` catalog registration, CI matrix alignment, Main dispatcher test rule cleanup, and watchlist test tag separation.

**Architecture:** Keep every change inside its owner boundary. Build dependency coordinates stay in the Gradle version catalog and the owning module build file. CI changes stay in `.github/workflows/android.yml` and mirror `docs/verification-matrix.md`. Feature test infrastructure stays in the owning feature test source set. Watchlist semantics remain feature-owned because they are screen selector/accessibility contracts.

**Tech Stack:** Kotlin, Gradle version catalog, GitHub Actions, JUnit4, kotlinx-coroutines-test, Jetpack Compose UI tests, Android Gradle Plugin.

---

## Source Of Truth

- Start with `AGENTS.md`, `docs/agent-workflow.md`, `docs/module-contracts.md`, and `docs/verification-matrix.md`.
- Use `settings.gradle.kts` as the active module list. Do not infer active modules from leftover directories.
- Treat `docs/improvement-analysis.md` as the backlog source and `docs/deep-analysis-report.md` as the necessity review.
- Do not implement conditional items from the deep analysis report unless the user explicitly promotes them.

## Out Of Scope

Do not implement these in this pass:

- `core/common` or `core/ui` deletion commits. They are untracked build output only when `git ls-files core/common core/ui` is empty.
- `StationListUiStateReducer` extraction.
- Navigation transition helper extraction.
- Hilt `Optional<T>` nullable refactor.
- `distanceBetween` or Geocoder caching.
- Gson replacement.
- Backend proxy.
- Full dark mode semantic color migration.
- String resource migration.
- `historyForWatchlistContext` cleanup by itself.

## File Structure

- Modify: `gradle/libs.versions.toml`
  Owns all externally versioned Gradle coordinates.
- Modify: `core/network/build.gradle.kts`
  Owns `core:network` dependencies, including `proj4j` for KATEC coordinate conversion.
- Modify: `.github/workflows/android.yml`
  Owns GitHub Actions CI matrix.
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/MainDispatcherRule.kt`
  Owns station-list JVM test Main dispatcher setup.
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
  Removes repeated Main dispatcher setup/teardown.
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSemantics.kt`
  Separates accessibility copy from test selectors.
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
  Uses the new card test tag.
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`
  Reads selectors from the new ASCII test tag constants.
- Modify after code lands: `docs/improvement-analysis.md` and `docs/deep-analysis-report.md`
  Update status only for tasks actually completed and verified.

## Task 0: Preflight

**Files:**
- Read: `AGENTS.md`
- Read: `docs/agent-workflow.md`
- Read: `docs/module-contracts.md`
- Read: `docs/verification-matrix.md`
- Read: `docs/deep-analysis-report.md`

- [x] **Step 1: Confirm the existing worktree state**

Run:

```bash
git status --short
```

Expected:
- Existing user edits are visible before patches start.
- If `docs/deep-analysis-report.md` is still untracked, preserve it and do not overwrite it.

- [x] **Step 2: Confirm active modules**

Run:

```bash
sed -n '1,120p' settings.gradle.kts
```

Expected:
- `:core:network`, `:feature:station-list`, `:feature:watchlist`, `:tools:demo-seed`, and `:benchmark` are included.
- `:core:common` and `:core:ui` are not included.

- [x] **Step 3: Confirm the work is still necessary**

Run:

```bash
rg -n 'org\.locationtech\.proj4j:proj4j|proj4j =' gradle/libs.versions.toml core/network/build.gradle.kts
rg -n ':domain:location:test|:tools:demo-seed:test' .github/workflows/android.yml
rg -n 'Dispatchers\.setMain|Dispatchers\.resetMain|setMain|resetMain' feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt
rg -n 'WATCHLIST_CARD_CONTENT_DESCRIPTION|WATCHLIST_DISTANCE_METRIC_TAG|onNodeWithTag|testTag' feature/watchlist/src/main/kotlin feature/watchlist/src/test/kotlin
```

Expected at baseline:
- `core/network/build.gradle.kts` still contains `implementation("org.locationtech.proj4j:proj4j:1.4.1")`.
- `.github/workflows/android.yml` does not contain both `:domain:location:test` and `:tools:demo-seed:test`.
- `StationListViewModelTest.kt` still contains repeated `Dispatchers.setMain` and `Dispatchers.resetMain`.
- `WATCHLIST_DISTANCE_METRIC_TAG` is still Korean or the card tag still reuses content description.

## Task 1: Register `proj4j` In Version Catalog

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/network/build.gradle.kts`

- [x] **Step 1: Add the version catalog entries**

Modify `gradle/libs.versions.toml` so the relevant sections contain these entries:

```toml
[versions]
playServicesLocation = "21.3.0"
proj4j = "1.4.1"
retrofit = "3.0.0"
```

```toml
[libraries]
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "playServicesLocation" }
proj4j = { module = "org.locationtech.proj4j:proj4j", version.ref = "proj4j" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
```

- [x] **Step 2: Replace the hardcoded dependency**

Modify `core/network/build.gradle.kts` so the final dependency block is:

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.proj4j)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
```

- [x] **Step 3: Confirm no direct coordinate remains**

Run:

```bash
rg -n 'org\.locationtech\.proj4j:proj4j' core/network/build.gradle.kts gradle/libs.versions.toml
```

Expected:
- Only `gradle/libs.versions.toml` contains the coordinate.

- [x] **Step 4: Verify `core:network`**

Run:

```bash
./gradlew :core:network:test
```

Expected:
- `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit this task**

Run:

```bash
git add gradle/libs.versions.toml core/network/build.gradle.kts
git commit -m "chore: catalog proj4j dependency"
```

Expected:
- Commit succeeds.

## Task 2: Align CI With Verification Matrix

**Files:**
- Modify: `.github/workflows/android.yml`

- [x] **Step 1: Add the missing matrix tasks**

Modify `.github/workflows/android.yml` so the `Verification Matrix` command is:

```yaml
      - name: Verification Matrix
        run: |
          JAVA_HOME="${JAVA_HOME}" PATH="${JAVA_HOME}/bin:${PATH}" ./gradlew \
            :domain:location:test \
            :core:model:test \
            :domain:station:test \
            :domain:settings:test \
            :core:database:testDebugUnitTest \
            :core:datastore:testDebugUnitTest \
            :core:designsystem:testDebugUnitTest \
            :core:location:testDebugUnitTest \
            :core:network:test \
            :data:settings:testDebugUnitTest \
            :data:station:testDebugUnitTest \
            :feature:settings:testDebugUnitTest \
            :feature:station-list:testDebugUnitTest \
            :feature:watchlist:testDebugUnitTest \
            :app:testDemoDebugUnitTest \
            :app:testProdDebugUnitTest \
            :tools:demo-seed:test \
            :app:assembleDemoDebug \
            :app:assembleProdDebug \
            :benchmark:assemble
```

Do not add `:app:assembleDemoRelease` or `:app:assembleProdRelease` in this task. Those are conditional CI-cost decisions.

- [x] **Step 2: Verify YAML contains both missing tasks**

Run:

```bash
rg -n ':domain:location:test|:tools:demo-seed:test' .github/workflows/android.yml
```

Expected:
- Both task names are printed.

- [x] **Step 3: Run the same CI command locally**

Run:

```bash
JAVA_HOME="${JAVA_HOME}" PATH="${JAVA_HOME}/bin:${PATH}" ./gradlew \
  :domain:location:test \
  :core:model:test \
  :domain:station:test \
  :domain:settings:test \
  :core:database:testDebugUnitTest \
  :core:datastore:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :core:location:testDebugUnitTest \
  :core:network:test \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :tools:demo-seed:test \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

Expected:
- `BUILD SUCCESSFUL`.

- [x] **Step 4: Commit this task**

Run:

```bash
git add .github/workflows/android.yml
git commit -m "ci: align Android verification matrix"
```

Expected:
- Commit succeeds.

## Task 3: Introduce `MainDispatcherRule` For Station List ViewModel Tests

**Files:**
- Create: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/MainDispatcherRule.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

- [x] **Step 1: Create the dispatcher rule**

Create `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/MainDispatcherRule.kt`:

```kotlin
package com.gasstation.feature.stationlist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

- [x] **Step 2: Update imports in `StationListViewModelTest.kt`**

Remove these imports:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
```

Add:

```kotlin
import org.junit.Rule
```

- [x] **Step 3: Replace the dispatcher field**

Replace:

```kotlin
    private val dispatcher = StandardTestDispatcher()
```

with:

```kotlin
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatcher = mainDispatcherRule.dispatcher
```

- [x] **Step 4: Remove repeated setup and teardown from each test**

For each test in `StationListViewModelTest.kt`, remove this setup call:

```kotlin
        Dispatchers.setMain(dispatcher)
```

Remove each teardown block:

```kotlin
        } finally {
            Dispatchers.resetMain()
        }
```

After unwrapping, the first test must start like this:

```kotlin
    @Test
    fun `refresh with precise location builds query without map provider`() = runTest(dispatcher) {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry()),
                freshness = StationFreshness.Stale,
                fetchedAt = null,
            ),
        )
        val settingsFixture = SettingsUseCaseTestFixture(UserPreferences.default())
        val viewModel = stationListViewModel(
            repository = repository,
            settingsFixture = settingsFixture,
            locationRepository = FakeLocationRepository(
                result = LocationLookupResult.Success(Coordinates(37.498095, 127.027610)),
            ),
        )
    }
```

Keep all remaining statements from that test after the shown setup block in their current order. Apply the same unwrapping rule to every test in the file.

- [x] **Step 5: Confirm no repeated Main dispatcher calls remain**

Run:

```bash
rg -n 'Dispatchers\.setMain|Dispatchers\.resetMain|setMain|resetMain' feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt
```

Expected:
- No matches in `StationListViewModelTest.kt`.
- Matches may remain in `MainDispatcherRule.kt`.

- [x] **Step 6: Verify station-list tests**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest
```

Expected:
- `BUILD SUCCESSFUL`.

- [x] **Step 7: Commit this task**

Run:

```bash
git add feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/MainDispatcherRule.kt feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt
git commit -m "test: centralize station list main dispatcher"
```

Expected:
- Commit succeeds.

## Task 4: Separate Watchlist Accessibility Copy From Test Tags

**Files:**
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSemantics.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- Modify: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`

- [x] **Step 1: Update watchlist semantics constants**

Replace `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSemantics.kt` with:

```kotlin
package com.gasstation.feature.watchlist

const val WATCHLIST_CARD_CONTENT_DESCRIPTION = "관심 주유소 카드"
const val WATCHLIST_CARD_TEST_TAG = "watchlist-card"
const val WATCHLIST_DISTANCE_METRIC_TAG = "watchlist-distance-metric"
```

- [x] **Step 2: Use the card test tag in the screen**

In `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`, replace:

```kotlin
                                    .testTag(WATCHLIST_CARD_CONTENT_DESCRIPTION),
```

with:

```kotlin
                                    .testTag(WATCHLIST_CARD_TEST_TAG),
```

Keep the existing accessibility copy where it is used for content descriptions. Do not replace content descriptions with ASCII test tags.

- [x] **Step 3: Update screen tests**

In `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt`, replace `onNodeWithTag` calls that use `WATCHLIST_CARD_CONTENT_DESCRIPTION` with `WATCHLIST_CARD_TEST_TAG`.

Specifically, replace these two references:

```kotlin
WATCHLIST_CARD_CONTENT_DESCRIPTION
```

with:

```kotlin
WATCHLIST_CARD_TEST_TAG
```

only where they are passed to `onNodeWithTag`. Keep `WATCHLIST_CARD_CONTENT_DESCRIPTION` for content-description assertions if such assertions are added in a later task.

- [x] **Step 4: Confirm no Korean test tag remains**

Run:

```bash
rg -n 'testTag\(.*[가-힣]|WATCHLIST_DISTANCE_METRIC_TAG = "[가-힣]|onNodeWithTag\(WATCHLIST_CARD_CONTENT_DESCRIPTION' feature/watchlist/src/main/kotlin feature/watchlist/src/test/kotlin
```

Expected:
- No matches.

- [x] **Step 5: Verify watchlist tests**

Run:

```bash
./gradlew :feature:watchlist:testDebugUnitTest
```

Expected:
- `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit this task**

Run:

```bash
git add feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSemantics.kt feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistScreenTest.kt
git commit -m "test: separate watchlist selectors from copy"
```

Expected:
- Commit succeeds.

## Task 5: Update Status Documentation

**Files:**
- Modify: `docs/improvement-analysis.md`
- Modify: `docs/deep-analysis-report.md`

- [x] **Step 1: Update `docs/improvement-analysis.md` for completed backlog items**

If Task 1 completed, update item `2-2. proj4j 버전 카탈로그 미등록` to `[완료됨]` and add a short implementation note:

```markdown
`proj4j`는 `gradle/libs.versions.toml`의 `proj4j` version/library alias로 이동했고, `core/network/build.gradle.kts`는 `implementation(libs.proj4j)`를 사용합니다.
```

If Task 2 completed, add a CI note under build hygiene or the relevant verification section:

```markdown
GitHub Actions `Verification Matrix`는 `docs/verification-matrix.md`의 머지 전 권장 회귀 세트 중 `:domain:location:test`와 `:tools:demo-seed:test`를 포함합니다. release assemble은 CI 시간과 R8 회귀 필요성에 따라 별도 결정합니다.
```

- [x] **Step 2: Update `docs/deep-analysis-report.md` for completed implementation**

In `docs/deep-analysis-report.md`, move completed items out of "실행 필요" wording or mark them as completed with verification evidence. Keep conditional and "지금 하지 않을 것" sections intact.

- [x] **Step 3: Verify docs**

Run:

```bash
git diff --check -- docs/improvement-analysis.md docs/deep-analysis-report.md
```

Expected:
- No whitespace errors.

- [x] **Step 4: Commit this task**

Run:

```bash
git add docs/improvement-analysis.md docs/deep-analysis-report.md
git commit -m "docs: update required fixes status"
```

Expected:
- Commit succeeds.

## Task 6: Final Verification

**Files:**
- Read: all changed files

- [x] **Step 1: Run targeted verification**

Run:

```bash
./gradlew \
  :core:network:test \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest
```

Expected:
- `BUILD SUCCESSFUL`.

- [x] **Step 2: Run CI-equivalent verification**

Run:

```bash
JAVA_HOME="${JAVA_HOME}" PATH="${JAVA_HOME}/bin:${PATH}" ./gradlew \
  :domain:location:test \
  :core:model:test \
  :domain:station:test \
  :domain:settings:test \
  :core:database:testDebugUnitTest \
  :core:datastore:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :core:location:testDebugUnitTest \
  :core:network:test \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  :tools:demo-seed:test \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

Expected:
- `BUILD SUCCESSFUL`.

- [x] **Step 3: Check final diff**

Run:

```bash
git status --short
git diff --stat
git diff --check
```

Expected:
- Only intended files changed.
- No whitespace errors.
- No uncommitted changes remain if each task was committed.

## Conditional Promotion Rules

Promote these only with a new user decision or a failing verification signal:

- Add release assemble to CI if R8/minify regressions are a current concern and CI time is acceptable.
- Add `values-night-v31/themes.xml` if dark mode is accepted as a portfolio quality requirement.
- Enable Gradle parallel/build cache only after baseline timing and a stable matrix run.
- Add backend proxy only for public distribution, quota protection, or key abuse risk.

## Completion Criteria

This plan is complete when:

- `proj4j` is cataloged and `:core:network:test` passes.
- CI includes `:domain:location:test` and `:tools:demo-seed:test`.
- `StationListViewModelTest.kt` has no direct `Dispatchers.setMain` or `Dispatchers.resetMain`.
- Watchlist test tags are ASCII selectors and accessibility copy remains Korean.
- Targeted verification and CI-equivalent verification pass.
- Status docs reflect only completed, verified work.
