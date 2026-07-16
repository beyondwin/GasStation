# Nearby UI Density And Filter Clarity

## Goal

GasStation 주변 화면에서 결정에 필요한 정보는 유지하면서 세로 공간과 해석 비용을 줄인다. 사용자는 기본 화면에서 최저가 요약과 더 많은 주유소 행을 빠르게 비교하고, 반경·유종·브랜드 필터와 가격 이력 상태를 처음 사용해도 이해할 수 있어야 한다.

성공 기준은 다음과 같다.

- 결과 요약은 일반적인 화면 폭과 기본 글꼴에서 2줄로 읽힌다.
- 하단 내비게이션과 주유소 행의 중복 visible label을 제거하되 접근성 의미는 유지한다.
- `-` 가격 상태를 없애고 이력 없음과 변동 없음을 구분한다.
- 반경·유종·브랜드 필터가 동일한 Urban Signal 앵커 메뉴를 사용한다.
- 자영알뜰·고속도로알뜰·농협알뜰은 하나의 `알뜰` 필터로 동작한다.
- 주변 필터와 설정 브랜드 목록에서 `자가상표`가 마지막에 표시된다.

## Relationship To Existing Design

이 문서는 `2026-07-17-full-app-urban-signal-ui-redesign-design.md`의 후속 조정이다. 기존 문서의 색상, 타이포그래피, price-first hierarchy, 실제 브랜드 자산, 상태 화면, navigation state 보존, demo/prod, 캐시, 위치, 외부 지도 계약은 유지한다.

다음 항목만 이 문서가 기존 디자인을 대체한다.

- Station List decision summary의 5줄 배치
- Station List 반경·유종·브랜드 기본 Material dropdown 표현
- Station List row의 visible `가격` label과 neutral `-` price delta
- bottom navigation의 visible `주변`·`관심`·`설정` label
- `BrandFilter`의 알뜰 3종 개별 선택과 `자가상표` 표시 순서

## Explored Approaches

### Decision Summary

1. **판단 우선형 — 선택됨.** 첫 줄에 최저가와 결과 수, 둘째 줄에 평균과 절감액을 좌우 배치한다. 가격이 첫 시선이라는 제품 원칙과 가장 잘 맞는다.
2. **2×2 계기판형.** 네 metric의 정렬은 쉽지만 분석 도구처럼 보이고 label 수가 늘어난다.
3. **문장형.** 자연스럽게 읽히지만 좁은 폭과 큰 글꼴에서 첫 줄이 쉽게 깨진다.

### Filter Selection

1. **아이보리 앵커 메뉴 — 선택됨.** 현재 chip과 anchored interaction을 유지하면서 검정 테두리와 선택면, 노란 signal로 앱의 시각 언어를 적용한다.
2. **검정 앵커 메뉴.** 브랜드 대비는 강하지만 검정 요약 strip과 겹쳐 화면이 무겁다.
3. **2열 bottom sheet.** 많은 브랜드를 스캔하기 쉽지만 반경·유종과 다른 interaction을 만들고 현재 chip의 공간적 연결을 끊는다.

### Price History Status

1. **상태 직접 설명 — 선택됨.** `가격 이력 없음`, `변동 없음`, `▲ N원`, `▼ N원`으로 실제 의미를 표시한다.
2. **대화형 문구.** `첫 가격 확인`은 친근하지만 history가 없다는 사실이 항상 첫 확인을 뜻하지 않는다.
3. **변화가 있을 때만 표시.** 가장 조용하지만 neutral 상태가 비어 보이고 데이터가 없는 이유를 설명하지 못한다.

## Screen Design

### Decision Summary Strip

결과가 2개 이상이면 검정 summary strip은 기본 글꼴에서 약 86dp 높이와 두 visual row를 사용한다.

| Row | Left | Right |
| --- | --- | --- |
| 1 | 노란색 `최저 1,968원` 또는 `공동 최저 1,968원` | `36곳` |
| 2 | `평균 2,070원` | 노란색 `102원 저렴` |

좌우 값은 baseline을 맞추고 숫자에는 tabular number를 유지한다. 첫 줄의 최저가가 strip의 hero이며 결과 수는 보조 정보다. 둘째 줄은 평균과 절감액의 관계를 같은 행에서 비교하게 한다.

기존 계산 계약은 유지한다.

- 0개: summary strip을 숨기고 empty guidance를 표시한다.
- 1개: `최저 1,968원`과 `1곳`만 한 행에 표시하고 평균과 절감액을 만들지 않는다.
- 2개 이상: 표시 중인 station의 typed `priceWon`으로 평균과 절감액을 계산한다.
- 최저가가 여러 개면 `공동 최저`를 사용한다.

2줄은 기본 글꼴 acceptance criterion이다. 320dp 폭이나 200% font scale에서 텍스트를 축소하거나 자르지 않으며 strip 높이와 행 수가 자연스럽게 늘어날 수 있다.

### Shared Anchored Filter Menu

정렬 chip은 기존처럼 `거리순`과 `가격순`을 직접 전환한다. `3km`, `휘발유`, `전체` chip은 각각 같은 anchored single-choice menu를 연다.

공통 visual contract:

- canvas: `ColorSurface` 아이보리
- border: `ColorBlack`, 2dp
- corner: 16dp
- menu row: 최소 48dp
- selected row: 검정 배경, 노란 label, trailing check
- unselected row: 아이보리 배경, 검정 label
- open chip: 위쪽 chevron과 노란 focus border
- elevation: 내용을 구분하는 짧은 shadow만 사용
- 한 번에 하나의 menu만 열림
- 바깥 탭 또는 system back으로 dismiss
- menu width와 anchor offset은 화면 bounds 안으로 제한
- 화면에 들어가지 않는 옵션은 menu 내부에서 scroll

반경과 유종은 text-only row를 사용한다. 브랜드 menu는 `전체`만 text-only이고 구체 브랜드에는 compact 실제 logo tile과 label을 함께 표시한다. `알뜰`은 기존 `ic_rtx`, `자가상표`는 `ic_etc`를 사용한다. 가짜 `ALL` logo는 만들지 않는다.

선택하면 menu를 먼저 닫고 기존 `SearchRadiusSelected`, `FuelTypeSelected`, `BrandFilterSelected` action을 전달한다. 기존 settings update use case와 active-query refresh 규칙이 이어서 동작한다.

### Brand Filter Vocabulary And Order

사용자에게 보이는 브랜드 필터 순서는 다음으로 고정한다.

1. 전체
2. SK에너지
3. GS칼텍스
4. 현대오일뱅크
5. S-OIL
6. 알뜰
7. E1
8. SK가스
9. 자가상표

`알뜰`은 `Brand.RTO`, `Brand.RTX`, `Brand.NHO`를 모두 포함한다. 실제 station의 브랜드 identity와 접근성 label은 계속 자영알뜰·고속도로알뜰·농협알뜰을 구분한다. 통합은 검색 설정의 filter vocabulary에만 적용한다.

Settings BrandFilter detail도 같은 9개 option과 순서를 사용한다. `알뜰` option은 공통 알뜰 logo와 `알뜰주유소 전체를 표시합니다.` 설명을 사용한다. `자가상표`는 마지막 row다. Settings는 기존 pushed detail screen과 radio semantics를 유지하며 Station List의 popup menu로 바꾸지 않는다.

### Station Comparison Row

가격 숫자 위의 visible `가격` label을 제거한다. 가격 숫자와 작은 `원` 단위가 row의 첫 번째 읽기 대상이며, 역명과 거리, 유종, 가격 이력, bookmark 순서의 의미는 유지한다.

가격 이력 copy는 다음과 같다.

| Domain state | Visible copy | Tone |
| --- | --- | --- |
| `Unavailable` | `가격 이력 없음` | neutral muted |
| `Unchanged` | `변동 없음` | neutral muted |
| `Increased(N)` | `▲ N원` | rise/error tone |
| `Decreased(N)` | `▼ N원` | fall/information tone |

이력 상태는 유종 chip 오른쪽에 표시하고 최대 1줄을 사용한다. 좁은 화면에서 유종과 상태가 가격 또는 역명을 밀어내면 metadata row만 다음 줄로 확장한다. `-`는 visible copy와 accessibility copy 어디에도 사용하지 않는다.

새 copy의 한국어/영어 resource contract는 다음과 같다.

| Meaning | Korean | English |
| --- | --- | --- |
| radius menu title | `검색 반경` | `Search radius` |
| fuel menu title | `유종 선택` | `Fuel type` |
| brand menu title | `브랜드 선택` | `Brand` |
| unavailable history | `가격 이력 없음` | `No price history` |
| unchanged price | `변동 없음` | `No change` |
| increased price | `▲ %1$d원` | `▲ %1$d won` |
| decreased price | `▼ %1$d원` | `▼ %1$d won` |
| grouped alteul description | `알뜰주유소 전체를 표시합니다.` | `Shows all Alteul stations.` |

### Icon-Only Bottom Navigation

하단 내비게이션의 visible `주변`, `관심`, `설정` text를 제거하고 기존 세 icon을 유지한다.

- selected icon: `ColorYellow`와 현재의 미세한 scale emphasis
- unselected icon: 낮은 대비의 `ColorSurface`
- disabled watchlist icon: 현재 disabled opacity
- indicator pill: 추가하지 않음
- item touch target: 최소 48dp
- system navigation inset: 유지

visible label을 제거해도 `주변`, `관심`, `설정` 문자열은 accessibility content description으로 유지한다. selected semantics, watchlist disabled state description, `bottom-nav-nearby`, `bottom-nav-watchlist`, `bottom-nav-settings` test tag도 유지한다.

## Architecture And Ownership

### `core:model`

실제 `Brand` enum은 변경하지 않는다. `BrandFilter`는 다음 grouped filter identity를 갖는다.

```text
ALL, SKE, GSC, HDO, SOL, ALTEUL, E1G, SKG, ETC
```

현재 single nullable `brand` 표현을 matched brand set으로 바꾼다.

- `ALL`: 모든 `Brand` match
- single-brand filters: 대응하는 한 `Brand` match
- `ALTEUL`: `RTO`, `RTX`, `NHO` match

`matches(stationBrand)`는 이 membership contract만 소유한다. 표시 label과 drawable 선택은 `core:designsystem`에 둔다.

### `data:settings`

DataStore는 enum name 문자열을 저장하므로 기존 사용자 값을 명시적으로 호환한다.

- stored `RTO`, `RTX`, `NHO` -> `BrandFilter.ALTEUL`
- 새 valid enum name -> 해당 `BrandFilter`
- unknown value -> `BrandFilter.ALL`

새로운 저장은 `ALTEUL`을 기록한다. DataStore 파일 구조, serializer key, Room schema는 변경하지 않는다.

### `core:designsystem`

- `BrandFilter.ALTEUL` visible label은 `알뜰`
- filter icon mapping은 `ALTEUL`에 공통 알뜰 asset을 제공
- icon-only navigation item visual primitive

feature 전용 menu option, 문자열 분기, menu open state는 소유하지 않는다.

### `feature:station-list`

- 반경·유종·브랜드가 공유하는 anchored menu component와 local open/dismiss state
- option ordering과 기존 `StationListAction` dispatch
- compact summary layout
- typed price-history UI projection과 localized copy
- price label 제거와 metadata layout

현재 location, refresh, query, cache, failure orchestration은 변경하지 않는다.

### `feature:settings`

- grouped BrandFilter option list와 exact order
- `ALTEUL` 설명과 shared logo mapping 사용
- 기존 selected radio semantics와 domain update action 유지

### `app`

- icon-only top-level item 조립
- 접근성 이름, selected/disabled semantics, stable test tag 유지

navigation route, save/restore policy, coordinate payload는 변경하지 않는다.

## Data And Interaction Flow

```text
Filter chip tap
  -> feature local menu opens
  -> user selects one option
  -> menu dismisses
  -> existing StationListAction
  -> existing domain:settings update use case
  -> UserPreferences flow updates
  -> StationList active query refreshes by existing policy
  -> summary and rows re-render from filtered typed results
```

알뜰 선택 시 flow는 같고 `StationQuery.brandFilter`가 `ALTEUL`을 전달한다. `data:station`의 existing assembler가 `BrandFilter.matches`를 호출해 세 알뜰 identity를 포함한다. network request, cache key, database query는 추가하지 않는다.

## Accessibility And Responsive Behavior

- 모든 filter row, navigation item, bookmark action은 최소 48dp target을 갖는다.
- selected menu option은 color뿐 아니라 check와 selected semantics를 제공한다.
- icon-only navigation은 visible text가 없어도 content description과 selected state를 제공한다.
- rise/fall은 color, arrow, amount를 함께 사용한다.
- neutral price states는 서로 다른 명시적 문구를 사용한다.
- 한국어와 영어 visible/accessibility copy는 resource로 관리한다.
- 320dp width와 200% font scale에서 price, station name, summary, menu label을 clip하지 않는다.
- 큰 글꼴에서 summary와 row가 높아지고 menu가 scroll되는 것은 허용한다.

## Error And Edge Cases

- filter menu가 screen edge에 가까우면 popup position과 width를 안쪽으로 clamp한다.
- anchor가 recomposition 중 사라지거나 body state가 바뀌면 open menu를 dismiss한다.
- 선택 중 refresh가 시작돼도 기존 cached rows를 유지하는 정책을 바꾸지 않는다.
- 알뜰 선택 결과가 0개면 기존 filtered empty guidance를 표시한다.
- stored BrandFilter가 legacy 알뜰 name이면 `ALTEUL`, 알 수 없는 name이면 `ALL`로 복원한다.
- price history가 unavailable일 때 상승·하락 색이나 arrow를 추정하지 않는다.
- singleton result는 존재하지 않는 평균이나 절감액 placeholder를 표시하지 않는다.

## Testing Strategy

### Pure And Data Tests

- `BrandFilter.ALTEUL`이 RTO, RTX, NHO만 포함하는지 검증
- exact BrandFilter identity와 display order 검증
- stored RTO, RTX, NHO가 모두 ALTEUL로 복원되는지 검증
- ALTEUL round-trip과 unknown-value ALL fallback 검증
- existing summary zero, singleton, rounding, tied-minimum tests 유지
- Unavailable, Unchanged, Increased, Decreased UI projection 검증

### Compose Tests

- summary가 default font에서 two-row contract를 지키는지 검증
- singleton summary가 평균과 절감액을 표시하지 않는지 검증
- 반경·유종·브랜드 chip이 같은 menu surface를 열고 기존 action을 전달하는지 검증
- outside tap/back dismiss와 one-menu-at-a-time 동작 검증
- selected menu row의 check, selected semantics, 48dp target 검증
- brand menu와 Settings detail에서 ALTEUL 단일 option, shared logo, ETC last 검증
- station row에서 visible `가격`과 `-`가 없고 네 price-history 상태가 구분되는지 검증
- bottom navigation visible label이 없고 접근성 이름, selected state, stable tag가 남는지 검증
- 320dp width와 200% font scale에서 clipping이 없는지 검증

### Screenshot And Regression Coverage

Roborazzi snapshot을 다음 상태에 맞게 갱신하거나 추가한다.

- Station List populated light/dark with compact summary and icon-only navigation
- radius menu open
- fuel menu open
- brand menu open with grouped ALTEUL and ETC last
- Settings BrandFilter detail light/dark
- price history unavailable, unchanged, rise, fall representative rows

최소 관련 검증 범위는 `core:model`, `core:designsystem`, `data:settings`, `data:station`, `feature:station-list`, `feature:settings`, `app`의 unit/Compose/Roborazzi test와 `verifyModuleBoundaries`다. demo/prod build와 기존 connected selector 계약도 유지되는지 확인한다.

## Non-Goals

- 정렬 interaction을 menu로 변경하지 않는다.
- Settings detail을 popup menu로 변경하지 않는다.
- 실제 station `Brand` identity를 합치지 않는다.
- 검색 반경, 유종, 정렬, cache key, retry, stale 정책을 변경하지 않는다.
- 새 navigation destination, external map behavior, bookmark 정책을 추가하지 않는다.
- 새로운 dependency나 생성형 brand asset을 추가하지 않는다.

## Acceptance Criteria

- 기본 Station List에서 summary가 2줄로 읽히고 첫 row가 현재보다 위에서 시작한다.
- 모든 filter popup이 승인된 아이보리/검정/노랑 visual contract를 공유한다.
- 주변과 설정에서 알뜰 option은 하나이며 세 알뜰 brand를 모두 포함한다.
- 자가상표는 주변과 설정 브랜드 option의 마지막이다.
- station row에 visible `가격`과 `-`가 없다.
- icon-only bottom navigation이 시각적으로 간결하면서 screen reader와 test automation에서 세 destination을 구분한다.
- 기존 사용자 알뜰 설정, demo/prod 검색, 캐시, 위치, bookmark, 외부 지도 흐름에 회귀가 없다.
