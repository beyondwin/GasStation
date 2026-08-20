# GasStation 문서 허브

이 문서는 현재 GasStation을 이해하고 변경할 때 시작하는 사람용 허브입니다. 현재 사실과 계약은 코드, `settings.gradle.kts`, Gradle task, CI workflow를 우선으로 확인합니다. 문서의 소유 범위와 검토 시점은 [documentation-catalog.json](documentation-catalog.json)에 기록합니다.

## 1. 새 기여자와 로컬 실행

- 제품 목적, `demo`/`prod` 실행 경로, 빠른 시작: [루트 README](../README.md)
- 설치 조건, 기여와 커밋 기준: [CONTRIBUTING.md](../CONTRIBUTING.md)
- 작업 시작 전 운영 계약: [AGENTS.md](../AGENTS.md), [docs/AGENTS.md](AGENTS.md)
- 목적별 코드 읽기와 첫 변경의 진입점: [프로젝트 읽기 가이드](project-reading-guide.md)
- 현재 작업 순서와 체크리스트: [에이전트 워크플로](agent-workflow.md)
- 기존 온보딩 핸드북의 안정적인 진입점: [개발자 온보딩 가이드](onboarding/developer-onboarding-guide.md)
- UI 정체성과 가격 우선 원칙: [.impeccable.md](../.impeccable.md)

## 2. 아키텍처와 기능 변경

- 활성 18개 모듈의 정확한 그래프와 런타임 흐름: [아키텍처](architecture.md)
- 모듈별 소유 범위와 금지 의존: [모듈 계약](module-contracts.md)
- 상태 전이, 사용자 액션, 화면 effect: [상태 모델](state-model.md)
- 캐시, stale, refresh 실패, watchlist fallback: [오프라인 전략](offline-strategy.md)
- 보안 선택과 backend proxy 승격 조건: [보안 트레이드오프](security-trade-offs.md)

## 3. 테스트·릴리스·운영 검증

- 변경 유형에서 필요한 검증 범위를 고르는 기준: [테스트 전략](test-strategy.md), [검증 매트릭스](verification-matrix.md)
- API 24/28/36 에뮬레이터 lane, receipt, failure triage와 승격 기준: [Android 기기 검증 런북](runbooks/device-verification.md)
- 배포, tag, GitHub Release, APK 산출물: [배포](deployment.md)
- macrobenchmark, 물리 기기 측정, 성능 근거: [성능](performance.md)
- build cache·configuration cache와 CI 속도 결정: [빌드 속도](build-velocity.md)
- 현재 릴리스 변경 요약: [CHANGELOG.md](../CHANGELOG.md)

### 모듈별 운영 계약

- Room schema, snapshot, migration, cache 데이터 변경: [core:database AGENTS](../core/database/AGENTS.md)
- macrobenchmark 증거, physical device, selector 계약: [benchmark AGENTS](../benchmark/AGENTS.md)

## 4. 결정·근거·이력 조사

- 현재 수용된 아키텍처 결정: [ADR — Backend Proxy Escalation](adr/2026-05-18-backend-proxy-escalation.md)
- 설계와 구현 계획의 작성 당시 기록: [`docs/superpowers/`](superpowers/)
- 심층 분석 및 개선 이력: [`docs/history/`](history/), [`docs/improvements/`](improvements/)
- Compose stability 측정 스냅샷: [`docs/compose-metrics/`](compose-metrics/)
- 버전별 릴리스 근거: [`docs/release-notes/`](release-notes/)

이력 문서는 결정 배경과 측정 근거를 찾는 데 사용합니다. 현재 동작이나 완료 상태는 이력 파일명만으로 판단하지 말고, 위의 현재 계약 문서와 실제 저장소를 다시 확인합니다.
