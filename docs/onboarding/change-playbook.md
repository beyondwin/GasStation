# 변경 플레이북

첫 버그·기능을 시작할 때 어디를 볼지 좁힌다. 체크리스트는 [작업 절차](../agent-workflow.md), 경계는 [모듈 계약](../module-contracts.md)다.

## 바꾸기 전에

1. `git status --short`
2. `settings.gradle.kts`에서 모듈이 활성인지 확인
3. [읽기 가이드](../project-reading-guide.md)에서 파일 찾기
4. [모듈 계약](../module-contracts.md)에서 금지 의존 확인
5. 구현보다 가까운 테스트를 먼저 읽기

정책은 domain/data, Android 구현은 core, 화면은 feature, 조립은 app이다. 화면에서 Room이나 위치 provider를 끌어오지 않는다.

## 어디부터

| 변경 | 먼저 볼 곳 | 계약 |
| --- | --- | --- |
| Nearby 화면 | `StationListScreen.kt`, designsystem | [상태 모델](../state-model.md), `.impeccable.md` |
| 목록 상태·동시성 | `LocationStateMachine`, orchestrator, coordinator, queue, assembler | [상태 모델](../state-model.md) |
| 검색·캐시·재시도 | `StationRepository`, cache/retry policy, DAO test | [오프라인 전략](../offline-strategy.md) |
| 위치·주소 | `domain:location`, `core:location` | [아키텍처](../architecture.md) |
| 설정 | `UserPreferences`, update use case, DataStore | [모듈 계약](../module-contracts.md) |
| 관심 | watch use case, latest-intent gate, assembler | [오프라인 전략](../offline-strategy.md) |
| 외부 지도 | command payload, `ExternalMapLauncher` | [상태 모델](../state-model.md) |
| demo seed | seed tool, demo startup, seed JSON | [테스트 전략](../test-strategy.md) |
| 빌드/CI 입력 | build logic, workflow, policy test | [Build Input Provenance](../runbooks/build-input-provenance.md) |

## 버그

```text
재현 → 소유 모듈 → 테스트 읽기 → 실패 기준 추가 → 최소 수정 → 검증 → 문서
```

- 화면 버그는 `demo`에서 재현할 수 있는지 본다.
- 가능하면 실패하는 테스트부터 만든다.
- 주변 리팩터링을 같이 하지 않는다.
- 캐시 버그는 성공한 빈 결과와 캐시 없음을 구분한다.
- command 버그는 lifecycle, handler 실패, exact-head 승인을 같이 본다.

네트워크 실패 뒤 목록이 비면 UI만 고치지 않는다. orchestrator, `hasCachedSnapshot`, snapshot DAO를 같이 본다.

## 기능

순서는 `제품 흐름 → domain 계약 → data/core → feature 상태 → app 연결 → demo/prod → 테스트와 문서`다.

설정 하나도 `domain:settings → core:datastore → data:settings → feature:settings → 목록 query` 순으로 본다. 관심은 Nearby 결과만 복제하는 기능이 아니다.

## 테스트 읽기

- 값 객체: domain/core model test
- 캐시·재시도·관심: data/database test
- 화면 상태: feature owner test + integration
- startup/flavor: demo/prod app test
- 기기·릴리스·성능: 각 런북. unit 결과로 대체하지 않는다.

명령은 복사하지 않고 [검증 매트릭스](../verification-matrix.md)에서 고른다.

## 문서

| 바뀐 의미 | 문서 |
| --- | --- |
| 모듈·의존·흐름 | [아키텍처](../architecture.md), [모듈 계약](../module-contracts.md) |
| 상태, command | [상태 모델](../state-model.md) |
| 캐시, stale, 실패 | [오프라인 전략](../offline-strategy.md) |
| UI 위계 | `README.md`, `.impeccable.md` |
| 테스트·CI | [테스트 전략](../test-strategy.md), [검증 매트릭스](../verification-matrix.md) |
| 릴리스·성능·보안 | [배포](../deployment.md), [성능](../performance.md), [보안](../security-trade-offs.md) |

이력 파일만 고쳤다면 본문을 지금 사실처럼 고치지 않는다. live 사실이 바뀌면 [catalog](../documentation-catalog.json)의 owner를 기준으로 현재 문서를 고친다.

처음부터 cache policy나 build logic을 넓게 바꾸지 않는다. 다음은 [검증과 전달](verification-and-delivery.md). 지도는 [문서 허브](../README.md)다.
