# AGENTS and Docs Guardrail Restructure Design

**Date:** 2026-04-23

## Goal

GasStation의 에이전트용 문서 체계를 "실수 방지" 중심으로 재정리한다.

우선순위는 다음과 같다.

1. 계층 경계, demo/prod, 캐시 fallback, 설정 쓰기, 위치 경계 같은 회귀 위험을 먼저 막는다.
2. 새 작업자가 처음 1분 안에 어디를 봐야 하는지 알 수 있게 한다.
3. 같은 원칙이 여러 문서에 반복되는 비용을 줄인다.
4. 포트폴리오 정체성은 유지하되, `AGENTS.md`의 주된 목적이 되지 않게 한다.

이번 작업은 코드 동작을 바꾸지 않는다. 문서 구조와 문서 간 책임을 정리해 이후 코드 변경이 더 안전하게 진행되도록 만드는 작업이다.

## Audience and Success Bar

1차 독자는 GasStation 저장소에서 변경을 수행하는 에이전트와 개발자다. 리뷰어와 면접관에게 보이는 설명력도 중요하지만, 이번 작업의 주된 성공 기준은 작업자가 실수하지 않는 것이다.

성공 기준:

- `AGENTS.md`만 읽어도 반드시 지켜야 할 guardrail이 보인다.
- 세부 절차가 필요할 때 어느 문서를 열어야 하는지 바로 알 수 있다.
- 모듈 책임의 단일 출처가 `docs/module-contracts.md`로 명확하다.
- 작업 절차의 단일 출처가 `docs/agent-workflow.md`로 명확하다.
- 상태, 오프라인, 테스트, 검증 명령의 세부 설명이 전문 문서에 남아 있다.
- README와 `docs/superpowers/*`는 이번 재구성의 주 대상이 아니다.

## Current State Summary

현재 문서 체계는 이미 비교적 잘 나뉘어 있다.

- `AGENTS.md`는 짧은 운영 헌장이다.
- `docs/agent-workflow.md`는 실제 작업 절차와 체크리스트를 담는다.
- `docs/module-contracts.md`는 모듈 책임 표를 담는다.
- `docs/project-reading-guide.md`는 처음 읽을 문서와 질문별 진입점을 안내한다.
- `docs/state-model.md`, `docs/offline-strategy.md`, `docs/test-strategy.md`, `docs/verification-matrix.md`는 전문 주제를 다룬다.

남은 문제는 문서가 없어서가 아니라 역할 경계가 조금 겹친다는 점이다.

- `AGENTS.md`와 `docs/agent-workflow.md`가 모듈 경계와 변경 원칙을 일부 반복한다.
- `AGENTS.md`의 읽기 순서가 `docs/project-reading-guide.md`와 일부 겹친다.
- `docs/agent-workflow.md`에 모듈별 상세 책임이 들어 있어 `docs/module-contracts.md`와 중복된다.
- 전문 문서들이 자신이 어떤 판단의 source of truth인지 첫 화면에서 충분히 강하게 말하지 않는다.

## Chosen Approach

선택한 접근은 guardrail-first 재구성이다.

`AGENTS.md`는 모든 작업자가 반드시 지켜야 하는 금지선과 판단 기준만 둔다. 긴 절차와 세부 설명은 연결 문서로 보낸다.

문서별 역할은 아래처럼 고정한다.

| 문서 | 역할 |
| --- | --- |
| `AGENTS.md` | 모든 변경에 적용되는 실수 방지 운영 헌장 |
| `docs/project-reading-guide.md` | 처음 들어온 작업자를 위한 질문별 라우터 |
| `docs/agent-workflow.md` | 실제 변경 절차, 테스트 선택, 최종 점검 |
| `docs/module-contracts.md` | 모듈 책임과 금지 의존의 단일 출처 |
| `docs/architecture.md` | 현재 모듈 그래프와 런타임 흐름 설명 |
| `docs/state-model.md` | 영속/세션/읽기/UI effect 상태 계약의 단일 출처 |
| `docs/offline-strategy.md` | 캐시, stale, refresh 실패, watchlist fallback 의미의 단일 출처 |
| `docs/test-strategy.md` | 테스트 철학과 계층별 신뢰 범위의 단일 출처 |
| `docs/verification-matrix.md` | 실제 검증 명령과 실행 범위의 단일 출처 |

## `AGENTS.md` Design

`AGENTS.md`는 지금보다 더 guardrail 중심으로 읽히게 한다. 길이는 현재와 비슷하거나 더 짧게 유지한다.

예상 섹션:

- `Operating Contract`
- `Product And UI Invariants`
- `Architecture Guardrails`
- `Change Guardrails`
- `Required Reading By Task`
- `Documentation Ownership`

### Content to Keep

아래 내용은 모든 작업자가 항상 알아야 하므로 `AGENTS.md`에 남긴다.

- GasStation은 실제 운전자가 가격, 거리, 브랜드, 유종, watchlist, 외부 지도 연결 기준으로 가까운 주유소를 빠르게 비교하는 앱이다.
- 가격은 station card의 첫 번째 읽기 대상이다.
- `demo`와 `prod`는 모두 정식 실행 경로다.
- 활성 모듈 기준은 `settings.gradle.kts`다.
- `app`은 조립만 담당한다.
- `feature:*`는 Room, Retrofit, DataStore, `core:location` 구현을 직접 호출하지 않는다.
- `domain:*`은 Android, Compose, Room, Retrofit, DataStore 타입을 노출하지 않는다.
- `data:*`는 화면 상태와 Compose 타입을 만들지 않는다.
- 위치 조회 경계는 `feature:station-list -> domain:location -> core:location`이다.
- 설정 쓰기는 명시적 `domain:settings` use case를 통한다.
- 캐시 존재 판단은 `fetchedAt != null`이 아니라 `StationSearchResult.hasCachedSnapshot` 의미를 우선한다.
- 사용자 흐름이 바뀌면 테스트와 README/demo story도 함께 점검한다.

### Content to Move or Compress

아래 내용은 `AGENTS.md`에서 줄이거나 링크로 바꾼다.

- 모듈별 상세 책임 표는 `docs/module-contracts.md`로 보낸다.
- 변경 유형별 절차는 `docs/agent-workflow.md`로 보낸다.
- 처음 읽을 전체 순서는 `docs/project-reading-guide.md`로 보낸다.
- 상태, 오프라인, 테스트, 검증 명령의 세부 설명은 각각의 전문 문서로 보낸다.
- 포트폴리오 검토자에게 보여줄 설득 문장은 README와 architecture 문서에 둔다.

## Connected Document Design

### `docs/project-reading-guide.md`

이 문서는 라우터로 유지한다. 설계 원칙을 반복하기보다 "질문별 가장 빠른 진입점"을 더 명확하게 한다.

유지할 내용:

- 먼저 볼 문서 순서
- 질문별 가장 빠른 진입점
- 변경 목적별 바로 열 파일
- 길을 잃었을 때 돌아갈 핵심 파일

줄일 내용:

- `AGENTS.md`와 중복되는 운영 원칙
- `docs/module-contracts.md`와 중복되는 모듈 책임 설명

### `docs/agent-workflow.md`

이 문서는 절차 문서로 집중시킨다.

유지할 내용:

- 작업 시작 전 확인
- 새 기능 추가 순서
- 기존 동작 수정 순서
- UI, settings, location, station search, watchlist, demo/prod 변경 절차
- 테스트 선택
- 문서 업데이트 기준
- 최종 리뷰 체크리스트

줄일 내용:

- 모듈별 "여기에 둔다 / 두지 않는다" 상세 반복
- `docs/module-contracts.md` 표와 같은 내용의 긴 설명

### `docs/module-contracts.md`

이 문서는 모듈 책임의 단일 출처로 강화한다.

유지할 내용:

- 모듈 인벤토리 표
- 허용 의존과 금지 책임
- 경계가 헷갈릴 때 보는 기준
- 현재 프로젝트 전제

개선할 내용:

- 상단에 "모듈 위치 판단의 source of truth"라는 역할을 명확히 쓴다.
- `AGENTS.md`와 `agent-workflow.md`가 이 문서를 참조한다는 점을 명시한다.

### 전문 문서

전문 문서는 큰 리라이트 없이 상단 역할을 선명하게 한다.

- `docs/architecture.md`: 현재 모듈 그래프와 런타임 흐름의 source of truth
- `docs/state-model.md`: 상태 원천과 lifecycle 판단의 source of truth
- `docs/offline-strategy.md`: cache/stale/fallback 의미의 source of truth
- `docs/test-strategy.md`: 어떤 층을 어떤 테스트로 막는지의 source of truth
- `docs/verification-matrix.md`: 실제 검증 명령의 source of truth

## Scope

이번 설계에 포함한다.

- `AGENTS.md` 재구성
- `docs/project-reading-guide.md` 소폭 정리
- `docs/agent-workflow.md` 중복 축소
- `docs/module-contracts.md` 역할 강화
- `docs/architecture.md`, `docs/state-model.md`, `docs/offline-strategy.md`, `docs/test-strategy.md`, `docs/verification-matrix.md` 상단에 source-of-truth 문장 추가
- 문서 변경에 대한 맞춤형 검증

이번 설계에 포함하지 않는다.

- Android 코드 변경
- Gradle 설정 변경
- README의 제품 소개 구조 변경
- 기존 `docs/superpowers/specs/*` 또는 `docs/superpowers/plans/*` 히스토리 재작성
- 문서 전체 번역이나 스타일 전면 통일
- 새로운 기능 설계

## Verification

문서 작업이므로 검증은 정적 확인 중심으로 한다.

필수 확인:

- `git diff --check`
- `rg`로 남은 중복 핵심 문구 확인
- `AGENTS.md`가 짧은 운영 헌장 역할을 유지하는지 수동 리뷰
- `docs/project-reading-guide.md`, `docs/agent-workflow.md`, `docs/module-contracts.md` 사이의 역할 충돌 수동 리뷰

추가 확인:

- Markdown 표가 깨지지 않는지 확인
- `docs/verification-matrix.md`의 명령을 바꾸지 않았는지 확인

Gradle 테스트는 기본적으로 필요하지 않다. 코드와 빌드 설정을 바꾸지 않기 때문이다.

## Risks and Mitigations

### Risk: `AGENTS.md` becomes too thin

실수 방지선이 모두 링크 뒤로 숨어버리면 새 작업자가 처음에 중요한 불변식을 놓칠 수 있다.

Mitigation:

- `AGENTS.md`에는 반드시 지켜야 할 invariants를 직접 남긴다.
- 링크는 세부 설명으로만 사용한다.

### Risk: Documentation churn without clearer ownership

문장을 줄였지만 각 문서의 책임이 더 선명해지지 않으면 유지보수성이 좋아지지 않는다.

Mitigation:

- 각 주요 문서 상단에 그 문서가 소유하는 판단 범위를 명시한다.
- 중복 제거 기준은 "짧게 만들기"가 아니라 "source of truth를 하나로 만들기"로 둔다.

### Risk: Portfolio identity gets lost

`AGENTS.md`에서 포트폴리오 문장을 줄이면 프로젝트 정체성이 약해질 수 있다.

Mitigation:

- `AGENTS.md`에는 product/UI invariant만 남긴다.
- 포트폴리오 설득은 README와 architecture 문서가 담당하게 한다.

## Approval Notes

사용자는 최적화 우선순위를 `B -> A -> D -> C`로 정했다.

- B: 실수 방지
- A: 새 에이전트 온보딩 속도
- D: 문서 유지보수성
- C: 포트폴리오 정체성

사용자는 변경 범위로 문서 구조 전반 정리를 선택했고, guardrail-first 재구성 방향을 승인했다.
