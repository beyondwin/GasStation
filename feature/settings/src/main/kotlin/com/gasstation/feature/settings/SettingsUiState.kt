package com.gasstation.feature.settings

import com.gasstation.core.designsystem.gasStationBrandFilterLabel
import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.BrandFilter
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.MapProvider
import com.gasstation.core.model.SearchRadius
import com.gasstation.core.model.SortOrder
import com.gasstation.domain.settings.model.UserPreferences

data class SettingsUiState(
    val searchRadius: SearchRadius,
    val fuelType: FuelType,
    val brandFilter: BrandFilter,
    val sortOrder: SortOrder,
    val mapProvider: MapProvider,
) {
    companion object {
        fun from(preferences: UserPreferences) = SettingsUiState(
            searchRadius = preferences.searchRadius,
            fuelType = preferences.fuelType,
            brandFilter = preferences.brandFilter,
            sortOrder = preferences.sortOrder,
            mapProvider = preferences.mapProvider,
        )
    }
}

fun SettingsUiState.selectedLabelFor(section: SettingsSection): StringResource = when (section) {
    SettingsSection.SearchRadius -> searchRadius.toLabel()
    SettingsSection.FuelType -> fuelType.toLabel()
    SettingsSection.BrandFilter -> brandFilter.toLabel()
    SettingsSection.SortOrder -> sortOrder.toLabel()
    SettingsSection.MapProvider -> mapProvider.toLabel()
}

fun SettingsUiState.optionsFor(section: SettingsSection): List<SettingOptionUiModel> = when (section) {
    SettingsSection.SearchRadius -> SearchRadius.entries.map { option ->
        SettingOptionUiModel(
            label = option.toLabel(),
            subtitle = option.toDescription(),
            meta = option.selectedMeta(searchRadius == option),
            action = SettingsAction.SearchRadiusSelected(option),
            isSelected = searchRadius == option,
        )
    }

    SettingsSection.FuelType -> FuelType.entries.map { option ->
        SettingOptionUiModel(
            label = option.toLabel(),
            subtitle = option.toDescription(),
            meta = option.selectedMeta(fuelType == option),
            action = SettingsAction.FuelTypeSelected(option),
            isSelected = fuelType == option,
        )
    }

    SettingsSection.BrandFilter -> BrandFilter.entries.map { option ->
        SettingOptionUiModel(
            label = option.toLabel(),
            subtitle = option.toDescription(),
            meta = option.selectedMeta(brandFilter == option),
            action = SettingsAction.BrandFilterSelected(option),
            isSelected = brandFilter == option,
            brandIconBrand = option.brand,
        )
    }

    SettingsSection.SortOrder -> SortOrder.entries.map { option ->
        SettingOptionUiModel(
            label = option.toLabel(),
            subtitle = option.toDescription(),
            meta = option.selectedMeta(sortOrder == option),
            action = SettingsAction.SortOrderSelected(option),
            isSelected = sortOrder == option,
        )
    }

    SettingsSection.MapProvider -> MapProvider.entries.map { option ->
        SettingOptionUiModel(
            label = option.toLabel(),
            subtitle = option.toDescription(),
            meta = option.selectedMeta(mapProvider == option),
            action = SettingsAction.MapProviderSelected(option),
            isSelected = mapProvider == option,
        )
    }
}

internal fun SearchRadius.toLabel(): StringResource = when (this) {
    SearchRadius.KM_3 -> StringResource.raw("3km")
    SearchRadius.KM_4 -> StringResource.raw("4km")
    SearchRadius.KM_5 -> StringResource.raw("5km")
}

private fun SearchRadius.toDescription(): StringResource = when (this) {
    SearchRadius.KM_3 -> StringResource.fromId(R.string.settings_radius_km3_desc)
    SearchRadius.KM_4 -> StringResource.fromId(R.string.settings_radius_km4_desc)
    SearchRadius.KM_5 -> StringResource.fromId(R.string.settings_radius_km5_desc)
}

internal fun FuelType.toLabel(): StringResource = when (this) {
    FuelType.GASOLINE -> StringResource.fromId(R.string.settings_fuel_type_gasoline)
    FuelType.DIESEL -> StringResource.fromId(R.string.settings_fuel_type_diesel)
    FuelType.PREMIUM_GASOLINE -> StringResource.fromId(R.string.settings_fuel_type_premium_gasoline)
    FuelType.KEROSENE -> StringResource.fromId(R.string.settings_fuel_type_kerosene)
    FuelType.LPG -> StringResource.raw("LPG")
}

private fun FuelType.toDescription(): StringResource = when (this) {
    FuelType.GASOLINE -> StringResource.fromId(R.string.settings_fuel_type_gasoline_desc)
    FuelType.DIESEL -> StringResource.fromId(R.string.settings_fuel_type_diesel_desc)
    FuelType.PREMIUM_GASOLINE -> StringResource.fromId(R.string.settings_fuel_type_premium_gasoline_desc)
    FuelType.KEROSENE -> StringResource.fromId(R.string.settings_fuel_type_kerosene_desc)
    FuelType.LPG -> StringResource.fromId(R.string.settings_fuel_type_lpg_desc)
}

internal fun BrandFilter.toLabel(): StringResource = StringResource.raw(gasStationBrandFilterLabel())

private fun BrandFilter.toDescription(): StringResource = when (this) {
    BrandFilter.ALL -> StringResource.fromId(R.string.settings_brand_all_desc)

    BrandFilter.SKE,
    BrandFilter.GSC,
    BrandFilter.HDO,
    BrandFilter.SOL,
    BrandFilter.RTO,
    BrandFilter.RTX,
    BrandFilter.NHO,
    BrandFilter.ETC,
    -> StringResource.fromId(R.string.settings_brand_station_filter_desc, listOf(gasStationBrandFilterLabel()))

    BrandFilter.E1G,
    BrandFilter.SKG,
    -> StringResource.fromId(R.string.settings_brand_charger_filter_desc, listOf(gasStationBrandFilterLabel()))
}

internal fun SortOrder.toLabel(): StringResource = when (this) {
    SortOrder.DISTANCE -> StringResource.fromId(R.string.settings_sort_distance_label)
    SortOrder.PRICE -> StringResource.fromId(R.string.settings_sort_price_label)
}

private fun SortOrder.toDescription(): StringResource = when (this) {
    SortOrder.DISTANCE -> StringResource.fromId(R.string.settings_sort_distance_desc)
    SortOrder.PRICE -> StringResource.fromId(R.string.settings_sort_price_desc)
}

internal fun MapProvider.toLabel(): StringResource = when (this) {
    MapProvider.TMAP -> StringResource.fromId(R.string.settings_map_tmap)
    MapProvider.KAKAO_NAVI -> StringResource.fromId(R.string.settings_map_kakao)
    MapProvider.NAVER_MAP -> StringResource.fromId(R.string.settings_map_naver)
}

private fun MapProvider.toDescription(): StringResource = when (this) {
    MapProvider.TMAP -> StringResource.fromId(R.string.settings_map_tmap_desc)
    MapProvider.KAKAO_NAVI -> StringResource.fromId(R.string.settings_map_kakao_desc)
    MapProvider.NAVER_MAP -> StringResource.fromId(R.string.settings_map_naver_desc)
}

private fun Any.selectedMeta(isSelected: Boolean): StringResource? =
    if (isSelected) StringResource.fromId(R.string.settings_selected_meta) else null
