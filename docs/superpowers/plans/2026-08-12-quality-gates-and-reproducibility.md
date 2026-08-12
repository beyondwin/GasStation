# Quality Gates And Reproducibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the repository's existing lint, coverage, mutation, architecture, device, and build inputs into low-noise regression gates that detect blind spots without freezing unreviewed debt.

**Architecture:** Every new gate follows clean baseline capture, parser/TestKit verification, report-only observation, and explicit blocking promotion. Standard-library scripts parse JaCoCo/PIT and repository diffs; convention behavior moves behind tested build logic; Kotlin 2.4's built-in ABI validation protects selected JVM contracts.

**Tech Stack:** Gradle 9.6.1 Kotlin DSL, Kotlin 2.4.10, AGP, Android Lint, JaCoCo XML, PIT, Gradle TestKit, Python 3 standard library, GitHub Actions, Gradle Managed Devices

## Global Constraints

- Do not use the design document's observational coverage/PIT values as implementation baselines; remeasure from the implementation-start HEAD.
- Changed executable code blocks at 80% line and 70% branch; if changed executable lines contain no branch counters, branch is `N/A`, not 100% or failure.
- Domain/core targets are 90% line and 80% branch; data/state-holder targets are 85% line and 70% branch. Reach targets through ratchets rather than inventing an initial floor above the clean baseline.
- A module baseline may not fall by more than 0.5 percentage points; one release raises a floor by at most 2 percentage points.
- PIT blocking floors: station 45%, location 75%, settings report-only until mutant review.
- Compose rendering remains protected by state/semantics/Roborazzi rather than a global raw line floor.
- Do not add Detekt, Sonar, a second formatter, Android-wide PIT, or automatic flaky retries.
- Every blocking promotion has a previous report-only commit that can be reverted independently.

---

### Task 1: Capture a clean machine-readable quality baseline

**Files:**
- Create: `scripts/quality/capture_baseline.py`
- Create: `scripts/quality/tests/test_capture_baseline.py`
- Create after measurement: `config/quality/quality-baseline.json`
- Modify: `docs/test-strategy.md`

**Interfaces:**
- Consumes: JaCoCo XML and three PIT XML reports from the same HEAD
- Produces: commit, tool inputs, line/branch counters, mutation status counters, and timestamped environment metadata

- [ ] **Step 1: Add parser fixture tests**

Use small checked-in XML strings under `scripts/quality/tests/fixtures/` and assert exact counters. Missing report, zero total, malformed XML, or mixed commit inputs must exit non-zero.

- [ ] **Step 2: Implement baseline capture**

Use only `argparse`, `json`, `pathlib`, and `xml.etree.ElementTree`. Write deterministic sorted JSON; omit wall-clock time from equality-sensitive content and record `sourceCommit` from an explicit argument.

- [ ] **Step 3: Generate fresh reports**

Run: `./gradlew coverageXmlReport :domain:station:pitest :domain:location:pitest :domain:settings:pitest --warning-mode fail`

Run: `python3 scripts/quality/capture_baseline.py --commit "$(git rev-parse HEAD)" --coverage build/reports/coverage/report.xml --pitest domain/station/build/reports/pitest/mutations.xml --pitest domain/location/build/reports/pitest/mutations.xml --pitest domain/settings/build/reports/pitest/mutations.xml --output config/quality/quality-baseline.json`

- [ ] **Step 4: Review and commit the observed baseline**

Verify totals manually against XML counters before commit.

```bash
git add scripts/quality config/quality docs/test-strategy.md
git commit -m "test: capture clean quality baseline"
```

### Task 2: Remove the four test-source lint errors

**Files:**
- Modify: `app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt`
- Modify: `app/src/test/java/com/gasstation/navigation/GasStationTopLevelNavigationTest.kt`
- Modify: focused app tests/fixtures if a permission helper is extracted

- [ ] **Step 1: Preserve behavior with public-API tests**

Replace assertions that inspect `NavController.currentBackStack` with current destination, navigate/pop result, and saved-state restoration assertions. Add an API-23-compatible permission helper or SDK-specific fixture; do not add `@SuppressLint`.

- [ ] **Step 2: Run the current failing path**

Run: `./gradlew :app:lintDemoDebug :app:lintProdDebug -Pgasstation.lintTestSources=true --warning-mode fail --continue`

Expected before fixes: four errors. After changes: zero test-source lint errors; warnings remain for review in Task 3.

- [ ] **Step 3: Run app regressions and commit**

Run: `./gradlew :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :app:compileDemoDebugAndroidTestKotlin`

```bash
git add app/src/androidTest app/src/test
git commit -m "test: remove test source lint violations"
```

### Task 3: Promote explicit demo, prod, and test-source lint paths

**Files:**
- Modify: Android application/library convention plugins
- Create reviewed module lint baselines only where a warning is intentional
- Modify: `.github/workflows/android.yml`
- Add: build-logic TestKit fixtures/tests for lint property behavior

- [ ] **Step 1: Inventory every warning**

Run production and test-source lint separately with text/XML reports. Fix obsolete or actionable warnings. Use source suppression for a narrowly justified false positive; baseline only reviewed debt that cannot be safely changed in this program.

- [ ] **Step 2: Add TestKit RED coverage**

Create temporary application/library fixtures and assert `gasstation.lintTestSources=false` excludes tests, `true` includes them, and baselines do not hide a newly introduced error.

- [ ] **Step 3: Add explicit CI jobs**

Keep production static analysis:

```text
spotlessCheck :app:lintDemoDebug :app:lintProdDebug lint
verifyModuleBoundaries verifyNoDeprecatedComposeTestApis verifyCiRobolectricRuntime
```

Add a separate `lint-tests` job invoking root lint with `-Pgasstation.lintTestSources=true`. Do not conflate Gradle `--warning-mode fail` with Android Lint warning policy.

- [ ] **Step 4: Verify report-only then blocking promotion**

First commit the reviewed baselines and job with `continue-on-error: true`; run one CI cycle or reproduce locally. In the next narrow commit, remove `continue-on-error` only when the job is green.

- [ ] **Step 5: Commit**

```bash
git add build-logic .github/workflows/android.yml app core data feature
git commit -m "ci: gate production and test lint paths"
```

### Task 4: Test convention logic and ratchet Kotlin compiler warnings

**Files:**
- Modify: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/test/kotlin/ConventionPluginTest.kt`
- Modify: application/library/JVM convention plugins
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Adds: Gradle TestKit to the included build
- Adds: `gasstation.kotlinWarningsAsErrors` property, default false until a module is cleaned

- [ ] **Step 1: Add TestKit dependencies and RED fixtures**

Add `testImplementation(gradleTestKit())` and Kotlin test/JUnit support. Fixtures assert JVM target 17, lint source property, warning property, Roborazzi exclusion, and coverage task discovery.

- [ ] **Step 2: Implement compiler property consistently**

For every `KotlinCompile`, set `allWarningsAsErrors` from `gasstation.kotlinWarningsAsErrors`. Keep JVM target unchanged.

- [ ] **Step 3: Clean and promote selected contract modules**

Run the property for `domain:*`, `core:model`, and `core:observability`; fix warnings without suppressing useful diagnostics. Then apply strict mode for those modules through module properties or convention configuration. Other modules remain report-only until cleaned.

- [ ] **Step 4: Run and commit**

Run: `./gradlew :build-logic:convention:test :domain:station:test :domain:location:test :domain:settings:test :core:model:test :core:observability:test --warning-mode fail`

```bash
git add build-logic domain core
git commit -m "build: test and ratchet Kotlin conventions"
```

### Task 5: Verify coverage truth, changed code, and module ratchets

**Files:**
- Create: `scripts/quality/verify_coverage.py`
- Create: `scripts/quality/tests/test_verify_coverage.py`
- Create: `config/quality/coverage-policy.json`
- Create: `config/quality/coverage-baseline.json`
- Modify: root `build.gradle.kts`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- `verify-report`: XML exists, totals are non-zero, expected sources exist
- `verify-changed`: maps zero-context Git diff lines to JaCoCo `<line>` counters
- `verify-ratchets`: resolves package/source files to one module source root and compares per-module counters

- [ ] **Step 1: Add parser and diff RED fixtures**

Cover covered/uncovered changed lines, partial branch counters, no-branch `N/A`, deleted-only diff, duplicate source mapping, zero totals, missing expected source, and 0.5pp boundary arithmetic.

- [ ] **Step 2: Replace hard-coded class discovery**

Move Android variant class-directory discovery behind a tested Gradle helper using AGP variant artifacts. Fail `verifyCoverageReport` if an expected module contributes no authored source or execution data; do not silently accept the current `built_in_kotlinc` path becoming empty.

- [ ] **Step 3: Generate a source-to-module manifest**

Scan active modules from `settings.gradle.kts` and their `src/main`, `src/demo`, and `src/prod` source roots. Map package plus filename to exactly one module. A duplicate mapping is a gate failure rather than guessed attribution.

- [ ] **Step 4: Implement thresholds and targets**

Block changed executable code below 80% line or 70% branch. Store clean module baselines and fail drops greater than 0.5pp. Store domain/core 90/80 and data/state 85/70 as targets; initial floors start from reviewed clean values and rise by no more than 2pp per release.

- [ ] **Step 5: Promote through report-only CI**

Generate XML, run the script with `GASSTATION_CI_BASE_REF`, upload its JSON summary, and observe one cycle. Then make `verify-report`, changed coverage, and ratchet exit codes blocking in a separate commit.

- [ ] **Step 6: Verify and commit**

Run: `./gradlew coverageXmlReport --warning-mode fail`

Run: `python3 scripts/quality/verify_coverage.py --report build/reports/coverage/report.xml --base "$(git merge-base origin/main HEAD)" --policy config/quality/coverage-policy.json`

```bash
git add build.gradle.kts scripts/quality config/quality .github/workflows/android.yml
git commit -m "ci: enforce trustworthy changed coverage"
```

### Task 6: Expand dependency and public API boundary gates

**Files:**
- Create: `build-logic/convention/src/main/kotlin/GasStationRootQualityConventionPlugin.kt`
- Create: TestKit module/dependency/public-API fixtures
- Modify: `build-logic/convention/build.gradle.kts`
- Modify: root `build.gradle.kts` and public JVM module build files
- Create: checked-in Kotlin ABI dumps for selected modules
- Modify: `docs/module-contracts.md`

- [ ] **Step 1: Move root quality logic behind TestKit**

Preserve current task names while moving module-edge capture and typed tasks into a root convention plugin. Add RED fixtures for `compileOnly`, variant production dependency, forbidden external family, allowed test-only dependency, and intended `core:location -> domain:location` exception.

- [ ] **Step 2: Add reviewed external allowlists in report-only mode**

Resolve production compile classpaths but exclude test, androidTest, KSP, and tooling configurations. Emit sorted consumer/group/module reports. Review current app and Hilt/Compose assembly dependencies before turning violations into failures.

- [ ] **Step 3: Enable built-in Kotlin ABI validation**

For `domain:*`, `core:model`, and `core:observability`, configure Kotlin 2.4's built-in [`abiValidation()`](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html). Generate reference dumps with each module's `updateKotlinAbi` task and review every public declaration before commit.

- [ ] **Step 4: Reject platform types in contract ABI**

Add a typed verification task that scans generated ABI dumps for Android, Compose, Room, Retrofit, and DataStore types. Source regex is not an acceptable substitute. Introduce explicit API warning first, then strict only for reviewed modules.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew :build-logic:convention:test :domain:station:checkKotlinAbi :domain:location:checkKotlinAbi :domain:settings:checkKotlinAbi :core:model:checkKotlinAbi :core:observability:checkKotlinAbi verifyModuleBoundaries verifyPublicApiBoundaries`

```bash
git add build-logic build.gradle.kts domain core docs/module-contracts.md
git commit -m "build: enforce dependency and public API contracts"
```

### Task 7: Ratchet high-value mutation testing

**Files:**
- Modify: three domain module build files
- Create: `scripts/quality/verify_pitest.py`
- Create: parser tests/fixtures
- Create: `config/quality/mutation-baseline.json`
- Modify: CI workflow

- [ ] **Step 1: Add exact PIT parser tests**

Count `KILLED`, `SURVIVED`, and `NO_COVERAGE` by mutated class/package. Fail malformed or empty reports. Compare changed policy packages against the clean baseline.

- [ ] **Step 2: Apply approved floors**

Set station `mutationThreshold` to 45 and location to 75. Keep settings report-only. Fail an increase in `NO_COVERAGE` only for changed policy packages.

- [ ] **Step 3: Route by changed modules**

Map `domain/station/**` to `:domain:station:pitest`, `domain/location/**` to `:domain:location:pitest`, and `domain/settings/**` to `:domain:settings:pitest`; run all three on a schedule. Do not add Android/Compose PIT or full-PR unconditional PIT.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew :domain:station:pitest :domain:location:pitest :domain:settings:pitest --warning-mode fail`

Run: `python3 scripts/quality/verify_pitest.py --baseline config/quality/mutation-baseline.json domain/station/build/reports/pitest/mutations.xml domain/location/build/reports/pitest/mutations.xml domain/settings/build/reports/pitest/mutations.xml`

```bash
git add domain scripts/quality config/quality .github/workflows/android.yml
git commit -m "ci: ratchet domain mutation coverage"
```

### Task 8: Establish bounded API 24, 28, and 36 device evidence

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: permission/device tests and selectors
- Modify: `.github/workflows/android.yml`
- Create: `docs/runbooks/device-verification.md`

- [ ] **Step 1: Add GMD definitions and discover exact tasks**

Define Pixel-class managed devices for API 24, 28, and 36. Run `./gradlew tasks --group verification` and record the generated variant task names in the runbook.

- [ ] **Step 2: Make permission selectors SDK-aware**

API 28 uses legacy permission-controller behavior; API 36 covers target-era behavior. Keep API 33+ Geocoder callback smoke out of API 24/28 expectations.

- [ ] **Step 3: Run a non-blocking hosted-runner spike**

PR path runs one bounded API 28 demo permission/navigation flow. Schedule runs API 24, 28, and 36. Always upload JUnit, Android test report, logcat, and failure screenshots. Do not retry failures automatically.

- [ ] **Step 4: Promote only stable evidence**

After repeated green scheduled runs, make the bounded PR device job blocking. Quarantine requires owner, issue, and seven-day expiry in workflow metadata.

- [ ] **Step 5: Commit**

```bash
git add app .github/workflows/android.yml docs/runbooks/device-verification.md
git commit -m "ci: add bounded Android device evidence"
```

### Task 9: Pin build and CI inputs

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/verification-metadata.xml`
- Modify: `.github/workflows/android.yml`
- Modify: deployment/build documentation

- [ ] **Step 1: Verify the wrapper checksum from Gradle's official checksum endpoint**

Download `https://services.gradle.org/distributions/gradle-9.6.1-bin.zip.sha256`, compare it with the distribution, and add `distributionSha256Sum`. Do not trust only the local cache hash.

- [ ] **Step 2: Generate complete dependency verification metadata**

Resolve the actual lint, unit, screenshot, assemble, coverage, PIT, and device configurations while using `--write-verification-metadata sha256`. Review every new component/checksum; running only `help` is insufficient.

- [ ] **Step 3: Pin Actions to peeled commit SHAs**

Resolve checkout, setup-java, setup-gradle, upload/download-artifact, and Codecov tags immediately before the change. Pin the commit SHA and keep the version tag in a comment.

- [ ] **Step 4: Pin the runner label with an honest boundary**

Move supported jobs from `ubuntu-latest` to the tested `ubuntu-24.04` label. Document that hosted images are still mutable and this is not bit-for-bit runner reproducibility.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew --dependency-verification strict help`

Run: `./gradlew :build-logic:convention:test spotlessCheck lint coverageXmlReport --warning-mode fail`

```bash
git add gradle .github/workflows/android.yml docs
git commit -m "build: pin verified build inputs"
```

### Task 10: Synchronize quality contracts and close Phase 4

**Files:**
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`
- Modify: `docs/deployment.md`
- Modify: `docs/performance.md`
- Modify: `docs/build-velocity.md`

- [ ] **Step 1: Document gate ownership, floors, targets, and rollback**

State report-only versus blocking status precisely. Put exact command selection in `verification-matrix.md`; keep release, performance, and build-input details in their specialist documents.

- [ ] **Step 2: Run all new self-tests**

Run: `python3 -m unittest discover -s scripts/quality/tests`

Run: `./gradlew :build-logic:convention:test :domain:station:checkKotlinAbi :domain:location:checkKotlinAbi :domain:settings:checkKotlinAbi :core:model:checkKotlinAbi :core:observability:checkKotlinAbi verifyModuleBoundaries verifyPublicApiBoundaries`

- [ ] **Step 3: Run repository verification**

Run: `scripts/agent/verify.sh docs`

Run: `scripts/agent/verify.sh auto`

Run: `git diff --check`

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs: define enforced engineering quality gates"
```

Expected: every blocking gate passes at one final HEAD; non-blocking device or scheduled evidence is labeled explicitly rather than presented as verified.
