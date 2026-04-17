# GasStation Reference Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 레거시 단일 모듈 `GasStation` 앱을 멀티 모듈, 클린 아키텍처, Flow 중심 상태 모델, demo/prod 실행 전략을 갖춘 면접용 reference app으로 재구성한다.

**Architecture:** 먼저 계약과 상태를 고정한 뒤 모듈 경계를 세우고, 그 위에 DataStore, Room, Flow reducer, feature UI를 얹는다. 캐시 정책, 권한 정책, 설정 소유권, demo/prod 분리를 구현 초기에 고정해서 이후 단계가 흔들리지 않게 만든다.

**Tech Stack:** Kotlin, Gradle convention plugins, Jetpack Compose, Coroutines/Flow, Hilt, DataStore, Room, Retrofit/OkHttp, Turbine, JUnit, Compose UI Test, Macrobenchmark

---

## Scope Check

이 계획은 하나의 앱을 완성하는 단일 계획이다. 설정, 위치, 검색, 지도 handoff, demo/prod, 품질 계층이 모두 포함되지만 서로 독립 서비스가 아니라 하나의 사용자 흐름과 모듈 그래프를 공유한다. 따라서 별도 계획으로 쪼개지 않고, **계약 -> 데이터 -> 프레젠테이션 -> 앱 통합 -> 품질 강화** 순으로 하나의 계획에 담는다.

## File Structure

### Root / Build
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/GasStationJvmLibraryConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/GasStationAndroidHiltConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/GasStationAndroidRoomConventionPlugin.kt`

### Core
- Create: `core/common/build.gradle.kts`
- Create: `core/common/src/main/kotlin/com/gasstation/core/common/result/AppResult.kt`
- Create: `core/common/src/main/kotlin/com/gasstation/core/common/dispatchers/DispatcherProvider.kt`
- Create: `core/model/build.gradle.kts`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/Coordinates.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/DistanceMeters.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/MoneyWon.kt`
- Create: `core/ui/build.gradle.kts`
- Create: `core/designsystem/build.gradle.kts`
- Create: `core/testing/build.gradle.kts`
- Create: `core/location/build.gradle.kts`
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/LocationPermissionState.kt`
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/ForegroundLocationProvider.kt`
- Create: `core/network/build.gradle.kts`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetStationDto.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetResponseDto.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/model/KakaoTransCoordDto.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/service/OpinetService.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/service/KakaoService.kt`
- Create: `core/database/build.gradle.kts`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheEntity.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`
- Create: `core/datastore/build.gradle.kts`
- Create: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesSerializer.kt`
- Create: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataSource.kt`

### Domain
- Create: `domain/settings/build.gradle.kts`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/model/UserPreferences.kt`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/SettingsRepository.kt`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/ObserveUserPreferencesUseCase.kt`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/UpdatePreferredSortOrderUseCase.kt`
- Create: `domain/station/build.gradle.kts`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelType.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/BrandFilter.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SortOrder.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SearchRadius.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/MapProvider.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/Station.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationFreshness.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQueryCacheKey.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveNearbyStationsUseCase.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/RefreshNearbyStationsUseCase.kt`

### Data
- Create: `data/settings/build.gradle.kts`
- Create: `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- Create: `data/station/build.gradle.kts`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/mapper/StationMappers.kt`

### Feature
- Create: `feature/settings/build.gradle.kts`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsAction.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`
- Create: `feature/station-list/build.gradle.kts`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListEffect.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`

### App / Benchmark / Docs
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/gasstation/MainActivity.kt`
- Create: `app/src/main/kotlin/com/gasstation/navigation/GasStationNavHost.kt`
- Create: `app/src/main/kotlin/com/gasstation/navigation/GasStationDestination.kt`
- Create: `app/src/main/kotlin/com/gasstation/map/ExternalMapLauncher.kt`
- Create: `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`
- Create: `app/src/demo/kotlin/com/gasstation/DemoSeedData.kt`
- Create: `app/src/prod/kotlin/com/gasstation/ProdSecretsModule.kt`
- Create: `benchmark/build.gradle.kts`
- Create: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`
- Modify: `README.md`
- Create: `docs/architecture.md`
- Create: `docs/state-model.md`
- Create: `docs/offline-strategy.md`

## Task 1: Bootstrap build-logic and the module graph

**Files:**
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/GasStationAndroidApplicationComposeConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/GasStationAndroidLibraryConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/GasStationJvmLibraryConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/GasStationAndroidHiltConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/GasStationAndroidRoomConventionPlugin.kt`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `core/common/build.gradle.kts`
- Create: `core/model/build.gradle.kts`
- Create: `core/location/build.gradle.kts`
- Create: `core/network/build.gradle.kts`
- Create: `core/database/build.gradle.kts`
- Create: `core/datastore/build.gradle.kts`
- Create: `core/ui/build.gradle.kts`
- Create: `core/designsystem/build.gradle.kts`
- Create: `core/testing/build.gradle.kts`
- Create: `domain/settings/build.gradle.kts`
- Create: `domain/station/build.gradle.kts`
- Create: `data/settings/build.gradle.kts`
- Create: `data/station/build.gradle.kts`
- Create: `feature/settings/build.gradle.kts`
- Create: `feature/station-list/build.gradle.kts`
- Create: `benchmark/build.gradle.kts`

- [ ] **Step 1: Wire in the included build and new module list**

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

- [ ] **Step 2: Create the convention build**

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

- [ ] **Step 3: Add the core plugin implementations**

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

- [ ] **Step 4: Add Hilt/Room plugins and skeletal module build files**

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

- [ ] **Step 5: Verify the build graph exists**

Run: `./gradlew projects`  
Expected: `:core:*`, `:domain:*`, `:data:*`, `:feature:*`, `:benchmark` modules appear and configuration succeeds.

- [ ] **Step 6: Verify the app can at least configure**

Run: `./gradlew :app:assembleDebug`  
Expected: `BUILD SUCCESSFUL` or a single missing-source failure that points to the next task, not plugin misconfiguration.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml build-logic \
  core domain data feature benchmark
git commit -m "build: bootstrap gasstation module graph"
```

## Task 2: Define shared value objects and domain contracts

**Files:**
- Create: `core/common/src/main/kotlin/com/gasstation/core/common/result/AppResult.kt`
- Create: `core/common/src/main/kotlin/com/gasstation/core/common/dispatchers/DispatcherProvider.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/Coordinates.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/DistanceMeters.kt`
- Create: `core/model/src/main/kotlin/com/gasstation/core/model/MoneyWon.kt`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/model/UserPreferences.kt`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/SettingsRepository.kt`
- Create: `domain/settings/src/main/kotlin/com/gasstation/domain/settings/usecase/ObserveUserPreferencesUseCase.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelType.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/BrandFilter.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SortOrder.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/SearchRadius.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/MapProvider.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/Station.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQueryCacheKey.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveNearbyStationsUseCase.kt`
- Test: `domain/station/src/test/kotlin/com/gasstation/domain/station/StationQueryCacheKeyTest.kt`
- Test: `domain/settings/src/test/kotlin/com/gasstation/domain/settings/UserPreferencesTest.kt`

- [ ] **Step 1: Write the failing domain tests for cache-key and preference defaults**

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

- [ ] **Step 2: Run the domain tests to verify they fail**

Run: `./gradlew :domain:station:test :domain:settings:test`  
Expected: FAIL because `StationQuery`, `toCacheKey`, and `UserPreferences` do not exist yet.

- [ ] **Step 3: Implement the shared value objects and settings contract**

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

- [ ] **Step 4: Implement station domain models and cache-key logic**

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

- [ ] **Step 5: Add use case shells and rerun tests**

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

Run: `./gradlew :domain:station:test :domain:settings:test`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/common core/model domain/settings domain/station
git commit -m "feat: add domain contracts for settings and station search"
```

## Task 3: Implement persisted settings with DataStore

**Files:**
- Create: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesSerializer.kt`
- Create: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesDataSource.kt`
- Create: `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- Test: `data/settings/src/test/kotlin/com/gasstation/data/settings/DefaultSettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository test**

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

- [ ] **Step 2: Run the settings repository test to verify it fails**

Run: `./gradlew :data:settings:testDebugUnitTest`  
Expected: FAIL because `DefaultSettingsRepository` and `UserPreferencesDataSource` do not exist.

- [ ] **Step 3: Create the DataStore-facing data source**

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

- [ ] **Step 4: Implement the repository**

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

- [ ] **Step 5: Re-run the test and then add the Android-backed DataStore implementation**

Run: `./gradlew :data:settings:testDebugUnitTest`  
Expected: PASS with the in-memory fake.

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

- [ ] **Step 6: Commit**

```bash
git add core/datastore data/settings
git commit -m "feat: add datastore-backed settings repository"
```

## Task 4: Implement the station cache policy and Room schema

**Files:**
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheEntity.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt`
- Test: `data/station/src/test/kotlin/com/gasstation/data/station/StationCachePolicyTest.kt`

- [ ] **Step 1: Write the failing cache-policy test**

```kotlin
class StationCachePolicyTest {
    @Test
    fun `result becomes stale after five minutes`() {
        val fetchedAt = Instant.parse("2026-04-18T03:00:00Z")
        val now = Instant.parse("2026-04-18T03:05:01Z")

        assertEquals(
            StationFreshness.Stale,
            StationCachePolicy(staleAfter = Duration.ofMinutes(5)).freshnessOf(fetchedAt, now),
        )
    }
}
```

- [ ] **Step 2: Run the cache-policy test to verify it fails**

Run: `./gradlew :data:station:testDebugUnitTest`  
Expected: FAIL because `StationCachePolicy` and `StationFreshness` do not exist.

- [ ] **Step 3: Implement the freshness policy**

```kotlin
// data/station/src/main/kotlin/com/gasstation/data/station/StationCachePolicy.kt
package com.gasstation.data.station

import com.gasstation.domain.station.model.StationFreshness
import java.time.Duration
import java.time.Instant

class StationCachePolicy(
    private val staleAfter: Duration = Duration.ofMinutes(5),
) {
    fun freshnessOf(fetchedAt: Instant, now: Instant): StationFreshness =
        if (Duration.between(fetchedAt, now) > staleAfter) {
            StationFreshness.Stale
        } else {
            StationFreshness.Fresh
        }
}
```

- [ ] **Step 4: Add the Room cache schema**

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

- [ ] **Step 5: Re-run the unit test and verify the schema compiles**

Run: `./gradlew :data:station:testDebugUnitTest :core:database:testDebugUnitTest`  
Expected: PASS for the policy test and `BUILD SUCCESSFUL` for database module compilation.

- [ ] **Step 6: Commit**

```bash
git add core/database data/station
git commit -m "feat: define station cache policy and room schema"
```

## Task 5: Implement network mappers and the repository merge path

**Files:**
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetStationDto.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetResponseDto.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/model/KakaoTransCoordDto.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/service/OpinetService.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/service/KakaoService.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/mapper/StationMappers.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Test: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository merge test**

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

- [ ] **Step 2: Run the repository test to verify it fails**

Run: `./gradlew :data:station:testDebugUnitTest`  
Expected: FAIL because DTOs, mappers, repository, and fake DAO contract do not exist yet.

- [ ] **Step 3: Implement the remote DTOs and service interfaces**

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

- [ ] **Step 4: Implement mappers and repository**

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

- [ ] **Step 5: Re-run the repository test**

Run: `./gradlew :data:station:testDebugUnitTest`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/network data/station
git commit -m "feat: add station repository merge path"
```

## Task 6: Implement the location contract and permission state model

**Files:**
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/LocationPermissionState.kt`
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/ForegroundLocationProvider.kt`
- Test: `core/location/src/test/kotlin/com/gasstation/core/location/LocationPermissionStateTest.kt`

- [ ] **Step 1: Write the failing permission-state test**

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

- [ ] **Step 2: Run the location test to verify it fails**

Run: `./gradlew :core:location:testDebugUnitTest`  
Expected: FAIL because `LocationPermissionState` does not exist.

- [ ] **Step 3: Add the permission and provider contracts**

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

- [ ] **Step 4: Re-run the test**

Run: `./gradlew :core:location:testDebugUnitTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/location
git commit -m "feat: add location permission contract"
```

## Task 7: Build the settings feature with persisted preferences

**Files:**
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsAction.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsViewModel.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- Create: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsRoute.kt`
- Test: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Write the failing SettingsViewModel test**

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

- [ ] **Step 2: Run the feature test to verify it fails**

Run: `./gradlew :feature:settings:testDebugUnitTest`  
Expected: FAIL because `SettingsViewModel`, `SettingsUiState`, and `SettingsAction` do not exist.

- [ ] **Step 3: Implement the feature state and actions**

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

- [ ] **Step 4: Implement the ViewModel and route**

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

- [ ] **Step 5: Re-run the feature test**

Run: `./gradlew :feature:settings:testDebugUnitTest`  
Expected: PASS

- [ ] **Step 6: Add the Compose route and verify it compiles**

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

Run: `./gradlew :feature:settings:compileDebugKotlin`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add feature/settings
git commit -m "feat: add settings feature flow"
```

## Task 8: Build the station list feature, app navigation, and external map handoff

**Files:**
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListEffect.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Create: `app/src/main/kotlin/com/gasstation/navigation/GasStationDestination.kt`
- Create: `app/src/main/kotlin/com/gasstation/navigation/GasStationNavHost.kt`
- Create: `app/src/main/kotlin/com/gasstation/map/ExternalMapLauncher.kt`
- Create: `app/src/main/kotlin/com/gasstation/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

- [ ] **Step 1: Write the failing StationListViewModel test**

```kotlin
class StationListViewModelTest {
    @Test
    fun `successful refresh emits content state with stale flag from repository`() = runTest {
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

- [ ] **Step 2: Run the feature test to verify it fails**

Run: `./gradlew :feature:station-list:testDebugUnitTest`  
Expected: FAIL because the station-list state, action, and view model do not exist.

- [ ] **Step 3: Implement state, action, and effect**

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

- [ ] **Step 4: Implement the ViewModel reducer**

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

- [ ] **Step 5: Re-run the unit test**

Run: `./gradlew :feature:station-list:testDebugUnitTest`  
Expected: PASS

- [ ] **Step 6: Add navigation and external map launcher**

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

- [ ] **Step 7: Verify app compilation**

Run: `./gradlew :app:assembleDebug`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add feature/station-list app/src/main
git commit -m "feat: add station list flow and app shell"
```

## Task 9: Add demo/prod execution modes, CI, benchmarks, and docs

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`
- Create: `app/src/demo/kotlin/com/gasstation/DemoSeedData.kt`
- Create: `app/src/prod/kotlin/com/gasstation/ProdSecretsModule.kt`
- Create: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`
- Create: `.github/workflows/android.yml`
- Modify: `README.md`
- Create: `docs/architecture.md`
- Create: `docs/state-model.md`
- Create: `docs/offline-strategy.md`

- [ ] **Step 1: Add the failing documentation acceptance check**

```markdown
<!-- README acceptance checklist -->
- demo flavor can run without API keys
- prod flavor documents required local secrets
- architecture diagram reflects actual module graph
- offline/stale behavior is described with screenshots or state table
```

- [ ] **Step 2: Configure demo and prod flavors**

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

- [ ] **Step 3: Add the benchmark shell and CI workflow**

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

- [ ] **Step 4: Write the docs and README updates**

```markdown
<!-- docs/offline-strategy.md -->
# Offline Strategy

- Cache key: `locationBucket + searchRadius + fuelType`
- Sort order excluded from cache key
- Demo flavor uses deterministic seed data
- Stale threshold: 5 minutes
- Last successful result remains visible after refresh failures
```

```markdown
<!-- README.md additions -->
## Run modes

### Demo
- no API keys required
- fake location + fixture station data
- recommended for reviewers

### Prod
- requires local `opinet.apikey` and `kakao.apikey`
- real network + real device location
```

- [ ] **Step 5: Run the final verification set**

Run: `./gradlew :app:assembleDemoDebug :benchmark:assemble :domain:station:test :data:station:testDebugUnitTest`  
Expected: `BUILD SUCCESSFUL`

Run (with local secrets configured): `./gradlew :app:assembleProdDebug`  
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app benchmark .github/workflows/android.yml README.md docs
git commit -m "docs: finalize execution modes and quality gates"
```

## Self-Review

### Spec coverage
- 아키텍처와 모듈 그래프: Task 1, Task 2
- 캐시 정책과 stale 규칙: Task 2, Task 4, Task 5
- 설정 소유권과 DataStore: Task 2, Task 3, Task 7
- 위치 권한과 approximate 대응: Task 6, Task 8
- station list 상태 모델 / reducer / effect: Task 8
- demo/prod reviewer onboarding: Task 9
- benchmark / CI / docs: Task 9

### Placeholder scan
- `TBD`, `TODO`, `implement later`, `similar to` 없음
- 각 task마다 파일 경로, 코드, 명령, 기대 결과, commit 메시지 포함

### Type consistency
- `UserPreferences`, `StationQuery`, `StationQueryCacheKey`, `StationSearchResult`, `LocationPermissionState` 이름을 전 task에서 동일하게 사용
- `StationFreshness`는 Task 2에서 도메인 모델로 정의하고 Task 4, Task 8에서 그대로 사용
- `ExternalMapLauncher`, `SettingsRepository`, `StationRepository` 이름을 task 간 일관되게 유지
