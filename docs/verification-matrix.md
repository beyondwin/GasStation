# 검증 매트릭스

이 문서는 "언제 어떤 명령까지 돌리면 되는가"를 바로 보여주는 실행 체크리스트입니다.

## 빠른 요약

| 상황 | 권장 명령 |
| --- | --- |
| 로컬에서 빨리 빌드만 확인 | `:app:assembleDemoDebug`, `:benchmark:assemble` |
| 머지 전 신뢰 점검 | 단위 테스트 + assemble 전체 매트릭스 |
| 데모 시연 직전 | `:app:connectedDemoDebugAndroidTest` |

- 모든 Gradle 검증은 Java 17 기준입니다.

## 빠른 로컬 확인

```bash
./gradlew :app:assembleDemoDebug :benchmark:assemble
```

## 머지 전 권장 검증

```bash
./gradlew \
  :core:model:test \
  :domain:station:test \
  :domain:settings:test \
  :core:database:testDebugUnitTest \
  :core:datastore:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :core:location:testDebugUnitTest \
  :core:network:test \
  :data:settings:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:assembleDebug \
  :app:assembleDemoDebug \
  :app:assembleProdDebug \
  :benchmark:assemble
```

## 데모 시연 전 점검

```bash
./gradlew :app:connectedDemoDebugAndroidTest
```

CI는 위 명령 중 emulator가 필요 없는 범위를 최소 신뢰 매트릭스로 사용한다.
