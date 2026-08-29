# 문서

필요한 문서만 고른다. 지금 동작은 코드와 `settings.gradle.kts`가 기준이다. 문서 목록은 [documentation-catalog.json](documentation-catalog.json)이다.

| 하고 싶은 일 | 문서 |
| --- | --- |
| 앱을 돌려본다 | [README](../README.md), [시작하기](onboarding/getting-started.md) |
| 처음 맡았다 | [온보딩](onboarding/developer-onboarding-guide.md) |
| 어디를 고칠지 찾는다 | [읽기 가이드](project-reading-guide.md) |
| 작업 순서 | [작업 절차](agent-workflow.md), [변경 플레이북](onboarding/change-playbook.md) |
| 모듈을 어디에 둘지 | [모듈 계약](module-contracts.md) |
| 구조와 흐름 | [아키텍처](architecture.md), [둘러보기](onboarding/architecture-tour.md) |
| 화면 상태 | [상태 모델](state-model.md) |
| 캐시·실패 | [오프라인 전략](offline-strategy.md) |
| 어떤 테스트를 돌릴지 | [검증 매트릭스](verification-matrix.md), [테스트 전략](test-strategy.md) |
| 릴리스 | [배포](deployment.md) |
| 에이전트 규칙 | [AGENTS.md](../AGENTS.md) |

## 시작

- [README](../README.md) — 제품, demo/prod, 미리보기
- [기여](../CONTRIBUTING.md) — 설치, 커밋
- [AGENTS.md](../AGENTS.md), [docs/AGENTS.md](AGENTS.md) — 작업 규칙
- [온보딩](onboarding/developer-onboarding-guide.md)
  - [시작하기](onboarding/getting-started.md)
  - [아키텍처 둘러보기](onboarding/architecture-tour.md)
  - [변경 플레이북](onboarding/change-playbook.md)
  - [검증과 전달](onboarding/verification-and-delivery.md)
- [읽기 가이드](project-reading-guide.md)
- [작업 절차](agent-workflow.md)
- [디자인](../.impeccable.md)

## 계약

- [아키텍처](architecture.md)
- [모듈 계약](module-contracts.md)
- [상태 모델](state-model.md)
- [오프라인 전략](offline-strategy.md)
- [보안](security-trade-offs.md)
- [core:database](../core/database/AGENTS.md)
- [benchmark](../benchmark/AGENTS.md)

## 검증·운영

- [런북](runbooks/README.md)
- [검증 매트릭스](verification-matrix.md)
- [테스트 전략](test-strategy.md)
- [기기 검증](runbooks/device-verification.md)
- [빌드 입력](runbooks/build-input-provenance.md)
- [배포](deployment.md)
- [성능](performance.md)
- [빌드 속도](build-velocity.md)
- [CHANGELOG](../CHANGELOG.md)

## 결정·이력

이력은 그때의 기록이다. 지금 코드에 맞춰 다시 쓰지 않는다.

- [ADR](adr/README.md), [Backend proxy](adr/2026-05-18-backend-proxy-escalation.md)
- [설계 이력](superpowers/README.md)
- [분석 이력](history/README.md)
- [개선 이력](improvements/README.md)
- [Compose metrics](compose-metrics/README.md)
- [릴리스 노트](release-notes/README.md)
