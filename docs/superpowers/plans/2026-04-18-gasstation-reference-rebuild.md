# GasStation 레퍼런스 재구축 구현 계획

> **에이전트 작업자용:** 필수 하위 스킬: 작업별 구현에는 `superpowers:subagent-driven-development`(권장) 또는 `superpowers:executing-plans`를 사용한다. 진행 추적은 체크박스 문법(`- [ ]`)을 사용한다.

**목표:** 레거시 단일 모듈 `GasStation` 앱을 멀티 모듈, 클린 아키텍처, Flow 중심 상태 모델, demo/prod 실행 전략을 갖춘 면접용 레퍼런스 앱으로 재구성한다.

**아키텍처:** 먼저 계약과 상태를 고정한 뒤 모듈 경계를 세우고, 그 위에 DataStore, Room, Flow 리듀서, 기능 UI를 얹는다. 캐시 정책, 권한 정책, 설정 소유권, demo/prod 분리를 구현 초기에 고정해서 이후 단계가 흔들리지 않게 만든다.

**기술 스택:** Kotlin, Gradle convention plugins, Jetpack Compose, Coroutines/Flow, Hilt, DataStore, Room, Retrofit/OkHttp, Turbine, JUnit, Compose UI Test, Macrobenchmark

---

## 범위 점검

이 계획은 하나의 앱을 완성하는 단일 계획이다. 설정, 위치, 검색, 지도 연동, demo/prod, 품질 계층이 모두 포함되지만 서로 독립된 서비스가 아니라 하나의 사용자 흐름과 모듈 그래프를 공유한다. 따라서 별도 계획으로 쪼개지 않고, **계약 -> 데이터 -> 프레젠테이션 -> 앱 통합 -> 품질 강화** 순으로 하나의 계획에 담는다.

## 파일 구조

### 루트 / 빌드
- 수정: `settings.gradle.kts`
- 수정: `build.gradle.kts`
- 수정: `gradle/libs.versions.toml`
- 생성: `build-logic/settings.gradle.kts`
- 생성: `build-logic/convention/build.gradle.kts`
- 생성: `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- 생성: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`
- 생성: `build-logic/convention/src/main/kotlin/GasStationJvmLibraryConventionPlugin.kt`
- 생성: `build-logic/convention/src/main/kotlin/GasStationAndroidHiltConventionPlugin.kt`
- 생성: `build-logic/convention/src/main/kotlin/GasStationAndroidRoomConventionPlugin.kt`

### 코어
- 생성: `core/common/build.gradle.kts`
- 생성: `core/common/src/main/kotlin/com/gasstation/core/common/result/AppResult.kt`
- 생성: `core/common/src/main/kotlin/com/gasstation/core/common/dispatchers/DispatcherProvider.kt`
- 생성: `core/model/build.gradle.kts`
- 생성: `core/model/src/main/kotlin/com/gasstation/core/model/Coordinates.kt`
- 생성: `core/model/src/main/kotlin/com/gasstation/core/model/DistanceMeters.kt`
- 생성: `core/model/src/main/kotlin/com/gasstation/core/model/MoneyWon.kt`
- 생성: `core/ui/build.gradle.kts`
- 생성: `core/designsystem/build.gradle.kts`
- 생성: `core/testing/build.gradle.kts`
- 생성: `core/location/build.gradle.kts`
- 생성: `core/location/src/main/kotlin/com/gasstation/core/location/LocationPermissionState.kt`
- 생성: `core/location/src/main/kotlin/com/gasstation/core/location/ForegroundLocationProvider.kt`
- 생성: `core/network/build.gradle.kts`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetStationDto.kt`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetResponseDto.kt`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/model/KakaoTransCoordDto.kt`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/service/OpinetService.kt`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/service/KakaoService.kt`
- 생성: `core/database/build.gradle.kts`
- 생성: `core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt`
- 생성: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheEntity.kt`
- 생성: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`
- 생성: `core/datastore/build.gradle.kts`
- 생성: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesSerializer.kt`
- 생성: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataSource.kt`

### 도메인
- 생성: `domain/settings/build.gradle.kts`
- 생성: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/model/UserPreferences.kt`
- 생성: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/SettingsRepository.kt`
- 생성: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/ObserveUserPreferencesUseCase.kt`
- 생성: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdatePreferredSortOrderUseCase.kt`
- 생성: `domain/station/build.gradle.kts`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelType.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/BrandFilter.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SortOrder.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SearchRadius.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/MapProvider.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/Station.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationFreshness.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQueryCacheKey.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveNearbyStationsUseCase.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/RefreshNearbyStationsUseCase.kt`

### 데이터
- 생성: `data/settings/build.gradle.kts`
- 생성: `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- 생성: `data/station/build.gradle.kts`
- 생성: `data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt`
- 생성: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- 생성: `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`
- 생성: `data/station/src/main/kotlin/com/gasstation/data/station/mapper/StationMappers.kt`

### 기능
- 생성: `feature/settings/build.gradle.kts`
- 생성: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
- 생성: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsAction.kt`
- 생성: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
- 생성: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- 생성: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`
- 생성: `feature/station-list/build.gradle.kts`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListEffect.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`

### 앱 / 벤치마크 / 문서
- 수정: `app/build.gradle.kts`
- 수정: `app/src/main/AndroidManifest.xml`
- 생성: `app/src/main/kotlin/com/gasstation/MainActivity.kt`
- 생성: `app/src/main/kotlin/com/gasstation/navigation/GasStationNavHost.kt`
- 생성: `app/src/main/kotlin/com/gasstation/navigation/GasStationDestination.kt`
- 생성: `app/src/main/kotlin/com/gasstation/map/ExternalMapLauncher.kt`
- 생성: `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`
- 생성: `app/src/demo/kotlin/com/gasstation/DemoSeedData.kt`
- 생성: `app/src/prod/kotlin/com/gasstation/ProdSecretsModule.kt`
- 생성: `benchmark/build.gradle.kts`
- 생성: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`
- 수정: `README.md`
- 생성: `docs/architecture.md`
- 생성: `docs/state-model.md`
- 생성: `docs/offline-strategy.md`

## 작업 1: build-logic과 모듈 그래프 초기 구성

**대상 파일:**
- 생성: `build-logic/settings.gradle.kts`
- 생성: `build-logic/convention/build.gradle.kts`
- 생성: `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- 생성: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`
- 생성: `build-logic/convention/src/main/kotlin/GasStationJvmLibraryConventionPlugin.kt`
- 생성: `build-logic/convention/src/main/kotlin/GasStationAndroidHiltConventionPlugin.kt`
- 생성: `build-logic/convention/src/main/kotlin/GasStationAndroidRoomConventionPlugin.kt`
- 수정: `settings.gradle.kts`
- 수정: `build.gradle.kts`
- 수정: `gradle/libs.versions.toml`
- 생성: `core/common/build.gradle.kts`
- 생성: `core/model/build.gradle.kts`
- 생성: `core/location/build.gradle.kts`
- 생성: `core/network/build.gradle.kts`
- 생성: `core/database/build.gradle.kts`
- 생성: `core/datastore/build.gradle.kts`
- 생성: `core/ui/build.gradle.kts`
- 생성: `core/designsystem/build.gradle.kts`
- 생성: `core/testing/build.gradle.kts`
- 생성: `domain/settings/build.gradle.kts`
- 생성: `domain/station/build.gradle.kts`
- 생성: `data/settings/build.gradle.kts`
- 생성: `data/station/build.gradle.kts`
- 생성: `feature/settings/build.gradle.kts`
- 생성: `feature/station-list/build.gradle.kts`
- 생성: `benchmark/build.gradle.kts`

- [ ] **단계 1: 포함 빌드와 새 모듈 목록 연결**

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GasStation"

include(
    ":app",
    ":core:common",
    ":core:model",
    ":core:ui",
    ":core:designsystem",
    ":core:testing",
    ":core:location",
    ":core:network",
    ":core:database",
    ":core:datastore",
    ":domain:settings",
    ":domain:station",
    ":data:settings",
    ":data:station",
    ":feature:settings",
    ":feature:station-list",
    ":benchmark",
)
```

- [ ] **단계 2: 컨벤션 빌드 생성**

```kotlin
// build-logic/settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
// build-logic/convention/build.gradle.kts
plugins {
    `kotlin-dsl`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:8.3.2")
    implementation(kotlin("gradle-plugin", "1.9.23"))
}

gradlePlugin {
    plugins {
        register("gasStationAndroidApplicationCompose") {
            id = "gasstation.android.application.compose"
            implementationClass = "GasStationAndroidApplicationComposeConventionPlugin"
        }
        register("gasStationAndroidLibrary") {
            id = "gasstation.android.library"
            implementationClass = "GasStationAndroidLibraryConventionPlugin"
        }
        register("gasStationJvmLibrary") {
            id = "gasstation.jvm.library"
            implementationClass = "GasStationJvmLibraryConventionPlugin"
        }
        register("gasStationAndroidHilt") {
            id = "gasstation.android.hilt"
            implementationClass = "GasStationAndroidHiltConventionPlugin"
        }
        register("gasStationAndroidRoom") {
            id = "gasstation.android.room"
            implementationClass = "GasStationAndroidRoomConventionPlugin"
        }
    }
}
```

- [ ] **단계 3: 코어 플러그인 구현 추가**

```kotlin
// build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class GasStationAndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            compileSdk = 34
            defaultConfig {
                minSdk = 24
                targetSdk = 34
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            buildFeatures.compose = true
            buildFeatures.buildConfig = true
            composeOptions.kotlinCompilerExtensionVersion = "1.5.11"
            compileOptions.sourceCompatibility = JavaVersion.VERSION_17
            compileOptions.targetCompatibility = JavaVersion.VERSION_17
        }

        dependencies {
            add("implementation", platform("androidx.compose:compose-bom:2024.04.00"))
            add("implementation", "androidx.compose.material3:material3")
            add("implementation", "androidx.activity:activity-compose:1.8.2")
        }
    }
}
```

```kotlin
// build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class GasStationAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            compileSdk = 34
            defaultConfig.minSdk = 24
            compileOptions.sourceCompatibility = JavaVersion.VERSION_17
            compileOptions.targetCompatibility = JavaVersion.VERSION_17
            testOptions.unitTests.isIncludeAndroidResources = true
        }
    }
}
```

```kotlin
// build-logic/convention/src/main/kotlin/GasStationJvmLibraryConventionPlugin.kt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

class GasStationJvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}
```

- [ ] **단계 4: Hilt/Room 플러그인과 기본 모듈 빌드 파일 추가**

```kotlin
// build-logic/convention/src/main/kotlin/GasStationAndroidHiltConventionPlugin.kt
import org.gradle.api.Plugin
import org.gradle.api.Project

class GasStationAndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.dagger.hilt.android")
        pluginManager.apply("com.google.devtools.ksp")
        target.dependencies.add("implementation", "com.google.dagger:hilt-android:2.51.1")
        target.dependencies.add("ksp", "com.google.dagger:hilt-android-compiler:2.51.1")
    }
}
```

```kotlin
// build-logic/convention/src/main/kotlin/GasStationAndroidRoomConventionPlugin.kt
import org.gradle.api.Plugin
import org.gradle.api.Project

class GasStationAndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        target.dependencies.add("implementation", "androidx.room:room-runtime:2.6.1")
        target.dependencies.add("implementation", "androidx.room:room-ktx:2.6.1")
        target.dependencies.add("ksp", "androidx.room:room-compiler:2.6.1")
    }
}
```

```kotlin
// domain/station/build.gradle.kts
plugins {
    id("gasstation.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    testImplementation(kotlin("test"))
    testImplementation("app.cash.turbine:turbine:1.1.0")
}
```

```kotlin
// feature/station-list/build.gradle.kts
plugins {
    id("gasstation.android.library")
    id("gasstation.android.hilt")
}

android {
    namespace = "com.gasstation.feature.stationlist"
    buildFeatures.compose = true
}

dependencies {
    implementation(project(":domain:station"))
    implementation(project(":domain:settings"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
}
```

- [ ] **단계 5: 빌드 그래프 존재 여부 검증**

실행: `./gradlew projects`
예상: `:core:*`, `:domain:*`, `:data:*`, `:feature:*`, `:benchmark` modules appear and configuration succeeds.

- [ ] **단계 6: 앱이 최소한 구성 단계까지 진행되는지 검증**

실행: `./gradlew :app:assembleDebug`
예상: `BUILD SUCCESSFUL` 또는 다음 작업을 가리키는 단일 소스 누락 실패이며, 플러그인 오설정은 아님

- [ ] **단계 7: 커밋**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml build-logic \
  core domain data feature benchmark
git commit -m "build: bootstrap gasstation module graph"
```

## 작업 2: 공용 값 객체와 도메인 계약 정의

**대상 파일:**
- 생성: `core/common/src/main/kotlin/com/gasstation/core/common/result/AppResult.kt`
- 생성: `core/common/src/main/kotlin/com/gasstation/core/common/dispatchers/DispatcherProvider.kt`
- 생성: `core/model/src/main/kotlin/com/gasstation/core/model/Coordinates.kt`
- 생성: `core/model/src/main/kotlin/com/gasstation/core/model/DistanceMeters.kt`
- 생성: `core/model/src/main/kotlin/com/gasstation/core/model/MoneyWon.kt`
- 생성: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/model/UserPreferences.kt`
- 생성: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/SettingsRepository.kt`
- 생성: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/ObserveUserPreferencesUseCase.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelType.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/BrandFilter.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SortOrder.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SearchRadius.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/MapProvider.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/Station.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQueryCacheKey.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt`
- 생성: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveNearbyStationsUseCase.kt`
- 테스트: `domain/station/src/test/kotlin/com/gasstation/domain/station/StationQueryCacheKeyTest.kt`
- 테스트: `domain/settings/src/test/kotlin/com/gasstation/domain/settings/UserPreferencesTest.kt`

- [ ] **단계 1: 캐시 키와 기본 선호값용 실패 테스트 작성**

```kotlin
// domain/station/src/test/kotlin/com/gasstation/domain/station/StationQueryCacheKeyTest.kt
class StationQueryCacheKeyTest {
    @Test
    fun `cache key ignores sort order and map provider`() {
        val first = StationQuery(
            coordinates = Coordinates(37.498095, 127.027610),
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
            brandFilter = BrandFilter.ALL,
            sortOrder = SortOrder.DISTANCE,
            mapProvider = MapProvider.TMAP,
        )

        val second = first.copy(
            sortOrder = SortOrder.PRICE,
            mapProvider = MapProvider.KAKAO_NAVI,
        )

        assertEquals(first.toCacheKey(bucketMeters = 250), second.toCacheKey(bucketMeters = 250))
    }
}
```

```kotlin
// domain/settings/src/test/kotlin/com/gasstation/domain/settings/UserPreferencesTest.kt
class UserPreferencesTest {
    @Test
    fun `defaults match current legacy behavior`() {
        val defaults = UserPreferences.default()

        assertEquals(SearchRadius.KM_3, defaults.searchRadius)
        assertEquals(FuelType.GASOLINE, defaults.fuelType)
        assertEquals(BrandFilter.ALL, defaults.brandFilter)
        assertEquals(SortOrder.DISTANCE, defaults.sortOrder)
        assertEquals(MapProvider.TMAP, defaults.mapProvider)
    }
}
```

- [ ] **단계 2: 도메인 테스트를 실행해 실패를 확인**

실행: `./gradlew :domain:station:test :domain:settings:test`
예상: `StationQuery`, `toCacheKey`, `UserPreferences`가 아직 없으므로 실패

- [ ] **단계 3: 공용 값 객체와 설정 계약 구현**

```kotlin
// core/model/src/main/kotlin/com/gasstation/core/model/Coordinates.kt
package com.gasstation.core.model

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)
```

```kotlin
// core/model/src/main/kotlin/com/gasstation/core/model/DistanceMeters.kt
package com.gasstation.core.model

@JvmInline
value class DistanceMeters(val value: Int)
```

```kotlin
// core/model/src/main/kotlin/com/gasstation/core/model/MoneyWon.kt
package com.gasstation.core.model

@JvmInline
value class MoneyWon(val value: Int)
```

```kotlin
// domain/settings/src/main/kotlin/com/gasstation/domain/settings/model/UserPreferences.kt
package com.gasstation.domain.settings.model

import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder

data class UserPreferences(
    val searchRadius: SearchRadius,
    val fuelType: FuelType,
    val brandFilter: BrandFilter,
    val sortOrder: SortOrder,
    val mapProvider: MapProvider,
) {
    companion object {
        fun default() = UserPreferences(
            searchRadius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
            brandFilter = BrandFilter.ALL,
            sortOrder = SortOrder.DISTANCE,
            mapProvider = MapProvider.TMAP,
        )
    }
}
```

```kotlin
// domain/settings/src/main/kotlin/com/gasstation/domain/settings/SettingsRepository.kt
package com.gasstation.domain.settings

import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeUserPreferences(): Flow<UserPreferences>
    suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences)
}
```

- [ ] **단계 4: 주유소 도메인 모델과 캐시 키 로직 구현**

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/SearchRadius.kt
enum class SearchRadius(val meters: Int) {
    KM_3(3_000),
    KM_4(4_000),
    KM_5(5_000),
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelType.kt
enum class FuelType {
    GASOLINE,
    DIESEL,
    PREMIUM_GASOLINE,
    KEROSENE,
    LPG,
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/BrandFilter.kt
enum class BrandFilter {
    ALL,
    SKE,
    GSC,
    HDO,
    SOL,
    RTO,
    RTX,
    NHO,
    ETC,
    E1G,
    SKG,
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/SortOrder.kt
enum class SortOrder {
    DISTANCE,
    PRICE,
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/MapProvider.kt
enum class MapProvider {
    TMAP,
    KAKAO_NAVI,
    NAVER_MAP,
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt
data class StationQuery(
    val coordinates: Coordinates,
    val radius: SearchRadius,
    val fuelType: FuelType,
    val brandFilter: BrandFilter,
    val sortOrder: SortOrder,
    val mapProvider: MapProvider,
) {
    fun toCacheKey(bucketMeters: Int): StationQueryCacheKey {
        val latBucket = ((coordinates.latitude * 111_000) / bucketMeters).toInt()
        val lngBucket = ((coordinates.longitude * 88_800) / bucketMeters).toInt()
        return StationQueryCacheKey(
            latitudeBucket = latBucket,
            longitudeBucket = lngBucket,
            radiusMeters = radius.meters,
            fuelType = fuelType,
        )
    }
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQueryCacheKey.kt
data class StationQueryCacheKey(
    val latitudeBucket: Int,
    val longitudeBucket: Int,
    val radiusMeters: Int,
    val fuelType: FuelType,
)
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/Station.kt
data class Station(
    val id: String,
    val name: String,
    val brandCode: String,
    val price: MoneyWon,
    val distance: DistanceMeters,
    val coordinates: Coordinates,
)
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt
data class StationSearchResult(
    val stations: List<Station>,
    val freshness: StationFreshness,
    val fetchedAt: Instant?,
)
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationFreshness.kt
sealed interface StationFreshness {
    data object Fresh : StationFreshness
    data object Stale : StationFreshness
}
```

- [ ] **단계 5: 유스케이스 틀 추가 후 테스트 재실행**

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt
interface StationRepository {
    fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult>
    suspend fun refreshNearbyStations(query: StationQuery)
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveNearbyStationsUseCase.kt
class ObserveNearbyStationsUseCase(
    private val stationRepository: StationRepository,
) {
    operator fun invoke(query: StationQuery) = stationRepository.observeNearbyStations(query)
}
```

```kotlin
// domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/ObserveUserPreferencesUseCase.kt
class ObserveUserPreferencesUseCase(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke() = settingsRepository.observeUserPreferences()
}
```

```kotlin
// domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdatePreferredSortOrderUseCase.kt
class UpdatePreferredSortOrderUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(sortOrder: SortOrder) {
        settingsRepository.updateUserPreferences { current ->
            current.copy(sortOrder = sortOrder)
        }
    }
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/RefreshNearbyStationsUseCase.kt
class RefreshNearbyStationsUseCase(
    private val stationRepository: StationRepository,
) {
    suspend operator fun invoke(query: StationQuery) {
        stationRepository.refreshNearbyStations(query)
    }
}
```

실행: `./gradlew :domain:station:test :domain:settings:test`
예상: 통과

- [ ] **단계 6: 커밋**

```bash
git add core/common core/model domain/settings domain/station
git commit -m "feat: add domain contracts for settings and station search"
```

## 작업 3: DataStore 기반 영속 설정 구현

**대상 파일:**
- 생성: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesSerializer.kt`
- 생성: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataSource.kt`
- 생성: `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- 테스트: `data/settings/src/test/kotlin/com/gasstation/data/settings/DefaultSettingsRepositoryTest.kt`

- [ ] **단계 1: 실패하는 리포지토리 테스트 작성**

```kotlin
class DefaultSettingsRepositoryTest {
    @Test
    fun `updates are persisted and re-emitted`() = runTest {
        val dataSource = InMemoryUserPreferencesDataSource(UserPreferences.default())
        val repository = DefaultSettingsRepository(dataSource)

        repository.updateUserPreferences { it.copy(sortOrder = SortOrder.PRICE) }

        assertEquals(SortOrder.PRICE, repository.observeUserPreferences().first().sortOrder)
    }
}

private class InMemoryUserPreferencesDataSource(
    initial: UserPreferences,
) : UserPreferencesDataSource {
    private val state = MutableStateFlow(initial)

    override val userPreferences: Flow<UserPreferences> = state

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }
}
```

- [ ] **단계 2: 설정 리포지토리 테스트를 실행해 실패를 확인**

실행: `./gradlew :data:settings:testDebugUnitTest`
예상: `DefaultSettingsRepository`와 `UserPreferencesDataSource`가 없으므로 실패

- [ ] **단계 3: DataStore 연동 데이터 소스 생성**

```kotlin
// core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataSource.kt
package com.gasstation.core.datastore

import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesDataSource {
    val userPreferences: Flow<UserPreferences>
    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}
```

```kotlin
// core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesSerializer.kt
package com.gasstation.core.datastore

object UserPreferencesSerializer {
    fun serialize(preferences: UserPreferences): String = listOf(
        preferences.searchRadius.name,
        preferences.fuelType.name,
        preferences.brandFilter.name,
        preferences.sortOrder.name,
        preferences.mapProvider.name,
    ).joinToString("|")
}
```

- [ ] **단계 4: 리포지토리 구현**

```kotlin
// data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt
package com.gasstation.data.settings

import com.gasstation.core.datastore.UserPreferencesDataSource
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow

class DefaultSettingsRepository(
    private val dataSource: UserPreferencesDataSource,
) : SettingsRepository {
    override fun observeUserPreferences(): Flow<UserPreferences> = dataSource.userPreferences

    override suspend fun updateUserPreferences(
        transform: (UserPreferences) -> UserPreferences,
    ) {
        dataSource.update(transform)
    }
}
```

- [ ] **단계 5: 테스트를 다시 실행한 뒤 Android 기반 DataStore 구현 추가**

실행: `./gradlew :data:settings:testDebugUnitTest`
예상: 인메모리 가짜 구현 기준으로 통과

```kotlin
// core/datastore/src/main/kotlin/com/gasstation/core/datastore/AndroidUserPreferencesDataSource.kt
class AndroidUserPreferencesDataSource(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesDataSource {
    override val userPreferences: Flow<UserPreferences> =
        dataStore.data.map { preferences ->
            UserPreferences(
                searchRadius = SearchRadius.valueOf(preferences[SEARCH_RADIUS] ?: SearchRadius.KM_3.name),
                fuelType = FuelType.valueOf(preferences[FUEL_TYPE] ?: FuelType.GASOLINE.name),
                brandFilter = BrandFilter.valueOf(preferences[BRAND_FILTER] ?: BrandFilter.ALL.name),
                sortOrder = SortOrder.valueOf(preferences[SORT_ORDER] ?: SortOrder.DISTANCE.name),
                mapProvider = MapProvider.valueOf(preferences[MAP_PROVIDER] ?: MapProvider.TMAP.name),
            )
        }

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        val next = transform(userPreferences.first())
        dataStore.edit { mutable ->
            mutable[SEARCH_RADIUS] = next.searchRadius.name
            mutable[FUEL_TYPE] = next.fuelType.name
            mutable[BRAND_FILTER] = next.brandFilter.name
            mutable[SORT_ORDER] = next.sortOrder.name
            mutable[MAP_PROVIDER] = next.mapProvider.name
        }
    }
}
```

- [ ] **단계 6: 커밋**

```bash
git add core/datastore data/settings
git commit -m "feat: add datastore-backed settings repository"
```

## 작업 4: 주유소 캐시 정책과 Room 스키마 구현

**대상 파일:**
- 생성: `core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt`
- 생성: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheEntity.kt`
- 생성: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`
- 생성: `data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt`
- 테스트: `data/station/src/test/kotlin/com/gasstation/data/station/StationCachePolicyTest.kt`

- [ ] **단계 1: 실패하는 캐시 정책 테스트 작성**

```kotlin
class StationCachePolicyTest {
    @Test
    fun `result becomes 오래된 데이터 after five minutes`() {
        val fetchedAt = Instant.parse("2026-04-18T03:00:00Z")
        val now = Instant.parse("2026-04-18T03:05:01Z")

        assertEquals(
            StationFreshness.Stale,
            StationCachePolicy(오래된 데이터After = Duration.ofMinutes(5)).freshnessOf(fetchedAt, now),
        )
    }
}
```

- [ ] **단계 2: 캐시 정책 테스트를 실행해 실패를 확인**

실행: `./gradlew :data:station:testDebugUnitTest`
예상: `StationCachePolicy`와 `StationFreshness`가 없으므로 실패

- [ ] **단계 3: 신선도 정책 구현**

```kotlin
// data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt
package com.gasstation.data.station

import com.gasstation.domain.station.model.StationFreshness
import java.time.Duration
import java.time.Instant

class StationCachePolicy(
    private val 오래된 데이터After: Duration = Duration.ofMinutes(5),
) {
    fun freshnessOf(fetchedAt: Instant, now: Instant): StationFreshness =
        if (Duration.between(fetchedAt, now) > 오래된 데이터After) {
            StationFreshness.Stale
        } else {
            StationFreshness.Fresh
        }
}
```

- [ ] **단계 4: Room 캐시 스키마 추가**

```kotlin
// core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheEntity.kt
@Entity(
    tableName = "station_cache",
    primaryKeys = ["latitudeBucket", "longitudeBucket", "radiusMeters", "fuelType", "stationId"],
)
data class StationCacheEntity(
    val latitudeBucket: Int,
    val longitudeBucket: Int,
    val radiusMeters: Int,
    val fuelType: String,
    val stationId: String,
    val brandCode: String,
    val name: String,
    val priceWon: Int,
    val distanceMeters: Double,
    val latitude: Double,
    val longitude: Double,
    val fetchedAtEpochMillis: Long,
)
```

```kotlin
// core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt
@Dao
interface StationCacheDao {
    @Query("""
        SELECT * FROM station_cache
        WHERE latitudeBucket = :latitudeBucket
          AND longitudeBucket = :longitudeBucket
          AND radiusMeters = :radiusMeters
          AND fuelType = :fuelType
    """)
    fun observeStations(
        latitudeBucket: Int,
        longitudeBucket: Int,
        radiusMeters: Int,
        fuelType: String,
    ): Flow<List<StationCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StationCacheEntity>)

    @Query("""
        DELETE FROM station_cache
        WHERE fetchedAtEpochMillis < :cutoffEpochMillis
    """)
    suspend fun pruneOlderThan(cutoffEpochMillis: Long)
}
```

- [ ] **단계 5: 단위 테스트를 다시 실행하고 스키마 컴파일을 검증**

실행: `./gradlew :data:station:testDebugUnitTest :core:database:testDebugUnitTest`
예상: 정책 테스트는 통과하고 데이터베이스 모듈 컴파일은 `BUILD SUCCESSFUL`

- [ ] **단계 6: 커밋**

```bash
git add core/database data/station
git commit -m "feat: define station cache policy and room schema"
```

## 작업 5: 네트워크 매퍼와 리포지토리 병합 경로 구현

**대상 파일:**
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetStationDto.kt`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetResponseDto.kt`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/model/KakaoTransCoordDto.kt`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/service/OpinetService.kt`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/service/KakaoService.kt`
- 생성: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`
- 생성: `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`
- 생성: `data/station/src/main/kotlin/com/gasstation/data/station/mapper/StationMappers.kt`
- 생성: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- 테스트: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`

- [ ] **단계 1: 실패하는 리포지토리 병합 테스트 작성**

```kotlin
class DefaultStationRepositoryTest {
    @Test
    fun `refresh writes remote stations into cache and excludes sort order from cache key`() = runTest {
        val query = StationQuery(
            coordinates = Coordinates(37.498095, 127.027610),
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
            brandFilter = BrandFilter.ALL,
            sortOrder = SortOrder.DISTANCE,
            mapProvider = MapProvider.TMAP,
        )
        val fakeNetwork = FakeStationRemoteDataSource(
            stations = listOf(
                OpinetStationDto(
                    stationId = "station-1",
                    name = "강남주유소",
                    brandCode = "GSC",
                    priceWon = 1689,
                    distanceMeters = 800.0,
                    gisX = 127.0276,
                    gisY = 37.4980,
                )
            )
        )
        val fakeDao = InMemoryStationCacheDao()
        val repository = DefaultStationRepository(
            stationCacheDao = fakeDao,
            remoteDataSource = fakeNetwork,
            cachePolicy = StationCachePolicy(),
            clock = Clock.fixed(Instant.parse("2026-04-18T03:00:00Z"), ZoneOffset.UTC),
        )

        repository.refreshNearbyStations(query)

        val cached = fakeDao.observeStations(
            latitudeBucket = query.toCacheKey(250).latitudeBucket,
            longitudeBucket = query.toCacheKey(250).longitudeBucket,
            radiusMeters = SearchRadius.KM_3.meters,
            fuelType = FuelType.GASOLINE.name,
        ).first()

        assertEquals(1, cached.size)
        assertEquals("station-1", cached.single().stationId)
    }
}

private class FakeStationRemoteDataSource(
    private val stations: List<OpinetStationDto>,
) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): List<OpinetStationDto> = stations
}

private class InMemoryStationCacheDao : StationCacheDao {
    private val state = MutableStateFlow<List<StationCacheEntity>>(emptyList())

    override fun observeStations(
        latitudeBucket: Int,
        longitudeBucket: Int,
        radiusMeters: Int,
        fuelType: String,
    ): Flow<List<StationCacheEntity>> = state.map { entities ->
        entities.filter {
            it.latitudeBucket == latitudeBucket &&
                it.longitudeBucket == longitudeBucket &&
                it.radiusMeters == radiusMeters &&
                it.fuelType == fuelType
        }
    }

    override suspend fun upsertAll(entities: List<StationCacheEntity>) {
        state.value = entities
    }

    override suspend fun pruneOlderThan(cutoffEpochMillis: Long) = Unit
}
```

- [ ] **단계 2: 리포지토리 테스트를 실행해 실패를 확인**

실행: `./gradlew :data:station:testDebugUnitTest`
예상: DTO, 매퍼, 리포지토리, 가짜 DAO 계약이 아직 없으므로 실패

- [ ] **단계 3: 원격 DTO와 서비스 인터페이스 구현**

```kotlin
// core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetStationDto.kt
@Serializable
data class OpinetStationDto(
    val stationId: String,
    val name: String,
    val brandCode: String,
    val priceWon: Int,
    val distanceMeters: Double,
    val gisX: Double,
    val gisY: Double,
)
```

```kotlin
// core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetResponseDto.kt
@Serializable
data class OpinetResponseDto(
    val stations: List<OpinetStationDto>,
)
```

```kotlin
// core/network/src/main/kotlin/com/gasstation/core/network/service/OpinetService.kt
interface OpinetService {
    @GET("/api/aroundAll.do")
    suspend fun findStations(
        @Query("code") code: String,
        @Query("x") x: Double,
        @Query("y") y: Double,
        @Query("radius") radius: Int,
        @Query("prodcd") fuelType: String,
        @Query("out") out: String = "json",
    ): OpinetResponseDto
}
```

- [ ] **단계 4: 매퍼와 리포지토리 구현**

```kotlin
// data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt
interface StationRemoteDataSource {
    suspend fun fetchStations(query: StationQuery): List<OpinetStationDto>
}
```

```kotlin
// data/station/src/main/kotlin/com/gasstation/data/station/mapper/StationMappers.kt
fun OpinetStationDto.toEntity(
    cacheKey: StationQueryCacheKey,
    fetchedAt: Instant,
) = StationCacheEntity(
    latitudeBucket = cacheKey.latitudeBucket,
    longitudeBucket = cacheKey.longitudeBucket,
    radiusMeters = cacheKey.radiusMeters,
    fuelType = cacheKey.fuelType.name,
    stationId = stationId,
    brandCode = brandCode,
    name = name,
    priceWon = priceWon,
    distanceMeters = distanceMeters,
    latitude = gisY,
    longitude = gisX,
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
)

fun StationCacheEntity.toDomain(
    brandFilter: BrandFilter,
    sortOrder: SortOrder,
): Station? {
    if (brandFilter != BrandFilter.ALL && brandCode != brandFilter.name) return null
    return Station(
        id = stationId,
        name = name,
        brandCode = brandCode,
        price = MoneyWon(priceWon),
        distance = DistanceMeters(distanceMeters.toInt()),
        coordinates = Coordinates(latitude = latitude, longitude = longitude),
    )
}
```

```kotlin
// data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt
class DefaultStationRepository(
    private val stationCacheDao: StationCacheDao,
    private val remoteDataSource: StationRemoteDataSource,
    private val cachePolicy: StationCachePolicy,
    private val clock: Clock,
) : StationRepository {
    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
        val cacheKey = query.toCacheKey(bucketMeters = 250)
        return stationCacheDao.observeStations(
            latitudeBucket = cacheKey.latitudeBucket,
            longitudeBucket = cacheKey.longitudeBucket,
            radiusMeters = cacheKey.radiusMeters,
            fuelType = cacheKey.fuelType.name,
        ).map { entities ->
            val fetchedAt = entities.maxOfOrNull { it.fetchedAtEpochMillis } ?: 0L
            val freshness = cachePolicy.freshnessOf(
                fetchedAt = Instant.ofEpochMilli(fetchedAt),
                now = clock.instant(),
            )
            StationSearchResult(
                stations = entities.mapNotNull { it.toDomain(query.brandFilter, query.sortOrder) }
                    .let { stations ->
                        when (query.sortOrder) {
                            SortOrder.DISTANCE -> stations.sortedBy { it.distance.value }
                            SortOrder.PRICE -> stations.sortedBy { it.price.value }
                        }
                    },
                freshness = freshness,
                fetchedAt = if (fetchedAt == 0L) null else Instant.ofEpochMilli(fetchedAt),
            )
        }
    }

    override suspend fun refreshNearbyStations(query: StationQuery) {
        val cacheKey = query.toCacheKey(bucketMeters = 250)
        val remoteStations = remoteDataSource.fetchStations(query)
        val entities = remoteStations.map { it.toEntity(cacheKey, clock.instant()) }
        stationCacheDao.upsertAll(entities)
    }
}
```

- [ ] **단계 5: 리포지토리 테스트 재실행**

실행: `./gradlew :data:station:testDebugUnitTest`
예상: 통과

- [ ] **단계 6: 커밋**

```bash
git add core/network data/station
git commit -m "feat: add station repository merge path"
```

## 작업 6: 위치 계약과 권한 상태 모델 구현

**대상 파일:**
- 생성: `core/location/src/main/kotlin/com/gasstation/core/location/LocationPermissionState.kt`
- 생성: `core/location/src/main/kotlin/com/gasstation/core/location/ForegroundLocationProvider.kt`
- 테스트: `core/location/src/test/kotlin/com/gasstation/core/location/LocationPermissionStateTest.kt`

- [ ] **단계 1: 실패하는 권한 상태 테스트 작성**

```kotlin
class LocationPermissionStateTest {
    @Test
    fun `approximate granted is not equivalent to precise granted`() {
        assertNotEquals(
            LocationPermissionState.ApproximateGranted,
            LocationPermissionState.PreciseGranted,
        )
    }
}
```

- [ ] **단계 2: 위치 테스트를 실행해 실패를 확인**

실행: `./gradlew :core:location:testDebugUnitTest`
예상: `LocationPermissionState`가 없으므로 실패

- [ ] **단계 3: 권한과 제공자 계약 추가**

```kotlin
// core/location/src/main/kotlin/com/gasstation/core/location/LocationPermissionState.kt
sealed interface LocationPermissionState {
    data object Denied : LocationPermissionState
    data object ApproximateGranted : LocationPermissionState
    data object PreciseGranted : LocationPermissionState
}
```

```kotlin
// core/location/src/main/kotlin/com/gasstation/core/location/ForegroundLocationProvider.kt
interface ForegroundLocationProvider {
    suspend fun currentLocation(permissionState: LocationPermissionState): Coordinates?
}
```

- [ ] **단계 4: 테스트 재실행**

실행: `./gradlew :core:location:testDebugUnitTest`
예상: 통과

- [ ] **단계 5: 커밋**

```bash
git add core/location
git commit -m "feat: add location permission contract"
```

## 작업 7: 영속 선호값 기반 설정 기능 구성

**대상 파일:**
- 생성: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
- 생성: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsAction.kt`
- 생성: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
- 생성: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- 생성: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`
- 테스트: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsViewModelTest.kt`

- [ ] **단계 1: 실패하는 SettingsViewModel 테스트 작성**

```kotlin
class SettingsViewModelTest {
    @Test
    fun `changing sort order persists selection`() = runTest {
        val repository = FakeSettingsRepository(UserPreferences.default())
        val viewModel = SettingsViewModel(
            observeUserPreferences = ObserveUserPreferencesUseCase(repository),
            settingsRepository = repository,
        )

        viewModel.onAction(SettingsAction.SortOrderSelected(SortOrder.PRICE))

        assertEquals(SortOrder.PRICE, viewModel.uiState.value.sortOrder)
    }
}

private class FakeSettingsRepository(
    initial: UserPreferences,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observeUserPreferences(): Flow<UserPreferences> = state

    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }
}
```

- [ ] **단계 2: 기능 테스트를 실행해 실패를 확인**

실행: `./gradlew :feature:settings:testDebugUnitTest`
예상: `SettingsViewModel`, `SettingsUiState`, `SettingsAction`이 아직 없으므로 실패

- [ ] **단계 3: 기능 상태와 액션 구현**

```kotlin
// feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt
data class SettingsUiState(
    val searchRadius: SearchRadius,
    val fuelType: FuelType,
    val brandFilter: BrandFilter,
    val sortOrder: SortOrder,
    val mapProvider: MapProvider,
) {
    companion object {
        fun from(preferences: UserPreferences) = SettingsUiState(
            searchRadius = preferences.searchRadius,
            fuelType = preferences.fuelType,
            brandFilter = preferences.brandFilter,
            sortOrder = preferences.sortOrder,
            mapProvider = preferences.mapProvider,
        )
    }
}
```

```kotlin
// feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsAction.kt
sealed interface SettingsAction {
    data class SortOrderSelected(val sortOrder: SortOrder) : SettingsAction
    data class FuelTypeSelected(val fuelType: FuelType) : SettingsAction
    data class SearchRadiusSelected(val radius: SearchRadius) : SettingsAction
    data class BrandFilterSelected(val brandFilter: BrandFilter) : SettingsAction
    data class MapProviderSelected(val mapProvider: MapProvider) : SettingsAction
}
```

- [ ] **단계 4: ViewModel과 라우트 구현**

```kotlin
// feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeUserPreferences: ObserveUserPreferencesUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        observeUserPreferences()
            .map(SettingsUiState::from)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState.from(UserPreferences.default()),
            )

    fun onAction(action: SettingsAction) {
        viewModelScope.launch {
            settingsRepository.updateUserPreferences { current ->
                when (action) {
                    is SettingsAction.SortOrderSelected -> current.copy(sortOrder = action.sortOrder)
                    is SettingsAction.FuelTypeSelected -> current.copy(fuelType = action.fuelType)
                    is SettingsAction.SearchRadiusSelected -> current.copy(searchRadius = action.radius)
                    is SettingsAction.BrandFilterSelected -> current.copy(brandFilter = action.brandFilter)
                    is SettingsAction.MapProviderSelected -> current.copy(mapProvider = action.mapProvider)
                }
            }
        }
    }
}
```

```kotlin
// feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Column {
        Text(text = "정렬 기준: ${uiState.sortOrder.name}")
        Button(onClick = { onAction(SettingsAction.SortOrderSelected(SortOrder.PRICE)) }) {
            Text("가격순")
        }
    }
}
```

- [ ] **단계 5: 기능 테스트 재실행**

실행: `./gradlew :feature:settings:testDebugUnitTest`
예상: 통과

- [ ] **단계 6: Compose 라우트 추가 및 컴파일 검증**

```kotlin
// feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}
```

실행: `./gradlew :feature:settings:compileDebugKotlin`
예상: `BUILD SUCCESSFUL`

- [ ] **단계 7: 커밋**

```bash
git add feature/settings
git commit -m "feat: add settings feature flow"
```

## 작업 8: 주유소 목록 기능, 앱 내비게이션, 외부 지도 연동 구성

**대상 파일:**
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListEffect.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- 생성: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- 생성: `app/src/main/kotlin/com/gasstation/navigation/GasStationDestination.kt`
- 생성: `app/src/main/kotlin/com/gasstation/navigation/GasStationNavHost.kt`
- 생성: `app/src/main/kotlin/com/gasstation/map/ExternalMapLauncher.kt`
- 생성: `app/src/main/kotlin/com/gasstation/MainActivity.kt`
- 수정: `app/src/main/AndroidManifest.xml`
- 테스트: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

- [ ] **단계 1: 실패하는 StationListViewModel 테스트 작성**

```kotlin
class StationListViewModelTest {
    @Test
    fun `successful refresh emits content state with 오래된 데이터 flag from repository`() = runTest {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(
                    Station(
                        id = "station-1",
                        name = "강남주유소",
                        brandCode = "GSC",
                        price = MoneyWon(1689),
                        distance = DistanceMeters(800),
                        coordinates = Coordinates(37.498095, 127.027610),
                    )
                ),
                freshness = StationFreshness.Stale,
                fetchedAt = Instant.parse("2026-04-18T03:00:00Z"),
            )
        )
        val viewModel = StationListViewModel(
            observeNearbyStations = ObserveNearbyStationsUseCase(repository),
            refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
            observeUserPreferences = ObserveUserPreferencesUseCase(
                FakeSettingsRepository(UserPreferences.default())
            ),
        )

        viewModel.onAction(StationListAction.RefreshRequested)

        assertEquals(true, viewModel.uiState.value.isStale)
        assertEquals(1, viewModel.uiState.value.stations.size)
    }
}

private class FakeStationRepository(
    private val result: StationSearchResult,
) : StationRepository {
    override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> =
        flowOf(result)

    override suspend fun refreshNearbyStations(query: StationQuery) = Unit
}
```

- [ ] **단계 2: 기능 테스트를 실행해 실패를 확인**

실행: `./gradlew :feature:station-list:testDebugUnitTest`
예상: 주유소 목록 상태, 액션, ViewModel이 아직 없으므로 실패

- [ ] **단계 3: 상태, 액션, 효과 구현**

```kotlin
// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt
data class StationListItemUiModel(
    val id: String,
    val name: String,
    val priceLabel: String,
    val distanceLabel: String,
    val latitude: Double,
    val longitude: Double,
) {
    constructor(station: Station) : this(
        id = station.id,
        name = station.name,
        priceLabel = "${station.price.value}원",
        distanceLabel = "${station.distance.value}m",
        latitude = station.coordinates.latitude,
        longitude = station.coordinates.longitude,
    )
}
```

```kotlin
// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt
data class StationListUiState(
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val isGpsEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val stations: List<StationListItemUiModel> = emptyList(),
    val selectedRadius: SearchRadius = SearchRadius.KM_3,
    val selectedFuelType: FuelType = FuelType.GASOLINE,
    val selectedSortOrder: SortOrder = SortOrder.DISTANCE,
    val message: String? = null,
)
```

```kotlin
// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt
sealed interface StationListAction {
    data object RefreshRequested : StationListAction
    data class PermissionChanged(val permissionState: LocationPermissionState) : StationListAction
    data class StationClicked(val station: StationListItemUiModel) : StationListAction
    data object RetryClicked : StationListAction
}
```

```kotlin
// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListEffect.kt
sealed interface StationListEffect {
    data class OpenExternalMap(val stationName: String, val latitude: Double, val longitude: Double) : StationListEffect
    data object OpenLocationSettings : StationListEffect
    data class ShowSnackbar(val message: String) : StationListEffect
}
```

- [ ] **단계 4: ViewModel 리듀서 구현**

```kotlin
// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt
@HiltViewModel
class StationListViewModel @Inject constructor(
    private val observeNearbyStations: ObserveNearbyStationsUseCase,
    private val refreshNearbyStations: RefreshNearbyStationsUseCase,
    observeUserPreferences: ObserveUserPreferencesUseCase,
) : ViewModel() {
    private val _effects = MutableSharedFlow<StationListEffect>()
    val effects: SharedFlow<StationListEffect> = _effects

    private val query = MutableStateFlow(
        StationQuery(
            coordinates = Coordinates(37.498095, 127.027610),
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
            brandFilter = BrandFilter.ALL,
            sortOrder = SortOrder.DISTANCE,
            mapProvider = MapProvider.TMAP,
        )
    )

    val uiState: StateFlow<StationListUiState> =
        combine(
            observeUserPreferences(),
            query.flatMapLatest(observeNearbyStations::invoke),
        ) { preferences, result ->
            StationListUiState(
                isLoading = false,
                isRefreshing = false,
                isStale = result.freshness is StationFreshness.Stale,
                stations = result.stations.map(::StationListItemUiModel),
                selectedRadius = preferences.searchRadius,
                selectedFuelType = preferences.fuelType,
                selectedSortOrder = preferences.sortOrder,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StationListUiState(isLoading = true),
        )

    fun onAction(action: StationListAction) {
        when (action) {
            StationListAction.RefreshRequested,
            StationListAction.RetryClicked -> viewModelScope.launch {
                refreshNearbyStations(query.value)
            }
            is StationListAction.PermissionChanged -> Unit
            is StationListAction.StationClicked -> viewModelScope.launch {
                _effects.emit(
                    StationListEffect.OpenExternalMap(
                        stationName = action.station.name,
                        latitude = action.station.latitude,
                        longitude = action.station.longitude,
                    )
                )
            }
        }
    }
}
```

```kotlin
// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt
@Composable
fun StationListScreen(
    uiState: StationListUiState,
    onAction: (StationListAction) -> Unit,
) {
    Column {
        if (uiState.isStale) {
            Text("오래된 결과")
        }
        uiState.stations.forEach { station ->
            Button(onClick = { onAction(StationListAction.StationClicked(station)) }) {
                Text("${station.name} ${station.priceLabel}")
            }
        }
    }
}
```

```kotlin
// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt
@Composable
fun StationListRoute(
    viewModel: StationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StationListScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}
```

- [ ] **단계 5: 단위 테스트 재실행**

실행: `./gradlew :feature:station-list:testDebugUnitTest`
예상: 통과

- [ ] **단계 6: 내비게이션과 외부 지도 실행기 추가**

```kotlin
// app/src/main/kotlin/com/gasstation/navigation/GasStationDestination.kt
sealed interface GasStationDestination {
    data object StationList : GasStationDestination
    data object Settings : GasStationDestination
}
```

```kotlin
// app/src/main/kotlin/com/gasstation/map/ExternalMapLauncher.kt
interface ExternalMapLauncher {
    fun open(provider: MapProvider, stationName: String, latitude: Double, longitude: Double)
}
```

```kotlin
// app/src/main/kotlin/com/gasstation/navigation/GasStationNavHost.kt
@Composable
fun GasStationNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "station-list") {
        composable("station-list") { StationListRoute() }
        composable("settings") { SettingsRoute() }
    }
}
```

```kotlin
// app/src/main/kotlin/com/gasstation/MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GasStationNavHost()
            }
        }
    }
}
```

- [ ] **단계 7: 앱 컴파일 검증**

실행: `./gradlew :app:assembleDebug`
예상: `BUILD SUCCESSFUL`

- [ ] **단계 8: 커밋**

```bash
git add feature/station-list app/src/main
git commit -m "feat: add station list flow and app shell"
```

## 작업 9: demo/prod 실행 모드, CI, 벤치마크, 문서 추가

**대상 파일:**
- 수정: `app/build.gradle.kts`
- 생성: `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`
- 생성: `app/src/demo/kotlin/com/gasstation/DemoSeedData.kt`
- 생성: `app/src/prod/kotlin/com/gasstation/ProdSecretsModule.kt`
- 생성: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`
- 생성: `.github/workflows/android.yml`
- 수정: `README.md`
- 생성: `docs/architecture.md`
- 생성: `docs/state-model.md`
- 생성: `docs/offline-strategy.md`

- [ ] **단계 1: 실패하는 문서 완료 기준 점검 항목 추가**

```markdown
<!-- README acceptance checklist -->
- demo flavor는 API 키 없이 실행 가능
- prod flavor 문서에 필요한 로컬 시크릿이 정리되어 있음
- 아키텍처 다이어그램이 실제 모듈 그래프를 반영함
- 오프라인 / 오래된 데이터 동작이 스크린샷 또는 상태 표로 설명된다
```

- [ ] **단계 2: demo와 prod flavor 구성**

```kotlin
// app/build.gradle.kts
android {
    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
        }
        create("prod") {
            dimension = "environment"
        }
    }
}
```

```kotlin
// app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DemoLocationModule {
    @Provides
    fun provideForegroundLocationProvider(): ForegroundLocationProvider =
        object : ForegroundLocationProvider {
            override suspend fun currentLocation(permissionState: LocationPermissionState): Coordinates =
                Coordinates(latitude = 37.498095, longitude = 127.027610)
        }
}
```

- [ ] **단계 3: 벤치마크 기본 틀과 CI 워크플로 추가**

```kotlin
// benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun collect() = rule.collect(
        packageName = "com.gasstation",
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```

```yaml
# .github/workflows/android.yml
name: Android CI

on:
  pull_request:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :app:assembleDemoDebug :domain:station:test :domain:settings:test :data:settings:testDebugUnitTest :data:station:testDebugUnitTest
```

- [ ] **단계 4: 문서와 README 업데이트 작성**

```markdown
<!-- docs/offline-strategy.md -->
# 오프라인 전략

- 캐시 키: `locationBucket + searchRadius + fuelType`
- 정렬 순서는 캐시 키에서 제외
- demo flavor는 결정적인 시드 데이터를 사용
- 오래된 데이터 기준 시간: 5분
- 새로고침 실패 후에도 마지막 성공 결과가 계속 보인다
```

```markdown
<!-- README.md additions -->
## 실행 모드

### 데모
- API 키 필요 없음
- 가짜 위치 + 픽스처 주유소 데이터
- 검토자에게 권장

### 프로덕션
- 로컬 `opinet.apikey`와 `kakao.apikey` 필요
- 실제 네트워크 + 실제 기기 위치
```

- [ ] **단계 5: 최종 검증 세트 실행**

실행: `./gradlew :app:assembleDemoDebug :benchmark:assemble :domain:station:test :data:station:testDebugUnitTest`
예상: `BUILD SUCCESSFUL`

실행(로컬 시크릿 설정 후): `./gradlew :app:assembleProdDebug`
예상: `BUILD SUCCESSFUL`

- [ ] **단계 6: 커밋**

```bash
git add app benchmark .github/workflows/android.yml README.md docs
git commit -m "docs: finalize execution modes and quality gates"
```

## 자체 점검

### 스펙 반영 범위
- 아키텍처와 모듈 그래프: 작업 1, 작업 2
- 캐시 정책과 오래된 데이터 규칙: 작업 2, 작업 4, 작업 5
- 설정 소유권과 DataStore: 작업 2, 작업 3, 작업 7
- 위치 권한과 대략 위치 대응: 작업 6, 작업 8
- 주유소 목록 상태 모델 / 리듀서 / 효과: 작업 8
- demo/prod 검토자 온보딩: 작업 9
- 벤치마크 / CI / 문서: 작업 9

### 플레이스홀더 점검
- `TBD`, `TODO`, `implement later`, `similar to` 없음
- 각 작업마다 파일 경로, 코드, 명령, 기대 결과, 커밋 메시지 포함

### 타입 일관성
- `UserPreferences`, `StationQuery`, `StationQueryCacheKey`, `StationSearchResult`, `LocationPermissionState` 이름을 전 작업에서 동일하게 사용
- `StationFreshness`는 작업 2에서 도메인 모델로 정의하고 작업 4, 작업 8에서 그대로 사용
- `ExternalMapLauncher`, `SettingsRepository`, `StationRepository` 이름을 작업 간 일관되게 유지
