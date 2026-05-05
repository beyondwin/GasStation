package com.gasstation.data.settings

import com.gasstation.core.datastore.StoredUserPreferences
import com.gasstation.core.datastore.UserPreferencesDataSource
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSettingsRepositoryTest {

    @Test
    fun `observeUserPreferences returns the current data source value`() = runBlocking {
        val expected = UserPreferences.default().copy(sortOrder = SortOrder.PRICE)
        val repository = DefaultSettingsRepository(
            dataSource = InMemoryUserPreferencesDataSource(
                StoredUserPreferences.Default.copy(sortOrderName = "PRICE"),
            ),
        )

        assertEquals(expected, repository.observeUserPreferences().first())
    }

    @Test
    fun `updateUserPreferences persists transformed preferences`() = runBlocking {
        val repository = DefaultSettingsRepository(
            dataSource = InMemoryUserPreferencesDataSource(StoredUserPreferences.Default),
        )

        repository.updateUserPreferences { current ->
            current.copy(sortOrder = SortOrder.PRICE)
        }

        assertEquals(
            SortOrder.PRICE,
            repository.observeUserPreferences().first().sortOrder,
        )
    }

    @Test
    fun `repository maps invalid stored enum names to domain defaults`() = runBlocking {
        val repository = DefaultSettingsRepository(
            dataSource = InMemoryUserPreferencesDataSource(
                StoredUserPreferences(
                    searchRadiusName = "UNKNOWN_RADIUS",
                    fuelTypeName = "UNKNOWN_FUEL",
                    brandFilterName = "UNKNOWN_BRAND",
                    sortOrderName = "UNKNOWN_SORT",
                    mapProviderName = "UNKNOWN_MAP",
                ),
            ),
        )

        assertEquals(
            UserPreferences.default(),
            repository.observeUserPreferences().first(),
        )
    }

    @Test
    fun `updateUserPreferences re-emits to an active collector`() = runBlocking {
        val repository = DefaultSettingsRepository(
            dataSource = InMemoryUserPreferencesDataSource(StoredUserPreferences.Default),
        )
        val emissions = mutableListOf<UserPreferences>()

        val collectJob = launch {
            repository.observeUserPreferences()
                .take(2)
                .toList(emissions)
        }
        yield()

        repository.updateUserPreferences { current ->
            current.copy(sortOrder = SortOrder.PRICE)
        }
        collectJob.join()

        assertEquals(
            listOf(
                UserPreferences.default(),
                UserPreferences.default().copy(sortOrder = SortOrder.PRICE),
            ),
            emissions,
        )
    }
}

private class InMemoryUserPreferencesDataSource(
    initial: StoredUserPreferences,
) : UserPreferencesDataSource {
    private val state = MutableStateFlow(initial)

    override val userPreferences: Flow<StoredUserPreferences> = state

    override suspend fun update(transform: (StoredUserPreferences) -> StoredUserPreferences) {
        state.value = transform(state.value)
    }
}
