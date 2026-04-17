package com.gasstation.core.datastore

import android.content.SharedPreferences
import androidx.datastore.core.DataStoreFactory
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AndroidUserPreferencesDataSourceTest {

    @Test
    fun `empty datastore emits default preferences`() = runBlocking {
        val file = createTempStoreFile()
        val scope = testScope()
        val dataSource = AndroidUserPreferencesDataSource(
            dataStore = DataStoreFactory.create(
                serializer = UserPreferencesSerializer,
                scope = scope.scope,
                produceFile = { file },
            ),
        )

        assertEquals(UserPreferences.default(), dataSource.userPreferences.first())

        scope.job.cancelAndJoin()
    }

    @Test
    fun `updates are persisted across datastore instances`() = runBlocking {
        val file = createTempStoreFile()
        val firstScope = testScope()
        val firstDataSource = AndroidUserPreferencesDataSource(
            dataStore = DataStoreFactory.create(
                serializer = UserPreferencesSerializer,
                scope = firstScope.scope,
                produceFile = { file },
            ),
        )

        firstDataSource.update { current ->
            current.copy(mapProvider = MapProvider.NAVER_MAP)
        }
        firstScope.job.cancelAndJoin()

        val secondScope = testScope()
        val secondDataSource = AndroidUserPreferencesDataSource(
            dataStore = DataStoreFactory.create(
                serializer = UserPreferencesSerializer,
                scope = secondScope.scope,
                produceFile = { file },
            ),
        )

        assertEquals(
            MapProvider.NAVER_MAP,
            secondDataSource.userPreferences.first().mapProvider,
        )

        secondScope.job.cancelAndJoin()
    }

    @Test
    fun `legacy shared preferences are migrated into datastore on first read without deleting legacy values`() = runBlocking {
        val file = createTempStoreFile()
        val scope = testScope()
        val legacyPreferences = FakeSharedPreferences(
            mutableMapOf(
                LegacyUserPreferencesMigration.KEY_DISTANCE_TYPE to "4km",
                LegacyUserPreferencesMigration.KEY_OIL_TYPE to "경유",
                LegacyUserPreferencesMigration.KEY_GAS_STATION_TYPE to "GS칼텍스",
                LegacyUserPreferencesMigration.KEY_SORT_TYPE to "가격순 보기",
                LegacyUserPreferencesMigration.KEY_MAP_TYPE to "네이버지도",
            ),
        )
        val dataSource = AndroidUserPreferencesDataSource(
            dataStore = DataStoreFactory.create(
                serializer = UserPreferencesSerializer,
                migrations = listOf(LegacyUserPreferencesMigration(legacyPreferences)),
                scope = scope.scope,
                produceFile = { file },
            ),
        )

        assertEquals(
            UserPreferences.default().copy(
                searchRadius = SearchRadius.KM_4,
                fuelType = FuelType.DIESEL,
                brandFilter = BrandFilter.GSC,
                sortOrder = com.gasstation.domain.station.model.SortOrder.PRICE,
                mapProvider = MapProvider.NAVER_MAP,
            ),
            dataSource.userPreferences.first(),
        )
        assertEquals(true, legacyPreferences.contains(LegacyUserPreferencesMigration.KEY_DISTANCE_TYPE))
        assertEquals(true, legacyPreferences.contains(LegacyUserPreferencesMigration.KEY_OIL_TYPE))
        assertEquals(true, legacyPreferences.contains(LegacyUserPreferencesMigration.KEY_GAS_STATION_TYPE))
        assertEquals(true, legacyPreferences.contains(LegacyUserPreferencesMigration.KEY_SORT_TYPE))
        assertEquals(true, legacyPreferences.contains(LegacyUserPreferencesMigration.KEY_MAP_TYPE))

        scope.job.cancelAndJoin()
    }

    @Test
    fun `legacy special brand labels map to the expected typed filters`() = runBlocking {
        val cases = listOf(
            "자영알뜰" to BrandFilter.RTO,
            "고속도로알뜰" to BrandFilter.RTX,
            "농협알뜰" to BrandFilter.NHO,
            "자가상표" to BrandFilter.ETC,
        )

        cases.forEach { (legacyBrand, expectedFilter) ->
            val file = createTempStoreFile()
            val scope = testScope()
            val legacyPreferences = FakeSharedPreferences(
                mutableMapOf(
                    LegacyUserPreferencesMigration.KEY_GAS_STATION_TYPE to legacyBrand,
                ),
            )
            val dataSource = AndroidUserPreferencesDataSource(
                dataStore = DataStoreFactory.create(
                    serializer = UserPreferencesSerializer,
                    migrations = listOf(LegacyUserPreferencesMigration(legacyPreferences)),
                    scope = scope.scope,
                    produceFile = { file },
                ),
            )

            assertEquals(expectedFilter, dataSource.userPreferences.first().brandFilter)

            scope.job.cancelAndJoin()
        }
    }

    private fun createTempStoreFile(): File =
        Files.createTempFile("user-preferences", ".preferences_pb").toFile().apply {
            deleteOnExit()
            delete()
        }

    private fun testScope(): TestScopeHandle {
        val job = SupervisorJob()
        return TestScopeHandle(
            scope = CoroutineScope(job + Dispatchers.IO),
            job = job,
        )
    }

    private data class TestScopeHandle(
        val scope: CoroutineScope,
        val job: Job,
    )
}

private class FakeSharedPreferences(
    private val values: MutableMap<String, String>,
) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        values[key] ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String?, defValue: Int): Int = defValue

    override fun getLong(key: String?, defValue: Long): Long = defValue

    override fun getFloat(key: String?, defValue: Float): Float = defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(
        private val values: MutableMap<String, String>,
    ) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null && value != null) {
                values[key] = value
            }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) {
                values.remove(key)
            }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            values.clear()
            return this
        }

        override fun commit(): Boolean = true

        override fun apply() = Unit

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
    }
}
