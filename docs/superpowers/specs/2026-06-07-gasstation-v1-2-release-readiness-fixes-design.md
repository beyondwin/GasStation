# GasStation v1.2 Release Readiness Fixes Design

> 작성일: 2026-06-07
> 기준 커밋: `a04ca39`
> 범위: v1.2 릴리스 메타데이터 마감, watchlist invalid cache fallback 결함 수정, proxy endpoint 설정 검증
> 사용자 플로우 영향: 주유소 목록/북마크/watchlist/외부 지도 handoff 흐름은 유지한다. 결함 경로에서는 crash 대신 가능한 fallback을 보여준다.
> 짝 구현 plan: `docs/superpowers/plans/2026-06-07-gasstation-v1-2-release-readiness-fixes.md`

## 목표

`v1.1.3..HEAD` 45개 커밋은 v1.2 hardening, proxy-ready network boundary, refresh transaction, module-boundary guard, mutation gate, clean-code fixes를 이미 담고 있다. 새 버전을 내기 전에 남은 작업은 세 가지다.

1. 릴리스 산출물의 버전 메타데이터를 실제 새 버전으로 마감한다.
2. `data:station` watchlist fallback에서 invalid cached row가 남긴 raw 가격을 delta 계산에 다시 쓰는 결함을 닫는다.
3. proxy endpoint mode의 설정 오류를 Retrofit 내부 예외가 아니라 GasStation 설정 오류로 빠르게 설명한다.

이 작업은 v1.2를 "기능 추가가 끝난 브랜치"에서 "태그를 찍어도 되는 릴리스 후보"로 바꾸는 마감 작업이다.

## 배경: 코드 리뷰에서 확인한 사실

### P1: 릴리스 메타데이터가 아직 `1.1.3`

- `app/build.gradle.kts`는 `versionCode = 7`, `versionName = "1.1.3"`이다.
- `README.md`의 현재 앱 버전도 `1.1.3` / `versionCode 7`이다.
- `CHANGELOG.md`의 v1.2 관련 내용은 아직 `Unreleased` 아래에 있다.
- `docs/release-notes/2026-06-07-v1.2.0.md`가 없다.
- `docs/deployment.md`는 새 버전 발행 시 version bump, CHANGELOG 이동, release note 작성, README 릴리스 인덱스 갱신을 필수 절차로 둔다.

따라서 현재 상태에서 `v1.2.0` 태그를 찍으면 태그와 앱 메타데이터가 서로 모순된다.

### P2: watchlist invalid cache fallback이 raw cached row를 다시 신뢰함

Round 2 clean-code fixes는 DB read boundary에서 `StationCacheEntity.toDomainStation`을 nullable로 바꿔 invalid coordinates/price를 예외 대신 스킵하게 했다. 목록 경로는 이 계약을 잘 사용한다.

하지만 watchlist 경로에는 아직 한 지점이 남았다.

- `WatchlistSummaryAssembler.toWatchedSummary`는 `cachedStation?.toDomainStation(origin)`이 null이면 history fallback으로 `Station`을 복원한다.
- 그런데 `resolvePriceDelta`는 `cachedStation != null`만 보고 `cachedStation.priceWon`을 `StationPriceDelta.from`에 넣는다.
- cached row의 가격이 음수라서 `toDomainStation`은 null을 반환했는데, 같은 invalid raw row가 delta 계산에서는 다시 current price로 쓰인다.

결과적으로 watchlist는 "invalid cached row + valid history + watched fallback" 조합에서 정상 fallback을 보여주지 못하고 `StationPriceDelta.from(currentPriceWon = -1)`의 `require`로 터질 수 있다.

핵심 결함은 `cachedStation` 존재와 `valid cached snapshot` 의미가 분리되지 않은 것이다.

### P3: proxy mode 설정 오류가 너무 늦고 불명확함

v1.2는 기본 direct Opinet 경로를 유지하면서 `gasstation.stationEndpointMode=proxy`, `gasstation.proxyBaseUrl=...`로 proxy endpoint mode를 빌드할 수 있게 한다.

현재 기본값은 안전하다.

- default `STATION_ENDPOINT_MODE`는 `direct`
- default `PROXY_BASE_URL`은 blank
- demo test는 이 기본값을 확인한다

하지만 명시적으로 proxy mode를 선택하고 `gasstation.proxyBaseUrl`을 비워 두거나 잘못된 URL을 넣으면 `NetworkModule.provideProxyStationService`가 Retrofit `.baseUrl(...)`까지 내려가서 Retrofit/HttpUrl 계층의 예외로 실패한다. 이는 v1.2의 "proxy readiness" 상태로는 부족하다. 설정 경계에서 GasStation이 설명하는 오류로 막아야 한다.

## 요구사항

### P1: v1.2.0 릴리스 메타데이터 마감

- R1.1 `app/build.gradle.kts`의 앱 버전을 `versionCode = 8`, `versionName = "1.2.0"`으로 올린다.
- R1.2 `CHANGELOG.md`의 현재 `Unreleased` 항목을 `## 1.2.0 - 2026-06-07` 섹션으로 이동한다.
- R1.3 새 `Unreleased` 섹션은 비워 두되 "릴리스 후 다음 변경 사항을 기록합니다." 문장을 유지한다.
- R1.4 `docs/release-notes/2026-06-07-v1.2.0.md`를 작성한다.
- R1.5 `README.md`의 현재 앱 버전과 릴리스 인덱스를 v1.2.0 기준으로 갱신한다.
- R1.6 physical-device benchmark를 새로 수집하지 않았으므로 README와 `docs/performance.md`의 성능 수치는 변경하지 않는다. v1.2.0 release note에는 기존 2026-05-18 물리 기기 수치가 최신 committed evidence이고, watchlist benchmark 재측정은 아직 미수행이라고 명시한다.

### P2: watchlist invalid cache fallback 결함 수정

- R2.1 `cachedStation != null`과 `cachedSnapshot != null`을 구분한다.
- R2.2 `cachedStation?.toDomainStation(origin)`이 null이면, delta와 lastSeen 계산도 cached row가 아니라 history fallback 기준으로 계산한다.
- R2.3 cached row가 유효하면 기존 동작을 유지한다.
  - station은 cached snapshot을 사용한다.
  - delta는 cached fetchedAt 이전 history와 cached current price를 비교한다.
  - lastSeen은 cached fetchedAt을 사용한다.
- R2.4 cached row가 invalid여도 `cachedStation.fuelType`은 history context 선택에 사용할 수 있다. fuel type은 string context이고, price/coordinates 불변식 실패와 독립적이다.
- R2.5 invalid cached price + valid history + watched fallback coordinates 조합은 crash 없이 history fallback station을 보여준다.

수용 기준:

- AC2.1 `WatchlistRepositoryTest`에 "invalid cached row를 history fallback delta 계산에서 무시한다" 테스트를 추가하면 수정 전에는 `IllegalArgumentException`으로 실패하고, 수정 후 통과한다.
- AC2.2 기존 `WatchlistRepositoryTest`의 cached snapshot 우선, latest cached snapshot fallback, fuel type context, invalid fallback coordinate 테스트가 모두 통과한다.
- AC2.3 `data:station` 외 계층의 공개 API는 바꾸지 않는다.

### P3: proxy endpoint mode 설정 검증

- R3.1 proxy mode에서 proxy base URL이 blank이면 명확한 `IllegalArgumentException`으로 실패한다.
- R3.2 proxy base URL이 absolute HTTP(S) URL이 아니면 명확한 `IllegalArgumentException`으로 실패한다.
- R3.3 path가 있는 base URL은 Retrofit 계약에 맞게 `/`로 끝나야 한다. `https://gasstation-proxy.example/api/`는 허용하고, `https://gasstation-proxy.example/api`는 거부한다.
- R3.4 host-only URL(`https://gasstation-proxy.example`)은 OkHttp `HttpUrl` 정규화 결과가 `/` path가 되므로 허용한다.
- R3.5 default direct mode는 지금처럼 blank proxy URL을 허용한다. direct mode에서는 proxy URL을 검사하지 않는다.

수용 기준:

- AC3.1 `NetworkRuntimeConfigTest` 또는 `NetworkModule` 테스트가 blank proxy base URL failure를 직접 검증한다.
- AC3.2 invalid URL과 non-trailing-slash path URL failure를 직접 검증한다.
- AC3.3 valid proxy URL은 `ProxyStationFetcher` provider를 만들 수 있다.
- AC3.4 `NetworkConfigResourceTest`는 default direct + blank proxy URL 계약을 계속 확인한다.

## 설계

### P1 설계: release-prep commit으로 분리

버전 bump와 릴리스 문서는 기능 수정과 섞지 않는다. P2/P3가 먼저 green이 된 뒤 마지막 commit에서 릴리스 메타데이터를 마감한다.

릴리스 버전은 `1.2.0`으로 둔다. `v1.1.3` 이후 변경이 patch 수준 문서 수정이 아니라 proxy-ready network boundary, DB transaction, CI/gate, mutation gate 같은 hardening 범위를 포함하므로 minor bump가 맞다.

release note는 새 성능 수치를 만들지 않는다. 이전 세션에서 물리 기기가 없던 상태에서 benchmark 숫자를 갱신하지 않기로 했고, `docs/performance.md`도 이 제약을 이미 설명한다.

### P2 설계: valid cached row를 명시적으로 파생

`toWatchedSummary` 시작부에서 아래 의미를 분리한다.

- `cachedSnapshot`: `cachedStation?.toDomainStation(origin)` 결과. null이면 price/coordinates 중 하나가 invalid이거나 cached row가 없다.
- `validCachedStation`: `cachedSnapshot != null`일 때만 raw `StationCacheEntity`를 delta/lastSeen 계산에 사용할 수 있는 값.
- `historyForContext`: 지금처럼 cached row fuel type이 있으면 같은 fuel type history만 본다. cached row 자체가 invalid이어도 fuel type context는 유지한다.

이후 함수들은 `validCachedStation`만 cached current price/fetchedAt source로 받는다. invalid cached row는 station fallback뿐 아니라 delta/lastSeen에서도 사라진다.

### P3 설계: provider 경계에서 base URL 검증

검증 위치는 `core:network`의 `NetworkModule.provideProxyStationService`가 소유한다. 이유는 다음과 같다.

- app은 BuildConfig 문자열을 runtime config로 전달하는 조립 계층이다.
- URL이 Retrofit base URL로 유효한지는 network provider 경계의 책임이다.
- `core:network`는 이미 OkHttp/Retrofit에 의존하므로 `HttpUrl` 검증을 새 의존성 없이 쓸 수 있다.

구현은 `requireValidProxyBaseUrl(baseUrl: String): String` private helper로 둔다. helper는 trim한 값을 `toHttpUrlOrNull`로 파싱하고, `encodedPath.endsWith("/")`를 확인한 뒤 정규화된 URL string을 반환한다.

## 비목표

- 새 proxy service 구현 또는 실제 proxy 배포.
- direct Opinet default를 proxy default로 바꾸는 것.
- Opinet API key 보안 모델 변경.
- 새 physical-device benchmark 수치 작성.
- watchlist UI 레이아웃 변경.
- DB schema/migration 변경.

## 위험 및 완화

- P2에서 invalid cached row의 fuel type을 history context로 계속 쓰는 것이 과하게 관대해 보일 수 있다. 하지만 fuel type은 cached row의 price/coordinates 불변식 실패와 별개이고, watchlist는 저장 항목 비교 화면이므로 같은 fuel type history를 보존하는 편이 사용자에게 덜 손실적이다.
- P3에서 host-only URL을 허용하면 사용자가 slash 없이 입력해도 동작한다. OkHttp가 `https://host`를 `https://host/`로 정규화하므로 Retrofit 계약과 충돌하지 않는다.
- P1에서 성능 숫자를 갱신하지 않으면 v1.2 release note가 덜 화려해 보일 수 있다. 그러나 물리 기기 증거 없이 숫자를 바꾸는 것이 더 큰 리스크다.

## 검증 기준

필수 fast 검증:

```bash
./gradlew :data:station:testDebugUnitTest :core:network:test :app:testDemoDebugUnitTest --console=plain
```

릴리스 최소 검증:

```bash
git diff --check -- README.md CHANGELOG.md CONTRIBUTING.md app/build.gradle.kts docs/deployment.md docs/verification-matrix.md docs/release-notes/*.md
./gradlew :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble
./gradlew :app:assembleProdRelease
```

변경 폭을 감안한 최종 권장 검증:

```bash
./gradlew spotlessCheck lint verifyModuleBoundaries --continue
./gradlew \
  :core:network:test \
  :data:station:testDebugUnitTest \
  :domain:station:test \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest \
  verifyRoborazziDebug \
  :app:assembleProdRelease \
  --console=plain
```
