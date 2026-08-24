# GasStation 운영 런북

이 허브는 변경에 맞는 검증을 선택한 뒤 실제 device, build-input, release와 performance 절차의 단일 소유자로 이동하는 경로입니다. 명령을 여러 문서에서 복사하지 말고 아래 owner의 전제, 실행, 증거와 실패 조건을 함께 읽습니다.

| 목적 | 단일 소유자 | 이 문서가 답하는 질문 |
| --- | --- | --- |
| 변경 유형별 검증 선택 | [검증 매트릭스](../verification-matrix.md) | 이 변경에 필요한 최소·집중·확장 검증은 무엇인가? |
| API 24/28/36 runtime evidence | [Android 기기 검증](device-verification.md) | 어떤 host/lane/wrapper/receipt만 device PASS로 인정하는가? |
| wrapper, action, JDK, SDK, dependency와 unsigned reproducibility | [Build Input Provenance](build-input-provenance.md) | 어떤 governed input과 receipt가 build/release 근거가 되는가? |
| release 준비, assemble, tag와 GitHub Release | [배포](../deployment.md) | 어떤 순서와 보안 경계로 Android 산출물을 만든다? |
| hero macrobenchmark와 baseline profile | [성능](../performance.md) | 어떤 physical-device 실행만 committed 성능 근거가 되는가? |

build cache·configuration cache와 CI feedback 결정은 [빌드 속도](../build-velocity.md), 테스트 계층의 의미는 [테스트 전략](../test-strategy.md)이 설명합니다. 현재 문서 전체로 돌아가려면 [문서 허브](../README.md)를 사용합니다.

이 허브는 실행 결과를 주장하지 않습니다. 각 runbook의 historical evidence, `NOT RUN`, `NOT_MEASURED`와 지원 host 한계를 그대로 유지하고, 새 증거는 정규 owner 경로를 실제로 실행한 경우에만 갱신합니다.
