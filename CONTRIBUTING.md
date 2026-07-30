# Contributing to GasStation

GasStation은 한국 운전자가 현재 위치 기반으로 가까운 주유소를 비교하는 Android 앱입니다. 외부 기여를 환영합니다.

## 시작하기

1. Java 21 이상, Android SDK 37. 앱의 Java/Kotlin bytecode target은 JVM 17입니다.
2. 저장소의 agent script와 Codex/Claude hook은 Python 3.9 이상 표준 라이브러리만 사용합니다.
3. `~/.gradle/gradle.properties`에 `opinet.apikey`를 둘 수 있습니다. `demo` 빌드는 키 없이 동작합니다.
4. 처음에는 `demo`로 검증하세요.

```bash
./gradlew :app:assembleDemoDebug
./gradlew :app:testDemoDebugUnitTest
```

## 운영 계약

- 모든 작업자는 `AGENTS.md`를 먼저 읽습니다.
- 작업 순서와 체크리스트는 `docs/agent-workflow.md`.
- 모듈 경계 판단은 `docs/module-contracts.md`.

## 머지 전 검증

`docs/verification-matrix.md`의 머지 전 회귀 세트가 단일 출처입니다. 요약:

```bash
./gradlew \
  spotlessCheck lint \
  :core:model:test \
  :domain:location:test \
  :core:observability:test \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  verifyRoborazziDebug \
  coverageXmlReport \
  :app:assembleProdRelease
```

명령 변경/확장 시 `docs/verification-matrix.md`를 먼저 갱신한 뒤 위 블록을 같이 동기화합니다.

## 릴리스와 배포

새 버전 발행은 [`docs/deployment.md`](docs/deployment.md)를 따릅니다. 릴리스 PR은 `app/build.gradle.kts`의 `versionCode`/`versionName`, `CHANGELOG.md`, `README.md`, `docs/release-notes/`를 함께 갱신합니다. merge 후 `main`의 같은 SHA에 `vX.Y.Z` 태그를 push하면 GitHub Actions가 전체 release 검증을 다시 실행하고, 모두 성공한 경우에만 demo APK, unsigned prod APK, SHA-256 checksum을 GitHub Release에 게시합니다.

## 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/)을 따릅니다.

- `feat:` 사용자 기능
- `fix:` 버그 수정
- `refactor:` 동작 변경 없는 구조 정리
- `chore:` 빌드/도구/메타데이터
- `docs:` 문서 전용
- `test:` 테스트 전용

## 코드 스타일

`./gradlew spotlessApply`를 커밋 전에 실행합니다. Spotless convention plugin이 ktlint를 내부적으로 호출하므로 별도 `ktlintFormat` 태스크를 실행할 필요는 없습니다.

## 행동 강령

존중과 건설적 토론을 원칙으로 합니다. 사용자 데이터/위치 처리에 영향을 주는 PR은 보안 영향을 명시합니다.
