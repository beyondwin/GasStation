> 이 문서는 1.0.2 시점의 분석 history입니다. 1.1.0 이후의 baseline 결정은 `docs/superpowers/specs/2026-05-11-production-baseline-design.md`가 단일 출처입니다.

# GasStation 프로젝트 심층 분석 보고서

작성일: 2026-05-05
원본 분석 기준 커밋: `40cb155` (`main`)
리뷰/보완 기준 커밋: `3629816` (`main`)
기준 버전: `1.0.2` (`versionCode 3`)

리뷰 기준 문서: `AGENTS.md`, `docs/agent-workflow.md`, `docs/module-contracts.md`, `docs/improvement-analysis.md`, `docs/verification-matrix.md`

## 개요

### 프로젝트 현황 요약

GasStation은 17개 활성 Gradle 모듈로 구성된 Android 멀티모듈 reference/portfolio 앱입니다. 한국 운전자가 현위치 기반으로 주변 주유소 가격·거리·브랜드를 비교하고 외부 지도 앱으로 길안내까지 연결하는 실사용 흐름을, `demo`(seed JSON 기반 재현) / `prod`(Opinet Open API 실호출) 두 정식 경로로 제공합니다.

코드 구성을 수치로 보면:

- 활성 모듈 17개 (`app`, `core:*` 6개, `domain:*` 3개, `data:*` 2개, `feature:*` 3개, `tools:demo-seed`, `benchmark`)
- Kotlin 소스 약 220+ 파일 (테스트 포함)
- Kotlin `2.3.20`, KSP `2.3.7`, AGP `9.1.1`, Compose BOM `2026.03.01`, Java 17 toolchain
- `compileSdk 35`, `minSdk 24`, `targetSdk 35`
- Room DB version 5, 4개의 explicit migration
- 이번 pass 이후 `docs/improvement-analysis.md`의 즉시/단기 backlog는 줄었고, 조건부 항목은 별도 판단 대상으로 남아 있습니다.

### 강점 요약

1. **클린 아키텍처 + 레이어 단방향 의존성이 실제로 강제됨.** AGENTS.md, `docs/module-contracts.md`가 "어떤 모듈이 무엇을 소유하면 안 되는가"를 표로 고정하고, settings 의존을 `core:datastore -> domain:settings`에서 끊고 storage-local DTO를 도입한 10-1 작업처럼 실제 코드와 일치합니다.
2. **상태 분리가 ViewModel 차원에서 정교함.** `StationListViewModel`이 `LocationStateMachine`(권한/GPS/주소)과 `StationSearchOrchestrator`(query/cache/blocking failure)로 책임을 나눠 단일 ViewModel 비대화를 방지합니다.
3. **오프라인/캐시 전략이 명시적이고 테스트로 보호됨.** `station_cache_snapshot`을 `station_cache`와 분리해 "성공한 빈 결과"와 "캐시 자체 없음"을 구분합니다. retry는 `Timeout`/`Network`만 1회, 500ms 후.
4. **빌드 컨벤션 플러그인으로 모듈별 build script 단순화.** `build-logic/convention`이 6개 plugin을 등록하고 공통 unit/UI test 의존성까지 흡수합니다.
5. **`demo`/`prod` 모두 정식 경로로 다룸.** `demo`는 mock이 아니라 `DemoSeedStartupHook`로 DB와 preferences를 매번 동일 상태로 초기화하는 deterministic 경로이며, screenshot/benchmark/UI 테스트의 기준입니다.
6. **문서가 단일 출처로 잘 분리됨.** AGENTS, project-reading-guide, module-contracts, architecture, state-model, offline-strategy, test-strategy, verification-matrix가 각자 책임을 분명히 가집니다.
7. **R8 minify on, backup 비활성화, cleartext 도메인 화이트리스트** 등 모바일 보안 기본 위생을 1.0.1에서 갖추었습니다.

### 주요 개선 기회와 필요성 판정

이 보고서의 개선 항목은 `docs/improvement-analysis.md`의 backlog를 대체하지 않습니다. 현재 기준에서 이 문서가 해야 할 일은 "새 backlog 단일 출처"가 아니라, 이미 식별된 항목과 새 제안을 비교해 실제 착수 가치가 있는지 판정하는 것입니다.

| 판정 | 항목 | 이유 |
| --- | --- | --- |
| 완료됨 | `proj4j` version catalog 등록 | `proj4j`는 `gradle/libs.versions.toml`의 version/library alias로 이동했고 `core/network/build.gradle.kts`는 `implementation(libs.proj4j)`를 사용합니다. 검증: `:core:network:test` 통과. |
| 완료됨 | CI workflow와 `verification-matrix.md` 동기화 | GitHub Actions `Verification Matrix`가 live source of truth인 `docs/verification-matrix.md`의 머지 전 범위에 맞춰 `:domain:location:test`, `:app:testProdDebugUnitTest`, `:tools:demo-seed:test`를 포함합니다. aggregate `:app:assembleDebug`와 release assemble은 포함하지 않습니다. 검증: workflow-equivalent Gradle command 통과. |
| 완료됨 | `MainDispatcherRule` 도입 | `feature:station-list` 테스트의 `Dispatchers.setMain/resetMain` 반복을 `MainDispatcherRule`로 중앙화했습니다. 검증: `:feature:station-list:testDebugUnitTest` 통과. |
| 완료됨 | watchlist ASCII test tag 분리 | watchlist selector는 `watchlist-card`, `watchlist-distance-metric` ASCII tag를 사용하고 한글 accessibility content description은 유지합니다. 검증: `:feature:watchlist:testDebugUnitTest` 통과. |
| 조건부 실행 | `values-night-v31/themes.xml` splash 추가 | 다크 모드 품질을 포트폴리오 평가 범위로 본다면 필요합니다. 앱이 light-only를 명시적으로 선택한다면 필수는 아닙니다. |
| 조건부 실행 | Gradle parallel/build cache 활성화 | correctness 문제가 아니라 개발 속도 문제입니다. 먼저 baseline 시간을 재고, 실패 task가 없을 때만 켭니다. `configuration-cache`는 별도 검증 전까지 보류합니다. |
| 조건부 실행 | backend proxy | 공개 배포, quota 비용, key abuse 리스크를 감수하는 순간 필요합니다. 현재 portfolio/reference 앱으로만 유지한다면 문서화된 한계 수용으로 충분합니다. |
| 조건부 실행 | 다크 모드 semantic color migration | feature가 정적 `ColorBlack`/`ColorYellow`를 많이 참조하는 것은 사실입니다. 다만 yellow/black/white identity가 강한 앱이므로 전면 치환보다 화면별 대비 검증 후 필요한 surface/text부터 고칩니다. |
| 지금 불필요 | `core/common`, `core/ui` 삭제 commit | `settings.gradle.kts`에 include되지 않고 `git ls-files`에도 잡히지 않는 build 산출물뿐입니다. repo 변경이 아니라 `./gradlew clean` 또는 로컬 디렉터리 청소 문제입니다. |
| 지금 불필요 | `StationListUiStateReducer` 추출 | 현재 `runningFold` projection으로 핵심 recomposition 비용은 이미 줄었습니다. 새 상태 입력이 늘어날 때 다시 판단하면 됩니다. |
| 지금 불필요 | Nav transition helper 파일 분리 | 동작 리스크가 없고 제품 가치가 낮은 순수 가독성 변경입니다. navigation 수정 작업이 생길 때 같이 처리합니다. |
| 지금 불필요 | Hilt `Optional<T>` nullable 전환 | 현재 경계가 동작하고 있고 Kotlin 미학 외 실질 문제가 작습니다. flavor binding을 건드리는 비용이 더 큽니다. |
| 지금 불필요 | Gson/Moshi 또는 kotlinx-serialization 전환 | 현재 네트워크 DTO 규모에서 R8 문제가 재현되지 않았습니다. API schema가 커지거나 ProGuard 문제가 생길 때 검토합니다. |

---

## 1. 아키텍처 분석

### 현황

- 의존 그래프: `app -> feature:* -> domain:* -> core:model`, `app -> data:* -> core:network/database -> core:model`, `app -> core:location -> domain:location`. `core:designsystem`은 `core:model`에만 의존 (브랜드 라벨 매핑 때문).
- 위치 경계는 `feature:station-list -> domain:location -> core:location` 단방향.
- 설정 쓰기는 `domain/settings/usecase/Update*UseCase`만 통과.
- `domain:settings`가 `core:model` enum을 public API로 게시(`UserPreferences`가 `Brand`, `FuelType` 등을 노출하므로). storage-local DTO(`StoredUserPreferences`)와 domain model 사이의 mapper는 `data:settings`가 소유.
- ViewModel 책임 분리: `StationListViewModel` (UI 조합) + `LocationStateMachine` + `StationSearchOrchestrator` 3개 컴포넌트.

### 강점

- Hilt 모듈도 layer를 따라 분산: `core:network/di/NetworkModule`, `core:database/DatabaseModule`, `core:datastore/UserPreferencesDataStoreModule`, `app/di/*`. `app`은 외부 handoff(`ExternalMapModule`, `AnalyticsModule`)와 startup hook 바인딩만 담당.
- 정책(`StationCachePolicy`, `StationRetryPolicy`)은 일반 클래스로 분리해 ViewModel/Repository에서 주입 가능 — 단위 테스트가 쉬움.
- `StationSearchOrchestrator`의 `CachedSnapshotState`(`Unknown`/`Present`/`Absent`)와 `PendingBlockingFailure`는 비동기 race(failure 먼저, observation 나중)를 명시적으로 처리합니다.

### 개선점

1. **app 모듈 단일 화면 시작점 분기 가독성.** `GasStationNavHost.kt` 하단의 `forwardEnterTransition`/`forwardExitTransition`/`backwardEnterTransition`/`backwardExitTransition` 4개 함수는 navigation 패키지 전용 helper인데 NavHost 정의 위치와 200줄 이상 떨어져 있습니다. 별도 `NavTransitions.kt` 파일로 추출 가치 있음.
2. **`StationListViewModel`이 여전히 Hilt + StateFlow + UseCase + EventLogger + dispatch까지 8개 의존성.** 분리는 잘 되었지만 테스트 fixture 수가 늘어 `StationListViewModelTest`에서 helper boilerplate가 많음. `StationListUiStateReducer` 같은 pure reducer 추출을 추가로 고려할 수 있습니다.
3. **`combine(... 5개 flow)` UI 조합.** `StationListViewModel.kt`에서 `combine`이 5개 flow를 받는데, 추가 입력이 더 늘어나면 6-arity 한계 또는 가독성 저하 위험.
4. **`Optional<DemoLocationOverride>`, `Optional<SeedStationRemoteDataSource>` 사용.** Hilt의 OptionalBinding 제약으로 java `Optional`을 노출하는데, Kotlin idiomatic은 nullable + `@BindsOptionalOf` + `Provider<T?>`. 현재 구조도 동작하지만 Kotlin signature 일관성 면에서 개선 여지.

### 구체적 제안

UI state 조합을 reducer로 분리:

```kotlin
// feature/station-list/src/main/kotlin/.../StationListUiStateReducer.kt
internal class StationListUiStateReducer {
    fun reduce(
        preferences: UserPreferences,
        location: LocationState,
        transient: StationListTransientState,
        searchProjection: StationListSearchUiProjection,
        blockingFailure: StationListFailureReason?,
    ): StationListUiState = StationListUiState(
        currentCoordinates = location.currentCoordinates,
        // ... 기존 매핑
    )
}
```

ViewModel은 `combine(...)`만 호출하고 reducer를 그대로 위임합니다. 단위 테스트가 ViewModel scope/coroutine 없이도 가능해집니다.

**비즈니스 임팩트:** 추가 상태를 도입할 때 ViewModel을 건드리지 않고 reducer 단위 테스트만 추가하면 되어, station-list 회귀 테스트 작성 비용이 줄어듭니다.

**필요성 판정:** 지금은 실행하지 않습니다. 현재 `StationListViewModel`은 `LocationStateMachine`, `StationSearchOrchestrator`, `runningFold` projection으로 이미 주요 책임과 비용을 분리했습니다. reducer 추출은 새 상태 입력이 늘어나거나 ViewModel 테스트 변경 비용이 더 커질 때 착수합니다.

---

## 2. 코드 품질 분석

### 현황

- 코드 스타일은 Kotlin official, `nonTransitiveRClass=true`, `useAndroidX=true`. lint config는 `OldTargetApi`/`GradleDependency` 등 4개를 ignore.
- Magic number는 `DEFAULT_BUCKET_METERS = 250`, `RETRY_DELAY_MS = 500L`, `withTimeoutOrNull(10_000)` 등 commit 위치마다 적절히 상수화.
- 데이터 클래스 + sealed interface 패턴 활발 사용: `StationListAction`, `StationListEffect`, `LocationLookupResult`, `LocationAcquisitionResult`, `RefreshOutcome`, `CachedSnapshotState`.
- `Coordinates.init`에 invariant guard (`require(latitude in -90.0..90.0)`).

### 강점

- Magic 문자열을 `core:designsystem/BrandLabels.kt`로 단일 출처화해 `RTX = "고속도로알뜰"` 같은 라벨 불일치 회귀를 차단.
- 거리 계산 Haversine 공식이 `data/station` 내부에 함수형으로 분리(`distanceBetween`).
- 의도적 격리 패턴: `StationEventLogger.logSafely`가 `CancellationException`은 rethrow하고 일반 Exception만 격리. fatal error 삼키기 회피.

### 개선점

1. **한글 test tag 분리 완료.** `WatchlistSemantics.kt`는 `watchlist-card`, `watchlist-distance-metric` ASCII selector와 `WATCHLIST_CARD_CONTENT_DESCRIPTION = "관심 주유소 카드"` 접근성 텍스트를 분리합니다. `:feature:watchlist:testDebugUnitTest`가 통과했습니다.
2. **`LegacyCloseIcon`/`WatchlistCloseIcon` 중복 Canvas 코드.** 두 feature가 동일한 close 아이콘을 보유합니다. 중복은 사실이지만 화면 수가 둘뿐이라 test tag 정리보다 우선순위는 낮습니다.
3. **`StationListScreen.kt` 950+ 라인.** 읽기 비용은 있지만 제품 결함은 아닙니다. 화면 변경이 생길 때 card/state block 단위로 점진 분리하는 정도가 적절합니다.
4. **`DefaultStationRepository.toBrand()` 내부 fallback이 silent.** `String.toBrand(): Brand = Brand.entries.firstOrNull { it.name == this } ?: Brand.ETC`. Opinet 응답 코드 변경 감지를 강화할 수 있지만, 지금 이를 위해 새 logging 의존성을 data 계층에 넣을 필요는 없습니다.
5. **`historyForWatchlistContext`의 `maxBy` 방어성.** `isEmpty()` guard가 있어 현재 crash는 아닙니다. `maxByOrNull` 전환은 data:station을 만질 때 같이 하는 cleanup입니다.
6. **`legacyYellow`, `legacyBlack` 토큰 명명 잔재.** 이름은 아쉽지만 public visual contract가 이미 테스트로 고정되어 있습니다. 다크 모드 작업과 묶어 판단합니다.

### 구체적 제안

`WatchlistSemantics.kt`의 ASCII test tag 분리 결과:

```kotlin
// feature/watchlist/src/main/kotlin/.../WatchlistSemantics.kt
const val WATCHLIST_CARD_CONTENT_DESCRIPTION = "관심 주유소 카드"
const val WATCHLIST_CARD_TEST_TAG = "watchlist-card"
const val WATCHLIST_DISTANCE_METRIC_TAG = "watchlist-distance-metric"
```

`Brand.fromCode()` factory는 지금 필수 작업이 아닙니다. 구현한다면 `Brand` enum에 companion object를 명시적으로 추가하거나 data 계층의 mapper 함수로 유지해야 합니다. unknown brand 관측이 필요해지는 시점에는 `domain:station`의 event/logger 계약을 통해 알리고, 단순히 `data:station`에 새 logging 의존성을 추가하지 않습니다.

**비즈니스 임팩트:** UI test selector를 한글에서 ASCII로 바꿔 IDE 검색·refactor 도구 안정성이 좋아지고, 접근성 텍스트 변경과 테스트 selector 변경을 분리했습니다.

---

## 3. 테스트 전략 분석

### 현황

- 테스트 파일은 `feature:*`, `core:*`, `data:*`, `domain:*`, `app`, `tools:demo-seed`, `benchmark` 모두에 존재.
- 계층별 unit test + Robolectric (Android library) + Compose UI test (jvm Robolectric) + connected device test (`androidTest`).
- 핵심 회귀 보호 파일: `DefaultStationRepositoryTest`, `StationCachePolicyTest`, `StationRetryPolicyTest`, `StationListViewModelTest`, `LocationStateMachineTest`, `StationSearchOrchestratorTest`, `GasStationDatabaseMigrationTest`.
- `app:src/testProd`/`testDemo`로 flavor-specific test source set 구분.
- `app:connectedDemoDebugAndroidTest`로 실제 기기/에뮬레이터 watchlist 플로우 검증.
- `:core:location:connectedDebugAndroidTest`로 API 33+ Geocoder callback path device smoke.

### 강점

- DAO 단위로 deterministic latest row SQL을 직접 테스트.
- Migration test가 `version 5` 기준 모든 step을 통과시킴.
- `kotlinx-coroutines-test`, Turbine, MockWebServer를 적절히 분리해 사용.
- Hilt 테스트 instrumentation은 `HiltTestRunner`로 일원화.
- `app:testProdDebugUnitTest`가 `prod` flavor 그래프와 secret 검증을 강제.

### 개선점

1. **`StationListViewModelTest`의 `Dispatchers.setMain`/`resetMain` 반복 해소.** JUnit4 `MainDispatcherRule`이 `feature:station-list` test infra에 추가됐고 `:feature:station-list:testDebugUnitTest`가 통과했습니다.
2. **CI workflow와 verification-matrix 권장 세트 동기화.** 현재 CI에는 `:domain:location:test`, `:app:testProdDebugUnitTest`, `:tools:demo-seed:test`가 포함되어 있습니다. workflow-equivalent Gradle command가 통과했고, release assemble은 비용 대비 효과를 별도로 판단해야 합니다.
3. **`prodRelease`/`demoRelease` assemble이 CI에 없음.** `isMinifyEnabled = true` 활성화 후 R8 회귀를 자동으로 잡고 싶다면 필요합니다. 모든 PR에서 돌릴지, release/minify 관련 변경에서만 돌릴지는 CI 시간 기준으로 결정합니다.
4. **screenshot/UI snapshot 테스트 미도입.** station card price-first hierarchy를 시각적으로 보호할 수 있지만, 현재 단위/Compose 테스트가 이미 넓어 즉시 필수는 아닙니다.
5. **watchlist silent-discard 경로는 이미 테스트가 있음.** `WatchlistRepositoryTest`에 `observeWatchlist drops watched entries with no last known snapshot or history`가 존재하므로 새 P1 작업이 아닙니다.

### 구체적 제안

CI 강화 (`.github/workflows/android.yml`) 결과:

```yaml
- name: Verification Matrix
  run: |
    ./gradlew \
      :core:model:test \
      :core:designsystem:testDebugUnitTest \
      :domain:location:test \
      :domain:station:test \
      :domain:settings:test \
      :core:database:testDebugUnitTest \
      :core:datastore:testDebugUnitTest \
      :core:location:testDebugUnitTest \
      :core:network:test \
      :data:settings:testDebugUnitTest \
      :data:station:testDebugUnitTest \
      :feature:settings:testDebugUnitTest \
      :feature:station-list:testDebugUnitTest \
      :feature:watchlist:testDebugUnitTest \
      :app:testDemoDebugUnitTest \
      :app:testProdDebugUnitTest \
      :tools:demo-seed:test \
      :app:assembleDemoDebug \
      :app:assembleProdDebug \
      :benchmark:assemble
```

이 matrix는 live source of truth인 `docs/verification-matrix.md`의 머지 전 권장 회귀 세트에 맞춰졌습니다. `:app:testProdDebugUnitTest`는 포함하고, aggregate `:app:assembleDebug`와 `demoRelease`/`prodRelease` assemble은 포함하지 않습니다. release assemble은 R8 검증을 CI 기본값으로 끌어올릴지 결정한 뒤 추가합니다.

`MainDispatcherRule` 도입 결과:

```kotlin
// feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/MainDispatcherRule.kt
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

**비즈니스 임팩트:** CI는 문서가 약속한 demo/prod 회귀 범위와 맞아졌고, dispatcher rule은 테스트의 반복 boilerplate를 줄였습니다. 이미 존재하는 watchlist silent-discard 테스트는 유지 대상으로만 봅니다.

---

## 4. 성능 최적화

### 현황

- `:benchmark` 모듈이 cold start, watchlist 진입, baseline profile generation을 측정.
- UI projection이 `runningFold`로 station list identity 재사용.
- DB level: `station_cache_snapshot` 마커, `station_price_history` keep-latest-10, `pruneOlderThan` 7일 cutoff.
- DAO `observeLatestStationsByIds`가 SQL deterministic tie-breaker로 station별 1행만 반환.
- OkHttp call/connect/read timeout 4–8초.

### 강점

- `runningFold` UI projection은 매 emission마다 전체 list 재할당하던 회귀를 막은 의미 있는 최적화.
- `combine` 5개 flow의 distinctUntilChanged 적용으로 불필요한 emission 차단.
- Room SQL 단계에서 deterministic latest row 보장 — Kotlin sort 제거로 `O(N log N)` 회피.

### 개선점

1. **Gradle 병렬 빌드/build cache 비활성화.** 17개 모듈이라 개선 가능성은 있지만 correctness 문제가 아닙니다. 실제 필요성은 baseline 빌드 시간과 CI 병목 여부로 판단합니다.
2. **`distanceBetween` Haversine을 station마다 다시 계산.** 현재 목록 규모에서는 병목 근거가 약합니다. benchmark에서 station list projection이 병목으로 잡힐 때만 캐싱을 검토합니다.
3. **OkHttp client에 명시적 `ConnectionPool`/HTTP cache 미설정.** Retrofit/OkHttp 기본 connection pooling이 있으므로 지금 필수는 아닙니다. Opinet 응답 캐시 정책을 제품적으로 정한 뒤 검토합니다.
4. **Geocoder 호출이 모든 location refresh마다 발생.** 동일 좌표 refresh가 잦다는 사용 증거가 있으면 필요합니다. 현재는 주소 라벨 품질보다 캐시 복잡도 증가가 더 클 수 있습니다.

### 구체적 제안

`gradle.properties` 점진 활성화:

```properties
org.gradle.parallel=true
org.gradle.caching=true
# configuration-cache는 Hilt/KSP 호환성 별도 검증
# org.gradle.configuration-cache=true
```

주소 라벨 캐시:

```kotlin
// LocationStateMachine.kt
fun resolveAddressLabel(coordinates: Coordinates): String? {
    val current = state.value
    if (current.currentCoordinates == coordinates && current.currentAddressLabel != null) {
        return current.currentAddressLabel
    }
    return getCurrentAddress(coordinates).toLabel()
}
```

**비즈니스 임팩트:** parallel build 활성화는 로컬 개발자/CI 빌드 시간을 줄일 수 있지만, 이 프로젝트에서는 먼저 현재 matrix 실행 시간을 재야 합니다. 주소 라벨 캐시는 동일 좌표 새로고침 시 Geocoder 호출을 줄일 수 있습니다.
**필요성 판정:** 성능 항목은 전부 "측정 후 실행"입니다. 현재 코드에는 `runningFold` projection, SQL latest row, cache pruning처럼 실제 회귀 위험이 큰 성능 문제는 이미 처리되어 있습니다. 추가 최적화는 benchmark 또는 CI 시간 측정 결과가 있을 때만 착수합니다.

---

## 5. 보안 분석

### 현황

- Prod API key는 사용자별 `~/.gradle/gradle.properties`의 `opinet.apikey`에서 `BuildConfig.OPINET_API_KEY`로 주입.
- network security config가 cleartext를 `www.opinet.co.kr`만 허용.
- `android:allowBackup="false"`로 backup 비활성화.
- `ProdSecretsStartupHook`가 빈 키일 때 fail-fast.
- R8 `minifyEnabled=true`로 release 코드 축소.

### 강점

- 위치 권한 분리(`PreciseGranted`/`ApproximateGranted`/`Denied`)와 `Priority.PRIORITY_BALANCED_POWER_ACCURACY` fallback.
- ViewModel/Repository가 secret을 직접 들고 있지 않고 `core:network`로 주입 경계 유지.
- HTTPS 미사용은 Opinet API 자체가 HTTP인 외부 제약 — 도메인 화이트리스트로 격리.

### 개선점

1. **`BuildConfig.OPINET_API_KEY`는 APK에 평문 포함** — 공개 배포 시 backend proxy 필수.
2. **OkHttp logging/redaction은 현재 결함이 아님.** logging interceptor를 아직 쓰지 않으므로 지금 추가할 필요는 없습니다. 향후 네트워크 로그를 추가할 때 query param의 `code=<api key>` redaction을 필수 조건으로 둡니다.
3. **release build에 `proguard-rules.pro` 검증 자동화 없음.** R8 회귀를 CI에서 잡고 싶다면 release assemble을 추가합니다. CI 시간이 문제면 release 관련 변경에서만 실행합니다.
4. **debug build에서 prod debug와 prod release가 같은 applicationId 공유.** 포트폴리오 앱에서 prod debug를 실제 prod 앱과 동시에 설치해야 하는 요구가 없다면 지금 바꿀 필요는 없습니다.

### 구체적 제안

API key redaction interceptor (debug 모드만):

```kotlin
// core/network/src/debug/.../RedactingLoggingInterceptor.kt
internal class RedactingLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val redacted = request.url.newBuilder()
            .setQueryParameter("code", "<redacted>")
            .build()
        Timber.tag("opinet").d("→ ${request.method} $redacted")
        return chain.proceed(request)
    }
}
```

**비즈니스 임팩트:** 향후 이슈 디버깅 시 secret 노출 없는 안전한 로그 흐름 마련.
**필요성 판정:** backend proxy와 log redaction은 현재 portfolio/reference 범위에서는 "문서화하고 대기"가 맞습니다. 공개 배포, 공유 CI 로그, 네트워크 logging 도입 중 하나가 생기면 필수 작업으로 승격합니다.

---

## 6. 유지보수성 및 확장성

### 현황

- 모듈 경계 문서화가 매우 강함(`docs/module-contracts.md`이 "이 모듈에 두지 말 것" 컬럼까지 보유).
- AGENTS.md가 작업자에게 "활성 모듈은 `settings.gradle.kts` 기준"이라고 못박음.
- Convention plugin이 6개로 안정화.
- Version catalog가 38개 library + 6개 plugin alias를 가짐.

### 강점

- 새 기능 추가 시 "domain → data → core → feature → app" 순서가 모듈 계약으로 enforce됨.
- 새 brand나 fuel type 추가는 `core:model` enum 1곳 변경 → 매퍼만 갱신.
- demo seed 도구가 별도 JVM module(`tools:demo-seed`)로 분리되어 앱 런타임과 격리.

### 개선점

1. **`core/common`, `core/ui` 비활성 디렉터리.** `settings.gradle.kts`에 include되지 않고 `git ls-files core/common core/ui` 결과도 비어 있습니다. 현재 남은 것은 build 산출물뿐이므로 repo commit 대상이 아니라 로컬 workspace hygiene입니다.
2. **`proj4j` catalog 등록 완료.** `proj4j`는 `gradle/libs.versions.toml`의 version/library alias로 등록됐고 `core/network/build.gradle.kts`는 `implementation(libs.proj4j)`를 사용합니다. 검증은 `:core:network:test`가 통과했습니다.
3. **`docs/superpowers/specs/`, `docs/superpowers/plans/` 이력 문서가 현재 기준과 어긋날 수 있음.** `docs/project-reading-guide.md`가 이미 live 문서 우선 원칙을 설명하므로 새 AGENTS 섹션은 불필요합니다.
4. **Hilt Module의 의존성 wire 지점이 5–6개 파일에 분산** — dependency map 다이어그램이 README에 있으면 신규 기여자 온보딩에 도움.

### 구체적 제안

비활성 디렉터리 정리:

```bash
./gradlew clean
find core/common core/ui -maxdepth 2 -type f -print 2>/dev/null
git ls-files core/common core/ui
```

위 명령에서 tracked 파일이 없으면 commit을 만들지 않습니다. 로컬 build 산출물만 남은 상태라면 `clean` 또는 수동 workspace 청소로 충분합니다.

`proj4j` version catalog 등록 결과:

```toml
# gradle/libs.versions.toml
[versions]
proj4j = "1.4.1"

[libraries]
proj4j = { module = "org.locationtech.proj4j:proj4j", version.ref = "proj4j" }
```

```kotlin
// core/network/build.gradle.kts
implementation(libs.proj4j)
```

**비즈니스 임팩트:** dependency 감사(Renovate/Dependabot)가 모든 library를 한 곳에서 추적, version drift 방지.

---

## 7. 의존성 관리

### 현황

- Gradle 9.x, AGP 9.1.1, Kotlin 2.3.20, KSP 2.3.7 — 매우 최신.
- Compose BOM `2026.03.01`, Material 3, Material Icons Extended.
- Hilt 2.59.2, Room 2.8.4, DataStore 1.2.1, Retrofit 3.0.0, OkHttp 5.3.2.
- `accompanist-permissions 0.37.3`, `coreLibraryDesugaring 2.1.5`.
- Test: JUnit 4.13.2, kotlinx-coroutines-test 1.10.2, Turbine 1.2.1, Robolectric 4.16.1, MockWebServer.

### 강점

- 거의 모든 의존성이 catalog 통합.
- BOM 사용으로 Compose 라이브러리 버전 일관성 자동 보장.
- `coreLibraryDesugaring` 활성화로 `java.time` API 사용에 따른 minSdk 제약 회피.
- KSP 사용으로 KAPT 대비 빌드 시간 단축.

### 개선점

1. **`proj4j` 카탈로그 등록 완료** (6절에서 완료 상태와 검증을 기록).
2. **`accompanist-permissions`는 Google이 이미 AndroidX Compose permissions API로 이전 권고.** deprecation path 모니터링 필요.
3. **`Gson` converter** — `kotlinx.serialization` 또는 `Moshi` 같은 reflection-less JSON 라이브러리 검토 가치. R8 + Gson은 reflection rule 작성 부담이 있음.
4. **release build R8 활성화 후 Hilt/Room/Retrofit/Gson ProGuard 규칙 검증 필요.**

### 구체적 제안

JVM convention plugin에 test 의존성 흡수:

```kotlin
// build-logic/.../GasStationJvmLibraryConventionPlugin.kt
override fun apply(target: Project) = with(target) {
    pluginManager.apply("org.jetbrains.kotlin.jvm")

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

    dependencies {
        add("testImplementation", libs.findLibrary("junit").get())
        add("testImplementation", libs.findLibrary("kotlin-test").get())
        add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    }
}
```

**비즈니스 임팩트:** domain/jvm 모듈 build.gradle.kts 단순화, 신규 jvm 모듈 추가 시 boilerplate 제거.

---

## 8. 데이터 계층 분석

### 현황

- Room 4 entity: `StationCacheEntity`, `StationCacheSnapshotEntity`, `StationPriceHistoryEntity`, `WatchedStationEntity`. DB version 5.
- Migration 1→2(add price history + watchlist), 2→3(re-key history with fuelType), 3→4(introduce snapshot marker), 4→5(add latest-by-station index).
- `StationCachePolicy(staleAfter=5분, retainFor=7일)`.
- `StationRetryPolicy(RETRY_DELAY_MS=500ms, isRetryable=Timeout/Network only)`.
- DataStore: 커스텀 key-value plain text serializer, version="2", 사용자 설정 5개.
- Network: Retrofit + Gson + OkHttp, KATEC 좌표 변환은 `core:network` 내부 `LocalKoreanCoordinateTransform`(proj4j 활용).

### 강점

- `station_cache_snapshot` 마커로 "성공 빈 결과"와 "캐시 없음" 구분 — blocking failure 판단의 단일 출처.
- Remote → entity → domain mapper가 명확하게 분리.
- DAO transaction(`replaceSnapshot`, `pruneOlderThan`)으로 atomic update.
- Migration test가 schema validation을 자동화.

### 개선점

1. **`UserPreferencesSerializer` plain text format은 schema migration 도구 부재.** 새 키 추가 시 명시적 migration 코드는 없고 `defaultValue` fallback에 의존.
2. **`fetchedAtEpochMillis` based pruning은 clock skew 영향을 받을 수 있음.** 다만 refresh 성공 후 새 snapshot을 저장한 다음 cutoff pruning을 호출하므로 현재 구조에서 즉시 빈 화면 결함으로 보기에는 근거가 약합니다.
3. **`WatchedStationEntity`의 `brandCode`는 `Brand.name` string.** enum rename을 하지 않는 한 문제는 없습니다. brand code를 stable wire value로 분리할 때 같이 봅니다.

### 구체적 제안

clock skew 방어는 지금 바로 구현하지 않습니다. 구현하려면 먼저 `StationCacheDao`에 "가장 최신 snapshot 조회" 계약을 추가해야 하며, 이는 DAO/API/test surface를 늘립니다. 현재는 기존 `pruneOlderThan` repository 호출 경로 테스트와 snapshot 보존 테스트를 유지하는 것이 충분합니다.

**비즈니스 임팩트:** 데이터 계층에서 지금 필요한 작업은 새 기능보다 기존 cache/watchlist/pruning 테스트 유지입니다. clock skew guard는 실제 재현 사례나 product requirement가 생길 때 설계합니다.

---

## 9. UI/기능 분석

### 현황

- Material 3 + Compose, `core:designsystem`이 토큰(color, typography, spacing, corner, stroke), 공통 chrome을 소유.
- `core:designsystem/BrandLabels.kt`가 brand/filter 표시 라벨 단일 출처.
- 화면 정보 위계: 가격 → 거리 → 역명 → 유종/브랜드 → watch toggle.
- 상태 분기: Permission required, GPS required, Initial loading, Failure(blocking), Empty results, Results.
- 다크 모드 색상 scheme는 `GasStationThemeDefaults`에 정의되지만 feature가 정적 토큰을 직접 참조해 다크 적용이 부분적.

### 강점

- 한 디자인 시스템(metric, supporting-info, row, status, guidance)이 station-list/watchlist/settings 모두에 적용.
- Pull-to-refresh + 별도 `RefreshingStatusRail`로 새로고침 시 사용자 피드백 강화.
- semantic role, `stateDescription`, `contentDescription`, test tag가 일관되게 적용.

### 개선점

1. **다크 모드 부분 처리.** 정적 컬러 토큰 직접 참조로 `DarkColorScheme`이 일부만 적용.
2. **Splash 다크 override 부재.** `values-night-v31/themes.xml` 누락.
3. **사용자 표시 문자열 하드코딩.** Kotlin 소스에 직접 포함된 안내 메시지들이 있습니다. 다만 현재 i18n 요구가 없고 feature가 Android resource ownership 전략을 아직 갖고 있지 않아 전면 이동은 시기상조입니다.
4. **Empty state의 안내 문구가 소극적.** "다시 시도"만 노출됩니다. 단, 설정 진입은 top bar에 이미 있으므로 blocking 결함은 아닙니다.

### 구체적 제안

Empty state에 settings로 가는 빠른 link는 선택 사항입니다. 추가한다면 `StationListScreen`의 기존 `onSettingsClick`을 `StationListContent`와 `EmptyState`까지 내려야 합니다.

```kotlin
@Composable
private fun EmptyState(
    onAction: (StationListAction) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GasStationGuidanceCard(
        modifier = modifier,
        title = "조건에 맞는 주변 주유소가 없습니다.",
        body = "반경, 유종, 브랜드 조건을 조정하거나 다시 조회해보세요.",
        actionLabel = "조건 변경",
        onAction = onSettingsClick,
        secondaryActionLabel = "다시 시도",
        onSecondaryAction = { onAction(StationListAction.RetryClicked) },
    )
}
```

다크 모드 점진 마이그레이션:

```kotlin
// Feature에서는 정적 ColorBlack 대신 semantic role 사용
val textColor = MaterialTheme.colorScheme.onBackground
val surfaceColor = MaterialTheme.colorScheme.surface
```

**비즈니스 임팩트:** empty state에 직접 행동 유도가 들어가면 사용자가 결과 0건 상황에서 즉시 반경/브랜드를 넓힐 수 있어 success path 도달율 향상.

**필요성 판정:** UI/기능 항목 중 즉시 필요한 것은 watchlist test tag 분리와, 다크 모드 지원을 유지하기로 결정한 경우의 splash night resource입니다. 문자열 resource화와 empty state secondary action은 product/i18n 요구가 생길 때 착수합니다.

---

## 10. 필요성 기준 실행 플랜

### 판정 기준

아래 조건을 만족하지 않으면 "좋은 정리"여도 지금 하지 않습니다.

- 사용자 플로우, demo/prod 안정성, 테스트 신뢰성, build reproducibility 중 하나를 실제로 개선한다.
- 변경 파일의 소유 모듈이 `docs/module-contracts.md`와 맞다.
- 완료 기준과 검증 명령이 명확하다.
- 이미 `docs/improvement-analysis.md`나 `docs/superpowers/plans/2026-05-05-remaining-risk-resolution.md`에서 완료된 작업을 중복하지 않는다.

### 완료됨

| 순서 | 작업 | 파일 | 왜 필요한가 | 검증 |
| --- | --- | --- | --- | --- |
| 1 | `proj4j` version catalog 등록 | `gradle/libs.versions.toml`, `core/network/build.gradle.kts` | `proj4j`가 version/library alias로 이동했고 `implementation(libs.proj4j)`로 참조됩니다. | `./gradlew :core:network:test` 통과 |
| 2 | CI matrix 누락분 보강 | `.github/workflows/android.yml` | CI가 `docs/verification-matrix.md`의 머지 전 범위에 맞춰 `:domain:location:test`, `:app:testProdDebugUnitTest`, `:tools:demo-seed:test`를 포함합니다. aggregate `:app:assembleDebug`와 release assemble은 제외했습니다. | workflow-equivalent Gradle command 통과 |
| 3 | `MainDispatcherRule` 도입 | `feature/station-list/src/test/...` | `StationListViewModelTest`의 Main dispatcher setup 반복을 rule로 중앙화했습니다. | `./gradlew :feature:station-list:testDebugUnitTest` 통과 |
| 4 | watchlist test tag ASCII 분리 | `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSemantics.kt`, `WatchlistScreen.kt`, `WatchlistScreenTest.kt` | 접근성 한글 텍스트와 테스트 selector를 분리했습니다. | `./gradlew :feature:watchlist:testDebugUnitTest` 통과 |

구현 결과:

1. `proj4j`는 `[versions] proj4j = "1.4.1"`와 `[libraries] proj4j = { module = "org.locationtech.proj4j:proj4j", version.ref = "proj4j" }`로 등록됐고 `implementation(libs.proj4j)`로 교체됐습니다.
2. CI는 `:domain:location:test`, `:app:testProdDebugUnitTest`, `:tools:demo-seed:test`를 포함합니다. `:app:assembleDemoRelease`, `:app:assembleProdRelease`, aggregate `:app:assembleDebug`는 live verification matrix 기준에서 제외했습니다.
3. dispatcher rule은 `StandardTestDispatcher` 기본 rule로 `Dispatchers.setMain/resetMain`을 중앙화했습니다.
4. watchlist는 `WATCHLIST_DISTANCE_METRIC_TAG = "watchlist-distance-metric"` 같은 selector와 `WATCHLIST_CARD_CONTENT_DESCRIPTION = "관심 주유소 카드"` 같은 접근성 텍스트를 분리했습니다.

### 조건부 실행

| 작업 | 실행 조건 | 지금 판단 |
| --- | --- | --- |
| `values-night-v31/themes.xml` splash 추가 | 다크 모드를 포트폴리오 품질 기준에 포함한다 | 조건부 필요. light-only 전략이면 생략 가능 |
| release assemble을 CI 기본 matrix에 추가 | PR당 CI 시간이 감당 가능하고 R8 회귀를 빨리 잡아야 한다 | 조건부 필요. 먼저 CI 시간 측정 |
| Gradle parallel/build cache 활성화 | baseline 대비 의미 있는 시간 개선이 있고 verification matrix가 안정적이다 | 측정 후 결정 |
| close icon 공통 primitive | settings/watchlist 외 세 번째 사용처가 생기거나 디자인 시스템 작업을 이미 하는 중이다 | 보류 |
| 다크 모드 semantic color migration | 실제 dark screenshot에서 price-first hierarchy나 대비 문제가 확인된다 | 화면별로 점진 처리 |
| 사용자 표시 문자열 resource화 | i18n 또는 resource 기반 테스트 전략이 제품 요구가 된다 | 지금은 보류 |
| backend proxy | 공개 배포, quota 비용, key abuse 리스크가 생긴다 | 현재 portfolio/reference 범위에서는 문서화로 충분 |

### 지금 하지 않을 것

| 항목 | 하지 않는 이유 |
| --- | --- |
| `core/common`, `core/ui` 삭제 commit | tracked source가 아니라 build 산출물뿐입니다. repo 변경이 아니라 로컬 clean 문제입니다. |
| `StationListUiStateReducer` 추출 | 현재 projection 최적화가 이미 있고, 새 상태 입력이 늘기 전에는 구조 변경 비용이 더 큽니다. |
| Nav transition helper 추출 | 제품/테스트 안정성 개선이 거의 없는 가독성 변경입니다. navigation 수정 때 같이 처리합니다. |
| Hilt `Optional<T>` nullable 전환 | 현재 flavor binding이 동작합니다. Kotlin idiom만으로 DI 표면을 흔들 필요는 없습니다. |
| `distanceBetween`/Geocoder 캐시 | 병목 증거가 없습니다. benchmark나 실제 호출 문제가 먼저 필요합니다. |
| Gson 교체 | 현재 DTO/R8 문제가 재현되지 않았습니다. 네트워크 schema가 커질 때 검토합니다. |
| `historyForWatchlistContext` 단독 cleanup | crash가 아니고 silent-discard 테스트도 이미 있습니다. data:station 수정이 생길 때 같이 처리합니다. |

---

## 결론

GasStation 1.0.2는 portfolio/reference 앱이라는 목표에 부합하는 production-grade 멀티모듈 Android 구조를 갖추고 있습니다. **clean architecture가 문서로 강제되고 코드로 일치**한다는 점이 가장 큰 강점이며, 캐시/오프라인/retry/event logging 같은 회귀 위험 영역이 정책 객체와 단위 테스트로 분리되어 있습니다.

현재 기준으로 "정말 필요한" 네 가지 작업인 **`proj4j` catalog 등록, CI matrix 누락 보강, Main dispatcher test rule, watchlist test tag 분리**는 완료됐습니다. 다크 splash, release assemble CI, Gradle parallel/build cache, dark semantic migration은 조건부 작업입니다.

이 프로젝트가 production 배포로 확장한다면, **backend proxy, key restriction, quota monitoring**을 가장 먼저 설계해야 하며, 그 외 영역은 현재의 module-contracts/test-strategy/verification-matrix 문서 체계 위에서 점진 개선이 가능합니다.
