# GasStation 변경 플레이북

이 문서는 첫 버그 수정이나 기능 추가를 시작할 때 소유자, 테스트와 문서 영향을 좁히는 실전 경로입니다. 실제 작업 체크리스트는 [에이전트 워크플로](../agent-workflow.md), 모듈 경계는 [모듈 계약](../module-contracts.md)이 소유합니다.

## 변경 전에 소유자 찾기

1. `git status --short`로 기존 변경을 확인합니다.
2. `settings.gradle.kts`에서 대상 모듈이 실제로 활성인지 확인합니다.
3. [프로젝트 읽기 가이드](../project-reading-guide.md)에서 질문에 맞는 코드 진입점을 찾습니다.
4. [모듈 계약](../module-contracts.md)에서 소유 범위와 금지 의존을 확인합니다.
5. 관련 production code보다 먼저 가까운 테스트를 읽어 현재 계약과 edge case를 파악합니다.

정책은 domain/data, Android 구현은 core, 표시와 interaction은 feature, 최종 조립은 app에 둡니다. 화면에서 시작해 Room, Retrofit, DataStore나 위치 provider를 feature로 끌어오지 않습니다.

## 변경 유형별 첫 진입점

| 변경 | 먼저 읽을 소유 표면 | 함께 볼 계약 |
| --- | --- | --- |
| Nearby 표시·interaction | `StationListScreen.kt`, UI model, design-system component | [상태 모델](../state-model.md), `.impeccable.md` |
| station-list 상태·동시성·command | `LocationStateMachine`, `StationSearchOrchestrator`, `RefreshCoordinator`, `StationListCommandQueue`, assembler와 owner tests | [상태 모델](../state-model.md) |
| 검색·cache·retry | `StationRepository`, `DefaultStationRepository`, result/cache/retry policy, database DAO tests | [오프라인 전략](../offline-strategy.md) |
| 위치·주소 라벨 | `domain:location`, `core:location`, station-list location integration tests | [아키텍처](../architecture.md) |
| 설정 저장 | `UserPreferences`, explicit update use cases, DataStore source, settings repository와 feature | [모듈 계약](../module-contracts.md) |
| watchlist | watch use case/result, latest-intent gate, summary assembler, watchlist ViewModel/screen | [오프라인 전략](../offline-strategy.md) |
| 외부 지도 | command payload, app launcher, navigation handoff tests | [상태 모델](../state-model.md) |
| demo seed | seed tool, demo startup hook와 seed asset | [테스트 전략](../test-strategy.md) |
| build/CI/의존 입력 | build logic, workflow와 policy tests | [Build Input Provenance](../runbooks/build-input-provenance.md) |

## 첫 버그 수정

기본 순서는 다음과 같습니다.

```text
재현 -> 소유 모듈 찾기 -> 관련 테스트 읽기 -> 실패 기준 추가 -> 최소 수정 -> 집중 검증 -> 문서 영향 확인
```

- 화면 버그는 먼저 deterministic `demo`에서 재현 가능한지 봅니다.
- 가능한 경우 현재 증상을 실패하는 테스트로 고정합니다. 문서-only 변경이면 현재 문서 gate의 보호 범위를 확인합니다.
- 주변 리팩터링을 함께 하지 않고 실패 원인을 소유하는 표면만 수정합니다.
- cache/failure 버그는 성공한 빈 결과와 cache 없음, stale snapshot 보존을 구분합니다.
- station-list command 버그는 lifecycle gap, handler 실패/취소, exact-head acknowledgement와 at-least-once 외부 side effect 경계를 함께 봅니다.

예를 들어 network 실패 뒤 목록이 비어 보인다면 UI만 고치지 않습니다. orchestrator, `hasCachedSnapshot`, repository 관찰과 snapshot DAO를 함께 읽어 마지막 성공 결과를 보존하는 owner를 찾습니다.

## 첫 기능 추가

기본 순서는 `제품 흐름 -> domain 계약 -> data/core 필요성 -> feature state/action/command -> app wiring -> demo/prod -> 테스트와 문서`입니다.

1. 가격 비교 속도와 기존 사용자 흐름을 해치지 않는지 확인합니다.
2. 새 domain model, use case 또는 repository contract가 필요한지 결정합니다.
3. 저장·network·location·Room schema·DataStore가 바뀌는지 확인합니다.
4. feature의 state, action, command/effect와 표시 policy를 설계합니다.
5. 새 route, Hilt binding 또는 flavor 조립이 필요할 때만 app을 수정합니다.
6. 사용자에게 보이는 동작은 deterministic `demo`에서 재현 가능한지 확인합니다.
7. domain/data/core의 작은 계약 테스트부터 feature integration과 app 조립으로 확장합니다.

설정 하나도 `domain:settings -> core:datastore -> data:settings -> feature:settings -> station-list query 영향` 순서로 봅니다. watchlist는 현재 Nearby 결과에 포함된 항목만 복제하는 기능으로 만들지 않습니다.

## 테스트를 읽는 법

- 값 객체와 순수 규칙은 domain/core model test에서 경계를 찾습니다.
- cache, retry와 watchlist는 data/database test에서 snapshot, transaction과 fallback을 확인합니다.
- 화면 상태는 해당 feature owner test와 integration test를 함께 봅니다.
- app startup, flavor와 navigation은 demo/prod app test를 확인합니다.
- device·release·performance는 로컬 unit 결과로 대체하지 않고 각 specialist runbook의 증거 경계를 따릅니다.

정확한 명령 조합은 복사하지 않고 [검증 매트릭스](../verification-matrix.md)에서 선택합니다.

## 문서 영향 판단

| 변경 의미 | 확인할 현재 소유 문서 |
| --- | --- |
| 모듈·의존 방향·runtime 흐름 | [아키텍처](../architecture.md), [모듈 계약](../module-contracts.md) |
| 상태 source, lifecycle, UI command/effect | [상태 모델](../state-model.md) |
| cache, stale, failure, watchlist fallback | [오프라인 전략](../offline-strategy.md) |
| UI 정보 위계와 공통 primitive | 루트 `README.md`, `.impeccable.md`, architecture |
| 테스트 의미·범위·CI | [테스트 전략](../test-strategy.md), [검증 매트릭스](../verification-matrix.md) |
| release·성능·보안 | [배포](../deployment.md), [성능](../performance.md), [보안 트레이드오프](../security-trade-offs.md) |

일회성 plan이나 report만 갱신하고 현재 계약을 그대로 둔다면 historical body를 현재 사실처럼 다시 쓰지 않습니다. live 사실이 바뀌었다면 [documentation catalog](../documentation-catalog.json)의 owner와 review trigger를 기준으로 현재 문서를 갱신합니다.

## 첫 3일 읽기 루트

- Day 1: root 운영 계약, product README, `settings.gradle.kts`, architecture/module contract, demo build와 navigation.
- Day 2: station-list route/ViewModel에서 네 collaborator와 assembler, data repository/cache/retry, state/offline 문서까지 추적.
- Day 3: 문구·테스트·문서 링크처럼 작은 변경을 고르고 관련 테스트, 최소 수정, 집중 검증과 문서 영향을 끝까지 기록.

처음부터 cache policy, location provider, build logic이나 benchmark를 넓게 바꾸지 않습니다. 다음은 [검증과 전달](verification-and-delivery.md)에서 변경에 맞는 증거와 handoff를 준비합니다. 전체 문서로 돌아가려면 [문서 허브](../README.md)를 사용합니다.
