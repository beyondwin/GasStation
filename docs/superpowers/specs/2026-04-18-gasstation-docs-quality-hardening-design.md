# GasStation Docs and Quality Hardening Design

**Date:** 2026-04-18

## Goal

GasStation을 포트폴리오/레퍼런스 프로젝트로 검토하는 사람이 짧은 시간 안에 구조, 설계 의도, 검증 수준을 이해할 수 있도록 문서 체계를 재정렬하고, 현재 정책과 맞지 않는 불필요한 코드/의존/호환 흔적을 함께 정리한다.

이번 작업은 문서만 늘리는 것이 아니라 아래 세 가지를 동시에 맞춘다.

- 검토자 진입 경험 개선
- 공식 문서와 실제 코드/CI의 정합성 강화
- 현재 지원 정책과 맞지 않는 잔여물 정리

## Audience and Success Bar

1차 독자는 팀 내부 유지보수자가 아니라 포트폴리오/레퍼런스 프로젝트 검토자다.

평가 목표는 아래 두 축을 균형 있게 만족하는 것이다.

- 5~10분 안에 구조와 강점을 빠르게 이해할 수 있어야 한다.
- 더 깊게 읽는 사람에게는 아키텍처 선택, 상태 모델, 오프라인 전략, 테스트 전략의 설계 수준이 설득력 있게 드러나야 한다.

## Current State Summary

현재 저장소는 이미 상당 부분 정리되어 있다.

- `app / feature / domain / data / core / tools / benchmark` 멀티모듈 구조가 실제 코드와 일치한다.
- `docs/architecture.md`, `docs/state-model.md`, `docs/offline-strategy.md`, `docs/project-reading-guide.md`가 존재한다.
- `domain`, `data`, `core`, `feature`, `app`, `benchmark` 전반에 테스트가 분포되어 있다.
- `demo`와 `prod` flavor가 분리되어 있고, demo seed 기반 시연 경로가 존재한다.

반면 아래 간극이 남아 있다.

- README가 포트폴리오 검토자 기준의 랜딩 페이지 역할을 충분히 하지 못한다.
- 모듈 경계, 검증 전략, CI 기준이 공식 문서로 분리되어 있지 않다.
- README의 검증 서술과 실제 CI 범위가 일치하지 않는다.
- 레거시 미지원 정책과 맞지 않는 호환 코드/잔여 의존이 일부 남아 있다.
- 이름이나 구조 때문에 현재 의도보다 더 오래된 잔재처럼 보이는 코드가 있다.

## Design Principles

- 문서는 많이 쓰는 것이 아니라 역할이 분명해야 한다.
- 공식 문서와 작업 히스토리는 구분한다.
- 현재 지원하는 경로만 설명하고 검증한다.
- 레거시 유저/레거시 앱 호환을 위한 코드와 설명은 유지하지 않는다.
- 코드 정리는 문서 정리와 분리하지 않고, 설명력을 높이는 방향으로 함께 수행한다.

## Documentation Information Architecture

문서 체계는 세 층으로 재구성한다.

### 1. `README.md`

검토자용 메인 랜딩 페이지다. README는 프로젝트의 전체 사실을 모두 담는 문서가 아니라, "왜 이 프로젝트를 더 읽어야 하는가"를 빠르게 설득하는 문서로 제한한다.

포함 내용:

- 프로젝트 한 줄 소개
- 왜 이 앱이 레퍼런스 프로젝트인지
- 핵심 사용자 플로우 3개
- 모듈 구조 한 장 요약
- `demo` / `prod` 실행 차이
- 검증 수준 요약
- 상세 공식 문서 링크

제거 대상:

- 유지 비용이 큰 장문의 구현 세부
- README 안에서만 유지되는 긴 검증 명령 설명
- 공식 문서와 중복되는 상세 서술

### 2. `docs/`

공식 문서 영역이다. 여기의 문서는 README에서 링크되는 "현재 프로젝트의 정식 설명"으로 취급한다.

유지/개선 문서:

- `docs/architecture.md`
- `docs/state-model.md`
- `docs/offline-strategy.md`
- `docs/project-reading-guide.md`

신규 문서:

- `docs/module-contracts.md`
- `docs/test-strategy.md`
- `docs/verification-matrix.md`

### 3. `docs/superpowers/`

작업 히스토리 영역이다. spec/plan 문서는 유지하되, 포트폴리오 검토자가 먼저 읽는 공식 문서로 취급하지 않는다.

즉, `docs/`는 현재 프로젝트 설명이고 `docs/superpowers/`는 작업 기록이다.

## Official Document Scope

### `docs/architecture.md`

현재의 모듈 설명 중심 문서에서 확장해 아래를 포함한다.

- 의존 방향과 책임 분리 이유
- `app`이 composition root인 이유
- 상태 생성 지점과 데이터 흐름 연결
- `demo` / `prod` 분리 이유와 트레이드오프
- 현재 구조가 레퍼런스 앱으로서 어떤 장점을 가지는지

### `docs/module-contracts.md`

모듈별 계약을 문서화한다.

섹션:

- 모듈 지도 한눈 요약
- 모듈별 책임
- 허용 의존 / 금지 의존
- 대표 진입 파일
- 검토자가 봐야 할 설계 포인트
- 의도적으로 남긴 트레이드오프

이 문서는 읽기 가이드가 아니라 경계 계약 문서다.

### `docs/state-model.md`

현재 구조를 유지하되, 아래를 더 명확히 한다.

- 영속 상태와 세션 상태의 경계
- 저장소 읽기 모델의 책임
- effect와 화면 상태를 분리한 이유
- demo 강제 위치/시드 경로가 상태 모델에 미치는 영향

### `docs/offline-strategy.md`

현재 문서를 유지하되, 아래를 더 분명히 한다.

- stale 유지 전략의 사용자 가치
- Room snapshot과 history 조합이 어떤 실패 모드를 커버하는지
- watchlist fallback이 왜 필요한지
- demo가 "오프라인 의미 체계 시연용"이라는 점

### `docs/project-reading-guide.md`

역할을 "처음 읽는 순서 안내"로 제한한다.

이 문서에는 설계 설득이나 CI 기준을 넣지 않는다.

### `docs/test-strategy.md`

테스트 전략을 공식화한다.

섹션:

- 테스트 철학: 현재 지원 경로 중심, 레거시 호환 비대상
- 계층별 테스트 목적
- `domain` / `data` / `feature` / `app integration` / `benchmark` 검증 범위
- 일부를 테스트하지 않는 이유
- 회귀 위험이 큰 영역
- 테스트 이름과 실제 신뢰 범위의 관계

### `docs/verification-matrix.md`

실행 명령과 자동화 범위를 정리한다.

섹션:

- 빠른 로컬 확인
- 머지 전 권장 검증
- CI 필수 검증
- 데모 시연 전 점검
- 각 명령이 보장하는 범위
- README와 CI를 이 문서 기준으로 맞추는 규칙

## Cleanup and Simplification Scope

이번 작업은 문서 정리와 함께, 실제로 설명력을 떨어뜨리는 불필요한 것들을 정리한다.

정리 기준은 아래 세 가지다.

- 실제 참조가 없으면 삭제 우선
- 참조는 있으나 현재 가치 설명에 기여하지 않으면 축소 또는 이름 정리
- 예외 경로지만 시연/제품 의미가 있으면 남기되 의도를 공식 문서에 명시

### A. 삭제 우선 후보

#### `:core:testing`

현재 어떤 모듈도 `:core:testing`을 의존하지 않는다. 의미 없는 공통 모듈은 구조만 복잡하게 만든다. 실제 재사용이 없다면 삭제한다.

#### `core:common`의 `AppResult`, `DispatcherProvider`

현재 아키텍처는 Flow + UI state 중심인데, 이 공통 타입들은 실사용이 거의 없다. 실제 설계에 기여하지 않으면 제거하거나 더 작은 범위로 축소한다.

### B. 축소 또는 제거 후보

#### 레거시 포맷 호환 코드

`core/datastore/.../UserPreferencesSerializer.kt`의 `decodeLegacyPipeFormat`은 과거 포맷 호환 로직이다. 현재 정책은 레거시 유저/레거시 앱 미지원이므로 제거 대상이다.

#### Kakao 네트워크 의존 잔존물

문서상 현재 앱 런타임과 시드 생성은 더 이상 Kakao 좌표 변환 API에 의존하지 않는다. 그런데 코드에는 아래가 남아 있다.

- `KAKAO_API_KEY`
- `NetworkRuntimeConfig.kakaoApiKey`
- `KakaoService`
- `NetworkModule.provideKakaoService`
- 관련 테스트

외부 지도 앱 딥링크의 Kakao Map 지원은 유지하되, 네트워크 API 의존 흔적은 정리한다.

#### 불필요한 Gradle 의존

현재 코드 직접 참조가 보이지 않는 모듈 의존은 제거 대상이다.

후보:

- `data:station -> core:location`
- `domain:settings -> core:model`

실제 import 기준으로 다시 확인한 뒤 정리한다.

### C. 이름 또는 역할 정리 후보

#### `Legacy*` UI 네이밍

현재 제품이 레거시 앱을 지원하지 않는데 `LegacyChrome*`, `LegacyTopBar`, `LegacyYellowBackground` 같은 이름은 과거 앱 잔재처럼 보일 수 있다.

선택지는 두 가지다.

- 계속 사용할 의도가 있다면 현행 디자인 시스템 이름으로 바꾼다.
- 진짜 과거 전환기의 임시 네이밍이라면 더 과감히 정리한다.

#### `Legacy*` 내부 구조체 일부

`LegacyListRow`가 실사용이 약하고, 그 주변의 slot/content 구조체가 실제 화면보다 과한 추상화라면 줄인다.

### D. 유지하되 문서화가 필요한 것

#### `DemoLocationOverride`

삭제 대상이 아니다. demo 시연 경로를 안정적으로 고정하는 핵심 장치다. 다만 왜 존재하는지 README/architecture에서 분명히 설명해야 한다.

#### `StationEventLogger`

지금은 작은 확장 포인트로 보이지만, 관찰성 확장 의도가 있다면 문서에서 "왜 추상화가 있는지"를 설명해야 한다. 아니라면 축소도 검토한다.

#### `DemoSeedStartupHook`의 직접 DB 초기화 경로

예외적인 부트스트랩 코드지만 demo 시연 안정성에는 의미가 있다. 유지한다면 "왜 일반 저장소 경로 대신 직접 적재하는지"를 문서화한다.

## Testing Strategy Design

검증 기준은 과거 호환성이 아니라 "현재 지원하는 구조와 실행 경로를 얼마나 신뢰할 수 있는가"다.

### `domain`

- 값 객체 불변식
- 정렬/필터/캐시 키 규칙
- 도메인 계약 surface

가장 빠르고 안정적인 핵심 규칙 검증 층으로 유지한다.

### `data` and `core infra`

- Room DAO
- 캐시 정책
- 가격 히스토리
- serializer
- 네트워크 매핑 및 좌표 변환

신뢰성의 핵심이므로 회귀 가치가 큰 테스트를 우선 유지한다.

### `feature`

- ViewModel 상태 전이
- effect 방출
- 핵심 Compose 화면 계약

모든 UI를 빽빽하게 검증하기보다, 사용자 플로우와 상태 조합 회귀 위험이 큰 부분에 집중한다.

### `app integration`

- startup hook
- Hilt 그래프
- demo seed 로딩
- 대표 계측 플로우

demo 경로는 포트폴리오 시연 경로이므로 공식 검증 대상이다.

### `benchmark`

- cold start
- 대표 이동 흐름

성능 근거를 보여주는 보조 수단이 아니라, 레퍼런스 앱 완성도를 설명하는 증거로 유지한다.

## Verification Matrix Design

검증은 세 단계로 나눈다.

### 1. 빠른 로컬 확인

- 빠르게 빌드와 핵심 경로만 확인
- 개발 중 반복 실행용

### 2. 머지 전 권장 검증

- 핵심 단위 테스트
- 대표 UI 테스트
- 대표 assemble
- 필요 시 계측 테스트

### 3. CI 필수 검증

자동화에서 항상 보장해야 하는 최소 신뢰 매트릭스다.

현재 CI에는 아래 보강을 검토한다.

- `:core:database:testDebugUnitTest`
- `:core:designsystem:testDebugUnitTest`
- `:core:network:test`
- `:feature:watchlist:testDebugUnitTest`
- `:app:testDemoDebugUnitTest` 또는 동등한 앱 단위 테스트 범위

README에는 전체 명령을 장황하게 복제하지 않고, 검증 레벨과 의미만 요약하고 상세 명령은 `docs/verification-matrix.md`로 보낸다.

## Implementation Roadmap

### Phase 1. 실제 미사용/불필요 항목 정리

- `:core:testing` 실사용 여부 최종 확인 후 제거
- `core:common`의 불필요 타입 정리
- 레거시 포맷 호환 코드 제거
- Kakao 네트워크 의존 흔적 제거
- 불필요한 Gradle 의존 제거

### Phase 2. 테스트와 CI 기준 재정렬

- CI 매트릭스 보강 또는 재정리
- README와 CI의 검증 범위 불일치 제거
- 로컬/CI/시연 전 검증 레벨 구분

### Phase 3. 공식 문서 작성/개정

- `docs/module-contracts.md`
- `docs/test-strategy.md`
- `docs/verification-matrix.md`
- 기존 architecture/state/offline/reading-guide 개정

### Phase 4. README 재작성

- 포트폴리오 랜딩 페이지 중심으로 재작성
- 공식 문서 링크 구조 정리

### Phase 5. 최종 정합성 검증

- 문서 내용과 실제 코드/CI 비교
- 명령어, 모듈 책임, 정리 대상 반영 여부 확인
- 검토자 시점에서 읽기 순서 검증

## Success Criteria

- 검토자가 `README -> architecture -> test-strategy` 순서로 10분 내 구조를 이해할 수 있다.
- 공식 문서와 실제 CI/코드가 충돌하지 않는다.
- 레거시 미지원 정책과 충돌하는 호환 코드가 제거된다.
- 미사용 모듈과 무의미한 의존이 줄어든다.
- 프로젝트 강점이 `구조`, `상태 설계`, `오프라인 전략`, `검증 수준`으로 분명하게 드러난다.

## Non-Goals

- 신규 기능 추가
- 앱 UI 리디자인
- 새로운 사용자 플로우 도입
- 과거 버전 호환 유지
- 대규모 아키텍처 재구축

## Risks and Mitigations

- "미사용처럼 보이는 코드"가 사실은 테스트나 시연 경로에서 필요할 수 있다.
  - 삭제 전 실제 참조, 테스트, flavor 경로를 다시 확인한다.
- README를 너무 마케팅 문서처럼 만들면 기술 깊이가 약해질 수 있다.
  - README는 짧게 유지하되 공식 문서로 자연스럽게 연결한다.
- CI 범위를 늘리면 실행 시간이 증가할 수 있다.
  - 빠른 피드백이 필요한 항목과 신뢰 확보용 항목을 분리해 설계한다.
- `Legacy*` 네이밍 정리가 넓게 퍼져 있을 수 있다.
  - 이름 변경은 별도 단계로 나누고, 먼저 문서와 의미 정리를 선행한다.
