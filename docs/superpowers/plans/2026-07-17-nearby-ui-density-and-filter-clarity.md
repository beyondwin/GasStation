# Nearby UI Density and Filter Clarity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주변 화면의 요약·필터·목록·하단 탐색을 더 빠르게 읽히도록 압축하고, 알뜰 브랜드를 하나의 사용자 선택으로 통합한다.

**Architecture:** 실제 주유소 브랜드 `Brand`는 보존하고 사용자 필터 `BrandFilter`만 다대일 매칭을 지원한다. 필터 메뉴와 가격 이력 표현은 `feature:station-list`, 브랜드 라벨/로고 선택과 공통 chrome은 `core:designsystem`, 저장값 호환성은 `data:settings`, 하단 탐색 조립은 `app` 경계 안에서 변경한다.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt, Coroutines/Flow, JUnit, Robolectric Compose UI tests, Roborazzi, Gradle Kotlin DSL.

## Global Constraints

- 시작과 각 커밋 직전에 `git status --short`를 실행하고 기존 사용자 변경인 `gradle.properties`를 수정·스테이징하지 않는다.
- `Brand`의 `RTO`, `RTX`, `NHO` 값과 원격/DB 매핑은 유지한다. 통합은 `BrandFilter.ALTEUL`에서만 수행한다.
- `BrandFilter.entries`의 표시 순서는 `ALL, SKE, GSC, HDO, SOL, ALTEUL, E1G, SKG, ETC`로 고정한다. `자가상표`는 마지막이다.
- 메뉴는 반경·유종·브랜드 모두 동일한 ivory/black/yellow 컴포넌트를 사용하며 동시에 하나만 열린다.
- 화면에서 `가격`과 하단 탭 글자를 숨겨도 가격 우선 위계, 접근성 설명, selected/disabled semantics, 기존 test tag를 보존한다.
- 한국어·영어 리소스를 같은 커밋에서 갱신한다.
- 각 작업은 RED 테스트, 최소 구현, 모듈 검증, 커밋 순서로 진행한다.
- 기준 설계 문서는 `docs/superpowers/specs/2026-07-17-nearby-ui-density-and-filter-clarity-design.md`다.

---

## Task 1: 알뜰 브랜드를 하나의 필터 계약으로 통합

**Files:**

- Modify: `core/model/src/main/kotlin/com/gasstation/core/model/BrandFilter.kt`
- Modify: `core/model/src/test/kotlin/com/gasstation/core/model/BrandFilterTest.kt`
- Modify: `core/model/src/test/kotlin/com/gasstation/core/model/SharedEnumContractTest.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/BrandLabels.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/BrandLabelsTest.kt`
- Modify: `data/settings/src/main/kotlin/com/gasstation/data/settings/DefaultSettingsRepository.kt`
- Modify: `data/settings/src/test/kotlin/com/gasstation/data/settings/DefaultSettingsRepositoryTest.kt`
- Modify: `data/station/src/test/kotlin/com/gasstation/data/station/StationSearchResultAssemblerTest.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingOptionUiModel.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsUiState.kt`
- Modify: `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsDetailScreen.kt`
- Modify: `feature/settings/src/main/res/values/strings.xml`
- Modify: `feature/settings/src/main/res/values-en/strings.xml`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsUiStateTest.kt`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/SettingsScreenTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

### Step 1: 사용자 필터 계약 테스트를 RED로 작성

- [ ] `BrandFilterTest`에 정확한 순서와 다대일 매칭 테스트를 추가한다.

```kotlin
@Test
fun `brand filter entries expose grouped alteul and private label last`() {
    assertEquals(
        listOf(
            BrandFilter.ALL,
            BrandFilter.SKE,
            BrandFilter.GSC,
            BrandFilter.HDO,
            BrandFilter.SOL,
            BrandFilter.ALTEUL,
            BrandFilter.E1G,
            BrandFilter.SKG,
            BrandFilter.ETC,
        ),
        BrandFilter.entries,
    )
}

@Test
fun `alteul matches every alteul source brand only`() {
    assertTrue(BrandFilter.ALTEUL.matches(Brand.RTO))
    assertTrue(BrandFilter.ALTEUL.matches(Brand.RTX))
    assertTrue(BrandFilter.ALTEUL.matches(Brand.NHO))
    assertFalse(BrandFilter.ALTEUL.matches(Brand.SKE))
    assertFalse(BrandFilter.ALTEUL.matches(Brand.ETC))
}
```

- [ ] `SharedEnumContractTest`의 기존 enum 목록을 새 9개 값과 순서로 바꾼다.
- [ ] `BrandLabelsTest`에 `ALTEUL`의 사용자 라벨과 대표 로고 계약을 추가한다.

```kotlin
@Test
fun `grouped alteul filter uses one label and one representative logo`() {
    assertEquals("알뜰", BrandFilter.ALTEUL.gasStationBrandFilterLabel())
    assertEquals(Brand.RTO, BrandFilter.ALTEUL.gasStationBrandFilterIconBrand())
    assertNull(BrandFilter.ALL.gasStationBrandFilterIconBrand())
}
```

- [ ] 현재 코드에서 enum 값과 새 함수를 찾지 못해 실패하는지 확인한다.

Run: `./gradlew :core:model:test :core:designsystem:testDebugUnitTest`

Expected: `BrandFilter.ALTEUL` 또는 `gasStationBrandFilterIconBrand` unresolved reference로 실패.

### Step 2: 다대일 필터와 공통 표시 계약 구현

- [ ] `BrandFilter`를 단일 nullable brand 대신 매칭 집합으로 바꾼다.

```kotlin
enum class BrandFilter(private val matchedBrands: Set<Brand>) {
    ALL(emptySet()),
    SKE(setOf(Brand.SKE)),
    GSC(setOf(Brand.GSC)),
    HDO(setOf(Brand.HDO)),
    SOL(setOf(Brand.SOL)),
    ALTEUL(setOf(Brand.RTO, Brand.RTX, Brand.NHO)),
    E1G(setOf(Brand.E1G)),
    SKG(setOf(Brand.SKG)),
    ETC(setOf(Brand.ETC)),
    ;

    fun matches(stationBrand: Brand): Boolean = this == ALL || stationBrand in matchedBrands
}
```

- [ ] `BrandLabels.kt`에 정확한 필터 라벨과 대표 로고 함수를 둔다. 실제 `Brand.gasStationBrandLabel()`은 세 알뜰 유형을 계속 구분한다.

```kotlin
fun BrandFilter.gasStationBrandFilterLabel(): String = when (this) {
    BrandFilter.ALL -> "전체"
    BrandFilter.SKE -> Brand.SKE.gasStationBrandLabel()
    BrandFilter.GSC -> Brand.GSC.gasStationBrandLabel()
    BrandFilter.HDO -> Brand.HDO.gasStationBrandLabel()
    BrandFilter.SOL -> Brand.SOL.gasStationBrandLabel()
    BrandFilter.ALTEUL -> "알뜰"
    BrandFilter.E1G -> Brand.E1G.gasStationBrandLabel()
    BrandFilter.SKG -> Brand.SKG.gasStationBrandLabel()
    BrandFilter.ETC -> Brand.ETC.gasStationBrandLabel()
}

fun BrandFilter.gasStationBrandFilterIconBrand(): Brand? = when (this) {
    BrandFilter.ALL -> null
    BrandFilter.SKE -> Brand.SKE
    BrandFilter.GSC -> Brand.GSC
    BrandFilter.HDO -> Brand.HDO
    BrandFilter.SOL -> Brand.SOL
    BrandFilter.ALTEUL -> Brand.RTO
    BrandFilter.E1G -> Brand.E1G
    BrandFilter.SKG -> Brand.SKG
    BrandFilter.ETC -> Brand.ETC
}
```

- [ ] 모델과 디자인 시스템 테스트를 통과시킨다.

Run: `./gradlew :core:model:test :core:designsystem:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

### Step 3: 저장된 구 enum 이름의 읽기 호환성 테스트와 구현

- [ ] `DefaultSettingsRepositoryTest`에 `RTO`, `RTX`, `NHO` 각각이 `ALTEUL`로 복원되는 parameterized-equivalent 반복 테스트를 추가한다.

```kotlin
@Test
fun `legacy alteul filter names restore as grouped alteul`() = runBlocking {
    listOf("RTO", "RTX", "NHO").forEach { legacyName ->
        val repository = DefaultSettingsRepository(
            dataSource = InMemoryUserPreferencesDataSource(
                StoredUserPreferences.Default.copy(brandFilterName = legacyName),
            ),
        )

        assertEquals(BrandFilter.ALTEUL, repository.observeUserPreferences().first().brandFilter)
    }
}
```

- [ ] 새 선택값 저장 시 문자열 `ALTEUL`을 기록하고 알 수 없는 문자열은 `ALL`로 복구하는 테스트를 유지/추가한다.
- [ ] `StoredUserPreferences.toDomain()`의 브랜드만 전용 파서를 사용한다.

```kotlin
private fun parseBrandFilter(value: String): BrandFilter = when (value) {
    "RTO", "RTX", "NHO" -> BrandFilter.ALTEUL
    else -> enumOrDefault(value, BrandFilter.ALL)
}
```

- [ ] `brandFilter = parseBrandFilter(brandFilterName)`로 연결하고 `toStored()`는 기존처럼 `brandFilter.name`을 저장한다.
- [ ] 데이터 설정 테스트를 통과시킨다.

Run: `./gradlew :data:settings:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, legacy 세 값과 신규 `ALTEUL` 저장 테스트 통과.

### Step 4: 검색 조립기가 세 알뜰 브랜드를 반환하는지 증명

- [ ] `StationSearchResultAssemblerTest`에 RTO/RTX/NHO/SKE가 섞인 결과를 만들고 `BrandFilter.ALTEUL` 조회가 앞의 세 개만 포함하는 테스트를 추가한다.
- [ ] 프로덕션 조립기의 `query.brandFilter.matches(station.brand)` 경로는 변경하지 않는다. 새 모델 계약만으로 테스트가 통과해야 한다.

Run: `./gradlew :data:station:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

### Step 5: 설정 화면 목록·설명·로고 tag를 새 필터 계약으로 변경

- [ ] `SettingOptionUiModel`이 대표 로고와 필터 기반 tag를 함께 전달하도록 필드를 추가한다.

```kotlin
data class SettingOptionUiModel(
    val label: StringResource,
    val subtitle: StringResource? = null,
    val meta: StringResource? = null,
    val action: SettingsAction,
    val isSelected: Boolean,
    val brandIconBrand: Brand? = null,
    val brandIconTag: String? = null,
)
```

- [ ] `SettingsUiState.optionsFor(BrandFilter)`가 `gasStationBrandFilterIconBrand()`와 `option.name`을 사용한다.

```kotlin
brandIconBrand = option.gasStationBrandFilterIconBrand(),
brandIconTag = option.takeUnless { it == BrandFilter.ALL }?.name,
```

- [ ] 설정 설명의 exhaustive `when`을 `ALTEUL`에 맞추고 전용 설명 리소스를 연결한다.

```kotlin
BrandFilter.ALTEUL -> StringResource.fromId(R.string.settings_brand_alteul_desc)
```

- [ ] 리소스를 추가한다.

```xml
<!-- values/strings.xml -->
<string name="settings_brand_alteul_desc">알뜰주유소 전체를 표시합니다.</string>

<!-- values-en/strings.xml -->
<string name="settings_brand_alteul_desc">Shows all Alteul stations.</string>
```

- [ ] `SettingsDetailScreen`의 tag를 실제 대표 로고의 `RTO`가 아니라 옵션의 `ALTEUL`로 노출한다.

```kotlin
modifier = Modifier.testTag(
    "$SETTINGS_BRAND_LOGO_TAG_PREFIX${requireNotNull(option.brandIconTag)}",
),
```

- [ ] `SettingsUiStateTest`에서 옵션 라벨 순서가 `전체, SK에너지, GS칼텍스, 현대오일뱅크, S-OIL, 알뜰, E1, SK가스, 자가상표`인지, 알뜰 action이 `BrandFilterSelected(ALTEUL)`인지 확인한다.
- [ ] `SettingsScreenTest`에서 `RTO/RTX/NHO` 개별 행이 없고 `알뜰` 행과 `settings-brand-logo-ALTEUL` 하나만 있는지 확인한다.
- [ ] `StationListViewModelTest`의 필터 fixture `BrandFilter.RTO`를 `BrandFilter.ALTEUL`로 바꿔 컴파일 계약을 맞춘다.
- [ ] 설정과 주변 화면 단위 테스트를 통과시킨다.

Run: `./gradlew :feature:settings:testDebugUnitTest :feature:station-list:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, 제거된 enum 참조가 `rg -n 'BrandFilter\.(RTO|RTX|NHO)' --glob '*.kt'`에서 0건.

### Step 6: 첫 작업 커밋

- [ ] `git diff --check`와 `git status --short`를 확인한다.
- [ ] `gradle.properties`를 제외한 Task 1 파일만 스테이징한다.
- [ ] 커밋한다.

Run: `git commit -m "feat: group alteul brand filters"`

---

## Task 2: 반경·유종·브랜드에 공통 anchored menu 적용

**Files:**

- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFilterMenu.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListFilterRail.kt`
- Modify: `feature/station-list/src/main/res/values/strings.xml`
- Modify: `feature/station-list/src/main/res/values-en/strings.xml`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/UrbanSignal.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/component/UrbanSignalContractsTest.kt`

### Step 1: 메뉴 동작과 semantics 테스트를 RED로 작성

- [ ] 아래 stable tag를 `StationListFilterMenu.kt`의 top-level internal constants로 정의할 사용처를 먼저 테스트에 쓴다.

```kotlin
internal const val STATION_LIST_RADIUS_FILTER_TAG = "station-list-filter-radius"
internal const val STATION_LIST_FUEL_FILTER_TAG = "station-list-filter-fuel"
internal const val STATION_LIST_BRAND_FILTER_TAG = "station-list-filter-brand"
internal const val STATION_LIST_FILTER_MENU_TAG = "station-list-filter-menu"
internal const val STATION_LIST_FILTER_OPTION_TAG_PREFIX = "station-list-filter-option-"
internal const val STATION_LIST_FILTER_BRAND_LOGO_TAG_PREFIX = "station-list-filter-brand-logo-"
```

- [ ] `StationListScreenTest`에 반경 버튼을 누르면 `검색 반경`, `3km`, `4km`, `5km`가 나타나고 `4km` 선택 시 `SearchRadiusSelected(KM_4)`가 한 번 전달되는 테스트를 추가한다.
- [ ] 유종 버튼을 누르면 `유종 선택`과 모든 유종 옵션이 같은 `STATION_LIST_FILTER_MENU_TAG` 아래 나타나는 테스트를 추가한다.
- [ ] 브랜드 버튼을 누르면 `브랜드 선택`, grouped `알뜰`, 마지막 `자가상표`가 나타나고 구분된 세 알뜰 라벨은 나타나지 않는 테스트를 추가한다.
- [ ] 반경 메뉴가 열린 상태에서 유종 chip을 누르면 메뉴 노드가 하나뿐이고 제목이 `유종 선택`으로 바뀌는 테스트를 추가한다.
- [ ] `Espresso.pressBack()`을 호출하거나 popup 밖 root 좌표를 누르면 열린 메뉴가 사라지는 테스트를 각각 추가한다.
- [ ] 선택 행의 `selected=true`, 모든 chip/option의 최소 높이 48dp, `전체`에는 로고가 없고 `알뜰`에는 `station-list-filter-brand-logo-ALTEUL`이 있는지 확인한다.
- [ ] 320dp 폭에서 메뉴 bounds가 root bounds 안에 있고, 브랜드 목록을 scroll해 마지막 `자가상표`를 선택할 수 있는지 확인한다.

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests '*StationListScreenTest*filter*'`

Expected: 새 tag와 메뉴 제목 리소스가 없어 컴파일 또는 assertion 실패.

### Step 2: 메뉴 표시용 타입과 공통 Urban Signal 크기 토큰 구현

- [ ] `UrbanSignalTokens`에 메뉴 전용 로고 크기를 추가하고 계약 테스트에 정확한 값을 고정한다.

```kotlin
val filterMenuLogoTileSize = 32.dp
val filterMenuLogoSize = 24.dp
```

- [ ] `StationListFilterMenu.kt`에 메뉴 종류와 generic 옵션을 정의한다.

```kotlin
internal enum class StationListFilterMenuKind { Radius, Fuel, Brand }

internal data class StationListFilterOption<T>(
    val value: T,
    val label: String,
    val testKey: String,
    val brand: Brand? = null,
)
```

- [ ] 한국어/영어 제목 리소스를 추가한다.

```xml
<!-- values/strings.xml -->
<string name="station_list_filter_radius_title">검색 반경</string>
<string name="station_list_filter_fuel_title">유종 선택</string>
<string name="station_list_filter_brand_title">브랜드 선택</string>

<!-- values-en/strings.xml -->
<string name="station_list_filter_radius_title">Search radius</string>
<string name="station_list_filter_fuel_title">Fuel type</string>
<string name="station_list_filter_brand_title">Brand</string>
```

### Step 3: ivory/black/yellow 공통 anchored menu 구현

- [ ] `StationListFilterMenu`는 `DropdownMenu` 한 개를 사용하고 다음 시각 계약을 명시한다.

```kotlin
DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    modifier = Modifier
        .testTag(STATION_LIST_FILTER_MENU_TAG)
        .widthIn(min = 220.dp, max = 300.dp)
        .border(2.dp, ColorBlack, RoundedCornerShape(16.dp))
        .background(ColorSurface, RoundedCornerShape(16.dp)),
    shape = RoundedCornerShape(16.dp),
    containerColor = ColorSurface,
    tonalElevation = 0.dp,
    shadowElevation = 8.dp,
) {
    Text(
        text = title,
        style = GasStationTheme.typography.meta,
        color = ColorBlack,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    options.forEach { option ->
        StationListFilterMenuRow(
            option = option,
            selected = option.value == selected,
            onClick = { onSelected(option.value) },
        )
    }
}
```

- [ ] 행은 최소 48dp, 선택 행은 `ColorBlack` 배경/`ColorYellow` 텍스트와 check, 비선택 행은 ivory/black으로 렌더한다.
- [ ] 브랜드 옵션만 `GasStationBrandLogoTile`을 `filterMenuLogoTileSize/filterMenuLogoSize`로 렌더한다. `ALL`은 leading 빈칸을 만들지 않는다.
- [ ] `ALTEUL`의 대표 `Brand.RTO`가 기존 `gasStationBrandIconResource()`를 통해 공통 `ic_rtx` asset을 사용하게 하고 새 drawable은 만들지 않는다.
- [ ] option modifier에 `selected`와 `Role.RadioButton` semantics를 설정하고 `testKey`로 tag를 만든다.

### Step 4: 세 chip을 하나의 열림 상태에 연결

- [ ] `StationListFilterRail`에서 각각의 `rememberSaveable`을 제거하고 rail-level 상태 하나만 둔다.

```kotlin
var expandedMenuName by rememberSaveable { mutableStateOf<String?>(null) }
val expandedMenu = expandedMenuName?.let(StationListFilterMenuKind::valueOf)
```

- [ ] radius/fuel/brand chip은 같은 `FilterMenuChip` 모양과 각각의 stable tag를 사용한다. 닫힌 chip은 down chevron, 열린 chip은 up chevron과 2dp yellow outline을 표시한다.
- [ ] 정렬 chip은 메뉴를 열지 않고 기존 `SortToggleRequested` 동작과 semantics를 유지한다.
- [ ] `SearchRadius`, `FuelType`, `BrandFilter` 옵션을 `StationListFilterOption`으로 매핑한다. 브랜드의 `brand`는 `gasStationBrandFilterIconBrand()`, `testKey`는 enum `name`을 쓴다.
- [ ] 옵션 선택 시 먼저 상태를 닫고 정확한 `StationListAction`을 한 번 전송한다.
- [ ] filter rail이 content body에서 제거되면 popup도 composition에서 제거되는 구조를 유지한다. menu state를 ViewModel이나 navigation state로 승격하지 않는다.
- [ ] 테스트를 통과시킨다.

Run: `./gradlew :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

### Step 5: 두 번째 작업 커밋

- [ ] `git diff --check`와 기존 `gradle.properties` 보존을 확인한다.
- [ ] Task 2 파일만 스테이징해 커밋한다.

Run: `git commit -m "feat: unify nearby filter menus"`

---

## Task 3: 의사결정 요약을 두 줄로 압축

**Files:**

- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListQuerySummary.kt`
- Modify: `feature/station-list/src/main/res/values/strings.xml`
- Modify: `feature/station-list/src/main/res/values-en/strings.xml`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`

### Step 1: 두 줄 정보 구조 테스트를 RED로 작성

- [ ] 기존 summary 테스트를 다음 계약으로 바꾼다.

```kotlin
composeRule.onNodeWithTag(STATION_LIST_DECISION_LOWEST_TAG)
    .assertTextEquals("최저 1,968원")
composeRule.onNodeWithTag(STATION_LIST_DECISION_COUNT_TAG)
    .assertTextEquals("36곳")
composeRule.onNodeWithTag(STATION_LIST_DECISION_AVERAGE_TAG)
    .assertTextEquals("평균 2,070원")
composeRule.onNodeWithTag(STATION_LIST_DECISION_SAVINGS_TAG)
    .assertTextEquals("102원 저렴")
```

- [ ] 동률이면 `공동 최저 1,968원`인지 확인한다.
- [ ] singleton summary는 첫 행만 보여 `average`와 `savings` tag가 존재하지 않는지 확인한다.
- [ ] 320dp 폭과 fontScale 2.0에서 네 tag가 display되고 가로 화면 경계를 넘지 않는 기존 bounds 테스트를 두 줄 구조에 맞춘다.
- [ ] English locale에서 기존 원화 formatter 계약을 유지한 `Lowest 1,968원`, `36 stations`, `Average 2,070원`, `102원 below average`를 확인한다.

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests '*StationListScreenTest*summary*'`

Expected: 기존 `최저가`와 분리된 가격 노드 때문에 text assertion 실패.

### Step 2: 문자열과 2-row layout 구현

- [ ] 요약 문자열을 결합형으로 바꾼다.

```xml
<!-- values/strings.xml -->
<string name="station_list_decision_lowest">최저 %1$s</string>
<string name="station_list_decision_tied_lowest">공동 최저 %1$s</string>
<string name="station_list_decision_average">평균 %1$s</string>
<string name="station_list_decision_savings">%1$s 저렴</string>

<!-- values-en/strings.xml -->
<string name="station_list_decision_lowest">Lowest %1$s</string>
<string name="station_list_decision_tied_lowest">Tied lowest %1$s</string>
<string name="station_list_decision_average">Average %1$s</string>
<string name="station_list_decision_savings">%1$s below average</string>
```

- [ ] `StationListDecisionSummaryStrip`의 내부를 최대 두 개의 `Row`로 바꾼다.

```kotlin
Column(
    modifier = Modifier.weight(1f),
    verticalArrangement = Arrangement.spacedBy(GasStationTheme.spacing.space8),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GasStationTheme.spacing.space8),
    ) {
        Text(
            text = stringResource(
                if (summary.isLowestPriceTied) R.string.station_list_decision_tied_lowest
                else R.string.station_list_decision_lowest,
                summary.lowestPriceWon.gasStationWonLabel(),
            ),
            modifier = Modifier.weight(1f).testTag(STATION_LIST_DECISION_LOWEST_TAG),
            color = ColorYellow,
            style = numericMetricStyle,
        )
        Text(
            text = stringResource(R.string.station_list_decision_count, summary.count),
            modifier = Modifier.testTag(STATION_LIST_DECISION_COUNT_TAG),
            color = ColorSurface,
            style = numericMetricStyle,
        )
    }
    if (summary.averagePriceWon != null && summary.savingsWon != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GasStationTheme.spacing.space8),
        ) {
            Text(
                text = stringResource(
                    R.string.station_list_decision_average,
                    summary.averagePriceWon.gasStationWonLabel(),
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag(STATION_LIST_DECISION_AVERAGE_TAG),
                color = ColorSurface,
                style = numericMetricStyle,
                maxLines = 2,
            )
            Text(
                text = stringResource(
                    R.string.station_list_decision_savings,
                    summary.savingsWon.gasStationWonLabel(),
                ),
                modifier = Modifier.testTag(STATION_LIST_DECISION_SAVINGS_TAG),
                color = ColorYellow,
                style = numericMetricStyle,
                maxLines = 2,
            )
        }
    }
}
```

- [ ] 둘째 행은 average에 `weight(1f)`, savings에 yellow을 적용한다. `maxLines = 2`를 허용해 큰 글꼴에서 잘라내지 않는다.
- [ ] summary strip의 black/yellow/ivory 색과 기존 네 test tag를 유지한다.
- [ ] 전체 station-list 테스트를 통과시킨다.

Run: `./gradlew :feature:station-list:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

### Step 3: 세 번째 작업 커밋

- [ ] `git diff --check` 후 Task 3 파일만 커밋한다.

Run: `git commit -m "feat: compact nearby decision summary"`

---

## Task 4: 가격 라벨을 제거하고 가격 이력 상태를 명시

**Files:**

- Create: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListPriceHistoryUiModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`
- Modify: `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListCards.kt`
- Modify: `feature/station-list/src/main/res/values/strings.xml`
- Modify: `feature/station-list/src/main/res/values-en/strings.xml`
- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/Metric.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/RoborazziDesignSystemTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListItemUiModelTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListScreenTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RoborazziStationListScreenTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListDecisionSummaryTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListRoutePolicyTest.kt`
- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

### Step 1: domain 상태별 UI 모델 테스트를 RED로 작성

- [ ] `StationListItemUiModelTest`에 네 상태의 타입 보존 테스트를 작성한다.

```kotlin
@Test
fun `price history keeps unavailable distinct from unchanged`() {
    assertEquals(
        StationListPriceHistoryUiModel.Unavailable,
        StationListItemUiModel(
            entry = stationEntry(priceDelta = StationPriceDelta.Unavailable),
        ).priceHistory,
    )
    assertEquals(
        StationListPriceHistoryUiModel.Unchanged,
        StationListItemUiModel(
            entry = stationEntry(priceDelta = StationPriceDelta.Unchanged),
        ).priceHistory,
    )
}

@Test
fun `price history keeps rise and fall amounts`() {
    assertEquals(
        StationListPriceHistoryUiModel.Increased(20),
        StationListItemUiModel(
            entry = stationEntry(priceDelta = StationPriceDelta.Increased(20)),
        ).priceHistory,
    )
    assertEquals(
        StationListPriceHistoryUiModel.Decreased(30),
        StationListItemUiModel(
            entry = stationEntry(priceDelta = StationPriceDelta.Decreased(30)),
        ).priceHistory,
    )
}
```

- [ ] `StationListScreenTest`에서 행마다 `가격` 텍스트가 없고 네 상태가 각각 `가격 이력 없음`, `변동 없음`, `▲ 20원`, `▼ 30원`으로 표시되는 테스트를 추가한다.
- [ ] English locale은 `No price history`, `No change`, `▲ 20 won`, `▼ 30 won`을 확인한다.

Run: `./gradlew :feature:station-list:testDebugUnitTest --tests '*StationListItemUiModelTest*' --tests '*StationListScreenTest*price*'`

Expected: `StationListPriceHistoryUiModel` unresolved reference와 기존 `-` assertion 실패.

### Step 2: typed price history UI model 구현

- [ ] 새 파일에 domain 상태를 소실 없이 매핑한다.

```kotlin
sealed interface StationListPriceHistoryUiModel {
    data object Unavailable : StationListPriceHistoryUiModel
    data object Unchanged : StationListPriceHistoryUiModel
    data class Increased(val amountWon: Int) : StationListPriceHistoryUiModel
    data class Decreased(val amountWon: Int) : StationListPriceHistoryUiModel

    companion object {
        fun from(delta: StationPriceDelta): StationListPriceHistoryUiModel = when (delta) {
            StationPriceDelta.Unavailable -> Unavailable
            StationPriceDelta.Unchanged -> Unchanged
            is StationPriceDelta.Increased -> Increased(delta.amountWon)
            is StationPriceDelta.Decreased -> Decreased(delta.amountWon)
        }
    }
}

internal enum class PriceDeltaTone { Rise, Fall, Neutral }
```

- [ ] `StationListItemUiModel`에서 `priceDeltaLabel`과 `priceDeltaTone`을 제거하고 다음 필드를 둔다.

```kotlin
val priceHistory: StationListPriceHistoryUiModel,
```

- [ ] entry constructor는 `priceHistory = StationListPriceHistoryUiModel.from(entry.priceDelta)`를 사용한다.
- [ ] `rg -l 'priceDeltaLabel|priceDeltaTone' feature/station-list/src/test`로 찾은 모든 fixture를 새 필드로 바꾼다. 중립 fixture는 의도를 분명히 하도록 `Unavailable` 또는 `Unchanged` 중 하나를 명시한다.

### Step 3: 가격 라벨 optional 계약과 localized indicator 구현

- [ ] `GasStationMetricBlock`의 label을 nullable default로 바꾸고 값이 있을 때만 렌더한다.

```kotlin
fun GasStationMetricBlock(
    number: String,
    unit: String,
    emphasis: GasStationMetricEmphasis,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    numberColor: Color = MaterialTheme.colorScheme.onBackground,
    unitColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val spacing = GasStationTheme.spacing
    val metaStyle = ChromeTextRole.Meta.style()
    val numberStyle = emphasis.numberRole.style()

    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceBetween) {
        label?.let { labelText ->
            Text(
                text = labelText,
                style = metaStyle,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = number,
                modifier = Modifier.weight(1f, fill = false),
                style = numberStyle,
                color = numberColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = unit,
                modifier = Modifier.padding(
                    start = spacing.space4,
                    bottom = emphasis.unitBottomPadding,
                ),
                style = metaStyle,
                color = unitColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

- [ ] station card 호출부는 `label`을 전달하지 않는다. 디자인 시스템 snapshot fixture는 명시적 label을 유지해 optional 경로를 함께 커버한다.
- [ ] 정확한 리소스를 추가한다.

```xml
<!-- values/strings.xml -->
<string name="station_list_price_history_unavailable">가격 이력 없음</string>
<string name="station_list_price_history_unchanged">변동 없음</string>
<string name="station_list_price_history_increased">▲ %1$d원</string>
<string name="station_list_price_history_decreased">▼ %1$d원</string>

<!-- values-en/strings.xml -->
<string name="station_list_price_history_unavailable">No price history</string>
<string name="station_list_price_history_unchanged">No change</string>
<string name="station_list_price_history_increased">▲ %1$d won</string>
<string name="station_list_price_history_decreased">▼ %1$d won</string>
```

- [ ] `PriceDeltaIndicator`가 UI 모델을 받아 label/tone을 한 번에 결정하게 한다.

```kotlin
private data class PriceHistoryPresentation(val label: String, val tone: PriceDeltaTone)

@Composable
private fun StationListPriceHistoryUiModel.presentation(): PriceHistoryPresentation = when (this) {
    StationListPriceHistoryUiModel.Unavailable -> PriceHistoryPresentation(
        stringResource(R.string.station_list_price_history_unavailable),
        PriceDeltaTone.Neutral,
    )
    StationListPriceHistoryUiModel.Unchanged -> PriceHistoryPresentation(
        stringResource(R.string.station_list_price_history_unchanged),
        PriceDeltaTone.Neutral,
    )
    is StationListPriceHistoryUiModel.Increased -> PriceHistoryPresentation(
        stringResource(R.string.station_list_price_history_increased, amountWon),
        PriceDeltaTone.Rise,
    )
    is StationListPriceHistoryUiModel.Decreased -> PriceHistoryPresentation(
        stringResource(R.string.station_list_price_history_decreased, amountWon),
        PriceDeltaTone.Fall,
    )
}
```

- [ ] 화살표는 문자열에 이미 포함되므로 기존 `ArrowDropUp/ArrowDropDown` icon을 제거한다. 상승/하락 색, neutral 색, `STATION_LIST_PRICE_CHANGE_TAG`는 유지한다.
- [ ] metadata container를 `FlowRow`로 바꿔 기본 폭에서는 유종과 이력을 한 줄에 두고, 좁은 폭/큰 글꼴에서는 이력만 다음 줄로 흐르게 한다.

```kotlin
FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    maxItemsInEachRow = 2,
) {
    FuelChip(text = fuelTypeLabel)
    PriceDeltaIndicator(
        history = station.priceHistory,
        modifier = Modifier.testTag(STATION_LIST_PRICE_CHANGE_TAG),
    )
}
```

- [ ] 이력 문구 자체는 `maxLines = 1`과 ellipsis를 유지하며 가격 숫자나 station name의 폭을 줄이지 않는다.
- [ ] 320dp 폭과 fontScale 2.0에서 상태 문구가 station name/가격과 겹치지 않는 기존 bounds 테스트를 갱신한다.

Run: `./gradlew :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`, `rg -n 'priceDeltaLabel|priceDeltaTone|text = "-"' feature/station-list/src` 0건.

### Step 4: 네 번째 작업 커밋

- [ ] `git diff --check` 후 Task 4 파일만 커밋한다.

Run: `git commit -m "feat: clarify station price history"`

---

## Task 5: 하단 탐색을 아이콘 전용으로 변경

**Files:**

- Modify: `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/component/UrbanSignal.kt`
- Modify: `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/RoborazziDesignSystemTest.kt`
- Modify: `app/src/main/java/com/gasstation/navigation/GasStationTopLevelNavigation.kt`
- Modify: `app/src/testDemo/java/com/gasstation/navigation/GasStationBottomNavigationTest.kt`
- Verify: `app/src/testDemo/java/com/gasstation/navigation/GasStationRootWatchlistLayoutTest.kt`

### Step 1: 시각 라벨 제거와 접근성 보존 테스트를 RED로 작성

- [ ] `GasStationBottomNavigationTest`에서 `주변`, `관심`, `설정` Text 노드가 존재하지 않는지 확인한다.
- [ ] 기존 `BOTTOM_NAV_*_TAG` 노드 각각에 content description `주변`, `관심`, `설정`과 최소 48dp touch bounds가 있는지 확인한다.
- [ ] 선택 destination의 `selected=true`, 비선택은 false, 위치가 없을 때 관심 탭 disabled state description이 기존 문구인지 확인한다.

Run: `./gradlew :app:testDemoDebugUnitTest --tests '*GasStationBottomNavigationTest*'`

Expected: 현재 visible Text label 때문에 label 부재 assertion 실패.

### Step 2: 공통 navigation item을 icon-only로 구현

- [ ] `GasStationNavigationBarItem`의 label을 nullable로 바꾸고 label 유무에 따라 `alwaysShowLabel`을 결정한다.

```kotlin
@Composable
fun RowScope.GasStationNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
) {
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "urban-signal-navigation-selection",
    )
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = selectionScale
                    scaleY = selectionScale
                },
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        },
        label = label,
        modifier = modifier,
        enabled = enabled,
        alwaysShowLabel = label != null,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = ColorYellow,
            selectedTextColor = ColorYellow,
            unselectedIconColor = ColorSurface.copy(alpha = 0.72f),
            unselectedTextColor = ColorSurface.copy(alpha = 0.72f),
            disabledIconColor = ColorSurface.copy(alpha = 0.36f),
            disabledTextColor = ColorSurface.copy(alpha = 0.36f),
            indicatorColor = Color.Transparent,
        ),
    )
}
```

- [ ] `GasStationBottomNavigation`은 기존 문자열을 변수로 resolve하고 각 item modifier semantics에 content description을 부여한다.

```kotlin
val nearbyLabel = stringResource(R.string.nav_nearby)
val watchlistLabel = stringResource(R.string.nav_watchlist)
val settingsLabel = stringResource(R.string.nav_settings)

GasStationNavigationBarItem(
    selected = selected == TopLevelDestination.Nearby,
    onClick = onNearby,
    icon = { Icon(Icons.Rounded.LocalGasStation, contentDescription = null) },
    modifier = Modifier
        .testTag(BOTTOM_NAV_NEARBY_TAG)
        .semantics { contentDescription = nearbyLabel },
)
```

- [ ] 관심과 설정도 같은 방식으로 바꾸고 관심 disabled `stateDescription`을 같은 semantics block에 유지한다.
- [ ] nav 문자열 리소스는 삭제하지 않는다. 화면 표시 대신 접근성 이름으로 사용한다.
- [ ] 디자인 시스템 Roborazzi fixture는 label 없는 기본 경로를 기록하도록 바꾸되, 공통 API의 optional label 경로가 필요한 다른 caller가 있으면 명시적 label로 유지한다.

Run: `./gradlew :core:designsystem:testDebugUnitTest :app:testDemoDebugUnitTest :app:testProdDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

### Step 3: 관심 목록 가용 높이 회귀 확인

- [ ] 기존 `GasStationRootWatchlistLayoutTest`를 실행해 5개 행이 하단 nav 위에 계속 보이는지 확인한다.
- [ ] 실패하면 임의 행 높이 축소 없이 NavigationBar의 content 배치와 window inset만 점검한다.

Run: `./gradlew :app:testDemoDebugUnitTest --tests '*GasStationRootWatchlistLayoutTest*'`

Expected: `BUILD SUCCESSFUL`.

### Step 4: 다섯 번째 작업 커밋

- [ ] `git diff --check` 후 Task 5 파일만 커밋한다.

Run: `git commit -m "feat: simplify bottom navigation labels"`

---

## Task 6: 시각 회귀, README 자산, 문서 계약, 전체 검증

**Files:**

- Modify: `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/RoborazziStationListScreenTest.kt`
- Modify: `feature/station-list/src/test/snapshots/images/*.png`
- Modify: `feature/settings/src/test/kotlin/com/gasstation/feature/settings/RoborazziSettingsScreenTest.kt`
- Modify: `feature/settings/src/test/snapshots/images/*.png`
- Modify: `core/designsystem/src/test/snapshots/images/*.png`
- Modify: `README.md`
- Modify: `docs/readme-assets/playstore_11.png`
- Modify: `docs/readme-assets/playstore_22.png`
- Modify: `docs/readme-assets/playstore_33.png`
- Modify: `CHANGELOG.md`
- Modify: `docs/architecture.md`
- Modify: `docs/agent-workflow.md`
- Modify: `docs/test-strategy.md`
- Modify: `docs/verification-matrix.md`

### Step 1: Roborazzi 시나리오를 승인된 화면 상태로 확장

- [ ] 주변 populated fixture에 다음 가격 이력 상태를 최소 하나씩 포함한다: `Unavailable`, `Unchanged`, `Increased(20)`, `Decreased(30)`.
- [ ] 기존 주변 화면 light/dark snapshot이 2줄 summary, price label 부재, icon-only bottom nav를 포함하도록 fixture를 갱신한다.
- [ ] 반경/유종/브랜드 메뉴를 각각 연 뒤 캡처하는 테스트를 추가한다. 열기 동작은 stable chip tag를 클릭하고 `STATION_LIST_FILTER_MENU_TAG`가 나타난 뒤 캡처한다.
- [ ] 브랜드 메뉴 snapshot은 `알뜰`이 하나이고 `자가상표`가 마지막에 보이도록 필요한 capture height를 확보한다.
- [ ] 설정 BrandFilter detail snapshot은 grouped `알뜰`과 마지막 `자가상표` 순서를 담는다.
- [ ] 디자인 시스템 navigation snapshot은 icon-only 배치를 담는다.

### Step 2: snapshot 기록 후 검증

- [ ] 먼저 기록 명령을 실행한다.

Run: `./gradlew recordRoborazziDebug`

Expected: 변경된 golden PNG가 생성되고 테스트 task 성공.

- [ ] 생성된 이미지를 직접 열어 다음을 확인한다.

  - 요약이 정상 폭에서 정확히 2줄이다.
  - 세 메뉴 모두 ivory surface, 2dp black border, yellow selected treatment를 공유한다.
  - 메뉴가 chip에 anchor되고 화면 밖으로 잘리지 않는다.
  - `가격`과 `-`가 없다.
  - 하단 nav에 글자는 없고 세 아이콘 중심축과 선택 색이 안정적이다.
  - dark theme에서도 상승/하락/neutral 문구 대비가 충분하다.

- [ ] 검증 모드로 재실행한다.

Run: `./gradlew verifyRoborazziDebug`

Expected: `BUILD SUCCESSFUL`, image diff 0건.

### Step 3: README용 실제 demo 화면 3장 재촬영

- [ ] `:app:installDemoDebug` 또는 현재 프로젝트의 문서화된 demo 실행 경로로 360dp급 emulator에 설치한다.
- [ ] 기본 font scale에서 다음 상태를 실제 앱으로 연다.

  1. Nearby populated: 2줄 summary, 닫힌 세 filter chip, explicit price-history 문구, icon-only nav.
  2. Watchlist populated: 저장된 5개 주유소와 icon-only nav가 보이는 상태.
  3. Settings BrandFilter detail: grouped `알뜰` 단일 행과 마지막 `자가상표`.

- [ ] `docs/readme-assets/playstore_11.png`, `playstore_22.png`, `playstore_33.png`을 교체한다. system/device framing만 세 이미지에 동일하게 crop하고 앱 내부 pixel은 편집하지 않는다.
- [ ] README 이미지 링크가 위 세 파일을 가리키는지 확인하고 기능 설명에서 개별 알뜰 필터 표현을 grouped 알뜰로 바꾼다.

### Step 4: 구현과 문서의 계약 동기화

- [ ] `CHANGELOG.md` Unreleased에 다음 사용자 변화만 요약한다: 2줄 summary, 공통 filter menu, grouped 알뜰, 명시적 가격 이력, icon-only bottom nav.
- [ ] `docs/architecture.md`에 실제 `Brand`는 유지되고 `BrandFilter.ALTEUL`이 RTO/RTX/NHO를 매칭한다는 경계를 기록한다.
- [ ] `docs/agent-workflow.md`의 UI 검토 체크에 menu open 상태와 접근성 semantics 검증을 추가한다.
- [ ] `docs/test-strategy.md`에 legacy settings migration, three-menu interaction, price-history variants, large-font summary 검증을 기록한다.
- [ ] `docs/verification-matrix.md`에 이 변경의 touched-module 명령과 Roborazzi/menu-open snapshot gate를 실제 Gradle task 이름으로 반영한다.

### Step 5: 정적 점검과 전체 회귀 실행

- [ ] 제거된 enum/문구/상태 필드가 남지 않았는지 확인한다.

Run: `rg -n 'BrandFilter\.(RTO|RTX|NHO)|priceDeltaLabel|priceDeltaTone|text = "-"' --glob '*.kt'`

Expected: 출력 없음.

- [ ] 사용자에게 보여줄 개별 알뜰 필터 라벨이 필터/설정 코드에 남지 않았는지 확인한다. 실제 station brand 라벨은 허용한다.

Run: `rg -n '자영알뜰|고속도로알뜰|농협알뜰' feature data/settings core/designsystem/src/main`

Expected: `core/designsystem/BrandLabels.kt`의 실제 `Brand` 라벨 외 필터/설정 UI 출력 경로에는 없음.

- [ ] formatting, lint, module boundary, touched modules, demo/prod, snapshots를 한 번에 실행한다.

Run:

```bash
./gradlew \
  spotlessCheck \
  lint \
  verifyModuleBoundaries \
  :core:model:test \
  :core:designsystem:testDebugUnitTest \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  verifyRoborazziDebug \
  :app:compileDemoDebugAndroidTestKotlin \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

Expected: 모든 task `BUILD SUCCESSFUL`.

- [ ] `git diff --check`를 실행한다.
- [ ] `git status --short`에서 의도한 파일과 기존 사용자 변경 `M gradle.properties`만 있는지 확인한다.

### Step 6: 여섯 번째 작업 커밋과 최종 증거 기록

- [ ] Task 6의 test/golden/README/docs 파일만 스테이징한다. `gradle.properties`는 제외한다.
- [ ] 커밋한다.

Run: `git commit -m "test: verify compact nearby experience"`

- [ ] 최종 `git status --short`, `git log -6 --oneline`, 전체 검증 명령의 성공 결과를 handoff에 기록한다.
- [ ] 완료 보고에는 구현된 사용자 변화, demo/prod/Roborazzi 결과, README 이미지 경로, 보존한 사용자 변경 `gradle.properties`를 명시한다.

---

## Acceptance Checklist

- [ ] 요약은 정상 화면에서 두 줄이며 최저가와 절감액이 yellow로 먼저 읽힌다.
- [ ] 3km, 휘발유, 전체 버튼은 같은 디자인의 anchored menu를 연다.
- [ ] 필터와 설정에서 알뜰은 하나이며 RTO/RTX/NHO 실제 station을 모두 포함한다.
- [ ] 자가상표는 브랜드 메뉴와 설정 목록의 마지막이다.
- [ ] 기존 저장값 RTO/RTX/NHO는 앱 업데이트 후 ALTEUL로 안전하게 복원된다.
- [ ] station row에 `가격` 라벨이 없지만 가격 숫자가 첫 번째 읽기 대상이다.
- [ ] `-` 대신 `가격 이력 없음`, `변동 없음`, `▲ N원`, `▼ N원`이 표시된다.
- [ ] 하단 nav는 icon-only이고 TalkBack 이름, selected, disabled semantics가 유지된다.
- [ ] 320dp와 fontScale 2.0에서 summary, menu, station metadata가 잘리거나 겹치지 않는다.
- [ ] demo/prod 빌드, unit/UI tests, lint, module boundary, Roborazzi가 모두 통과한다.
- [ ] `gradle.properties`의 기존 사용자 변경은 손대지 않는다.
