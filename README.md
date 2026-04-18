# 주유주유소

현재 위치 주변의 주유소를 빠르게 찾고, 마지막 성공 결과를 오프라인으로 유지하며, 관심 주유소 비교까지 이어지는 멀티모듈 Android 레퍼런스 앱입니다.

## 왜 이 프로젝트인가

- `app / feature / domain / data / core` 책임 분리를 실제 코드와 문서에 맞춰 유지합니다.
- demo 시연 경로와 prod 실행 경로를 분리해 포트폴리오 검토와 실제 실행 조건을 동시에 설명합니다.
- Room 기반 snapshot + history 조합으로 stale fallback과 watchlist 비교를 구현합니다.
- 테스트 전략과 CI 매트릭스를 공식 문서로 관리합니다.

## 핵심 사용자 플로우

1. 현재 위치 기준 주유소 검색
2. 가격 변화와 관심 주유소 저장
3. 관심 주유소 비교 화면 진입

## 실행 모드

### Demo

- `demo` flavor는 API 키 없이 빌드하고 실행할 수 있습니다.
- 강남역 2번 출구 고정 위치와 승인된 demo seed 자산으로 항상 같은 시연 상태를 재현합니다.

```bash
./gradlew :app:assembleDemoDebug
```

### Prod

- `prod` flavor는 `opinet.apikey`가 필요합니다.

```bash
./gradlew :app:assembleProdDebug
```

예시:

```properties
# ~/.gradle/gradle.properties 또는 프로젝트 gradle.properties
opinet.apikey=your-opinet-key
```

시드를 다시 생성하려면 `./gradlew :tools:demo-seed:generateDemoSeed`를 실행합니다. 현재 seed 생성과 앱 런타임 검색은 `opinet.apikey`만 사용합니다.

## 문서

- [프로젝트 읽기 가이드](docs/project-reading-guide.md)
- [아키텍처](docs/architecture.md)
- [모듈 계약](docs/module-contracts.md)
- [상태 모델](docs/state-model.md)
- [오프라인 전략](docs/offline-strategy.md)
- [테스트 전략](docs/test-strategy.md)
- [검증 매트릭스](docs/verification-matrix.md)

## 검증

- 모든 Gradle 검증은 Java 17 기준으로 실행합니다.
- `./benchmark/run-demo-benchmark.sh`는 Java 17로 `:app:assembleDemoDebug`와 `:benchmark:assemble`만 빠르게 확인하는 assemble 전용 도우미 스크립트입니다.
- 전체 신뢰 기준은 [검증 매트릭스](docs/verification-matrix.md)에 고정합니다.

## 완료 기준 점검표

- [x] `demo` flavor는 API 키 없이 실행할 수 있다
- [x] `prod` flavor 문서에 필요한 로컬 시크릿이 정리되어 있다
- [x] 아키텍처 다이어그램이 현재 모듈 그래프를 반영한다
- [x] 오프라인 / 오래된 데이터 동작이 문서화되어 있다
- [x] 가격 변화, 관심 저장, 비교 화면까지 포함한 대표 시연 흐름이 있다
