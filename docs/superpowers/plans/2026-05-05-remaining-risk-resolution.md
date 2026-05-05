# Remaining Risk Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the residual risks left after the improvement backlog pass: external secret exposure handling, device-backed Geocoder validation, Gradle test convention cleanup, deprecated system bar API replacement, theme/string cleanup, and DataStore dependency direction.

**Architecture:** Keep each risk in its owner boundary. `app` owns runtime security policy and system chrome, `core:location` owns platform Geocoder behavior, `build-logic` owns shared Gradle conventions, `core:designsystem` owns shared visual tokens and brand/display labels, and settings persistence changes must preserve `domain:settings` as the public contract. External key rotation and git history rewrite are operational tasks, not app code tasks.

**Tech Stack:** Kotlin, Android Gradle Plugin, Hilt, Room, DataStore, Jetpack Compose, Robolectric, Android instrumented tests, Gradle version catalog, git, optional `git-filter-repo`.

---

## Current Baseline

This plan assumes the backlog branch already contains the following completed work:

- Project `gradle.properties` no longer contains tracked `daum.apikey` or `opinet.apikey` assignments.
- `prod` startup fails fast when a local `opinet.apikey` is missing.
- Cleartext is scoped to exact `www.opinet.co.kr`.
- Cache pruning, retry catch narrowing, station event wiring, release minification, brand label centralization, and API 33+ Geocoder callback wrapping are implemented.
- Android backup/data extraction is disabled in `app/src/main/AndroidManifest.xml`.

Before executing any task, run:

```bash
git status --short --untracked-files=all
git branch --show-current
sed -n '1,220p' AGENTS.md
sed -n '1,220p' docs/agent-workflow.md
sed -n '1,220p' docs/module-contracts.md
```

Expected:
- Work continues from `codex/improvement-backlog` or a new `codex/...` branch derived from it.
- There are no unrelated user edits in files targeted by the task.
- Active modules are read from `settings.gradle.kts`, not from directory names alone.

---

## Task 0: Execution Setup And Checkpoint Rules

**Files:**
- Read: `AGENTS.md`
- Read: `docs/agent-workflow.md`
- Read: `docs/module-contracts.md`
- Read: `docs/verification-matrix.md`
- Modify only if stale: `docs/improvement-analysis.md`

**Acceptance Criteria:**
- The worker knows which tasks are repo-local, external-operational, device-dependent, or broad refactors.
- Every task starts on a clean checkpoint and ends with verification evidence.
- No external key value is printed in terminal output, docs, commit messages, or PR text.

- [ ] **Step 1: Create a task-local branch when implementing a broad task**

For code tasks after Task 2, create a separate branch from the integration branch. Example branch for the Geocoder task:

```bash
git switch codex/improvement-backlog
git switch -c codex/remaining-risk-geocoder-device-validation
```

Expected: branch name starts with `codex/remaining-risk-` and names the specific task.

- [ ] **Step 2: Record the task acceptance criteria before editing**

Add a short local note in the agent checkpoint, not necessarily in a repo file:

```text
Task:
Owner:
Done when:
Verification:
Stop rules:
```

Expected: the task has a clear owner module and a test command before patches start.

- [ ] **Step 3: Use the smallest honest verification first**

For each task, run the task-specific command listed below. Run the full merge regression set only after multiple tasks are integrated:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew \
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

Expected: `BUILD SUCCESSFUL`.

---

## Task 1: External Secret Rotation And History Decision

**Classification:** External-operational. Do not execute provider actions or history rewrites from an agent session without explicit user approval.

**Files:**
- Read: `README.md`
- Read: `docs/improvement-analysis.md`
- Read: `docs/verification-matrix.md`
- Modify only if the external decision changes repo guidance: the same three files

**Acceptance Criteria:**
- The current branch contains no active tracked API key assignments outside redacted docs.
- The exposed Opinet key is revoked in the provider system.
- A new Opinet key is stored outside the repo.
- The repository owner explicitly chooses either "no history rewrite" or "history rewrite".
- No raw key is written to any repo file or shared terminal transcript.

- [ ] **Step 1: Verify current tracked config/source files are clean**

Run:

```bash
rg -n --glob '!docs/**' --glob '!README.md' --glob '!**/build/**' \
  -e '^(daum|opinet)\.apikey=[^[:space:]<].+' .
rc=$?
if [ "$rc" -eq 1 ]; then
  exit 0
fi
exit "$rc"
```

Expected: exit `0` after `rg` returns no matches.

- [ ] **Step 2: Check whether local user Gradle properties still contain stale keys**

Run:

```bash
if [ -f "$HOME/.gradle/gradle.properties" ]; then
  grep -nE '^(daum|opinet)\.apikey=' "$HOME/.gradle/gradle.properties" || true
else
  echo "No user Gradle properties file"
fi
```

Expected:
- `daum.apikey` is absent.
- `opinet.apikey` may exist only as the current locally issued key.
- Do not print the value in any shared report.

- [ ] **Step 3: Revoke the exposed Opinet key**

Use the Opinet provider console or support process outside the repository.

Done when:
- The previously committed Opinet key no longer authenticates.
- The provider account has a newly issued key or a documented decision to pause `prod` execution.
- The new key is stored in the operator password manager.

Stop rule:
- If provider access is unavailable, stop Task 1 and record `blocked: Opinet provider access required`.

- [ ] **Step 4: Remove any stale Daum key from user-local Gradle properties**

Run only on the operator machine, not in the repo:

```bash
if [ -f "$HOME/.gradle/gradle.properties" ]; then
  awk '!/^daum\.apikey=/' "$HOME/.gradle/gradle.properties" > /tmp/gasstation-gradle.properties
  mv /tmp/gasstation-gradle.properties "$HOME/.gradle/gradle.properties"
  chmod 600 "$HOME/.gradle/gradle.properties"
fi
```

Expected: `grep -n '^daum\.apikey=' "$HOME/.gradle/gradle.properties"` prints nothing.

- [ ] **Step 5: Install the new Opinet key locally without writing it to the repo**

Run from a private shell where `GASSTATION_OPINET_API_KEY` is populated by the password manager:

```bash
test -n "$GASSTATION_OPINET_API_KEY"
mkdir -p "$HOME/.gradle"
touch "$HOME/.gradle/gradle.properties"
awk '!/^opinet\.apikey=/' "$HOME/.gradle/gradle.properties" > /tmp/gasstation-gradle.properties
printf 'opinet.apikey=%s\n' "$GASSTATION_OPINET_API_KEY" >> /tmp/gasstation-gradle.properties
mv /tmp/gasstation-gradle.properties "$HOME/.gradle/gradle.properties"
chmod 600 "$HOME/.gradle/gradle.properties"
```

Expected:
- `~/.gradle/gradle.properties` has exactly one `opinet.apikey=` line.
- The repo `gradle.properties` still has no `opinet.apikey=` assignment.

- [ ] **Step 6: Verify the local prod path after rotation**

Run:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew :app:assembleProdDebug :app:testProdDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Make the history rewrite decision**

Use this decision table:

| Condition | Decision |
| --- | --- |
| Repo never left the trusted local machine | Do not rewrite history. Keep the cleanup commit and revoked key. |
| Repo was pushed to a private remote with limited collaborators | Rotate key first, then ask all collaborators if a forced rewrite is acceptable. |
| Repo was pushed to public remote, CI logs, or forks | Treat the key as permanently compromised. Revoke it. History rewrite is optional cleanup, not the primary fix. |

Expected: a written decision in the task checkpoint.

- [ ] **Step 8: Prepare for optional history rewrite**

Run only after explicit owner approval:

```bash
git clone --mirror /Users/kws/source/android/GasStation /tmp/GasStation-history-backup.git
git -C /tmp/GasStation-history-backup fsck --full
```

Expected: `fsck` completes without corruption errors.

- [ ] **Step 9: Build a replacement file outside the repo**

Run in a private shell where revoked key values are in environment variables:

```bash
python3 - <<'PY' > /tmp/gasstation-secret-replacements.txt
import os
import re

for name in ("REVOKED_OPINET_API_KEY", "REVOKED_DAUM_API_KEY"):
    value = os.environ.get(name, "")
    if value:
        print(f"regex:{re.escape(value)}==><removed-{name.lower()}>")
PY
test -s /tmp/gasstation-secret-replacements.txt
```

Expected:
- The replacement file exists outside the repo.
- It is deleted after validation.
- It is never committed.

- [ ] **Step 10: Rewrite history if approved**

Run from the repository root:

```bash
git filter-repo --replace-text /tmp/gasstation-secret-replacements.txt
```

Expected: command completes successfully.

Stop rule:
- If `git-filter-repo` is not installed, do not substitute a different destructive tool in the same session. Install it or switch to a documented BFG plan after owner approval.

- [ ] **Step 11: Validate rewritten history**

Run:

```bash
git grep -n 'opinet\.apikey=' $(git rev-list --all) -- gradle.properties || true
git grep -n 'daum\.apikey=' $(git rev-list --all) -- gradle.properties || true
rm -f /tmp/gasstation-secret-replacements.txt
```

Expected:
- No raw revoked key value appears.
- Any remaining `apikey=` text is a redacted marker or current non-secret doc guidance.

- [ ] **Step 12: Push rewritten history only after collaborator coordination**

Run only after all collaborators agree to reclone or rebase:

```bash
git push --force-with-lease origin main
```

Expected: remote history is replaced and every active branch is reconciled.

---

## Task 2: Device-Backed Geocoder Callback Validation

**Classification:** Repo-local code plus device-dependent verification.

**Files:**
- Modify: `core/location/build.gradle.kts`
- Create: `core/location/src/androidTest/kotlin/com/gasstation/core/location/AndroidAddressResolverDeviceTest.kt`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`
- Modify: `docs/improvement-analysis.md`

**Acceptance Criteria:**
- API 33+ callback path is smoke-tested on a real device or emulator.
- The test asserts termination into the domain result type, not a device-specific address string.
- The test skips cleanly on API < 33 or when `Geocoder.isPresent()` is false.
- Unit tests still cover exact callback success/error/cancellation behavior.

- [ ] **Step 1: Add Android instrumented test dependencies**

Patch `core/location/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:location"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.location)
    testImplementation(libs.app.cash.turbine)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
}
```

Expected: the new `androidTestImplementation` lines are the only dependency additions.

- [ ] **Step 2: Add the device smoke test**

Create `core/location/src/androidTest/kotlin/com/gasstation/core/location/AndroidAddressResolverDeviceTest.kt`:

```kotlin
package com.gasstation.core.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationAddressLookupResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AndroidAddressResolverDeviceTest {
    @Test
    fun api33GeocoderCallbackPathReturnsTerminalResult() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        assumeTrue(Geocoder.isPresent())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = AndroidAddressResolver(context)

        val result = resolver.addressFor(
            Coordinates(
                latitude = 37.498095,
                longitude = 127.027610,
            ),
        )

        assertTrue(
            "Geocoder should return a terminal domain result, not hang",
            result is LocationAddressLookupResult.Success ||
                result is LocationAddressLookupResult.Unavailable ||
                result is LocationAddressLookupResult.Error,
        )
    }
}
```

- [ ] **Step 3: Verify unit behavior before device verification**

Run:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew :core:location:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify the connected device task surface**

Run:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew :core:location:tasks --all | rg 'connected.*AndroidTest|connectedDebugAndroidTest'
```

Expected: `connectedDebugAndroidTest` or an equivalent connected test task is listed.

- [ ] **Step 5: Run the smoke test on API 33+**

Run with an emulator or device connected:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew :core:location:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gasstation.core.location.AndroidAddressResolverDeviceTest
```

Expected:
- PASS on API 33+ devices with `Geocoder.isPresent()`.
- SKIPPED on unsupported devices.

Stop rule:
- If no device is connected, record `not run: requires connected API 33+ device/emulator` and keep the code behind unit tests until a device run is available.

- [ ] **Step 6: Update docs**

Update `docs/test-strategy.md` under `core:location`:

```markdown
`AndroidAddressResolverDeviceTest` is a connected smoke test for the API 33+ Geocoder callback path. It verifies terminal domain result behavior only because provider output varies by device and network.
```

Update `docs/verification-matrix.md` under 기기 기반 UI 확인 or a new location smoke section:

```bash
./gradlew :core:location:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gasstation.core.location.AndroidAddressResolverDeviceTest
```

- [ ] **Step 7: Final verification**

Run:

```bash
git diff --check -- core/location/build.gradle.kts core/location/src/androidTest/kotlin/com/gasstation/core/location/AndroidAddressResolverDeviceTest.kt docs/test-strategy.md docs/verification-matrix.md docs/improvement-analysis.md
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew :core:location:testDebugUnitTest
```

Expected: `git diff --check` passes and Gradle prints `BUILD SUCCESSFUL`.

---

## Task 3: Gradle Test Convention Cleanup

**Classification:** Broad repo-local build refactor. Run in its own branch.

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt`
- Modify: Android library module `build.gradle.kts` files
- Modify: `docs/improvement-analysis.md`
- Modify: `docs/test-strategy.md`

**Acceptance Criteria:**
- Common Android library unit-test dependencies are supplied by convention plugins.
- Compose library UI-test dependencies are supplied by the Compose library convention plugin.
- Module build files retain only module-specific test dependencies.
- No new third-party libraries are introduced.
- All affected module tests pass.

- [ ] **Step 1: Inventory current duplicated test dependencies**

Run:

```bash
rg -n 'testImplementation|androidTestImplementation|debugImplementation' --glob 'build.gradle.kts'
```

Expected current duplicate groups:
- `libs.junit` in most Android modules.
- `libs.kotlinx.coroutines.test` in several data/feature/core modules.
- `libs.robolectric` in Android resource or Compose test modules.
- Compose test BOM, `androidx-ui-test-junit4`, `androidx-ui-tooling`, `androidx-ui-test-manifest` in Compose feature modules.

- [ ] **Step 2: Add Android library baseline test dependencies**

Patch `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt` inside `dependencies { ... }`:

```kotlin
dependencies {
    add("coreLibraryDesugaring", libs.findLibrary("android-desugarJdkLibs").get())
    add("testImplementation", libs.findLibrary("junit").get())
    add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    add("testImplementation", libs.findLibrary("androidx-test-core").get())
    add("testImplementation", libs.findLibrary("robolectric").get())
}
```

Expected: non-Compose Android library modules no longer need local `junit`, `kotlinx-coroutines-test`, `androidx-test-core`, or `robolectric` declarations.

- [ ] **Step 3: Add Compose library test dependencies**

Patch `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt`:

```kotlin
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.platform
```

Then update `apply`:

```kotlin
class GasStationAndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("gasstation.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<LibraryExtension> {
            buildFeatures {
                compose = true
            }
        }

        dependencies {
            add("testImplementation", platform(libs.findLibrary("androidx-compose-bom").get()))
            add("testImplementation", libs.findLibrary("androidx-ui-test-junit4").get())
            add("debugImplementation", libs.findLibrary("androidx-ui-tooling").get())
            add("debugImplementation", libs.findLibrary("androidx-ui-test-manifest").get())
        }
    }
}
```

Expected: feature Compose modules no longer need local Compose test/debug dependency declarations.

- [ ] **Step 4: Remove duplicated Android library test dependencies**

Remove these lines where present:

```kotlin
testImplementation(libs.junit)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.androidx.test.core)
testImplementation(libs.robolectric)
```

Keep module-specific lines:

```kotlin
testImplementation(libs.app.cash.turbine)
testImplementation(libs.kotlin.test)
testImplementation(libs.mockwebserver)
testImplementation(libs.kotlinx.coroutines.core)
testImplementation(project(":core:model"))
```

Expected:
- `core/location/build.gradle.kts` keeps `app.cash.turbine`; removes duplicated baseline dependencies.
- `core/network/build.gradle.kts` keeps `mockwebserver` and `kotlinx.coroutines.core`; removes `junit`.
- `data/station/build.gradle.kts` keeps `kotlin.test`; removes duplicated `junit` and `kotlinx.coroutines.test` if supplied by convention.

- [ ] **Step 5: Remove duplicated Compose library test dependencies**

In `feature/station-list/build.gradle.kts`, `feature/settings/build.gradle.kts`, `feature/watchlist/build.gradle.kts`, and `core/designsystem/build.gradle.kts`, remove local declarations now supplied by the Compose library convention:

```kotlin
testImplementation(platform(libs.androidx.compose.bom))
testImplementation(libs.junit)
testImplementation(libs.androidx.ui.test.junit4)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.robolectric)
debugImplementation(libs.androidx.ui.tooling)
debugImplementation(libs.androidx.ui.test.manifest)
```

Keep `testImplementation(libs.app.cash.turbine)` in modules that use Turbine.

Expected: feature modules retain implementation dependencies and module-specific test dependencies only.

- [ ] **Step 6: Do not over-normalize app module dependencies**

Leave `app/build.gradle.kts` local test dependencies in place unless a second application convention task is explicitly created:

```kotlin
testImplementation(libs.hilt.android.testing)
testImplementation(libs.robolectric)
kspTest(libs.hilt.android.compiler)
androidTestImplementation(libs.hilt.android.testing)
kspAndroidTest(libs.hilt.android.compiler)
```

Reason: app has Hilt/flavor-specific test wiring and is the only application module.

- [ ] **Step 7: Verify convention plugin compilation**

Run:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew :build-logic:convention:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Verify all affected tests**

Run:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew \
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
  :tools:demo-seed:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Update backlog status**

In `docs/improvement-analysis.md`, mark `2-5 테스트 컨벤션 플러그인` complete only after Step 8 passes. Include the convention owner and the verification command.

---

## Task 4: Deprecated Status Bar API Replacement

**Classification:** UI/system chrome change. Run in its own branch and verify visual invariants.

**Files:**
- Modify: `app/src/main/java/com/gasstation/MainActivity.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaults.kt` only if the status bar token shape changes
- Create: `app/src/test/java/com/gasstation/SystemBarPolicyTest.kt`
- Modify: `docs/improvement-analysis.md`

**Acceptance Criteria:**
- `window.statusBarColor` and local `@Suppress("DEPRECATION")` for status bar color are removed.
- System bar setup happens in `app`, not inside feature UI or domain/data modules.
- `GasStationTheme` remains a theme provider and no longer mutates the Activity window.
- Yellow/black/white identity and splash resources remain intact.

- [ ] **Step 1: Write a source-level regression test**

Create `app/src/test/java/com/gasstation/SystemBarPolicyTest.kt`:

```kotlin
package com.gasstation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemBarPolicyTest {
    @Test
    fun `system bar setup is owned by app activity`() {
        val mainActivity = projectFile("app/src/main/java/com/gasstation/MainActivity.kt")
            .readText()

        assertTrue(mainActivity.contains("enableEdgeToEdge("))
        assertTrue(mainActivity.contains("SystemBarStyle"))
    }

    @Test
    fun `designsystem theme does not write deprecated status bar color`() {
        val theme = projectFile("core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt")
            .readText()

        assertFalse(theme.contains("statusBarColor"))
        assertFalse(theme.contains("@Suppress(\"DEPRECATION\")"))
    }

    private fun projectFile(path: String): File =
        File(path).takeIf(File::exists)
            ?: error("Could not find project file: $path")
}
```

Run to confirm it fails before implementation:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew :app:testDemoDebugUnitTest --tests com.gasstation.SystemBarPolicyTest
```

Expected before implementation: failure because `MainActivity` does not call `enableEdgeToEdge` and `Theme.kt` still contains `statusBarColor`.

- [ ] **Step 2: Move system bar setup to MainActivity**

Patch `app/src/main/java/com/gasstation/MainActivity.kt`:

```kotlin
package com.gasstation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.GasStationThemeDefaults
import com.gasstation.map.ExternalMapLauncher
import com.gasstation.navigation.GasStationNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var externalMapLauncher: ExternalMapLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_GasStation)
        applySystemBars()
        super.onCreate(savedInstanceState)
        setContent {
            GasStationTheme {
                GasStationNavHost(
                    externalMapLauncher = externalMapLauncher,
                )
            }
        }
    }

    private fun applySystemBars() {
        val statusBarStyle = GasStationThemeDefaults.statusBarStyle
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(statusBarStyle.backgroundColor.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(statusBarStyle.backgroundColor.toArgb()),
        )
    }
}
```

Expected: system chrome policy is app-owned.

- [ ] **Step 3: Remove window mutation from GasStationTheme**

Patch `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt` by removing these imports:

```kotlin
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
```

Remove the `statusBarStyle` parameter from `GasStationTheme`:

```kotlin
fun GasStationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = GasStationThemeDefaults.dynamicColor,
    content: @Composable () -> Unit,
)
```

Delete the `LocalView` and `SideEffect` block that writes `window.statusBarColor`.

Expected: `GasStationTheme` only computes and provides Material/designsystem theme values.

- [ ] **Step 4: Verify tests and resources**

Run:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew \
  :app:testDemoDebugUnitTest --tests com.gasstation.SystemBarPolicyTest \
  :core:designsystem:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:assembleDemoDebug \
  :app:assembleProdDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Visual smoke check**

Install `demoDebug` on an emulator and open the station list:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew :app:installDemoDebug
```

Expected:
- Status bar text/icons remain readable.
- Splash and first screen keep yellow/black/white identity.
- Price remains the first station-card reading target.

Stop rule:
- If edge-to-edge changes cause content overlap with status/navigation bars, revert Task 4 and create a separate UI layout padding task.

---

## Task 5: Theme And String Resource Cleanup

**Classification:** Broad UI/documentation cleanup. Run in a separate branch after Task 4.

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-v31/themes.xml`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/**`
- Modify only where user-visible text owner changes: `feature/*/src/main/kotlin/**`
- Modify: `docs/test-strategy.md`
- Modify: `docs/improvement-analysis.md`

**Acceptance Criteria:**
- App-level Android strings live in `app/src/main/res/values/strings.xml`.
- Shared visual tokens stay in `core:designsystem`.
- Feature-specific screen copy does not move into `core:designsystem`.
- UI semantics and test tags are preserved.
- No broad visual redesign is bundled into this cleanup.

- [ ] **Step 1: Inventory user-visible strings**

Run:

```bash
rg -n '"[^"]*[가-힣][^"]*"' app/src/main core feature \
  --glob '*.kt' \
  --glob '*.xml'
```

Expected: a list of Korean string literals and XML strings.

Classify each hit:

| Owner | Move? | Rule |
| --- | --- | --- |
| `app` manifest/system label | Yes | Move to `app/src/main/res/values/strings.xml`. |
| `feature:*` screen copy | No unless the feature owns resources | Keep near the screen state and tests. |
| test display name | No | Test names are not app UI. |
| log/debug text | No | Not user-visible UI. |
| `core:designsystem` generic component copy | Only if generic | Do not add feature-specific copy. |

- [ ] **Step 2: Inventory duplicated theme/token values**

Run:

```bash
rg -n '#[0-9A-Fa-f]{6,8}|windowSplashScreen|statusBar|navigationBar' \
  app/src/main/res core/designsystem/src/main/kotlin \
  --glob '*.xml' \
  --glob '*.kt'
```

Expected: a list of app XML theme values and designsystem token values.

Classify each hit:

| Owner | Rule |
| --- | --- |
| Splash resources | App owns Android launch chrome. |
| Color tokens used by Compose UI | `core:designsystem` owns. |
| Feature spacing/layout values | Feature owns unless already shared as a primitive. |

- [ ] **Step 3: Move only app-owned strings**

Patch `app/src/main/res/values/strings.xml` only for app-level strings:

```xml
<resources>
    <string name="app_name">주유주유소</string>
</resources>
```

Expected: do not add station-list, settings, or watchlist screen copy here unless Android resource ownership for that feature is introduced in the same task.

- [ ] **Step 4: Preserve feature-local UI copy**

If `rg` finds feature strings such as button labels, empty states, or guidance text, leave them in the feature module unless the feature already has a resource strategy. Add a note to `docs/improvement-analysis.md` for any remaining feature-local copy:

```markdown
Feature-local Compose strings remain in feature modules until each feature owns an Android resource strategy. Moving them to `app` resources would invert ownership.
```

Expected: module boundaries stay intact.

- [ ] **Step 5: Preserve visual token ownership**

Do not move `ColorYellow`, spacing, typography, or corner/stroke values out of `core:designsystem`. If duplicate XML theme colors exist only for launcher/splash resources, keep them in app resources and document that they are Android launch chrome, not Compose tokens.

- [ ] **Step 6: Verify UI tests**

Run:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Update docs**

In `docs/test-strategy.md`, add a short rule under UI regression risk:

```markdown
Theme/string cleanup must not move feature-owned user copy into `app` or `core:designsystem`; screen semantics and test tags remain the regression guard.
```

Mark `9-1/9-2` complete in `docs/improvement-analysis.md` only if Step 6 passes and every moved string/token has an owner rationale.

---

## Task 6: DataStore Dependency Direction Review

**Classification:** Architecture refactor. Run in its own branch after all smaller tasks are merged.

**Files:**
- Modify: `core/datastore/build.gradle.kts`
- Create: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/StoredUserPreferences.kt`
- Modify: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataSource.kt`
- Modify: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/AndroidUserPreferencesDataSource.kt`
- Modify: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesSerializer.kt`
- Modify: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataStoreModule.kt`
- Modify: `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- Modify: `core/datastore/src/test/kotlin/com/gasstation/core/datastore/UserPreferencesSerializerTest.kt`
- Modify: `data/settings/src/test/kotlin/com/gasstation/data/settings/DefaultSettingsRepositoryTest.kt`
- Modify: `docs/module-contracts.md`
- Modify: `docs/architecture.md`
- Modify: `docs/improvement-analysis.md`

**Acceptance Criteria:**
- `core:datastore` no longer depends on `domain:settings`.
- `core:datastore` persists a storage-local DTO with primitive/string enum names.
- `data:settings` maps between storage DTO and `domain:settings.model.UserPreferences`.
- `domain:settings` public model and use cases stay unchanged.
- Settings tests pass across domain, datastore, data, and feature modules.

- [ ] **Step 1: Verify current dependency direction**

Run:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew :core:datastore:dependencies --configuration debugRuntimeClasspath > /tmp/core-datastore-deps.txt
rg -n 'domain:settings|project :domain:settings' /tmp/core-datastore-deps.txt
```

Expected before refactor: `domain:settings` appears in `core:datastore` dependencies.

- [ ] **Step 2: Create storage DTO**

Create `core/datastore/src/main/kotlin/com/gasstation/core/datastore/StoredUserPreferences.kt`:

```kotlin
package com.gasstation.core.datastore

data class StoredUserPreferences(
    val searchRadiusName: String,
    val fuelTypeName: String,
    val brandFilterName: String,
    val sortOrderName: String,
    val mapProviderName: String,
) {
    companion object {
        val Default = StoredUserPreferences(
            searchRadiusName = "KM_3",
            fuelTypeName = "GASOLINE",
            brandFilterName = "ALL",
            sortOrderName = "DISTANCE",
            mapProviderName = "TMAP",
        )
    }
}
```

Expected: DTO has no dependency on `domain:settings`.

- [ ] **Step 3: Change DataStore type to storage DTO**

Patch `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataSource.kt`:

```kotlin
package com.gasstation.core.datastore

import kotlinx.coroutines.flow.Flow

interface UserPreferencesDataSource {
    val userPreferences: Flow<StoredUserPreferences>

    suspend fun update(transform: (StoredUserPreferences) -> StoredUserPreferences)
}
```

Patch `AndroidUserPreferencesDataSource.kt`:

```kotlin
package com.gasstation.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow

class AndroidUserPreferencesDataSource(
    private val dataStore: DataStore<StoredUserPreferences>,
) : UserPreferencesDataSource {
    override val userPreferences: Flow<StoredUserPreferences> = dataStore.data

    override suspend fun update(transform: (StoredUserPreferences) -> StoredUserPreferences) {
        dataStore.updateData { current ->
            transform(current)
        }
    }
}
```

- [ ] **Step 4: Update serializer**

Patch `UserPreferencesSerializer.kt` to serialize `StoredUserPreferences`:

```kotlin
package com.gasstation.core.datastore

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

object UserPreferencesSerializer : Serializer<StoredUserPreferences> {
    private const val ENTRY_DELIMITER = "\n"
    private const val KEY_VALUE_DELIMITER = "="
    private const val VERSION = "2"
    private const val KEY_VERSION = "version"
    private const val KEY_SEARCH_RADIUS = "searchRadius"
    private const val KEY_FUEL_TYPE = "fuelType"
    private const val KEY_BRAND_FILTER = "brandFilter"
    private const val KEY_SORT_ORDER = "sortOrder"
    private const val KEY_MAP_PROVIDER = "mapProvider"

    override val defaultValue: StoredUserPreferences = StoredUserPreferences.Default

    override suspend fun readFrom(input: InputStream): StoredUserPreferences {
        val encoded = input.readBytes().decodeToString().trim()
        if (encoded.isBlank()) {
            return defaultValue
        }

        return decodeKeyValueFormat(encoded)
    }

    override suspend fun writeTo(t: StoredUserPreferences, output: OutputStream) {
        output.write(
            listOf(
                KEY_VERSION to VERSION,
                KEY_SEARCH_RADIUS to t.searchRadiusName,
                KEY_FUEL_TYPE to t.fuelTypeName,
                KEY_BRAND_FILTER to t.brandFilterName,
                KEY_SORT_ORDER to t.sortOrderName,
                KEY_MAP_PROVIDER to t.mapProviderName,
            ).joinToString(ENTRY_DELIMITER) { (key, value) ->
                "$key$KEY_VALUE_DELIMITER$value"
            }.encodeToByteArray(),
        )
    }

    private fun decodeKeyValueFormat(encoded: String): StoredUserPreferences {
        val values = encoded.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { line ->
                val separatorIndex = line.indexOf(KEY_VALUE_DELIMITER)
                if (separatorIndex <= 0) {
                    return@mapNotNull null
                }
                line.substring(0, separatorIndex) to line.substring(separatorIndex + 1)
            }.toMap()

        return StoredUserPreferences(
            searchRadiusName = values[KEY_SEARCH_RADIUS] ?: defaultValue.searchRadiusName,
            fuelTypeName = values[KEY_FUEL_TYPE] ?: defaultValue.fuelTypeName,
            brandFilterName = values[KEY_BRAND_FILTER] ?: defaultValue.brandFilterName,
            sortOrderName = values[KEY_SORT_ORDER] ?: defaultValue.sortOrderName,
            mapProviderName = values[KEY_MAP_PROVIDER] ?: defaultValue.mapProviderName,
        )
    }
}
```

- [ ] **Step 5: Update Hilt module and Gradle dependencies**

Patch `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataStoreModule.kt`:

```kotlin
package com.gasstation.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UserPreferencesDataStoreModule {

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<StoredUserPreferences> =
        DataStoreFactory.create(
            serializer = UserPreferencesSerializer,
            produceFile = { context.filesDir.resolve(USER_PREFERENCES_FILE_NAME) },
        )

    @Provides
    @Singleton
    fun provideUserPreferencesDataSource(
        dataStore: DataStore<StoredUserPreferences>,
    ): UserPreferencesDataSource = AndroidUserPreferencesDataSource(dataStore)

    private const val USER_PREFERENCES_FILE_NAME = "user_preferences.pb"
}
```

Patch `core/datastore/build.gradle.kts` so the dependency block becomes:

```kotlin
dependencies {
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.core)
    testImplementation(libs.junit)
}
```

Expected: `core:datastore` no longer depends on `core:model` or `domain:settings`.

- [ ] **Step 6: Map in data settings**

Patch `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`:

```kotlin
package com.gasstation.data.settings

import com.gasstation.core.datastore.StoredUserPreferences
import com.gasstation.core.datastore.UserPreferencesDataSource
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultSettingsRepository @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : SettingsRepository {
    override fun observeUserPreferences(): Flow<UserPreferences> =
        dataSource.userPreferences.map(StoredUserPreferences::toDomain)

    override suspend fun updateUserPreferences(
        transform: (UserPreferences) -> UserPreferences,
    ) {
        dataSource.update { current ->
            transform(current.toDomain()).toStored()
        }
    }
}

private fun StoredUserPreferences.toDomain(): UserPreferences {
    val defaults = UserPreferences.default()
    return UserPreferences(
        searchRadius = enumOrDefault(searchRadiusName, defaults.searchRadius),
        fuelType = enumOrDefault(fuelTypeName, defaults.fuelType),
        brandFilter = enumOrDefault(brandFilterName, defaults.brandFilter),
        sortOrder = enumOrDefault(sortOrderName, defaults.sortOrder),
        mapProvider = enumOrDefault(mapProviderName, defaults.mapProvider),
    )
}

private fun UserPreferences.toStored(): StoredUserPreferences =
    StoredUserPreferences(
        searchRadiusName = searchRadius.name,
        fuelTypeName = fuelType.name,
        brandFilterName = brandFilter.name,
        sortOrderName = sortOrder.name,
        mapProviderName = mapProvider.name,
    )

private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(default)
```

Expected: storage decode accepts unknown enum names by falling back at the data repository boundary.

- [ ] **Step 7: Update tests**

Update `UserPreferencesSerializerTest` to assert `StoredUserPreferences.Default` and raw stored names.

Add a `DefaultSettingsRepositoryTest` case:

```kotlin
@Test
fun `repository maps invalid stored enum names to domain defaults`() = runTest {
    val dataSource = FakeUserPreferencesDataSource(
        StoredUserPreferences(
            searchRadiusName = "UNKNOWN_RADIUS",
            fuelTypeName = "UNKNOWN_FUEL",
            brandFilterName = "UNKNOWN_BRAND",
            sortOrderName = "UNKNOWN_SORT",
            mapProviderName = "UNKNOWN_MAP",
        ),
    )
    val repository = DefaultSettingsRepository(dataSource)

    val preferences = repository.observeUserPreferences().first()

    assertEquals(UserPreferences.default(), preferences)
}
```

If `FakeUserPreferencesDataSource` does not exist, create it in the test file with:

```kotlin
private class FakeUserPreferencesDataSource(
    initial: StoredUserPreferences,
) : UserPreferencesDataSource {
    private val state = MutableStateFlow(initial)

    override val userPreferences: Flow<StoredUserPreferences> = state

    override suspend fun update(transform: (StoredUserPreferences) -> StoredUserPreferences) {
        state.value = transform(state.value)
    }
}
```

- [ ] **Step 8: Verify dependency direction and settings tests**

Run:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew :core:datastore:dependencies --configuration debugRuntimeClasspath > /tmp/core-datastore-deps.txt
! rg -n 'domain:settings|project :domain:settings' /tmp/core-datastore-deps.txt
./gradlew :domain:settings:test :core:datastore:testDebugUnitTest :data:settings:testDebugUnitTest :feature:settings:testDebugUnitTest
```

Expected:
- `rg` finds no `domain:settings` dependency in `core:datastore`.
- Gradle prints `BUILD SUCCESSFUL`.

- [ ] **Step 9: Update module docs**

Patch `docs/module-contracts.md`:

```markdown
| `core:datastore` | DataStore data source, serializer, storage-local settings DTO | DataStore | 화면 상태, 설정 정책, domain model |
```

Patch `docs/architecture.md` to say:

```markdown
`core:datastore` persists a storage-local DTO. `data:settings` maps it to `domain:settings.UserPreferences`, so storage does not depend on the domain settings model.
```

Mark `10-1 datastore 의존 방향 재검토` complete in `docs/improvement-analysis.md` only after Step 8 passes.

---

## Final Integration Verification

Run after any two or more tasks from this plan are integrated:

```bash
git diff --check
rg -n --glob '!docs/**' --glob '!README.md' --glob '!**/build/**' \
  -e '^(daum|opinet)\.apikey=[^[:space:]<].+' .
rc=$?
if [ "$rc" -eq 1 ]; then
  true
else
  exit "$rc"
fi
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew \
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

Expected: no secret matches, `git diff --check` passes, and Gradle prints `BUILD SUCCESSFUL`.

---

## Self-Review

- Spec coverage: external secret operations, optional history rewrite, Geocoder device validation, build convention cleanup, status bar API replacement, theme/string cleanup, and DataStore dependency direction are each assigned to concrete tasks.
- Stop rules: provider access, history rewrite approval, missing API 33+ device, edge-to-edge overlap, and DataStore dependency inversion risk each has an explicit stop condition.
- Placeholder scan: no raw secret value or token-shaped example is included. Secret steps use environment variables and files outside the repo.
- Module boundaries: `app`, `core:location`, `build-logic`, `core:designsystem`, `core:datastore`, `data:settings`, and `domain:settings` ownership remains explicit.
- Verification: every code task has a targeted command and a final integration command.

## Execution Handoff

Recommended order:

1. Task 1, because provider key revocation is the only way to reduce the exposed-key risk.
2. Task 2, because it is narrow and device-dependent.
3. Task 3, because build convention cleanup touches many modules.
4. Task 4, because system chrome changes need visual checks.
5. Task 5, because theme/string cleanup should not be mixed with system chrome.
6. Task 6, because DataStore dependency inversion is the broadest architecture refactor.

Each task should be implemented in a separate checkpoint. Task 1 history rewrite must not run without explicit owner approval.
