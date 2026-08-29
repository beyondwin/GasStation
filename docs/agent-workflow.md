# 작업 절차

기능을 넣거나 고칠 때의 순서다. 짧은 규칙은 `AGENTS.md`, 구조는 `docs/architecture.md`, 위치는 `docs/module-contracts.md`다.

## 기본

화면 파일 하나부터 고치지 않는다. 계약 → 구현 → 상태 → 화면 → 검증 순이 안전하다.

1. 목적을 한 문장으로 정한다.
2. 관련 문서와 테스트를 찾는다.
3. 정책을 누가 맡는지 정한다.
4. domain 계약이 바뀌는지 본다.
5. data/core 구현이 필요한지 본다.
6. feature의 state, action, command, screen을 맞춘다.
7. demo/prod와 테스트 범위를 본다.
8. 문서가 약속한 동작이 바뀌면 문서를 고친다.

## 시작 전

- `git status --short`
- 활성 모듈은 `settings.gradle.kts`
- 지금 동작은 live 문서와 코드
- `docs/superpowers/` 등은 이력이다
- 구현보다 테스트를 먼저 읽는다
- 새 dependency 전에 같은 계층의 기존 패턴을 찾는다
- UI면 `core:designsystem`을 먼저 본다
- 상태/캐시면 `docs/state-model.md`, `docs/offline-strategy.md`

비사소한 변경은 `scripts/agent/preflight.sh`로 branch, worktree, dirty path, Java, SDK를 본다. 이어 할 작업이면 `git worktree list`와 그 공간의 status를 보고 같은 작업을 이어간다. 기존 변경을 stash/reset하지 않고, 같은 목적의 worktree를 중복 만들지 않는다.

## 어디에 두나

1. `settings.gradle.kts`에서 활성인지 확인
2. `docs/module-contracts.md`에서 금지 항목 확인
3. 정책은 domain/data, 화면은 feature, 조립은 app
4. `core:*`가 앱 정책을 먹기 시작하면 domain/data가 맞는지 다시 본다

## 새 기능

1. 기존 route로 충분한지, 새 route가 필요한지
2. 새 개념이면 `domain:*`부터
3. 저장·원격·캐시면 `data:*` 또는 `core:*`
4. feature에 action, state, command
5. navigation은 마지막에 `app`
6. 사용자에게 보이면 demo에서 재현되는지
7. 테스트는 domain/data부터, feature로 흐름을 막는다

값이 domain/data 원천인지 feature 파생인지 먼저 가른다.

## 기존 동작

- 정렬/필터: `domain:station` 모델, `DefaultStationRepository`
- 위치: `domain:location` → `core:location`
- 주소: `AddressLabelNormalizer` → `core:location` → station-list 표시
- 설정: `domain:settings` use case
- cache/stale: `StationCachePolicy`, `core:database`
- 재시도: `StationRetryPolicy`
- 관심 비교: `DefaultStationRepository`, `feature:watchlist`
- 이벤트: `StationEvent`, `CrashReporter`
- 외부 지도: `ExternalMapLauncher`

## UI

기준 화면은 station list다.

1. `core:designsystem` 토큰과 component
2. feature에 중복 metric/row가 있으면 공유 primitive 후보인지 본다
3. 가격이 첫 시선, 거리가 두 번째
4. 브랜드 아이콘만 쓰고 visible 브랜드 텍스트를 넣지 않는다
5. permission, GPS, loading, empty, failure가 같은 guidance로 읽히게 한다
6. semantics와 test tag를 지우면 대체 테스트를 같이 만든다
7. `testTag`는 ASCII, 스크린 리더 문구는 `contentDescription`
8. `주변·관심·설정` icon-only nav, SettingsDetail에서만 숨긴다

색은 `#FFFCF2`, `#222222`, `#FFDC00`이다.

## 설정

`UserPreferences` → update use case → datastore DTO → settings repository → feature. 목록 query에 영향을 주면 station-list도 본다. feature가 `SettingsRepository`를 직접 부르지 않는다.

## 위치

`feature:station-list -> domain:location -> core:location`

feature는 Android provider를 모른다. 권한 상태는 route에서 domain 타입으로 바꾼다. 현재 위치는 refresh 때 `GetCurrentLocationUseCase`, availability는 foreground에서 `ObserveLocationAvailabilityUseCase`다. 주소는 검색 입력이 아니라 표시다.

## 검색과 캐시

- 캐시 키는 위치 버킷, 반경, 유종
- 브랜드·정렬은 읽기 모델
- 좌표가 있는 상태에서 조건이 바뀌면 refresh를 다시 요청한다
- 실패해도 기존 스냅샷은 유지한다
- 성공한 빈 결과와 캐시 없음은 다르다
- 전면 실패는 `hasCachedSnapshot`으로 본다
- 재시도는 `StationRetryPolicy`, 결과는 `StationEvent.RetryAttempted`

<!-- station-data-policy-ref: retry -->[오프라인 전략의 구조화된 `retry` 계약](offline-strategy.md#기계-판독-정책-계약)

동시성 변경은 [검증 매트릭스의 집중 회귀](verification-matrix.md#station-list-상태-동시성-집중-회귀)를 따른다.

<!-- station-list-state-contract-ref -->[상태 모델의 구조화된 station-list 계약](state-model.md#station-list-결정적-상태-계약)

## 관심

현재 목록의 복제가 아니다. 기준 좌표는 navigation payload와 `SavedStateHandle`이다. 최신 캐시가 없어도 저장 항목을 유지한다. 기본 row는 108–116dp, 5행이 보이고 200% 글꼴이면 스크롤된다. selector는 `bottom-nav-watchlist`, `station-list-watch-toggle`, `watchlist-card`다.

## demo / prod

둘 다 정식이다. `demo`는 seed와 고정 좌표, `prod`는 실제 키·위치·네트워크. 사용자에게 보이면 demo에서 재현되는지 본다. 자동화는 실서버에 기대지 않는다.

## 테스트 선택

값 객체는 `domain:*:test` / `core:model:test`. 캐시는 `data:station:testDebugUnitTest`. UI는 해당 feature test. 조립은 `app:testDemoDebugUnitTest` / `app:testProdDebugUnitTest`. 명령 조합은 `docs/verification-matrix.md`다.

## 문서

설명이 지금 코드와 사용자 흐름을 바꾸면 live 문서도 고친다. 일회성 설계는 `docs/superpowers/`에 남긴다.

| 변경 | 문서 |
| --- | --- |
| 모듈·의존 | `module-contracts.md`, `architecture.md` |
| 흐름 | `architecture.md` |
| 상태 | `state-model.md` |
| 캐시 | `offline-strategy.md` |
| UI | `README.md`, `.impeccable.md` |
| 테스트 의미 | `test-strategy.md` |
| 명령·CI | `verification-matrix.md` |
| 릴리스 | `deployment.md`, `CHANGELOG.md` |
| 성능 | `performance.md` |
| 보안 | `security-trade-offs.md` |

AGENTS.md에는 항상 필요한 규칙만 넣는다.

## 끝내기 전

- 활성 모듈 기준과 맞는가
- feature가 infra를 직접 알게 되지 않았는가
- domain이 Android/UI/storage DTO를 노출하지 않는가
- data가 화면 문구를 소유하지 않는가
- demo와 prod 중 하나만 우연히 동작하지 않는가
- 문서의 사용자 흐름이 테스트로 막히는가
