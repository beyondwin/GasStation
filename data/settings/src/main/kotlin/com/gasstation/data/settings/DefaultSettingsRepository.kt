package com.gasstation.data.settings

import com.gasstation.core.datastore.StoredUserPreferences
import com.gasstation.core.datastore.UserPreferencesDataSource
import com.gasstation.core.model.BrandFilter
import com.gasstation.domain.settings.SettingsRepository
import com.gasstation.domain.settings.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultSettingsRepository @Inject constructor(private val dataSource: UserPreferencesDataSource) : SettingsRepository {
    override fun observeUserPreferences(): Flow<UserPreferences> = dataSource.userPreferences.map(StoredUserPreferences::toDomain)

    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences): UserPreferences =
        dataSource.update { current ->
            transform(current.toDomain()).toStored()
        }.toDomain()
}

private fun StoredUserPreferences.toDomain(): UserPreferences {
    val defaults = UserPreferences.default()
    return UserPreferences(
        searchRadius = enumOrDefault(searchRadiusName, defaults.searchRadius),
        fuelType = enumOrDefault(fuelTypeName, defaults.fuelType),
        brandFilter = parseBrandFilter(brandFilterName),
        sortOrder = enumOrDefault(sortOrderName, defaults.sortOrder),
        mapProvider = enumOrDefault(mapProviderName, defaults.mapProvider),
    )
}

private fun UserPreferences.toStored(): StoredUserPreferences = StoredUserPreferences(
    searchRadiusName = searchRadius.name,
    fuelTypeName = fuelType.name,
    brandFilterName = brandFilter.name,
    sortOrderName = sortOrder.name,
    mapProviderName = mapProvider.name,
)

private fun parseBrandFilter(value: String): BrandFilter = when (value) {
    "RTO", "RTX", "NHO" -> BrandFilter.ALTEUL
    else -> enumOrDefault(value, BrandFilter.ALL)
}

private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(default)
