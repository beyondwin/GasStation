# Station List Query Context Summary Design

## Goal

주유소 리스트 상단의 `현재 조건` 영역을 리스트 카드와 같은 위계로 보이지 않게 낮추고, `6bbb36e` 시점에 제공하던 현재 위치 주소를 함께 복구한다.

이 영역은 조작 가능한 필터 카드가 아니라 "현재 어떤 위치와 조건으로 목록을 보고 있는지"를 알려주는 상태 요약이다.

## Current Problem

현재 `feature:station-list`의 `FilterSummary`는 `GasStationCard`를 사용한다. 바로 아래 주유소 항목들도 같은 카드 컴포넌트를 쓰기 때문에, `현재 조건` 요약이 실제 주유소 리스트 아이템과 같은 중요도로 읽힌다.

현재 표시는 다음 구조다.

- 카드 제목: `현재 조건`
- 설명: `반경과 유종 기준으로 정렬합니다.`
- 칩: `3km`, `휘발유`

내용은 보조 정보인데 카드 외곽선, 흰 배경, 패딩, 제목 위계가 주유소 카드와 같아 화면 위계가 어색하다.

## Legacy Behavior Reference

Commit `6bbb36e`의 이전 UI는 현재 위치 주소를 리스트 위에 표시했다.

- `HomeScreen`에서 `homeViewModel.currentAddress`를 수집했다.
- 주소 조회 성공 시 `CurrentAddresssText(address = ...)`를 렌더링했다.
- `CurrentAddresssText`는 주소를 리스트 위 중앙 정렬 텍스트로 표시했다.
- 예시 주소는 `서울 영등포구 당산동 194-32`였다.

현재 멀티모듈 구조에는 `StationListUiState.currentCoordinates`만 있고 주소 문자열은 없다. 이전 Kakao/Daum reverse geocoding 경로는 현재 코드에서 제거된 상태다.

## Product Decision

리스트 상단에는 카드가 아닌 얇은 `조회 기준 요약`을 둔다.

권장 표시:

```text
서울 영등포구 당산동 194-32
3km · 휘발유 기준
```

주소는 첫 줄에 둔다. 사용자가 "어디 기준으로 찾고 있는지"를 확인하는 핵심 컨텍스트이기 때문이다.

조건은 두 번째 줄에 텍스트로 낮춘다. 칩을 유지하면 다시 필터 컨트롤이나 카드형 콘텐츠처럼 보일 수 있으므로, `3km · 휘발유 기준`처럼 메타 텍스트로 표시한다.

`현재 조건` 제목과 `반경과 유종 기준으로 정렬합니다.` 설명문은 제거한다. 주소가 들어오면 두 줄만으로 의미가 충분하고, 설명문은 요약 영역을 다시 무겁게 만든다.

## UI Design

### Query Context Summary

새 컴포저블은 `FilterSummary`를 대체한다. 이름은 `QueryContextSummary`를 권장한다.

역할:

- 현재 주소가 있으면 첫 줄에 표시한다.
- 현재 반경과 유종을 두 번째 줄에 표시한다.
- 리스트 카드와 같은 `GasStationCard`를 사용하지 않는다.
- 화면 배경인 노란색 위에 직접 놓이는 가벼운 텍스트 블록으로 렌더링한다.

시각 규칙:

- 외곽 카드, 흰 배경, 검은 테두리 사용 금지
- 조건 칩 사용 금지
- 주소는 `cardTitle`보다 낮은 위계의 텍스트 스타일 사용
- 조건은 `meta` 계열 텍스트 스타일 사용
- 좌우 정렬은 리스트 카드와 맞추되, 콘텐츠 자체는 카드처럼 닫히지 않게 처리
- 세로 공간은 현재 카드보다 줄인다

권장 스타일:

- 주소: 검정, 한 줄 우선, 길면 2줄까지 허용하고 말줄임
- 조건: 진한 회색, 한 줄
- 두 줄 간격은 작게 유지

## Data Model

`StationListUiState`에 현재 위치 주소를 선택적으로 추가한다.

```kotlin
val currentAddressLabel: String? = null
```

주소는 화면 상태의 보조 필드다. 주유소 조회 쿼리나 캐시 키에는 포함하지 않는다.

## Domain Boundary

주소 조회는 위치 도메인의 읽기 기능으로 다룬다.

권장 도메인 계약:

- `LocationRepository`에 좌표를 주소 라벨로 변환하는 suspend 함수 추가
- `ReverseGeocodeLocationUseCase` 또는 `GetCurrentAddressUseCase` 추가
- 반환 타입은 성공/실패를 명확히 표현하는 sealed result 사용

주소 조회 실패는 주유소 목록 조회 실패가 아니다. 따라서 주소 실패가 리스트 로딩을 막으면 안 된다.

## Address Source

주소 라벨은 새 외부 API 연동을 되살리지 않고 `core:location`의 Android platform reverse geocoding 구현으로 만든다. 이렇게 하면 현재 제거된 Kakao/Daum DTO, API key, network service 경계를 다시 도입하지 않아도 된다.

주소 문자열 우선순위:

1. 도로명 주소를 만들 수 있으면 도로명 주소를 표시한다.
2. 도로명 주소가 없으면 지번/지역 기반 주소를 표시한다.
3. 표시 가능한 주소 조각이 없으면 주소 실패로 처리한다.

주소 라벨은 사람이 읽는 화면 텍스트다. station query, cache key, station repository, Opinet network 경로에는 전달하지 않는다.

## Data Flow

새로고침 흐름은 다음 순서를 따른다.

1. 현재 위치 권한/GPS 상태를 확인한다.
2. 현재 좌표를 가져온다.
3. 좌표를 `sessionState.currentCoordinates`에 반영한다.
4. 주유소 목록 조회 쿼리를 만들고 refresh를 수행한다.
5. 같은 좌표에 대해 주소 조회를 수행해 성공하면 `currentAddressLabel`을 갱신한다.

주소 조회는 주유소 목록 표시를 지연시키지 않는다. 구현은 병렬 실행 또는 별도 coroutine 실행을 선택할 수 있지만, 사용자 경험 기준은 "주소가 늦거나 실패해도 목록은 계속 보인다"이다.

좌표가 바뀌면 이전 주소가 새 위치의 주소처럼 보이지 않아야 한다. 새 좌표 refresh가 시작될 때 주소를 비우거나, 좌표와 주소를 함께 저장해 같은 좌표일 때만 표시한다.

## Error Handling

주소 조회 성공:

- 주소 첫 줄과 조건 두 번째 줄을 표시한다.

주소 조회 중:

- 로딩 문구를 따로 표시하지 않는다.
- 조건 요약만 표시한다.

주소 조회 실패:

- 스낵바를 띄우지 않는다.
- 실패 화면으로 전환하지 않는다.
- 조건 요약만 표시한다.

권한/GPS/현재 위치 실패:

- 기존 `StationListFailureReason`과 배너/실패 화면 정책을 유지한다.

주유소 refresh 실패:

- 기존 캐시 fallback과 blocking failure 정책을 유지한다.
- 주소 조회 실패와 refresh 실패를 섞지 않는다.

## Testing

Compose 테스트:

- 주소가 있으면 주소와 `3km · 휘발유 기준`이 표시된다.
- 주소가 없으면 조건 요약만 표시된다.
- `현재 조건` 텍스트가 더 이상 표시되지 않는다.
- `반경과 유종 기준으로 정렬합니다.` 텍스트가 더 이상 표시되지 않는다.

ViewModel 테스트:

- 현재 위치 성공 후 주소 조회 성공이면 `currentAddressLabel`이 갱신된다.
- 주소 조회 실패여도 주유소 목록 상태와 blocking failure가 실패로 바뀌지 않는다.
- 좌표가 바뀌는 refresh에서는 이전 주소가 새 조건 요약에 남지 않는다.

Domain/core location 테스트:

- 주소 조회 use case가 repository 결과를 그대로 전달한다.
- core location 구현이 platform reverse geocoding 결과에서 표시 가능한 주소 문자열을 만든다.
- 빈 응답이나 표시 가능한 주소 조각이 없는 결과는 주소 실패로 매핑된다.

## Non-Goals

- 설정 화면에서 반경/유종 변경 UX를 바꾸지 않는다.
- 정렬 토글 UX를 바꾸지 않는다.
- 주유소 카드 디자인을 바꾸지 않는다.
- 주소 조회 실패를 사용자에게 별도 오류로 노출하지 않는다.
- 주소 문자열을 station cache key나 station query에 포함하지 않는다.

## Risks

### Platform reverse geocoding 가용성

Android platform reverse geocoding은 기기/환경에 따라 결과가 비어 있거나 지연될 수 있다. 이 기능은 보조 컨텍스트이므로 실패 시 조건 요약만 표시하고 주유소 목록 흐름은 유지한다.

### 오래된 주소 표시

좌표가 바뀌는 동안 이전 주소가 남으면 사용자가 잘못된 위치 기준으로 목록을 보고 있다고 오해할 수 있다. 주소는 좌표와 함께 저장하거나 refresh 시작 시 비워야 한다.

### 화면 밀도

주소가 길면 리스트 시작 위치가 과하게 밀릴 수 있다. 주소는 최대 2줄로 제한하고 조건은 한 줄로 유지한다.

## Acceptance Criteria

- 리스트 상단의 조건 영역은 더 이상 `GasStationCard`처럼 보이지 않는다.
- 주유소 카드와 조회 기준 요약의 위계가 명확히 구분된다.
- 주소 조회 성공 시 리스트 위에 현재 위치 주소가 표시된다.
- 주소 조회 실패 시에도 조건 요약과 주유소 리스트는 정상 표시된다.
- 기존 권한, GPS, refresh, stale cache 정책은 유지된다.
