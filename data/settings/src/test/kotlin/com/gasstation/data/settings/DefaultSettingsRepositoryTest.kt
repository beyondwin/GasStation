package com.gasstation.data.settings

import com.gasstation.core.datastore.UserPreferencesDataSource
import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.core.model.SortOrder
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSettingsRepositoryTest {

    @Test
    fun `observeUserPreferences returns the current data source value`() = runBlocking {
        val expected = UserPreferences.default().copy(sortOrder = SortOrder.PRICE)
        val repository = DefaultSettingsRepository(
            dataSource = InMemoryUserPreferencesDataSource(expected),
        )

        assertEquals(expected, repository.observeUserPreferences().first())
    }

    @Test
    fun `updateUserPreferences persists transformed preferences`() = runBlocking {
        val repository = DefaultSettingsRepository(
            dataSource = InMemoryUserPreferencesDataSource(UserPreferences.default()),
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
    fun `updateUserPreferences re-emits to an active collector`() = runBlocking {
        val repository = DefaultSettingsRepository(
            dataSource = InMemoryUserPreferencesDataSource(UserPreferences.default()),
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
    initial: UserPreferences,
) : UserPreferencesDataSource {
    private val state = MutableStateFlow(initial)

    override val userPreferences: Flow<UserPreferences> = state

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }
}
