# 스펙: 이펙티브 자바 · 클린코드 관점 코드 개선 (Round 2)

Date: 2026-06-07
Status: Proposed
Scope: `core/model`, `core/network`, `data/station`, `domain/station`
선행 작업: [clean-code-improvements-spec.md](./clean-code-improvements-spec.md) (Round 1, P1~P4 적용 완료)

## 1. 배경

Round 1에서 "신뢰할 수 없는 외부 입력은 경계에서 검증한다"는 원칙(이펙티브 자바 아이템 76 실패 원자성, 클린코드 경계 검증)을 적용해, `Coordinates.ofOrNull` 안전 팩터리를 만들고 **네트워크 진입 경계**(`ProxyStationFetcher`, `NetworkStationMappers`)에서 범위를 벗어난 좌표를 예외 없이 걸러내도록 고쳤다.

그러나 경계는 네트워크 진입만이 아니다. 본 라운드는 Round 1과 **동일한 클래스의 실패 모드가 아직 남아 있는 두 경계**(읽기 경로의 DB→도메인 복원, Opinet 가격 검증)를 닫고, 더불어 Round 1과 같은 가독성 원칙(단일 추상화 수준, 다형성)을 추가 지점에 적용한다.

참고 강의(인프런):
- 백기선 「이펙티브 자바 완벽 공략」 아이템 17 불변 클래스 · 아이템 1 정적 팩터리 메서드
- 최범균 「객체 지향 프로그래밍 입문」 캡슐화 / Tell, Don't Ask
- 박우빈 「Readable Code」 추상화 수준 / 메서드 추출

## 2. 문제 정의

### P1 — (결함) 읽기 경계(DB→도메인)에 안전 생성이 빠져 캐시 1행이 화면 전체를 깨뜨림

Round 1은 **쓰기 경계**(network → `RemoteStation`)만 닫았다. 반대편 **읽기 경계**(DB `StationCacheEntity`/`WatchedStationEntity` → 도메인 `Station`)는 여전히 예외를 던지는 생성자를 직접 호출한다.

- `data/station/.../mapper/StationMappers.kt:31-33` — `StationCacheEntity.toDomainStation`이 `MoneyWon(priceWon)`, `Coordinates(latitude, longitude)`를 직접 생성한다. 이 매퍼는 `observeNearbyStations` → `StationSearchResultAssembler.toSearchResult`(`StationSearchResultAssembler.kt:21` `map { ... }`)의 **핫패스**다.
- `data/station/.../WatchlistSummaryAssembler.kt:28,33` — 캐시 스냅샷이 없을 때 `Coordinates(latitude, longitude)`, `MoneyWon(latestPrice.priceWon)`를 직접 생성한다.

DB도 신뢰 경계다(스키마 마이그레이션, 과거에 저장된 데이터, 외부 시드, 향후 다른 writer). 캐시 행 한 건이라도 범위를 벗어난 좌표나 음수 가격을 가지면 `map`/`mapNotNull` 내부에서 `IllegalArgumentException`이 발생하고, 그 예외가 `combine` 람다를 뚫고 나가 **정상 행을 포함한 검색 결과 Flow 전체가 깨진다.** 이는 Round 1이 진입 경계에서 고친 바로 그 실패 모드가 읽기 경계에 그대로 남은 것이다.

- 위배 원칙: 이펙티브 자바 아이템 76(실패 원자성), 클린코드 "경계에서 신뢰할 수 없는 입력 검증".
- 현재 테스트 공백: `StationMappers`/`StationSearchResultAssembler`에 대한 직접 단위 테스트가 없고, `DefaultStationRepositoryTest`/`WatchlistRepositoryTest`는 정상 좌표·가격 행만 다룬다.

### P2 — (결함) Opinet 가격 검증이 Proxy와 불일치해 음수 가격이 read에서 폭발

두 fetcher의 가격 검증 기준이 다르다.

- Proxy: `ProxyStationFetcher.kt:35` — `priceWon?.takeIf { it > 0 } ?: return null` (양수 검증 **있음**).
- Opinet: `NetworkStationMappers.kt:11` — `priceWon?.toIntOrNull() ?: return null` (양수 검증 **없음**).

Opinet 응답에 `priceWon = "-1"`이 오면 `toIntOrNull()`은 통과 → `NetworkRemoteStation(priceWon = -1)` 생성 → `RemoteStation` → DB 캐시까지 그대로 들어간다. 그리고 **나중에 읽을 때** `toDomainStation`의 `MoneyWon(priceWon)`에서 `require(value >= 0)`가 터진다(P1의 폭발 지점과 동일). 진입 시점엔 멀쩡하고 읽기 시점에 터지는 잠복 결함이며, 같은 역할의 두 경계가 서로 다른 불변식을 강제한다는 일관성 결함이기도 하다.

- 위배 원칙: 같은 책임(원격 가격 정규화)을 가진 두 매퍼가 다른 계약을 가짐. 이펙티브 자바 아이템 76, 경계 일관성.

### P3 — (가독성) `WatchlistSummaryAssembler.toWatchedSummary`에 추상화 수준이 혼재

`WatchlistSummaryAssembler.kt:16-61`의 `toWatchedSummary`는 "표시할 station 결정"(3-way `when`)과 "priceDelta 결정"(또 다른 3-way `when`)을 한 함수에 직렬로 나열한다. 흐름과 두 개의 독립적 결정 로직이 한 추상화 수준에 섞여 있어 읽기 어렵다. Round 1의 P2(`observeNearbyStations` 결과 조립 추출)와 동일한 원칙을 이 함수에 적용할 수 있다.

- 위배 원칙: 단일 추상화 수준 원칙, 메서드 추출(박우빈 Readable Code).

### P4 — (객체지향, 선택) `StationPriceDelta`의 파생 프로퍼티가 타입 분기로 구현됨

`StationPriceDelta.kt:6-18`의 `direction`, `amountWonOrNull`은 `when (this) { is Increased -> ... }`로 자기 타입을 검사한다. variant(`Unavailable`/`Unchanged`/`Increased`/`Decreased`)를 추가하면 두 `when`을 모두 손봐야 하며, 데이터를 꺼내 바깥에서 분기하는 형태에 가깝다. 각 variant가 프로퍼티를 override하면 "묻지 말고 시켜라"(최범균 캡슐화)에 더 부합한다.

- 관련 원칙: Tell, Don't Ask / 다형성으로 조건문 대체.
- 주의: Kotlin의 sealed + exhaustive `when`도 충분히 관용적이라 **이득이 명확하지 않은 토론 영역**이다. 우선순위 최하, 선택 사항으로 둔다.

## 3. 요구사항 및 수용 기준

### P1 (필수)

- R1.1 DB→도메인 복원은 범위를 벗어난 좌표/음수 가격에 대해 **예외를 던지지 않고** 해당 행을 걸러야 한다.
- R1.2 `Coordinates`/`MoneyWon`의 도메인 불변식(`require`)은 **유지**한다(내부 fail-fast). 경계에서만 비예외 팩터리(`ofOrNull`)를 사용한다.
- R1.3 안전 생성은 좌표(`Coordinates.ofOrNull`, 이미 존재)와 가격(`MoneyWon.ofOrNull`, 신규) 양쪽에 적용한다.
- 수용 기준
  - AC1.1 `toSearchResult`에 [정상 행, 범위 초과 좌표 행]을 주면 정상 행만 결과에 남고 예외가 없다.
  - AC1.2 `toSearchResult`에 [정상 행, 음수 가격 행]을 주면 정상 행만 남고 예외가 없다.
  - AC1.3 `toWatchedSummary`에서 캐시 스냅샷 없이 history만 있고 엔티티 좌표가 범위를 벗어나면 `null`을 반환한다(예외 아님).
  - AC1.4 `MoneyWon.ofOrNull(1680)` → 인스턴스, `MoneyWon.ofOrNull(-1)` → null, `MoneyWon(-1)` 생성자는 여전히 예외.
  - AC1.5 기존 `DefaultStationRepositoryTest`/`WatchlistRepositoryTest`가 수정 없이 통과한다.

### P2 (필수)

- R2.1 Opinet 매퍼는 Proxy와 동일하게 가격이 양수가 아니면 행을 걸러야 한다(읽기 시점까지 음수 가격이 전파되지 않는다).
- 수용 기준
  - AC2.1 Opinet 응답에 [정상 행, `priceWon="-1"` 행]이 오면 정상 행만 `Success`로 매핑되고, 이후 read에서도 예외가 없다.
  - AC2.2 `priceWon="0"` 행도 걸러진다(Proxy의 `> 0` 계약과 일치).
  - AC2.3 기존 정상 가격 매핑 테스트는 그대로 통과한다.

### P3 (권장, 동작 불변)

- R3.1 `toWatchedSummary`는 "station 선택"과 "priceDelta 계산"을 의도가 드러나는 private 함수로 추출해, 본문이 "선택 → 계산 → 조립"으로 읽혀야 한다.
- 수용 기준: AC3.1 기존 `WatchlistRepositoryTest`가 수정 없이 통과. AC3.2 반환값·필터 동작 불변.

### P4 (선택, 동작 불변)

- R4.1 `direction`/`amountWonOrNull`을 각 variant의 override로 전환하되, **공개 API 시그니처와 결과는 보존**한다(소비처 `StationListItemUiModel`, `WatchlistItemUiModel` 무수정).
- 수용 기준: AC4.1 기존 `StationPriceDeltaTest` 통과. AC4.2 두 feature UI 모델이 수정 없이 컴파일·동작.

## 4. 범위 밖 (Non-goals)

- 좌표 변환 알고리즘(KATEC↔WGS84) 정확도 변경.
- `StationRefreshFailureReason` 분류 확장.
- DB 스키마/마이그레이션 변경, Room 엔티티에 검증 추가.
- UI/디자인 시스템 변경, 새 기능 추가.

## 5. 위험 및 완화

- `toDomainStation`의 반환 타입이 `Station` → `Station?`로 바뀌어 두 호출처에 영향.
  - `StationSearchResultAssembler.kt:21` `map` → `mapNotNull`로 변경(불량 행 스킵).
  - `WatchlistSummaryAssembler.kt:21`는 이미 `cachedStation?.toDomainStation(origin)`로 nullable 체인이라 자연 전파. 단, fallback 분기(28,33행)의 직접 생성도 함께 안전화해야 누락이 없다.
- P3/P4는 동작 보존이 핵심. 기존 테스트를 회귀 가드로 두고 순수 추출/이관만 수행, 신규 로직 금지.
- P4는 이득이 작으므로, 리뷰에서 "분기 유지가 더 낫다"는 의견이 나오면 폐기 가능(선택 항목).

## 6. 우선순위

1. P1 (읽기 경계 결함, 사용자 영향) — 필수
2. P2 (Opinet 가격 일관성, P1과 동일 폭발 지점) — 필수 (P1과 같은 PR 권장)
3. P3 (가독성) — 권장
4. P4 (다형성 전환) — 선택
