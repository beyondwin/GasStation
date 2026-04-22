# AGENTS.md

GasStation 작업자는 이 파일을 먼저 읽는다. 이 파일은 모든 변경에 적용되는 짧은 운영 계약이며, 세부 절차와 긴 설명은 연결 문서가 소유한다.

## Operating Contract

- 변경 전 `git status --short`로 기존 사용자 변경을 확인한다.
- 활성 모듈은 파일시스템에 남은 디렉터리가 아니라 `settings.gradle.kts`의 Gradle include 기준으로 판단한다.
- 새 의존성이나 새 위치를 정하기 전에 `docs/module-contracts.md`를 먼저 확인한다.
- 실제 작업 순서, 테스트 선택, 최종 체크리스트는 `docs/agent-workflow.md`를 따른다.

## Product And UI Invariants

GasStation은 포트폴리오/reference 성격의 Android 앱이며, 실제 운전자가 가까운 주유소를 가격, 거리, 브랜드, 유종, watchlist 상태, 외부 지도 연결 기준으로 빠르게 비교할 수 있어야 한다.

- `demo`와 `prod`는 모두 정식 실행 경로다. `demo`는 mock 예외 경로가 아니라 문서, 테스트, benchmark가 기대는 재현 가능한 경로다.
- Price is the hero. 가격은 station card의 첫 번째 읽기 대상이다.
- 거리, 역명, 브랜드, 유종, watch 상태, freshness, failure state는 가격 결정을 돕는 context다.
- UI 변경은 yellow, black, white 정체성과 가격 우선 정보 위계를 보존한다.
- UI 변경 전 `core:designsystem` 토큰과 공통 chrome을 먼저 확인한다.
- generic Material sameness, soft generic cards, 불필요한 장식으로 정보 속도를 늦추지 않는다.

## Architecture Guardrails

- `app`은 Hilt 조립, startup hook, navigation, flavor 연결, 외부 앱 handoff만 담당한다. 비즈니스 정책을 소유하지 않는다.
- `feature:*`는 화면 상태, UI model, Compose 화면, effect를 소유한다. Room, Retrofit, DataStore, `core:location` 구현을 직접 호출하지 않는다.
- `domain:*`는 repository 계약, use case, 도메인 모델을 소유한다. Android, Compose, Room, Retrofit, DataStore 타입을 노출하지 않는다.
- `data:*`는 repository 구현과 저장/원격/캐시 조합을 담당한다. 화면 상태나 Compose 타입을 만들지 않는다.
- `core:model`은 값 객체와 불변식을 둔다.
- `core:designsystem`은 테마, 토큰, 공통 UI primitive를 둔다. feature 전용 문구나 화면 상태 분기를 소유하지 않는다.
- `core:database`, `core:network`, `core:datastore`, `core:location`은 공유 인프라 구현을 둔다.
- `tools:demo-seed`와 `benchmark`는 앱 런타임 기능 구현의 우회 경로가 아니다.

모듈별 상세 책임과 금지 의존은 `docs/module-contracts.md`가 단일 출처다.

## Change Guardrails

- 새 기능은 화면에서 바로 시작하지 말고 domain contract와 상태 흐름부터 확인한다.
- 설정 쓰기는 명시적 `domain:settings` use case를 통한다.
- 위치 조회는 `feature:station-list -> domain:location -> core:location` 경계를 유지한다.
- 목록 검색은 `StationQuery`, `StationRepository`, 캐시 정책을 기준으로 판단한다.
- 캐시 존재 여부는 `fetchedAt != null`보다 `StationSearchResult.hasCachedSnapshot` 의미를 우선한다.
- watchlist는 현재 목록의 복제 화면이 아니라 저장 항목 비교 화면이다.
- UI 변경은 접근성, semantics, test tag 계약을 제거하지 않는다. 제거가 필요하면 대체 테스트를 함께 만든다.
- 문서가 약속하는 사용자 흐름을 바꾸면 테스트와 README/demo story도 함께 점검한다.

## Required Reading By Task

- 처음 이해하거나 길을 잃었을 때: `docs/project-reading-guide.md`
- 실제 작업 순서와 체크리스트: `docs/agent-workflow.md`
- 모듈 위치 판단: `docs/module-contracts.md`
- 구조와 런타임 흐름: `docs/architecture.md`
- 상태 변경: `docs/state-model.md`
- 캐시, stale, refresh 실패, watchlist fallback: `docs/offline-strategy.md`
- 테스트 선택과 검증 명령: `docs/test-strategy.md`, `docs/verification-matrix.md`
- 기능 변경 전: 관련 feature, domain, data, core 테스트

## Documentation Ownership

AGENTS.md를 비대하게 만들지 않는다.

- 모든 작업자가 항상 알아야 하는 원칙만 이 파일에 둔다.
- 작업 순서와 체크리스트는 `docs/agent-workflow.md`에 둔다.
- 구조 설명은 `docs/architecture.md`에 둔다.
- 모듈 경계 판단은 `docs/module-contracts.md`에 둔다.
- 상태, 오프라인, 테스트, 검증 명령은 각각의 전문 문서에 둔다.
- 특정 기능 설계와 계획은 `docs/superpowers/specs/`와 `docs/superpowers/plans/`에 둔다.

이 파일에 새 섹션을 추가하기 전에 기존 문서 중 더 적절한 소유자가 있는지 먼저 확인한다.
