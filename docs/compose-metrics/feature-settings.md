# Compose Stability Metrics — feature:settings

> 측정 환경: AGP 9.1.1 / Kotlin 2.3.20 / Compose Compiler 2.3.20
> 생성 명령: `./gradlew :feature:settings:assembleDebug`
> 측정일: 2026-05-11

## Classes

```
unstable class com.gasstation.feature.settings.SettingOptionUiModel {
  unstable val label: StringResource
  unstable val subtitle: StringResource?
  unstable val meta: StringResource?
  runtime val action: SettingsAction
  stable val isSelected: Boolean
  stable val brandIconBrand: Brand?
  <runtime stability> = Unstable
}
stable class com.gasstation.feature.settings.SettingsAction.SortOrderSelected {
  stable val sortOrder: SortOrder
  <runtime stability> = Stable
}
stable class com.gasstation.feature.settings.SettingsAction.FuelTypeSelected {
  stable val fuelType: FuelType
  <runtime stability> = Stable
}
stable class com.gasstation.feature.settings.SettingsAction.SearchRadiusSelected {
  stable val radius: SearchRadius
  <runtime stability> = Stable
}
stable class com.gasstation.feature.settings.SettingsAction.BrandFilterSelected {
  stable val brandFilter: BrandFilter
  <runtime stability> = Stable
}
stable class com.gasstation.feature.settings.SettingsAction.MapProviderSelected {
  stable val mapProvider: MapProvider
  <runtime stability> = Stable
}
stable class com.gasstation.feature.settings.SettingsUiState {
  stable val searchRadius: SearchRadius
  stable val fuelType: FuelType
  stable val brandFilter: BrandFilter
  stable val sortOrder: SortOrder
  stable val mapProvider: MapProvider
  <runtime stability> = Stable
}
unstable class com.gasstation.feature.settings.SettingsViewModel {
  unstable val updatePreferredSortOrder: UpdatePreferredSortOrderUseCase
  unstable val updateFuelType: UpdateFuelTypeUseCase
  unstable val updateSearchRadius: UpdateSearchRadiusUseCase
  unstable val updateBrandFilter: UpdateBrandFilterUseCase
  unstable val updateMapProvider: UpdateMapProviderUseCase
  unstable val mutableUiState: MutableStateFlow<SettingsUiState>
  unstable val uiState: StateFlow<SettingsUiState>
  <runtime stability> = Unstable
}
```

## Composables

```
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsDetailRoute(
  stable section: SettingsSection
  stable onBackClick: Function0<Unit>
  unstable viewModelStoreOwner: ViewModelStoreOwner
  unstable viewModel: SettingsViewModel? = @dynamic <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsDetailScreen(
  stable section: SettingsSection
  unstable options: List<SettingOptionUiModel>
  stable onBackClick: Function0<Unit>
  stable onOptionClick: Function1<SettingOptionUiModel, Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsDetailOptionRow(
  stable section: SettingsSection
  unstable option: SettingOptionUiModel
  stable onClick: Function0<Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsDetailBrandLeadingSlot(
  unstable option: SettingOptionUiModel
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.AllBrandFilterIcon()
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsDetailDivider()
restartable skippable scheme("[androidx.compose.ui.UiComposable, [androidx.compose.ui.UiComposable]]") fun com.gasstation.feature.settings.SettingsDetailTopBarAction(
  stable contentDescription: String
  stable onClick: Function0<Unit>
  stable icon: Function2<Composer, Int, Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.LegacyBackIcon()
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SelectedCheckIcon()
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsRoute(
  stable onCloseClick: Function0<Unit>
  stable onSectionClick: Function1<SettingsSection, Unit>
  unstable viewModel: SettingsViewModel? = @dynamic <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsScreen(
  stable uiState: SettingsUiState
  stable onCloseClick: Function0<Unit>
  stable onSectionClick: Function1<SettingsSection, Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsSectionGroupBlock(
  stable group: SettingsSectionGroup
  unstable sections: List<SettingsSection>
  stable uiState: SettingsUiState
  stable onSectionClick: Function1<SettingsSection, Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsGroupHeader(
  stable group: SettingsSectionGroup
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsMenuRow(
  stable section: SettingsSection
  unstable selectedLabel: StringResource
  stable onClick: Function0<Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.SettingsMenuDivider()
restartable skippable scheme("[androidx.compose.ui.UiComposable, [androidx.compose.ui.UiComposable]]") fun com.gasstation.feature.settings.SettingsTopBarAction(
  stable contentDescription: String
  stable onClick: Function0<Unit>
  stable icon: Function2<Composer, Int, Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.LegacyCloseIcon()
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.settings.LegacyChevronIcon()
```

## Unstable 분류 대응

- `SettingsUiState`: 모든 필드가 stable enum/data class — 현재 stable 상태 유지. `SettingsScreen` composable이 안정적으로 skip 가능합니다.
- `SettingOptionUiModel`: `StringResource` sealed interface 필드(`label`, `subtitle`, `meta`) 때문에 unstable로 분류됩니다. `StringResource.FromId`의 `List<Any>` args 필드가 원인입니다. 이 타입은 설정 목록 렌더링 시 옵션 변경이 없는 한 재사용되며, 선택 상태(`isSelected`) 변경 시에만 재구성이 필요합니다. 이 baseline 단계에서는 kept as-is로 유지합니다.
- `SettingsViewModel`: ViewModel 클래스이며 Compose 트리에 직접 전달되지 않음 — kept as-is.
