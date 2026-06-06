# 스펙: 이펙티브 자바 · 클린코드 관점 코드 개선

Date: 2026-06-07
Status: Proposed
Scope: `core/network`, `core/model`, `data/station`, `feature/station-list`

## 1. 배경

인프런 "이펙티브 자바 완벽 공략(아이템 17 불변 클래스, 아이템 76 실패 원자성)"과 김영한 "실전 자바 - 불변 객체", 조영호 "단일 추상화 수준 원칙", 백기선 "함수 추출하기" 강의의 원칙을 렌즈로 현재 코드를 점검했다.

도메인 모델 계층(`MoneyWon`, `DistanceMeters`, `Coordinates`)은 이미 불변 + 자기 검증으로 원칙을 충실히 따른다. 다만 그 검증이 **도메인 생성자에만** 있고 **네트워크 경계에는 없어서**, 신뢰할 수 없는 외부 입력이 도메인 불변식을 위반할 때 예외가 새어 나가는 결함이 존재한다. 본 문서는 이 결함(우선순위 1)과 가독성 개선(우선순위 2~4)의 요구사항·수용 기준을 정의한다.

## 2. 문제 정의

### P1 — (결함) 좌표 한 건의 범위 위반이 새로고침 전체를 실패시킴

`Coordinates`(core/model)는 `init { require(latitude in -90.0..90.0) ... }`로 범위를 강제하며, 위반 시 `IllegalArgumentException`을 던진다. 그런데 두 네트워크 경계가 외부 입력으로 `Coordinates`를 **직접 생성**한다.

- Opinet 경로: `NetworkStationFetcher.fetchStations`(core/network)는 행을 `toNetworkRemoteStation() ?: continue`로 **걸러내도록** 설계됐다. 그러나 그 경로의 `rawCoordinatesToWgs84` → `LocalKoreanCoordinateTransform.ktmToWgs84`는 `Coordinates(...)`를 그대로 생성한다. KATEC 변환 결과가 범위를 벗어나면 `require`가 예외를 던지고, 이 예외가 루프/`buildList`를 뚫고 나간다.
- Proxy 경로: `ProxyStationFetcher`의 `toNetworkRemoteStation`은 DTO의 `latitude`/`longitude`를 **범위 검사 없이** `Coordinates(lat, lon)`로 생성한다. 응답에 `latitude = 200.0` 같은 값이 한 건만 있어도 `mapNotNull`에서 예외가 발생한다.

두 경우 모두 예외는 `DefaultStationRepository.refreshNearbyStations`의 catch-all에서 `StationRefreshFailureReason.Unknown`으로 변환되어 사용자에게 "새로고침 실패"로 노출된다. 즉 **불량 행 1건을 건너뛴다는 의도가 깨지고, 정상 행까지 모두 버려진다.**

- 위배 원칙: 이펙티브 자바 아이템 76(실패 원자성), 클린코드 "경계(boundary)에서 신뢰할 수 없는 입력 검증".
- 현재 테스트 공백: `NetworkStationFetcherTest`의 "filters out stations with incomplete payloads"는 빈 문자열/누락 필드만 검증한다. 범위 초과 좌표 케이스가 없다. `ProxyStationFetcherTest`도 동일.

### P2 — (가독성) `observeNearbyStations`에 추상화 수준이 혼재

`DefaultStationRepository.observeNearbyStations`(data/station)는 `combine` → `flatMapLatest` 내부에서 "스냅샷 없음", "캐시 비어있음" 두 종류의 `StationSearchResult(...)`를 인라인으로 직접 조립한다. 흐름 제어(고수준)와 결과 객체 생성(저수준)이 한 함수에 섞여 있다.

- 위배 원칙: 단일 추상화 수준 원칙, 조합 메서드 패턴.

### P3 — (가독성) ViewModel `init` 블록이 4개 파이프라인을 직접 보유

`StationListViewModel.init`(feature/station-list)은 선호도 관찰, 쿼리 빌드/리프레시 트리거, 검색 결과 프로젝션, 5-인자 `combine` UI 상태 바인딩을 한 블록에 나열한다. `combine` 5-인자 오버로드는 한계치라 입력이 하나만 늘어도 깨진다.

- 위배 원칙: 단일 추상화 수준 원칙, 함수 추출하기.

### P4 — (사소) `Brand.fromCode` 선형 탐색

`Brand.fromCode`(core/model)는 `entries.firstOrNull { it.name == code }`로 코드→상수를 매핑한다. 항목 10개라 성능 이슈는 아니지만, 의도("코드로 enum 조회")가 `Map` 또는 `enumValueOf` 기반보다 덜 드러난다.

- 관련 원칙: 이펙티브 자바 아이템 34(int 상수 대신 열거 타입), 아이템 1(정적 팩터리).

## 3. 요구사항 및 수용 기준

### P1 (필수)

- R1.1 외부 입력으로부터의 좌표 생성은 **예외를 던지지 않고** 범위를 벗어나면 해당 행을 `null`로 걸러야 한다.
- R1.2 두 경계(Opinet KATEC 변환 경로, Proxy 직접 생성 경로) 모두 동일한 안전 생성 규칙을 적용한다.
- R1.3 `Coordinates`의 도메인 불변식(`require`)은 **유지**한다. 내부 코드가 잘못된 좌표를 만들면 여전히 빠르게 실패해야 한다(fail-fast). 경계에서만 비예외 경로를 사용한다.
- 수용 기준
  - AC1.1 Proxy 응답에 `latitude = 200.0`(범위 초과) 행 1건 + 정상 행 1건이 오면, 정상 행만 `Success`로 반환되고 예외가 발생하지 않는다.
  - AC1.2 Opinet 응답에서 KATEC 변환 결과가 범위를 벗어나는 행은 걸러지고, 같은 응답의 정상 행은 보존된다.
  - AC1.3 범위 초과 좌표만 있는 응답은 `NetworkStationFetchResult.Failure`를 반환한다(예외 아님).
  - AC1.4 기존 정상 좌표 매핑/round-trip 테스트는 그대로 통과한다.

### P2 (권장)

- R2.1 `observeNearbyStations`는 결과 객체 조립을 의도가 드러나는 private 함수로 추출해 흐름 제어만 남긴다(동작 불변).
- 수용 기준: AC2.1 기존 `DefaultStationRepository` 테스트가 수정 없이 통과한다. AC2.2 함수 본문이 "관찰 → 분기 → 결과 위임" 형태로 읽힌다.

### P3 (권장)

- R3.1 `init`은 `observePreferences()`, `triggerRefreshOnQueryChange()`, `observeSearchProjection()`, `bindUiState()` 같은 private 함수 호출 목록으로 환원한다(동작 불변).
- 수용 기준: AC3.1 기존 ViewModel 테스트 통과. AC3.2 UI 상태/이펙트 방출 동작 변화 없음.

### P4 (선택)

- R4.1 `fromCode`를 코드→상수 `Map` 조회 또는 `enumValueOf` + 폴백으로 의도를 명확화한다.
- 수용 기준: AC4.1 `fromCode("ETC")`, 미지의 코드 → `ETC` 폴백, 유효 코드 매핑이 모두 보존된다.

## 4. 범위 밖 (Non-goals)

- 좌표 변환 알고리즘(KATEC↔WGS84) 자체의 정확도 변경.
- `StationRefreshFailureReason` 분류 체계 확장.
- UI/디자인 시스템 변경.
- 새 기능 추가.

## 5. 위험 및 완화

- 공개 시그니처 변경(`ktmToWgs84: Coordinates → Coordinates?`)이 호출처와 테스트에 영향. → 대안으로 `Coordinates.ofOrNull` 안전 팩터리를 core/model에 추가해 경계에서만 사용하고, 변환기는 이 팩터리를 위임 사용한다(구현 문서 참조).
- 리팩터(P2/P3)는 동작 보존이 핵심. → 기존 테스트를 회귀 가드로 두고 순수 추출만 수행, 신규 로직 금지.

## 6. 우선순위

1. P1 (런타임 결함, 사용자 영향) — 필수
2. P3 (변경 취약성 높은 `init`) — 권장
3. P2 (가독성) — 권장
4. P4 (사소) — 선택
