# Compose Stability Metrics — feature:watchlist

> 측정 환경: AGP 9.1.1 / Kotlin 2.3.20 / Compose Compiler 2.3.20
> 생성 명령: `./gradlew :feature:watchlist:assembleDebug`
> 측정일: 2026-05-11

## Classes

```
stable class com.gasstation.feature.watchlist.WatchlistItemUiModel {
  stable val id: String
  stable val name: String
  stable val brand: Brand
  stable val brandLabel: String
  stable val priceLabel: String
  stable val priceNumberLabel: String
  stable val priceUnitLabel: String
  stable val distanceLabel: String
  stable val distanceNumberLabel: String
  stable val distanceUnitLabel: String
  stable val priceDeltaLabel: String
  stable val priceDeltaTone: WatchlistPriceDeltaTone
  stable val lastSeenLabel: String
  stable val latitude: Double
  stable val longitude: Double
  <runtime stability> = Stable
}
unstable class com.gasstation.feature.watchlist.WatchlistUiState {
  unstable val stations: List<WatchlistItemUiModel>
  <runtime stability> = Unstable
}
unstable class com.gasstation.feature.watchlist.WatchlistViewModel {
  unstable val stationEventLogger: StationEventLogger
  unstable val origin: Coordinates
  stable var hasLoggedCompareViewed: Boolean
  unstable val uiState: StateFlow<WatchlistUiState>
  <runtime stability> = Unstable
}
```

## Composables

```
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.watchlist.WatchlistRoute(
  stable onCloseClick: Function0<Unit>
  unstable viewModel: WatchlistViewModel? = @dynamic <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.watchlist.WatchlistScreen(
  unstable uiState: WatchlistUiState
  stable onCloseClick: Function0<Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable, [androidx.compose.ui.UiComposable]]") fun com.gasstation.feature.watchlist.WatchlistTopBarAction(
  stable contentDescription: String
  stable onClick: Function0<Unit>
  stable icon: Function2<Composer, Int, Unit>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.watchlist.WatchlistCloseIcon()
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.watchlist.WatchlistDeltaIndicator(
  stable label: String
  stable tone: WatchlistPriceDeltaTone
  stable modifier: Modifier? = @static <expression>
)
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun com.gasstation.feature.watchlist.EmptyWatchlist(
  stable modifier: Modifier? = @static <expression>
)
```

## Unstable 분류 대응

- `WatchlistItemUiModel`: 모든 필드가 stable — 현재 stable 상태 유지, 추가 조치 불필요.
- `WatchlistUiState`: `List<WatchlistItemUiModel>` 필드 때문에 unstable로 분류됩니다. `WatchlistItemUiModel` 자체는 stable이지만 `List<T>`는 Compose 컴파일러가 불변성을 보장할 수 없습니다. ViewModel의 `StateFlow`에서 방출되어 최상위 composable에만 전달되므로 실질적인 불필요 recomposition 위험이 낮습니다 — kept as-is.
- `WatchlistViewModel`: ViewModel 클래스이며 Compose 트리에 직접 전달되지 않음 — kept as-is.
