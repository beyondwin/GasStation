# GasStation National Average Station List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add low-call national-average benchmark data to the station list so each card can show `전국 평균 대비` and the list can show a small national trend summary without changing the current nearby-search flow.

**Architecture:** Keep this as the first shippable slice. Add a benchmark read path alongside the existing station snapshot path, cache it in Room, seed it in demo, and combine it only into `feature:station-list`. Do not widen scope to watchlist, regional averages, or station detail in this plan.

**Tech Stack:** Kotlin, Flow, Room, Retrofit/Gson, Hilt, Compose, Robolectric, Turbine, MockWebServer

---

## Scope Guard

This plan intentionally ships one working slice:

- In scope:
  - `avgAllPrice.do`
  - `avgRecentPrice.do`
  - `avgLastWeek.do`
  - Room benchmark cache
  - demo seed parity
  - station-list UI only
- Out of scope:
  - `feature:watchlist`
  - `detailById.do`
  - brand averages
  - regional averages

If you try to add watchlist or regional averages while doing this plan, stop. That is a second plan.

## File Map

### Create

- `domain/station/src/main/kotlin/com/gasstation/domain/station/FuelBenchmarkRepository.kt`
  Separate benchmark source-of-truth contract.
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelBenchmark.kt`
  Domain model for current national average, recent 7-day points, and last-week average.
- `domain/station/src/main/kotlin/com/gasstation/domain/station/model/AverageGap.kt`
  Shared helper for `station price vs benchmark price`.
- `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveFuelBenchmarkUseCase.kt`
- `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/RefreshFuelBenchmarkUseCase.kt`
- `domain/station/src/test/kotlin/com/gasstation/domain/station/FuelBenchmarkGapTest.kt`
- `core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetAveragePriceDtos.kt`
  DTOs for `avgAllPrice`, `avgRecentPrice`, `avgLastWeek`.
- `core/network/src/main/kotlin/com/gasstation/core/network/benchmark/NetworkFuelBenchmarkFetcher.kt`
- `core/network/src/test/kotlin/com/gasstation/core/network/benchmark/NetworkFuelBenchmarkFetcherTest.kt`
- `core/database/src/main/kotlin/com/gasstation/core/database/station/FuelBenchmarkEntity.kt`
- `core/database/src/main/kotlin/com/gasstation/core/database/station/FuelBenchmarkDailyEntity.kt`
- `core/database/src/main/kotlin/com/gasstation/core/database/station/FuelBenchmarkDao.kt`
- `core/database/src/test/kotlin/com/gasstation/core/database/station/FuelBenchmarkDaoTest.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/FuelBenchmarkRemoteDataSource.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultFuelBenchmarkRemoteDataSource.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/SeedFuelBenchmarkRemoteDataSource.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/DefaultFuelBenchmarkRepository.kt`
- `data/station/src/main/kotlin/com/gasstation/data/station/FuelBenchmarkCachePolicy.kt`
- `data/station/src/test/kotlin/com/gasstation/data/station/DefaultFuelBenchmarkRepositoryTest.kt`
- `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedFuelBenchmarkRemoteDataSource.kt`
- `app/src/demo/kotlin/com/gasstation/di/DemoFuelBenchmarkRemoteDataSourceModule.kt`

### Modify

- `core/network/src/main/kotlin/com/gasstation/core/network/service/OpinetService.kt`
  Add benchmark endpoints.
- `app/src/main/java/com/gasstation/di/AppConfigModule.kt`
  Provide `NetworkFuelBenchmarkFetcher` to Hilt.
- `core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt`
  Register new entities and `MIGRATION_3_4`.
- `core/database/src/main/kotlin/com/gasstation/core/database/DatabaseModule.kt`
  Provide `FuelBenchmarkDao`.
- `data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt`
  Bind the benchmark repository/data source and provide cache policy.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
  Observe benchmark flow and refresh it alongside nearby station refresh.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
  Add benchmark summary fields.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
  Add average-gap label/tone.
- `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
  Render a market summary card and card-level average-gap text.
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListItemUiModelTest.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
- `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetModels.kt`
- `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetLoader.kt`
- `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
- `app/src/testDemo/java/com/gasstation/demo/seed/DemoSeedAssetLoaderTest.kt`
- `app/src/testDemo/java/com/gasstation/startup/DemoSeedStartupHookTest.kt`
- `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedJsonWriter.kt`
- `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt`
- `tools/demo-seed/src/test/kotlin/com/gasstation/tools/demoseed/DemoSeedGeneratorTest.kt`
- `README.md`
- `docs/offline-strategy.md`

## Task 1: Add the Domain Benchmark Contract

**Files:**
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/FuelBenchmarkRepository.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelBenchmark.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/AverageGap.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveFuelBenchmarkUseCase.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/RefreshFuelBenchmarkUseCase.kt`
- Test: `domain/station/src/test/kotlin/com/gasstation/domain/station/FuelBenchmarkGapTest.kt`

- [ ] **Step 1: Write the failing domain test**

```kotlin
package com.gasstation.domain.station

import com.gasstation.domain.station.model.AverageGap
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelBenchmarkGapTest {
    @Test
    fun `from returns BelowBy when station price is cheaper than the benchmark`() {
        assertEquals(
            AverageGap.BelowBy(24),
            AverageGap.from(stationPriceWon = 1_635, benchmarkPriceWon = 1_659),
        )
    }

    @Test
    fun `from returns AboveBy when station price is more expensive than the benchmark`() {
        assertEquals(
            AverageGap.AboveBy(11),
            AverageGap.from(stationPriceWon = 1_670, benchmarkPriceWon = 1_659),
        )
    }

    @Test
    fun `from returns AtParity when prices match exactly`() {
        assertEquals(
            AverageGap.AtParity,
            AverageGap.from(stationPriceWon = 1_659, benchmarkPriceWon = 1_659),
        )
    }
}
```

- [ ] **Step 2: Run the domain test to verify it fails**

Run: `./gradlew :domain:station:test --tests "com.gasstation.domain.station.FuelBenchmarkGapTest"`

Expected: FAIL because `AverageGap` does not exist yet.

- [ ] **Step 3: Add the domain models and repository contract**

```kotlin
package com.gasstation.domain.station.model

import java.time.Instant

data class FuelBenchmark(
    val fuelType: FuelType,
    val currentNationalAverageWon: Int,
    val lastWeekAverageWon: Int?,
    val recentDailyAverages: List<FuelBenchmarkDailyAverage>,
    val fetchedAt: Instant,
)

data class FuelBenchmarkDailyAverage(
    val date: String,
    val averagePriceWon: Int,
)
```

```kotlin
package com.gasstation.domain.station.model

sealed interface AverageGap {
    data class BelowBy(val amountWon: Int) : AverageGap
    data class AboveBy(val amountWon: Int) : AverageGap
    data object AtParity : AverageGap
    data object Unavailable : AverageGap

    companion object {
        fun from(
            stationPriceWon: Int,
            benchmarkPriceWon: Int?,
        ): AverageGap {
            if (benchmarkPriceWon == null) return Unavailable
            val delta = stationPriceWon - benchmarkPriceWon
            return when {
                delta < 0 -> BelowBy(-delta)
                delta > 0 -> AboveBy(delta)
                else -> AtParity
            }
        }
    }
}
```

```kotlin
package com.gasstation.domain.station

import com.gasstation.domain.station.model.FuelBenchmark
import com.gasstation.domain.station.model.FuelType
import kotlinx.coroutines.flow.Flow

interface FuelBenchmarkRepository {
    fun observeFuelBenchmark(fuelType: FuelType): Flow<FuelBenchmark?>

    suspend fun refreshFuelBenchmark(fuelType: FuelType)
}
```

- [ ] **Step 4: Add thin use cases**

```kotlin
package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.FuelBenchmarkRepository
import com.gasstation.domain.station.model.FuelType
import javax.inject.Inject

class ObserveFuelBenchmarkUseCase @Inject constructor(
    private val repository: FuelBenchmarkRepository,
) {
    operator fun invoke(fuelType: FuelType) = repository.observeFuelBenchmark(fuelType)
}
```

```kotlin
package com.gasstation.domain.station.usecase

import com.gasstation.domain.station.FuelBenchmarkRepository
import com.gasstation.domain.station.model.FuelType
import javax.inject.Inject

class RefreshFuelBenchmarkUseCase @Inject constructor(
    private val repository: FuelBenchmarkRepository,
) {
    suspend operator fun invoke(fuelType: FuelType) {
        repository.refreshFuelBenchmark(fuelType)
    }
}
```

- [ ] **Step 5: Run the domain test to verify it passes**

Run: `./gradlew :domain:station:test --tests "com.gasstation.domain.station.FuelBenchmarkGapTest"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  domain/station/src/main/kotlin/com/gasstation/domain/station/FuelBenchmarkRepository.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/model/FuelBenchmark.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/model/AverageGap.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveFuelBenchmarkUseCase.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/RefreshFuelBenchmarkUseCase.kt \
  domain/station/src/test/kotlin/com/gasstation/domain/station/FuelBenchmarkGapTest.kt
git commit -m "feat: add fuel benchmark domain contracts"
```

## Task 2: Add the Opinet Benchmark Fetcher

**Files:**
- Modify: `core/network/src/main/kotlin/com/gasstation/core/network/service/OpinetService.kt`
- Modify: `app/src/main/java/com/gasstation/di/AppConfigModule.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetAveragePriceDtos.kt`
- Create: `core/network/src/main/kotlin/com/gasstation/core/network/benchmark/NetworkFuelBenchmarkFetcher.kt`
- Test: `core/network/src/test/kotlin/com/gasstation/core/network/benchmark/NetworkFuelBenchmarkFetcherTest.kt`

- [ ] **Step 1: Write the failing network test**

```kotlin
package com.gasstation.core.network.benchmark

import com.gasstation.core.network.di.NetworkModule
import com.gasstation.domain.station.model.FuelType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkFuelBenchmarkFetcherTest {
    @Test
    fun `fetchBenchmark maps current average recent points and last week average for gasoline`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().addHeader("Content-Type", "application/json").setBody(
                """{"RESULT":{"OIL":[{"TRADE_DT":"20260418","PRODCD":"B027","PRICE":"1659","DIFF":"-4"}]}}""",
            ),
        )
        server.enqueue(
            MockResponse().addHeader("Content-Type", "application/json").setBody(
                """{"RESULT":{"OIL":[{"DATE":"20260417","PRODCD":"B027","PRICE":"1661"},{"DATE":"20260416","PRODCD":"B027","PRICE":"1664"}]}}""",
            ),
        )
        server.enqueue(
            MockResponse().addHeader("Content-Type", "application/json").setBody(
                """{"RESULT":{"OIL":[{"WEEK":"202616","STA_DT":"20260411","END_DT":"20260417","AREA_CD":"00","PRODCD":"B027","PRICE":"1668"}]}}""",
            ),
        )
        server.start()

        try {
            val fetcher = NetworkFuelBenchmarkFetcher(
                opinetService = NetworkModule.provideOpinetService(server.url("/").toString()),
                opinetApiKey = "opinet-key",
            )

            val result = fetcher.fetchBenchmark(FuelType.GASOLINE)

            assertEquals(1659, result.currentNationalAverageWon)
            assertEquals(1668, result.lastWeekAverageWon)
            assertEquals(listOf("20260417", "20260416"), result.recentDailyAverages.map { it.date })

            assertEquals("/api/avgAllPrice.do", server.takeRequest().requestUrl?.encodedPath)
            assertEquals("B027", server.takeRequest().requestUrl?.queryParameter("prodcd"))
            assertEquals("/api/avgLastWeek.do", server.takeRequest().requestUrl?.encodedPath)
        } finally {
            server.shutdown()
        }
    }
}
```

- [ ] **Step 2: Run the network test to verify it fails**

Run: `./gradlew :core:network:test --tests "com.gasstation.core.network.benchmark.NetworkFuelBenchmarkFetcherTest"`

Expected: FAIL because the fetcher, DTOs, and service methods do not exist yet.

- [ ] **Step 3: Extend `OpinetService` and add DTOs**

```kotlin
interface OpinetService {
    @GET("/api/aroundAll.do")
    suspend fun findStations(
        @Query("code") code: String,
        @Query("x") x: Double,
        @Query("y") y: Double,
        @Query("radius") radius: Int,
        @Query("sort") sort: String,
        @Query("prodcd") fuelType: String,
        @Query("out") out: String = "json",
    ): OpinetResponseDto

    @GET("/api/avgAllPrice.do")
    suspend fun getNationalAveragePrices(
        @Query("code") code: String,
        @Query("prodcd") fuelType: String,
        @Query("out") out: String = "json",
    ): OpinetAveragePriceResponseDto

    @GET("/api/avgRecentPrice.do")
    suspend fun getRecentAveragePrices(
        @Query("code") code: String,
        @Query("prodcd") fuelType: String,
        @Query("out") out: String = "json",
    ): OpinetRecentAveragePriceResponseDto

    @GET("/api/avgLastWeek.do")
    suspend fun getLastWeekAveragePrices(
        @Query("code") code: String,
        @Query("prodcd") fuelType: String,
        @Query("out") out: String = "json",
    ): OpinetLastWeekAveragePriceResponseDto
}
```

```kotlin
data class OpinetAveragePriceResponseDto(
    @SerializedName("RESULT") val result: ResultDto? = null,
) {
    data class ResultDto(
        @SerializedName("OIL") val prices: List<OpinetAveragePriceDto>? = null,
    )
}

data class OpinetAveragePriceDto(
    @SerializedName("TRADE_DT") val tradeDate: String? = null,
    @SerializedName("PRODCD") val productCode: String? = null,
    @SerializedName("PRICE") val price: String? = null,
)

data class OpinetRecentAveragePriceResponseDto(
    @SerializedName("RESULT") val result: ResultDto? = null,
) {
    data class ResultDto(
        @SerializedName("OIL") val prices: List<OpinetRecentAveragePriceDto>? = null,
    )
}

data class OpinetRecentAveragePriceDto(
    @SerializedName("DATE") val date: String? = null,
    @SerializedName("PRODCD") val productCode: String? = null,
    @SerializedName("PRICE") val price: String? = null,
)

data class OpinetLastWeekAveragePriceResponseDto(
    @SerializedName("RESULT") val result: ResultDto? = null,
) {
    data class ResultDto(
        @SerializedName("OIL") val prices: List<OpinetLastWeekAveragePriceDto>? = null,
    )
}

data class OpinetLastWeekAveragePriceDto(
    @SerializedName("WEEK") val week: String? = null,
    @SerializedName("STA_DT") val startDate: String? = null,
    @SerializedName("END_DT") val endDate: String? = null,
    @SerializedName("AREA_CD") val areaCode: String? = null,
    @SerializedName("PRODCD") val productCode: String? = null,
    @SerializedName("PRICE") val price: String? = null,
)
```

- [ ] **Step 4: Implement the benchmark fetcher**

```kotlin
package com.gasstation.core.network.benchmark

import com.gasstation.core.network.service.OpinetService
import com.gasstation.core.network.station.toFuelProductCode
import com.gasstation.domain.station.model.FuelType

data class NetworkFuelBenchmark(
    val currentNationalAverageWon: Int,
    val lastWeekAverageWon: Int?,
    val recentDailyAverages: List<NetworkFuelBenchmarkPoint>,
)

data class NetworkFuelBenchmarkPoint(
    val date: String,
    val averagePriceWon: Int,
)

class NetworkFuelBenchmarkFetcher(
    private val opinetService: OpinetService,
    private val opinetApiKey: String,
) {
    suspend fun fetchBenchmark(fuelType: FuelType): NetworkFuelBenchmark {
        val productCode = fuelType.toFuelProductCode()
        val current = opinetService.getNationalAveragePrices(
            code = opinetApiKey,
            fuelType = productCode,
        ).result?.prices.orEmpty().firstOrNull()?.price?.toIntOrNull()
            ?: error("Missing current average for $fuelType")

        val recent = opinetService.getRecentAveragePrices(
            code = opinetApiKey,
            fuelType = productCode,
        ).result?.prices.orEmpty().mapNotNull { row ->
            val date = row.date ?: return@mapNotNull null
            val price = row.price?.toIntOrNull() ?: return@mapNotNull null
            NetworkFuelBenchmarkPoint(date = date, averagePriceWon = price)
        }

        val lastWeek = opinetService.getLastWeekAveragePrices(
            code = opinetApiKey,
            fuelType = productCode,
        ).result?.prices.orEmpty().firstOrNull()?.price?.toIntOrNull()

        return NetworkFuelBenchmark(
            currentNationalAverageWon = current,
            lastWeekAverageWon = lastWeek,
            recentDailyAverages = recent,
        )
    }
}
```

Add the Hilt provider in `AppConfigModule.kt`:

```kotlin
@Provides
@Singleton
fun provideNetworkFuelBenchmarkFetcher(
    opinetService: OpinetService,
    @Named("opinetApiKey") opinetApiKey: String,
): NetworkFuelBenchmarkFetcher = NetworkFuelBenchmarkFetcher(
    opinetService = opinetService,
    opinetApiKey = opinetApiKey,
)
```

- [ ] **Step 5: Run the network test to verify it passes**

Run: `./gradlew :core:network:test --tests "com.gasstation.core.network.benchmark.NetworkFuelBenchmarkFetcherTest"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  core/network/src/main/kotlin/com/gasstation/core/network/service/OpinetService.kt \
  app/src/main/java/com/gasstation/di/AppConfigModule.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/model/OpinetAveragePriceDtos.kt \
  core/network/src/main/kotlin/com/gasstation/core/network/benchmark/NetworkFuelBenchmarkFetcher.kt \
  core/network/src/test/kotlin/com/gasstation/core/network/benchmark/NetworkFuelBenchmarkFetcherTest.kt
git commit -m "feat: add opinet benchmark fetcher"
```

## Task 3: Add the Room Benchmark Cache

**Files:**
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/FuelBenchmarkEntity.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/FuelBenchmarkDailyEntity.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/FuelBenchmarkDao.kt`
- Modify: `core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt`
- Modify: `core/database/src/main/kotlin/com/gasstation/core/database/DatabaseModule.kt`
- Test: `core/database/src/test/kotlin/com/gasstation/core/database/station/FuelBenchmarkDaoTest.kt`
- Test: `core/database/src/test/kotlin/com/gasstation/core/database/GasStationDatabaseMigrationTest.kt`

- [ ] **Step 1: Write the DAO test first**

```kotlin
package com.gasstation.core.database.station

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gasstation.core.database.GasStationDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FuelBenchmarkDaoTest {
    private lateinit var database: GasStationDatabase
    private lateinit var dao: FuelBenchmarkDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GasStationDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.fuelBenchmarkDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `replaceBenchmark overwrites prior benchmark rows for one fuel type`() = runBlocking {
        dao.replaceBenchmark(
            entity = FuelBenchmarkEntity(
                fuelType = "GASOLINE",
                currentNationalAverageWon = 1659,
                lastWeekAverageWon = 1668,
                fetchedAtEpochMillis = 1776481200000,
            ),
            daily = listOf(
                FuelBenchmarkDailyEntity("GASOLINE", "20260417", 1661),
                FuelBenchmarkDailyEntity("GASOLINE", "20260416", 1664),
            ),
        )

        dao.replaceBenchmark(
            entity = FuelBenchmarkEntity(
                fuelType = "GASOLINE",
                currentNationalAverageWon = 1655,
                lastWeekAverageWon = 1666,
                fetchedAtEpochMillis = 1776488400000,
            ),
            daily = listOf(
                FuelBenchmarkDailyEntity("GASOLINE", "20260418", 1655),
            ),
        )

        assertEquals(1655, dao.observeBenchmark("GASOLINE").first()?.currentNationalAverageWon)
        assertEquals(listOf("20260418"), dao.observeDaily("GASOLINE").first().map { it.date })
    }
}
```

- [ ] **Step 2: Run the database tests to verify they fail**

Run: `./gradlew :core:database:testDebugUnitTest --tests "com.gasstation.core.database.station.FuelBenchmarkDaoTest"`

Expected: FAIL because DAO/entities do not exist yet.

- [ ] **Step 3: Add the entities and DAO**

```kotlin
@Entity(tableName = "fuel_benchmark_cache", primaryKeys = ["fuelType"])
data class FuelBenchmarkEntity(
    val fuelType: String,
    val currentNationalAverageWon: Int,
    val lastWeekAverageWon: Int?,
    val fetchedAtEpochMillis: Long,
)

@Entity(
    tableName = "fuel_benchmark_daily_cache",
    primaryKeys = ["fuelType", "date"],
)
data class FuelBenchmarkDailyEntity(
    val fuelType: String,
    val date: String,
    val averagePriceWon: Int,
)
```

```kotlin
@Dao
abstract class FuelBenchmarkDao {
    @Query("SELECT * FROM fuel_benchmark_cache WHERE fuelType = :fuelType LIMIT 1")
    abstract fun observeBenchmark(fuelType: String): Flow<FuelBenchmarkEntity?>

    @Query("SELECT * FROM fuel_benchmark_daily_cache WHERE fuelType = :fuelType ORDER BY date DESC")
    abstract fun observeDaily(fuelType: String): Flow<List<FuelBenchmarkDailyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertBenchmark(entity: FuelBenchmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertDaily(rows: List<FuelBenchmarkDailyEntity>)

    @Query("DELETE FROM fuel_benchmark_daily_cache WHERE fuelType = :fuelType")
    protected abstract suspend fun deleteDaily(fuelType: String)

    @Transaction
    open suspend fun replaceBenchmark(
        entity: FuelBenchmarkEntity,
        daily: List<FuelBenchmarkDailyEntity>,
    ) {
        upsertBenchmark(entity)
        deleteDaily(entity.fuelType)
        if (daily.isNotEmpty()) {
            upsertDaily(daily)
        }
    }
}
```

- [ ] **Step 4: Register the DAO and migration**

```kotlin
@Database(
    entities = [
        StationCacheEntity::class,
        StationPriceHistoryEntity::class,
        WatchedStationEntity::class,
        FuelBenchmarkEntity::class,
        FuelBenchmarkDailyEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class GasStationDatabase : RoomDatabase() {
    abstract fun stationCacheDao(): StationCacheDao
    abstract fun stationPriceHistoryDao(): StationPriceHistoryDao
    abstract fun watchedStationDao(): WatchedStationDao
    abstract fun fuelBenchmarkDao(): FuelBenchmarkDao

    companion object {
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fuel_benchmark_cache` (
                        `fuelType` TEXT NOT NULL,
                        `currentNationalAverageWon` INTEGER NOT NULL,
                        `lastWeekAverageWon` INTEGER,
                        `fetchedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`fuelType`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fuel_benchmark_daily_cache` (
                        `fuelType` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `averagePriceWon` INTEGER NOT NULL,
                        PRIMARY KEY(`fuelType`, `date`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
```

```kotlin
@Provides
fun provideFuelBenchmarkDao(
    database: GasStationDatabase,
): FuelBenchmarkDao = database.fuelBenchmarkDao()
```

Also update `DatabaseModule` to include `GasStationDatabase.MIGRATION_3_4`.

- [ ] **Step 5: Extend the migration test**

Add this assertion block inside `GasStationDatabaseMigrationTest`:

```kotlin
assertTrue(tableExists(migratedDatabase.openHelper.writableDatabase, "fuel_benchmark_cache"))
assertTrue(tableExists(migratedDatabase.openHelper.writableDatabase, "fuel_benchmark_daily_cache"))
```

And update the builder:

```kotlin
.addMigrations(
    GasStationDatabase.MIGRATION_1_2,
    GasStationDatabase.MIGRATION_2_3,
    GasStationDatabase.MIGRATION_3_4,
)
```

- [ ] **Step 6: Run the database tests to verify they pass**

Run:

```bash
./gradlew :core:database:testDebugUnitTest \
  --tests "com.gasstation.core.database.station.FuelBenchmarkDaoTest" \
  --tests "com.gasstation.core.database.GasStationDatabaseMigrationTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  core/database/src/main/kotlin/com/gasstation/core/database/station/FuelBenchmarkEntity.kt \
  core/database/src/main/kotlin/com/gasstation/core/database/station/FuelBenchmarkDailyEntity.kt \
  core/database/src/main/kotlin/com/gasstation/core/database/station/FuelBenchmarkDao.kt \
  core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt \
  core/database/src/main/kotlin/com/gasstation/core/database/DatabaseModule.kt \
  core/database/src/test/kotlin/com/gasstation/core/database/station/FuelBenchmarkDaoTest.kt \
  core/database/src/test/kotlin/com/gasstation/core/database/GasStationDatabaseMigrationTest.kt
git commit -m "feat: cache fuel benchmarks locally"
```

## Task 4: Add the Benchmark Repository and Remote Data Sources

**Files:**
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/FuelBenchmarkRemoteDataSource.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultFuelBenchmarkRemoteDataSource.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/SeedFuelBenchmarkRemoteDataSource.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultFuelBenchmarkRepository.kt`
- Create: `data/station/src/main/kotlin/com/gasstation/data/station/FuelBenchmarkCachePolicy.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt`
- Test: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultFuelBenchmarkRepositoryTest.kt`

- [ ] **Step 1: Write the failing repository test**

```kotlin
package com.gasstation.data.station

import com.gasstation.core.database.station.FuelBenchmarkDailyEntity
import com.gasstation.core.database.station.FuelBenchmarkEntity
import com.gasstation.domain.station.model.FuelType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultFuelBenchmarkRepositoryTest {
    @Test
    fun `refreshFuelBenchmark persists remote benchmark rows and observeFuelBenchmark maps them back`() = runBlocking {
        val now = Instant.parse("2026-04-18T03:00:00Z")
        val dao = RecordingFuelBenchmarkDao()
        val repository = DefaultFuelBenchmarkRepository(
            dao = dao,
            remoteDataSource = FakeFuelBenchmarkRemoteDataSource(
                FuelBenchmarkRemote(
                    fuelType = FuelType.GASOLINE,
                    currentNationalAverageWon = 1659,
                    lastWeekAverageWon = 1668,
                    recentDailyAverages = listOf(
                        FuelBenchmarkRemotePoint("20260417", 1661),
                        FuelBenchmarkRemotePoint("20260416", 1664),
                    ),
                ),
            ),
            seedRemoteDataSource = Optional.empty(),
            cachePolicy = FuelBenchmarkCachePolicy(refreshAfterSeconds = 60 * 60 * 2),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        repository.refreshFuelBenchmark(FuelType.GASOLINE)

        val benchmark = repository.observeFuelBenchmark(FuelType.GASOLINE).first()

        assertEquals(1659, benchmark?.currentNationalAverageWon)
        assertEquals(1668, benchmark?.lastWeekAverageWon)
        assertEquals(listOf("20260417", "20260416"), benchmark?.recentDailyAverages?.map { it.date })
    }
}
```

- [ ] **Step 2: Run the repository test to verify it fails**

Run: `./gradlew :data:station:testDebugUnitTest --tests "com.gasstation.data.station.DefaultFuelBenchmarkRepositoryTest"`

Expected: FAIL because the repository/data source classes do not exist yet.

- [ ] **Step 3: Add the remote data source and cache policy**

```kotlin
package com.gasstation.data.station

import com.gasstation.core.network.benchmark.NetworkFuelBenchmarkFetcher
import com.gasstation.domain.station.model.FuelType
import javax.inject.Inject

data class FuelBenchmarkRemote(
    val fuelType: FuelType,
    val currentNationalAverageWon: Int,
    val lastWeekAverageWon: Int?,
    val recentDailyAverages: List<FuelBenchmarkRemotePoint>,
)

data class FuelBenchmarkRemotePoint(
    val date: String,
    val averagePriceWon: Int,
)

interface FuelBenchmarkRemoteDataSource {
    suspend fun fetchBenchmark(fuelType: FuelType): FuelBenchmarkRemote
}

class DefaultFuelBenchmarkRemoteDataSource @Inject constructor(
    private val fetcher: NetworkFuelBenchmarkFetcher,
) : FuelBenchmarkRemoteDataSource {
    override suspend fun fetchBenchmark(fuelType: FuelType): FuelBenchmarkRemote {
        val remote = fetcher.fetchBenchmark(fuelType)
        return FuelBenchmarkRemote(
            fuelType = fuelType,
            currentNationalAverageWon = remote.currentNationalAverageWon,
            lastWeekAverageWon = remote.lastWeekAverageWon,
            recentDailyAverages = remote.recentDailyAverages.map { point ->
                FuelBenchmarkRemotePoint(
                    date = point.date,
                    averagePriceWon = point.averagePriceWon,
                )
            },
        )
    }
}
```

```kotlin
package com.gasstation.data.station

import com.gasstation.domain.station.model.FuelType
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

interface SeedFuelBenchmarkRemoteDataSource {
    suspend fun fetchBenchmark(fuelType: FuelType): FuelBenchmarkRemote
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SeedFuelBenchmarkRemoteDataSourceModule {
    @BindsOptionalOf
    abstract fun bindSeedFuelBenchmarkRemoteDataSource(): SeedFuelBenchmarkRemoteDataSource
}
```

```kotlin
class FuelBenchmarkCachePolicy(
    private val refreshAfterSeconds: Long = 60L * 60L * 2L,
) {
    fun shouldRefresh(
        fetchedAtEpochMillis: Long?,
        nowEpochMillis: Long,
    ): Boolean {
        if (fetchedAtEpochMillis == null) return true
        val ageMillis = nowEpochMillis - fetchedAtEpochMillis
        return ageMillis >= refreshAfterSeconds * 1000L
    }
}
```

- [ ] **Step 4: Implement the repository**

```kotlin
class DefaultFuelBenchmarkRepository @Inject constructor(
    private val dao: FuelBenchmarkDao,
    private val remoteDataSource: FuelBenchmarkRemoteDataSource,
    private val seedRemoteDataSource: Optional<SeedFuelBenchmarkRemoteDataSource>,
    private val cachePolicy: FuelBenchmarkCachePolicy,
    private val clock: Clock,
) : FuelBenchmarkRepository {
    override fun observeFuelBenchmark(fuelType: FuelType): Flow<FuelBenchmark?> =
        combine(
            dao.observeBenchmark(fuelType.name),
            dao.observeDaily(fuelType.name),
        ) { entity, daily ->
            entity?.let {
                FuelBenchmark(
                    fuelType = fuelType,
                    currentNationalAverageWon = it.currentNationalAverageWon,
                    lastWeekAverageWon = it.lastWeekAverageWon,
                    recentDailyAverages = daily.map { row ->
                        FuelBenchmarkDailyAverage(
                            date = row.date,
                            averagePriceWon = row.averagePriceWon,
                        )
                    },
                    fetchedAt = Instant.ofEpochMilli(it.fetchedAtEpochMillis),
                )
            }
        }

    override suspend fun refreshFuelBenchmark(fuelType: FuelType) {
        val cached = dao.observeBenchmark(fuelType.name).first()
        if (!cachePolicy.shouldRefresh(cached?.fetchedAtEpochMillis, clock.instant().toEpochMilli())) return

        val remote = if (seedRemoteDataSource.isPresent) {
            seedRemoteDataSource.get().fetchBenchmark(fuelType)
        } else {
            remoteDataSource.fetchBenchmark(fuelType)
        }

        dao.replaceBenchmark(
            entity = FuelBenchmarkEntity(
                fuelType = fuelType.name,
                currentNationalAverageWon = remote.currentNationalAverageWon,
                lastWeekAverageWon = remote.lastWeekAverageWon,
                fetchedAtEpochMillis = clock.instant().toEpochMilli(),
            ),
            daily = remote.recentDailyAverages.map { point ->
                FuelBenchmarkDailyEntity(
                    fuelType = fuelType.name,
                    date = point.date,
                    averagePriceWon = point.averagePriceWon,
                )
            },
        )
    }
}
```

- [ ] **Step 5: Wire Hilt bindings**

In `StationDataModule.kt`, add:

```kotlin
@Binds
@Singleton
abstract fun bindFuelBenchmarkRepository(
    repository: DefaultFuelBenchmarkRepository,
): FuelBenchmarkRepository

@Binds
@Singleton
abstract fun bindFuelBenchmarkRemoteDataSource(
    dataSource: DefaultFuelBenchmarkRemoteDataSource,
): FuelBenchmarkRemoteDataSource
```

And in the companion object:

```kotlin
@Provides
@Singleton
fun provideFuelBenchmarkCachePolicy(): FuelBenchmarkCachePolicy =
    FuelBenchmarkCachePolicy()
```

- [ ] **Step 6: Run the repository test to verify it passes**

Run: `./gradlew :data:station:testDebugUnitTest --tests "com.gasstation.data.station.DefaultFuelBenchmarkRepositoryTest"`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  data/station/src/main/kotlin/com/gasstation/data/station/FuelBenchmarkRemoteDataSource.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/DefaultFuelBenchmarkRemoteDataSource.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/SeedFuelBenchmarkRemoteDataSource.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/DefaultFuelBenchmarkRepository.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/FuelBenchmarkCachePolicy.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt \
  data/station/src/test/kotlin/com/gasstation/data/station/DefaultFuelBenchmarkRepositoryTest.kt
git commit -m "feat: add fuel benchmark repository"
```

## Task 5: Extend Demo Seed and Startup Seeding

**Files:**
- Modify: `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetModels.kt`
- Modify: `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetLoader.kt`
- Modify: `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
- Create: `app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedFuelBenchmarkRemoteDataSource.kt`
- Create: `app/src/demo/kotlin/com/gasstation/di/DemoFuelBenchmarkRemoteDataSourceModule.kt`
- Modify: `app/src/testDemo/java/com/gasstation/demo/seed/DemoSeedAssetLoaderTest.kt`
- Modify: `app/src/testDemo/java/com/gasstation/startup/DemoSeedStartupHookTest.kt`
- Modify: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedJsonWriter.kt`
- Modify: `tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt`
- Modify: `tools/demo-seed/src/test/kotlin/com/gasstation/tools/demoseed/DemoSeedGeneratorTest.kt`

- [ ] **Step 1: Write the failing demo asset test**

Append this test to `DemoSeedAssetLoaderTest.kt`:

```kotlin
@Test
fun `parse decodes benchmark payloads from demo json`() {
    val document = DemoSeedAssetLoader().parse(
        """
        {
          "seedVersion": 2,
          "generatedAtEpochMillis": 1770000000000,
          "origin": { "label": "Gangnam Station Exit 2", "latitude": 37.497927, "longitude": 127.027583 },
          "queries": [],
          "history": [],
          "benchmarks": [
            {
              "fuelType": "GASOLINE",
              "currentNationalAverageWon": 1659,
              "lastWeekAverageWon": 1668,
              "recentDailyAverages": [
                { "date": "20260417", "averagePriceWon": 1661 }
              ]
            }
          ]
        }
        """.trimIndent(),
    )

    assertEquals(1659, document.benchmarks.single().currentNationalAverageWon)
    assertEquals("20260417", document.benchmarks.single().recentDailyAverages.single().date)
}
```

- [ ] **Step 2: Run the demo tests to verify they fail**

Run:

```bash
./gradlew :app:testDemoDebugUnitTest --tests "com.gasstation.demo.seed.DemoSeedAssetLoaderTest"
./gradlew :tools:demo-seed:test --tests "com.gasstation.tools.demoseed.DemoSeedGeneratorTest"
```

Expected: FAIL because benchmark seed models do not exist.

- [ ] **Step 3: Extend the seed schema**

```kotlin
data class DemoSeedDocument(
    val seedVersion: Int,
    val generatedAtEpochMillis: Long,
    val origin: DemoSeedOriginDocument,
    val queries: List<DemoSeedQueryDocument>,
    val history: List<DemoSeedHistoryDocument>,
    val benchmarks: List<DemoSeedFuelBenchmarkDocument>,
)

data class DemoSeedFuelBenchmarkDocument(
    val fuelType: String,
    val currentNationalAverageWon: Int,
    val lastWeekAverageWon: Int?,
    val recentDailyAverages: List<DemoSeedFuelBenchmarkDailyDocument>,
)

data class DemoSeedFuelBenchmarkDailyDocument(
    val date: String,
    val averagePriceWon: Int,
)
```

And parse it in `DemoSeedAssetLoader`:

```kotlin
benchmarks = root.optJSONArray("benchmarks")?.mapObjects { it.toBenchmark() }.orEmpty()
```

```kotlin
private fun JSONObject.toBenchmark(): DemoSeedFuelBenchmarkDocument =
    DemoSeedFuelBenchmarkDocument(
        fuelType = getString("fuelType"),
        currentNationalAverageWon = getInt("currentNationalAverageWon"),
        lastWeekAverageWon = if (isNull("lastWeekAverageWon")) null else getInt("lastWeekAverageWon"),
        recentDailyAverages = getJSONArray("recentDailyAverages").mapObjects { row ->
            DemoSeedFuelBenchmarkDailyDocument(
                date = row.getString("date"),
                averagePriceWon = row.getInt("averagePriceWon"),
            )
        },
    )
```

- [ ] **Step 4: Seed the benchmark tables and bind the demo remote data source**

In `DemoSeedStartupHook.seedDatabase()` add:

```kotlin
val benchmarkInsert = writableDatabase.compileStatement(
    """
    INSERT OR REPLACE INTO fuel_benchmark_cache (
        fuelType,
        currentNationalAverageWon,
        lastWeekAverageWon,
        fetchedAtEpochMillis
    ) VALUES (?, ?, ?, ?)
    """.trimIndent(),
)
val benchmarkDailyInsert = writableDatabase.compileStatement(
    """
    INSERT OR REPLACE INTO fuel_benchmark_daily_cache (
        fuelType,
        date,
        averagePriceWon
    ) VALUES (?, ?, ?)
    """.trimIndent(),
)
```

And inside the transaction:

```kotlin
writableDatabase.execSQL("DELETE FROM fuel_benchmark_cache")
writableDatabase.execSQL("DELETE FROM fuel_benchmark_daily_cache")

document.benchmarks.forEach { benchmark ->
    benchmarkInsert.clearBindings()
    benchmarkInsert.bindString(1, benchmark.fuelType)
    benchmarkInsert.bindLong(2, benchmark.currentNationalAverageWon.toLong())
    if (benchmark.lastWeekAverageWon == null) benchmarkInsert.bindNull(3) else benchmarkInsert.bindLong(3, benchmark.lastWeekAverageWon.toLong())
    benchmarkInsert.bindLong(4, document.generatedAtEpochMillis)
    benchmarkInsert.executeInsert()

    benchmark.recentDailyAverages.forEach { row ->
        benchmarkDailyInsert.clearBindings()
        benchmarkDailyInsert.bindString(1, benchmark.fuelType)
        benchmarkDailyInsert.bindString(2, row.date)
        benchmarkDailyInsert.bindLong(3, row.averagePriceWon.toLong())
        benchmarkDailyInsert.executeInsert()
    }
}
```

Create the demo seed remote data source:

```kotlin
class DemoSeedFuelBenchmarkRemoteDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetLoader: DemoSeedAssetLoader,
) : SeedFuelBenchmarkRemoteDataSource {
    override suspend fun fetchBenchmark(fuelType: FuelType): FuelBenchmarkRemote {
        val benchmark = assetLoader.load(context).benchmarks.first { it.fuelType == fuelType.name }
        return FuelBenchmarkRemote(
            fuelType = fuelType,
            currentNationalAverageWon = benchmark.currentNationalAverageWon,
            lastWeekAverageWon = benchmark.lastWeekAverageWon,
            recentDailyAverages = benchmark.recentDailyAverages.map { row ->
                FuelBenchmarkRemotePoint(date = row.date, averagePriceWon = row.averagePriceWon)
            },
        )
    }
}
```

And bind it:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DemoFuelBenchmarkRemoteDataSourceModule {
    @Binds
    @Singleton
    abstract fun bindSeedFuelBenchmarkRemoteDataSource(
        dataSource: DemoSeedFuelBenchmarkRemoteDataSource,
    ): SeedFuelBenchmarkRemoteDataSource
}
```

- [ ] **Step 5: Extend the generator**

In `DemoSeedJsonWriter.kt`, add:

```kotlin
data class DemoSeedFuelBenchmark(
    val fuelType: String,
    val currentNationalAverageWon: Int,
    val lastWeekAverageWon: Int?,
    val recentDailyAverages: List<DemoSeedFuelBenchmarkPoint>,
)

data class DemoSeedFuelBenchmarkPoint(
    val date: String,
    val averagePriceWon: Int,
)
```

And in `DemoSeedGenerator.createDocument()`:

```kotlin
val benchmarks = FuelType.entries.map { fuelType ->
    val benchmark = benchmarkFetcher.fetchBenchmark(fuelType)
    DemoSeedFuelBenchmark(
        fuelType = fuelType.name,
        currentNationalAverageWon = benchmark.currentNationalAverageWon,
        lastWeekAverageWon = benchmark.lastWeekAverageWon,
        recentDailyAverages = benchmark.recentDailyAverages.map { point ->
            DemoSeedFuelBenchmarkPoint(
                date = point.date,
                averagePriceWon = point.averagePriceWon,
            )
        },
    )
}
```

Also update the root document:

```kotlin
return DemoSeedDocument(
    seedVersion = 2,
    generatedAtEpochMillis = generatedAtEpochMillis,
    origin = DemoSeedOriginJson(
        label = originLabel,
        latitude = origin.latitude,
        longitude = origin.longitude,
    ),
    queries = snapshots,
    history = history,
    benchmarks = benchmarks,
)
```

- [ ] **Step 6: Run the demo tests to verify they pass**

Run:

```bash
./gradlew :tools:demo-seed:test --tests "com.gasstation.tools.demoseed.DemoSeedGeneratorTest"
./gradlew :app:testDemoDebugUnitTest \
  --tests "com.gasstation.demo.seed.DemoSeedAssetLoaderTest" \
  --tests "com.gasstation.startup.DemoSeedStartupHookTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetModels.kt \
  app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedAssetLoader.kt \
  app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt \
  app/src/demo/kotlin/com/gasstation/demo/seed/DemoSeedFuelBenchmarkRemoteDataSource.kt \
  app/src/demo/kotlin/com/gasstation/di/DemoFuelBenchmarkRemoteDataSourceModule.kt \
  app/src/testDemo/java/com/gasstation/demo/seed/DemoSeedAssetLoaderTest.kt \
  app/src/testDemo/java/com/gasstation/startup/DemoSeedStartupHookTest.kt \
  tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedJsonWriter.kt \
  tools/demo-seed/src/main/kotlin/com/gasstation/tools/demoseed/DemoSeedGenerator.kt \
  tools/demo-seed/src/test/kotlin/com/gasstation/tools/demoseed/DemoSeedGeneratorTest.kt
git commit -m "feat: seed benchmark data in demo mode"
```

## Task 6: Wire the Benchmark Into the Station List UI

**Files:**
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Test: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListItemUiModelTest.kt`
- Test: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`
- Test: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

- [ ] **Step 1: Write the failing UI-model and view-model tests**

Add this to `StationListItemUiModelTest.kt`:

```kotlin
@Test
fun `station list item maps lower-than-average price to benchmark label`() {
    val item = StationListItemUiModel(
        entry = stationEntry(),
        benchmarkAverageWon = 1713,
    )

    assertEquals("전국 평균보다 24원 저렴", item.averageGapLabel)
    assertEquals(AverageGapTone.Positive, item.averageGapTone)
}
```

Add this to `StationListViewModelTest.kt`:

```kotlin
assertEquals("오늘 전국 평균 1,659원", viewModel.uiState.value.benchmarkSummaryLabel)
assertEquals("최근 7일 평균 하락세", viewModel.uiState.value.benchmarkTrendLabel)
assertEquals("전국 평균보다 29원 저렴", viewModel.uiState.value.stations.single().averageGapLabel)
```

Add this to `StationListScreenTest.kt`:

```kotlin
composeRule.onNodeWithText("오늘 전국 평균 1,659원").assertExists()
composeRule.onNodeWithText("전국 평균보다 24원 저렴").assertExists()
```

- [ ] **Step 2: Run the station-list tests to verify they fail**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest \
  --tests "com.gasstation.feature.stationlist.StationListItemUiModelTest" \
  --tests "com.gasstation.feature.stationlist.StationListViewModelTest" \
  --tests "com.gasstation.feature.stationlist.StationListScreenTest"
```

Expected: FAIL because benchmark fields do not exist yet.

- [ ] **Step 3: Extend the UI state and item model**

In `StationListUiState.kt`:

```kotlin
data class StationListUiState(
    val currentCoordinates: Coordinates? = null,
    val permissionState: LocationPermissionState = LocationPermissionState.Denied,
    val isGpsEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val stations: List<StationListItemUiModel> = emptyList(),
    val selectedBrandFilter: BrandFilter = BrandFilter.ALL,
    val selectedRadius: SearchRadius = SearchRadius.KM_3,
    val selectedFuelType: FuelType = FuelType.GASOLINE,
    val selectedSortOrder: SortOrder = SortOrder.DISTANCE,
    val lastUpdatedAt: Instant? = null,
    val benchmarkSummaryLabel: String? = null,
    val benchmarkTrendLabel: String? = null,
)
```

In `StationListItemUiModel.kt`:

```kotlin
data class StationListItemUiModel(
    val id: String,
    val name: String,
    val brandLabel: String,
    val priceLabel: String,
    val distanceLabel: String,
    val priceNumberLabel: String,
    val priceUnitLabel: String,
    val distanceNumberLabel: String,
    val distanceUnitLabel: String,
    val priceDeltaLabel: String,
    val priceDeltaTone: PriceDeltaTone = PriceDeltaTone.Neutral,
    val isWatched: Boolean,
    val latitude: Double,
    val longitude: Double,
    val averageGapLabel: String = "-",
    val averageGapTone: AverageGapTone = AverageGapTone.Neutral,
) {
    constructor(
        entry: StationListEntry,
        benchmarkAverageWon: Int?,
    ) : this(
        id = entry.station.id,
        name = entry.station.name,
        brandLabel = entry.station.brand.toLabel(),
        priceLabel = entry.station.price.value.toPriceLabel(),
        distanceLabel = entry.station.distance.toDistanceLabel(),
        priceNumberLabel = entry.station.price.value.toGroupedDigits(),
        priceUnitLabel = "원",
        distanceNumberLabel = entry.station.distance.toDistanceNumberLabel(),
        distanceUnitLabel = "km",
        priceDeltaLabel = entry.priceDelta.toLabel(),
        priceDeltaTone = entry.priceDelta.toTone(),
        isWatched = entry.isWatched,
        latitude = entry.station.coordinates.latitude,
        longitude = entry.station.coordinates.longitude,
        averageGapLabel = AverageGap.from(
            stationPriceWon = entry.station.price.value,
            benchmarkPriceWon = benchmarkAverageWon,
        ).toLabel(),
        averageGapTone = AverageGap.from(
            stationPriceWon = entry.station.price.value,
            benchmarkPriceWon = benchmarkAverageWon,
        ).toTone(),
    )
}

enum class AverageGapTone {
    Positive,
    Negative,
    Neutral,
}
```

With helpers:

```kotlin
private fun AverageGap.toLabel(): String = when (this) {
    is AverageGap.BelowBy -> "전국 평균보다 ${amountWon}원 저렴"
    is AverageGap.AboveBy -> "전국 평균보다 ${amountWon}원 높음"
    AverageGap.AtParity -> "전국 평균과 동일"
    AverageGap.Unavailable -> "-"
}

private fun AverageGap.toTone(): AverageGapTone = when (this) {
    is AverageGap.BelowBy -> AverageGapTone.Positive
    is AverageGap.AboveBy -> AverageGapTone.Negative
    AverageGap.AtParity,
    AverageGap.Unavailable,
    -> AverageGapTone.Neutral
}
```

- [ ] **Step 4: Combine benchmark flow in the view model**

Add constructor deps:

```kotlin
private val observeFuelBenchmark: ObserveFuelBenchmarkUseCase,
private val refreshFuelBenchmark: RefreshFuelBenchmarkUseCase,
```

Add a benchmark state flow:

```kotlin
private val benchmark = preferences
    .map { it.fuelType }
    .distinctUntilChanged()
    .flatMapLatest { fuelType -> observeFuelBenchmark(fuelType) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
```

Update the main combine:

```kotlin
combine(preferences, sessionState, searchResult, benchmark) { prefs, session, result, benchmark ->
    StationListUiState(
        currentCoordinates = session.currentCoordinates,
        permissionState = session.permissionState,
        isGpsEnabled = session.isGpsEnabled,
        isLoading = session.isLoading,
        isRefreshing = session.isRefreshing,
        isStale = result.freshness is StationFreshness.Stale,
        stations = result.stations.map { entry ->
            StationListItemUiModel(
                entry = entry,
                benchmarkAverageWon = benchmark?.currentNationalAverageWon,
            )
        },
        selectedBrandFilter = prefs.brandFilter,
        selectedRadius = prefs.searchRadius,
        selectedFuelType = prefs.fuelType,
        selectedSortOrder = prefs.sortOrder,
        lastUpdatedAt = result.fetchedAt,
        benchmarkSummaryLabel = benchmark?.currentNationalAverageWon?.let { "오늘 전국 평균 ${DecimalFormat("#,###").format(it)}원" },
        benchmarkTrendLabel = benchmark?.recentDailyAverages?.let(::trendSummary),
    )
}
```

Refresh the benchmark after a successful nearby refresh:

```kotlin
runCatching {
    refreshNearbyStations(buildQuery(preferences.value, coordinates))
    refreshFuelBenchmark(preferences.value.fuelType)
}.onFailure {
    mutableEffects.emit(StationListEffect.ShowSnackbar("주유소 목록을 새로고침하지 못했습니다."))
}
```

Use this helper:

```kotlin
private fun trendSummary(points: List<FuelBenchmarkDailyAverage>): String? {
    if (points.size < 2) return null
    val newest = points.first().averagePriceWon
    val oldest = points.last().averagePriceWon
    return when {
        newest < oldest -> "최근 7일 평균 하락세"
        newest > oldest -> "최근 7일 평균 상승세"
        else -> "최근 7일 평균 보합"
    }
}
```

- [ ] **Step 5: Render the summary card and card-level gap text**

In `StationListScreen.kt`, add a new summary item after `FilterSummary`:

```kotlin
if (uiState.benchmarkSummaryLabel != null) {
    item {
        GasStationCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = GasStationTheme.spacing.space16,
                vertical = GasStationTheme.spacing.space16,
            ),
        ) {
            GasStationSectionHeading(
                title = uiState.benchmarkSummaryLabel,
                subtitle = uiState.benchmarkTrendLabel,
            )
        }
    }
}
```

And inside `StationCard`, after the brand row:

```kotlin
Text(
    text = station.averageGapLabel,
    style = typography.body,
    color = when (station.averageGapTone) {
        AverageGapTone.Positive -> ColorSupportInfo
        AverageGapTone.Negative -> ColorSupportError
        AverageGapTone.Neutral -> ColorGray2
    },
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)
```

- [ ] **Step 6: Run the station-list tests to verify they pass**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest \
  --tests "com.gasstation.feature.stationlist.StationListItemUiModelTest" \
  --tests "com.gasstation.feature.stationlist.StationListViewModelTest" \
  --tests "com.gasstation.feature.stationlist.StationListScreenTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListItemUiModelTest.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt
git commit -m "feat: show national benchmark on station list"
```

## Task 7: Update Docs and Run Full Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/offline-strategy.md`

- [ ] **Step 1: Update README to describe the new station-list benchmark**

Add one bullet under core user flow:

```markdown
2. 가격 변화와 전국 평균 대비 비교 확인
3. 관심 주유소 비교 화면 진입
```

And under project purpose:

```markdown
- Opinet 현재 가격과 전국 평균 데이터를 함께 써서 "지금 이 가격이 싼지"를 설명합니다.
```

- [ ] **Step 2: Update the offline strategy doc**

Add the new cache units:

```markdown
- `fuel_benchmark_cache`
  유종별 현재 전국 평균과 주간 평균 캐시
- `fuel_benchmark_daily_cache`
  유종별 최근 7일 전국 평균 포인트
```

And add one paragraph:

```markdown
station-list는 주유소 스냅샷과 별도로 benchmark 캐시를 읽는다. benchmark는 station별이 아니라 유종별 공유 캐시라 호출 수가 작고, demo에서도 같은 문구를 안정적으로 재현할 수 있다.
```

- [ ] **Step 3: Run the full verification set**

Run:

```bash
./gradlew \
  :domain:station:test \
  :core:network:test \
  :core:database:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :tools:demo-seed:test \
  :app:testDemoDebugUnitTest \
  :feature:station-list:testDebugUnitTest
```

Expected: PASS across all touched modules.

- [ ] **Step 4: Commit**

```bash
git add README.md docs/offline-strategy.md
git commit -m "docs: document national benchmark flow"
```

## Self-Review

### Spec coverage

- `avgAllPrice.do` wired: covered in Task 2.
- `avgRecentPrice.do` wired: covered in Task 2.
- `avgLastWeek.do` wired: covered in Task 2.
- low-call shared cache: covered in Tasks 3 and 4.
- demo parity: covered in Task 5.
- station-list UI: covered in Task 6.
- docs: covered in Task 7.
- watchlist: intentionally deferred, not a gap.
- regional averages: intentionally deferred, not a gap.

### Placeholder scan

- No `TBD`, `TODO`, or "handle later" placeholders remain.
- Every code step includes actual Kotlin/SQL content.
- Every test step includes an exact command.

### Type consistency

- `FuelBenchmarkRepository` is introduced in Task 1 and implemented in Task 4.
- `FuelBenchmarkEntity` / `FuelBenchmarkDailyEntity` are created in Task 3 and used in Task 4.
- `AverageGap` is introduced in Task 1 and used in Task 6.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-18-gasstation-national-average-station-list.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
