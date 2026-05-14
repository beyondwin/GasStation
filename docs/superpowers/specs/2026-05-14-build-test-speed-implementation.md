# GasStation Build/Test Speed Improvement 구현 문서

> 작성일: 2026-05-14
> 기준 위치: `/Users/kws/source/android/GasStation`
> 구현 순서는 [`docs/superpowers/plans/2026-05-14-build-test-speed-improvements.md`](../plans/2026-05-14-build-test-speed-improvements.md)가 소유한다.

---

## 1. 결론

현재 가장 큰 개선 여지는 앱 코드가 아니라 Gradle 실행 기본값, lint 범위, screenshot test 분리, 반복 Robolectric/Compose 테스트 구조에 있다.

우선순위는 아래 순서다.

| 우선순위 | 개선 | 이유 |
| --- | --- | --- |
| P1 | Gradle configuration cache/build cache/parallel 기본값 검증 후 활성화 | 현재 configuration cache가 주요 경로에서 동작하지만 `gradle.properties`에는 꺼져 있다. |
| P1 | lint 범위 축소 | `spotlessCheck lint` 19.56초 중 `feature:station-list` lint가 17.62초, app lint가 15.97초로 가장 크다. |
| P1 | Roborazzi를 일반 unit-test job에서 제외 | unit-test job과 screenshot-tests job이 같은 Roborazzi test를 중복 실행한다. |
| P2 | route auto-refresh 정책을 pure Kotlin으로 추출 | `GpsAvailabilityMonitorTest` 6.60초 중 대부분이 Compose Activity rule 비용이다. |
| P2 | app 공통 resource test 중복 축소 | `app/src/test` 공통 테스트가 demo/prod unit task에서 모두 돈다. |
| P3 | Android resource-inclusive unit test를 opt-in으로 전환 | 일부 Android library는 resources 없이도 테스트 가능하지만 전체 convention이 resources를 켜고 있다. |

## 2. 측정 근거

### 실행한 명령

```bash
git status --short
/usr/bin/time -p ./gradlew help --configuration-cache
/usr/bin/time -p ./gradlew :app:assembleDemoDebug --configuration-cache --profile
/usr/bin/time -p ./gradlew :app:assembleDemoDebug
/usr/bin/time -p ./gradlew :domain:location:test :core:model:test :domain:station:test :domain:settings:test :core:database:testDebugUnitTest :core:datastore:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:location:testDebugUnitTest :core:network:test :data:settings:testDebugUnitTest :data:station:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :tools:demo-seed:test --profile
/usr/bin/time -p ./gradlew spotlessCheck lint --profile
```

### 관찰 결과

| 항목 | 결과 |
| --- | --- |
| 시작 상태 | `git status --short` 출력 없음 |
| 활성 모듈 | `settings.gradle.kts` 기준 18개 include |
| unit test 파일 수 | host/unit test 69개, instrumentation/benchmark 4개 |
| configuration cache `help` 최초/재사용 | 7.45초 -> 0.46초 |
| `:app:assembleDemoDebug` up-to-date 기본/configuration-cache 재사용 | 1.69초 -> 0.63초 |
| `:app:assembleDemoDebug --configuration-cache --profile` | 15.46초, 254 tasks, 36 executed |
| CI unit-test 명령과 같은 범위 | 66.45초, 423 tasks, 141 executed |
| `spotlessCheck lint` | 19.56초, 535 tasks, 28 executed |

### unit test 병목

`build/test-results/**/TEST-*.xml` 기준 느린 test class는 아래와 같다.

| 시간 | 테스트 | 판단 |
| ---: | --- | --- |
| 6.602s | `feature:station-list/GpsAvailabilityMonitorTest` | route/lifecycle/auto-refresh 확인이 Compose Activity rule에 몰려 있다. |
| 5.396s | `feature:watchlist/WatchlistScreenTest` | 여러 Compose render pass가 반복된다. |
| 4.941s | `feature:settings/SettingsScreenTest` | scroll/semantics Compose render pass가 반복된다. |
| 4.360s | `core:designsystem/RoborazziDesignSystemTest` | screenshot capture가 unit-test task에 포함된다. |
| 3.049s | `feature:station-list/RoborazziStationListScreenTest` | screenshot capture가 unit-test task에 포함된다. |
| 2.991s | `core:location/AddressLabelFormatterTest` | Android `Address` 때문에 Robolectric runner를 사용한다. |
| 2.854s | `app/testProd/BackupPolicyResourceTest` | 파일 읽기 테스트인데 Robolectric runner가 붙어 있다. |
| 2.852s | `core:database/GasStationDatabaseMigrationTest` | Room migration DB 생성 비용이다. |
| 2.577s | `app/testDemo/BackupPolicyResourceTest` | 같은 공통 테스트가 demo/prod에서 모두 돈다. |

### lint 병목

`build/reports/profile/profile-2026-05-14-04-46-18.html` 기준:

| 모듈 | 시간 | 주요 작업 |
| --- | ---: | --- |
| `feature:station-list` | 17.615s | `lintAnalyzeDebug` 11.553s, `lintAnalyzeDebugUnitTest` 5.227s |
| `app` | 15.967s | `lintAnalyzeDemoDebugUnitTest` 7.022s, `lintAnalyzeDemoDebugAndroidTest` 4.782s, `lintAnalyzeDemoDebug` 2.870s |

현재 convention은 Android app/library 모두 `lint.checkDependencies = true`다. `app`이 dependency lint를 볼 수 있는데 library lint도 dependency lint를 반복하면 transitive 분석이 중복될 수 있다.

## 3. 구현 레시피

### 3.1 Gradle 실행 기본값

수정 파일:

- `gradle.properties`

권장 변경:

```properties
org.gradle.configuration-cache=true
org.gradle.caching=true
org.gradle.parallel=true
```

검증 명령:

```bash
./gradlew help
./gradlew :app:assembleDemoDebug
./gradlew :domain:location:test :core:model:test :domain:station:test :domain:settings:test :core:database:testDebugUnitTest :core:datastore:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:location:testDebugUnitTest :core:network:test :data:settings:testDebugUnitTest :data:station:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :tools:demo-seed:test
./gradlew spotlessCheck lint
./gradlew :app:assembleProdDebug :benchmark:assemble
```

성공 기준:

- 모든 명령이 `BUILD SUCCESSFUL`.
- configuration cache 문제가 있으면 해당 task를 고친 뒤 활성화한다.
- `--configuration-cache-problems=warn`를 영구 기본값으로 두지 않는다. PR에서 문제를 숨기지 않기 위해 실패가 더 낫다.

### 3.2 Compose compiler report/metric opt-in

수정 파일:

- `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt`

현재는 Compose compiler report와 metric directory가 항상 설정된다. 기본 빌드에서는 끄고, 필요할 때만 `-Pgasstation.composeCompilerReports=true`로 켠다.

적용 코드:

```kotlin
val composeCompilerReportsEnabled = providers
    .gradleProperty("gasstation.composeCompilerReports")
    .map(String::toBoolean)
    .orElse(false)

extensions.configure<ComposeCompilerGradlePluginExtension> {
    if (composeCompilerReportsEnabled.get()) {
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
    }
}
```

검증 명령:

```bash
./gradlew :feature:station-list:compileDebugKotlin
./gradlew :feature:station-list:compileDebugKotlin -Pgasstation.composeCompilerReports=true
```

성공 기준:

- 기본 명령은 `build/compose-reports`, `build/compose-metrics`를 새로 만들지 않는다.
- property를 켠 명령은 기존 directory를 생성한다.

### 3.3 lint 범위 축소

수정 파일:

- `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`
- `.github/workflows/android.yml`
- `docs/verification-matrix.md`

library convention 변경:

```kotlin
val lintTestSourcesEnabled = providers
    .gradleProperty("gasstation.lintTestSources")
    .map(String::toBoolean)
    .orElse(false)

extensions.configure<LibraryExtension> {
    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = false
        checkTestSources = lintTestSourcesEnabled.get()
        sarifReport = true
        htmlReport = true
        xmlReport = false
    }
}
```

application convention 변경:

```kotlin
val lintTestSourcesEnabled = providers
    .gradleProperty("gasstation.lintTestSources")
    .map(String::toBoolean)
    .orElse(false)

extensions.configure<ApplicationExtension> {
    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
        checkTestSources = lintTestSourcesEnabled.get()
        sarifReport = true
        htmlReport = true
        xmlReport = false
    }
}
```

CI 기본은 빠른 PR feedback을 위해 test source lint를 끈다.

```yaml
- name: Spotless + Lint
  run: ./gradlew spotlessCheck lint --continue
```

test source lint가 필요할 때는 별도 명령으로 실행한다.

```bash
./gradlew lint -Pgasstation.lintTestSources=true --continue
```

성공 기준:

- `spotlessCheck lint`가 성공한다.
- `feature:station-list:lintAnalyzeDebugUnitTest`와 `app:lintAnalyzeDemoDebugUnitTest`가 기본 PR 경로에서 사라지거나 시간이 크게 줄어든다.
- test source lint opt-in 명령도 성공한다.

### 3.4 Roborazzi를 일반 unit-test job에서 제외

수정 파일:

- `build-logic/convention/src/main/kotlin/GasStationRoborazziConventionPlugin.kt`
- `.github/workflows/android.yml`
- `docs/verification-matrix.md`

Roborazzi task가 직접 요청되지 않은 일반 unit-test task에서는 screenshot test class를 제외한다. `verifyRoborazziDebug`를 요청하면 기존처럼 포함한다.

적용 코드:

```kotlin
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

class GasStationRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val includeInUnitTests = providers
                .gradleProperty("gasstation.includeRoborazziInUnitTests")
                .map(String::toBoolean)
                .orElse(false)
            val roborazziTaskRequested = gradle.startParameter.taskNames.any {
                it.contains("Roborazzi", ignoreCase = true)
            }

            tasks.withType<Test>().configureEach {
                if (!roborazziTaskRequested && !includeInUnitTests.get()) {
                    exclude("**/Roborazzi*Test.class")
                }
            }

            dependencies {
                add("testImplementation", libs.findLibrary("roborazzi-core").get())
                add("testImplementation", libs.findLibrary("roborazzi-compose").get())
                add("testImplementation", libs.findLibrary("roborazzi-junit-rule").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
            }
        }
    }
}
```

검증 명령:

```bash
./gradlew :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest
./gradlew verifyRoborazziDebug
./gradlew :core:designsystem:testDebugUnitTest -Pgasstation.includeRoborazziInUnitTests=true
```

성공 기준:

- 일반 unit test에서 `RoborazziDesignSystemTest`, `RoborazziStationListScreenTest`가 실행되지 않는다.
- `verifyRoborazziDebug`는 기존 snapshot을 계속 비교한다.
- property opt-in 명령은 screenshot test까지 포함한다.

### 3.5 route 정책 pure Kotlin 추출

수정 파일:

- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicy.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/GpsAvailabilityMonitorTest.kt`

신규 파일:

```kotlin
package com.gasstation.feature.stationlist

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState

internal fun StationListUiState.shouldAutoRefreshOnRoute(): Boolean =
    isAvailabilityKnown &&
        isGpsEnabled &&
        (
            currentCoordinates == null ||
                hasDeniedLocationAccess ||
                needsRecoveryRefresh
            )

internal fun StationListUiState.watchlistCoordinatesOrNull(): Coordinates? =
    currentCoordinates?.takeIf {
        isGpsEnabled &&
            (
                permissionState != LocationPermissionState.Denied ||
                    hasDeniedLocationAccess
                )
    }
```

`StationListRoute.kt` 변경:

```kotlin
LaunchedEffect(uiState.shouldAutoRefreshOnRoute()) {
    if (uiState.shouldAutoRefreshOnRoute()) {
        viewModel.onAction(StationListAction.AutoRefreshRequested)
    }
}

StationListScreen(
    uiState = uiState,
    snackbarHostState = snackbarHostState,
    onAction = viewModel::onAction,
    onRequestPermissions = { permissionState.launchMultiplePermissionRequest() },
    onOpenLocationSettings = {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    },
    onSettingsClick = onSettingsClick,
    onWatchlistClick = uiState.watchlistCoordinatesOrNull()?.let { coordinates ->
        { onWatchlistClick(coordinates) }
    },
)
```

검증 포인트:

- `GpsAvailabilityMonitorTest`에는 lifecycle collection smoke test만 남긴다.
- auto-refresh 조건과 watchlist 노출 조건은 `StationListRoutePolicyTest`가 빠르게 검증한다.
- 기존 `StationListViewModelTest`의 location/permission behavior는 유지한다.

### 3.6 app 공통 테스트 중복 축소

수정 파일 이동:

- `app/src/test/java/com/gasstation/BackupPolicyResourceTest.kt` -> `app/src/testDemo/java/com/gasstation/BackupPolicyResourceTest.kt`
- `app/src/test/java/com/gasstation/AppIconResourceTest.kt` -> `app/src/testDemo/java/com/gasstation/AppIconResourceTest.kt`
- `app/src/test/java/com/gasstation/SplashThemeResourceTest.kt` -> `app/src/testDemo/java/com/gasstation/SplashThemeResourceTest.kt`
- `app/src/test/java/com/gasstation/NetworkSecurityConfigResourceTest.kt` -> `app/src/testDemo/java/com/gasstation/NetworkSecurityConfigResourceTest.kt`
- `app/src/test/java/com/gasstation/SystemBarPolicyTest.kt` -> `app/src/testDemo/java/com/gasstation/SystemBarPolicyTest.kt`
- `app/src/test/java/com/gasstation/map/ExternalMapLauncherTest.kt` -> `app/src/testDemo/java/com/gasstation/map/ExternalMapLauncherTest.kt`

삭제:

- `app/src/test/java/com/gasstation/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/gasstation/ExampleInstrumentedTest.kt`

남길 파일:

- `app/src/test/java/com/gasstation/startup/AppStartupGraphTest.kt`
- `app/src/test/java/com/gasstation/startup/AppStartupRunnerTest.kt`

이유:

- app resource는 `app/src/main/res`에만 있고 demo/prod overlay가 없다.
- startup graph는 flavor binding이 달라서 demo/prod 모두 검증해야 한다.
- placeholder example test는 제품 계약을 보호하지 않는다.

검증 명령:

```bash
./gradlew :app:testDemoDebugUnitTest :app:testProdDebugUnitTest
./gradlew :app:connectedDemoDebugAndroidTest --dry-run
```

### 3.7 Android resource unit test opt-in

이 단계는 P1/P2가 끝난 뒤 별도 PR로 수행한다.

수정 방향:

- `GasStationAndroidLibraryConventionPlugin`은 `unitTests.isIncludeAndroidResources = false`를 기본값으로 둔다.
- Compose/resource/Robolectric resource가 필요한 모듈만 build file에서 `unitTests.isIncludeAndroidResources = true`를 명시한다.

우선 opt-in 후보:

- `core:designsystem`
- `feature:settings`
- `feature:station-list`
- `feature:watchlist`

검증해야 할 후보:

- `core:database`: Room + ApplicationProvider는 필요하지만 app resource는 필요하지 않을 가능성이 높다.
- `core:location`: Android framework class를 쓰지만 app resource는 필요하지 않을 가능성이 높다.
- `data:settings`, `data:station`: repository tests 중심이라 resource 불필요 가능성이 높다.

검증 명령:

```bash
./gradlew :core:database:testDebugUnitTest :core:location:testDebugUnitTest :data:settings:testDebugUnitTest :data:station:testDebugUnitTest
```

## 4. 기대 효과

| 개선 | 기대 효과 |
| --- | --- |
| configuration cache 기본 활성화 | 반복 로컬 Gradle invocation의 configuration time을 1초 안팎까지 낮춘다. |
| build cache 기본 활성화 | CI와 로컬에서 cacheable task output 재사용 여지를 만든다. |
| lint dependency/test source 범위 조정 | PR static-analysis job에서 가장 큰 `feature:station-list`와 app lint 비용을 줄인다. |
| Roborazzi unit-test 제외 | unit-tests job에서 screenshot capture 7초 이상을 제거하고 screenshot-tests job으로 책임을 모은다. |
| route policy pure test | `GpsAvailabilityMonitorTest`의 Compose Activity 비용을 줄이고 정책 변경을 빠른 JVM assertion으로 막는다. |
| app 공통 테스트 demo 단일화 | prod app unit task에서 중복 resource Robolectric 테스트를 제거한다. |

## 5. 완료 기준

최종 PR은 아래를 만족해야 한다.

```bash
./gradlew help
./gradlew spotlessCheck lint --continue
./gradlew :domain:location:test :core:model:test :domain:station:test :domain:settings:test :core:database:testDebugUnitTest :core:datastore:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:location:testDebugUnitTest :core:network:test :data:settings:testDebugUnitTest :data:station:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest :tools:demo-seed:test
./gradlew verifyRoborazziDebug
./gradlew :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble
git diff --check -- gradle.properties .github/workflows/android.yml build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt build-logic/convention/src/main/kotlin/GasStationAndroidLibraryComposeConventionPlugin.kt build-logic/convention/src/main/kotlin/GasStationRoborazziConventionPlugin.kt docs/verification-matrix.md docs/superpowers/specs/2026-05-14-build-test-speed-implementation.md docs/superpowers/plans/2026-05-14-build-test-speed-improvements.md
```
