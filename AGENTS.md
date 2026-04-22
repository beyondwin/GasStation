# AGENTS.md

GasStation 작업자는 이 파일을 먼저 읽고, 세부 내용은 연결된 문서로 이동한다. 이 파일은 모든 변경에 적용되는 짧은 운영 헌장이다. 긴 절차, 예외, 화면별 세부 지시는 `docs/agent-workflow.md`와 전문 문서에 둔다.

## Project Identity

GasStation은 포트폴리오/reference 성격의 Android 앱이며, 실제 운전자가 가까운 주유소를 가격, 거리, 브랜드, 유종, watchlist 상태, 외부 지도 연결 기준으로 빠르게 비교할 수 있어야 한다.

이 저장소에서는 다음을 빠르게 확인할 수 있어야 한다.

- 멀티모듈 Android 구조가 일관된 책임 경계를 가진다.
- demo와 prod가 모두 정식 실행 경로로 유지된다.
- 화면 상태, 캐시 fallback, 설정, watchlist, 외부 지도 handoff가 명확한 계약으로 연결된다.
- 디자인 시스템과 presentation layer가 같은 정보 위계를 반복한다.

## Design Direction

기존 yellow, black, white 정체성을 유지하되 현대적인 도시 공공 안내판처럼 빠르게 읽히는 UI를 지향한다.

- Price is the hero. 가격은 station card의 첫 번째 읽기 대상이다.
- 거리, 역명, 브랜드, 유종, watch 상태, freshness, failure state는 가격 결정을 돕는 context다.
- 고대비와 엄격한 리듬을 쓰되 장식 효과로 정보 속도를 늦추지 않는다.
- generic Material sameness, soft generic cards, 불필요한 장식은 피한다.
- UI 변경은 `core:designsystem` 토큰과 공통 chrome을 먼저 확인한 뒤 feature 화면에 적용한다.

## Architecture Rules

활성 모듈의 기준은 `settings.gradle.kts`다. 파일시스템에 남은 오래된 디렉터리보다 Gradle include를 신뢰한다.

- `app`은 Hilt 조립, startup hook, navigation, flavor 연결, 외부 앱 handoff만 담당한다. 비즈니스 정책을 소유하지 않는다.
- `feature:*`는 화면 상태, UI model, Compose 화면, effect를 소유한다. Room, Retrofit, DataStore 구현을 직접 호출하지 않는다.
- `domain:*`는 repository 계약, use case, 도메인 모델을 소유한다. Android, Compose, Room, Retrofit 타입을 노출하지 않는다.
- `data:*`는 repository 구현과 저장/원격/캐시 조합을 담당한다. 화면 상태나 Compose 타입을 만들지 않는다.
- `core:model`은 값 객체와 불변식을 둔다.
- `core:designsystem`은 테마, 토큰, 공통 UI primitive를 둔다.
- `core:database`, `core:network`, `core:datastore`, `core:location`은 공유 인프라 구현을 둔다.
- `tools:demo-seed`와 `benchmark`는 앱 런타임 기능 구현의 우회 경로가 아니다.

경계가 헷갈리면 새 의존성을 추가하기 전에 `docs/module-contracts.md`를 먼저 확인한다.

## Required Reading

처음 작업을 시작할 때는 목적에 맞게 아래 문서를 읽는다.

1. 전체 이해: `README.md` -> `docs/project-reading-guide.md` -> `docs/architecture.md`
2. 모듈 위치 판단: `docs/module-contracts.md`
3. 상태 변경: `docs/state-model.md`
4. 캐시, stale, refresh 실패, watchlist fallback: `docs/offline-strategy.md`
5. 테스트 선택: `docs/test-strategy.md`와 `docs/verification-matrix.md`
6. 실제 작업 절차: `docs/agent-workflow.md`

기능 변경 전에는 관련 feature, domain, data, core 테스트를 먼저 찾아 현재 계약을 확인한다.

## Change Discipline

변경은 기존 구조를 따라 작게 진행한다.

- 새 기능은 화면에서 바로 시작하지 말고 domain contract와 상태 흐름부터 확인한다.
- 설정 쓰기는 명시적 `domain:settings` use case를 통한다.
- 위치 조회는 `feature:station-list -> domain:location -> core:location` 경계를 유지한다.
- 목록 검색은 `StationQuery`, `StationRepository`, 캐시 정책을 기준으로 판단한다.
- 캐시 존재 여부는 `fetchedAt != null`보다 `StationSearchResult.hasCachedSnapshot` 의미를 우선한다.
- demo는 mock 예외 경로가 아니라 검토, 문서, 테스트, benchmark가 기대는 정식 경로다.
- UI 변경은 가격 우선 정보 위계와 접근성/semantics/test tag 계약을 보존한다.
- 문서가 약속하는 사용자 흐름을 바꾸면 테스트와 README/demo story도 함께 점검한다.

## Documentation Ownership

AGENTS.md를 비대하게 만들지 않는다.

- 모든 작업자가 항상 알아야 하는 원칙만 이 파일에 둔다.
- 작업 순서와 체크리스트는 `docs/agent-workflow.md`에 둔다.
- 구조 설명은 `docs/architecture.md`에 둔다.
- 모듈 경계 판단은 `docs/module-contracts.md`에 둔다.
- 상태, 오프라인, 테스트, 검증 명령은 각각의 전문 문서에 둔다.
- 특정 기능 설계와 계획은 `docs/superpowers/specs/`와 `docs/superpowers/plans/`에 둔다.

이 파일에 새 섹션을 추가하기 전에 기존 문서 중 더 적절한 소유자가 있는지 먼저 확인한다.
