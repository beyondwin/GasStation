package com.gasstation.core.datastore

import androidx.datastore.core.DataStoreFactory
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.core.model.MapProvider
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
