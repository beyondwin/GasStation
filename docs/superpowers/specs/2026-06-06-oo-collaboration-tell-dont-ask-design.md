# GasStation 객체지향 협력 정리 (묻지 말고 시켜라 · 변경 보호) 설계

> 작성일: 2026-06-06
> 기준 커밋: `66129eb`
> 범위: `StationPriceDelta` 책임 이동, 가격/거리 표기 단일화, `StationQuery.toCacheKey` 매직넘버 캡슐화, UI 모델 불변식 대칭화
> 사용자 플로우 영향: 없음 (목록/watchlist 카드의 화면 출력 문자열·색조·정렬·캐시 키 동작 모두 불변)
> 짝 구현 plan: `docs/superpowers/plans/2026-06-06-oo-collaboration-tell-dont-ask.md`
> 근거 강의(인프런, 조영호 "오브젝트"):
> - 오브젝트 설계 원칙편 6-1 「디미터 법칙과 묻지 말고 시켜라 원칙」 (courseId 336658 / unitId 280443)
> - 오브젝트 기초편 4-5 「결합도 낮추기 - 변경 보호」 (courseId 334416 / unitId 234578)
> - 오브젝트 기초편 5-2 「메시지와 메서드의 분리」 (courseId 334416 / unitId 234582)

## 목표

조영호 "오브젝트" 강의의 네 가지 원칙 — **묻지 말고 시켜라(Tell, Don't Ask)**, **변경 보호 패턴**, **디미터 법칙**, **다형적 메시지** — 을 기준으로, 현재 코드에 흩어진 동일 책임의 중복과 캡슐화 누수를 제거한다. 네 개의 독립 트랙으로 구성하며 각 트랙은 독립적으로 commit 가능하다. **어떤 트랙도 사용자 대면 출력(라벨 문자열, 색조, 정렬 순서, 캐시 키 값)을 바꾸지 않는다.** 이 작업은 동작을 바꾸는 것이 아니라 동일 동작을 더 좋은 책임 배치로 옮기는 순수 리팩터링이다.

## 배경: 탐색에서 확인한 사실

2026-06-06 실제 파일 확인 결과:

1. **`StationPriceDelta` 해석 로직이 소비처에 복제돼 있다(묻지 말고 시켜라 위반).** 도메인 sealed 타입 `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationPriceDelta.kt`는 데이터만 들고 있고, "이 delta가 상승/하락/중립 중 무엇이냐"를 두 feature가 각자 `when`으로 캐묻는다.
   - `feature/station-list/.../StationListItemUiModel.kt`의 `private fun StationPriceDelta.toLabel()`·`toTone()`
   - `feature/watchlist/.../WatchlistItemUiModel.kt`의 `private fun StationPriceDelta.toLabel()`·`internal fun StationPriceDelta.toTone()`
   네 개의 `when`이 동일한 "어떤 하위 타입이 상승/하락/중립인가"라는 지식을 중복 보유한다. delta 변형이 추가되면 네 곳을 모두 고쳐야 한다. 강의 6-1: *"객체에게 질문한 후 그 답을 이용해 상태/표현을 정하는 로직이 있다면 그 로직을 그 객체의 책임으로 옮겨라."*

2. **가격/거리 표기 포맷이 두 feature에 글자까지 복제돼 있다(변경 보호 부재 · DRY 위반).** `toPriceLabel`, `toGroupedDigits`, `toDistanceLabel`, `toDistanceNumberLabel`, 원화 단위 상수 `원`가 `StationListItemUiModel.kt`·`WatchlistItemUiModel.kt` 양쪽에 동일하게 존재한다. AGENTS.md의 제품 불변식 "Price is the hero"를 가진 앱인데 가격 표기 규칙이 두 출처로 갈라져 있어, 한쪽만 고치면 화면별로 어긋난다. 강의 4-5: *"변하는 부분을 식별하고 그 주변에 안정적인 추상화를 둔다."*

3. **표기 포맷이 값 객체 내부를 reach-through 한다(디미터 법칙).** UI 모델 생성자가 `entry.station.price.value`, `summary.station.distance.value`처럼 `Station`을 관통해 `MoneyWon`/`DistanceMeters`의 `.value` 원시값을 꺼내 밖에서 가공한다. `MoneyWon`/`DistanceMeters`가 자기 표기를 모르는 빈약한 값이라 발생하는 문제다(2번과 동일 뿌리). 강의 6-1: *"오직 하나의 도트만 사용하라."*

4. **`StationQuery.toCacheKey()`에 의미 불명 매직넘버가 안정적 값 객체 안에 박혀 있다(변경 보호 · 숨은 제약).** `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationQuery.kt`가 위·경도→미터 변환에 `111_000`, `88_800`을 주석 없이 하드코딩한다. `88_800`은 "한국 위도(약 37도)에서 경도 1도당 미터" 근사치라는 **숨은 제약**인데 어디에도 명시돼 있지 않다. 캐시 버킷팅(변하기 쉬운 정책)이 도메인 값 객체에 결합돼 있다.

5. **두 UI 모델의 불변식이 비대칭이다(일관성 결함).** `WatchlistItemUiModel`은 `init`에서 `require(priceNumberLabel.isNotBlank())` 등 split metric 라벨의 공백 금지 불변식을 갖고, 이를 검증하는 테스트(`direct constructor rejects blank split metric labels`)도 있다. 동일 필드를 가진 `StationListItemUiModel`에는 불변식이 없다. 같은 데이터의 계약이 화면마다 다르다.

6. **기존 테스트가 출력 문자열을 정확히 고정하고 있다(= 안전망 존재).** `feature/station-list/.../StationListItemUiModelTest.kt`와 `feature/watchlist/.../WatchlistItemUiModelTest.kt`가 `"32원"`, `"-"`, `"1,689원"`, `"1,689"`, `"0.3km"`, `PriceDeltaTone.Rise` 등을 단언한다. `domain/.../StationPriceDeltaTest.kt`, `StationQueryCacheKeyTest.kt`도 마찬가지다. 따라서 리팩터링의 정확성은 "이 테스트들이 수정 없이 그대로 통과"로 검증된다. **이 테스트들의 단언값은 절대 바꾸지 않는다.**

7. **모듈 경계가 책임 이동의 목적지를 이미 규정한다.**
   - `domain:station`은 `core:model`에만 의존한다(`docs/module-contracts.md:24`). delta의 도메인 의미(상승/하락/중립)는 여기에 둘 수 있다.
   - `core:designsystem`은 `core:model`에 의존하며 이미 `BrandLabels.kt`에서 `Brand.gasStationBrandLabel()` 같은 **표시 매핑 확장 함수**를 소유한다(`docs/module-contracts.md:29`). 값 객체의 표시 포맷도 동일 성격이라 여기가 정당한 집이다.
   - 두 feature 모두 `core:designsystem`과 `core:model`에 의존한다(build.gradle.kts 확인). 따라서 공유 포맷터를 designsystem에 두면 두 feature가 그대로 끌어 쓸 수 있다.

8. **`DomainContractSurfaceTest`가 `StationPriceDelta`의 permitted subclass 집합을 정확히 고정한다.** `{Unavailable, Unchanged, Increased, Decreased}`만 허용한다. 따라서 Track A는 **새 하위 타입을 추가하면 안 되고**, sealed 타입에 프로퍼티/중첩 enum만 추가한다(중첩 enum은 permitted subclass가 아니라 이 테스트에 영향 없음).

## 비목표 (Out of Scope)

- 두 feature의 tone enum(`PriceDeltaTone`, `WatchlistPriceDeltaTone`)을 하나로 통합하는 것. watchlist tone은 `toColor()`를 갖는 등 화면별로 다르게 진화 중이므로 각 feature가 자기 enum을 유지하되, "어떤 delta가 어떤 방향인가"라는 **도메인 지식만** 한 곳으로 모은다.
- delta 라벨 텍스트("20원"/"-")를 designsystem이나 domain이 만드는 것. 라벨 텍스트는 화면 표현이고 designsystem은 `StationPriceDelta`(domain 타입)를 알 수 없으므로, 텍스트 조립은 feature에 남기고 feature는 도메인의 `amountWonOrNull` + designsystem의 원화 포맷터만 조합한다.
- `StationQuery.toCacheKey`의 버킷팅 알고리즘 자체나 캐시 키 값 변경. 상수의 **명명·문서화**만 한다(값은 동일).
- `MoneyWon`/`DistanceMeters`에 도메인 행위를 추가하는 것. 값 객체는 `core:model`에 두되 **표시 포맷은 정책/표현**이므로 `core:model`이 아니라 `core:designsystem`에 둔다(`core:model`은 "앱 정책 금지").
- 사용자 대면 동작 변경, 모듈 그래프 재배선, 새 화면/기능.

---

## Track A: `StationPriceDelta` 책임 이동 (묻지 말고 시켜라 · 다형적 메시지)

**소유:** `domain:station`

**문제:** delta의 방향(상승/하락/중립)과 변동액 추출을 소비처가 `when`으로 캐묻고, 그 지식이 네 곳에 중복된다.

### 설계

`StationPriceDelta`가 스스로를 표현하도록 두 가지 멤버를 추가한다.

- 중첩 enum `PriceDirection { RISE, FALL, NEUTRAL }`.
- `val direction: PriceDirection` — `Increased`→RISE, `Decreased`→FALL, `Unavailable`/`Unchanged`→NEUTRAL.
- `val amountWonOrNull: Int?` — `Increased`/`Decreased`는 `amountWon`, 그 외 `null`.

이로써 "어느 하위 타입이 어느 방향인가"라는 지식이 **도메인 한 곳**에만 존재한다(다형적 메시지로 통일). permitted subclass는 그대로이므로 `DomainContractSurfaceTest`는 영향 없다.

### 산출물

- `StationPriceDelta.kt`에 `PriceDirection`, `direction`, `amountWonOrNull` 추가.
- `StationPriceDeltaTest.kt`에 세 변형 × `direction`/`amountWonOrNull` 단언 추가.

**완료 기준:** `:domain:station:test` 통과. `direction`/`amountWonOrNull`이 4변형 모두에 대해 명세대로 동작.

---

## Track B: 가격/거리 표기 단일화 (변경 보호 · 디미터 법칙 · DRY)

**소유:** `core:designsystem`

**문제:** 가격/거리 포맷 함수와 원화 단위 상수가 두 feature에 복제돼 있고, `.value` reach-through로 값 객체 내부를 노출한다.

### 설계

`core:designsystem`에 값 객체 표시 포맷터를 `BrandLabels.kt`와 동일한 확장 함수 스타일로 단일 출처화한다. 신규 파일 `ValueFormats.kt`:

- `const val GAS_STATION_WON_UNIT = "원"`, `const val GAS_STATION_DISTANCE_UNIT = "km"`
- `fun MoneyWon.gasStationPriceDigits(): String` → `DecimalFormat("#,###")` (예: `1689`→`"1,689"`)
- `fun MoneyWon.gasStationPriceLabel(): String` → `"${digits}${WON}"` (예: `"1,689원"`)
- `fun DistanceMeters.gasStationDistanceDigits(): String` → `DecimalFormat("#,##0.0").format(value / 1000.0)` (예: `300`→`"0.3"`)
- `fun DistanceMeters.gasStationDistanceLabel(): String` → `"${digits}km"` (예: `"0.3km"`)

확장 수신자를 값 객체로 둬서 호출부가 `.value`를 직접 만지지 않게 한다(디미터 완화). 출력 문자열은 현재 동작과 **바이트 단위로 동일**해야 한다(기존 feature 테스트가 보증).

### 산출물

- 신규 `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/ValueFormats.kt`.
- 신규 `core/designsystem/src/test/kotlin/com/gasstation/core/designsystem/ValueFormatsTest.kt`.

**완료 기준:** `:core:designsystem:testDebugUnitTest` 통과. 포맷터가 기존 라벨 문자열과 동일 출력.

---

## Track C: feature UI 모델 정리 (Track A·B 소비 · 중복 제거 · 불변식 대칭)

**소유:** `feature:station-list`, `feature:watchlist`

**문제:** 두 UI 모델이 자체 포맷 함수와 delta `when`을 들고 있고, 불변식이 비대칭이다.

### 설계

- 두 UI 모델 생성자가 designsystem 포맷터(`gasStationPriceLabel` 등)와 도메인 멤버(`direction`, `amountWonOrNull`)를 사용하도록 변경한다.
- 각 feature의 `private fun ...toLabel()`·`toTone()`과 `private fun Int.toPriceLabel()`·`toGroupedDigits()`·`DistanceMeters.toDistance*` 중복을 제거한다.
- delta 라벨: `delta.amountWonOrNull?.let { "$it${GAS_STATION_WON_UNIT}" } ?: "-"` (현재 출력과 동일).
- delta tone: 각 feature가 `delta.direction`을 자기 tone enum으로 매핑(`when(direction)` 3분기, 단일 출처는 domain).
- `StationListItemUiModel`에 `WatchlistItemUiModel`과 동일한 split metric 불변식(`require(...isNotBlank())`)을 추가해 대칭화한다(Track 5번 결함 해소). 포맷터 출력은 항상 non-blank이므로 정상 경로 영향 없음.
- tone enum 자체(`PriceDeltaTone`/`WatchlistPriceDeltaTone`)와 `toColor()`는 그대로 둔다(비목표).

### 산출물

- `StationListItemUiModel.kt`, `WatchlistItemUiModel.kt` 리팩터링.
- 기존 `StationListItemUiModelTest.kt`·`WatchlistItemUiModelTest.kt`는 **수정 없이** 그대로 통과해야 한다(회귀 안전망).

**완료 기준:** `:feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest`가 테스트 단언값 변경 없이 통과. 두 UI 모델에 가격/거리/delta 포맷 중복 코드가 0.

---

## Track D: `StationQuery.toCacheKey` 매직넘버 캡슐화 (변경 보호 · 숨은 제약 명시)

**소유:** `domain:station`

**문제:** `111_000`/`88_800`이 의미 없이 박혀 있고, `88_800`의 "한국 위도 근사" 제약이 숨어 있다.

### 설계

값은 유지하되 의미를 드러낸다.

- companion에 named const 추가: `private const val METERS_PER_LATITUDE_DEGREE = 111_000` (위도 1도 ≈ 111km, 전 지구 공통).
- `private const val METERS_PER_LONGITUDE_DEGREE_KR = 88_800` + 한 줄 주석: 경도 1도당 미터는 위도에 따라 달라지며, 이 값은 한국 위도(약 37도) 근사치라는 제약을 명시.
- `toCacheKey` 본문이 이 상수를 참조하도록 치환. 산출 캐시 키 값은 불변(`StationQueryCacheKeyTest`가 `16649`/`45120`을 고정하므로 그대로 통과).

### 산출물

- `StationQuery.kt`에 named const + 제약 주석.

**완료 기준:** `:domain:station:test` 통과(캐시 키 값 불변). 매직넘버가 명명된 상수와 제약 주석으로 대체됨.

---

## 검증 (전체)

```bash
./gradlew \
  :domain:station:test \
  :core:designsystem:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  verifyModuleBoundaries
```

`verifyModuleBoundaries`로 책임 이동이 모듈 경계를 깨지 않았음을 확인한다(designsystem→core:model, feature→designsystem/domain은 모두 허용 엣지).

## 트랙 간 의존

- Track C는 Track A·B의 산출물을 소비하므로 **A·B 이후** 실행한다.
- Track A·B·D는 서로 독립이다.
- 권장 순서: A → B → C → D. 각 트랙 종료 시 해당 모듈 테스트로 commit 단위를 끊는다.
