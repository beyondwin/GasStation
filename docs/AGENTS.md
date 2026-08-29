# Documentation Agent Contract

`docs/`를 바꿀 때 루트 `AGENTS.md`를 보완한다.

## 범위

- live 문서 목록은 [`documentation-catalog.json`](documentation-catalog.json)이다.
- 온보딩 입구는 `docs/onboarding/developer-onboarding-guide.md`다. 지금 동작의 설명은 연결된 live 문서가 맞는다.
- `docs/superpowers/`, `docs/history/`, `docs/improvements/`, `docs/compose-metrics/`, 과거 release note는 이력이거나 당시 증거다. 파일명만으로 지금 완료를 추론하지 않는다.

## 기준

- 지금 주장은 코드, `settings.gradle.kts`, Gradle task, `.github/workflows/android.yml`과 맞춘다.
- 과거 plan이나 분석 리포트를 구현 증거로 쓰지 않는다.

## 검증

- live 문서를 바꾸면 `scripts/agent/verify.sh docs`를 실행한다.
- 이력만 바꿨으면 `git diff --check -- <changed files>`로 그 파일만 본다.
- 문서가 파일, 모듈, 명령, 버전, CI job을 지목하면 그게 실제로 있는지 확인한다.

## 하지 말 것

- 지금 코드에 맞추려고 과거 plan, 측정값, 경로, 완료 결과를 다시 쓰지 않는다.
- 이력 문구만 고쳤을 때 관련 없는 Android suite를 돌리지 않는다.
