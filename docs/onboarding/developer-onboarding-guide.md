# GasStation 개발자 온보딩 가이드

이 경로는 GasStation을 처음 맡는 Android 개발자를 위한 안정적인 온보딩 진입점입니다. 제품과 구조를 먼저 이해하려는 사람, 앱을 실행하려는 사람, 첫 변경을 준비하는 사람, 검증과 handoff를 준비하는 사람을 각각의 짧은 경로로 안내합니다. 현재 사실은 실제 코드, `settings.gradle.kts`, Gradle task와 CI workflow를 우선합니다.

## 시작 전 준비

- Java 21 이상과 Android SDK 37을 준비합니다. 앱의 Java/Kotlin bytecode target은 JVM 17입니다.
- 저장소 agent script에는 Python 3.9 이상이 필요합니다.
- 루트의 [`AGENTS.md`](../../AGENTS.md)와 [`docs/AGENTS.md`](../AGENTS.md)를 먼저 읽습니다.
- 활성 모듈은 디렉터리 존재 여부가 아니라 `settings.gradle.kts`의 `include(...)`로 판단합니다.
- 첫 실행은 API key가 필요 없는 `demo`를 권장합니다. `prod`는 실제 Opinet key와 위치·네트워크를 사용합니다.

설치와 첫 build가 필요하면 [시작하기](getting-started.md)부터 진행합니다. 이미 로컬 실행 환경이 준비되어 있다면 아래 목적에 맞는 경로로 바로 이동해도 됩니다.

## 목적별 읽기 경로

| 목적 | 경로 | 마치면 할 수 있는 일 |
| --- | --- | --- |
| 저장소 checkout, 도구 확인, `demo`/`prod` 첫 실행 | [시작하기](getting-started.md) | 재현 가능한 `demo` build를 만들고 `prod` 실행 전제와 key 경계를 설명한다. |
| 제품 원칙, 레이어, 주요 runtime 흐름 이해 | [아키텍처 둘러보기](architecture-tour.md) | 가격 우선 제품 기준을 유지하며 변경의 책임 레이어를 좁힌다. |
| 첫 버그 수정 또는 기능 추가 | [변경 플레이북](change-playbook.md) | 소유자와 관련 테스트를 찾고 최소 변경 및 문서 영향을 결정한다. |
| 검증 선택, 증거, commit과 handoff | [검증과 전달](verification-and-delivery.md) | 실행한 범위와 미검증 범위를 분리해 재현 가능한 handoff를 남긴다. |

처음부터 순서대로 읽는다면 `시작하기 -> 아키텍처 둘러보기 -> 변경 플레이북 -> 검증과 전달`을 따릅니다. 세부 질문이 생기면 [프로젝트 읽기 가이드](../project-reading-guide.md)에서 질문별 코드 진입점을 찾고, 실제 작업은 [에이전트 워크플로](../agent-workflow.md)를 따릅니다.

## 현재 계약으로 바로 가기

- 활성 모듈의 정확한 그래프와 runtime 흐름: [아키텍처](../architecture.md)
- 모듈 책임과 금지 의존: [모듈 계약](../module-contracts.md)
- station-list 상태와 command lifecycle: [상태 모델](../state-model.md)
- cache, stale, refresh 실패와 watchlist fallback: [오프라인 전략](../offline-strategy.md)
- 테스트 의미: [테스트 전략](../test-strategy.md)
- 변경 유형별 검증 선택: [검증 매트릭스](../verification-matrix.md)
- 보안 한계와 proxy 승격 조건: [보안 트레이드오프](../security-trade-offs.md)
- 전체 문서 지도: [문서 허브](../README.md)

문서 설명과 코드가 다르면 실제 코드와 `settings.gradle.kts`를 먼저 확인하고, 관련 live 계약 문서와 catalog owner를 함께 갱신합니다. 오래된 `docs/superpowers/`, `docs/history/`, `docs/improvements/` 파일명만으로 현재 승인이나 완료를 추론하지 않습니다.

<!-- station-data-policy-ref: retry -->[오프라인 전략의 구조화된 `retry` 계약](../offline-strategy.md#기계-판독-정책-계약)

<!-- station-data-policy-ref: freshness -->[오프라인 전략의 구조화된 `freshness` 계약](../offline-strategy.md#기계-판독-정책-계약)

<!-- station-list-state-contract-ref -->[상태 모델의 구조화된 station-list 계약](../state-model.md#station-list-결정적-상태-계약)
