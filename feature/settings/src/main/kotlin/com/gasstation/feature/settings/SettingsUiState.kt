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

private fun SearchRadius.toLabel(): String = when (this) {
    SearchRadius.KM_3 -> "3km"
    SearchRadius.KM_4 -> "4km"
    SearchRadius.KM_5 -> "5km"
}

private fun SearchRadius.toDescription(): String = when (this) {
    SearchRadius.KM_3 -> "가장 촘촘하게 주변 가격을 비교합니다."
    SearchRadius.KM_4 -> "도심과 외곽 사이의 균형을 맞춥니다."
    SearchRadius.KM_5 -> "더 넓은 범위에서 최저가를 찾습니다."
}

private fun FuelType.toLabel(): String = when (this) {
    FuelType.GASOLINE -> "휘발유"
    FuelType.DIESEL -> "경유"
    FuelType.PREMIUM_GASOLINE -> "고급휘발유"
    FuelType.KEROSENE -> "등유"
    FuelType.LPG -> "LPG"
}

private fun FuelType.toDescription(): String = when (this) {
    FuelType.GASOLINE -> "일반 승용차 기준으로 가장 넓게 비교합니다."
    FuelType.DIESEL -> "디젤 차량 기준 가격만 빠르게 추립니다."
    FuelType.PREMIUM_GASOLINE -> "고급휘발유 취급 주유소만 우선 보여줍니다."
    FuelType.KEROSENE -> "등유 가격 비교가 필요한 주유소를 찾습니다."
    FuelType.LPG -> "LPG 충전 가격을 중심으로 목록을 맞춥니다."
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

private fun BrandFilter.toDescription(): String = when (this) {
    BrandFilter.ALL -> "브랜드 제한 없이 가까운 가격을 한 번에 확인합니다."
    BrandFilter.SKE -> "SK에너지 주유소만 골라 비교합니다."
    BrandFilter.GSC -> "GS칼텍스 주유소만 골라 비교합니다."
    BrandFilter.HDO -> "현대오일뱅크 주유소만 골라 비교합니다."
    BrandFilter.SOL -> "S-OIL 주유소만 골라 비교합니다."
    BrandFilter.RTO -> "자영알뜰 주유소만 골라 비교합니다."
    BrandFilter.RTX -> "고속도로알뜰 주유소만 골라 비교합니다."
    BrandFilter.NHO -> "농협알뜰 주유소만 골라 비교합니다."
    BrandFilter.ETC -> "자가상표 주유소만 골라 비교합니다."
    BrandFilter.E1G -> "E1 충전소만 골라 비교합니다."
    BrandFilter.SKG -> "SK가스 충전소만 골라 비교합니다."
}

private fun SortOrder.toLabel(): String = when (this) {
    SortOrder.DISTANCE -> "거리순 보기"
    SortOrder.PRICE -> "가격순 보기"
}

private fun SortOrder.toDescription(): String = when (this) {
    SortOrder.DISTANCE -> "가장 가까운 주유소부터 차례대로 읽습니다."
    SortOrder.PRICE -> "가장 저렴한 가격부터 빠르게 스캔합니다."
}

private fun MapProvider.toLabel(): String = when (this) {
    MapProvider.TMAP -> "티맵"
    MapProvider.KAKAO_NAVI -> "카카오네비"
    MapProvider.NAVER_MAP -> "네이버지도"
}

private fun MapProvider.toDescription(): String = when (this) {
    MapProvider.TMAP -> "티맵으로 바로 길찾기를 시작합니다."
    MapProvider.KAKAO_NAVI -> "카카오네비로 바로 길찾기를 시작합니다."
    MapProvider.NAVER_MAP -> "네이버지도로 바로 길찾기를 시작합니다."
}

private fun Any.selectedMeta(isSelected: Boolean): String? = if (isSelected) "현재 선택" else null
