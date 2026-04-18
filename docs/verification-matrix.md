# 검증 매트릭스

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
