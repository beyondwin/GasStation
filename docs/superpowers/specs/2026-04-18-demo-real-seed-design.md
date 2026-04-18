# Demo Real Seed Design

작성일: 2026-04-18

## 목표

`demo` flavor가 더 이상 하드코딩 4건에만 의존하지 않고, 강남역 2번 출구 기준 실제 API 응답으로부터 생성된 정적 시드를 사용하게 만든다.

핵심 요구는 두 가지다.
- `demo` 실행은 계속 API 키 없이 가능해야 한다.
- 반경과 유종을 바꿔도 실제 데이터 기반 목록, 가격 변동, watchlist 비교를 로컬에서 재현할 수 있어야 한다.

## 비목표

- `demo` 실행 중 외부 API를 다시 호출하지 않는다.
- `demo`를 실시간 동기화 모드로 바꾸지 않는다.
- 브랜드 필터나 정렬 조건별로 별도 원본 시드를 만들지 않는다.

## 사용자 시나리오

검토자는 `demo` 앱을 실행한다.

앱은 강남역 2번 출구 고정 좌표를 현재 위치로 사용한다. 사용자가 반경을 `1km`, `3km`, `5km`로 바꾸거나 유종을 `휘발유`, `경유`, `고급휘발유`, `등유`, `LPG`로 바꿔도, 앱은 이미 로컬 DB에 적재된 실제 데이터 기반 스냅샷을 사용해 목록을 보여준다.

watchlist에 주유소를 담으면 비교 화면에서 가격 변화와 마지막 확인 시각이 함께 보인다. 이 변화 정보는 실제 API를 여러 번 친 결과가 아니라, 최신 실제 스냅샷 1회분에서 파생된 결정적 히스토리로 구성된다.

## 권장 접근

세 가지 접근 중 이 설계는 “실제 API 기반 시드 생성 스크립트 + 정적 자산 적재”를 채택한다.

1. 실제 API를 한 번 호출해 전체 조합 데이터를 수집한다.
2. 수집 결과를 사람이 읽을 수 있는 정적 JSON 시드 파일로 저장한다.
3. `demo` startup hook가 이 파일을 읽어 Room에 `station_cache`와 `station_price_history`를 채운다.

이 접근을 선택한 이유는 다음과 같다.
- `demo` 실행이 완전히 오프라인이 된다.
- 검토 결과가 재현 가능하다.
- 시드 변경 diff를 코드 리뷰로 확인할 수 있다.
- 기존 `prod` 네트워크 경로를 재사용할 수 있다.

## 데이터 범위

기준 위치는 강남역 2번 출구 고정 좌표다. 구현에서는 해당 좌표를 하나의 상수로 소유하고, demo 위치 override와 시드 생성기가 같은 값을 사용한다.

시드 생성 대상 조합은 전체다.
- 반경: `1km`, `3km`, `5km`
- 유종: `GASOLINE`, `DIESEL`, `PREMIUM_GASOLINE`, `KEROSENE`, `LPG`

총 15개 쿼리 조합을 수집한다.

브랜드 필터와 정렬 조건은 시드 생성 범위에 포함하지 않는다.
- 브랜드 필터는 현재 구조상 클라이언트 필터다.
- 정렬은 동일 캐시 스냅샷에 대한 뷰 정렬이다.
- 캐시 키도 이미 `locationBucket + searchRadius + fuelType`만 사용한다.

따라서 반경 x 유종 조합만 채우면 현재 제품 동작을 그대로 테스트할 수 있다.

## 시드 파일 형식

시드는 `demo` 자산에 두는 단일 JSON 파일로 관리한다.

권장 위치:
- `app/src/demo/assets/demo-station-seed.json`

루트 구조는 다음 의미를 가진다.
- `seedVersion`: 포맷 버전
- `generatedAtEpochMillis`: 실제 수집 시각
- `origin`: 기준 좌표와 설명
- `queries`: 반경 x 유종 조합별 스냅샷
- `history`: 주유소별 가격 히스토리 행

예시 구조:

```json
{
  "seedVersion": 1,
  "generatedAtEpochMillis": 1770000000000,
  "origin": {
    "label": "Gangnam Station Exit 2",
    "latitude": 37.497927,
    "longitude": 127.027583
  },
  "queries": [
    {
      "radiusMeters": 1000,
      "fuelType": "GASOLINE",
      "stations": [
        {
          "stationId": "A0001",
          "brandCode": "SKE",
          "name": "예시 주유소",
          "priceWon": 1639,
          "latitude": 37.498,
          "longitude": 127.028
        }
      ]
    }
  ],
  "history": [
    {
      "stationId": "A0001",
      "fuelType": "GASOLINE",
      "entries": [
        { "priceWon": 1619, "fetchedAtEpochMillis": 1769827200000 },
        { "priceWon": 1649, "fetchedAtEpochMillis": 1769913600000 },
        { "priceWon": 1639, "fetchedAtEpochMillis": 1770000000000 }
      ]
    }
  ]
}
```

파일은 DB 스냅샷 바이너리가 아니라 의미가 드러나는 JSON으로 유지한다. 사람이 diff를 읽을 수 있어야 하고, 생성기와 startup hook가 같은 스키마를 공유해야 한다.

## 시드 생성 방식

시드 생성은 `demo` 앱 실행 경로와 분리된 개발자 전용 작업이다. 실행 시점에만 실제 API 키가 필요하다.

생성기는 기존 `prod` 네트워크 경로를 재사용한다.
- `OpinetService`
- `KakaoService`
- 현재 `StationRemoteDataSource`가 사용하는 좌표 변환과 원격 매핑 규칙

생성 흐름은 다음과 같다.
1. 강남역 2번 출구 좌표를 기준 origin으로 잡는다.
2. 반경 x 유종 전체 조합 15개를 순회한다.
3. 각 조합마다 실제 API를 호출해 원격 주유소 목록을 수집한다.
4. 원격 결과를 station snapshot JSON으로 정규화한다.
5. snapshot에서 등장한 주유소별로 결정적 price history 3행을 만든다.
6. 최종 JSON 파일을 자산 경로에 덮어쓴다.

생성 명령은 Gradle task 또는 JVM 실행 진입점 하나로 노출한다. 중요한 건 “의도적으로 실행될 때만 네트워크를 사용한다”는 점이다. 평소 `assembleDemoDebug`나 앱 실행은 절대 외부 API를 의존하지 않는다.

## 결정적 히스토리 규칙

히스토리는 최신 실제값 1회분과 과거 파생값 2회분으로 구성한다.

규칙은 랜덤이 아니라 결정적이어야 한다.
- 최신 행: 실제 API 가격과 생성 시각
- 과거 1행: `stationId + fuelType` 기반 해시 오프셋을 적용한 가격
- 과거 2행: 같은 입력에서 파생한 두 번째 오프셋을 적용한 가격

오프셋 설계 원칙:
- 범위는 작게 유지해 실제 가격처럼 보여야 한다.
- 상승, 하락, 변동 없음이 섞여야 한다.
- 같은 station/fuel 조합이면 언제나 같은 과거 가격이 생성되어야 한다.

권장 규칙:
- offset1: `-20`원에서 `+20`원
- offset2: `-35`원에서 `+35`원
- 최종 가격은 음수가 되지 않도록 하한을 둔다.

권장 시각 규칙:
- latest: `generatedAtEpochMillis`
- previous1: latest - 24시간
- previous2: latest - 48시간

이렇게 하면 다음 UX가 살아난다.
- 목록의 가격 상승/하락 배지
- watchlist 비교의 이전 가격 대비 변화
- 마지막 확인 시각 표시

## Demo startup 적재 규칙

`DemoSeedStartupHook`는 더 이상 코드 내부의 4개 `StationCacheEntity`를 만들지 않는다. 대신 JSON 자산을 읽고, 그 내용을 Room에 적재한다.

적재 원칙은 다음과 같다.
- DB는 demo 실행마다 같은 초기 상태를 재현해야 한다.
- `station_cache`는 쿼리 조합별 snapshot 단위로 교체한다.
- `station_price_history`는 JSON의 history rows 전체를 삽입한다.
- 필요한 경우 `watched_station`은 비운 상태로 둔다.

초기 적재 순서는 다음과 같다.
1. DB open
2. 기존 demo seed 관련 데이터 정리
3. 쿼리별 `station_cache` snapshot 삽입
4. `station_price_history` 전체 삽입
5. DB close

핵심은 `observeNearbyStations()`와 `observeWatchlist()`가 현재 사용하는 DAO 형태를 그대로 만족시키는 것이다. 읽기 경로는 건드리지 않고, demo용 입력 데이터만 현실화한다.

## 위치 고정 규칙

demo 위치 override는 계속 유지한다. 다만 기존 좌표 상수와 시드 생성기 origin 상수가 서로 어긋나지 않도록 하나의 공통 상수 또는 공통 계약으로 묶는다.

기준 위치는 강남역 2번 출구다.

이 좌표는 다음 두 곳에서 동일하게 사용되어야 한다.
- demo current location override
- seed generation origin

## 실패 처리

생성 단계는 보수적으로 실패한다.

- 15개 조합 중 하나라도 원격 수집에 실패하면 생성 작업 전체를 실패시킨다.
- 어떤 조합이 실제로 빈 결과라면 그것은 정상 값으로 저장할 수 있다.
- 좌표 변환 실패나 필드 누락으로 개별 주유소가 드롭되면, 생성 로그에 station id와 개수를 남긴다.
- 생성이 성공했을 때만 JSON 파일을 교체한다.

startup 단계도 보수적으로 실패한다.
- JSON 파싱 실패 시 demo 실행을 조용히 계속하지 않는다.
- 필수 필드가 깨진 시드는 바로 예외로 드러내서 잘못된 자산을 조기에 발견한다.

## 테스트 전략

테스트는 세 층으로 나눈다.

### 1. 생성기 테스트

검증 대상:
- 반경 x 유종 전체 15개 조합 생성 여부
- JSON 직렬화 포맷 안정성
- 동일 입력에서 동일 history가 만들어지는지
- station/fuel별 history가 3행으로 생성되는지
- 가격 하한과 timestamp 규칙이 지켜지는지

### 2. Demo startup 적재 테스트

검증 대상:
- JSON 자산을 읽어 `station_cache`가 각 캐시 키 버킷에 정확히 들어가는지
- `station_price_history`가 station/fuel 단위로 들어가는지
- 기존 하드코딩 4건 대신 자산 기반 적재가 수행되는지

### 3. 통합 동작 검증

검증 대상:
- `demo`에서 반경을 바꿔도 목록이 비지 않는지
- 유종을 바꿔도 해당 조합 결과가 보이는지
- watchlist 비교 화면에서 가격 변화가 나타나는지
- 정렬과 브랜드 필터가 기존처럼 snapshot 위에서만 동작하는지

## 구현 경계

이번 변경은 아래 범위에 집중한다.
- demo 시드 생성 경로 추가
- demo 자산 포맷 정의
- demo startup hook 교체
- 테스트 데이터 현실화

이번 변경에서 하지 않는 것:
- prod 원격 검색 파이프라인 재설계
- 캐시 키 규칙 변경
- watchlist 읽기 모델 구조 변경
- 실시간 배치 동기화 기능 추가

## 완료 기준

아래 기준을 모두 만족해야 한다.
- `demo` flavor는 여전히 API 키 없이 실행된다.
- demo 목록은 강남역 2번 출구 기준 실제 데이터 기반으로 보인다.
- 반경 1/3/5km와 유종 전체를 바꿔도 로컬 시드 결과가 나온다.
- 가격 변동 배지와 watchlist 비교가 의미 있는 데이터를 보여준다.
- 시드 파일은 사람이 읽을 수 있고 재생성 가능하다.
- 생성 과정은 실제 키를 사용하지만, 앱 실행은 키에 의존하지 않는다.
