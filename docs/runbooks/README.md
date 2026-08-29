# 운영 런북

명령을 여러 문서에 복사하지 말고, 아래 owner의 전제와 실패 조건을 같이 읽는다.

| 목적 | 문서 | 답하는 질문 |
| --- | --- | --- |
| 어떤 검증을 돌릴지 | [검증 매트릭스](../verification-matrix.md) | 이 변경의 최소·집중 범위는? |
| API 24/28/36 기기 | [기기 검증](device-verification.md) | 어떤 lane/receipt만 PASS인가? |
| wrapper, JDK, SDK, 재현 | [Build Input Provenance](build-input-provenance.md) | 어떤 입력이 빌드 근거인가? |
| 릴리스 | [배포](../deployment.md) | 어떤 순서로 APK를 만드는가? |
| 성능 | [성능](../performance.md) | 어떤 물리 기기 실행만 숫자 근거인가? |

빌드 속도는 [Build Velocity](../build-velocity.md), 테스트 의미는 [테스트 전략](../test-strategy.md)다. 지도는 [문서 허브](../README.md)다.

이 허브는 실행 결과를 주장하지 않는다. `NOT RUN`은 그대로 둔다.
