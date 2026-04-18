# 프로젝트 읽기 가이드

이 문서는 "구조는 많이 바뀌었는데 어디서부터 봐야 할지 모르겠다"는 상황을 전제로 쓴 가이드입니다. 단순히 모듈 목록을 나열하지 않고, **왜 이런 구조로 다시 나눴는지**, **처음 보는 사람이 어떤 순서로 읽어야 덜 헤매는지**, **기능을 고치려면 어느 층부터 내려가야 하는지**를 같이 설명합니다.

## 먼저 결론

이 프로젝트는 예전의 "앱 하나에 화면, 상태, 저장소, 네트워크, Android 구현이 섞여 있는 구조"에서 벗어나기 위해 다시 나뉘었습니다. 핵심 의도는 아래 네 가지입니다.

- `app`을 최대한 얇게 만들어서 "앱 시작, 내비게이션, flavor 연결"만 담당하게 한다.
- 화면 상태와 사용자 액션은 `feature`에 모으고, 비즈니스 계약은 `domain`에 고정한다.
- Room, DataStore, Retrofit, 위치 구현처럼 Android/인프라 성격이 강한 코드는 `data`/`core`로 내린다.
- `demo`와 `prod`가 같은 기능 그래프를 공유하면서도, 검토용 실행과 실제 실행을 분리한다.

즉, 이 리빌드는 "코드를 예쁘게 쪼갠 것"이 아니라 다음 문제를 풀기 위한 구조 조정입니다.

- 한 파일을 고칠 때 영향 범위를 예측하기 어렵던 문제
- 화면 코드가 저장소/권한/네트워크 세부 구현을 너무 많이 알던 문제
- demo와 prod가 섞여서 검증 경로가 불안정하던 문제
- 캐시, 오래된 데이터, 관심 주유소 비교 같은 상태 규칙이 여기저기 흩어지던 문제

## 이 프로젝트를 이해하는 한 문장

이 앱은 **현재 위치와 사용자 선호 설정으로 주유소 목록을 만들고, 마지막 성공 결과를 Room에 유지하며, 관심 주유소 비교와 외부 지도 연동까지 이어지는 멀티모듈 Compose 앱**입니다.

코드를 읽을 때는 이 문장만 기억해도 방향을 잃지 않습니다.

## 왜 `app / feature / domain / data / core`로 나눴나

### 1. `app`은 조립만 하게 하려는 의도

`app`은 실제 제품의 시작점이지만, 로직의 중심이 되면 금방 비대해집니다. 그래서 이 프로젝트에서는 `app`을 composition root로 제한했습니다.

- 앱 시작: `App.kt`
- UI 시작: `MainActivity.kt`
- 화면 연결: `navigation/GasStationNavHost.kt`
- flavor별 startup hook 연결
- `BuildConfig` 기반 런타임 값 주입
- 외부 지도 실행기 바인딩

이렇게 하면 `app`을 읽을 때 "전체 앱이 어떻게 조립되는지"만 보면 됩니다. 반대로 여기서 도메인 규칙이나 캐시 정책까지 나오기 시작하면 구조가 다시 무너진 신호입니다.

### 2. `feature`는 화면 단위로 상태를 소유하게 하려는 의도

`feature:*`는 Compose 화면과 ViewModel을 가집니다.

- `feature:station-list`
- `feature:settings`
- `feature:watchlist`

이 층은 "사용자에게 무엇을 보여줄지"를 결정합니다. 중요한 점은 여기서 Room DAO나 Retrofit 호출을 직접 하지 않는다는 것입니다. ViewModel은 `use case`나 `repository interface`를 통해 필요한 데이터만 받습니다.

이렇게 나눈 이유는 두 가지입니다.

- 화면을 읽을 때 Android UI 흐름에만 집중할 수 있게 하려는 것
- 저장소 구현이 바뀌어도 화면 계약을 크게 흔들지 않으려는 것

### 3. `domain`은 앱의 규칙을 고정하려는 의도

`domain:*`에는 다음이 들어 있습니다.

- 모델: `Station`, `StationQuery`, `StationSearchResult`, `WatchedStationSummary`
- 설정 모델: `UserPreferences`
- 계약: `StationRepository`, `SettingsRepository`
- use case: `ObserveNearbyStationsUseCase`, `RefreshNearbyStationsUseCase` 등

여기서 중요한 건 `domain`이 "복잡한 계산을 전부 넣는 층"이라는 뜻은 아니라는 점입니다. 이 프로젝트의 `use case`들은 꽤 얇습니다. 그런데도 남겨둔 이유는 다음과 같습니다.

- 화면이 저장소 구현체를 직접 알지 않게 하기 위해
- 기능 경계를 문서처럼 보여주기 위해
- 나중에 정책이 늘어날 때 ViewModel 대신 도메인 계약 위에 쌓기 쉽게 하기 위해

즉, 지금은 얇아 보여도 **경계선 역할**이 큽니다.

### 4. `data`는 저장소 구현과 읽기 모델 조합을 담당하게 하려는 의도

`data:*`는 실제 repository 구현체가 있는 곳입니다.

- `data:settings`는 DataStore 기반 설정 저장
- `data:station`은 네트워크, Room, 캐시 정책, 가격 히스토리, watchlist 읽기 모델 조합

특히 `data:station`이 중요한 이유는, 이 앱의 핵심 복잡도가 "화면"보다 "읽기 모델 조합"에 있기 때문입니다.

- 주유소 목록은 단순 네트워크 응답이 아니라
  - 캐시 스냅샷
  - 가격 히스토리
  - 관심 여부
  - 정렬
  - stale 여부
  - 브랜드 필터
  를 합쳐서 만들어집니다.

이 책임을 ViewModel에 두면 화면이 너무 무거워지고, DAO에 두면 의미가 분산됩니다. 그래서 repository 구현 쪽에서 읽기 모델을 완성하도록 한 것입니다.

### 5. `core`는 재사용 가능한 원시 타입과 Android 인프라를 모으려는 의도

`core:*`는 범용성이 높은 조각입니다.

- `core:model`: `Coordinates`, `MoneyWon`, `DistanceMeters`
- `core:location`: 위치 계약과 Android 위치 구현
- `core:network`: Retrofit 서비스와 런타임 네트워크 설정
- `core:database`: Room DB/DAO/Entity
- `core:datastore`: 사용자 선호 저장 인프라
- `core:designsystem`: 테마와 토큰
- `core:common`: 공통 결과/dispatcher

왜 `core`가 필요한가를 한 문장으로 말하면, **도메인보다 아래에 있지만 여러 기능이 공유하는 기술 기반을 따로 묶기 위해서**입니다.

## 처음 보면 어디서부터 읽어야 하나

### 추천 1: 가장 안전한 순서

처음 보는 사람에게 가장 추천하는 순서는 아래입니다.

1. `README.md`
2. `docs/architecture.md`
3. `docs/state-model.md`
4. `docs/offline-strategy.md`
5. `app/src/main/java/com/gasstation/MainActivity.kt`
6. `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`
7. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListRoute.kt`
8. `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`
9. `domain/station/StationRepository.kt`
10. `data/station/DefaultStationRepository.kt`
11. 필요하면 `core/database`, `core/network`, `core/location`

이 순서가 좋은 이유는 **앱의 바깥에서 안쪽으로**, 그리고 **UI에서 데이터 원천으로** 내려가기 때문입니다. 처음부터 DAO나 Retrofit부터 보면 전체 맥락 없이 구현 디테일에 빠집니다.

### 추천 2: 실제 기능 하나를 따라가는 순서

"주변 주유소 목록이 어떻게 그려지는지" 하나만 따라가고 싶다면 아래 순서가 제일 빠릅니다.

1. `GasStationNavHost.kt`
2. `StationListRoute.kt`
3. `StationListViewModel.kt`
4. `ObserveNearbyStationsUseCase.kt`
5. `RefreshNearbyStationsUseCase.kt`
6. `StationRepository.kt`
7. `DefaultStationRepository.kt`
8. `StationRemoteDataSource.kt`
9. `StationCachePolicy.kt`
10. `core/database/station/*`

### 추천 3: 리빌드 이유까지 이해하고 싶을 때

구조만이 아니라 "왜 이렇게 다시 짰는가"까지 보고 싶다면 다음 문서를 함께 보세요.

- `docs/superpowers/plans/2026-04-18-multimodule-rearchitecture.md`
- `docs/superpowers/plans/2026-04-18-gasstation-reference-rebuild.md`

이 문서들은 구현 계획이지만, 실제로는 아키텍처 의도가 가장 직접적으로 드러납니다. 어떤 책임을 어디서 떼어냈는지, 왜 `app`을 비우고 `core`/`feature`로 옮겼는지 설명이 많습니다.

## 실제 읽기 루트: 파일 기준으로 설명

### 1. `app`부터 읽기

#### `app/src/main/java/com/gasstation/App.kt`

이 파일은 앱 프로세스 시작점입니다.

- Hilt 앱 선언
- Timber 초기화
- `AppStartupRunner` 실행

여기서 중요한 포인트는 "startup 로직도 앱 본체에 박아두지 않고 hook 집합으로 실행한다"는 점입니다. 이 구조를 택한 이유는 flavor별 초기화 책임을 분리하기 위해서입니다.

- `demo`는 시드 데이터 적재
- `prod`는 로컬 시크릿 검증

둘 다 시작 시점의 관심사이지만, 성격이 완전히 다르므로 같은 클래스에 섞지 않은 것입니다.

#### `app/src/main/java/com/gasstation/MainActivity.kt`

가장 단순한 UI 엔트리입니다.

- `GasStationTheme`
- `GasStationNavHost`
- `ExternalMapLauncher` 주입

여기서 확인할 수 있는 핵심은, 액티비티가 거의 비어 있다는 점입니다. 이건 의도된 설계입니다. 액티비티가 비대해지지 않게, 화면 조합과 효과 처리 대부분을 feature/nav 쪽으로 내린 것입니다.

#### `app/src/main/java/com/gasstation/navigation/GasStationNavHost.kt`

화면 연결 중심 파일입니다.

- 시작 화면: 주유소 목록
- 설정 화면 이동
- watchlist 화면 이동
- 외부 지도 실행 effect 처리

이 파일이 중요한 이유는 "앱의 사용자 흐름이 여기서 보인다"는 점입니다. 기능이 추가되면 대개 여기 목적지 하나가 늘어납니다.

### 2. `feature`에서 화면 상태 이해하기

#### `feature/station-list/StationListRoute.kt`

이 프로젝트에서 가장 먼저 읽어야 할 feature 파일입니다. 이유는 ViewModel보다 먼저 **Android 환경과 화면 상태가 만나는 지점**을 보여주기 때문입니다.

여기서 하는 일:

- 위치 권한 상태 감시
- GPS 활성화 여부 감시
- lifecycle resume 시 상태 재동기화
- effect 수집 후 외부 행동 수행
- `StationListScreen`에 순수 UI 상태 전달

왜 이렇게 나눴나:

- `Route`는 Android/Compose 환경 의존을 담당
- `ViewModel`은 상태 조합과 액션 처리 담당
- `Screen`은 가능한 한 순수 렌더링 담당

이 패턴 덕분에 "권한", "GPS", "네비게이션", "스낵바", "실제 UI"가 한 파일에 뒤엉키지 않습니다.

#### `feature/station-list/StationListViewModel.kt`

이 프로젝트에서 가장 중요한 파일 중 하나입니다. 목록 화면의 설계 철학이 거의 다 들어 있습니다.

이 ViewModel은 세 가지 입력을 합칩니다.

- 영속 설정: `UserPreferences`
- 세션 상태: 권한, GPS, 현재 좌표, 로딩 플래그
- 검색 결과: repository가 제공하는 `StationSearchResult`

이걸 합쳐서 `StationListUiState`를 만듭니다.

왜 이런 구조인가:

- 정렬, 유종, 반경 같은 값은 장기 선호이므로 DataStore 기반 영속 상태여야 함
- 권한, 현재 좌표, 로딩 여부는 세션 상태여야 함
- 주유소 목록 자체는 repository가 조합한 읽기 모델이어야 함

즉, 이 ViewModel은 모든 것을 직접 계산하는 곳이 아니라 **서로 성격이 다른 상태를 한 화면 모델로 조립하는 곳**입니다.

이 구분이 중요한 이유는, 영속 상태와 세션 상태를 섞기 시작하면 나중에 "앱을 다시 켰을 때 유지돼야 하는가?" 같은 질문이 모호해지기 때문입니다.

#### `feature/settings/*`

설정 기능은 구조를 이해하기 좋은 가장 단순한 예시입니다.

- Route: ViewModel 연결
- ViewModel: 설정 액션을 저장소 업데이트로 변환
- Screen: UI 렌더링

이 모듈을 먼저 읽으면 이 프로젝트의 기본 패턴을 빠르게 익힐 수 있습니다. station-list보다 단순해서 입문용으로 좋습니다.

#### `feature/watchlist/*`

watchlist는 "읽기 모델이 별도로 존재하는 이유"를 이해하기 좋은 모듈입니다.

이 화면은 현재 검색 결과만 보여주는 것이 아니라, 과거에 관심 표시한 주유소를 비교 가능한 형태로 보여줘야 합니다. 그래서 `observeWatchlist(origin)` 같은 별도 도메인 경로가 존재합니다.

## `domain`은 어떤 관점으로 읽어야 하나

`domain`을 읽을 때는 "비즈니스 로직이 많냐 적냐"보다 **어떤 계약을 고정하고 있는가**를 봐야 합니다.

### `domain/station/StationRepository.kt`

이 인터페이스 하나만 봐도 앱의 핵심 유스케이스가 보입니다.

- 근처 주유소 관찰
- watchlist 관찰
- 새로고침
- 관심 상태 변경

왜 좋은가:

- feature는 "무엇이 가능해야 하는지"만 알고
- data는 "그걸 어떻게 구현하는지"를 안다

이 계약이 있기 때문에 feature는 Room/Retrofit/DAO 상세 구현을 몰라도 됩니다.

### `StationQuery`, `StationSearchResult`, `WatchedStationSummary`

이 모델들은 화면과 저장소 사이의 핵심 언어입니다.

- `StationQuery`: 검색 입력의 표준형
- `StationSearchResult`: 목록 화면이 받는 결과 형식
- `WatchedStationSummary`: 비교 화면 읽기 모델

왜 필요한가:

- 네트워크 응답 DTO를 바로 화면에 주지 않기 위해
- Room row를 화면 상태에 직접 새지 않게 하기 위해
- "검색", "비교", "관심 상태"라는 앱 언어를 코드에 드러내기 위해

### 얇은 use case를 왜 유지하나

현재 use case는 repository 호출 위임이 많아서 없어 보여도 될 수 있습니다. 그래도 남겨둔 이유는 경계 고정입니다.

- ViewModel이 repository 세부 API에 직접 달라붙지 않게 함
- 나중에 트랜잭션, 정책, 로깅, 권한 검사, 조합 로직이 늘어날 자리를 확보
- 기능 흐름을 파일명만 봐도 읽을 수 있게 함

즉, 지금의 얇음은 과설계라기보다 **확장 여지를 남긴 얇은 경계**에 가깝습니다.

## `data`는 왜 가장 중요하고 가장 늦게 읽어야 하나

이 프로젝트의 진짜 복잡도는 `data:station`에 몰려 있습니다. 하지만 가장 늦게 읽는 게 좋습니다. 이유는 이 층이 여러 상태를 합쳐서 읽기 모델을 만들기 때문에, 먼저 읽으면 맥락 없이 복잡하게 느껴지기 쉽기 때문입니다.

### `data/station/DefaultStationRepository.kt`

이 파일은 다음 책임을 동시에 가집니다.

- 캐시 스냅샷 읽기
- 가격 히스토리 읽기
- 관심 주유소 상태 읽기
- stale 여부 판단
- 목록 정렬
- watchlist 비교 모델 생성
- 새로고침 시 Room 스냅샷 교체 및 히스토리 적재

왜 repository에서 이 일을 하나:

- ViewModel에 두면 UI가 너무 많은 저장소 의미를 알게 됨
- DAO에 두면 화면 의미가 DB 계층에 스며듦
- domain에 두면 Android/Room/네트워크 인프라 세부가 올라옴

그래서 `data`가 "여러 데이터 원천을 조합해서 앱용 읽기 모델을 완성하는 계층"이 됩니다.

### `StationCachePolicy.kt`

겉보기에는 단순하지만 의미가 큽니다.

- stale 기준을 한 곳에 고정
- "오래됨"을 UI가 아니라 데이터 정책으로 다룸

왜 중요한가:

- stale 판정이 화면마다 다르면 같은 데이터가 화면마다 다르게 보일 수 있음
- repository가 freshness를 결정해야 `StationSearchResult`가 일관된 의미를 가짐

### `StationRemoteDataSource.kt`

네트워크 호출 세부는 repository에서 또 한 번 분리했습니다.

왜 분리했나:

- repository는 "갱신 파이프라인"에 집중
- remote data source는 "외부 API와 좌표 변환"에 집중

이 앱은 Kakao 좌표 변환과 Opinet 조회를 연쇄적으로 수행합니다. 이 로직을 repository 한가운데 넣어두면 저장소 구현이 지나치게 커지기 때문에 별도 데이터 소스로 분리한 것입니다.

### `data/settings/DefaultSettingsRepository.kt`

이 모듈은 비교적 단순합니다. 하지만 구조적으로 중요합니다.

`SettingsRepository`가 단순해 보여도 분리해 둔 이유는, 설정 저장 기술(DataStore)이 바뀌어도 feature/domain이 영향을 덜 받게 하려는 것입니다.

## `core`는 어떤 순서로 보면 되나

### `core:model`

가장 먼저 보세요. 값 객체가 앱 언어의 바닥을 이룹니다.

- `Coordinates`
- `DistanceMeters`
- `MoneyWon`

값 객체를 따로 둔 이유는 primitive 난립을 막기 위해서입니다. `Double`, `Int`가 코드 전역에 떠다니면 의미가 약해지고, 잘못된 단위 사용을 잡기 어렵습니다.

### `core:location`

위치 관련 코드를 `app`이 아니라 `core`로 내린 이유는 "실제 Android 위치 구현"도 재사용 가능한 인프라로 보기 때문입니다.

특히 현재 구현은 `Optional<DemoLocationOverride>`를 받아서 demo/prod를 한 구현 안에서 자연스럽게 갈라냅니다.

왜 이렇게 했나:

- `prod`는 실제 fused location 사용
- `demo`는 고정 좌표 공급
- 그러나 feature/domain은 둘을 구분할 필요가 없음

즉, flavor 차이를 상위 계층에 퍼뜨리지 않으려는 설계입니다.

### `core:network`

`NetworkRuntimeConfig`를 `app`에서 주입하고 `core:network`가 소비하는 구조도 의도가 분명합니다.

왜 이렇게 했나:

- API 키는 앱 런타임/빌드 환경의 책임
- 네트워크 모듈은 "키를 어떻게 받는지"보다 "키를 받아서 서비스를 만든다"에 집중

즉 `BuildConfig` 의존을 `core:network` 내부에 숨겨 넣지 않고, composition root에서 주입합니다. 이건 멀티모듈에서 흔히 중요한 경계입니다.

### `core:database`

Room 스키마를 여기 모은 이유는 당연해 보이지만, 이 프로젝트에서는 특히 중요합니다. 캐시/히스토리/watchlist가 모두 주유소 읽기 모델의 재료이기 때문입니다.

이 모듈을 볼 때는 테이블 하나씩보다, 세 저장소가 함께 어떤 역할을 하는지 보는 편이 좋습니다.

- `StationCache`: 마지막 성공 스냅샷
- `StationPriceHistory`: 가격 변화 계산 재료
- `WatchedStation`: 관심 상태와 비교 화면 기반

## 왜 `demo`와 `prod`를 이렇게 나눴나

이 프로젝트에서 `demo/prod` 분리는 단순한 "샘플 모드"가 아닙니다. **검토 가능한 레퍼런스 앱**으로 만들기 위한 핵심 설계입니다.

### `demo`가 필요한 이유

- API 키 없이도 바로 빌드/실행 가능해야 함
- 검토자가 동일한 데이터셋으로 동작을 확인할 수 있어야 함
- 가격 변화, stale 상태, watchlist 흐름을 재현 가능해야 함

그래서 `demo`는 다음을 가집니다.

- 고정 좌표 override
- 시드 캐시 데이터
- 실제 앱과 같은 상태 모델/화면 흐름

즉, "가짜 화면"이 아니라 **실제 앱 구조를 그대로 타되 데이터 원천만 결정적으로 고정한 모드**입니다.

### `prod`가 필요한 이유

`prod`는 진짜 API 키와 실제 위치를 사용합니다. 중요한 건 기능 구조는 demo와 거의 같고, 달라지는 건 입력원과 시크릿 검증뿐이라는 점입니다.

이렇게 해야 demo 검증이 실제 구조와 멀어지지 않습니다.

### startup hook으로 나눈 이유

`AppStartupRunner`와 `AppStartupHook` 구조는 flavor별 초기화를 깔끔하게 분리하려는 장치입니다.

- `DemoSeedStartupHook`: 검토용 DB 시드
- `ProdSecretsStartupHook`: 시크릿 누락 빠른 실패

둘을 분리하지 않으면 `if (demo) ... else ...`가 앱 시작 코드 전반에 퍼지게 됩니다. 지금 구조는 그 분기를 startup hook 바인딩 지점으로 몰아넣습니다.

## 왜 캐시와 stale 처리를 이렇게 했나

이 앱은 "지금 네트워크 호출이 실패했으니 화면을 비운다"는 방식 대신, **마지막 성공 결과를 유지하고 stale 상태를 드러내는 방식**을 선택했습니다.

이 선택은 UX와 아키텍처 둘 다에 영향을 줍니다.

### UX 관점

- 사용자는 빈 화면보다 마지막 성공 결과를 더 유용하게 느낍니다.
- 가격이 최신이 아닐 수 있다는 사실만 분명히 알려주면 됩니다.

### 아키텍처 관점

- 화면은 `fresh/stale` 의미를 결과 모델로 받기만 하면 됨
- repository는 캐시 교체와 실패 시 유지 정책을 책임짐
- Room은 마지막 성공 스냅샷의 근거가 됨

그래서 stale 처리는 UI 장식이 아니라 데이터 정책입니다.

## 작업할 때는 어디서부터 찾으면 되나

### 1. 목록 화면을 바꾸고 싶다

아래 순서로 보세요.

1. `feature/station-list/StationListScreen.kt`
2. `feature/station-list/StationListUiState.kt`
3. `feature/station-list/StationListItemUiModel.kt`
4. 필요하면 `StationListViewModel.kt`

화면 문구, 카드 배치, 버튼 위치 같은 변경은 대개 여기서 끝납니다.

### 2. 목록에 들어가는 데이터 의미를 바꾸고 싶다

아래 순서로 보세요.

1. `feature/station-list/StationListViewModel.kt`
2. `domain/station/model/*`
3. `data/station/DefaultStationRepository.kt`

예를 들어 가격 변화 계산 기준, stale 표시 기준, 정렬 기준을 바꾸려면 이 라인으로 내려가야 합니다.

### 3. 설정 저장 동작을 바꾸고 싶다

아래 순서로 보세요.

1. `feature/settings/SettingsViewModel.kt`
2. `domain/settings/SettingsRepository.kt`
3. `data/settings/DefaultSettingsRepository.kt`
4. `core/datastore/*`

### 4. 위치나 권한 흐름을 바꾸고 싶다

아래 순서로 보세요.

1. `feature/station-list/StationListRoute.kt`
2. `core/location/ForegroundLocationProvider.kt`
3. `core/location/AndroidForegroundLocationProvider.kt`
4. `app/src/demo/kotlin/com/gasstation/DemoLocationModule.kt`

### 5. API 호출이나 캐시 정책을 바꾸고 싶다

아래 순서로 보세요.

1. `domain/station/model/StationQuery.kt`
2. `data/station/StationRemoteDataSource.kt`
3. `data/station/DefaultStationRepository.kt`
4. `data/station/StationCachePolicy.kt`
5. `core/database/station/*`

### 6. 새로운 화면을 추가하고 싶다

대체로 다음 단계를 따르면 됩니다.

1. `feature:new-feature` 모듈 추가
2. `Route`, `ViewModel`, `Screen`, `UiState`, `Action` 구성
3. 필요한 domain 계약 추가
4. 필요한 data 구현 추가
5. `app/navigation/GasStationNavHost.kt`에 연결

핵심은 새 기능이 생겨도 `app`에 로직을 넣지 않고, navigation만 연결하도록 유지하는 것입니다.

## 이 프로젝트에서 헷갈리기 쉬운 포인트

### 1. 왜 `domain:settings`가 `domain:station` 타입을 일부 쓰나

사용자 선호값이 결국 유종, 브랜드, 정렬, 지도 제공자 같은 주유소 탐색 언어를 포함하기 때문입니다. 완전히 분리하려면 중복 enum이 생기고 변환 코드만 늘어납니다.

즉, 설정은 독립 기능이지만 **주유소 탐색 언어를 소비하는 기능**이기도 합니다.

### 2. 왜 watchlist는 목록 화면의 단순 하위 기능이 아닌 별도 모델을 가지나

watchlist는 "현재 검색 결과의 일부"가 아니라, **사용자가 저장한 장기 관심 집합**입니다. 현재 검색 스냅샷이 없어도 비교 화면을 보여줘야 하므로, 별도 읽기 모델과 조합 규칙이 필요합니다.

### 3. 왜 `sortOrder`는 캐시 키에 안 들어가나

정렬은 같은 데이터셋을 다른 방식으로 보여주는 문제이기 때문입니다. 캐시 키에 넣으면 같은 검색 결과를 불필요하게 중복 저장하게 됩니다.

### 4. 왜 브랜드 필터도 캐시 키에 안 들어가나

브랜드 필터 역시 스냅샷을 좁혀 보여주는 뷰 관심사에 가깝기 때문입니다. 네트워크 원본을 다시 받아야 하는 입력이 아니므로 캐시 키에서 제외한 것입니다.

### 5. 왜 외부 지도 연동은 `app`에 남아 있나

이건 Android `Intent`와 실제 앱 설치 여부를 다루는 플랫폼 결합 로직이기 때문입니다. 도메인 규칙이라기보다 앱 셸 책임에 가깝습니다.

## 빠르게 맥락 잡는 체크리스트

새 세션에서 다시 봐야 할 때는 아래만 확인해도 구조가 빨리 돌아옵니다.

- `settings.gradle.kts`: 모듈 전체 지도
- `docs/architecture.md`: 책임 분리 요약
- `app/navigation/GasStationNavHost.kt`: 사용자 흐름
- `feature/station-list/StationListViewModel.kt`: 상태 조합 핵심
- `data/station/DefaultStationRepository.kt`: 데이터 조합 핵심
- `docs/offline-strategy.md`: stale/캐시 정책

## 추천 탐색 명령

### 모듈 전체 파일 보기

```bash
rg --files app core data domain feature
```

### ViewModel/Repository 진입점 찾기

```bash
rg -n "@HiltViewModel|interface .*Repository|class .*Repository" app core data domain feature --glob '*.kt'
```

### 특정 기능 흐름 추적

```bash
rg -n "StationList|Watchlist|UserPreferences|StationQuery" app core data domain feature --glob '*.kt'
```

## 마지막 요약

이 프로젝트는 "클린 아키텍처 흉내"를 내기 위해 모듈을 늘린 것이 아닙니다. **검토 가능한 demo 경로**, **안정적인 prod 경로**, **캐시와 stale 의미의 일관성**, **기능별 수정 범위 예측 가능성**을 확보하려고 다시 나눈 구조입니다.

처음 볼 때는 아래 한 줄만 기억하면 됩니다.

> `app`은 조립하고, `feature`는 화면 상태를 만들고, `domain`은 계약을 고정하고, `data`는 읽기 모델을 조합하고, `core`는 공통 인프라를 제공한다.

이 틀만 잡히면, 새 기능을 추가하든 기존 기능을 고치든 어디를 먼저 봐야 할지 훨씬 빨리 판단할 수 있습니다.
