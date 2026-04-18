# GasStation Portfolio Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 가격 변동 인사이트, 관심 주유소 비교, 검증 가능한 품질 계층을 추가해서 GasStation을 포트폴리오 대표작으로 업그레이드한다.

**Architecture:** 먼저 reflection 기반 startup 훅, demo location 바인딩 위치, 빈 `core:ui` 모듈 같은 멀티모듈 경계 문제를 정리한다. 그 다음 `core:database`에 가격 히스토리와 관심 주유소 저장을 추가하고, `data:station`이 읽기 모델을 조합해 `feature:station-list`와 신규 `feature:watchlist`에 공급한다. 마지막에 analytics/benchmark/README를 붙여 검증 결과까지 한 묶음으로 마무리한다.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines/Flow, Room, Hilt, Navigation Compose, JUnit4, Turbine, Robolectric, Macrobenchmark

---

## Scope Check

이 작업은 하나의 사용자 흐름에 묶인 단일 확장 계획이다. 가격 변화 계산, 관심 주유소 저장, 비교 화면, analytics, benchmark는 모두 "주유소 탐색 의사결정 경험"을 보강하는 같은 제품 흐름에 속하므로 별도 계획으로 분리하지 않는다.

## File Structure

### Module Hygiene
- Create: `app/src/main/java/com/gasstation/startup/AppStartupHook.kt`
- Create: `app/src/main/java/com/gasstation/startup/AppStartupRunner.kt`
- Create: `app/src/main/java/com/gasstation/di/DemoLocationOverrideModule.kt`
- Create: `app/src/main/java/com/gasstation/di/ExternalMapModule.kt`
- Create: `app/src/test/java/com/gasstation/startup/AppStartupRunnerTest.kt`
- Create: `app/src/demo/kotlin/com/gasstation/di/DemoStartupModule.kt`
- Create: `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
- Create: `app/src/prod/kotlin/com/gasstation/di/ProdStartupModule.kt`
- Create: `app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt`
- Modify: `app/src/main/java/com/gasstation/App.kt`
- Modify: `settings.gradle.kts`
- Modify: `feature/settings/build.gradle.kts`
- Modify: `feature/station-list/build.gradle.kts`
- Delete: `app/src/main/java/com/gasstation/di/LocationModule.kt`
- Delete: `app/src/demo/kotlin/com/gasstation/DemoSeedData.kt`
- Delete: `app/src/prod/kotlin/com/gasstation/ProdSecretsModule.kt`
- Delete: `core/location/src/main/kotlin/com/gasstation/core/location/DemoLocationOverrideModule.kt`
- Delete: `core/ui/build.gradle.kts`

### Database / Storage
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationPriceHistoryEntity.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationPriceHistoryDao.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationEntity.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationDao.kt`
- Create: `core/database/src/test/kotlin/com/gasstation/core/database/station/StationPriceHistoryDaoTest.kt`
- Create: `core/database/src/test/kotlin/com/gasstation/core/database/station/WatchedStationDaoTest.kt`
- Modify: `core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt`
- Modify: `core/database/src/main/kotlin/com/gasstation/core/database/DatabaseModule.kt`

### Domain / Data
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationPriceDelta.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationListEntry.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/WatchedStationSummary.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationEvent.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/StationEventLogger.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveWatchlistUseCase.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/UpdateWatchStateUseCase.kt`
- Create: `domain/station/src/test/kotlin/com/gasstation/domain/station/StationPriceDeltaTest.kt`
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt`
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt`
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/WatchlistRepositoryTest.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`

### Features / App
- Create: `feature/watchlist/build.gradle.kts`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistAction.kt`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistRoute.kt`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistUiState.kt`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistViewModel.kt`
- Create: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistViewModelTest.kt`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationDestination.kt`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- Create: `app/src/main/java/com/gasstation/analytics/LogcatStationEventLogger.kt`
- Create: `app/src/main/java/com/gasstation/di/AnalyticsModule.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

### Verification / Docs
- Create: `app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt`
- Modify: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`
- Create: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/StationListBenchmark.kt`
- Modify: `benchmark/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/state-model.md`
- Modify: `docs/offline-strategy.md`

### Task 0: Tighten module boundaries before feature work

**Files:**
- Create: `app/src/main/java/com/gasstation/startup/AppStartupHook.kt`
- Create: `app/src/main/java/com/gasstation/startup/AppStartupRunner.kt`
- Create: `app/src/main/java/com/gasstation/di/DemoLocationOverrideModule.kt`
- Create: `app/src/main/java/com/gasstation/di/ExternalMapModule.kt`
- Create: `app/src/test/java/com/gasstation/startup/AppStartupRunnerTest.kt`
- Create: `app/src/demo/kotlin/com/gasstation/di/DemoStartupModule.kt`
- Create: `app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt`
- Create: `app/src/prod/kotlin/com/gasstation/di/ProdStartupModule.kt`
- Create: `app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt`
- Modify: `app/src/main/java/com/gasstation/App.kt`
- Modify: `settings.gradle.kts`
- Modify: `feature/settings/build.gradle.kts`
- Modify: `feature/station-list/build.gradle.kts`
- Delete: `app/src/main/java/com/gasstation/di/LocationModule.kt`
- Delete: `app/src/demo/kotlin/com/gasstation/DemoSeedData.kt`
- Delete: `app/src/prod/kotlin/com/gasstation/ProdSecretsModule.kt`
- Delete: `core/location/src/main/kotlin/com/gasstation/core/location/DemoLocationOverrideModule.kt`
- Delete: `core/ui/build.gradle.kts`

- [ ] **Step 1: Write the failing startup runner test**

```kotlin
package com.gasstation.startup

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStartupRunnerTest {
    @Test
    fun `runner executes every registered hook`() {
        val calls = mutableListOf<String>()
        val runner = AppStartupRunner(
            hooks = setOf(
                AppStartupHook { calls += "demo" },
                AppStartupHook { calls += "prod" },
            ),
        )

        runner.run(application = Application())

        assertEquals(setOf("demo", "prod"), calls.toSet())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "*AppStartupRunnerTest"
```

Expected: `BUILD FAILED` because `AppStartupRunner` and `AppStartupHook` do not exist yet.

- [ ] **Step 3: Replace reflection startup hooks, move demo binding ownership to app, and remove the empty UI module**

Create the startup abstractions:

```kotlin
// app/src/main/java/com/gasstation/startup/AppStartupHook.kt
package com.gasstation.startup

import android.app.Application

fun interface AppStartupHook {
    fun run(application: Application)
}
```

```kotlin
// app/src/main/java/com/gasstation/startup/AppStartupRunner.kt
package com.gasstation.startup

import android.app.Application
import javax.inject.Inject

class AppStartupRunner @Inject constructor(
    private val hooks: Set<@JvmSuppressWildcards AppStartupHook>,
) {
    fun run(application: Application) {
        hooks.forEach { hook -> hook.run(application) }
    }
}
```

Update `App.kt` to use injection instead of reflection:

```kotlin
@HiltAndroidApp
class App : Application() {
    @Inject
    lateinit var appStartupRunner: AppStartupRunner

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("TimberInitializer is initialized.")
        }
        appStartupRunner.run(this)
    }
}
```

Move demo/prod startup responsibilities into source-set-specific hook classes:

```kotlin
// app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt
package com.gasstation.startup

import android.app.Application
import androidx.room.Room
import com.gasstation.core.database.GasStationDatabase
import com.gasstation.core.database.station.StationCacheEntity
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder
import com.gasstation.domain.station.model.StationQuery
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class DemoSeedStartupHook @Inject constructor() : AppStartupHook {
    override fun run(application: Application) {
        val database = Room.databaseBuilder(
            application,
            GasStationDatabase::class.java,
            GasStationDatabase.DATABASE_NAME,
        ).fallbackToDestructiveMigration(dropAllTables = true).build()

        runBlocking {
            val reviewerCoordinates = Coordinates(37.498095, 127.02761)
            val cacheKey = StationQuery(
                coordinates = reviewerCoordinates,
                radius = SearchRadius.KM_3,
                fuelType = FuelType.GASOLINE,
                brandFilter = BrandFilter.ALL,
                sortOrder = SortOrder.DISTANCE,
                mapProvider = MapProvider.TMAP,
            ).toCacheKey(bucketMeters = 250)
            val fetchedAt = System.currentTimeMillis()
            database.stationCacheDao().replaceSnapshot(
                latitudeBucket = cacheKey.latitudeBucket,
                longitudeBucket = cacheKey.longitudeBucket,
                radiusMeters = cacheKey.radiusMeters,
                fuelType = cacheKey.fuelType.name,
                entities = listOf(
                    StationCacheEntity(
                        latitudeBucket = cacheKey.latitudeBucket,
                        longitudeBucket = cacheKey.longitudeBucket,
                        radiusMeters = cacheKey.radiusMeters,
                        fuelType = cacheKey.fuelType.name,
                        stationId = "demo-1",
                        brandCode = "SKE",
                        name = "강남역 데모 주유소",
                        priceWon = 1_639,
                        latitude = 37.49761,
                        longitude = 127.02874,
                        fetchedAtEpochMillis = fetchedAt,
                    ),
                ),
            )
        }
        database.close()
    }
}
```

```kotlin
// app/src/demo/kotlin/com/gasstation/di/DemoStartupModule.kt
package com.gasstation.di

import com.gasstation.startup.AppStartupHook
import com.gasstation.startup.DemoSeedStartupHook
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoStartupModule {
    @Binds
    @IntoSet
    abstract fun bindDemoSeedStartupHook(
        hook: DemoSeedStartupHook,
    ): AppStartupHook
}
```

```kotlin
// app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt
package com.gasstation.startup

import android.app.Application
import com.gasstation.BuildConfig
import javax.inject.Inject
import timber.log.Timber

class ProdSecretsStartupHook @Inject constructor() : AppStartupHook {
    override fun run(application: Application) {
        val missingSecrets = buildList {
            if (BuildConfig.OPINET_API_KEY.isBlank()) add("opinet.apikey")
            if (BuildConfig.KAKAO_API_KEY.isBlank()) add("kakao.apikey")
        }
        check(missingSecrets.isEmpty()) {
            "Prod flavor requires local secrets: ${missingSecrets.joinToString()}."
        }
        Timber.i("Prod secrets loaded from local Gradle properties.")
    }
}
```

```kotlin
// app/src/prod/kotlin/com/gasstation/di/ProdStartupModule.kt
package com.gasstation.di

import com.gasstation.startup.AppStartupHook
import com.gasstation.startup.ProdSecretsStartupHook
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ProdStartupModule {
    @Binds
    @IntoSet
    abstract fun bindProdStartupHook(
        hook: ProdSecretsStartupHook,
    ): AppStartupHook
}
```

Move optional demo location binding ownership to `app`:

```kotlin
// app/src/main/java/com/gasstation/di/DemoLocationOverrideModule.kt
package com.gasstation.di

import com.gasstation.core.location.DemoLocationOverride
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoLocationOverrideModule {
    @BindsOptionalOf
    abstract fun bindDemoLocationOverride(): DemoLocationOverride
}
```

Rename the external map binding module:

```kotlin
// app/src/main/java/com/gasstation/di/ExternalMapModule.kt
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
object ExternalMapModule {
    @Provides
    @Singleton
    fun provideExternalMapLauncher(
        launcher: IntentExternalMapLauncher,
    ): ExternalMapLauncher = launcher
}
```

Remove the empty `core:ui` module from `settings.gradle.kts`, `feature/settings/build.gradle.kts`, and `feature/station-list/build.gradle.kts` by deleting the module include and the two `implementation(project(":core:ui"))` lines.

- [ ] **Step 4: Run focused verification**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "*AppStartupRunnerTest"
./gradlew :core:location:testDebugUnitTest
./gradlew :app:compileDemoDebugKotlin :app:compileProdDebugKotlin
```

Expected:

- First command: `BUILD SUCCESSFUL`
- Second command: `BUILD SUCCESSFUL`
- Third command: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/java/com/gasstation/App.kt \
  app/src/main/java/com/gasstation/di/DemoLocationOverrideModule.kt \
  app/src/main/java/com/gasstation/di/ExternalMapModule.kt \
  app/src/main/java/com/gasstation/startup/AppStartupHook.kt \
  app/src/main/java/com/gasstation/startup/AppStartupRunner.kt \
  app/src/test/java/com/gasstation/startup/AppStartupRunnerTest.kt \
  app/src/demo/kotlin/com/gasstation/di/DemoStartupModule.kt \
  app/src/demo/kotlin/com/gasstation/startup/DemoSeedStartupHook.kt \
  app/src/prod/kotlin/com/gasstation/di/ProdStartupModule.kt \
  app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt \
  settings.gradle.kts \
  feature/settings/build.gradle.kts \
  feature/station-list/build.gradle.kts \
  core/location/src/main/kotlin/com/gasstation/core/location/DemoLocationOverrideModule.kt \
  app/src/main/java/com/gasstation/di/LocationModule.kt \
  app/src/demo/kotlin/com/gasstation/DemoSeedData.kt \
  app/src/prod/kotlin/com/gasstation/ProdSecretsModule.kt \
  core/ui/build.gradle.kts
git commit -m "refactor: tighten app and module boundaries"
```

### Task 1: Add Room storage for price history and watched stations

**Files:**
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationPriceHistoryEntity.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/StationPriceHistoryDao.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationEntity.kt`
- Create: `core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationDao.kt`
- Create: `core/database/src/test/kotlin/com/gasstation/core/database/station/StationPriceHistoryDaoTest.kt`
- Create: `core/database/src/test/kotlin/com/gasstation/core/database/station/WatchedStationDaoTest.kt`
- Modify: `core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt`
- Modify: `core/database/src/main/kotlin/com/gasstation/core/database/DatabaseModule.kt`

- [ ] **Step 1: Write failing DAO tests for history trimming and watch toggling**

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
class StationPriceHistoryDaoTest {
    private lateinit var database: GasStationDatabase
    private lateinit var dao: StationPriceHistoryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GasStationDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.stationPriceHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `keepLatestTenByStation removes older rows`() = runBlocking {
        repeat(12) { index ->
            dao.insert(
                StationPriceHistoryEntity(
                    stationId = "station-1",
                    priceWon = 1600 + index,
                    fetchedAtEpochMillis = 1_744_947_200_000L + index,
                ),
            )
        }

        dao.keepLatestTenByStation("station-1")

        val rows = dao.observeByStationIds(listOf("station-1")).first()
        assertEquals((1602..1611).toList(), rows.map { it.priceWon })
    }
}
```

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
class WatchedStationDaoTest {
    private lateinit var database: GasStationDatabase
    private lateinit var dao: WatchedStationDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GasStationDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.watchedStationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert followed by delete updates watched station ids`() = runBlocking {
        dao.upsert(
            WatchedStationEntity(
                stationId = "station-1",
                name = "Gangnam First",
                brandCode = "GSC",
                latitude = 37.498095,
                longitude = 127.027610,
                watchedAtEpochMillis = 1_744_947_200_000L,
            ),
        )
        assertEquals(setOf("station-1"), dao.observeWatchedStationIds().first().toSet())

        dao.delete("station-1")

        assertEquals(emptySet<String>(), dao.observeWatchedStationIds().first().toSet())
    }
}
```

- [ ] **Step 2: Run the new tests and verify they fail**

Run:

```bash
./gradlew :core:database:testDebugUnitTest --tests "*StationPriceHistoryDaoTest" --tests "*WatchedStationDaoTest"
```

Expected: `BUILD FAILED` with unresolved references for the new DAO and entity types.

- [ ] **Step 3: Add the Room entities, DAOs, and database wiring**

```kotlin
// core/database/src/main/kotlin/com/gasstation/core/database/station/StationPriceHistoryEntity.kt
@Entity(
    tableName = "station_price_history",
    primaryKeys = ["stationId", "fetchedAtEpochMillis"],
    indices = [Index(value = ["stationId"]), Index(value = ["fetchedAtEpochMillis"])],
)
data class StationPriceHistoryEntity(
    val stationId: String,
    val priceWon: Int,
    val fetchedAtEpochMillis: Long,
)
```

```kotlin
// core/database/src/main/kotlin/com/gasstation/core/database/station/StationPriceHistoryDao.kt
@Dao
interface StationPriceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StationPriceHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<StationPriceHistoryEntity>)

    @Query(
        """
        SELECT * FROM station_price_history
        WHERE stationId IN (:stationIds)
        ORDER BY stationId ASC, fetchedAtEpochMillis DESC
        """,
    )
    fun observeByStationIds(stationIds: List<String>): Flow<List<StationPriceHistoryEntity>>

    @Query(
        """
        DELETE FROM station_price_history
        WHERE stationId = :stationId
          AND fetchedAtEpochMillis NOT IN (
              SELECT fetchedAtEpochMillis FROM station_price_history
              WHERE stationId = :stationId
              ORDER BY fetchedAtEpochMillis DESC
              LIMIT 10
          )
        """,
    )
    suspend fun keepLatestTenByStation(stationId: String)
}
```

```kotlin
// core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationEntity.kt
@Entity(tableName = "watched_station")
data class WatchedStationEntity(
    @PrimaryKey val stationId: String,
    val name: String,
    val brandCode: String,
    val latitude: Double,
    val longitude: Double,
    val watchedAtEpochMillis: Long,
)
```

```kotlin
// core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationDao.kt
@Dao
interface WatchedStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchedStationEntity)

    @Query("DELETE FROM watched_station WHERE stationId = :stationId")
    suspend fun delete(stationId: String)

    @Query("SELECT stationId FROM watched_station ORDER BY watchedAtEpochMillis DESC")
    fun observeWatchedStationIds(): Flow<List<String>>

    @Query("SELECT * FROM watched_station ORDER BY watchedAtEpochMillis DESC")
    fun observeWatchedStations(): Flow<List<WatchedStationEntity>>
}
```

```kotlin
// core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt
@Database(
    entities = [
        StationCacheEntity::class,
        StationPriceHistoryEntity::class,
        WatchedStationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class GasStationDatabase : RoomDatabase() {
    abstract fun stationCacheDao(): StationCacheDao
    abstract fun stationPriceHistoryDao(): StationPriceHistoryDao
    abstract fun watchedStationDao(): WatchedStationDao
}
```

Update `DatabaseModule.kt` to provide `stationPriceHistoryDao()` and `watchedStationDao()` alongside the existing cache DAO.

- [ ] **Step 4: Run focused verification**

Run:

```bash
./gradlew :core:database:testDebugUnitTest --tests "*StationCacheDaoTest" --tests "*StationPriceHistoryDaoTest" --tests "*WatchedStationDaoTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add \
  core/database/src/main/kotlin/com/gasstation/core/database/GasStationDatabase.kt \
  core/database/src/main/kotlin/com/gasstation/core/database/DatabaseModule.kt \
  core/database/src/main/kotlin/com/gasstation/core/database/station/StationPriceHistoryEntity.kt \
  core/database/src/main/kotlin/com/gasstation/core/database/station/StationPriceHistoryDao.kt \
  core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationEntity.kt \
  core/database/src/main/kotlin/com/gasstation/core/database/station/WatchedStationDao.kt \
  core/database/src/test/kotlin/com/gasstation/core/database/station/StationPriceHistoryDaoTest.kt \
  core/database/src/test/kotlin/com/gasstation/core/database/station/WatchedStationDaoTest.kt
git commit -m "feat: add station history and watch storage"
```

### Task 2: Extend domain contracts and repository read models

**Files:**
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationPriceDelta.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationListEntry.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/WatchedStationSummary.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationEvent.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/StationEventLogger.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveWatchlistUseCase.kt`
- Create: `domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/UpdateWatchStateUseCase.kt`
- Create: `domain/station/src/test/kotlin/com/gasstation/domain/station/StationPriceDeltaTest.kt`
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt`
- Modify: `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt`

- [ ] **Step 1: Write the failing domain test for delta conversion**

```kotlin
package com.gasstation.domain.station

import com.gasstation.domain.station.model.StationPriceDelta
import org.junit.Assert.assertEquals
import org.junit.Test

class StationPriceDeltaTest {
    @Test
    fun `from prices returns decreased when current price is cheaper`() {
        assertEquals(
            StationPriceDelta.Decreased(20),
            StationPriceDelta.from(previousPriceWon = 1700, currentPriceWon = 1680),
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :domain:station:test --tests "*StationPriceDeltaTest"
```

Expected: `BUILD FAILED` because `StationPriceDelta` does not exist yet.

- [ ] **Step 3: Add read-model types and repository contract changes**

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationPriceDelta.kt
sealed interface StationPriceDelta {
    data object Unavailable : StationPriceDelta
    data object Unchanged : StationPriceDelta
    data class Increased(val amountWon: Int) : StationPriceDelta
    data class Decreased(val amountWon: Int) : StationPriceDelta

    companion object {
        fun from(previousPriceWon: Int?, currentPriceWon: Int): StationPriceDelta = when {
            previousPriceWon == null -> Unavailable
            previousPriceWon == currentPriceWon -> Unchanged
            previousPriceWon < currentPriceWon -> Increased(currentPriceWon - previousPriceWon)
            else -> Decreased(previousPriceWon - currentPriceWon)
        }
    }
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationListEntry.kt
data class StationListEntry(
    val station: Station,
    val priceDelta: StationPriceDelta,
    val isWatched: Boolean,
    val lastSeenAt: Instant?,
)
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/WatchedStationSummary.kt
data class WatchedStationSummary(
    val station: Station,
    val priceDelta: StationPriceDelta,
    val lastSeenAt: Instant?,
)
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationEvent.kt
sealed interface StationEvent {
    data class SearchRefreshed(
        val radius: SearchRadius,
        val fuelType: FuelType,
        val sortOrder: SortOrder,
        val stale: Boolean,
    ) : StationEvent

    data class WatchToggled(val stationId: String, val watched: Boolean) : StationEvent
    data class CompareViewed(val count: Int) : StationEvent
    data class ExternalMapOpened(val stationId: String, val provider: MapProvider) : StationEvent
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt
interface StationRepository {
    fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult>
    fun observeWatchlist(origin: Coordinates): Flow<List<WatchedStationSummary>>
    suspend fun refreshNearbyStations(query: StationQuery)
    suspend fun updateWatchState(station: Station, watched: Boolean)
}
```

```kotlin
// domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt
data class StationSearchResult(
    val stations: List<StationListEntry>,
    val freshness: StationFreshness,
    val fetchedAt: Instant?,
)
```

Add thin use cases `ObserveWatchlistUseCase` and `UpdateWatchStateUseCase` that simply delegate to the repository.

- [ ] **Step 4: Run focused verification**

Run:

```bash
./gradlew :domain:station:test --tests "*StationPriceDeltaTest" --tests "*DomainContractSurfaceTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add \
  domain/station/src/main/kotlin/com/gasstation/domain/station/StationRepository.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/StationEventLogger.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationPriceDelta.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationListEntry.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/model/WatchedStationSummary.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationEvent.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationSearchResult.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/ObserveWatchlistUseCase.kt \
  domain/station/src/main/kotlin/com/gasstation/domain/station/usecase/UpdateWatchStateUseCase.kt \
  domain/station/src/test/kotlin/com/gasstation/domain/station/StationPriceDeltaTest.kt
git commit -m "feat: add station insights domain models"
```

### Task 3: Implement repository support for price deltas and watchlists

**Files:**
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`
- Modify: `data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt`
- Create: `data/station/src/test/kotlin/com/gasstation/data/station/WatchlistRepositoryTest.kt`

- [ ] **Step 1: Add failing repository tests for delta enrichment and watchlist observation**

```kotlin
@Test
fun `observeNearbyStations enriches snapshot with price delta and watched flag`() = runBlocking {
    val query = stationQuery()
    val cacheKey = query.toCacheKey(bucketMeters = CACHE_BUCKET_METERS)
    val stationCacheDao = RecordingStationCacheDao()
    val priceHistoryDao = RecordingStationPriceHistoryDao(
        history = listOf(
            history(stationId = "station-1", priceWon = 1710, fetchedAt = now.minusSeconds(300)),
        ),
    )
    val watchedStationDao = RecordingWatchedStationDao(
        watchedIds = listOf("station-1"),
    )
    stationCacheDao.seed(
        stationEntity(cacheKey = cacheKey, stationId = "station-1", priceWon = 1680),
    )

    val repository = repository(
        stationCacheDao = stationCacheDao,
        priceHistoryDao = priceHistoryDao,
        watchedStationDao = watchedStationDao,
    )

    val result = repository.observeNearbyStations(query).first()

    assertEquals(StationPriceDelta.Decreased(30), result.stations.single().priceDelta)
    assertEquals(true, result.stations.single().isWatched)
}
```

```kotlin
@Test
fun `observeWatchlist returns watched stations sorted by watched time`() = runBlocking {
    val repository = repository(
        watchedStationDao = RecordingWatchedStationDao(
            watchedStations = listOf(
                watched(stationId = "station-2", watchedAt = now.minusSeconds(10)),
                watched(stationId = "station-1", watchedAt = now.minusSeconds(5)),
            ),
        ),
        priceHistoryDao = RecordingStationPriceHistoryDao(
            history = listOf(history(stationId = "station-1", priceWon = 1650, fetchedAt = now)),
        ),
    )

    val items = repository.observeWatchlist(Coordinates(37.498095, 127.027610)).first()

    assertEquals(listOf("station-1", "station-2"), items.map { it.station.id })
}
```

- [ ] **Step 2: Run the repository tests to verify they fail**

Run:

```bash
./gradlew :data:station:testDebugUnitTest --tests "*DefaultStationRepositoryTest" --tests "*WatchlistRepositoryTest"
```

Expected: `BUILD FAILED` because the repository constructor and test doubles do not yet support the new DAOs and models.

- [ ] **Step 3: Implement data mapping and repository behavior**

Update `DefaultStationRepository` constructor to receive `StationPriceHistoryDao` and `WatchedStationDao`.

```kotlin
class DefaultStationRepository @Inject constructor(
    private val stationCacheDao: StationCacheDao,
    private val stationPriceHistoryDao: StationPriceHistoryDao,
    private val watchedStationDao: WatchedStationDao,
    private val remoteDataSource: StationRemoteDataSource,
    private val cachePolicy: StationCachePolicy,
    private val clock: Clock,
) : StationRepository
```

Implement enrichment in `observeNearbyStations` by combining cache, watched IDs, and latest history rows:

```kotlin
override fun observeNearbyStations(query: StationQuery): Flow<StationSearchResult> {
    val cacheKey = query.toCacheKey(bucketMeters = DEFAULT_BUCKET_METERS)
    val cacheFlow = stationCacheDao.observeStations(
        latitudeBucket = cacheKey.latitudeBucket,
        longitudeBucket = cacheKey.longitudeBucket,
        radiusMeters = cacheKey.radiusMeters,
        fuelType = cacheKey.fuelType.name,
    )

    return cacheFlow.flatMapLatest { cachedStations ->
        val stationIds = cachedStations.map { it.stationId }.distinct()
        combine(
            flowOf(cachedStations),
            watchedStationDao.observeWatchedStationIds(),
            stationPriceHistoryDao.observeByStationIds(stationIds),
        ) { cacheRows, watchedIds, historyRows ->
            val previousPriceById = historyRows
                .groupBy { it.stationId }
                .mapValues { (_, rows) -> rows.drop(1).firstOrNull()?.priceWon }

            val entries = cacheRows.map { row ->
                val station = row.toDomainStation(query.coordinates)
                StationListEntry(
                    station = station,
                    priceDelta = StationPriceDelta.from(previousPriceById[row.stationId], row.priceWon),
                    isWatched = row.stationId in watchedIds,
                    lastSeenAt = Instant.ofEpochMilli(row.fetchedAtEpochMillis),
                )
            }
            StationSearchResult(
                stations = sortEntries(entries, query.sortOrder, query.brandFilter),
                freshness = cacheRows.maxOfOrNull { it.fetchedAtEpochMillis }
                    ?.let(Instant::ofEpochMilli)
                    ?.let { cachePolicy.freshnessOf(it, clock.instant()) }
                    ?: StationFreshness.Stale,
                fetchedAt = cacheRows.maxOfOrNull { it.fetchedAtEpochMillis }?.let(Instant::ofEpochMilli),
            )
        }
    }
}
```

When refresh succeeds, persist history rows and trim to the latest 10 rows per station:

```kotlin
val entities = remoteStations.stations.map { it.toEntity(cacheKey, fetchedAt) }
stationCacheDao.replaceSnapshot(..., entities = entities)
stationPriceHistoryDao.insertAll(
    remoteStations.stations.map { station ->
        StationPriceHistoryEntity(
            stationId = station.stationId,
            priceWon = station.priceWon,
            fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
        )
    },
)
remoteStations.stations.forEach { station ->
    stationPriceHistoryDao.keepLatestTenByStation(station.stationId)
}
```

Implement `updateWatchState` using `WatchedStationDao.upsert/delete`, and `observeWatchlist` by mapping watched rows to `WatchedStationSummary`.

Update `StationDataModule.kt` if the repository provider is explicit in tests or DI.

- [ ] **Step 4: Run focused verification**

Run:

```bash
./gradlew :data:station:testDebugUnitTest --tests "*DefaultStationRepositoryTest" --tests "*WatchlistRepositoryTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add \
  data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt \
  data/station/src/main/kotlin/com/gasstation/data/station/StationDataModule.kt \
  data/station/src/test/kotlin/com/gasstation/data/station/DefaultStationRepositoryTest.kt \
  data/station/src/test/kotlin/com/gasstation/data/station/WatchlistRepositoryTest.kt
git commit -m "feat: implement station insights repository"
```

### Task 4: Upgrade the station list feature with watch toggles and analytics

**Files:**
- Create: `app/src/main/java/com/gasstation/analytics/LogcatStationEventLogger.kt`
- Create: `app/src/main/java/com/gasstation/di/AnalyticsModule.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

- [ ] **Step 1: Add failing ViewModel tests for watch toggling and analytics emission**

```kotlin
@Test
fun `watch tap updates repository and emits analytics`() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    try {
        val repository = FakeStationRepository(
            result = StationSearchResult(
                stations = listOf(stationEntry(isWatched = false)),
                freshness = StationFreshness.Fresh,
                fetchedAt = null,
            ),
        )
        val analytics = RecordingStationEventLogger()
        val viewModel = StationListViewModel(
            observeNearbyStations = ObserveNearbyStationsUseCase(repository),
            refreshNearbyStations = RefreshNearbyStationsUseCase(repository),
            updateWatchState = UpdateWatchStateUseCase(repository),
            observeUserPreferences = ObserveUserPreferencesUseCase(FakeSettingsRepository(UserPreferences.default())),
            settingsRepository = FakeSettingsRepository(UserPreferences.default()),
            foregroundLocationProvider = FakeForegroundLocationProvider(Coordinates(37.498095, 127.027610)),
            stationEventLogger = analytics,
        )

        viewModel.onAction(StationListAction.WatchToggled("station-1", true))
        advanceUntilIdle()

        assertEquals(listOf("station-1" to true), repository.watchUpdates)
        assertEquals(1, analytics.events.size)
    } finally {
        Dispatchers.resetMain()
    }
}
```

- [ ] **Step 2: Run the feature test to verify it fails**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListViewModelTest"
```

Expected: `BUILD FAILED` because the new action, use case, and logger injection do not exist yet.

- [ ] **Step 3: Implement UI model, actions, ViewModel plumbing, and logger binding**

Update the item UI model to carry insight fields:

```kotlin
data class StationListItemUiModel(
    val id: String,
    val name: String,
    val brandLabel: String,
    val priceLabel: String,
    val distanceLabel: String,
    val priceDeltaLabel: String,
    val isWatched: Boolean,
    val latitude: Double,
    val longitude: Double,
) {
    constructor(entry: StationListEntry) : this(
        id = entry.station.id,
        name = entry.station.name,
        brandLabel = entry.station.brand.toLabel(),
        priceLabel = "${entry.station.price.value}원",
        distanceLabel = "${entry.station.distance.value}m",
        priceDeltaLabel = entry.priceDelta.toLabel(),
        isWatched = entry.isWatched,
        latitude = entry.station.coordinates.latitude,
        longitude = entry.station.coordinates.longitude,
    )
}
```

Add a watch action:

```kotlin
sealed interface StationListAction {
    data object RefreshRequested : StationListAction
    data object RetryClicked : StationListAction
    data object SortToggleRequested : StationListAction
    data class WatchToggled(val stationId: String, val watched: Boolean) : StationListAction
    data class StationClicked(val station: StationListItemUiModel) : StationListAction
    data class PermissionChanged(val permissionState: LocationPermissionState) : StationListAction
    data class GpsAvailabilityChanged(val isEnabled: Boolean) : StationListAction
}
```

In `StationListViewModel`, inject `UpdateWatchStateUseCase` and `StationEventLogger`, then handle the action:

```kotlin
is StationListAction.WatchToggled -> viewModelScope.launch {
    val entry = searchResult.value.stations.first { it.station.id == action.stationId }
    updateWatchState(entry.station, action.watched)
    stationEventLogger.log(StationEvent.WatchToggled(action.stationId, action.watched))
}
```

In `StationListScreen.kt`, render the delta line and watch button inside each card:

```kotlin
Text(
    text = "${station.priceLabel} · ${station.distanceLabel}",
    style = MaterialTheme.typography.bodyLarge,
)
Text(
    text = station.priceDeltaLabel,
    style = MaterialTheme.typography.bodyMedium,
)
IconButton(onClick = { onAction(StationListAction.WatchToggled(station.id, !station.isWatched)) }) {
    Icon(
        imageVector = if (station.isWatched) Icons.Default.Star else Icons.Default.StarBorder,
        contentDescription = "관심 주유소 토글",
    )
}
```

Bind the logger in `app/src/main/java/com/gasstation/di/AnalyticsModule.kt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    abstract fun bindStationEventLogger(
        impl: LogcatStationEventLogger,
    ): StationEventLogger
}
```

- [ ] **Step 4: Run focused verification**

Run:

```bash
./gradlew :feature:station-list:testDebugUnitTest --tests "*StationListViewModelTest"
./gradlew :app:compileDemoDebugKotlin
```

Expected:

- First command: `BUILD SUCCESSFUL`
- Second command: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/java/com/gasstation/analytics/LogcatStationEventLogger.kt \
  app/src/main/java/com/gasstation/di/AnalyticsModule.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListAction.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt \
  feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt
git commit -m "feat: add station watch and analytics flow"
```

### Task 5: Add the watchlist feature module and navigation

**Files:**
- Create: `feature/watchlist/build.gradle.kts`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistAction.kt`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistRoute.kt`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistUiState.kt`
- Create: `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistViewModel.kt`
- Create: `feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistViewModelTest.kt`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationDestination.kt`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt`

- [ ] **Step 1: Write the failing ViewModel test for watchlist sorting**

```kotlin
package com.gasstation.feature.watchlist

import app.cash.turbine.test
import com.gasstation.core.model.Coordinates
import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import com.gasstation.domain.station.model.Brand
import com.gasstation.domain.station.model.Station
import com.gasstation.domain.station.model.StationPriceDelta
import com.gasstation.domain.station.model.WatchedStationSummary
import com.gasstation.domain.station.usecase.ObserveWatchlistUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistViewModelTest {
    @Test
    fun `watchlist exposes watched summaries from repository`() = runTest {
        val station = Station(
            id = "station-1",
            name = "Gangnam First",
            brand = Brand.GSC,
            price = MoneyWon(1680),
            distance = DistanceMeters(300),
            coordinates = Coordinates(37.498095, 127.027610),
        )
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "latitude" to 37.498095,
                "longitude" to 127.027610,
            ),
        )
        val viewModel = WatchlistViewModel(
            observeWatchlist = ObserveWatchlistUseCase(FakeWatchlistRepository(listOf(
                WatchedStationSummary(
                    station = station,
                    priceDelta = StationPriceDelta.Decreased(20),
                    lastSeenAt = null,
                ),
            ))),
            savedStateHandle = savedStateHandle,
        )

        viewModel.uiState.test {
            assertEquals("station-1", awaitItem().stations.single().id)
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :feature:watchlist:testDebugUnitTest --tests "*WatchlistViewModelTest"
```

Expected: `BUILD FAILED` because the module and ViewModel do not yet exist.

- [ ] **Step 3: Create the feature module and wire navigation**

Add the module to `settings.gradle.kts` and `app/build.gradle.kts`:

```kotlin
include(":feature:watchlist")
```

```kotlin
implementation(project(":feature:watchlist"))
```

Use the same plugin set as `feature/station-list/build.gradle.kts`, with dependencies on `:domain:station`, `:core:model`, and `:core:designsystem`.

Create a minimal route and screen:

```kotlin
// feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistUiState.kt
data class WatchlistUiState(
    val stations: List<WatchlistItemUiModel> = emptyList(),
)
```

```kotlin
// feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistViewModel.kt
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    observeWatchlist: ObserveWatchlistUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val origin = Coordinates(
        latitude = checkNotNull(savedStateHandle["latitude"]),
        longitude = checkNotNull(savedStateHandle["longitude"]),
    )

    val uiState = observeWatchlist(origin)
        .map { stations -> WatchlistUiState(stations.map(::WatchlistItemUiModel)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchlistUiState())
}
```

```kotlin
// app/src/main/java/com/gasstation/navigation/GasStationDestination.kt
data object Watchlist : GasStationDestination {
    override val route: String = "watchlist/{latitude}/{longitude}"

    fun createRoute(coordinates: Coordinates): String =
        "watchlist/${coordinates.latitude}/${coordinates.longitude}"
}
```

```kotlin
// app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt
composable(GasStationDestination.Watchlist.route) {
    WatchlistRoute(
        onOpenExternalMap = { effect ->
            externalMapLauncher.open(
                provider = effect.provider,
                stationName = effect.stationName,
                latitude = effect.latitude,
                longitude = effect.longitude,
            )
        },
    )
}
```

Expose navigation from the list screen with a top-app-bar action like `관심 비교`, and pass the last resolved coordinates into navigation:

```kotlin
// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt
data class StationListUiState(
    val currentCoordinates: Coordinates? = null,
    // existing fields...
)
```

```kotlin
// feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt
if (uiState.currentCoordinates != null) {
    onWatchlistClick(uiState.currentCoordinates)
}
```

- [ ] **Step 4: Run focused verification**

Run:

```bash
./gradlew :feature:watchlist:testDebugUnitTest --tests "*WatchlistViewModelTest"
./gradlew :app:compileDemoDebugKotlin
```

Expected:

- First command: `BUILD SUCCESSFUL`
- Second command: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add \
  settings.gradle.kts \
  app/build.gradle.kts \
  app/src/main/java/com/gasstation/navigation/GasStationDestination.kt \
  app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt \
  feature/watchlist/build.gradle.kts \
  feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist \
  feature/watchlist/src/test/kotlin/com/gasstation/feature/watchlist/WatchlistViewModelTest.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt \
  feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListUiState.kt
git commit -m "feat: add watchlist comparison feature"
```

### Task 6: Add UI verification, benchmark scenarios, and portfolio docs

**Files:**
- Create: `app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt`
- Modify: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt`
- Create: `benchmark/src/androidTest/kotlin/com/gasstation/benchmark/StationListBenchmark.kt`
- Modify: `benchmark/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/state-model.md`
- Modify: `docs/offline-strategy.md`

- [ ] **Step 1: Add a failing instrumentation test for the end-to-end portfolio flow**

```kotlin
package com.gasstation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class StationPortfolioFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun demoFlow_can_watch_station_and_open_watchlist() {
        rule.onNodeWithContentDescription("관심 주유소 토글").performClick()
        rule.onNodeWithText("관심 비교").performClick()
        rule.onNodeWithText("Gangnam First").assertExists()
    }
}
```

- [ ] **Step 2: Run the app instrumentation test and verify it fails**

Run:

```bash
./gradlew :app:connectedDemoDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gasstation.StationPortfolioFlowTest
```

Expected: `BUILD FAILED` or test failure because the watchlist UI and test tags/content descriptions are not fully connected yet.

- [ ] **Step 3: Extend benchmark coverage and document the results**

Create a macrobenchmark for cold start and watchlist navigation:

```kotlin
package com.gasstation.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationListBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartAndOpenWatchlist() = benchmarkRule.measureRepeated(
        packageName = "com.gasstation.demo",
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 5,
    ) {
        pressHome()
        startActivityAndWait()
        device.findObject(androidx.test.uiautomator.By.desc("관심 비교")).click()
    }
}
```

Update `BaselineProfileGenerator.kt` to click both `새로고침` and `관심 비교` during collection so the new code path is warmed.

Update `README.md` with these sections:

```markdown
## 핵심 기능

- 현재 위치 기준 주유소 탐색
- stale 캐시 유지와 새로고침 실패 복구
- 가격 변동 배지와 관심 주유소 비교
- 외부 지도 연동

## 검증

- 단위 테스트: 가격 변화 계산, watchlist 상태 전이, cache 정책
- UI 테스트: 관심 등록 후 비교 화면 진입
- Macrobenchmark: cold start, 리스트 진입, 비교 화면 이동
```

Also update `docs/architecture.md`, `docs/state-model.md`, and `docs/offline-strategy.md` so they mention `feature:watchlist`, `StationPriceHistory`, `WatchedStation`, and the 10-row trim policy.

- [ ] **Step 4: Run the full verification matrix**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home PATH="$JAVA_HOME/bin:$PATH" \
./gradlew \
  :core:database:testDebugUnitTest \
  :domain:station:test \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:assembleDemoDebug \
  :app:connectedDemoDebugAndroidTest \
  :benchmark:assemble
```

Expected: all listed tasks succeed; if emulator/device is unavailable, `:app:connectedDemoDebugAndroidTest` is the only acceptable skipped item and must be called out in the PR summary.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/androidTest/java/com/gasstation/StationPortfolioFlowTest.kt \
  benchmark/build.gradle.kts \
  benchmark/src/androidTest/kotlin/com/gasstation/benchmark/BaselineProfileGenerator.kt \
  benchmark/src/androidTest/kotlin/com/gasstation/benchmark/StationListBenchmark.kt \
  README.md \
  docs/architecture.md \
  docs/state-model.md \
  docs/offline-strategy.md
git commit -m "docs: finish portfolio validation and presentation"
```

## Self-Review

### Spec Coverage
- 대표 기능: Task 3, Task 4, Task 5
- 데이터 구조와 상태 모델: Task 1, Task 2, Task 3
- 테스트/성능/분석: Task 4, Task 6
- README/문서/포트폴리오 노출: Task 6

### Placeholder Scan
- `TBD`, `TODO`, "적절히 처리" 같은 표현 없음
- 각 작업에 실행 명령과 기대 결과 포함

### Type Consistency
- 선행 경계 정리: `AppStartupHook`, `AppStartupRunner`, `ExternalMapModule`
- 저장 모델: `StationPriceHistoryEntity`, `WatchedStationEntity`
- 도메인 읽기 모델: `StationListEntry`, `WatchedStationSummary`, `StationPriceDelta`
- feature 모듈 이름은 `feature:watchlist`로 고정
