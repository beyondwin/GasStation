# AGENTS.md

GasStation을 바꿀 때 이 파일부터 읽는다. 긴 절차는 아래 문서로 보낸다.

## 작업 규칙

- 시작 전 `git status --short`로 기존 변경을 본다.
- 활성 모듈은 폴더가 아니라 `settings.gradle.kts`의 `include`다.
- 판단 순서는 코드와 `settings.gradle.kts` → live 문서 → `docs/superpowers/`, `docs/history/`, `docs/improvements/` 이력이다.
- 이어 할 작업이면 새 branch/worktree를 만들기 전에 `git worktree list`, status, diff, `.superpowers/sdd/progress.md`를 본다.
- 비사소한 변경 전 `scripts/agent/preflight.sh`, 끝나기 전 `scripts/agent/verify.sh auto`를 쓴다.
- 새 의존이나 위치를 정하기 전에 `docs/module-contracts.md`를 본다.
- 작업 순서, 테스트 선택, 체크리스트는 `docs/agent-workflow.md`다.

## 에이전트 실행 원칙

- 실행 요청은 계획 제안에 그치지 않고, 요청 범위의 수정·검증·보고까지 마친다.
- 이미 승인된 작업은 다시 허락을 묻지 않는다. 통상적인 선택은 기존 코드와 승인된 결정에 맞추고, 결과에 영향을 주는 가정은 알린다.
- 근거로 해소할 수 없고 결과·범위를 크게 바꾸는 불확실성만 질문한다. 답이 필요한 부분은 기다리고, 독립적인 작업은 계속한다. 별도 승인이 필요하면 승인 전 가능한 준비와 검증을 먼저 마친다.
- 시스템·개발자 지침 안에서 사용자의 명시적 요청을 스킬의 일반 절차보다 우선한다. 스킬 때문에 중단하거나 승인을 요청하면 해당 `SKILL.md` 경로와 문구를 제시하고, 명시된 요구와 자신의 해석을 구분한다.
- 추가 요구는 진행 중인 작업에 반영한다. 중간 질문에 답한 뒤 작업을 이어가되, 사용자가 목표를 바꾸거나 취소하면 따른다.
- 작은 수정은 직접 처리한다. 허용된 병렬 위임은 독립적인 작업에만 쓰고, 범위·수정 파일·기대 결과를 정한 뒤 결과와 diff를 확인한다.
- 변경 위험과 `docs/verification-matrix.md`에 맞춰 필수 검사를 마친다. 낮은 위험의 문구 수정에 불필요한 테스트를 추가하지 않고, 새 변경·실패·미해결 우려가 있을 때만 검사를 확대하거나 반복한다.

## 제품

한국 운전자가 현재 위치 근처 주유소를 가격, 거리, 브랜드, 유종, 관심, 외부 지도로 빠르게 비교하는 앱이다.

- `demo`와 `prod`는 둘 다 정식 경로다. `demo`는 mock 예외가 아니라 문서·테스트·benchmark가 기대는 재현 경로다.
- 가격이 카드의 첫 읽기 대상이다.
- 거리, 역명, 브랜드, 유종, watch, freshness, 실패 상태는 가격 결정을 돕는 정보다.
- UI는 yellow, black, white와 가격 우선 위계를 지킨다.
- UI를 바꾸기 전에 `core:designsystem` 토큰과 공통 chrome을 본다.
- generic Material 카드나 장식으로 읽기 속도를 늦추지 않는다.

## 모듈

- `app` — Hilt 조립, startup, navigation, flavor, 외부 앱 연결. 정책을 두지 않는다.
- `feature:*` — 화면 상태, UI model, Compose, effect. Room, Retrofit, DataStore, `core:location`을 직접 부르지 않는다.
- `domain:*` — repository 계약, use case, 도메인 모델. Android, Compose, Room, Retrofit, DataStore 타입을 노출하지 않는다.
- `data:*` — 저장소 구현과 캐시 조합. 화면 상태나 Compose 타입을 만들지 않는다.
- `core:model` — 값 객체.
- `core:designsystem` — 테마, 토큰, 공통 UI. 화면 전용 문구를 두지 않는다.
- `core:observability` — SDK에 묶이지 않은 관찰 계약. 실제 SDK는 `app`이 연결한다.
- `core:database`, `core:network`, `core:datastore`, `core:location` — 공유 인프라.
- `tools:demo-seed`, `benchmark` — 앱 기능을 우회하는 경로가 아니다.

자세한 금지 의존은 `docs/module-contracts.md`다.

## 바꿀 때

- 화면부터 시작하지 말고 domain 계약과 상태 흐름부터 본다.
- 설정 쓰기는 `domain:settings` use case만 탄다.
- 위치는 `feature:station-list -> domain:location -> core:location`이다.
- 목록 검색은 `StationQuery`, `StationRepository`, 캐시 정책으로 본다.
- 캐시 있음은 `fetchedAt != null`이 아니라 `StationSearchResult.hasCachedSnapshot`이다.
- watchlist는 현재 목록의 복제가 아니라 저장 항목 비교 화면이다.
- UI 변경은 접근성, semantics, test tag를 지우지 않는다. 지우면 대체 테스트를 같이 만든다.
- 문서가 약속한 사용자 흐름을 바꾸면 테스트와 README/demo story도 본다.
- 진단·리뷰·설명만 요청되면 구현하지 않는다.
- Graphify 같은 생성형 분석은 live 문서 → `rg` → 코드/테스트로 관계를 못 찾을 때만 쓴다.
- push, PR, tag, release, publish, deploy는 요청 범위에 있을 때만 한다.
- 끝난 보고는 결과부터 간결한 한국어로 쓴다. 변경 파일, 실행한 명령과 결과, 안 돌린 범위, local/remote 상태를 적고 사실과 가정을 구분한다.

## 어디를 읽나

- 길을 잃었을 때: `docs/project-reading-guide.md`
- 작업 순서: `docs/agent-workflow.md`
- 모듈 위치: `docs/module-contracts.md`
- 구조: `docs/architecture.md`
- 상태: `docs/state-model.md`
- 캐시·stale·실패: `docs/offline-strategy.md`
- 테스트와 명령: `docs/test-strategy.md`, `docs/verification-matrix.md`
- 보안: `docs/security-trade-offs.md`

이 파일에는 항상 필요한 규칙만 둔다. 새 절을 넣기 전에 더 맞는 문서가 있는지 먼저 본다.
