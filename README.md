# 주유주유소

현재 위치 주변의 주유소를 빠르게 찾고, stale cache와 외부 지도 handoff까지 포함한 reference Android app입니다.

## 핵심 동작

- 현재 위치 기준 주유소 검색
- 거리순 / 가격순 정렬 토글
- 브랜드, 유종, 반경, 연동 지도 설정 저장
- Room 기반 마지막 성공 결과 유지
- stale 결과 표시와 refresh 실패 시 마지막 성공 결과 유지
- 티맵 / 카카오맵 / 네이버지도 외부 앱 handoff

## Run modes

### Demo

- `demo` flavor는 API key 없이 빌드되고 실행됩니다.
- 고정 위치와 seeded cache를 사용해서 reviewer가 바로 리스트 상태를 확인할 수 있습니다.
- 추천 실행 명령:

```bash
./gradlew :app:assembleDemoDebug
```

### Prod

- `prod` flavor는 로컬 Gradle property `opinet.apikey`, `kakao.apikey`를 읽습니다.
- 실제 실행 전 두 key가 모두 설정되어 있어야 합니다.
- 추천 실행 명령:

```bash
./gradlew :app:assembleProdDebug
```

예시:

```properties
# ~/.gradle/gradle.properties 또는 프로젝트 gradle.properties
opinet.apikey=your-opinet-key
kakao.apikey=your-kakao-key
```

## Architecture docs

- [Architecture](docs/architecture.md)
- [State Model](docs/state-model.md)
- [Offline Strategy](docs/offline-strategy.md)

## Verification

- 모든 Gradle 검증은 Java 17 기준으로 실행합니다.
- `./benchmark/run-demo-benchmark.sh`는 Java 17로 `:app:assembleDemoDebug`와 `:benchmark:assemble`만 빠르게 확인하는 assemble-only helper입니다.
- fresh-session 전체 검증과 CI 동기화 기준은 아래 full matrix입니다.

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home PATH="$JAVA_HOME/bin:$PATH" ./gradlew :core:model:test :domain:station:test :domain:settings:test :core:location:testDebugUnitTest :core:datastore:testDebugUnitTest :data:settings:testDebugUnitTest :data:station:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:station-list:testDebugUnitTest :app:assembleDebug :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble
```

## Acceptance checklist

- [x] demo flavor can run without API keys
- [x] prod flavor documents required local secrets
- [x] architecture diagram reflects the current module graph
- [x] offline/stale behavior is documented
