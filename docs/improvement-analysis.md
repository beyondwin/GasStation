# GasStation 개선 분석

> 작성일: 2026-05-05
> 기준 커밋: `2c5e8bd`
> 분석 범위: 빌드 설정, 보안, 성능, 버그, 코드 품질, 테스트, UI/접근성, 아키텍처
> 검증 방식: 로컬 소스, `settings.gradle.kts`, `docs/module-contracts.md`, `docs/agent-workflow.md`, `docs/test-strategy.md`, `docs/verification-matrix.md` 기준으로 재확인

이 문서는 현재 코드 기준으로 개선 가능한 사항을 심각도와 영역별로 정리한 단일 출처입니다. 구현 계획서가 아니라 개선 backlog이므로, 각 항목을 착수할 때는 관련 모듈 계약과 테스트를 다시 확인해야 합니다.

## 사용 원칙

- 실제 secret 값은 이 문서에 적지 않습니다. 이미 노출된 값은 `<redacted>`로만 표현합니다.
- 항목을 수정할 때는 `demo`와 `prod`가 모두 정식 경로라는 전제를 유지합니다.
- `app`은 조립과 외부 handoff, `feature:*`는 화면 상태와 effect, `domain:*`는 계약, `data:*`는 저장소 정책, `core:*`는 공유 인프라라는 경계를 우선합니다.
- 문서의 파일 경로와 라인 번호는 분석 시점 기준입니다. 구현 직전에는 `rg`와 실제 파일을 다시 확인합니다.
- 보안/빌드 항목은 작은 변경처럼 보여도 배포 결과와 개발자 onboarding에 영향을 주므로 README, 검증 명령, demo/prod 경로를 함께 점검합니다.

## 태그

| 태그 | 의미 |
| --- | --- |
| `[위험]` | 즉시 수정이 필요한 보안 또는 안정성 문제 |
| `[버그]` | 특정 조건에서 런타임 오류나 잘못된 동작을 일으키는 결함 |
| `[성능]` | 사용자 체감 또는 리소스 측면에서 개선할 수 있는 비효율 |
| `[미완성]` | 계약은 존재하지만 구현이 연결되지 않은 코드 경로 |
| `[품질]` | 기술 부채, 가독성, 일관성 문제 |
| `[테스트]` | 회귀 보호가 약한 구간 |
| `[접근성]` | 다크 모드, 스크린 리더, 로컬라이제이션 관련 결함 |
| `[빌드]` | Gradle 설정, 의존성, 모듈 구성 문제 |

## 우선순위 요약

| 우선순위 | 항목 | 핵심 검증 |
| --- | --- | --- |
| 완료 확인 | 1-1/1-2 secret 파일 정리, 1-3 cleartext 범위 축소, 1-4 backup 비활성화, 1-5 모바일 클라이언트 API 키 한계 문서화, 2-1 KSP 버전 수정, 2-4 release minification, 3-1/3-2/3-3 cache/UI 성능 항목, 4-1 retry catch 범위 축소, 5-1 이벤트 emit 연결, 6-2 Geocoder API 교체, 7-1 브랜드 라벨 단일 출처 | 관련 unit/resource/migration 테스트와 docs diff check |
| 남은 즉시/단기 | 2-2/2-3/2-6 build hygiene, 4-2 watchlist fallback 방어성, 8-1/8-2 테스트 보강 | 항목별 검증 |
| 장기 | 2-5 테스트 컨벤션 플러그인, 6-1 status bar API, 9-1/9-2 theme/string resource 정리, 10-1 datastore 의존 방향 재검토 | 전체 회귀 세트 또는 관련 모듈 matrix |

## 추가/보완/수정 관점

원본 분석을 그대로 나열하기보다, 아래 세 관점으로 읽으면 다음 작업을 정하기 쉽습니다.

| 관점 | 문서에서 반영한 내용 | 해당 항목 |
| --- | --- | --- |
| 추가 | 원본에 없거나 약했던 리스크를 새 항목으로 분리했습니다. 모바일 클라이언트에 들어간 API 키의 한계, 비활성 모듈 디렉터리 정리 절차, event emit 의미 결정처럼 구현 전에 결정을 요구하는 항목입니다. | 1-5, 2-6, 5-1 |
| 보완 | 기존 항목에 소유 모듈, 완료 기준, 검증 명령, demo/prod 영향, 문서 갱신 후보를 붙였습니다. 구현자가 "좋은 지적"에서 멈추지 않고 바로 task로 자를 수 있게 하는 보강입니다. | 전체 항목, 특히 1-1, 1-3, 3-2, 7-1, 9-1 |
| 수정 | 원본 분석 중 과하거나 부정확한 표현을 정정했습니다. secret 원문 노출 제거, `maxBy` crash 분류 완화, `resetMain()` 누출 설명 정정, KSP 버전 판단을 release 확인 기반으로 변경한 부분입니다. | 1-1, 2-1, 4-2, 8-1 |

### 추가 후보 판단 기준

새 항목을 이 문서에 더 넣을 때는 아래 조건 중 하나를 만족해야 합니다.

- 실제 사용자 흐름에서 가격 비교 속도, demo/prod 안정성, watchlist 비교, 외부 지도 handoff 중 하나를 개선합니다.
- AGENTS/module contract 관점에서 소유 경계가 흐려지는 문제를 막습니다.
- 이미 존재하는 테스트나 문서가 약속한 동작을 더 잘 보호합니다.
- 보안/빌드/배포처럼 작게 보여도 실패하면 개발자가 앱을 실행하지 못하게 됩니다.

### 보완 우선순위 판단 기준

기존 항목을 보완할 때는 "권장 조치"보다 "완료 기준"과 "검증"을 먼저 선명하게 만듭니다. 개선 backlog는 구현 상세보다 성공 조건이 더 중요합니다.

### 수정 우선순위 판단 기준

분석이 실제 코드와 다르면 심각도를 낮추거나 표현을 바꿉니다. 특히 `[버그]`, `[위험]` 태그는 재현 조건이나 피해 범위가 분명할 때만 유지합니다.

---

## 1. 보안

### 1-1. API 키가 버전 관리 파일에 포함됨 `[완료됨]`

**파일:** `gradle.properties`

**소유:** `app` build config, README/runtime setup

```properties
daum.apikey=<redacted>
opinet.apikey=<redacted>
```

두 키가 프로젝트 루트 `gradle.properties`에 평문으로 저장되어 있던 문제입니다. 프로젝트 파일의 tracked assignment는 제거됐고, README는 사용자별 `~/.gradle/gradle.properties` 또는 `-Popinet.apikey=<issued-key>` 입력을 안내합니다.

현재 `app/build.gradle.kts`는 이미 `providers.gradleProperty("opinet.apikey").orElse("")`로 값을 읽습니다. 문제는 읽는 코드보다 키를 프로젝트 공용 파일에 둔 운영 방식입니다.

**권장 조치:**
- `gradle.properties`에서 실제 키 값을 제거합니다.
- 로컬 실행은 `~/.gradle/gradle.properties` 또는 `./gradlew -Popinet.apikey=...` 같은 사용자별 입력으로 안내합니다.
- README의 예시에서 "프로젝트 `gradle.properties`"를 권장 경로로 보이지 않게 수정합니다.
- 이미 원격 저장소에 push된 키라면 키 폐기/재발급과 히스토리 정리 여부를 별도 판단합니다.

**완료 기준:**
- tracked 설정/소스 파일에 `apikey=` 실제 값 assignment가 남지 않습니다. 문서의 `<redacted>`, `<issued-key>`, 빈 값 예시는 secret scan에서 제외합니다.
- `prod` 빌드는 키가 없을 때도 assemble 가능하고, 실제 `prod` 실행은 기존 `ProdSecretsStartupHook`처럼 fail-fast 됩니다.
- README는 `demo` 실행에는 키가 필요 없고 `prod` 실행에만 사용자별 `opinet.apikey`가 필요하다는 점을 명확히 설명합니다.

**검증:** `git diff --check`, tracked config/source 대상 secret assignment scan, `./gradlew :app:assembleDemoDebug :app:assembleProdDebug :app:testProdDebugUnitTest`

---

### 1-2. 미사용 Daum API 키 고아 `[완료됨]`

**파일:** `gradle.properties`

**소유:** runtime setup, 외부 지도 handoff 문서

`daum.apikey`는 루트 `gradle.properties`에 존재했지만 현재 `buildConfigField`, Kotlin 소스, Gradle task에서 참조되지 않았습니다. tracked assignment는 제거됐고, 지도 handoff는 앱별 URI와 package name으로 처리되며 Daum/Kakao API 키를 사용하지 않습니다.

**권장 조치:**
- 현재 제품 경로에서 필요 없다면 제거합니다.
- 향후 지도 SDK/API 연동 계획이 있다면 별도 설계 문서에서 키 범위, 보관 위치, `demo`/`prod` 차이를 먼저 정의합니다.

**완료 기준:** `rg -n "daum\\.apikey|DAUM|KAKAO.*API"` 결과가 실제 런타임 필요성을 설명하거나, 프로젝트에서 완전히 제거됩니다.

**검증:** `git diff --check`, `./gradlew :app:assembleDemoDebug`

---

### 1-3. HTTP cleartext 트래픽 전역 허용 `[완료됨]`

**파일:** `app/src/main/AndroidManifest.xml`

**소유:** `app`, `core:network`

```xml
android:usesCleartextTraffic="true"
```

Opinet API가 HTTP endpoint를 요구하므로 일부 cleartext 허용은 불가피합니다. 전역 `usesCleartextTraffic`는 제거됐고, app network security config가 `www.opinet.co.kr` 정확한 도메인에만 cleartext를 허용합니다.

**권장 조치:**
- `app/src/main/res/xml/network_security_config.xml`을 추가해 Opinet 도메인에만 cleartext를 허용합니다.
- manifest에서는 `android:networkSecurityConfig="@xml/network_security_config"`를 연결하고 전역 `usesCleartextTraffic`는 제거합니다.
- 실제 base URL이 `opinet.co.kr` 하위 도메인인지 `core:network`의 Retrofit/OkHttp 설정과 함께 확인합니다.

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">www.opinet.co.kr</domain>
    </domain-config>
</network-security-config>
```

**완료 기준:** Opinet 통신만 cleartext 예외가 되고 다른 도메인은 Android 기본 정책을 따릅니다. 현재 구현은 `includeSubdomains="false"`로 `www.opinet.co.kr`만 허용합니다.

**검증:** `./gradlew :app:processDemoDebugMainManifest :app:testDemoDebugUnitTest --tests com.gasstation.NetworkSecurityConfigResourceTest :core:network:test`

---

### 1-4. Android 백업 규칙 미설정 `[완료됨]`

**파일:** `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/AndroidManifest.xml`

**소유:** `app`, `core:database`, `core:datastore`

`AndroidManifest.xml`에는 `android:allowBackup="true"`가 설정되어 있었고 backup/data extraction 규칙은 샘플 주석 위주였습니다. Room DB에는 캐시, 가격 히스토리, watchlist가 있고 DataStore에는 사용자 설정이 있습니다. 현재 구현은 포트폴리오/reference 앱 범위에서 이 로컬 상태를 백업 대상으로 보지 않기로 하고 Android backup/data extraction을 비활성화했습니다.

**권장 조치:**
- 포트폴리오/reference 앱 기준으로 백업 자체가 불필요하면 `allowBackup="false"`를 선택합니다.
- 백업을 유지한다면 캐시 DB와 일시 데이터는 `<exclude>`로 명시하고, watchlist와 사용자 설정을 복원할지 제품 의사결정을 남깁니다.
- `data_extraction_rules.xml`의 TODO 주석은 실제 정책으로 교체합니다.

**완료 기준:** 백업 대상과 제외 대상이 코드와 문서에서 일치합니다. 현재 결정은 백업 대상 없음이며 manifest의 `allowBackup=false`, backup/data extraction resource 미참조, app resource test로 보호합니다.

**검증:** `./gradlew :app:testDemoDebugUnitTest --tests com.gasstation.BackupPolicyResourceTest`

---

### 1-5. `BuildConfig`에 주입된 prod API 키는 APK에서 추출 가능 `[완료됨]`

**파일:** `app/build.gradle.kts`, `app/src/prod/kotlin/com/gasstation/startup/ProdSecretsStartupHook.kt`, `core/network`

**소유:** `app`, `core:network`, product security policy

`opinet.apikey`가 `BuildConfig.OPINET_API_KEY`로 들어가는 구조는 모바일 클라이언트 배포물에서 키가 완전히 비밀일 수 없다는 한계를 가집니다. README와 architecture는 현재 포트폴리오/reference 앱에서는 이 단순화를 수용하지만, 공개 배포 또는 quota 비용이 큰 API라면 backend proxy, key restriction, quota monitoring을 먼저 설계해야 한다고 명시합니다.

**권장 조치:**
- 단기: README나 architecture 문서에 "클라이언트 내 API 키는 secret boundary가 아니다"라는 한계를 명시합니다.
- 장기: 실제 서비스 배포를 목표로 할 때 backend proxy, key restriction, quota monitoring을 검토합니다.

**완료 기준:** 현재 구조를 수용하는 이유 또는 대체 구조가 문서화되어 있습니다.

**검증:** 문서 갱신이면 `git diff --check`; proxy 도입이면 별도 architecture/test matrix 필요

---

## 2. 빌드 및 의존성

### 2-1. KSP 버전과 Kotlin 버전 쌍 불일치 `[완료됨]`

**파일:** `gradle/libs.versions.toml`

**소유:** build logic

```toml
kotlin = "2.3.20"
ksp = "2.3.6"
```

KSP Gradle plugin은 Kotlin compiler와 맞는 release line을 사용해야 합니다. `gradle/libs.versions.toml`의 KSP 값은 Kotlin `2.3.20`에 맞춰 `2.3.7`로 갱신됐습니다.

**권장 조치:**
- Kotlin `2.3.20`에 대응하는 KSP release를 확인해 `libs.versions.toml`의 `ksp` 값을 교체합니다.
- 버전 변경 후 Hilt/Room KSP가 걸린 app/data/core database 테스트를 우선 실행합니다.

**완료 기준:** KSP plugin resolution이 성공하고 Room/Hilt generated source가 필요한 테스트와 assemble이 통과합니다.

**검증:** `./gradlew :app:assembleDemoDebug :app:assembleProdDebug :core:database:testDebugUnitTest :data:station:testDebugUnitTest`

---

### 2-2. `proj4j` 버전 카탈로그 미등록 `[빌드]`

**파일:** `core/network/build.gradle.kts`, `gradle/libs.versions.toml`

**소유:** `core:network`, build logic

```kotlin
implementation("org.locationtech.proj4j:proj4j:1.4.1")
```

`proj4j`만 버전 카탈로그를 거치지 않고 하드코딩되어 있습니다. 의존성 감사와 버전 일관성 유지에 사각지대가 생깁니다.

**권장 조치:** `libs.versions.toml`에 `proj4j` version/library alias를 추가하고 `core/network/build.gradle.kts`에서 `implementation(libs.proj4j)`로 참조합니다.

**완료 기준:** dependency coordinate가 카탈로그에 모이고 좌표 변환 테스트가 그대로 통과합니다.

**검증:** `./gradlew :core:network:test`

---

### 2-3. Gradle 병렬 빌드 비활성화 `[빌드]`

**파일:** `gradle.properties`

**소유:** build setup

```properties
# org.gradle.parallel=true
```

현재 활성 모듈은 `settings.gradle.kts` 기준 17개입니다. 병렬 빌드를 켜면 로컬/CI 빌드 시간이 줄 수 있지만, task isolation 문제는 실제 빌드로 확인해야 합니다.

**권장 조치:**
- `org.gradle.parallel=true`를 활성화한 뒤 빠른 로컬 확인과 머지 전 회귀 세트를 비교합니다.
- `org.gradle.configuration-cache=true`는 Hilt/KSP/Room, demo seed task와의 호환성을 별도 이슈로 검토합니다.

**완료 기준:** 병렬 빌드 활성화 후 기존 verification matrix가 안정적으로 통과하고, 실패 시 어느 task가 공유 상태를 가정하는지 기록됩니다.

**검증:** `./gradlew --parallel :app:assembleDemoDebug :app:testDemoDebugUnitTest :benchmark:assemble`

---

### 2-4. 릴리즈 빌드에 코드 축소 비활성화 `[완료됨]`

**파일:** `app/build.gradle.kts`

**소유:** `app`

```kotlin
isMinifyEnabled = false
```

`proguard-rules.pro`가 존재하지만 release build에서 R8/ProGuard가 실행되지 않던 항목입니다. release `isMinifyEnabled`는 `true`로 바뀌었고, resource shrinking은 Compose/splash/icon/external map 리소스 확인 전까지 보류됐습니다.

**권장 조치:**
- `release`에서 `isMinifyEnabled = true`를 켜고 `proguard-rules.pro`를 검증합니다.
- 리소스 축소(`isShrinkResources = true`)는 Compose, splash/icon resource, external map query에 영향이 없는지 확인한 뒤 적용합니다.

**완료 기준:** `demoRelease`와 `prodRelease`가 빌드되고 app startup, 외부 지도, Room/Hilt 경로가 깨지지 않습니다.

**검증:** `./gradlew :app:assembleDemoRelease :app:assembleProdRelease :app:testDemoDebugUnitTest :app:testProdDebugUnitTest`

---

### 2-5. 테스트 컨벤션 플러그인 부재 `[빌드]`

**파일:** `build-logic/convention/*`, 각 모듈 `build.gradle.kts`

**소유:** build logic

`gasstation.android.library`, `gasstation.android.feature`, `gasstation.android.room` 등의 컨벤션 플러그인은 있지만 테스트 의존성(`junit`, `robolectric`, `turbine`, `coroutines-test`)을 중앙화하는 플러그인은 없습니다. 여러 모듈이 비슷한 테스트 의존성을 반복 선언합니다.

**권장 조치:**
- Android unit test 공통 묶음과 JVM unit test 공통 묶음을 분리합니다.
- feature 모듈 Compose UI test 의존성은 별도 opt-in으로 둡니다.
- Robolectric SDK, `unitTests.isIncludeAndroidResources`, coroutine test dependency를 한 곳에서 관리합니다.

**완료 기준:** 중복 dependency 선언이 줄고, 기존 test task 표면이 바뀌지 않습니다.

**검증:** `./gradlew :feature:station-list:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:watchlist:testDebugUnitTest :core:database:testDebugUnitTest`

---

### 2-6. 빈 `core/ui`, `core/common` 디렉터리 `[빌드]`

**파일:** `core/ui`, `core/common`, `settings.gradle.kts`

**소유:** repository hygiene

두 디렉터리는 `settings.gradle.kts`에 include되어 있지 않은 비활성 모듈입니다. 현재 남아 있는 내용은 build 산출물뿐입니다.

**권장 조치:**
- 먼저 `./gradlew clean` 후에도 디렉터리가 남는지 확인합니다.
- 소스나 문서가 없다면 삭제해 모듈 탐색 혼란을 줄입니다.
- 활성 모듈 판단은 파일시스템이 아니라 `settings.gradle.kts` include 기준이라는 AGENTS 계약을 유지합니다.

**완료 기준:** 비활성 build 산출물 디렉터리가 사라지고, `settings.gradle.kts`의 활성 모듈 목록과 문서가 일치합니다.

**검증:** `git status --short`, `./gradlew :app:assembleDemoDebug`

---

## 3. 성능

### 3-1. UI state combine이 매 emission마다 전체 목록을 재매핑 `[완료됨]`

**파일:** `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt`

**소유:** `feature:station-list`

`preferences`, `locationStateMachine.state`, `transientState`, `searchOrchestrator.searchResult`, `searchOrchestrator.blockingFailure` 중 하나라도 emission이 발생하면 `result.stations.map(::StationListItemUiModel)`이 전체 목록을 새로 매핑하던 항목입니다. `StationListViewModel`은 search UI projection을 분리해 station source list가 같고 freshness/metadata만 바뀐 경우 기존 mapped item list identity를 재사용합니다.

**권장 조치:**
- `searchResult`에서 `stations.map(::StationListItemUiModel)`을 먼저 파생하고 `distinctUntilChanged()`를 적용할 수 있는지 확인합니다.
- `LocationState`, `StationListTransientState`, `blockingFailure`는 값 안정성이 충분한지 확인한 뒤 필요한 곳에만 `distinctUntilChanged()`를 둡니다.
- Compose recomposition 감소가 목표이므로 UI 테스트와 state equality 계약을 함께 봅니다.

**완료 기준:** 동일한 station result 재방출이 같은 UI list를 불필요하게 재생성하지 않고, loading/failure/freshness UI는 기존처럼 갱신됩니다.

**검증:** `./gradlew :feature:station-list:testDebugUnitTest`

---

### 3-2. `pruneOlderThan` 미호출로 cache snapshot이 누적됨 `[완료됨]`

**파일:** `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`, `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`

**소유:** `data:station`, `core:database`

`StationCacheDao.pruneOlderThan()`은 구현되어 있지만 프로덕션 repository 경로에서 호출되지 않던 항목입니다. `DefaultStationRepository.refreshNearbyStations()`는 성공 persistence 뒤 `StationCachePolicy.retainFor` 기본 7일 cutoff로 오래된 `station_cache`와 `station_cache_snapshot`을 정리합니다.

**권장 조치:**
- `DefaultStationRepository.refreshNearbyStations()` 성공 후 또는 별도 maintenance use case에서 cutoff 기반 pruning을 호출합니다.
- 현재 refresh한 snapshot을 같은 transaction/clock 기준으로 실수로 삭제하지 않도록 cutoff를 보수적으로 잡습니다.
- TTL은 `StationCachePolicy`와 별도 상수로 둘지, 같은 정책 객체가 소유할지 결정합니다.

**완료 기준:** 오래된 cache row와 snapshot row가 정리되고, 현재 query의 snapshot marker와 빈 결과 marker는 유지됩니다.

**검증:** `./gradlew :core:database:testDebugUnitTest :data:station:testDebugUnitTest`

---

### 3-3. watchlist 최신 캐시 조회가 Kotlin 정렬에 의존 `[완료됨]`

**파일:** `core/database/src/main/kotlin/com/gasstation/core/database/station/StationCacheDao.kt`, `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`

**소유:** `data:station`, `core:database`

`observeLatestStationsByIds()`는 SQL에서 정렬된 전체 row를 반환하고, repository의 `latestByStationId()`가 다시 정렬 후 `groupBy`로 station별 최신 row를 선택하던 항목입니다. DAO SQL이 station별 deterministic latest row만 반환하고 repository는 `associateBy { stationId }`만 수행합니다. DB version 5 migration은 stationId 선행 latest index를 추가합니다.

**권장 조치:**
- DAO 이름처럼 "station별 최신 row"만 반환하도록 SQL을 좁히거나, SQL 정렬을 신뢰해 Kotlin 중복 정렬을 제거합니다.
- tie-breaker(`fuelType`, `radiusMeters`, bucket)가 제품적으로 의미 있는지 먼저 결정합니다.

**완료 기준:** watchlist fallback 의미는 유지하면서 최신 cache 선택 비용이 줄고, 동일 station의 여러 fuel/radius snapshot tie-breaker가 테스트로 고정됩니다.

**검증:** `./gradlew :data:station:testDebugUnitTest`

---

## 4. 런타임 결함 및 방어성

### 4-1. `StationRetryPolicy`가 retry 실패에서 모든 `Throwable`을 catch `[완료됨]`

**파일:** `data/station/src/main/kotlin/com/gasstation/data/station/StationRetryPolicy.kt`

**소유:** `data:station`

retry 시도 중 `CancellationException`은 별도로 전파하지만 그 외는 `Throwable`로 잡아 `RetryAttempted(succeeded=false)`를 기록하던 항목입니다. 두 번째 시도 실패 이벤트는 이제 `StationRefreshException`에만 남고, 예기치 않은 exception/error와 cancellation은 retry event로 포장하지 않고 전파됩니다.

**권장 조치:**
- retry 두 번째 시도에서도 `StationRefreshException`만 실패 이벤트로 기록합니다.
- 예상하지 못한 exception/error는 retry event로 포장하지 말고 그대로 전파합니다.
- cancellation 전파 테스트를 유지합니다.

**완료 기준:** `Timeout`/`Network` 재시도 실패는 event를 남기고, DB 쓰기 실패 같은 비정책 예외는 별도 retry event 없이 원래 타입으로 전파됩니다.

**검증:** `./gradlew :data:station:testDebugUnitTest`

---

### 4-2. `historyForWatchlistContext`의 `maxBy`는 현재는 crash가 아니지만 방어 의도가 약함 `[품질]`

**파일:** `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`

**소유:** `data:station`

```kotlin
if (isEmpty()) return emptyList()

val fuelType = cachedFuelType
    ?: maxBy { it.fetchedAtEpochMillis }.fuelType
```

기존 분석에서는 이 항목을 빈 목록 crash로 분류했지만, 현재 코드에는 `isEmpty()` guard가 있어 즉시 재현 가능한 crash는 아닙니다. 다만 null fallback 의미가 한 줄에 숨어 있고, 향후 refactor에서 guard가 분리되면 취약해질 수 있습니다.

**권장 조치:** `maxByOrNull { ... }?.fuelType ?: return emptyList()`로 guard와 fallback을 같은 표현 안에 두거나, `require(isNotEmpty())`로 precondition을 명시합니다.

**완료 기준:** watchlist history가 비어 있거나 cached fuel type이 없는 경우의 fallback 의미가 테스트와 코드에서 명확합니다.

**검증:** `./gradlew :data:station:testDebugUnitTest`

---

## 5. 코드 경로 연결

### 5-1. `SearchRefreshed`, `CompareViewed`, `ExternalMapOpened` 이벤트 연결 `[완료됨]`

**파일:** `domain/station/src/main/kotlin/com/gasstation/domain/station/model/StationEvent.kt`, `feature/station-list`, `feature/watchlist`, `app/navigation`

**소유:** `domain:station` 계약, `feature:*` action/state, `app` handoff

세 이벤트는 domain 계약과 Logcat 매핑에 존재하고, 현재 프로덕션 코드에서 실제 사용자 흐름에 연결되어 있습니다.

| 이벤트 | 기대 시점 | 현재 상태 | 구현 시 주의 |
| --- | --- | --- | --- |
| `SearchRefreshed` | refresh 성공 후 | emit | 저장소 refresh가 스냅샷과 히스토리를 저장한 뒤 기록 |
| `CompareViewed` | watchlist 첫 데이터 표시 | emit | ViewModel 생존 동안 한 번만 기록하고 count는 표시 데이터 기준 |
| `ExternalMapOpened` | 외부 지도 handoff 요청 | emit | 실제 startActivity 성공이 아니라 요청 이벤트로 기록 |

**구현 결정:**
- `SearchRefreshed`는 refresh 성공 데이터가 저장된 뒤 기록합니다.
- `ExternalMapOpened`는 실제 외부 앱 실행 성공이 아니라 handoff 요청 기준으로 기록합니다.
- `CompareViewed`는 watchlist ViewModel 생존 동안 첫 표시 데이터 기준으로 한 번만 기록합니다.
- 이벤트 로깅 중 일반 예외는 사용자 흐름이나 저장소 성공을 실패로 바꾸지 않도록 격리하지만, cancellation과 fatal error는 삼키지 않습니다.

**완료 기준:** domain event variant가 실제 사용자 플로우에서 emit되고, 이벤트 문서가 현재 emit 의미와 안전한 logger 격리를 설명합니다.

**검증:** `./gradlew :domain:station:test :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:testDemoDebugUnitTest`

---

## 6. Deprecated API

### 6-1. `window.statusBarColor` API 35 deprecated `[품질]`

**파일:** `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt`

**소유:** `core:designsystem`, `app`

```kotlin
@Suppress("DEPRECATION")
window.statusBarColor = it.backgroundColor.toArgb()
```

경고는 suppress되어 있지만 API 35 edge-to-edge 흐름과 충돌할 수 있습니다. UI 변경 시 yellow/black/white identity와 공통 chrome 계약을 먼저 확인해야 합니다.

**권장 조치:** app entry에서 `enableEdgeToEdge()`와 `WindowCompat.getInsetsController()` 조합으로 status/navigation bar 정책을 명시하고, `GasStationThemeDefaults.statusBarStyle`과 역할을 분리합니다.

**완료 기준:** API 35 deprecation suppress가 제거되고, demo/prod 화면의 top bar와 splash 전환이 기존 identity를 유지합니다.

**검증:** `./gradlew :core:designsystem:testDebugUnitTest :app:testDemoDebugUnitTest`

---

### 6-2. `Geocoder.getFromLocation` 동기 API API 33 deprecated `[완료됨]`

**파일:** `core/location/src/main/kotlin/com/gasstation/core/location/AndroidAddressResolver.kt`

**소유:** `core:location`, `domain:location`

```kotlin
@Suppress("DEPRECATION")
Geocoder(context, Locale.KOREA).getFromLocation(...)
```

기존 동기 API는 I/O dispatcher에서 실행되어 메인 스레드 블로킹은 피했지만, API 33+에서는 listener 기반 비동기 overload가 권장됩니다. 현재 구현은 API 33+ callback overload를 coroutine으로 감싸고, pre-33은 기존 동기 API를 fallback으로 유지합니다.

**권장 조치:** API 33+에서는 callback overload를 coroutine으로 감싸고, 그 이하에서는 기존 동기 API를 fallback으로 사용합니다.

**완료 기준:** callback error는 `LocationAddressLookupResult.Error`, 빈 성공 결과는 `Unavailable`, cancellation은 전파되고 주소 라벨 정규화 의미는 유지됩니다.

**검증:** `./gradlew :core:location:testDebugUnitTest :feature:station-list:testDebugUnitTest`

---

## 7. 코드 품질

### 7-1. `RTX` 브랜드 라벨 불일치 `[완료됨]`

**파일:** `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListItemUiModel.kt`, `feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListScreen.kt`, `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistItemUiModel.kt`

**소유:** `core:model`, `core:designsystem`, `feature:*`

| 위치 | `RTX` 레이블 |
| --- | --- |
| station card UI model | `자가상표` |
| watchlist UI model | `자가상표` |
| brand filter label | `고속도로알뜰` |

같은 `Brand.RTX`/`BrandFilter.RTX`가 화면 위치에 따라 다르게 읽히던 항목입니다. `core:designsystem/BrandLabels.kt`가 `Brand`와 `BrandFilter` 표시 label을 단일 출처로 소유하고, station list/watchlist/settings가 이를 사용합니다. `RTX`는 `고속도로알뜰`, `ETC`는 `자가상표`로 고정됐습니다.

**권장 조치:**
- `Brand`와 `BrandFilter`의 표시 label을 한 소스에서 관리합니다.
- `core:model`은 vocabulary만 소유하고 UI 문자열은 `core:designsystem` 또는 feature UI mapper가 소유하는 경계를 유지합니다.
- 필터 label이 Opinet 코드 의미와 다르다면 문서/테스트로 의도를 고정합니다.

**완료 기준:** station card, filter, watchlist에서 같은 brand/filter 의미가 일관되게 보이고, label 계약 테스트가 있습니다.

**검증:** `./gradlew :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :core:designsystem:testDebugUnitTest`

---

### 7-2. close icon 중복 Canvas 코드 `[품질]`

**파일:** `feature/settings/src/main/kotlin/com/gasstation/feature/settings/SettingsScreen.kt`, `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistScreen.kt`

**소유:** `core:designsystem`

`LegacyCloseIcon`과 `WatchlistCloseIcon`이 동일한 Canvas 드로잉 코드를 각각 보유합니다. 두 화면 모두 공통 top bar action의 close affordance이므로 `core:designsystem` primitive 후보입니다.

**권장 조치:** `core:designsystem`에 `GasStationCloseIcon` 또는 top bar action primitive를 만들고 두 feature가 재사용합니다.

**완료 기준:** close icon stroke, size, color가 한 곳에서 관리되고 기존 semantics/content description은 유지됩니다.

**검증:** `./gradlew :core:designsystem:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:watchlist:testDebugUnitTest`

---

### 7-3. `legacyYellow`, `legacyBlack` 토큰 명명 잔재 `[품질]`

**파일:** `core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/GasStationThemeDefaults.kt`

**소유:** `core:designsystem`

`legacyYellow`와 `legacyBlack`은 현재 디자인 identity의 핵심 색상인데 이름은 과거 호환 토큰처럼 보입니다. 테스트도 이 이름을 계약으로 고정하고 있어 이후 색상 역할 정리에 혼선을 줄 수 있습니다.

**권장 조치:** `brandYellow`, `brandBlack` 또는 semantic role 이름으로 전환하고, 기존 public API 유지가 필요하면 deprecation path를 둡니다.

**완료 기준:** yellow/black/white 정체성은 유지하되 토큰 이름이 현재 역할을 설명합니다.

**검증:** `./gradlew :core:designsystem:testDebugUnitTest`

---

## 8. 테스트

### 8-1. `StationListViewModelTest`의 Main dispatcher 설정 반복 `[테스트]`

**파일:** `feature/station-list/src/test/kotlin/com/gasstation/feature/stationlist/StationListViewModelTest.kt`

**소유:** `feature:station-list` test infrastructure

여러 테스트가 `Dispatchers.setMain(dispatcher)` / `Dispatchers.resetMain()`을 수동 반복합니다. 기존 분석에서는 `try/finally` 예외 시 reset이 누락된다고 적었지만, 현재 코드의 `finally`는 예외가 나도 실행됩니다. 실제 문제는 누출보다 반복과 일관성입니다.

**권장 조치:** JUnit4 `TestWatcher` 기반 `MainDispatcherRule`을 도입해 dispatcher setup을 중앙화합니다. `StandardTestDispatcher`/`UnconfinedTestDispatcher` 선택은 현재 테스트의 scheduling 기대를 보고 결정합니다.

**완료 기준:** 각 테스트의 dispatcher boilerplate가 줄고, 기존 async/effect 테스트가 flaky해지지 않습니다.

**검증:** `./gradlew :feature:station-list:testDebugUnitTest`

---

### 8-2. watchlist silent-discard 경로 테스트 부재 `[테스트]`

**파일:** `data/station/src/main/kotlin/com/gasstation/data/station/DefaultStationRepository.kt`, `data/station/src/test`

**소유:** `data:station`

`observeWatchlist`에서 cached snapshot과 price history가 모두 없으면 `toWatchedSummary()`가 `null`을 반환하고 `mapNotNull`이 항목을 제외합니다. 이 fallback 의미가 테스트로 분명히 고정되어야 합니다.

**권장 조치:** `WatchlistRepositoryTest`에 "캐시와 히스토리 모두 없는 watchlist 항목은 결과에서 제외된다" 케이스를 추가합니다.

**완료 기준:** 저장 항목이 사라지는 조건이 의도된 silent discard인지, placeholder를 보여야 하는 미완성인지 테스트 이름으로 읽힙니다.

**검증:** `./gradlew :data:station:testDebugUnitTest`

---

### 8-3. `pruneOlderThan` repository 호출 경로 테스트 부재 `[완료됨]`

**파일:** `core/database`, `data/station` tests

**소유:** `data:station`, `core:database`

DAO의 prune 동작만으로는 repository가 실제 refresh 성공 후 정리를 호출하는지 보장할 수 없던 항목입니다. `DefaultStationRepositoryTest`는 refresh 성공 후 cutoff 호출을 검증하고, `StationCacheDaoTest`는 오래된 행/마커 제거와 최신/빈 snapshot 보존을 검증합니다.

**권장 조치:** refresh 성공 후 cutoff보다 오래된 snapshot이 제거되고 최신 snapshot은 유지되는 통합형 repository 테스트를 추가합니다.

**완료 기준:** 빈 결과 snapshot과 최신 snapshot 보존까지 함께 검증합니다.

**검증:** `./gradlew :core:database:testDebugUnitTest :data:station:testDebugUnitTest`

---

## 9. UI / 접근성

### 9-1. 다크 모드 지원이 부분적임 `[접근성]`

**파일:** `core/designsystem`, 각 feature 화면, `app/src/main/res/values-v31/themes.xml`

**소유:** `core:designsystem`, `feature:*`, `app`

`GasStationThemeDefaults`에는 light/dark color scheme가 있지만, feature 화면과 공통 component가 `ColorBlack`, `ColorYellow`, `ColorGray*` 같은 정적 토큰을 많이 직접 참조합니다. 시스템 다크 모드 전환 시 Material color role 기반으로 자연스럽게 바뀌지 않는 부분이 많습니다.

**권장 조치:**
- yellow/black/white brand identity는 유지하되, surface/text/support 색상은 semantic role로 점진 전환합니다.
- station card의 price-first hierarchy가 색상 전환 후에도 유지되는지 screenshot 또는 Compose test로 확인합니다.
- splash theme은 `values-night-v31` override 여부를 별도 확인합니다.

**완료 기준:** 다크 모드에서 가격, 거리, freshness, failure state가 충분한 대비로 읽히고 기존 test tag/semantics는 유지됩니다.

**검증:** `./gradlew :core:designsystem:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :feature:settings:testDebugUnitTest`

---

### 9-2. 사용자 표시 문자열이 Kotlin에 하드코딩됨 `[접근성]`

**파일:** `feature:*`, `app/src/main/res/values/strings.xml`

**소유:** `feature:*`, Android resources

`strings.xml`에는 앱 이름만 있고 화면 문구와 snackbar 메시지는 Kotlin 파일에 직접 들어 있습니다. 당장 i18n이 목표가 아니라면 심각한 결함은 아니지만, ViewModel이 표시 문자열을 직접 소유하면 resource 기반 locale 전환과 테스트 전략이 어려워집니다.

**권장 조치:**
- 단기: ViewModel effect는 resource key 또는 UI message wrapper를 전달하고, Composable/route에서 문자열을 해석합니다.
- 중기: feature별 `strings.xml`로 사용자 표시 문자열을 이동합니다.
- 테스트는 raw string보다 message id/semantic contract를 우선합니다.

**완료 기준:** snackbar/failure guidance 문구가 resource 기반으로 이동해도 기존 사용자 흐름과 테스트 의미가 유지됩니다.

**검증:** 관련 feature별 `testDebugUnitTest`

---

### 9-3. 스플래시 화면 다크 모드 override 부재 `[접근성]`

**파일:** `app/src/main/res/values-v31/themes.xml`

**소유:** `app`, `core:designsystem`

v31 스플래시 테마에서 splash background가 고정 색상입니다. `values-night-v31` override가 없어 시스템 다크 모드에서도 같은 밝은 배경이 나타납니다.

**권장 조치:** `app/src/main/res/values-night-v31/themes.xml`을 추가하고 dark scheme에 맞는 splash background/icon contrast를 정의합니다.

**완료 기준:** light/dark splash 모두 brand identity와 충분한 대비를 유지합니다.

**검증:** `./gradlew :app:testDemoDebugUnitTest`

---

### 9-4. 한글 문자열을 test tag로 사용 `[품질]`

**파일:** `feature/watchlist/src/main/kotlin/com/gasstation/feature/watchlist/WatchlistSemantics.kt`

**소유:** `feature:watchlist`

```kotlin
const val WATCHLIST_CARD_CONTENT_DESCRIPTION = "관심 주유소 카드"
const val WATCHLIST_DISTANCE_METRIC_TAG = "관심 주유소 거리 지표"
```

content description은 사용자/스크린 리더 대상이므로 한글이 적절하지만, test tag는 도구 대상 식별자입니다. 같은 의미라도 content description과 test tag를 분리하는 편이 안정적입니다.

**권장 조치:** `WATCHLIST_CARD_TEST_TAG = "watchlist-card"`처럼 ASCII test tag 상수를 분리하고, content description은 그대로 유지합니다.

**완료 기준:** 접근성 텍스트와 테스트 selector가 분리되고 기존 Compose UI 테스트가 새 tag를 사용합니다.

**검증:** `./gradlew :feature:watchlist:testDebugUnitTest`

---

## 10. 아키텍처

### 10-1. `core:datastore`가 `domain:settings`에 의존 `[품질]`

**파일:** `core/datastore`, `domain/settings`, `data/settings`, `docs/module-contracts.md`

**소유:** `core:datastore`, `domain:settings`, `data:settings`

`docs/module-contracts.md`는 현재 `core:datastore -> domain:settings` 의존을 명시적으로 허용합니다. 일반적인 clean architecture 관점에서는 `core:*`가 `domain:*`에 의존하는 모양이 예외지만, 현재 `domain:settings`가 순수 JVM 모델/use case라 런타임 문제는 없습니다.

**권장 조치:**
- 단기: 현 구조를 유지하되 예외임을 module contract에 계속 명시합니다.
- 장기: DataStore serializer 전용 DTO를 `core:datastore`에 두고, `data:settings`에서 domain mapper를 소유하는 방향을 검토합니다.

**완료 기준:** 의존 방향을 바꿀 경우 설정 저장, migration, demo preference reset, settings feature 테스트가 모두 유지됩니다.

**검증:** `./gradlew :domain:settings:test :core:datastore:testDebugUnitTest :data:settings:testDebugUnitTest :feature:settings:testDebugUnitTest`

---

## 항목별 착수 가이드

| 변경 유형 | 먼저 읽을 문서 | 최소 테스트 |
| --- | --- | --- |
| secret/build setup | `README.md`, `docs/verification-matrix.md`, `app/build.gradle.kts` | `:app:assembleDemoDebug`, `:app:assembleProdDebug`, `:app:testProdDebugUnitTest` |
| cache/watchlist/retry | `docs/offline-strategy.md`, `docs/state-model.md`, `docs/module-contracts.md` | `:domain:station:test`, `:data:station:testDebugUnitTest`, `:feature:station-list:testDebugUnitTest` |
| UI/design/accessibility | `.impeccable.md`, `docs/agent-workflow.md`, `core:designsystem` tests | affected `feature:*:testDebugUnitTest`, `:core:designsystem:testDebugUnitTest` |
| external map/event logging | `docs/state-model.md`, `docs/architecture.md`, `domain/station/model/StationEvent.kt` | `:domain:station:test`, `:feature:station-list:testDebugUnitTest`, `:app:testDemoDebugUnitTest` |
| module/build logic cleanup | `settings.gradle.kts`, `docs/module-contracts.md` | affected module tests plus `:app:assembleDemoDebug` |

## 문서 갱신 필요 후보

아래 항목을 실제로 수정하면 코드와 함께 문서도 점검합니다. 이미 완료된 항목은 해당 문서가 현재 구현 기준으로 갱신됐는지 유지 관리합니다.

- 1-1/1-5: README 실행 모드와 secret 안내
- 1-3/2-4/6-2: `docs/architecture.md`, `docs/test-strategy.md`, `docs/verification-matrix.md`
- 3-1/3-2/3-3/8-2/8-3: `docs/offline-strategy.md`, `docs/state-model.md`, `docs/test-strategy.md`
- 5-1: `docs/state-model.md`, `docs/architecture.md`
- 7-1/9-1/9-3: README screenshot/demo story, 디자인 관련 문서, `docs/module-contracts.md`
- 10-1: `docs/module-contracts.md`, `docs/architecture.md`
