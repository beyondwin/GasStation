# Contributing to GasStation

GasStation은 한국 운전자가 현재 위치 기반으로 가까운 주유소를 비교하는 Android 앱입니다. 외부 기여를 환영합니다.

## 시작하기

1. JDK 17, Android SDK 35.
2. `~/.gradle/gradle.properties`에 `opinet.apikey`를 둘 수 있습니다. `demo` 빌드는 키 없이 동작합니다.
3. 처음에는 `demo`로 검증하세요.

```bash
./gradlew :app:assembleDemoDebug
./gradlew :app:testDemoDebugUnitTest
```

## 운영 계약

- 모든 작업자는 `AGENTS.md`를 먼저 읽습니다.
- 작업 순서와 체크리스트는 `docs/agent-workflow.md`.
- 모듈 경계 판단은 `docs/module-contracts.md`.

## 머지 전 검증

`docs/verification-matrix.md`의 머지 전 회귀 세트를 통과해야 합니다.

```bash
./gradlew spotlessCheck ktlintCheck lint \
  :app:testDemoDebugUnitTest :app:testProdDebugUnitTest \
  :app:assembleDemoDebug :app:assembleProdDebug :app:assembleProdRelease \
  verifyRoborazziDebug koverXmlReport
```

## 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/)을 따릅니다.

- `feat:` 사용자 기능
- `fix:` 버그 수정
- `refactor:` 동작 변경 없는 구조 정리
- `chore:` 빌드/도구/메타데이터
- `docs:` 문서 전용
- `test:` 테스트 전용

## 코드 스타일

`./gradlew spotlessApply ktlintFormat`을 커밋 전에 실행합니다.

## 행동 강령

존중과 건설적 토론을 원칙으로 합니다. 사용자 데이터/위치 처리에 영향을 주는 PR은 보안 영향을 명시합니다.
