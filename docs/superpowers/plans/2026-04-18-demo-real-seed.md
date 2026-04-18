# Demo Real Seed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `demo` flavor가 강남역 2번 출구 기준 실제 API에서 생성한 정적 시드를 사용하고, 반경 `3km/4km/5km`와 유종 전체를 키 없이 로컬에서 재현하게 만든다.

**Architecture:** 현재 앱이 이미 사용하는 `3km/4km/5km` 반경 계약은 그대로 유지하고, `core:network`에 JVM에서도 재사용 가능한 원격 주유소 fetcher를 만든다. 그 위에 `tools:demo-seed` CLI가 실제 API를 한 번 수집해 JSON 자산을 생성하도록 하고, 마지막으로 `app` demo startup이 JSON 자산을 읽어 `station_cache`와 `station_price_history`를 적재하고 UI 테스트와 README를 새 시드 흐름에 맞게 갱신한다.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Retrofit, OkHttp, Gson, Room, Hilt, Robolectric, MockWebServer, JUnit4

---

### Task 1: Extract a JVM-safe remote station fetcher that both the app and the seed generator can share

**Files:**
- Modify: `core/network/build.gradle.kts`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkRemoteStation.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationMappers.kt`
- Create: `core/network/src/test/kotlin/com/gasstation/core/network/station/NetworkStationFetcherTest.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`

- [ ] **Step 1: Write the failing fetcher test around the shared network path**

Create `core/network/src/test/kotlin/com/gasstation/core/network/station/NetworkStationFetcherTest.kt`:

```kotlin
package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.di.NetworkModule
import com.gasstation.core.network.di.NetworkRuntimeConfig
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class NetworkStationFetcherTest {
    @Test
    fun `fetchStations converts origin and maps station coordinates into WGS84`() = runBlocking {
        val kakaoServer = MockWebServer()
        val opinetServer = MockWebServer()
        kakaoServer.enqueue(MockResponse().setBody("""{"documents":[{"x":958321.0,"y":1945512.0}]}"""))
        opinetServer.enqueue(
            MockResponse().setBody(
                """
                {"RESULT":{"OIL":[{"UNI_ID":"station-1","OS_NM":"Gangnam","POLL_DIV_CO":"GSC","PRICE":"1689","GIS_X_COOR":"958400.0","GIS_Y_COOR":"1945600.0"}]}}
                """.trimIndent(),
            ),
        )
        kakaoServer.enqueue(MockResponse().setBody("""{"documents":[{"x":127.0276,"y":37.4979}]}"""))

        val config = NetworkRuntimeConfig(kakaoApiKey = "kakao-key", opinetApiKey = "opinet-key")
        val fetcher = NetworkStationFetcher(
            opinetService = NetworkModule.provideOpinetService(opinetServer.url("/").toString()),
            kakaoService = NetworkModule.provideKakaoService(kakaoServer.url("/").toString(), config),
            opinetApiKey = "opinet-key",
        )

        val result = fetcher.fetchStations(
            origin = Coordinates(37.497927, 127.027583),
            radius = SearchRadius.KM_3,
            fuelType = FuelType.GASOLINE,
        )

        assertEquals(listOf("station-1"), result.map { it.stationId })
        assertEquals(1689, result.single().priceWon)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :core:network:test --tests "*NetworkStationFetcherTest"
```

Expected: `BUILD FAILED` because `NetworkStationFetcher` and `NetworkRemoteStation` do not exist yet.

- [ ] **Step 3: Add the shared remote station model and fetcher**

Update `core/network/build.gradle.kts` so the module is JVM-safe:

```kotlin
plugins {
    id("gasstation.jvm.library")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":domain:station"))
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
```

Create `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkRemoteStation.kt`:

```kotlin
package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates

data class NetworkRemoteStation(
    val stationId: String,
    val name: String,
    val brandCode: String,
    val priceWon: Int,
    val coordinates: Coordinates,
)
```

Create `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt`:

```kotlin
package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.service.KakaoService
import com.gasstation.core.network.service.OpinetService
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius

class NetworkStationFetcher(
    private val opinetService: OpinetService,
    private val kakaoService: KakaoService,
    private val opinetApiKey: String,
) {
    suspend fun fetchStations(
        origin: Coordinates,
        radius: SearchRadius,
        fuelType: FuelType,
    ): List<NetworkRemoteStation> {
        val originInKtm = kakaoService.transCoord(
            x = origin.longitude,
            y = origin.latitude,
            inputCoord = WGS84,
            outputCoord = KTM,
        ).documents.firstOrNull() ?: return emptyList()

        val response = opinetService.findStations(
            code = opinetApiKey,
            x = originInKtm.x,
            y = originInKtm.y,
            radius = radius.meters,
            sort = OPINET_DISTANCE_SORT,
            fuelType = fuelType.toFuelProductCode(),
        )

        return response.result?.stations.orEmpty().mapNotNull { station ->
            station.toNetworkRemoteStation(kakaoService)
        }
    }

    private companion object {
        const val OPINET_DISTANCE_SORT = "2"
        const val WGS84 = "WGS84"
        const val KTM = "KTM"
    }
}
```

Create `core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationMappers.kt`:

```kotlin
package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.model.OpinetStationDto
import com.gasstation.core.network.service.KakaoService
import com.gasstation.domain.station.model.FuelType

internal suspend fun OpinetStationDto.toNetworkRemoteStation(
    kakaoService: KakaoService,
): NetworkRemoteStation? {
    val stationId = stationId?.takeIf(String::isNotBlank) ?: return null
    val name = name?.takeIf(String::isNotBlank) ?: return null
    val brandCode = brandCode?.takeIf(String::isNotBlank) ?: return null
    val priceWon = priceWon?.toIntOrNull() ?: return null
    val rawX = gisX?.toDoubleOrNull() ?: return null
    val rawY = gisY?.toDoubleOrNull() ?: return null

    return NetworkRemoteStation(
        stationId = stationId,
        name = name,
        brandCode = brandCode,
        priceWon = priceWon,
        coordinates = rawCoordinatesToWgs84(rawX, rawY, kakaoService) ?: return null,
    )
}

internal suspend fun rawCoordinatesToWgs84(
    rawX: Double,
    rawY: Double,
    kakaoService: KakaoService,
): Coordinates? {
    if (rawY in -90.0..90.0 && rawX in -180.0..180.0) {
        return Coordinates(latitude = rawY, longitude = rawX)
    }

    val converted = kakaoService.transCoord(
        x = rawX,
        y = rawY,
        inputCoord = "KTM",
        outputCoord = "WGS84",
    ).documents.firstOrNull() ?: return null

    return Coordinates(latitude = converted.y, longitude = converted.x)
}

fun FuelType.toFuelProductCode(): String = when (this) {
    FuelType.GASOLINE -> "B027"
    FuelType.DIESEL -> "D047"
    FuelType.PREMIUM_GASOLINE -> "B034"
    FuelType.KEROSENE -> "C004"
    FuelType.LPG -> "K015"
}
```

- [ ] **Step 4: Make `data:station` delegate to the shared fetcher instead of owning duplicate remote logic**

Update `data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt`:

```kotlin
class DefaultStationRemoteDataSource @Inject constructor(
    private val networkStationFetcher: NetworkStationFetcher,
) : StationRemoteDataSource {
    override suspend fun fetchStations(query: StationQuery): RemoteStationFetchResult {
        val stations = networkStationFetcher.fetchStations(
            origin = query.coordinates,
            radius = query.radius,
            fuelType = query.fuelType,
        ).map { station ->
            RemoteStation(
                stationId = station.stationId,
                name = station.name,
                brandCode = station.brandCode,
                priceWon = station.priceWon,
                coordinates = station.coordinates,
            )
        }

        return RemoteStationFetchResult.Success(stations)
    }
}
```

- [ ] **Step 5: Run the shared-network tests and the dependent repository tests**

Run:

```bash
./gradlew :core:network:test :data:station:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add core/network/build.gradle.kts \
  core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkRemoteStation.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationFetcher.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/station/NetworkStationMappers.kt \
  core/network/src/test/kotlin/com/gasstation/core/network/station/NetworkStationFetcherTest.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/StationRemoteDataSource.kt
git commit -m "refactor: share remote station fetch path with demo seed tooling"
```

### Task 2: Add a dedicated seed-generation CLI that fetches the full matrix and writes deterministic JSON

**Files:**
- Modify: `settings.gradle.kts`
- Create: `tools/demo-seed/build.gradle.kts`
- Create: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGeneratorMain.kt`
- Create: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt`
- Create: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedHistoryFactory.kt`
- Create: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedJsonWriter.kt`
- Create: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedQueryMatrix.kt`
- Create: `tools/demo-seed/src/test/kotlin/com/gasstation/tools/demoseed/DemoSeedGeneratorTest.kt`

- [ ] **Step 1: Write the failing matrix and deterministic history tests**

Create `tools/demo-seed/src/test/kotlin/com/gasstation/tools/demoseed/DemoSeedGeneratorTest.kt`:

```kotlin
package com.gasstation.tools.demoseed

import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSeedGeneratorTest {
    @Test
    fun `query matrix covers every approved radius and fuel type combination`() {
        val matrix = DemoSeedQueryMatrix.all()

        assertEquals(15, matrix.size)
        assertTrue(matrix.any { it.radius == SearchRadius.KM_3 && it.fuelType == FuelType.GASOLINE })
        assertTrue(matrix.any { it.radius == SearchRadius.KM_4 && it.fuelType == FuelType.DIESEL })
        assertTrue(matrix.any { it.radius == SearchRadius.KM_5 && it.fuelType == FuelType.LPG })
    }

    @Test
    fun `history factory generates stable three-point history for a station`() {
        val entries = DemoSeedHistoryFactory.createEntries(
            stationId = "station-1",
            fuelType = FuelType.GASOLINE,
            latestPriceWon = 1689,
            generatedAtEpochMillis = 1_770_000_000_000,
        )

        assertEquals(3, entries.size)
        assertEquals(1689, entries.last().priceWon)
        assertEquals(entries, DemoSeedHistoryFactory.createEntries("station-1", FuelType.GASOLINE, 1689, 1_770_000_000_000))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew :tools:demo-seed:test --tests "*DemoSeedGeneratorTest"
```

Expected: `BUILD FAILED` because the new module and generator classes do not exist yet.

- [ ] **Step 3: Add the module, CLI entrypoint, matrix, and deterministic history logic**

Update `settings.gradle.kts`:

```kotlin
include(
    ":tools:demo-seed",
)
```

Create `tools/demo-seed/build.gradle.kts`:

```kotlin
plugins {
    id("gasstation.jvm.library")
    application
}

application {
    mainClass.set("com.gasstation.tools.demoseed.DemoSeedGeneratorMainKt")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:station"))
    implementation(project(":core:network"))
    implementation(libs.converter.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logging.interceptor)
    testImplementation(libs.junit)
}

val outputFile = rootProject.layout.projectDirectory.file("app/src/demo/assets/demo-station-seed.json")
tasks.register<JavaExec>("generateDemoSeed") {
    group = "demo seed"
    description = "Fetches the approved Gangnam demo matrix and writes the demo JSON asset."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(outputFile.asFile.absolutePath)
    systemProperty("opinet.apikey", providers.gradleProperty("opinet.apikey").orNull ?: "")
    systemProperty("kakao.apikey", providers.gradleProperty("kakao.apikey").orNull ?: "")
}
```

Create `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedQueryMatrix.kt`:

```kotlin
package com.gasstation.tools.demoseed

import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.SearchRadius

data class DemoSeedQuery(val radius: SearchRadius, val fuelType: FuelType)

object DemoSeedQueryMatrix {
    fun all(): List<DemoSeedQuery> = SearchRadius.entries.flatMap { radius ->
        FuelType.entries.map { fuelType -> DemoSeedQuery(radius = radius, fuelType = fuelType) }
    }
}
```

Create `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedHistoryFactory.kt`:

```kotlin
package com.gasstation.tools.demoseed

import com.gasstation.domain.station.model.FuelType
import kotlin.math.absoluteValue
import kotlin.math.max

data class DemoSeedHistoryEntry(val priceWon: Int, val fetchedAtEpochMillis: Long)

object DemoSeedHistoryFactory {
    fun createEntries(
        stationId: String,
        fuelType: FuelType,
        latestPriceWon: Int,
        generatedAtEpochMillis: Long,
    ): List<DemoSeedHistoryEntry> {
        val seed = "${stationId}:${fuelType.name}".hashCode().absoluteValue
        val offset1 = (seed % 41) - 20
        val offset2 = (seed % 71) - 35
        return listOf(
            DemoSeedHistoryEntry(
                priceWon = max(1, latestPriceWon + offset2),
                fetchedAtEpochMillis = generatedAtEpochMillis - 48L * 60L * 60L * 1000L,
            ),
            DemoSeedHistoryEntry(
                priceWon = max(1, latestPriceWon + offset1),
                fetchedAtEpochMillis = generatedAtEpochMillis - 24L * 60L * 60L * 1000L,
            ),
            DemoSeedHistoryEntry(
                priceWon = latestPriceWon,
                fetchedAtEpochMillis = generatedAtEpochMillis,
            ),
        )
    }
}
```

- [ ] **Step 4: Implement the generator and JSON writer around the shared network fetcher**

Create `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt`:

```kotlin
package com.gasstation.tools.demoseed

import com.gasstation.core.model.Coordinates
import com.gasstation.core.network.di.NetworkModule
import com.gasstation.core.network.di.NetworkRuntimeConfig
import com.gasstation.core.network.station.NetworkStationFetcher
import kotlinx.coroutines.runBlocking
import java.io.File

class DemoSeedGenerator(
    private val fetcher: NetworkStationFetcher,
) {
    fun generate(outputFile: File, origin: Coordinates, generatedAtEpochMillis: Long) = runBlocking {
        val snapshots = DemoSeedQueryMatrix.all().map { query ->
            DemoSeedSnapshot(
                radiusMeters = query.radius.meters,
                fuelType = query.fuelType.name,
                stations = fetcher.fetchStations(origin, query.radius, query.fuelType).map { station ->
                    DemoSeedStation(
                        stationId = station.stationId,
                        brandCode = station.brandCode,
                        name = station.name,
                        priceWon = station.priceWon,
                        latitude = station.coordinates.latitude,
                        longitude = station.coordinates.longitude,
                    )
                },
            )
        }

        val history = snapshots.flatMap { snapshot ->
            snapshot.stations.map { station ->
                    DemoSeedStationHistory(
                        stationId = station.stationId,
                        fuelType = snapshot.fuelType,
                        entries = DemoSeedHistoryFactory.createEntries(
                            stationId = station.stationId,
                            fuelType = enumValueOf<com.gasstation.domain.station.model.FuelType>(snapshot.fuelType),
                            latestPriceWon = station.priceWon,
                            generatedAtEpochMillis = generatedAtEpochMillis,
                        ),
                    )
            }
        }

        outputFile.writeText(
            DemoSeedJsonWriter.write(
                DemoSeedDocument(
                    seedVersion = 1,
                    generatedAtEpochMillis = generatedAtEpochMillis,
                    origin = DemoSeedOriginJson("Gangnam Station Exit 2", origin.latitude, origin.longitude),
                    queries = snapshots,
                    history = history,
                ),
            ),
        )
    }

    companion object {
        fun fromSystemProperties(): DemoSeedGenerator {
            val config = NetworkRuntimeConfig(
                kakaoApiKey = System.getProperty("kakao.apikey"),
                opinetApiKey = System.getProperty("opinet.apikey"),
            )
            return DemoSeedGenerator(
                fetcher = NetworkStationFetcher(
                    opinetService = NetworkModule.provideOpinetService(NetworkModule.provideOpinetBaseUrl()),
                    kakaoService = NetworkModule.provideKakaoService(NetworkModule.provideKakaoBaseUrl(), config),
                    opinetApiKey = config.opinetApiKey,
                ),
            )
        }
    }
}
```

Create `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedJsonWriter.kt`:

```kotlin
package com.gasstation.tools.demoseed

import com.google.gson.GsonBuilder

data class DemoSeedDocument(
    val seedVersion: Int,
    val generatedAtEpochMillis: Long,
    val origin: DemoSeedOriginJson,
    val queries: List<DemoSeedSnapshot>,
    val history: List<DemoSeedStationHistory>,
)

data class DemoSeedOriginJson(val label: String, val latitude: Double, val longitude: Double)
data class DemoSeedSnapshot(val radiusMeters: Int, val fuelType: String, val stations: List<DemoSeedStation>)
data class DemoSeedStation(
    val stationId: String,
    val brandCode: String,
    val name: String,
    val priceWon: Int,
    val latitude: Double,
    val longitude: Double,
)
data class DemoSeedStationHistory(
    val stationId: String,
    val fuelType: String,
    val entries: List<DemoSeedHistoryEntry>,
)

object DemoSeedJsonWriter {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    fun write(document: DemoSeedDocument): String = gson.toJson(document)
}
```

Create `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGeneratorMain.kt`:

```kotlin
package com.gasstation.tools.demoseed

import com.gasstation.core.model.Coordinates
import java.io.File

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Expected output file path argument." }
    val outputFile = File(args[0])
    require(System.getProperty("opinet.apikey").isNotBlank()) { "Missing opinet.apikey" }
    require(System.getProperty("kakao.apikey").isNotBlank()) { "Missing kakao.apikey" }

    DemoSeedGenerator.fromSystemProperties().generate(
        outputFile = outputFile,
        origin = Coordinates(latitude = 37.497927, longitude = 127.027583),
        generatedAtEpochMillis = System.currentTimeMillis(),
    )
}
```

- [ ] **Step 5: Run the module tests and generate the real JSON asset**

Run:

```bash
./gradlew :tools:demo-seed:test
./gradlew :tools:demo-seed:generateDemoSeed
```

Expected: tests pass, then `app/src/demo/assets/demo-station-seed.json` is created or updated with the full 15-query matrix.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts \
  tools/demo-seed/build.gradle.kts \
  tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGeneratorMain.kt \
  tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt \
  tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedHistoryFactory.kt \
  tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedJsonWriter.kt \
  tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedQueryMatrix.kt \
  tools/demo-seed/src/test/kotlin/com/gasstation/tools/demoseed/DemoSeedGeneratorTest.kt \
  app/src/demo/assets/demo-station-seed.json
git commit -m "feat: add real-data demo seed generator"
```

### Task 3: Replace the hardcoded demo startup seed with JSON asset loading and shared Gangnam origin

**Files:**
- Create: `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedOrigin.kt`
- Create: `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetLoader.kt`
- Create: `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetModels.kt`
- Modify: `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`
- Modify: `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
- Create: `app/src/test/java/com/gasstation/demo/seed/DemoSeedAssetLoaderTest.kt`
- Create: `app/src/test/java/com/gasstation/startup/DemoSeedStartupHookTest.kt`

- [ ] **Step 1: Write the failing asset-loader and startup-hook tests**

Create `app/src/test/java/com/gasstation/demo/seed/DemoSeedAssetLoaderTest.kt`:

```kotlin
package com.gasstation.demo.seed

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoSeedAssetLoaderTest {
    @Test
    fun `loader decodes query snapshots and history rows from demo json`() {
        val document = DemoSeedAssetLoader.parse(
            """
            {
              "seedVersion": 1,
              "generatedAtEpochMillis": 1770000000000,
              "origin": { "label": "Gangnam Station Exit 2", "latitude": 37.497927, "longitude": 127.027583 },
              "queries": [
                {
                  "radiusMeters": 3000,
                  "fuelType": "GASOLINE",
                  "stations": [
                    { "stationId": "station-1", "brandCode": "GSC", "name": "Gangnam", "priceWon": 1689, "latitude": 37.498, "longitude": 127.028 }
                  ]
                }
              ],
              "history": [
                {
                  "stationId": "station-1",
                  "fuelType": "GASOLINE",
                  "entries": [
                    { "priceWon": 1670, "fetchedAtEpochMillis": 1769827200000 },
                    { "priceWon": 1689, "fetchedAtEpochMillis": 1770000000000 }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, document.queries.size)
        assertEquals("station-1", document.history.single().stationId)
    }
}
```

Create `app/src/test/java/com/gasstation/startup/DemoSeedStartupHookTest.kt`:

```kotlin
package com.gasstation.startup

import androidx.test.core.app.ApplicationProvider
import com.gasstation.demo.seed.DemoSeedAssetLoader
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoSeedStartupHookTest {
    @Test
    fun `startup hook populates both cache snapshots and price history from asset`() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val hook = DemoSeedStartupHook(DemoSeedAssetLoader())

        hook.run(application)

        val database = androidx.room.Room.databaseBuilder(
            application,
            com.gasstation.core.database.GasStationDatabase::class.java,
            com.gasstation.core.database.GasStationDatabase.DATABASE_NAME,
        ).build()

        val statement = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM station_price_history")
        statement.moveToFirst()
        assertEquals(true, statement.getInt(0) > 0)
        statement.close()
        database.close()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest --tests "*DemoSeedAssetLoaderTest" --tests "*DemoSeedStartupHookTest"
```

Expected: `BUILD FAILED` because the asset loader classes and injectable startup hook constructor do not exist yet.

- [ ] **Step 3: Add the shared origin and JSON asset model/loader**

Create `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedOrigin.kt`:

```kotlin
package com.gasstation.demo.seed

import com.gasstation.core.model.Coordinates

object DemoSeedOrigin {
    val gangnamStationExit2 = Coordinates(
        latitude = 37.497927,
        longitude = 127.027583,
    )
}
```

Create `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetModels.kt`:

```kotlin
package com.gasstation.demo.seed

data class DemoSeedDocument(
    val seedVersion: Int,
    val generatedAtEpochMillis: Long,
    val origin: DemoSeedOriginDocument,
    val queries: List<DemoSeedQueryDocument>,
    val history: List<DemoSeedHistoryDocument>,
)

data class DemoSeedOriginDocument(val label: String, val latitude: Double, val longitude: Double)
data class DemoSeedQueryDocument(val radiusMeters: Int, val fuelType: String, val stations: List<DemoSeedStationDocument>)
data class DemoSeedStationDocument(
    val stationId: String,
    val brandCode: String,
    val name: String,
    val priceWon: Int,
    val latitude: Double,
    val longitude: Double,
)
data class DemoSeedHistoryDocument(val stationId: String, val fuelType: String, val entries: List<DemoSeedHistoryEntryDocument>)
data class DemoSeedHistoryEntryDocument(val priceWon: Int, val fetchedAtEpochMillis: Long)
```

Create `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetLoader.kt`:

```kotlin
package com.gasstation.demo.seed

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DemoSeedAssetLoader {
    fun load(context: Context): DemoSeedDocument =
        parse(context.assets.open("demo-station-seed.json").bufferedReader().use { it.readText() })

    fun parse(rawJson: String): DemoSeedDocument {
        val root = JSONObject(rawJson)
        return DemoSeedDocument(
            seedVersion = root.getInt("seedVersion"),
            generatedAtEpochMillis = root.getLong("generatedAtEpochMillis"),
            origin = root.getJSONObject("origin").let { origin ->
                DemoSeedOriginDocument(
                    label = origin.getString("label"),
                    latitude = origin.getDouble("latitude"),
                    longitude = origin.getDouble("longitude"),
                )
            },
            queries = root.getJSONArray("queries").mapObjects { query ->
                DemoSeedQueryDocument(
                    radiusMeters = query.getInt("radiusMeters"),
                    fuelType = query.getString("fuelType"),
                    stations = query.getJSONArray("stations").mapObjects { station ->
                        DemoSeedStationDocument(
                            stationId = station.getString("stationId"),
                            brandCode = station.getString("brandCode"),
                            name = station.getString("name"),
                            priceWon = station.getInt("priceWon"),
                            latitude = station.getDouble("latitude"),
                            longitude = station.getDouble("longitude"),
                        )
                    },
                )
            },
            history = root.getJSONArray("history").mapObjects { history ->
                DemoSeedHistoryDocument(
                    stationId = history.getString("stationId"),
                    fuelType = history.getString("fuelType"),
                    entries = history.getJSONArray("entries").mapObjects { entry ->
                        DemoSeedHistoryEntryDocument(
                            priceWon = entry.getInt("priceWon"),
                            fetchedAtEpochMillis = entry.getLong("fetchedAtEpochMillis"),
                        )
                    },
                )
            },
        )
    }
}

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }
```

- [ ] **Step 4: Make demo runtime consume the asset instead of hardcoded station entities**

Update `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`:

```kotlin
    private val reviewerCoordinates = DemoSeedOrigin.gangnamStationExit2
```

Update `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`:

```kotlin
class DemoSeedStartupHook @Inject constructor(
    private val assetLoader: DemoSeedAssetLoader,
) : AppStartupHook {
    override fun run(application: Application) {
        val document = assetLoader.load(application)
        val database = Room.databaseBuilder(
            application,
            GasStationDatabase::class.java,
            GasStationDatabase.DATABASE_NAME,
        ).fallbackToDestructiveMigration(dropAllTables = true).build()

        runBlocking {
            document.queries.forEach { query ->
                val cacheKey = StationQuery(
                    coordinates = DemoSeedOrigin.gangnamStationExit2,
                    radius = SearchRadius.entries.first { it.meters == query.radiusMeters },
                    fuelType = FuelType.valueOf(query.fuelType),
                    brandFilter = BrandFilter.ALL,
                    sortOrder = SortOrder.DISTANCE,
                    mapProvider = MapProvider.TMAP,
                ).toCacheKey(bucketMeters = CACHE_BUCKET_METERS)

                database.stationCacheDao().replaceSnapshot(
                    latitudeBucket = cacheKey.latitudeBucket,
                    longitudeBucket = cacheKey.longitudeBucket,
                    radiusMeters = cacheKey.radiusMeters,
                    fuelType = cacheKey.fuelType.name,
                    entities = query.stations.map { station ->
                        StationCacheEntity(
                            latitudeBucket = cacheKey.latitudeBucket,
                            longitudeBucket = cacheKey.longitudeBucket,
                            radiusMeters = cacheKey.radiusMeters,
                            fuelType = cacheKey.fuelType.name,
                            stationId = station.stationId,
                            brandCode = station.brandCode,
                            name = station.name,
                            priceWon = station.priceWon,
                            latitude = station.latitude,
                            longitude = station.longitude,
                            fetchedAtEpochMillis = document.generatedAtEpochMillis,
                        )
                    },
                )
            }

            database.stationPriceHistoryDao().insertAll(
                document.history.flatMap { row ->
                    row.entries.map { entry ->
                        StationPriceHistoryEntity(
                            stationId = row.stationId,
                            fuelType = row.fuelType,
                            priceWon = entry.priceWon,
                            fetchedAtEpochMillis = entry.fetchedAtEpochMillis,
                        )
                    }
                },
            )
        }

        database.close()
    }
}
```

- [ ] **Step 5: Run the app unit tests for demo asset parsing and startup seeding**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest --tests "*DemoSeedAssetLoaderTest" --tests "*DemoSeedStartupHookTest" :app:testDebugUnitTest --tests "*AppStartupGraphTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedOrigin.kt \
  app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetLoader.kt \
  app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetModels.kt \
  app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt \
  app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt \
  app/src/test/java/com/gasstation/demo/seed/DemoSeedAssetLoaderTest.kt \
  app/src/test/java/com/gasstation/startup/DemoSeedStartupHookTest.kt
git commit -m "feat: load demo station seed from generated asset"
```

### Task 4: Refresh the demo integration surface, regenerate the asset, and document the workflow

**Files:**
- Modify: `app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt`
- Modify: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `app/src/demo/assets/demo-station-seed.json`

- [ ] **Step 1: Write the failing demo-flow assertion that no longer depends on a hardcoded station name**

Update `app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt`:

```kotlin
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("관심 주유소 토글")
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onAllNodesWithContentDescription("관심 주유소 토글")
            .onFirst()
            .performClick()

        rule.onNodeWithText("관심 비교").performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("관심 주유소 카드")
                .fetchSemanticsNodes().isNotEmpty()
        }
```

- [ ] **Step 2: Run the instrumentation test to verify it fails before semantics are updated**

Run:

```bash
./gradlew :app:connectedDemoDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gasstation.StationPortfolioFlowTest
```

Expected: `BUILD FAILED` because the watchlist cards do not yet expose the stable content description the new assertion expects.

- [ ] **Step 3: Add stable watchlist semantics, regenerate the JSON asset, and update docs**

Update the watchlist row composable to expose a stable test hook:

```kotlin
modifier = Modifier.semantics { contentDescription = "관심 주유소 카드" }
```

Regenerate the asset after all code changes:

```bash
./gradlew :tools:demo-seed:generateDemoSeed
```

Update `README.md`:

```md
- `demo` flavor는 `app/src/demo/assets/demo-station-seed.json`에 저장된 실제 API 기반 시드를 사용합니다.
- 시드를 다시 생성하려면 로컬 Gradle 속성 `opinet.apikey`, `kakao.apikey`를 설정한 뒤 `./gradlew :tools:demo-seed:generateDemoSeed`를 실행합니다.
```

Update `docs/architecture.md`:

```md
- `demo`는 강남역 2번 출구 고정 위치와 JSON 자산 기반 Room seed를 사용한다.
- 시드 생성은 별도 CLI가 실제 API를 한 번 호출해 수행하며, 앱 실행은 외부 네트워크에 의존하지 않는다.
```

- [ ] **Step 4: Run the full verification matrix**

Run:

```bash
./gradlew :core:network:test \
  :domain:station:test \
  :core:datastore:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:assembleDemoDebug \
  :app:connectedDemoDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt \
  feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt \
  README.md \
  docs/architecture.md \
  app/src/demo/assets/demo-station-seed.json
git commit -m "feat: ship api-backed demo seed flow"
```
