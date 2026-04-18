# GasStation Multi-Module Rearchitecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `app`에 남아 있는 레거시 단일모듈 구조를 제거하고, `core`/`data`/`domain`/`feature`가 실제 책임대로 동작하는 멀티모듈 구조로 재편한다.

**Architecture:** `app`은 런처와 composition root만 유지하고, 앱 테마는 `core:designsystem`, 위치 구현은 `core:location`, 네트워크 런타임 설정은 `app -> core:network` 주입 구조로 이동시킨다. 이어서 `domain:settings`와 `data:station` 의존을 정리하고, 마지막에 `app` 내부 레거시 소스와 중복 DI/라이브러리 의존을 한 번에 제거한다.

**Tech Stack:** Kotlin, Android Gradle Plugin, Hilt, Jetpack Compose, Retrofit, Room, DataStore, JUnit4

---

### Task 1: Materialize `core:designsystem` and move the app theme out of `app`

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Color.kt`
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Typo.kt`
- Create: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt`
- Create: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeSurfaceTest.kt`
- Modify: `core/designsystem/build.gradle.kts`
- Modify: `app/src/main/java/com/gasstation/MainActivity.kt`
- Delete: `app/src/main/java/com/gasstation/ui/theme/Color.kt`
- Delete: `app/src/main/java/com/gasstation/ui/theme/Typo.kt`
- Delete: `app/src/main/java/com/gasstation/ui/theme/Theme.kt`

- [ ] **Step 1: Write the failing surface test for the new theme home**

```kotlin
package com.gasstation.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class GasStationThemeSurfaceTest {
    @Test
    fun `theme entry point lives in core designsystem package`() {
        assertEquals(
            "com.gasstation.core.designsystem.GasStationThemeKt",
            GasStationTheme::class.java.name.substringBefore("$$"),
        )
    }
}
```

- [ ] **Step 2: Add test dependencies and run the test to verify it fails**

Update `core/designsystem/build.gradle.kts` dependencies with:

```kotlin
testImplementation(libs.junit)
```

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests "*GasStationThemeSurfaceTest"
```

Expected: `BUILD FAILED` with an unresolved reference or missing symbol for `GasStationTheme`.

- [ ] **Step 3: Implement the theme files in `core:designsystem`**

Move the current app theme code into the new module with package names changed to `com.gasstation.core.designsystem`.

`core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt`

```kotlin
package com.gasstation.core.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ColorPrimaryDark,
)

private val LightColorScheme = lightColorScheme(
    primary = ColorPrimary,
)

@Composable
fun GasStationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
```

Replicate `Color.kt` and `Typo.kt` from the existing `app` theme package with the same values and package rename only.

- [ ] **Step 4: Repoint the app entry point to the shared theme and remove the old app theme files**

Update `app/src/main/java/com/gasstation/MainActivity.kt` import:

```kotlin
import com.gasstation.core.designsystem.GasStationTheme
```

Delete the old `app/src/main/java/com/gasstation/ui/theme/*` files after the new imports compile.

- [ ] **Step 5: Run focused verification**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests "*GasStationThemeSurfaceTest"
./gradlew :app:compileDemoDebugKotlin
```

Expected:

- First command: `BUILD SUCCESSFUL`
- Second command: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add \
  core/designsystem/build.gradle.kts \
  core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Color.kt \
  core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt \
  core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Typo.kt \
  core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeSurfaceTest.kt \
  app/src/main/java/com/gasstation/MainActivity.kt \
  app/src/main/java/com/gasstation/ui/theme
git commit -m "refactor: move app theme into core designsystem"
```

### Task 2: Move Android location implementation and binding into `core:location`

**Files:**
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/AndroidForegroundLocationProvider.kt`
- Create: `core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt`
- Create: `core/location/src/test/kotlin/com/gasstation/core/location/AndroidForegroundLocationProviderSurfaceTest.kt`
- Modify: `app/src/main/java/com/gasstation/di/LocationModule.kt`
- Delete: `app/src/main/java/com/gasstation/location/AndroidForegroundLocationProvider.kt`

- [ ] **Step 1: Write a failing surface test for the provider implementation**

Create `core/location/src/test/kotlin/com/gasstation/core/location/AndroidForegroundLocationProviderSurfaceTest.kt`:

```kotlin
package com.gasstation.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidForegroundLocationProviderSurfaceTest {
    @Test
    fun `android foreground provider is owned by core location`() {
        assertEquals(
            "com.gasstation.core.location.AndroidForegroundLocationProvider",
            AndroidForegroundLocationProvider::class.qualifiedName,
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :core:location:testDebugUnitTest --tests "*AndroidForegroundLocationProviderSurfaceTest"
```

Expected: `BUILD FAILED` because `AndroidForegroundLocationProvider` does not yet exist in `core:location`.

- [ ] **Step 3: Move the implementation class into `core:location`**

Create `core/location/src/main/kotlin/com/gasstation/core/location/AndroidForegroundLocationProvider.kt` with the current implementation and package rename only:

```kotlin
package com.gasstation.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.gasstation.BuildConfig
import com.gasstation.core.model.Coordinates
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidForegroundLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForegroundLocationProvider {
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(permissionState: LocationPermissionState): Coordinates? {
        loadDemoCoordinates(permissionState)?.let { return it }
        if (permissionState == LocationPermissionState.Denied) return null

        val priority = when (permissionState) {
            LocationPermissionState.PreciseGranted -> Priority.PRIORITY_HIGH_ACCURACY
            LocationPermissionState.ApproximateGranted -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            LocationPermissionState.Denied -> return null
        }
        val client = LocationServices.getFusedLocationProviderClient(context)

        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(priority, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    continuation.resume(
                        location?.let { Coordinates(it.latitude, it.longitude) },
                    )
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }

    private fun loadDemoCoordinates(permissionState: LocationPermissionState): Coordinates? {
        if (!BuildConfig.DEMO_MODE) return null

        return runCatching {
            val type = Class.forName("com.gasstation.DemoLocationModule")
            val instance = type.getField("INSTANCE").get(null)
            type.getMethod("currentLocation", LocationPermissionState::class.java)
                .invoke(instance, permissionState) as? Coordinates
        }.getOrNull()
    }
}
```

- [ ] **Step 4: Move the Hilt binding into `core:location` and reduce app ownership**

Create `core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt`:

```kotlin
package com.gasstation.core.location

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    @Singleton
    fun provideForegroundLocationProvider(
        @ApplicationContext context: Context,
    ): ForegroundLocationProvider = AndroidForegroundLocationProvider(context)
}
```

Shrink `app/src/main/java/com/gasstation/di/LocationModule.kt` to map-launcher responsibility only:

```kotlin
package com.gasstation.di

import com.gasstation.map.ExternalMapLauncher
import com.gasstation.map.IntentExternalMapLauncher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    @Singleton
    fun provideExternalMapLauncher(
        launcher: IntentExternalMapLauncher,
    ): ExternalMapLauncher = launcher
}
```

Delete `app/src/main/java/com/gasstation/location/AndroidForegroundLocationProvider.kt` after the new binding compiles.

- [ ] **Step 5: Run focused verification**

Run:

```bash
./gradlew :core:location:testDebugUnitTest --tests "*LocationPermissionStateTest" --tests "*AndroidForegroundLocationProviderSurfaceTest"
./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListViewModelTest"
./gradlew :app:compileDemoDebugKotlin
```

Expected: all commands `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add \
  core/location/src/main/kotlin/com/gasstation/core/location/AndroidForegroundLocationProvider.kt \
  core/location/src/main/kotlin/com/gasstation/core/location/LocationModule.kt \
  core/location/src/test/kotlin/com/gasstation/core/location/AndroidForegroundLocationProviderSurfaceTest.kt \
  app/src/main/java/com/gasstation/di/LocationModule.kt \
  app/src/main/java/com/gasstation/location/AndroidForegroundLocationProvider.kt
git commit -m "refactor: move android location implementation into core"
```

### Task 3: Replace `BuildConfig` reflection in `core:network` with explicit app-provided runtime config

**Files:**
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt`
- Create: `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt`
- Modify: `core/network/build.gradle.kts`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`
- Create: `app/src/main/java/com/gasstation/di/AppConfigModule.kt`
- Delete: `app/src/main/java/com/gasstation/di/NetworkModule.kt`

- [ ] **Step 1: Write the failing test for the new runtime config object**

Create `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt`:

```kotlin
package com.gasstation.core.network.di

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkRuntimeConfigTest {
    @Test
    fun `runtime config keeps externally provided api keys`() {
        val config = NetworkRuntimeConfig(
            kakaoApiKey = "kakao-key",
            opinetApiKey = "opinet-key",
        )

        assertEquals("kakao-key", config.kakaoApiKey)
        assertEquals("opinet-key", config.opinetApiKey)
    }
}
```

- [ ] **Step 2: Add test dependencies and run the test to verify it fails**

Update `core/network/build.gradle.kts`:

```kotlin
testImplementation(libs.junit)
```

Run:

```bash
./gradlew :core:network:testDebugUnitTest --tests "*NetworkRuntimeConfigTest"
```

Expected: `BUILD FAILED` because `NetworkRuntimeConfig` does not exist yet.

- [ ] **Step 3: Create the shared config type and refactor `core:network` to consume it**

Create `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt`:

```kotlin
package com.gasstation.core.network.di

data class NetworkRuntimeConfig(
    val kakaoApiKey: String,
    val opinetApiKey: String,
)
```

Refactor `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt` to remove:

```kotlin
private const val APP_BUILD_CONFIG_CLASS = "com.gasstation.BuildConfig"
private fun readBuildConfigString(fieldName: String): String = ...
```

and replace with injected config:

```kotlin
@Provides
@Singleton
fun provideKakaoService(
    @Named("kakaoBaseUrl") baseUrl: String,
    config: NetworkRuntimeConfig,
): KakaoService = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(
        defaultOkHttpClient().newBuilder()
            .addInterceptor(KakaoAuthorizationInterceptor(config.kakaoApiKey))
            .build(),
    )
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(KakaoService::class.java)
```

Pass `config.opinetApiKey` through Hilt to the station remote data source the same way it is used now.

- [ ] **Step 4: Provide the config from the app module and remove the legacy network module**

Create `app/src/main/java/com/gasstation/di/AppConfigModule.kt`:

```kotlin
package com.gasstation.di

import com.gasstation.BuildConfig
import com.gasstation.core.network.di.NetworkRuntimeConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    @Provides
    @Singleton
    fun provideNetworkRuntimeConfig(): NetworkRuntimeConfig = NetworkRuntimeConfig(
        kakaoApiKey = BuildConfig.KAKAO_API_KEY,
        opinetApiKey = BuildConfig.OPINET_API_KEY,
    )
}
```

Delete `app/src/main/java/com/gasstation/di/NetworkModule.kt`.

- [ ] **Step 5: Run focused verification**

Run:

```bash
./gradlew :core:network:testDebugUnitTest --tests "*NetworkRuntimeConfigTest"
./gradlew :data:station:testDebugUnitTest --tests "*DefaultStationRepositoryTest"
./gradlew :app:compileProdDebugKotlin
```

Expected: all commands `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add \
  core/network/build.gradle.kts \
  core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt \
  core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt \
  app/src/main/java/com/gasstation/di/AppConfigModule.kt \
  app/src/main/java/com/gasstation/di/NetworkModule.kt
git commit -m "refactor: inject network runtime config from app"
```

### Task 4: Tighten `domain:settings` and `data:station` module boundaries

**Files:**
- Modify: `domain/settings/build.gradle.kts`
- Modify: `data/station/build.gradle.kts`
- Modify: `domain/settings/src/test/kotlin/com/gasstation/domain/settings/UserPreferencesTest.kt`
- Modify: `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt`
- Modify: `feature/settings/build.gradle.kts`
- Modify: `feature/station-list/build.gradle.kts`

- [ ] **Step 1: Extend the contract tests to freeze the intended public domain surface**

Add assertions that `UserPreferences.default()` still exposes the same default values after dependency cleanup and that the station-domain enums remain transport-free.

Example addition to `domain/settings/src/test/kotlin/com/gasstation/domain/settings/UserPreferencesTest.kt`:

```kotlin
@Test
fun `defaults stay aligned with station domain value objects`() {
    val defaults = UserPreferences.default()

    assertEquals(SearchRadius.KM_3, defaults.searchRadius)
    assertEquals(FuelType.GASOLINE, defaults.fuelType)
    assertEquals(BrandFilter.ALL, defaults.brandFilter)
    assertEquals(SortOrder.DISTANCE, defaults.sortOrder)
    assertEquals(MapProvider.TMAP, defaults.mapProvider)
}
```

- [ ] **Step 2: Run the domain tests before touching Gradle dependencies**

Run:

```bash
./gradlew :domain:settings:test --tests "*UserPreferencesTest"
./gradlew :domain:station:test --tests "*DomainContractSurfaceTest"
```

Expected: both commands pass before the dependency edits.

- [ ] **Step 3: Remove the invalid Gradle edge and unused data dependency**

Update `domain/settings/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.app.cash.turbine)
}
```

Update `data/station/build.gradle.kts` to remove:

```kotlin
implementation(project(":domain:settings"))
```

Do not change `UserPreferences` or station-domain enums in this task; the goal is dependency cleanup, not model redesign.

- [ ] **Step 4: Remove transitive dependence assumptions from feature modules**

Keep the explicit direct dependencies already present in feature modules and verify no file in `feature:*` imports app-internal or removed transitive packages.

Run:

```bash
rg -n "com\\.gasstation\\.(ui|viewmodel|domain\\.repository|data\\.network|extensions|common)" feature
```

Expected: no matches.

- [ ] **Step 5: Run focused verification**

Run:

```bash
./gradlew :domain:settings:test
./gradlew :domain:station:test
./gradlew :data:station:testDebugUnitTest --tests "*DefaultStationRepositoryTest"
./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsViewModelTest"
./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListViewModelTest"
```

Expected: all commands `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add \
  domain/settings/build.gradle.kts \
  data/station/build.gradle.kts \
  domain/settings/src/test/kotlin/com/gasstation/domain/settings/UserPreferencesTest.kt \
  domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt \
  feature/settings/build.gradle.kts \
  feature/station-list/build.gradle.kts
git commit -m "refactor: tighten settings and station module boundaries"
```

### Task 5: Remove the legacy app source tree and simplify `app` dependencies

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml` only if component declarations must be updated
- Delete: `app/src/main/java/com/gasstation/common/ResultWrapper.kt`
- Delete: `app/src/main/java/com/gasstation/common/ServerError.kt`
- Delete: `app/src/main/java/com/gasstation/const/Const.kt`
- Delete: `app/src/main/java/com/gasstation/data/network/KakaoInterceptor.kt`
- Delete: `app/src/main/java/com/gasstation/data/network/KakaoService.kt`
- Delete: `app/src/main/java/com/gasstation/data/network/OpinetService.kt`
- Delete: `app/src/main/java/com/gasstation/domain/model/*`
- Delete: `app/src/main/java/com/gasstation/domain/repository/*`
- Delete: `app/src/main/java/com/gasstation/extensions/*`
- Delete: `app/src/main/java/com/gasstation/ui/*`
- Delete: `app/src/main/java/com/gasstation/viewmodel/HomeViewModel.kt`
- Delete: `app/src/main/java/com/gasstation/di/AppModule.kt`
- Delete: `app/src/main/java/com/gasstation/di/PreferenceModule.kt`

- [ ] **Step 1: Prove the new entry path is isolated from legacy code before deletion**

Run:

```bash
rg -n "com\\.gasstation\\.(ui\\.|viewmodel|domain\\.repository|data\\.network|extensions|common)" \
  app/src/main/java/com/gasstation/MainActivity.kt \
  app/src/main/java/com/gasstation/navigation \
  feature \
  data \
  domain \
  core
```

Expected: no matches outside the legacy `app/src/main/java/com/gasstation/ui` tree itself.

- [ ] **Step 2: Trim `app/build.gradle.kts` to composition-root dependencies only**

Replace the current dependency block with the direct module and library set still needed by the launcher app:

```kotlin
dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:location"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:station-list"))
    implementation(project(":data:settings"))
    implementation(project(":data:station"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.appcompat)
    implementation(libs.timber)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}
```

Remove direct `retrofit`, `room.runtime`, `play.services.location`, `converter.gson`, `logging.interceptor`, `constraintlayout.compose`, `androidx.material`, and `accompanist.permissions` from the app module when they are no longer needed directly by app-owned code.

- [ ] **Step 3: Delete the legacy source tree in one pass**

Delete the listed legacy files only after the app dependency block and new imports compile. The remaining `app` sources should be:

```text
App.kt
MainActivity.kt
navigation/*
map/ExternalMapLauncher.kt
di/AppConfigModule.kt
di/LocationModule.kt
```

- [ ] **Step 4: Run focused verification**

Run:

```bash
./gradlew :app:compileDemoDebugKotlin
./gradlew :app:testDemoDebugUnitTest
rg -n "com\\.gasstation\\.(ui\\.|viewmodel|domain\\.repository|data\\.network|extensions|common)" app/src/main/java feature data domain core
```

Expected:

- Both Gradle commands: `BUILD SUCCESSFUL`
- Final `rg`: no matches

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java app/src/main/AndroidManifest.xml
git commit -m "refactor: remove legacy app single-module sources"
```

### Task 6: Run full verification and capture final architecture evidence

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/superpowers/specs/2026-04-18-multimodule-rearchitecture-design.md` only if implementation changed the agreed structure

- [ ] **Step 1: Run the complete verification suite**

Run:

```bash
./gradlew test
./gradlew assembleDemoDebug
./gradlew assembleProdDebug
```

Expected: all commands `BUILD SUCCESSFUL`.

- [ ] **Step 2: Record the final module edges and leftover app files**

Run:

```bash
find app/src/main/java -type f | sort
./gradlew :app:dependencies --configuration demoDebugRuntimeClasspath
```

Expected:

- App sources are limited to the composition root files from Task 5.
- The dependency report no longer shows legacy app-owned network/location/theme implementations.

- [ ] **Step 3: Update architecture docs to reflect the final ownership**

Add a short “Module Ownership” section to `docs/architecture.md` with content like:

```md
## Module Ownership

- `app`: application entry points, top-level navigation, flavor hooks, app config DI
- `core:designsystem`: app theme and design tokens
- `core:location`: location contracts and Android foreground location implementation
- `core:network`: Retrofit services and injected runtime config consumption
- `data:*`: repository implementations
- `domain:*`: contracts, use cases, pure models
- `feature:*`: Compose routes, screens, and view models
```

- [ ] **Step 4: Re-run the smallest doc-sensitive check and inspect the diff**

Run:

```bash
git diff --stat
git diff -- docs/architecture.md
```

Expected: the diff shows only the planned architecture documentation updates.

- [ ] **Step 5: Commit**

```bash
git add docs/architecture.md docs/superpowers/specs/2026-04-18-multimodule-rearchitecture-design.md
git commit -m "docs: update module ownership architecture"
```
