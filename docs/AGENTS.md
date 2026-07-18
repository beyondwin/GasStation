# Documentation Agent Contract

이 파일은 `docs/` 아래 변경에 대해 루트 `AGENTS.md`를 보완한다.

## Scope

- Live 계약은 `README.md`, 루트 기여자 문서, `docs/agent-workflow.md`, `docs/project-reading-guide.md`, `docs/architecture.md`, `docs/module-contracts.md`, `docs/state-model.md`, `docs/offline-strategy.md`, `docs/test-strategy.md`, `docs/verification-matrix.md`, `docs/security-trade-offs.md`, `docs/deployment.md`, `docs/performance.md`, 현재 ADR이다.
- `docs/superpowers/`, `docs/history/`, `docs/improvements/`, `docs/compose-metrics/`, 과거 release note는 작업이 명시적으로 대상으로 삼지 않는 한 이력 근거다.

## Authority

- 현재 주장은 실제 코드, `settings.gradle.kts`, Gradle task, `.github/workflows/android.yml`과 대조한다.
- 현재 저장소를 확인하지 않은 승인 plan이나 이력 report를 구현 증거로 승격하지 않는다.

## Verification

- live 문서 변경에는 `scripts/agent/verify.sh docs`를 실행한다.
- 이력 문서만 바꿨다면 새 현재 주장을 추가한 경우가 아닌 한 `git diff --check -- <changed files>`를 실행하고 변경한 근거만 검사한다.
- 문서가 파일, 모듈, 명령, 버전, CI job을 지목하면 해당 표면이 실제로 존재하는지 확인한다.

## Do Not

- 현재 저장소와 맞추기 위해 과거 plan, 측정값, 경로, 완료 결과를 다시 쓰지 않는다.
- 이력 문구만 바꿀 때 관련 없는 Android suite를 실행하지 않는다.
