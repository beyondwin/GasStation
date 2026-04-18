# 주유주유소

현재 위치 주변의 주유소를 빠르게 찾고, 오래된 캐시 상태와 외부 지도 앱 연동까지 포함한 안드로이드 레퍼런스 앱입니다.

## 핵심 기능

- 현재 위치 기준 주유소 검색
- 가격 변동 배지와 관심 주유소 저장
- 관심 주유소 비교 화면
- 거리순 / 가격순 정렬 전환
- 브랜드, 유종, 검색 반경, 연동 지도 설정 저장
- Room 기반 마지막 성공 결과 유지
- 오래된 결과 표시와 새로고침 실패 시 마지막 성공 결과 유지
- 티맵 / 카카오맵 / 네이버지도 외부 앱 연동

## 실행 모드

### 데모

- `demo` flavor는 API 키 없이 빌드하고 실행할 수 있습니다.
- 강남역 2번 출구 고정 위치와 `app/src/demo/assets/demo-station-seed.json`에 저장된 실제 API 기반 시드를 사용해 검토자가 바로 목록 상태를 확인할 수 있습니다.
- 앱 실행 시에는 생성된 JSON 자산을 Room seed로 적재하므로 외부 네트워크에 의존하지 않습니다.
- 시드를 다시 생성하려면 로컬 Gradle 속성 `opinet.apikey`를 설정한 뒤 `./gradlew :tools:demo-seed:generateDemoSeed`를 실행합니다. `kakao.apikey`가 있어도 무방하지만, 시드 생성과 앱 런타임은 더 이상 Kakao 좌표 변환 API에 의존하지 않습니다.
- 권장 실행 명령:

```bash
./gradlew :app:assembleDemoDebug
```

### 프로덕션

- `prod` flavor는 로컬 Gradle 속성 `opinet.apikey`를 읽습니다.
- 실제 실행 전 `opinet.apikey`가 설정되어 있어야 합니다.
- 권장 실행 명령:

```bash
./gradlew :app:assembleProdDebug
```

예시:

```properties
# ~/.gradle/gradle.properties 또는 프로젝트 gradle.properties
opinet.apikey=your-opinet-key
```

## 아키텍처 문서

- [프로젝트 읽기 가이드](docs/project-reading-guide.md)
- [아키텍처](docs/architecture.md)
- [상태 모델](docs/state-model.md)
- [오프라인 전략](docs/offline-strategy.md)

## 검증

- 모든 Gradle 검증은 Java 17 기준으로 실행합니다.
- `./benchmark/run-demo-benchmark.sh`는 Java 17로 `:app:assembleDemoDebug`와 `:benchmark:assemble`만 빠르게 확인하는 assemble 전용 도우미 스크립트입니다.
- 새 세션 기준 전체 검증과 CI 동기화 기준은 아래 전체 매트릭스입니다.

단위 테스트는 가격 변화 계산, watchlist 상태 전이, 캐시 정책과 Room migration을 검증합니다. UI 테스트는 데모 플로우에서 관심 등록 후 비교 화면 진입과 watchlist 카드 노출을 확인하고, macrobenchmark는 cold start와 비교 화면 진입을 대상으로 합니다.

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home PATH="$JAVA_HOME/bin:$PATH" ./gradlew :core:database:testDebugUnitTest :domain:station:test :data:station:testDebugUnitTest :feature:station-list:testDebugUnitTest :feature:watchlist:testDebugUnitTest :app:assembleDemoDebug :app:connectedDemoDebugAndroidTest :benchmark:assemble
```

## 완료 기준 점검표

- [x] `demo` flavor는 API 키 없이 실행할 수 있다
- [x] `prod` flavor 문서에 필요한 로컬 시크릿이 정리되어 있다
- [x] 아키텍처 다이어그램이 현재 모듈 그래프를 반영한다
- [x] 오프라인 / 오래된 데이터 동작이 문서화되어 있다
- [x] 가격 변화, 관심 저장, 비교 화면까지 포함한 대표 시연 흐름이 있다
