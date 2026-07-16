# Full-App Urban Signal UI Redesign

## Goal

GasStation의 주변 주유소, 관심 주유소, 설정, 설정 상세, 공통 상태 화면을 하나의 프로덕션급 UI 체계로 재설계한다. 사용자는 현재 위치 주변의 주유소를 가격, 거리, 브랜드, 유종, 가격 변화, 저장 상태 기준으로 빠르게 비교할 수 있어야 한다.

이 디자인의 성공 기준은 다음과 같다.

- 가격이 모든 주유소 행에서 첫 번째 시선이 된다.
- 노란색, 검은색, 흰색이라는 기존 정체성을 유지하되 노란색을 화면 배경이 아니라 결정 신호로 사용한다.
- 주변, 관심, 설정의 최상위 이동은 항상 같은 하단 내비게이션으로 이해된다.
- 관심 주유소는 카드 모음이 아니라 고밀도 비교 목록으로 읽히며, 기본 글꼴과 일반적인 800dp급 화면 높이에서 5개 행이 보인다.
- 실제 브랜드 자산이 주변 목록, 관심 목록, 브랜드 설정 상세에서 같은 규칙으로 표시된다.
- 권한, GPS, 로딩, stale, 빈 결과, 실패 상태가 정상 목록과 같은 시각 언어를 사용한다.
- `demo`와 `prod`, 접근성 semantics, test tag, 외부 지도 handoff가 유지된다.

## Context

현재 앱은 강한 노란 배경, 굵은 검은 외곽선, 독립 카드 반복으로 브랜드 정체성은 분명하지만, 화면의 대부분이 같은 강도로 강조되어 가격 비교 속도가 느려진다. 특히 관심 주유소는 한 항목이 여러 metric/card 블록으로 구성되어 일반 화면에서 약 3개만 보이고, 저장 항목을 빠르게 비교하기 어렵다.

현재 코드에는 이미 재사용 가능한 색상, 타이포그래피, spacing, top bar, row, metric, 상태 컴포넌트가 있고, 브랜드 PNG와 `Brand` 매핑도 복원되어 있다. 이번 작업은 새 미학을 별도 계층에 덧붙이는 것이 아니라 `core:designsystem`의 공통 chrome을 정리하고 각 feature가 동일한 primitive를 사용하도록 만드는 전면 UI 갱신이다.

이 문서는 `2026-04-18-settings-detail-hierarchy-design.md`와 `2026-04-19-brand-icon-restoration-design.md`의 시각 배치 계약을 대체한다. 기존 문서의 settings 저장 흐름, radio semantics, 브랜드 resource mapping과 접근성 목적은 유지한다. 구현 시 `docs/agent-workflow.md`와 `docs/test-strategy.md`에 남은 watchlist icon+label/card 설명도 이 고밀도 row 계약에 맞춰 갱신한다.

## Reference Principles

2026년 디자인 방향을 조사하면서 다음 원칙을 가져온다. 특정 앱의 화면을 복제하지 않는다.

- [Apple Design Awards 2026](https://developer.apple.com/design/awards/)의 Tide Guide처럼 복잡한 실시간 정보를 명확한 위계와 제한된 색상으로 빠르게 읽게 한다.
- 같은 수상작군의 인터랙션 사례처럼 상태 변화와 선택에만 목적 있는 motion을 사용한다.
- [Material 3 Expressive](https://developer.android.com/develop/ui/compose/designsystems/material3)의 hierarchy, shape, motion 원칙을 따르되 generic Material card 모음처럼 보이지 않도록 GasStation의 검정·노랑 대비를 유지한다.

## Explored Directions

### A. Urban Signal

따뜻한 아이보리 canvas, 검은 chrome, 노란 decision signal, borderless comparison row를 사용하는 방향이다. 정보 속도와 GasStation 정체성의 균형이 가장 좋다.

### B. Price Instrument

가격 순위, 평균, 절감액을 계기판처럼 강하게 보여주는 방향이다. 의사결정은 빠르지만 화면 전체가 분석 도구처럼 딱딱해질 위험이 있다.

### C. Expressive Utility

큰 shape와 전환 motion으로 친근함을 높이는 방향이다. 상태 변화는 잘 보이지만 장식이 가격 비교를 방해할 수 있다.

### Chosen Direction

Urban Signal을 기본으로 하고 Price Instrument의 평균·최저가·절감 맥락을 요약 strip에만 도입한다. Expressive Utility의 motion은 refresh, 선택, 행 제거처럼 상태가 실제로 바뀌는 지점에만 제한한다.

## Visual System

### Color

Light theme의 기본 화면 canvas는 `ColorSurface`(`#FFFCF2`)다. 현재 `GasStationBackground`와 Material background가 사용하는 전면 노란색을 아이보리 surface로 바꾼다.

- Brand anchor: `ColorYellow` `#FFDC00`
- Primary text/chrome: `ColorBlack` `#222222`
- App canvas: `ColorSurface` `#FFFCF2`
- Raised summary/guidance surface: `ColorSurfaceRaised` `#FFF8DC`
- Divider: `ColorNeutralLine` `#D4CBAE`, 1dp
- Muted text: `ColorNeutralMuted` 또는 `ColorNeutralSubtle`
- Positive/negative/information 상태: 기존 support color를 유지하고 아이콘·문구를 함께 사용한다.
- 브랜드 로고 tile: 로고 원색을 보존하기 위한 실제 흰색 `#FFFFFF`

노란색은 선택된 tab, 저장 상태, 최저가, focus/selection, 진행 상태에만 쓴다. 큰 본문 배경, 모든 카드 외곽선, 장식용 gradient에는 쓰지 않는다. Dark theme은 기존 black/yellow identity를 유지하되 정보 위계와 component 구조는 light theme과 동일하게 유지한다.

### Typography

새 font dependency는 추가하지 않는다. 기존 `GasStationTypography`를 확장하거나 역할을 재배치한다.

- Top bar title: 22sp, ExtraBold
- Main station name: 17sp, Bold
- Watchlist station name: 16sp, Bold
- Main price: 32sp, Black, tabular numbers
- Watchlist price: 28sp, Black, tabular numbers
- Distance: 18sp 또는 기존 metric value 역할
- Body: 16sp
- Meta/chip: 12–13sp

가격 숫자와 평균·거리 같은 metric에는 `tnum`을 유지한다. 단위는 숫자보다 작게 baseline을 맞춘다. 가격보다 큰 브랜드 로고, 제목, badge를 만들지 않는다.

### Shape, Spacing, And Elevation

- 화면 좌우 기본 inset: 16dp
- 연관 정보 간격: 4/8/12dp token
- section 간격: 16/24dp token
- summary strip corner: 14–16dp
- logo tile corner: 12–14dp
- row 구분: neutral 1dp divider
- 최소 interactive target: 48dp

개별 주유소 행에는 외곽 카드와 elevation을 사용하지 않는다. card surface는 summary, permission/GPS/failure guidance처럼 하나의 독립 메시지를 묶을 때만 사용한다.

### Motion

- 화면 전환: 기존 140–220ms fade/short slide 범위 유지
- refresh rail과 stale banner: 150–220ms 등장/퇴장
- bookmark 선택 또는 제거: 180–220ms color/size/row placement transition
- bottom navigation 선택: 색상과 미세한 icon emphasis만 사용

연속 bounce, 장식용 parallax, 긴 spring, 목록 전체 stagger는 사용하지 않는다. 시스템의 animation scale이 꺼져 있으면 의미 전달이 animation에 의존하지 않아야 한다.

## Shared App Chrome And Navigation

`app`의 `GasStationNavHost`가 최상위 scaffold와 하단 내비게이션을 조립한다. app은 route와 외부 handoff만 소유하고 화면 정책은 소유하지 않는다.

Top-level destination은 다음 세 개다.

1. `주변` — StationList
2. `관심` — Watchlist
3. `설정` — Settings

주변, 관심, 설정에서는 bottom navigation을 표시하고 현재 tab을 노란색으로 표시한다. top-level 이동은 `launchSingleTop`, `saveState`, `restoreState`, start destination까지의 `popUpTo`를 사용해 tab별 scroll과 화면 상태를 보존한다.

SettingsDetail은 pushed destination이다. 상세 화면에서는 bottom navigation을 숨기고 검은 top bar의 뒤로가기 버튼으로 Settings에 복귀한다. Settings와 Watchlist의 기존 닫기 버튼은 top-level 화면에서 제거한다.

Watchlist의 거리 계산 기준 좌표는 기존 navigation argument와 `SavedStateHandle` 계약을 유지한다. `GasStationNavHost`는 StationList가 전달한 최신 좌표를 navigation payload로만 기억한다. 좌표가 아직 없는 동안 `관심` item은 화면에서 사라지지 않지만 disabled semantics와 `현재 위치 확인 후 사용 가능` 설명을 제공하고, StationList의 기존 권한/GPS recovery가 문제 해결의 단일 경로가 된다. app이 별도 위치 조회나 비즈니스 정책을 소유하지 않는다.

## Screen Designs

### 1. Station List — Nearby

#### Header And Filter Rail

검은 top bar에는 `주변 주유소` 제목과 refresh action만 둔다. 기존 top bar의 관심·설정 아이콘은 bottom navigation과 중복되므로 제거한다.

top bar 아래에는 현재 주소와 compact filter rail을 둔다.

- 정렬: 거리순/가격순
- 반경: 3km/4km/5km
- 유종
- 브랜드

정렬은 현재 `SortToggleRequested` 흐름을 사용한다. 반경, 유종, 브랜드 chip은 anchored single-choice menu를 열고 명시적인 `domain:settings` update use case를 통해 값을 쓴다. feature가 settings repository 또는 DataStore를 직접 호출하지 않는다. 값이 변경되면 기존 active query refresh 규칙을 그대로 따른다.

chip에는 현재 선택값이 항상 보이고, 선택 상태를 색상만으로 표현하지 않는다. 긴 브랜드명은 한 줄 ellipsis 처리하고 menu option에는 전체 label을 제공한다.

#### Decision Summary Strip

필터 아래의 검은 summary strip은 현재 화면에 표시되는 결과만 요약한다.

- 결과 개수
- 최저 가격
- 평균 가격
- 최저가와 평균의 차이

2개 이상일 때 평균은 표시된 station의 `MoneyWon.value` 산술평균을 1원 단위로 반올림한다. 정확히 0.5원인 양수 결과는 올림한다. 1개일 때는 개수와 해당 가격만 보여 주고 평균 대비 절감액을 만들지 않는다. 0개일 때 summary strip을 숨기고 empty guidance를 표시한다. 최저가가 여러 개면 `공동 최저가`로 표시한다.

feature UI model에 typed numeric price를 보존하거나 별도 pure projection model을 만든다. 이미 포맷된 `1,712` 문자열을 다시 parse해서 계산하지 않는다. domain, repository, cache schema는 변경하지 않는다.

#### Station Comparison Rows

주유소는 card가 아닌 연속 comparison row로 표시한다.

정보 순서는 다음과 같다.

1. 실제 브랜드 logo tile
2. 가격과 원 단위
3. 거리
4. 역명
5. 유종, 브랜드 접근성 정보, 가격 변화, freshness 맥락
6. 48dp bookmark action

main row는 기본 글꼴에서 약 120–132dp를 목표로 한다. logo tile은 50dp, 실제 로고는 최대 38dp다. price는 32sp로 가장 강하고, distance는 두 번째 metric이다. 역명은 최대 2줄이며 긴 이름 때문에 가격 또는 bookmark가 밀려나지 않는다. 각 행은 neutral divider로 구분한다.

행 탭은 기존 `StationClicked` effect를 통해 설정된 외부 지도 앱을 연다. 앱 내부 지도 화면은 만들지 않는다. bookmark는 기존 selected/state semantics를 유지한다.

### 2. Watchlist — Saved Comparison

Watchlist는 StationList의 복제 화면이 아니라 저장 항목을 현재 가격, 변화, 거리, 마지막 확인 시점으로 비교하는 화면이다.

top bar에는 `관심 주유소` 제목만 둔다. 별도 refresh session이 없는 현재 watchlist 계약에 맞춰 refresh나 닫기 action을 추가하지 않는다. 그 아래 compact summary strip에는 `저장한 N곳`, 평균 가격, 최근 확인 맥락을 표시한다. 평균은 StationList와 같은 typed numeric/rounding 규칙을 사용한다.

#### Dense Row Contract

기본 글꼴, 1.0 display scale, 일반적인 800dp급 사용 가능 화면 높이에서 summary strip과 bottom navigation 사이에 5개 complete row가 보여야 한다.

- visual row height target: 108–116dp
- logo tile: 44dp
- logo maximum: 34dp
- station name: 16sp, 1줄 ellipsis
- price: 28sp, 가장 강한 요소
- meta: price delta 또는 `변동 없음`, distance, last checked
- bookmark action target: 48dp
- row padding: 세로 12–16dp 범위
- row 간 독립 card, 12dp card gap, 중첩 metric block 금지

화면이 더 작거나 font scale이 커지면 행 높이를 강제로 고정하지 않는다. 텍스트 clipping보다 접근성을 우선해 row가 자연스럽게 커지고 목록이 scroll된다. `한 화면 5개`는 기본 환경의 density acceptance criterion이지 접근성 환경의 강제 조건이 아니다.

노란 bookmark action은 저장 상태를 나타내며 탭하면 기존 `UpdateWatchStateUseCase`로 저장을 해제한다. 제거 시 인접 행이 짧게 재배치되며 snackbar 또는 undo 같은 새 정책은 이번 범위에 추가하지 않는다. 행 자체에 새 navigation 행동을 추가하지 않으며, 동작 없는 decorative chevron은 구현하지 않는다.

빈 상태는 같은 ivory canvas 위에 저장 방법과 비교 목적을 짧게 안내하고 `주변` tab으로 이동할 수 있는 명확한 action을 제공한다.

### 3. Settings Overview

Settings는 top-level tab이므로 닫기 버튼을 제거하고 bottom navigation의 `설정`을 선택 상태로 표시한다.

기존 정보 구조는 유지한다.

- 탐색 설정: 찾기 범위, 유종, 주유소 브랜드
- 표시 설정: 정렬 기준
- 연결 설정: 길찾기 앱

각 group은 section heading과 borderless rows로 구성한다. 전체 group을 두꺼운 외곽 card로 감싸지 않는다. row에는 제목, 현재값, 한 줄 설명, chevron을 두고 현재값을 오른쪽 또는 제목 다음의 명확한 secondary emphasis로 표시한다. 화면 폭이 좁거나 font scale이 크면 현재값과 설명이 잘리지 않도록 세로로 재배치할 수 있다.

### 4. Settings Detail

SettingsDetail은 검은 top bar, 뒤로가기, section title을 사용하고 bottom navigation을 숨긴다. section 설명은 option list 위에 짧은 본문으로 한 번만 표시한다. 본문은 하나의 option list이며 `설명 카드 + 옵션 카드 N개` 구조를 사용하지 않는다.

- option row 전체가 48dp 이상 tap target
- title과 필요한 짧은 description
- 선택된 option은 trailing check와 `Role.RadioButton`, `selected=true`
- 선택 때문에 row 높이, corner, 외곽 구조가 바뀌지 않음
- 모든 설정 쓰기는 기존 명시적 domain update use case 사용

BrandFilter 상세의 `전체`는 text-only filter mode다. 구체 브랜드는 실제 logo tile, label, description을 표시한다.

## Brand Asset Contract

`core:model.Brand`, `core:designsystem/component/BrandIcon.kt`, `BrandLabels.kt`를 단일 출처로 유지한다.

| Brand code | Label | Drawable |
| --- | --- | --- |
| `SKE` | SK에너지 | `ic_ske` |
| `GSC` | GS칼텍스 | `ic_gsc` |
| `HDO` | 현대오일뱅크 | `ic_hdo` |
| `SOL` | S-OIL | `ic_sol` |
| `RTO` | 자영알뜰 | `ic_rtx` |
| `RTX` | 고속도로알뜰 | `ic_rtx` |
| `NHO` | 농협알뜰 | `ic_rtx` |
| `ETC` | 자가상표 | `ic_etc` |
| `E1G` | E1 | `ic_e1g` |
| `SKG` | SK가스 | `ic_skg` |

알뜰 3종은 공식 알뜰주유소 심벌을 공유하지만 label로 유형을 구분한다. 자가상표와 알 수 없는 외부 code의 fallback은 `ETC` 자산과 label을 사용한다. 로고는 자르기, 단색화, 임의 recolor, 생성형 대체 심벌을 금지한다.

StationList와 Watchlist에서는 중복 visible brand label을 별도로 반복하지 않고 station name과 logo를 사용한다. icon에는 의미 있는 브랜드 content description을 제공한다. Settings Detail은 선택 항목 식별을 위해 logo와 visible label을 함께 표시하므로 중복 screen reader announcement가 생기면 icon description을 `null`로 둔다.

## Shared States

### Permission And GPS

아이보리 canvas, 짧은 제목, 원인 설명, 검은 primary action을 사용하는 동일한 guidance pattern으로 표시한다. bottom navigation과 app chrome은 유지한다. 권한 거절과 GPS 꺼짐을 같은 문구로 합치지 않는다.

### Initial Loading

빈 card 중앙 spinner만 보여 주지 않는다. summary와 row 구조를 닮은 가벼운 skeleton 또는 현재의 guidance title/body를 사용하되, 실제 데이터처럼 오인할 가격 숫자는 만들지 않는다. 첫 usable content 보고 기준은 기존 정책을 유지한다.

### Refresh And Stale Data

캐시가 있으면 기존 목록을 유지한다. refresh rail 또는 compact status banner를 상단에 표시하고 content alpha를 과도하게 낮추지 않는다. stale 상태와 refresh 실패는 아이콘, 문구, 색을 함께 사용한다. `hasCachedSnapshot` 의미를 유지하고 cached content가 있는데 full-screen failure로 바꾸지 않는다.

### Empty And Blocking Failure

empty는 현재 조건에서 결과가 없음을 설명하고 필터 재설정 또는 재시도 action을 제공한다. blocking failure는 오류 원인과 재시도 action을 제공한다. 두 상태 모두 거대한 장식 illustration이나 generic empty-state art를 사용하지 않는다.

### Watchlist Empty

저장 항목이 없다는 사실, 주변 목록에서 bookmark를 누르는 방법, 관심 tab의 목적을 짧게 설명한다. bottom navigation의 `주변` 이동 action은 실제 click target과 semantics를 가져야 한다.

## Architecture And Ownership

### `core:designsystem`

- background, color, typography, spacing, divider, logo tile token
- shared top bar와 bottom navigation visual primitive
- comparison row의 재사용 가능한 visual building block
- shared summary/guidance/status primitive
- 브랜드 icon/label mapping

feature 전용 문구, 평균 계산, screen state 분기는 소유하지 않는다.

### `feature:station-list`

- filter rail state/action/menu
- typed station UI projection과 summary calculation
- nearby screen layout와 기존 permission/GPS/loading/stale/failure/empty 분기
- station click와 watch effect

설정 쓰기는 `domain:settings` use case를 통하고 위치 구현 또는 DataStore를 직접 호출하지 않는다.

### `feature:watchlist`

- saved comparison UI model, summary projection, dense row
- bookmark remove action과 짧은 row removal transition
- empty state

현재 위치를 새로 조회하거나 refresh session을 소유하지 않는다. 기준 좌표는 기존 navigation argument에서 받는다.

### `feature:settings`

- overview group/row와 detail option UI
- selected semantics와 브랜드 option 표시
- 기존 settings action/use case 연결

### `app`

- top-level scaffold와 navigation 조립
- tab state save/restore
- watchlist navigation coordinate payload
- 기존 `ExternalMapLauncher` handoff

app에는 평균 계산, filter 정책, watchlist 저장 정책을 두지 않는다.

### Domain And Data

새 repository, cache schema, network call, domain enum은 필요하지 않다. `MoneyWon`, settings update use case, `UpdateWatchStateUseCase`, 기존 station/watchlist repository 계약을 사용한다.

## Accessibility

- 모든 icon-only action과 option row는 최소 48dp target을 갖는다.
- 가격, 거리, 역명, 선택 상태의 의미를 색상만으로 전달하지 않는다.
- bookmark는 `selected`와 state description을 제공한다.
- settings option은 `Role.RadioButton`과 `selected`를 유지한다.
- bottom navigation은 label을 숨기지 않고 selected semantics를 제공한다.
- brand icon은 화면에 visible label이 없는 경우 브랜드 content description을 제공한다.
- ASCII test tag와 사용자용 한국어 content description을 분리한다.
- font scale 200%에서 가격, 역명, 현재 설정값, action이 clipping되지 않아야 한다. 고밀도 row는 이 환경에서 높이가 늘어날 수 있다.
- light/dark theme 모두 주요 text와 action이 WCAG AA 수준의 명도 대비를 충족해야 한다.

기존 semantics 또는 test tag를 제거해야 한다면 같은 사용자 계약을 보호하는 대체 selector와 테스트를 같은 변경에 추가한다.

## Testing Strategy

### Pure And ViewModel Tests

- StationList summary: 0개, 1개, 여러 개, 평균 반올림, 공동 최저가
- Watchlist summary: 0개, 1개, 여러 개, 평균 반올림
- 포맷된 label을 parse하지 않고 typed price로 계산하는 계약
- filter action이 명시적 settings use case를 호출하고 query refresh에 반영되는 흐름
- watchlist remove action이 `UpdateWatchStateUseCase`를 호출하는 흐름
- navigation coordinate가 없을 때 관심 tab disabled, 있을 때 watchlist route 생성

### Compose UI Tests

- price가 row의 primary metric이고 distance, station name, metadata가 유지됨
- 실제 브랜드 icon이 StationList, Watchlist, BrandFilter detail에 표시됨
- RTO/RTX/NHO는 알뜰 icon, ETC는 자가상표 icon 사용
- watchlist의 48dp bookmark semantics와 remove action
- Settings top-level에는 bottom navigation이 있고 닫기 버튼이 없음
- SettingsDetail에는 뒤로가기가 있고 bottom navigation이 없음
- permission, GPS, loading, stale, empty, blocking failure semantics
- 긴 역명, 큰 가격, 긴 설정값, font scale 확대에서 clipping 방지

### Screenshot And Device Coverage

Roborazzi 기준 이미지를 다음 상태에 추가하거나 갱신한다.

- StationList populated/stale/empty/permission/GPS/failure
- Watchlist 5개 populated와 empty
- Settings overview
- SettingsDetail 일반 option과 BrandFilter option
- light/dark theme의 대표 populated 화면

demo seed에는 주요 정유사뿐 아니라 알뜰과 자가상표 항목이 시각 검증 가능한 상태로 포함되어야 한다. 기존 connected demo flow와 benchmark selector는 bottom navigation 구조에 맞게 갱신한다.

구현 후 최소 관련 검증은 다음과 같다.

```bash
./gradlew \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  verifyModuleBoundaries \
  verifyRoborazziDebug \
  :app:assembleDemoDebug \
  :app:assembleProdDebug
```

기기 또는 에뮬레이터에서는 다음을 확인한다.

```bash
./gradlew :app:connectedDemoDebugAndroidTest
```

## Documentation Impact

구현과 함께 다음 live 문서를 점검한다.

- `README.md`: screenshots와 demo story
- `.impeccable.md`: 전체 화면 정보 위계와 token 계약
- `docs/architecture.md`: root navigation과 UI ownership
- `docs/state-model.md`: top-level navigation payload나 action 의미가 바뀌는 경우
- `docs/test-strategy.md`: bottom navigation, dense watchlist, screenshot coverage
- `docs/verification-matrix.md`: connected flow와 benchmark selector가 바뀌는 경우
- `docs/performance.md`: benchmark journey가 top-bar action에서 bottom navigation으로 바뀌는 경우

## Non-Goals

- 앱 내부 지도 또는 지도/목록 toggle 추가
- 새로운 backend, API, cache schema, station domain model 추가
- 로고 리브랜딩, vector 재제작, 생성형 로고 사용
- 새 font dependency 또는 전면적인 illustration system 추가
- 추천 알고리즘, 가격 예측, route optimization 추가
- watchlist 별도 refresh session 또는 별도 위치 조회 추가
- snackbar undo 같은 새로운 저장 정책 추가

## Acceptance Criteria

- 주변, 관심, 설정이 동일한 bottom navigation을 사용하고 SettingsDetail만 이를 숨긴다.
- light theme의 본문 canvas가 아이보리이며 yellow는 selection/decision signal로 제한된다.
- StationList는 decision summary와 borderless comparison row를 사용하고 가격이 가장 강한 요소다.
- Watchlist는 기본 환경에서 5개 complete row가 보이며, font scale 확대 시 clipping 없이 scroll된다.
- Watchlist row는 108–116dp visual target, 44dp logo tile, 34dp logo, 28sp price, 48dp bookmark target 계약을 지킨다.
- StationList, Watchlist, BrandFilter detail에 실제 브랜드 asset이 표시된다.
- 자영알뜰, 고속도로알뜰, 농협알뜰, 자가상표가 누락되지 않는다.
- Settings overview와 detail은 독립 card 반복이 아닌 grouped flat row hierarchy를 사용한다.
- permission, GPS, loading, stale, empty, failure가 같은 design system을 사용하고 cached content 유지 정책을 바꾸지 않는다.
- 기존 외부 지도, settings write, watch state, demo/prod, semantics/test tag 계약이 유지된다.
- 관련 unit, Compose, Roborazzi, assemble, module boundary, connected demo 검증이 통과한다.
