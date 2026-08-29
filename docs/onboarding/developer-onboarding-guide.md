# 개발자 온보딩

처음 맡았다면 여기서 고른다. 지금 동작은 코드와 `settings.gradle.kts`가 맞다.

## 준비

- Java 21, Android SDK 37. bytecode target은 JVM 17이다.
- agent script는 Python 3.9 이상.
- [`AGENTS.md`](../../AGENTS.md)와 [`docs/AGENTS.md`](../AGENTS.md)를 먼저 읽는다.
- 활성 모듈은 `settings.gradle.kts`의 `include(...)`다.
- 첫 실행은 키가 필요 없는 `demo`다.

설치가 필요하면 [시작하기](getting-started.md)부터 한다.

## 경로

| 목적 | 문서 | 끝나면 |
| --- | --- | --- |
| checkout, 도구, 첫 빌드 | [시작하기](getting-started.md) | `demo`를 빌드하고 `prod` 키 경계를 말할 수 있다 |
| 제품과 레이어 | [아키텍처 둘러보기](architecture-tour.md) | 가격 우선을 지키며 어디를 고칠지 좁힌다 |
| 첫 버그·기능 | [변경 플레이북](change-playbook.md) | 소유자와 테스트를 찾고 최소 수정을 한다 |
| 검증과 전달 | [검증과 전달](verification-and-delivery.md) | 돌린 것과 안 돌린 것을 나눠 남긴다 |

처음부터면 `시작하기 → 둘러보기 → 플레이북 → 검증과 전달`이다. 파일이 필요하면 [읽기 가이드](../project-reading-guide.md), 작업 순서는 [작업 절차](../agent-workflow.md)다.

## 계약

- 모듈 그래프와 흐름: [아키텍처](../architecture.md)
- 어디에 둘지: [모듈 계약](../module-contracts.md)
- 목록 상태: [상태 모델](../state-model.md)
- 캐시·실패: [오프라인 전략](../offline-strategy.md)
- 테스트 의미: [테스트 전략](../test-strategy.md)
- 명령 선택: [검증 매트릭스](../verification-matrix.md)
- 보안: [보안](../security-trade-offs.md)
- 전체 지도: [문서 허브](../README.md)

문서와 코드가 다르면 코드를 먼저 본다. `docs/superpowers/` 파일명으로 지금 완료를 판단하지 않는다.

<!-- station-data-policy-ref: retry -->[오프라인 전략의 구조화된 `retry` 계약](../offline-strategy.md#기계-판독-정책-계약)

<!-- station-data-policy-ref: freshness -->[오프라인 전략의 구조화된 `freshness` 계약](../offline-strategy.md#기계-판독-정책-계약)

<!-- station-list-state-contract-ref -->[상태 모델의 구조화된 station-list 계약](../state-model.md#station-list-결정적-상태-계약)
