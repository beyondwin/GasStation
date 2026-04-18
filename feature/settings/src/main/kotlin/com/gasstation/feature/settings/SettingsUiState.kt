package com.gasstation.feature.settings

import com.gasstation.domain.settings.model.UserPreferences
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder

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

fun SettingsUiState.selectedLabelFor(section: SettingsSection): String = when (section) {
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
            action = SettingsAction.SearchRadiusSelected(option),
            isSelected = searchRadius == option,
        )
    }
    SettingsSection.FuelType -> FuelType.entries.map { option ->
        SettingOptionUiModel(
            label = option.toLabel(),
            action = SettingsAction.FuelTypeSelected(option),
            isSelected = fuelType == option,
        )
    }
    SettingsSection.BrandFilter -> BrandFilter.entries.map { option ->
        SettingOptionUiModel(
            label = option.toLabel(),
            action = SettingsAction.BrandFilterSelected(option),
            isSelected = brandFilter == option,
        )
    }
    SettingsSection.SortOrder -> SortOrder.entries.map { option ->
        SettingOptionUiModel(
            label = option.toLabel(),
            action = SettingsAction.SortOrderSelected(option),
            isSelected = sortOrder == option,
        )
    }
    SettingsSection.MapProvider -> MapProvider.entries.map { option ->
        SettingOptionUiModel(
            label = option.toLabel(),
            action = SettingsAction.MapProviderSelected(option),
            isSelected = mapProvider == option,
        )
    }
}

private fun SearchRadius.toLabel(): String = when (this) {
    SearchRadius.KM_3 -> "3km"
    SearchRadius.KM_4 -> "4km"
    SearchRadius.KM_5 -> "5km"
}

private fun FuelType.toLabel(): String = when (this) {
    FuelType.GASOLINE -> "휘발유"
    FuelType.DIESEL -> "경유"
    FuelType.PREMIUM_GASOLINE -> "고급휘발유"
    FuelType.KEROSENE -> "등유"
    FuelType.LPG -> "LPG"
}

private fun BrandFilter.toLabel(): String = when (this) {
    BrandFilter.ALL -> "전체"
    BrandFilter.SKE -> "SK에너지"
    BrandFilter.GSC -> "GS칼텍스"
    BrandFilter.HDO -> "현대오일뱅크"
    BrandFilter.SOL -> "S-OIL"
    BrandFilter.RTO -> "자영알뜰"
    BrandFilter.RTX -> "고속도로알뜰"
    BrandFilter.NHO -> "농협알뜰"
    BrandFilter.ETC -> "자가상표"
    BrandFilter.E1G -> "E1"
    BrandFilter.SKG -> "SK가스"
}

private fun SortOrder.toLabel(): String = when (this) {
    SortOrder.DISTANCE -> "거리순 보기"
    SortOrder.PRICE -> "가격순 보기"
}

private fun MapProvider.toLabel(): String = when (this) {
    MapProvider.TMAP -> "티맵"
    MapProvider.KAKAO_NAVI -> "카카오네비"
    MapProvider.NAVER_MAP -> "네이버지도"
}
