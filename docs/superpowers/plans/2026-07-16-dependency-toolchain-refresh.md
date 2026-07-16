# Dependency And Toolchain Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring GasStation's stable Gradle, Android, Kotlin, AndroidX, and third-party dependency versions current as of 2026-07-16 without changing product behavior.

**Architecture:** Keep all dependency coordinates in the existing Gradle version catalog and keep SDK levels shared by the app/library convention plugins. Upgrade the Gradle wrapper with the official wrapper task, then verify both `demo` and `prod` paths plus static analysis, screenshot, coverage, and release assembly surfaces.

**Tech Stack:** Gradle 9.6.1, AGP 9.3.0, Kotlin 2.4.10, KSP 2.3.10, compile SDK 37, target SDK 36, Robolectric test SDK 36, Compose BOM 2026.06.01, Hilt 2.60.1, Spotless 8.8.0, ktlint 1.8.0, Kover 0.9.8, PIT 1.25.7.

## Global Constraints

- Adopt stable releases only; do not select alpha, beta, RC, milestone, or preview library coordinates.
- Preserve `minSdk = 24`, `versionCode = 8`, and `versionName = 1.2.0`; this is a dependency/toolchain refresh, not an app release.
- Preserve the `demo` and `prod` execution paths and all module boundaries.
- Do not edit historical measurement snapshots or past release notes merely to replace their recorded toolchain versions.
- Use Java 17 or newer to run Gradle; AGP 9.3.0 requires Java 17 and Gradle 9.5.0 or newer.

---

### Task 1: Refresh the version catalog and Android SDK levels

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `build-logic/convention/src/main/kotlin/GasStationSpotlessConventionPlugin.kt`
- Modify: `domain/{location,settings,station}/build.gradle.kts`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- Modify: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`
- Create: `config/robolectric/robolectric.properties`
- Modify: `docs/test-strategy.md`

**Interfaces:**
- Consumes: Stable update candidates from `./gradlew dependencyUpdates --no-configuration-cache --no-parallel` and official release metadata.
- Produces: A single catalog consumed by the root build, convention plugins, application, feature, data, domain, core, tool, and benchmark modules.

- [x] **Step 1: Establish the clean baseline**

Run:

```bash
git status --short
./gradlew :core:model:test :core:network:test :domain:location:test :core:observability:test :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest :app:assembleDemoDebug :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :benchmark:assemble
```

Expected: no tracked changes before the plan file is added and `BUILD SUCCESSFUL`.

- [x] **Step 2: Apply the stable catalog updates**

Set these exact values in `[versions]`:

```toml
agp = "9.3.0"
kotlin = "2.4.10"
ksp = "2.3.10"
compileSdk = "37"
targetSdk = "36"
composeBom = "2026.06.01"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
navigationCompose = "2.9.8"
hilt = "2.60.1"
hiltLifecycleViewModelCompose = "1.4.0"
coreKtx = "1.19.0"
playServicesLocation = "21.4.0"
proj4j = "1.4.3"
okhttp = "5.4.0"
coroutines = "1.11.0"
uiautomator = "2.4.0"
roborazzi = "1.68.0"
spotless = "8.8.0"
ktlint = "1.8.0"
kover = "0.9.8"
pitestEngine = "1.25.7"
```

Leave every catalog version not listed above unchanged because the freshness report identifies it as current or does not provide a verified stable replacement. The root dependency report does not traverse the included `build-logic` build, so Spotless and its ktlint engine are additionally checked against Maven Central release metadata.

Set `pitestVersion` from the shared `pitestEngine` catalog value in all three domain mutation-test modules. The Gradle plugin version and the PIT engine version are independent; the plugin's supported override removes the stale default engine reported by `dependencyUpdates`.

Register the Spotless alias once in the root `plugins` block with `apply false`. Spotless 8's shared build service must be loaded from one parent class loader when the convention plugin applies it across sibling modules.
Run `spotlessApply` once after the formatter upgrade and retain its mechanical ktlint 1.8 whitespace normalization.

`compileSdk` uses API 37 because the latest stable AndroidX Core, Lifecycle, and Hilt lifecycle artifacts require it in their AAR metadata. `targetSdk` remains API 36, while the common Robolectric test resource pins local unit tests to Robolectric 4.16.1's supported API 36. Revisit target/test API 37 when stable Robolectric support is available.

- [x] **Step 3: Remove AGP 9.3-obsolete lint report flags**

Remove `sarifReport`, `htmlReport`, and `xmlReport` assignments from both Android convention plugins. AGP 9.3 always generates these lint report artifacts, so retaining the assignments only emits deprecation warnings and does not control output.

- [x] **Step 4: Pin Robolectric unit tests to the newest supported SDK**

Create `config/robolectric/robolectric.properties` with `sdk=36`, then add that directory as the shared `test` resource source in both Android application and library convention plugins. This keeps product compilation on API 37 while local Robolectric tests use the stable runner's supported API 36.

- [x] **Step 5: Verify catalog resolution after Task 2 upgrades the wrapper**

Run:

```bash
./gradlew help
```

Expected: `BUILD SUCCESSFUL` with Gradle 9.6.1 resolving the upgraded plugins and dependency graph. AGP 9.3.0 cannot be configured by the previous Gradle 9.3.1 wrapper, so Task 2 must run first.

### Task 2: Upgrade the Gradle wrapper

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify if regenerated: `gradlew`
- Modify if regenerated: `gradlew.bat`
- Modify if regenerated: `gradle/wrapper/gradle-wrapper.jar`

**Interfaces:**
- Consumes: AGP 9.3.0's Gradle 9.5.0 minimum.
- Produces: A reproducible Gradle 9.6.1 entrypoint for local and CI builds.

- [x] **Step 1: Bootstrap and generate the official wrapper files**

Run:

```bash
# First set distributionUrl in gradle-wrapper.properties to the official 9.6.1 bin distribution,
# because the upgraded AGP cannot configure under the old Gradle 9.3.1 runtime.
./gradlew wrapper
./gradlew wrapper --gradle-version 9.6.1 --distribution-type bin
```

Expected: the wrapper distribution URL is `https://services.gradle.org/distributions/gradle-9.6.1-bin.zip` and both commands succeed.

- [x] **Step 2: Verify the wrapper runtime**

Run:

```bash
./gradlew --version
```

Expected: `Gradle 9.6.1`.

### Task 3: Reconcile live documentation with the upgraded coverage toolchain

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/verification-matrix.md`

**Interfaces:**
- Consumes: The actual Compose BOM value and Kover 0.9.8 verification result.
- Produces: Current dependency badge and an accurate coverage-tooling status note.

- [x] **Step 1: Update the Compose BOM badge**

Change the README badge value from `2026.03.01` to `2026.06.01`.

- [x] **Step 2: Test Kover's Android variant support before changing the status note**

Run:

```bash
./gradlew clean koverXmlReport
```

Expected: `BUILD SUCCESSFUL`; inspect `build/reports/kover/report.xml` and confirm Android module classes are present before removing the prior Kover 0.9.1/AGP 9.1.1 hold note. If Android module classes are still absent, retain the hold and update only the tested version pair.

- [x] **Step 3: Update the live verification note from observed evidence**

Record whether Kover 0.9.8 now includes Android debug unit-test variants. Do not add `koverVerify` as a blocking gate unless a meaningful coverage floor is separately designed and approved.

### Task 4: Run regression verification and commit

**Files:**
- Verify all changed files from Tasks 1-3.
- Modify: `core/designsystem/src/test/snapshots/status-banner-warning.png`
- Modify: `feature/station-list/src/test/snapshots/{empty,error,loading-with-cache,stale}.png`

**Interfaces:**
- Consumes: The upgraded wrapper, catalog, SDK levels, and live documentation.
- Produces: One reviewed commit on the current branch.

- [x] **Step 1: Run formatting and static checks**

Run:

```bash
./gradlew spotlessCheck lint verifyModuleBoundaries
```

Expected: `BUILD SUCCESSFUL`.

If the upgraded Compose/Roborazzi renderer produces only reviewed antialiasing-level pixel differences, record those affected goldens with the module-specific `recordRoborazziDebug` tasks and rerun this exact verification command. Do not accept structural, content, clipping, or hierarchy changes as a baseline refresh.

- [x] **Step 2: Run unit, screenshot, coverage, and assembly regression**

Run:

```bash
./gradlew :core:model:test :core:network:test :domain:location:test :core:observability:test :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest verifyRoborazziDebug koverXmlReport :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble :app:assembleProdRelease
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Re-run dependency freshness and inspect the diff**

Run:

```bash
./gradlew dependencyUpdates --no-configuration-cache --no-parallel
git diff --check
git diff --stat
git status --short
```

Expected: no verified stable direct dependency update remains, no whitespace errors exist, and only intended files are modified.

- [x] **Step 4: Commit the verified refresh**

Run:

```bash
git add CHANGELOG.md README.md build.gradle.kts build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt build-logic/convention/src/main/kotlin/GasStationSpotlessConventionPlugin.kt config/robolectric/robolectric.properties core/designsystem/src/test/snapshots/status-banner-warning.png data/station/src/test/kotlin/com/gasstation/data/station/FlavorAwareStationRemoteDataSourceTest.kt docs/test-strategy.md docs/verification-matrix.md docs/superpowers/plans/2026-07-16-dependency-toolchain-refresh.md feature/station-list/src/test/snapshots/empty.png feature/station-list/src/test/snapshots/error.png feature/station-list/src/test/snapshots/loading-with-cache.png feature/station-list/src/test/snapshots/stale.png gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.jar gradlew gradlew.bat
git commit -m "chore: refresh Android dependencies and toolchain"
```

Expected: one commit containing only the dependency/toolchain refresh and its documentation.
