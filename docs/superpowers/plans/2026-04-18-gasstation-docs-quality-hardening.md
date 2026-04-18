# GasStation Docs and Quality Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GasStation을 포트폴리오 검토자 관점에서 읽기 쉬운 레퍼런스 프로젝트로 다듬기 위해, 레거시 미지원 정책과 충돌하는 코드/의존을 정리하고 공식 문서, README, CI 검증 기준을 실제 코드와 맞춘다.

**Architecture:** 먼저 실제로 쓰지 않는 호환 코드와 죽은 모듈을 제거해 코드 표면을 줄이고, 이어서 Kakao API 잔존물과 `Legacy*` 네이밍을 현행 구조에 맞게 정리한다. 그 다음 모듈 계약, 테스트 전략, 검증 매트릭스, README를 공식 문서 체계로 재작성하고 CI를 그 문서와 동일한 기준으로 고정한다.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Jetpack Compose, Hilt, Room, DataStore, JUnit4, Robolectric, GitHub Actions, Markdown docs

---

### Task 1: Remove legacy preference serialization fallback

**Files:**
- Modify: `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesSerializer.kt`
- Modify: `core/datastore/src/test/kotlin/com/gasstation/core/datastore/UserPreferencesSerializerTest.kt`

- [ ] **Step 1: Replace the legacy pipe-format tests with the new support-policy test**

Update `core/datastore/src/test/kotlin/com/gasstation/core/datastore/UserPreferencesSerializerTest.kt` so the first test becomes:

```kotlin
    @Test
    fun `readFrom falls back to default when stored data is not key value format`() = runBlocking {
        val decoded = UserPreferencesSerializer.readFrom(
            ByteArrayInputStream("KM_4|DIESEL|GSC".encodeToByteArray()),
        )

        assertEquals(UserPreferences.default(), decoded)
    }
```

Delete these old tests entirely because they encode unsupported behavior:

```text
readFrom tolerates missing fields in legacy pipe format
readFrom ignores extra fields in legacy pipe format
```

- [ ] **Step 2: Run the datastore serializer test and verify it fails**

Run:

```bash
./gradlew :core:datastore:testDebugUnitTest --tests "*UserPreferencesSerializerTest.readFrom falls back to default when stored data is not key value format"
```

Expected: `BUILD FAILED` because `readFrom` still routes non key-value content through `decodeLegacyPipeFormat`.

- [ ] **Step 3: Remove the legacy decoding branch from the serializer**

Replace the top of `core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesSerializer.kt` with:

```kotlin
object UserPreferencesSerializer : Serializer<UserPreferences> {
    private const val ENTRY_DELIMITER = "\n"
    private const val KEY_VALUE_DELIMITER = "="
    private const val VERSION = "2"
    private const val KEY_VERSION = "version"
    private const val KEY_SEARCH_RADIUS = "searchRadius"
    private const val KEY_FUEL_TYPE = "fuelType"
    private const val KEY_BRAND_FILTER = "brandFilter"
    private const val KEY_SORT_ORDER = "sortOrder"
    private const val KEY_MAP_PROVIDER = "mapProvider"

    override val defaultValue: UserPreferences = UserPreferences.default()

    override suspend fun readFrom(input: InputStream): UserPreferences {
        val encoded = input.readBytes().decodeToString().trim()
        if (encoded.isBlank()) {
            return defaultValue
        }
        if (!encoded.contains(KEY_VALUE_DELIMITER)) {
            return defaultValue
        }

        return decodeKeyValueFormat(encoded)
    }
```

Delete these serializer members entirely:

```text
private const val LEGACY_DELIMITER = "|"
private fun decodeLegacyPipeFormat(encoded: String): UserPreferences
```

- [ ] **Step 4: Keep the key-value-format regression coverage and remove dead imports**

Make the remaining test file content look like this:

```kotlin
class UserPreferencesSerializerTest {

    @Test
    fun `readFrom falls back to default when stored data is not key value format`() = runBlocking {
        val decoded = UserPreferencesSerializer.readFrom(
            ByteArrayInputStream("KM_4|DIESEL|GSC".encodeToByteArray()),
        )

        assertEquals(UserPreferences.default(), decoded)
    }

    @Test
    fun `readFrom ignores unknown fields in evolvable key value format`() = runBlocking {
        val decoded = UserPreferencesSerializer.readFrom(
            ByteArrayInputStream(
                """
                version=2
                searchRadius=KM_5
                futureField=ignored
                sortOrder=PRICE
                """.trimIndent().encodeToByteArray(),
            ),
        )

        assertEquals(
            UserPreferences.default().copy(
                searchRadius = SearchRadius.KM_5,
                sortOrder = SortOrder.PRICE,
            ),
            decoded,
        )
    }

    @Test
    fun `writeTo emits evolvable key value format`() = runBlocking {
        val output = ByteArrayOutputStream()

        UserPreferencesSerializer.writeTo(
            UserPreferences.default().copy(
                searchRadius = SearchRadius.KM_4,
                fuelType = FuelType.DIESEL,
                brandFilter = BrandFilter.GSC,
                sortOrder = SortOrder.PRICE,
                mapProvider = MapProvider.NAVER_MAP,
            ),
            output,
        )

        val encoded = output.toString(Charsets.UTF_8.name())

        assertTrue(encoded.contains("version="))
        assertTrue(encoded.contains("searchRadius=KM_4"))
        assertTrue(encoded.contains("fuelType=DIESEL"))
        assertTrue(encoded.contains("brandFilter=GSC"))
        assertTrue(encoded.contains("sortOrder=PRICE"))
        assertTrue(encoded.contains("mapProvider=NAVER_MAP"))
    }
}
```

- [ ] **Step 5: Run focused verification**

Run:

```bash
./gradlew :core:datastore:testDebugUnitTest --tests "*UserPreferencesSerializerTest"
```

Expected: `BUILD SUCCESSFUL` and all three serializer tests pass.

- [ ] **Step 6: Commit**

```bash
git add \
  core/datastore/src/main/kotlin/com/gasstation/core/datastore/UserPreferencesSerializer.kt \
  core/datastore/src/test/kotlin/com/gasstation/core/datastore/UserPreferencesSerializerTest.kt
git commit -m "refactor: drop legacy preferences format support"
```

### Task 2: Remove dead `core:common` and `core:testing` modules

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `core/database/build.gradle.kts`
- Modify: `core/datastore/build.gradle.kts`
- Modify: `core/location/build.gradle.kts`
- Modify: `core/network/build.gradle.kts`
- Modify: `data/settings/build.gradle.kts`
- Modify: `data/station/build.gradle.kts`
- Modify: `domain/settings/build.gradle.kts`
- Modify: `domain/station/build.gradle.kts`
- Modify: `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt`
- Delete: `core/common/build.gradle.kts`
- Delete: `core/common/src/main/kotlin/com/gasstation/core/common/dispatchers/DispatcherProvider.kt`
- Delete: `core/common/src/main/kotlin/com/gasstation/core/common/result/AppResult.kt`
- Delete: `core/testing/build.gradle.kts`

- [ ] **Step 1: Add a domain regression test that proves `core:common` helpers are gone**

Replace the first test in `domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt` with:

```kotlin
    @Test
    fun `domain module no longer depends on core common helper types`() {
        assertTrue(
            runCatching {
                Class.forName("com.gasstation.core.common.dispatchers.DispatcherProvider")
            }.isFailure,
        )
        assertTrue(
            runCatching {
                Class.forName("com.gasstation.core.common.result.AppResult")
            }.isFailure,
        )
    }
```

Delete these imports because they should no longer exist:

```kotlin
import com.gasstation.core.common.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
```

- [ ] **Step 2: Run the domain station test and verify it fails**

Run:

```bash
./gradlew :domain:station:test --tests "*DomainContractSurfaceTest.domain module no longer depends on core common helper types"
```

Expected: `BUILD FAILED` because both `Class.forName` calls still succeed while `:core:common` is on the test classpath.

- [ ] **Step 3: Remove the dead modules from the Gradle project graph**

Update `settings.gradle.kts` so the `include` block becomes:

```kotlin
include(
    ":app",
    ":core:model",
    ":core:designsystem",
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
    ":feature:watchlist",
    ":tools:demo-seed",
    ":benchmark",
)
```

Delete these files:

```text
core/common/build.gradle.kts
core/common/src/main/kotlin/com/gasstation/core/common/dispatchers/DispatcherProvider.kt
core/common/src/main/kotlin/com/gasstation/core/common/result/AppResult.kt
core/testing/build.gradle.kts
```

- [ ] **Step 4: Remove `project(":core:common")` from every dependent module**

Delete the `implementation(project(":core:common"))` line from each of these files:

```kotlin
// core/database/build.gradle.kts
dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
```

```kotlin
// core/datastore/build.gradle.kts
dependencies {
    implementation(project(":domain:settings"))
    implementation(project(":domain:station"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.core)
    testImplementation(libs.junit)
}
```

```kotlin
// core/location/build.gradle.kts
dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
}
```

```kotlin
// core/network/build.gradle.kts
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:station"))
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.coroutines.core)
    implementation("com.google.dagger:hilt-core:2.59.2")
    implementation("org.locationtech.proj4j:proj4j:1.4.1")
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    ksp("com.google.dagger:hilt-compiler:2.59.2")
}
```

```kotlin
// data/settings/build.gradle.kts
dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":domain:settings"))
    testImplementation(project(":domain:station"))
    testImplementation(libs.junit)
}
```

```kotlin
// data/station/build.gradle.kts
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:location"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":domain:station"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
```

```kotlin
// domain/settings/build.gradle.kts
dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:station"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.app.cash.turbine)
}
```

```kotlin
// domain/station/build.gradle.kts
dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.app.cash.turbine)
}
```

- [ ] **Step 5: Keep the domain surface test focused on actual contracts**

Leave the rest of `DomainContractSurfaceTest.kt` intact, but make sure the file header now starts like this:

```kotlin
package com.gasstation.domain.station

import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationEvent
import com.gasstation.domain.station.model.StationListEntry
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.StationSearchResult
import com.gasstation.domain.station.model.WatchedStationSummary
import kotlinx.coroutines.flow.Flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
```

- [ ] **Step 6: Run focused verification**

Run:

```bash
./gradlew \
  :domain:station:test \
  :domain:settings:test \
  :core:database:testDebugUnitTest \
  :core:datastore:testDebugUnitTest \
  :core:location:testDebugUnitTest \
  :core:network:test \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with no missing-project errors for `:core:common` or `:core:testing`.

- [ ] **Step 7: Commit**

```bash
git add \
  settings.gradle.kts \
  core/database/build.gradle.kts \
  core/datastore/build.gradle.kts \
  core/location/build.gradle.kts \
  core/network/build.gradle.kts \
  data/settings/build.gradle.kts \
  data/station/build.gradle.kts \
  domain/settings/build.gradle.kts \
  domain/station/build.gradle.kts \
  domain/station/src/test/kotlin/com/gasstation/domain/station/DomainContractSurfaceTest.kt \
  core/common \
  core/testing
git commit -m "refactor: remove unused core support modules"
```

### Task 3: Remove Kakao API runtime plumbing and the remaining dead network build weight

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/gasstation/di/AppConfigModule.kt`
- Modify: `core/network/build.gradle.kts`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt`
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`
- Modify: `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt`
- Modify: `tools/demo-seed/build.gradle.kts`
- Modify: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt`
- Delete: `core/network/src/main/kotlin/com/gasstation/core/network/service/KakaoService.kt`

- [ ] **Step 1: Replace the network config test with a no-Kakao regression**

Replace `core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt` with:

```kotlin
package com.gasstation.core.network.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRuntimeConfigTest {
    @Test
    fun `runtime config keeps only opinet api key`() {
        val config = NetworkRuntimeConfig(
            opinetApiKey = "opinet-key",
        )

        assertEquals("opinet-key", config.opinetApiKey)
        assertEquals(
            listOf("opinetApiKey"),
            NetworkRuntimeConfig::class.java.declaredFields
                .map { it.name }
                .filterNot { it.startsWith("$") }
                .sorted(),
        )
    }

    @Test
    fun `network module exposes only opinet builders`() {
        assertTrue(
            NetworkModule::class.java.declaredMethods.none { method ->
                method.name.contains("Kakao")
            },
        )
    }
}
```

- [ ] **Step 2: Run the network test and verify it fails**

Run:

```bash
./gradlew :core:network:test --tests "*NetworkRuntimeConfigTest"
```

Expected: `BUILD FAILED` because `NetworkRuntimeConfig` still requires `kakaoApiKey` and `NetworkModule` still exposes Kakao builder methods.

- [ ] **Step 3: Remove Kakao-specific fields, methods, and properties**

Update `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt` to:

```kotlin
package com.gasstation.core.network.di

data class NetworkRuntimeConfig(
    val opinetApiKey: String,
)
```

Update `core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt` to:

```kotlin
package com.gasstation.core.network.di

import com.gasstation.core.network.service.OpinetService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {
    private const val OPINET_BASE_URL = "http://www.opinet.co.kr/"

    fun provideOpinetBaseUrl(): String = OPINET_BASE_URL

    fun provideOpinetApiKey(config: NetworkRuntimeConfig): String = config.opinetApiKey

    fun provideOpinetService(
        baseUrl: String,
    ): OpinetService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpinetService::class.java)
}
```

Delete this file completely:

```text
core/network/src/main/kotlin/com/gasstation/core/network/service/KakaoService.kt
```

- [ ] **Step 4: Remove app/build and demo-seed references to Kakao keys**

Update `app/build.gradle.kts` to remove the `kakaoApiKey` property and the `KAKAO_API_KEY` build config fields. The top of the file should become:

```kotlin
plugins {
    id("gasstation.android.application.compose")
    id("gasstation.android.hilt")
}

val opinetApiKey = providers.gradleProperty("opinet.apikey").orElse("").get()
```

The two flavor blocks should become:

```kotlin
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            testInstrumentationRunner = "com.gasstation.HiltTestRunner"
            buildConfigField("boolean", "DEMO_MODE", "true")
            buildConfigField("String", "OPINET_API_KEY", "\"\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("boolean", "DEMO_MODE", "false")
            buildConfigField("String", "OPINET_API_KEY", "\"$opinetApiKey\"")
        }
```

Update `app/src/main/java/com/gasstation/di/AppConfigModule.kt` to:

```kotlin
    @Provides
    @Singleton
    fun provideNetworkRuntimeConfig(): NetworkRuntimeConfig = NetworkRuntimeConfig(
        opinetApiKey = BuildConfig.OPINET_API_KEY,
    )
```

Update `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt` so `fromSystemProperties()` becomes:

```kotlin
        fun fromSystemProperties(): DemoSeedGenerator {
            val config = NetworkRuntimeConfig(
                opinetApiKey = System.getProperty("opinet.apikey").orEmpty(),
            )
            return DemoSeedGenerator(
                fetcher = SharedNetworkSeedStationFetcher(
                    fetcher = NetworkStationFetcher(
                        opinetService = NetworkModule.provideOpinetService(NetworkModule.provideOpinetBaseUrl()),
                        opinetApiKey = config.opinetApiKey,
                    ),
                ),
            )
        }
```

Update `tools/demo-seed/build.gradle.kts` to delete the Kakao system property line so the task registration ends with:

```kotlin
tasks.register<JavaExec>("generateDemoSeed") {
    group = "demo seed"
    description = "Fetches the approved Gangnam demo matrix and writes the demo JSON asset."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(outputFile.asFile.absolutePath)
    systemProperty("opinet.apikey", providers.gradleProperty("opinet.apikey").orNull ?: "")
}
```

- [ ] **Step 5: Remove the now-unused injection build weight from `core:network`**

Update `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt` to remove `@Inject` and `@Named`:

```kotlin
package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.service.OpinetService
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius

class NetworkStationFetcher(
    private val opinetService: OpinetService,
    private val opinetApiKey: String,
) {
```

Update `core/network/build.gradle.kts` to:

```kotlin
plugins {
    id("gasstation.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:station"))
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.locationtech.proj4j:proj4j:1.4.1")
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
```

- [ ] **Step 6: Run focused verification**

Run:

```bash
./gradlew \
  :core:network:test \
  :tools:demo-seed:test \
  :app:assembleDemoDebug \
  :app:assembleProdDebug
```

Expected: `BUILD SUCCESSFUL`, no references to `KAKAO_API_KEY`, and no missing `KakaoService` compile errors.

- [ ] **Step 7: Commit**

```bash
git add \
  app/build.gradle.kts \
  app/src/main/java/com/gasstation/di/AppConfigModule.kt \
  core/network/build.gradle.kts \
  core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkModule.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfig.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt \
  core/network/src/test/kotlin/com/gasstation/core/network/di/NetworkRuntimeConfigTest.kt \
  tools/demo-seed/build.gradle.kts \
  tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/service/KakaoService.kt
git commit -m "refactor: remove unused kakao api plumbing"
```

### Task 4: Rename the public design-system surface away from `Legacy*` and delete the unused list row API

**Files:**
- Move: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/LegacyChrome.kt` to `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Chrome.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaultsTest.kt`
- Move: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/LegacyChromeContractsTest.kt` to `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`

- [ ] **Step 1: Change the compile-surface test to require the new public API names**

Update the imports and compile helper in `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaultsTest.kt` to:

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gasstation.core.designsystem.component.GasStationCard
import com.gasstation.core.designsystem.component.GasStationSectionHeading
import com.gasstation.core.designsystem.component.GasStationTopBar
```

```kotlin
@Composable
private fun chromeApisCompile() {
    GasStationTopBar(
        title = {
            Text(text = "가격순")
        },
    )

    GasStationCard {
        GasStationSectionHeading(
            title = "현재 조건",
            subtitle = "가까운 순으로 정렬합니다.",
        )
    }
}
```

Delete both `LegacyListRow` calls from the helper entirely.

- [ ] **Step 2: Run the design-system test and verify it fails**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest --tests "*GasStationThemeDefaultsTest"
```

Expected: `BUILD FAILED` because `GasStationTopBar`, `GasStationCard`, and `GasStationSectionHeading` do not exist yet.

- [ ] **Step 3: Rename the public composables and the contract types in the design-system file**

Move `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/LegacyChrome.kt` to `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Chrome.kt`.

At the top of the moved file, rename the public surface like this:

```kotlin
package com.gasstation.core.designsystem.component

enum class MaterialTypographySlot {
    TitleLarge,
    TitleMedium,
    TitleSmall,
    LabelLarge,
    LabelMedium,
    LabelSmall,
    BodyMedium,
}

enum class ChromeTextRole(
    val fallbackMaterialSlot: MaterialTypographySlot,
) {
    TopBarTitle(MaterialTypographySlot.TitleLarge),
    SectionTitle(MaterialTypographySlot.TitleMedium),
    CardTitle(MaterialTypographySlot.TitleMedium),
    PriceHero(MaterialTypographySlot.LabelLarge),
    MetricValue(MaterialTypographySlot.LabelMedium),
    Body(MaterialTypographySlot.BodyMedium),
    Meta(MaterialTypographySlot.LabelSmall),
    Chip(MaterialTypographySlot.LabelSmall),
    BannerTitle(MaterialTypographySlot.TitleSmall),
    BannerBody(MaterialTypographySlot.LabelSmall),
    ;

    fun isProminentNumericEmphasis(): Boolean = this == PriceHero || this == MetricValue
}

enum class StructuredTextSlot {
    Title,
    Body,
}

data class TextSlotRole(
    val slot: StructuredTextSlot,
    val role: ChromeTextRole,
)

enum class ChromeCardSection {
    Header,
    PrimaryMetric,
    SupportingInfo,
    Actions,
}

data class ChromeCardStructure(
    val hasHeader: Boolean = false,
    val hasPrimaryMetric: Boolean = false,
    val hasSupportingInfo: Boolean = false,
    val hasActions: Boolean = false,
) {
    fun orderedSections(): List<ChromeCardSection> = buildList {
        if (hasHeader) add(ChromeCardSection.Header)
        if (hasPrimaryMetric) add(ChromeCardSection.PrimaryMetric)
        if (hasSupportingInfo) add(ChromeCardSection.SupportingInfo)
        if (hasActions) add(ChromeCardSection.Actions)
    }
}

data class StatusBannerContent(
    val title: String,
    val body: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Status banner title is required." }
    }

    fun orderedSlots(): List<TextSlotRole> = buildList {
        add(TextSlotRole(StructuredTextSlot.Title, ChromeTextRole.BannerTitle))
        if (!body.isNullOrBlank()) {
            add(TextSlotRole(StructuredTextSlot.Body, ChromeTextRole.BannerBody))
        }
    }
}
```

Rename the public composables and tone enum:

```kotlin
@Composable
fun GasStationBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.background(ColorYellow),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GasStationTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ColorBlack,
            scrolledContainerColor = ColorBlack,
            navigationIconContentColor = ColorYellow,
            titleContentColor = ColorYellow,
            actionIconContentColor = ColorYellow,
        ),
        title = {
            ProvideTextStyle(ChromeTextRole.TopBarTitle.style()) {
                title()
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@Composable
fun GasStationCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val corner = GasStationTheme.corner
    val stroke = GasStationTheme.stroke
    val spacing = GasStationTheme.spacing

    Surface(
        modifier = modifier,
        color = ColorBlack,
        shape = RoundedCornerShape(corner.large),
    ) {
        Surface(
            modifier = Modifier.padding(stroke.default),
            color = ColorWhite,
            shape = RoundedCornerShape(corner.medium),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
                content = content,
            )
        }
    }
}

@Composable
fun GasStationSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val spacing = GasStationTheme.spacing

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Text(
            text = title,
            style = ChromeTextRole.SectionTitle.style(),
            color = ColorBlack,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = ChromeTextRole.Body.style(),
                color = ColorGray2,
            )
        }
    }
}

enum class GasStationStatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error,
}

@Composable
fun GasStationStatusBanner(
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: GasStationStatusTone = GasStationStatusTone.Neutral,
) {
    val content = StatusBannerContent(
        title = text,
        body = detail,
    )
    val colors = tone.colors()
    val corner = GasStationTheme.corner
    val spacing = GasStationTheme.spacing
    val stroke = GasStationTheme.stroke

    Surface(
        modifier = modifier,
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(corner.medium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = stroke.default,
                    color = ColorBlack,
                    shape = RoundedCornerShape(corner.medium),
                )
                .padding(
                    horizontal = spacing.space12,
                    vertical = spacing.space12,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent),
            )
            Spacer(modifier = Modifier.width(spacing.space12))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                Text(
                    text = content.title,
                    style = ChromeTextRole.BannerTitle.style(),
                    color = colors.content,
                )
                if (content.body != null) {
                    Text(
                        text = content.body,
                        style = ChromeTextRole.BannerBody.style(),
                        color = colors.content.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

private data class StatusColors(
    val container: Color,
    val content: Color,
    val accent: Color,
)

private fun GasStationStatusTone.colors(): StatusColors = when (this) {
    GasStationStatusTone.Neutral -> StatusColors(
        container = ColorWhite,
        content = ColorBlack,
        accent = ColorGray2,
    )
    GasStationStatusTone.Info -> StatusColors(
        container = ColorGray4,
        content = ColorBlack,
        accent = ColorSupportInfo,
    )
    GasStationStatusTone.Success -> StatusColors(
        container = ColorGray4,
        content = ColorBlack,
        accent = ColorSupportSuccess,
    )
    GasStationStatusTone.Warning -> StatusColors(
        container = ColorWhite,
        content = ColorBlack,
        accent = ColorYellow,
    )
    GasStationStatusTone.Error -> StatusColors(
        container = ColorGray4,
        content = ColorBlack,
        accent = ColorSupportError,
    )
}

@Composable
private fun ChromeTextRole.style(): androidx.compose.ui.text.TextStyle = when (this) {
    ChromeTextRole.TopBarTitle -> GasStationTheme.typography.topBarTitle
    ChromeTextRole.SectionTitle -> GasStationTheme.typography.sectionTitle
    ChromeTextRole.CardTitle -> GasStationTheme.typography.cardTitle
    ChromeTextRole.PriceHero -> GasStationTheme.typography.priceHero
    ChromeTextRole.MetricValue -> GasStationTheme.typography.metricValue
    ChromeTextRole.Body -> GasStationTheme.typography.body
    ChromeTextRole.Meta -> GasStationTheme.typography.meta
    ChromeTextRole.Chip -> GasStationTheme.typography.chip
    ChromeTextRole.BannerTitle -> GasStationTheme.typography.bannerTitle
    ChromeTextRole.BannerBody -> GasStationTheme.typography.bannerBody
}
```

Delete the entire unused list-row API block:

```text
data class LegacyListRowContent
fun LegacyListRow
```

- [ ] **Step 4: Rename the contracts test and the feature imports**

Move the contracts test file to `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/ChromeContractsTest.kt` and update the assertions to the renamed types. Delete the old list-row contract tests entirely. The file should become:

```kotlin
class ChromeContractsTest {
    @Test
    fun `prominent numeric emphasis is reserved for price hero and metric value`() {
        assertEquals(
            setOf(
                ChromeTextRole.PriceHero,
                ChromeTextRole.MetricValue,
            ),
            ChromeTextRole.entries
                .filter(ChromeTextRole::isProminentNumericEmphasis)
                .toSet(),
        )
    }

    @Test
    fun `text roles resolve to the approved fallback material slots`() {
        assertEquals(MaterialTypographySlot.TitleLarge, ChromeTextRole.TopBarTitle.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.TitleMedium, ChromeTextRole.SectionTitle.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.TitleMedium, ChromeTextRole.CardTitle.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.LabelLarge, ChromeTextRole.PriceHero.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.LabelMedium, ChromeTextRole.MetricValue.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.BodyMedium, ChromeTextRole.Body.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.LabelSmall, ChromeTextRole.Meta.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.LabelSmall, ChromeTextRole.Chip.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.TitleSmall, ChromeTextRole.BannerTitle.fallbackMaterialSlot)
        assertEquals(MaterialTypographySlot.LabelSmall, ChromeTextRole.BannerBody.fallbackMaterialSlot)
    }

    @Test
    fun `status banner content preserves title then body hierarchy`() {
        val content = StatusBannerContent(
            title = "위치 권한이 필요합니다",
            body = "권한을 허용하면 가까운 주유소를 불러옵니다.",
        )

        assertEquals(
            listOf(
                TextSlotRole(
                    slot = StructuredTextSlot.Title,
                    role = ChromeTextRole.BannerTitle,
                ),
                TextSlotRole(
                    slot = StructuredTextSlot.Body,
                    role = ChromeTextRole.BannerBody,
                ),
            ),
            content.orderedSlots(),
        )
    }

    @Test
    fun `chrome card structured sections always render in approved order`() {
        val structure = ChromeCardStructure(
            hasHeader = true,
            hasPrimaryMetric = true,
            hasSupportingInfo = true,
            hasActions = true,
        )

        assertEquals(
            listOf(
                ChromeCardSection.Header,
                ChromeCardSection.PrimaryMetric,
                ChromeCardSection.SupportingInfo,
                ChromeCardSection.Actions,
            ),
            structure.orderedSections(),
        )
    }
}
```

Update feature imports and calls to use the new names. For example, the import block in `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt` should become:

```kotlin
import com.gasstation.core.designsystem.component.GasStationCard
import com.gasstation.core.designsystem.component.GasStationSectionHeading
import com.gasstation.core.designsystem.component.GasStationStatusBanner
import com.gasstation.core.designsystem.component.GasStationStatusTone
import com.gasstation.core.designsystem.component.GasStationTopBar
import com.gasstation.core.designsystem.component.GasStationBackground
```

And the top-level composable call should become:

```kotlin
    GasStationBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                GasStationTopBar(
                    title = { Text(text = "주유소 찾기") },
                    actions = {
                        if (onWatchlistClick != null) {
                            IconButton(
                                modifier = Modifier.semantics {
                                    contentDescription = "북마크"
                                },
                                onClick = onWatchlistClick,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                )
                            }
                        }
                        IconButton(onClick = { onAction(StationListAction.RefreshRequested) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "설정")
                        }
                    },
                )
            },
        ) { paddingValues ->
```

Make the same import-and-call renames in:

```text
feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt
feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt
feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt
feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt
```

In `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`, also rename the tone mapper to:

```kotlin
private fun StationListBannerTone.toStatusTone(): GasStationStatusTone = when (this) {
    StationListBannerTone.Neutral -> GasStationStatusTone.Neutral
    StationListBannerTone.Info -> GasStationStatusTone.Info
    StationListBannerTone.Warning -> GasStationStatusTone.Warning
    StationListBannerTone.Error -> GasStationStatusTone.Error
}
```

And update the banner call site to:

```kotlin
            GasStationStatusBanner(
                text = banner.title,
                detail = banner.body,
                tone = banner.tone.toStatusTone(),
            )
```

- [ ] **Step 5: Run focused verification**

Run:

```bash
./gradlew \
  :core:designsystem:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, no unresolved `Legacy*` imports in the four feature modules, and the list-row API is gone.

- [ ] **Step 6: Commit**

```bash
git add \
  core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component \
  core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaultsTest.kt \
  core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component \
  feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt \
  feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt \
  feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt
git commit -m "refactor: modernize design system surface names"
```

### Task 5: Add the new official architecture and contract documents

**Files:**
- Create: `docs/module-contracts.md`
- Modify: `docs/architecture.md`
- Modify: `docs/state-model.md`
- Modify: `docs/offline-strategy.md`
- Modify: `docs/project-reading-guide.md`

- [ ] **Step 1: Create the new module-contracts document**

Create `docs/module-contracts.md` with this content:

```markdown
# 모듈 계약

GasStation은 현재 `app / feature / domain / data / core / tools / benchmark` 구조를 실제 빌드 그래프로 사용한다. 이 문서는 각 모듈이 무엇을 소유하고 어디까지 의존할 수 있는지 고정한다.

## 모듈 지도

| 모듈 | 책임 | 허용 의존 | 하지 말아야 할 일 |
| --- | --- | --- | --- |
| `app` | 앱 시작점, DI 조립, 내비게이션, flavor 연결 | `feature:*`, `data:*`, 필요한 `core:*` | 비즈니스 규칙, 캐시 정책, 화면 상태 조합 |
| `feature:*` | 화면 상태와 사용자 액션 처리 | `domain:*`, 필요한 `core:*` | Room/Retrofit 직접 접근 |
| `domain:*` | 유스케이스, 저장소 계약, 순수 모델 | `core:model` | Android/UI/DB 타입 노출 |
| `data:*` | 저장소 구현, 캐시 조합, 영속화 | `domain:*`, 필요한 `core:*` | 화면 상태 생성 |
| `core:*` | 공유 인프라와 값 객체 | 필요한 최소 공통 의존 | 앱 전용 정책 소유 |
| `tools:demo-seed` | demo seed 생성 | `core:model`, `core:network`, `domain:station` | 앱 런타임 코드 경유 |
| `benchmark` | 성능 측정 | `app` | 기능 구현 |

## 대표 진입 파일

- `app/src/main/java/com/gasstation/MainActivity.kt`
- `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`

## 검토 포인트

- 현재 지원 경로는 `demo`와 `prod`뿐이다.
- 레거시 유저/레거시 앱 호환을 위한 직렬화나 네트워크 분기는 유지하지 않는다.
- demo는 실제 포트폴리오 시연 경로이므로 예외가 아니라 공식 제품 경로다.
```

- [ ] **Step 2: Expand `docs/architecture.md` to explain intent, not just structure**

At the top of `docs/architecture.md`, replace the intro paragraph with:

```markdown
# 아키텍처

GasStation은 책임을 기준으로 멀티모듈을 나눈 Compose 안드로이드 앱이다. `app`은 composition root와 실행 환경 연결만 담당하고, 화면 상태는 `feature`, 계약은 `domain`, 저장소 구현은 `data`, 공유 인프라와 값 객체는 `core`에 둔다.

이 프로젝트는 현재 `demo`와 `prod` 경로만 지원한다. 과거 앱 버전 호환이나 레거시 유저 데이터 호환을 위한 분기는 유지하지 않고, 현재 시연/실행 경로의 명확성과 검증 가능성을 우선한다.
```

Update the `core:designsystem` bullet to the renamed API surface:

```markdown
- `core:designsystem`
  `GasStationTheme`와 색상/타이포그래피 토큰, 그리고 현재 화면들이 사용하는 `GasStationTopBar`, `GasStationCard`, `GasStationSectionHeading`, `GasStationStatusBanner`, `GasStationBackground` 같은 UI primitive를 소유한다.
```

Delete the `core:testing` bullet entirely and remove any mention of `core:common`.

Replace the `prod` flavor note with:

```markdown
- `prod`
  `ProdSecretsStartupHook`이 `opinet.apikey` 존재만 강제한다. 현재 런타임 검색 파이프라인은 Opinet API 키만 사용한다.
```

- [ ] **Step 3: Tighten state and offline docs around the new support policy**

In `docs/state-model.md`, add this paragraph immediately after the first sentence:

```markdown
이 상태 모델은 현재 지원하는 `demo`/`prod` 실행 경로만 설명한다. 과거 앱 버전이나 과거 직렬화 포맷을 유지하기 위한 호환 상태는 더 이상 지원 범위에 포함하지 않는다.
```

In `docs/offline-strategy.md`, replace the last bullet block with:

```markdown
## 운영 메모

- `prod` 런타임의 실제 검색은 Opinet API 키만 필요하다.
- `tools:demo-seed:generateDemoSeed`는 seed 재생성을 위해 `opinet.apikey`만 사용한다.
- demo는 "오프라인 fallback을 보여주기 위한 샘플 모드"가 아니라, 실제 시연에서 사용하는 고정된 공식 경로다.
```

- [ ] **Step 4: Update the reading guide to point at the new public docs and names**

In `docs/project-reading-guide.md`, update the recommended order so the top of the list becomes:

```markdown
1. `README.md`
2. `docs/architecture.md`
3. `docs/module-contracts.md`
4. `docs/state-model.md`
5. `docs/offline-strategy.md`
6. `docs/test-strategy.md`
7. `docs/verification-matrix.md`
```

Replace the design-system note with:

```markdown
- 현재 UI는 `core:designsystem`의 `GasStationTopBar`, `GasStationCard`, `GasStationSectionHeading`, `GasStationStatusBanner`, `GasStationBackground` 같은 컴포넌트를 적극 사용한다.
```

- [ ] **Step 5: Run documentation verification**

Run:

```bash
rg -n 'LegacyTopBar|LegacyChromeCard|LegacyYellowBackground|core:testing|kakao\\.apikey|KAKAO_API_KEY' \
  README.md docs
```

Expected: matches only in archived `docs/superpowers/**` history files, not in `README.md` or `docs/*.md`.

- [ ] **Step 6: Commit**

```bash
git add \
  docs/module-contracts.md \
  docs/architecture.md \
  docs/state-model.md \
  docs/offline-strategy.md \
  docs/project-reading-guide.md
git commit -m "docs: add module contracts and update architecture docs"
```

### Task 6: Align test strategy, verification matrix, CI, and README

**Files:**
- Create: `docs/test-strategy.md`
- Create: `docs/verification-matrix.md`
- Modify: `.github/workflows/android.yml`
- Modify: `README.md`

- [ ] **Step 1: Write the official test-strategy document**

Create `docs/test-strategy.md` with:

```markdown
# 테스트 전략

GasStation은 현재 지원하는 `demo`/`prod` 경로를 기준으로 테스트를 설계한다. 과거 앱 버전 호환이나 레거시 직렬화 포맷 보존은 테스트 목표가 아니다.

## 계층별 목적

- `domain`
  값 객체 불변식, 정렬/필터/캐시 키 규칙, 저장소 계약을 검증한다.
- `data` / `core infra`
  Room DAO, 캐시 정책, serializer, 네트워크 매핑, 좌표 변환을 검증한다.
- `feature`
  ViewModel 상태 전이, effect 방출, 화면 계약을 검증한다.
- `app integration`
  startup hook, demo seed 적재, 앱 리소스와 그래프 구성을 검증한다.
- `benchmark`
  cold start와 대표 이동 흐름의 성능 기준을 확인한다.

## 제외하는 것

- 과거 앱 버전 호환을 위한 serializer regression
- 더 이상 사용하지 않는 네트워크 provider
- 현재 제품 경로에 포함되지 않는 실험적 분기

## 회귀 위험이 큰 영역

- `DefaultStationRepository`의 캐시/히스토리 조합
- `StationListViewModel`의 권한/위치/refresh 상태 전이
- demo seed 로딩과 startup hook
- 문서와 CI 검증 범위의 불일치
```

- [ ] **Step 2: Write the verification matrix and update CI to match it**

Create `docs/verification-matrix.md` with:

````markdown
# 검증 매트릭스

## 빠른 로컬 확인

```bash
./gradlew :app:assembleDemoDebug :benchmark:assemble
```

## 머지 전 권장 검증

```bash
./gradlew \
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
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

## 데모 시연 전 점검

```bash
./gradlew :app:connectedDemoDebugAndroidTest
```

CI는 위 명령 중 emulator가 필요 없는 범위를 최소 신뢰 매트릭스로 사용한다.
````

Update `.github/workflows/android.yml` so the `run:` block becomes:

```yaml
        run: |
          JAVA_HOME="${JAVA_HOME}" PATH="${JAVA_HOME}/bin:${PATH}" ./gradlew \
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
            :app:assembleDebug \
            :app:assembleDemoDebug \
            :app:assembleProdDebug \
            :benchmark:assemble
```

- [ ] **Step 3: Rewrite `README.md` as a portfolio landing page**

Replace the top half of `README.md` with:

````markdown
# 주유주유소

현재 위치 주변의 주유소를 빠르게 찾고, 마지막 성공 결과를 오프라인으로 유지하며, 관심 주유소 비교까지 이어지는 멀티모듈 Android 레퍼런스 앱입니다.

## 왜 이 프로젝트인가

- `app / feature / domain / data / core` 책임 분리를 실제 코드와 문서에 맞춰 유지합니다.
- demo 시연 경로와 prod 실행 경로를 분리해 포트폴리오 검토와 실제 실행 조건을 동시에 설명합니다.
- Room 기반 snapshot + history 조합으로 stale fallback과 watchlist 비교를 구현합니다.
- 테스트 전략과 CI 매트릭스를 공식 문서로 관리합니다.

## 핵심 사용자 플로우

1. 현재 위치 기준 주유소 검색
2. 가격 변화와 관심 주유소 저장
3. 관심 주유소 비교 화면 진입

## 실행 모드

### Demo

- `demo` flavor는 API 키 없이 빌드하고 실행할 수 있습니다.
- 강남역 2번 출구 고정 위치와 승인된 demo seed 자산으로 항상 같은 시연 상태를 재현합니다.

```bash
./gradlew :app:assembleDemoDebug
```

### Prod

- `prod` flavor는 `opinet.apikey`가 필요합니다.

```bash
./gradlew :app:assembleProdDebug
```

## 문서

- [아키텍처](docs/architecture.md)
- [모듈 계약](docs/module-contracts.md)
- [상태 모델](docs/state-model.md)
- [오프라인 전략](docs/offline-strategy.md)
- [테스트 전략](docs/test-strategy.md)
- [검증 매트릭스](docs/verification-matrix.md)
- [프로젝트 읽기 가이드](docs/project-reading-guide.md)
````

Keep the existing project-specific details below that heading only if they do not duplicate the new docs.

- [ ] **Step 4: Run full verification and text-level consistency checks**

Run:

```bash
./gradlew \
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
  :app:assembleDebug \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

Then run:

```bash
rg -n 'LegacyTopBar|LegacyChromeCard|LegacyYellowBackground|core:testing|kakao\\.apikey|KAKAO_API_KEY|decodeLegacyPipeFormat' \
  README.md docs app core data domain feature tools .github --glob '!docs/superpowers/**'
```

Expected: `BUILD SUCCESSFUL` for Gradle, and the `rg` command returns no matches outside excluded history files.

- [ ] **Step 5: Commit**

```bash
git add \
  docs/test-strategy.md \
  docs/verification-matrix.md \
  .github/workflows/android.yml \
  README.md
git commit -m "docs: align portfolio docs and verification matrix"
```
