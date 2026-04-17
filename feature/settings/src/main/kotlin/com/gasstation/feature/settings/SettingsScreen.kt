package com.gasstation.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gasstation.domain.station.model.BrandFilter
import com.gasstation.domain.station.model.FuelType
import com.gasstation.domain.station.model.MapProvider
import com.gasstation.domain.station.model.SearchRadius
import com.gasstation.domain.station.model.SortOrder

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "찾기 설정") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsSection(
                    title = "찾기 범위",
                    options = SearchRadius.entries,
                    selected = uiState.searchRadius,
                    label = SearchRadius::toLabel,
                    onSelected = { onAction(SettingsAction.SearchRadiusSelected(it)) },
                )
            }
            item {
                SettingsSection(
                    title = "오일 타입",
                    options = FuelType.entries,
                    selected = uiState.fuelType,
                    label = FuelType::toLabel,
                    onSelected = { onAction(SettingsAction.FuelTypeSelected(it)) },
                )
            }
            item {
                SettingsSection(
                    title = "주유소 브랜드",
                    options = BrandFilter.entries,
                    selected = uiState.brandFilter,
                    label = BrandFilter::toLabel,
                    onSelected = { onAction(SettingsAction.BrandFilterSelected(it)) },
                )
            }
            item {
                SettingsSection(
                    title = "정렬 기준",
                    options = SortOrder.entries,
                    selected = uiState.sortOrder,
                    label = SortOrder::toLabel,
                    onSelected = { onAction(SettingsAction.SortOrderSelected(it)) },
                )
            }
            item {
                SettingsSection(
                    title = "연동지도 서비스",
                    options = MapProvider.entries,
                    selected = uiState.mapProvider,
                    label = MapProvider::toLabel,
                    onSelected = { onAction(SettingsAction.MapProviderSelected(it)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SettingsSection(
    title: String,
    options: Iterable<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelected(option) },
                        label = { Text(text = label(option)) },
                    )
                }
            }
        }
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
