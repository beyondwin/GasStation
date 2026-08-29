# Contributing to GasStation

외부 기여를 환영한다.

## 시작

1. Java 21 이상, Android SDK 37. 앱 bytecode target은 JVM 17이다.
2. agent script와 Codex/Claude/Grok hook은 Python 3.9 이상 표준 라이브러리만 쓴다. PreToolUse는 `tool_input`과 `toolInput`을 같은 정책으로 본다.
3. `prod`를 실행할 때만 `~/.gradle/gradle.properties`에 `opinet.apikey`를 둔다. `demo`는 키가 필요 없다.
4. 처음에는 `demo`로 확인한다.

```bash
./gradlew :app:assembleDemoDebug
./gradlew :app:testDemoDebugUnitTest
```

## 규칙

- 작업자는 `AGENTS.md`를 먼저 읽는다.
- 작업 순서는 `docs/agent-workflow.md`.
- 모듈 경계는 `docs/module-contracts.md`.

## 머지 전

명령은 [검증 매트릭스의 머지 전 회귀](docs/verification-matrix.md#머지-전-권장-회귀-세트)가 맞는다. 문서만 바꿨으면 같은 문서의 문서 변경 절을 본다.

Coverage는 report와 ratchet을 함께 본다. 결과는 `build/reports/coverage/verification-summary.json`이다.

JVM mutation 설정은 `./gradlew verifyPitestConfiguration --warning-mode fail`로 확인한다. 실제 PIT는 plugin `pitest` task가 아니라 `scripts/quality/run_pitest.sh`만 쓴다.

명령을 바꾸면 `docs/verification-matrix.md`를 먼저 고친다.

## 릴리스

발행은 [`docs/deployment.md`](docs/deployment.md)를 따른다. 릴리스 PR은 `versionCode`/`versionName`, `CHANGELOG.md`, `README.md`, `docs/release-notes/`를 같이 고친다. merge 후 `main`의 같은 SHA에 `vX.Y.Z` 태그를 올리면 CI가 다시 검증하고, 모두 성공하면 demo APK, unsigned prod APK, checksum을 GitHub Release에 올린다.

## 커밋

[Conventional Commits](https://www.conventionalcommits.org/)을 쓴다.

- `feat:` 사용자 기능
- `fix:` 버그
- `refactor:` 동작 없는 구조 정리
- `chore:` 빌드/도구
- `docs:` 문서
- `test:` 테스트

## 스타일

커밋 전에 `./gradlew spotlessApply`를 실행한다. Spotless가 ktlint를 호출하므로 별도 `ktlintFormat`은 필요 없다.

## 행동

존중과 건설적 토론을 원칙으로 한다. 사용자 데이터나 위치 처리에 영향을 주는 PR은 보안 영향을 적는다.

## 빌드 입력

Wrapper, GitHub Action, JDK, Android SDK, Maven/plugin을 바꿀 때는 [Build Input Provenance](docs/runbooks/build-input-provenance.md)와 [검증 매트릭스](docs/verification-matrix.md)의 strict/cold-home 명령을 따른다. 그냥 `./gradlew` 결과는 검토된 receipt가 아니다.
