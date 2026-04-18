# 오프라인 전략

GasStation의 오프라인 전략은 "마지막 성공 결과를 비우지 않고 계속 보여준다"로 요약할 수 있습니다. 핵심 구현은 `data:station`의 Room 기반 읽기 모델 조합입니다.

## 핵심 전략 요약

| 항목 | 전략 |
| --- | --- |
| 목록 fallback | 마지막 성공 스냅샷을 Room에 유지하고 실패 시 그대로 재사용 |
| stale 판정 | `StationCachePolicy` 5분 기준으로 `Fresh`/`Stale` 구분 |
| 가격 변화 | `station_price_history`로 상승/하락/변동 없음 계산 |
| watchlist 유지 | 현재 검색 결과가 없어도 저장된 항목과 최신 히스토리로 비교 화면 유지 |
| demo 의미 | 고정 seed를 다시 적재해 오프라인 의미 체계를 반복 가능하게 시연 |

이 문서의 핵심은 "네트워크 실패 시 빈 화면으로 무너지지 않는다"는 점입니다.

## 저장 단위

`core:database`는 아래 세 저장 단위를 가집니다.

- `station_cache`
  특정 질의 버킷에 대한 최신 주유소 스냅샷
- `station_price_history`
  주유소별 가격 변화 히스토리
- `watched_station`
  사용자가 관심 저장한 주유소 목록

목록 화면과 watchlist 화면은 이 세 테이블을 조합해 그려집니다.

## 캐시 키

스냅샷 캐시 키는 `StationQueryCacheKey`로 표현되며 다음 값만 포함합니다.

- 위도 버킷
- 경도 버킷
- 검색 반경
- 유종

현재 기본 버킷 크기는 250m입니다.

다음 값들은 캐시 키에 포함되지 않습니다.

- 정렬 순서
  같은 스냅샷 집합을 거리순/가격순으로 다시 정렬할 수 있기 때문입니다.
- 브랜드 필터
  스냅샷을 읽은 뒤 클라이언트에서 필터링합니다.
- 외부 지도 제공자
  조회 데이터가 아니라 이동 연동 대상만 바꾸기 때문입니다.

## 조회 동작

`observeNearbyStations()`는 먼저 Room 스냅샷을 읽고, 그 결과에 관심 여부와 가격 히스토리를 결합해 `StationSearchResult`를 만듭니다.

- 캐시가 없으면 빈 목록 + `Stale`
- 캐시가 있으면 마지막 fetch 시각 기준으로 `Fresh` 또는 `Stale`
- 브랜드 필터와 정렬은 캐시를 읽은 뒤 적용

즉, 화면은 네트워크 응답을 직접 기다리지 않고 저장된 읽기 모델을 우선 봅니다.

## stale 판정

`StationCachePolicy`의 stale 기준은 5분입니다.

- 5분 이내면 `Fresh`
- 5분 초과면 `Stale`

stale이라고 해서 결과를 버리지 않습니다. UI는 오래된 결과를 그대로 유지한 채 stale 배너만 보여줄 수 있습니다.

## 새로고침 실패 시 동작

`refreshNearbyStations()`는 원격 조회가 실패하면 `StationRefreshException`을 던지고, 기존 Room 스냅샷은 유지합니다.

이 설계 때문에 네트워크 실패 시에도 아래가 보장됩니다.

- 목록이 빈 상태로 덮어써지지 않는다
- 마지막 성공 스냅샷은 계속 렌더링된다
- stale 여부만 바뀌거나 새로고침 실패 메시지만 추가된다

## 가격 히스토리

새로고침이 성공하면 각 주유소의 현재 가격을 `station_price_history`에 추가합니다. 히스토리는 주유소 ID + 유종 기준으로 최신 10건만 유지합니다.

이 히스토리로 다음 정보를 계산합니다.

- 목록 화면의 가격 상승/하락/변동 없음 배지
- watchlist 화면의 최근 비교 정보

히스토리가 충분하지 않으면 가격 변화는 `Unavailable`로 남습니다.

## watchlist fallback

watchlist는 현재 검색 결과에 없는 주유소도 유지해야 하므로 목록보다 한 단계 더 방어적으로 동작합니다.

- 최신 `station_cache`가 있으면 그 값을 우선 사용
- 없으면 `watched_station`에 저장된 마지막 좌표/브랜드/이름과 `station_price_history`의 최신 가격으로 대체
- 둘 다 없으면 해당 항목은 요약을 만들지 않음

이 덕분에 사용자가 관심 저장한 항목은 현재 화면의 검색 결과에 사라져도 비교 화면에서 쉽게 사라지지 않습니다.

## demo flavor의 오프라인 의미

`demo`는 오프라인 의미 체계를 보여주기 위한 고정 데이터 경로를 가집니다.

- 앱 시작 시 `DemoSeedStartupHook`이 Room에 demo seed 자산을 다시 적재
- `DemoSeedStationRemoteDataSource`가 실제 네트워크 대신 seed 문서에서 스냅샷을 읽음
- 위치는 강남역 2번 출구 고정 좌표 사용

즉, demo는 API 키 없이도 항상 같은 스냅샷과 히스토리에서 시작하고, prod와 같은 캐시/stale/watchlist 규칙을 그대로 보여줍니다.

## 운영 메모

- `prod` 런타임의 실제 검색은 Opinet API 키만 필요하다.
- `tools:demo-seed:generateDemoSeed`는 seed 재생성을 위해 `opinet.apikey`만 사용한다.
- demo는 "오프라인 fallback을 보여주기 위한 샘플 모드"가 아니라, 실제 시연에서 사용하는 고정된 공식 경로다.
