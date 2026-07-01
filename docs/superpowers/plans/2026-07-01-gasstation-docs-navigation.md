# GasStation Documentation Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve GasStation documentation navigation so both human developers and coding agents can find current contracts quickly and avoid using historical design records as current truth.

**Architecture:** This is a docs-only change. `docs/project-reading-guide.md` becomes the shared router, `README.md` remains the landing page, `docs/onboarding/developer-onboarding-guide.md` remains the learning handbook, `docs/agent-workflow.md` owns work procedure, and `docs/verification-matrix.md` owns command selection.

**Tech Stack:** Markdown documentation, existing Gradle project structure, existing `git diff --check` verification. No application code, Gradle wiring, or new validation script is introduced.

## Global Constraints

- Do not change app code, Gradle build logic, runtime behavior, tests, module boundaries, or architecture.
- Do not edit `AGENTS.md` in this implementation.
- Do not move, delete, or archive existing `docs/superpowers/`, `docs/history/`, or `docs/improvements/` files.
- Use `settings.gradle.kts` as the active Gradle module source of truth.
- Treat `docs/superpowers/`, `docs/history/`, and `docs/improvements/` as historical or evidence context unless a task explicitly asks for history.
- Keep `README.md` useful as a project-facing overview; do not turn it into the full maintainer handbook.
- Keep `docs/onboarding/developer-onboarding-guide.md` useful as a long-form learning document; do not make it replace live contract docs.
- Primary verification command: `git diff --check -- README.md docs/project-reading-guide.md docs/onboarding/developer-onboarding-guide.md docs/agent-workflow.md docs/verification-matrix.md`

---

## File Structure

**Modify: `docs/project-reading-guide.md`**

Responsibility: top-level navigation router for people and agents. It should classify current contract docs, learning docs, and historical/evidence docs, then route by reader type and task type.

**Modify: `README.md`**

Responsibility: project landing page. It should keep product overview, preview, runtime modes, code tour, performance, and verification summary while grouping documentation links by purpose and authority.

**Modify: `docs/onboarding/developer-onboarding-guide.md`**

Responsibility: human learning handbook. It should teach the project narrative and first-change workflow while clearly deferring current contract decisions to live docs.

**Modify: `docs/agent-workflow.md`**

Responsibility: practical change procedure. It should tell agents and developers how to choose live docs before changes and which docs to inspect or update by change type.

**Modify: `docs/verification-matrix.md`**

Responsibility: command selection and execution scope. It should distinguish historical doc edits, live contract doc edits, and README/release/performance evidence edits.

No new source files, test files, scripts, dependencies, or Gradle tasks are expected.

---

### Task 1: Strengthen The Shared Reading Router

**Files:**
- Modify: `docs/project-reading-guide.md`

**Interfaces:**
- Consumes: Approved spec `docs/superpowers/specs/2026-07-01-gasstation-docs-navigation-design.md`.
- Produces: A clearer router used by downstream README, onboarding, workflow, and verification edits.

- [ ] **Step 1: Re-read the current router and active module list**

Run:

```bash
sed -n '1,220p' docs/project-reading-guide.md
sed -n '1,220p' settings.gradle.kts
```

Expected: `docs/project-reading-guide.md` starts with `# 프로젝트 읽기 가이드`; `settings.gradle.kts` includes the active module list from `:app` through `:benchmark`.

- [ ] **Step 2: Replace the opening through the historical-doc warning**

Replace the current introduction, `## 먼저 볼 문서` section, historical warning, and ADR bullet at the top of `docs/project-reading-guide.md` with this exact text:

```markdown
# 프로젝트 읽기 가이드

이 문서는 처음 보는 사람과 에이전트가 "지금 목적에 맞게 무엇을 먼저 읽어야 하는가"를 찾는 라우터입니다. 운영 계약은 `AGENTS.md`, 작업 절차는 `docs/agent-workflow.md`, 모듈 위치 판단은 `docs/module-contracts.md`가 소유하고, 여기서는 문서 선택과 읽기 순서만 제공합니다.

## 문서 분류

### 현재 계약 문서

현재 구조, 책임, 상태, 오프라인 정책, 테스트 의미, 검증 명령을 판단할 때 우선합니다.

1. `AGENTS.md`
2. `README.md`
3. `docs/project-reading-guide.md`
4. `docs/agent-workflow.md`
5. `docs/module-contracts.md`
6. `docs/architecture.md`
7. `docs/state-model.md`
8. `docs/offline-strategy.md`
9. `docs/test-strategy.md`
10. `docs/verification-matrix.md`
11. `docs/security-trade-offs.md`
12. `docs/deployment.md`
13. `docs/performance.md`
14. `docs/adr/`

현재 계약은 위 문서와 실제 코드가 기준입니다. 활성 모듈은 항상 `settings.gradle.kts`의 Gradle include 기준으로 판단합니다.

### 학습 문서

- `docs/onboarding/developer-onboarding-guide.md`: 처음 프로젝트를 맡은 개발자가 제품 목적, 기술 선택, 런타임 흐름, 첫 변경 절차를 순서대로 이해하기 위한 핸드북입니다.
- `CONTRIBUTING.md`: 새 기여자가 실행, 검증, 커밋 기준을 빠르게 확인하기 위한 기여 가이드입니다.
- `.impeccable.md`: UI 작업 시 yellow/black/white 정체성과 가격 우선 정보 위계를 확인하는 디자인 컨텍스트입니다.

학습 문서는 이해를 돕지만 현재 계약을 대체하지 않습니다. 판단이 겹치면 현재 계약 문서를 우선합니다.

### 이력과 근거 문서

- `docs/superpowers/specs/`: 작성 당시 설계 결정 기록
- `docs/superpowers/plans/`: 작성 당시 구현 계획 기록
- `docs/history/`: 심층 분석과 개선 이력
- `docs/improvements/`: 특정 개선 패스의 설계와 구현 기록
- `docs/release-notes/`: 릴리스별 변경 근거
- `docs/compose-metrics/`: Compose stability 측정 스냅샷

이 문서들은 왜 그런 결정이 있었는지 이해할 때 유용합니다. 하지만 그 안에는 작성 당시의 모듈 경계, API 키, Gradle 명령, 구현 계획이 남아 있을 수 있으므로 현재 기준을 판단할 때는 `settings.gradle.kts`, 현재 계약 문서, 실제 코드를 우선합니다.

## 에이전트 Fast Path

1. `git status --short`로 기존 사용자 변경을 확인합니다.
2. `AGENTS.md`를 읽고 항상 적용되는 운영 계약을 확인합니다.
3. `settings.gradle.kts`에서 활성 모듈을 확인합니다.
4. 이 문서의 "변경 목적별 바로 열 파일"과 "질문별 가장 빠른 진입점"에서 목적에 맞는 현재 계약 문서를 고릅니다.
5. 관련 테스트 파일을 먼저 읽고 현재 계약을 확인합니다.
6. `docs/superpowers/`, `docs/history/`, `docs/improvements/`는 사용자가 이력 분석을 요청했거나 현재 판단의 배경이 필요할 때만 근거로 봅니다.

## 신규 개발자 Fast Path

1. `README.md`에서 제품 목적, 실행 모드, 대표 구조를 봅니다.
2. `docs/onboarding/developer-onboarding-guide.md`의 1장부터 6장까지 읽어 제품과 기술 선택을 이해합니다.
3. `demo` 경로로 앱을 실행하거나 `README.md`의 미리보기와 5분 코드 투어를 따라갑니다.
4. 목록 화면을 처음 추적할 때는 이 문서의 "권장 코드 읽기 순서" 중 "목록 플로우"를 따릅니다.
5. 실제 변경 전에는 `docs/agent-workflow.md`, `docs/module-contracts.md`, 관련 현재 계약 문서를 다시 확인합니다.

## 먼저 볼 문서

전체를 처음 훑을 때의 기본 순서는 아래와 같습니다.

1. `AGENTS.md`
2. `README.md`
3. `docs/project-reading-guide.md`
4. `docs/onboarding/developer-onboarding-guide.md`
5. `docs/architecture.md`
6. `docs/module-contracts.md`
7. `docs/agent-workflow.md`
8. `docs/state-model.md`
9. `docs/offline-strategy.md`
10. `docs/test-strategy.md`
11. `docs/verification-matrix.md`

이 순서는 "운영 계약 -> 큰 그림 -> 라우터 -> 개발자 온보딩 -> 구조 -> 경계 -> 작업 절차 -> 상태 -> 캐시/오프라인 -> 테스트 의미 -> 실행 명령" 순서입니다.
```

- [ ] **Step 3: Split the question table into purpose-focused rows without deleting existing coverage**

Keep the `## 질문별 가장 빠른 진입점` heading. Update the first four rows so the top of the table reads exactly:

```markdown
| 질문 | 먼저 볼 파일 |
| --- | --- |
| 모든 작업에 적용되는 운영 원칙은 어디서 보나 | `AGENTS.md` |
| 나는 에이전트이고 변경 작업을 시작하려 한다 | `AGENTS.md`, `settings.gradle.kts`, `docs/agent-workflow.md`, 이 문서의 변경 목적별 진입점 |
| 처음 프로젝트를 맡은 개발자는 무엇부터 보면 되나 | `README.md`, `docs/onboarding/developer-onboarding-guide.md`, 이 문서의 신규 개발자 Fast Path |
| 앱 전체 구조는 어디서 보나 | 먼저 `settings.gradle.kts`, `README.md`, `docs/architecture.md`; 더 깊게는 `docs/module-contracts.md` |
| 새 기능이나 수정 작업은 어떤 순서로 하나 | `AGENTS.md`, `docs/agent-workflow.md`, `docs/module-contracts.md` |
```

Keep the remaining existing rows for app startup, station list, settings, watchlist, UI, offline, demo, prod, events, startup metric, benchmark, performance, proxy, backlog, CI, and verification.

- [ ] **Step 4: Run router-specific checks**

Run:

```bash
rg -n "현재 계약 문서|학습 문서|이력과 근거 문서|에이전트 Fast Path|신규 개발자 Fast Path|docs/superpowers" docs/project-reading-guide.md
git diff --check -- docs/project-reading-guide.md
```

Expected: `rg` prints matches for all six patterns; `git diff --check` prints no output.

- [ ] **Step 5: Commit Task 1**

Run:

```bash
git add docs/project-reading-guide.md
git commit -m "docs: clarify project reading paths"
```

Expected: commit succeeds with only `docs/project-reading-guide.md` staged.

---

### Task 2: Group The README Documentation Map

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1's router categories.
- Produces: README documentation map that distinguishes learning, current contracts, operations, and history.

- [ ] **Step 1: Re-read the README documentation area**

Run:

```bash
sed -n '150,205p' README.md
```

Expected: output includes `## 문서 지도` and `## 5분 코드 투어`.

- [ ] **Step 2: Replace the `## 문서 지도` section**

Replace from `## 문서 지도` up to but not including `## 5분 코드 투어` with this exact text:

```markdown
## 문서 지도

현재 구조와 실행 명령의 기준은 live 문서와 실제 코드입니다. `docs/superpowers/`, `docs/history/`, `docs/improvements/`는 설계와 분석 이력을 보관하지만 현재 계약을 판단할 때는 아래 live 문서와 `settings.gradle.kts`를 우선합니다.

### 시작과 학습

- [프로젝트 읽기 가이드](docs/project-reading-guide.md): 사람과 에이전트가 목적별로 무엇을 먼저 읽을지 고르는 라우터입니다.
- [개발자 온보딩 가이드](docs/onboarding/developer-onboarding-guide.md): 처음 프로젝트를 맡은 개발자를 위해 제품 목적, 기술 선택 이유, 실제 로직 흐름, 수정 위치, 검증 방법을 순서대로 설명합니다.
- [기여 가이드](CONTRIBUTING.md): 새 기여자가 처음 실행할 명령, 머지 전 검증, 커밋 메시지 기준을 설명합니다.

### 현재 계약

- [작업자 운영 계약](AGENTS.md): 모든 변경에 적용되는 짧은 운영 계약입니다.
- [작업 절차](docs/agent-workflow.md): 변경 목적별 작업 순서, 테스트 선택, 문서 갱신 기준을 설명합니다.
- [아키텍처](docs/architecture.md): 모듈 책임, 런타임 흐름, flavor 차이를 설명합니다.
- [모듈 계약](docs/module-contracts.md): 각 모듈의 소유 범위와 변경 경계를 고정합니다.
- [상태 모델](docs/state-model.md): 영속 상태, 세션 상태, 읽기 모델, UI effect를 구분해 설명합니다.
- [오프라인 전략](docs/offline-strategy.md): 캐시 스냅샷, stale 판정, refresh 실패, watchlist fallback을 다룹니다.
- [테스트 전략](docs/test-strategy.md): 어떤 층을 어떤 테스트로 검증하는지 설명합니다.
- [검증 매트릭스](docs/verification-matrix.md): 실제로 어떤 Gradle 명령을 돌리면 되는지 정리합니다.
- [보안 trade-off](docs/security-trade-offs.md): API key, cleartext, backup, certificate pinning, proxy 승격 조건을 설명합니다.
- [디자인 컨텍스트](.impeccable.md): yellow/black/white 정보 위계, UI 유지 기준을 설명합니다.

### 운영, 릴리스, 성능

- [배포 절차](docs/deployment.md): 릴리스 준비, GitHub PR/tag 흐름, Android release 산출물과 공개 배포 전 보안 gate를 설명합니다.
- [성능](docs/performance.md): hero macrobenchmark 정의, 실기기 측정값, baseline profile 경로와 제약을 정리합니다.
- [Backend proxy ADR](docs/adr/2026-05-18-backend-proxy-escalation.md): Opinet API key를 backend proxy로 승격해야 하는 조건을 기록합니다.
- [CHANGELOG](CHANGELOG.md): 버전별 주요 변경 사항을 요약합니다.
- [릴리즈 노트](docs/release-notes/): 릴리스별 사용자 영향, 개발자 영향, 검증 결과를 보관합니다.

### 이력과 근거

- [심층 분석 리포트](docs/history/deep-analysis-report.md): 완료된 필수 수정과 조건부 승격 항목을 요약합니다.
- [개선 분석](docs/history/improvement-analysis.md): 완료된 backlog 항목과 남은 개선 후보의 기준을 보관합니다.
- `docs/superpowers/specs/`, `docs/superpowers/plans/`: 완료되었거나 진행했던 설계/구현 계획의 이력을 보관합니다.
- `docs/improvements/`: 특정 개선 패스의 설계와 구현 기록을 보관합니다.
```

- [ ] **Step 3: Run README checks**

Run:

```bash
rg -n "시작과 학습|현재 계약|운영, 릴리스, 성능|이력과 근거|live 문서와 실제 코드" README.md
git diff --check -- README.md
```

Expected: `rg` prints matches for all five patterns; `git diff --check` prints no output.

- [ ] **Step 4: Commit Task 2**

Run:

```bash
git add README.md
git commit -m "docs: group readme documentation map"
```

Expected: commit succeeds with only `README.md` staged.

---

### Task 3: Clarify The Onboarding Guide Authority Boundary

**Files:**
- Modify: `docs/onboarding/developer-onboarding-guide.md`

**Interfaces:**
- Consumes: Task 1's live contract and learning-doc categories.
- Produces: Onboarding guide opening that explains how to use it without overriding live docs.

- [ ] **Step 1: Re-read the onboarding opening**

Run:

```bash
sed -n '1,90p' docs/onboarding/developer-onboarding-guide.md
```

Expected: output starts with `# GasStation 개발자 온보딩 가이드`.

- [ ] **Step 2: Replace the first three paragraphs after the title**

Replace the paragraphs immediately after `# GasStation 개발자 온보딩 가이드` and before `## 1. 이 프로젝트를 한 문장으로 이해하기` with this exact text:

```markdown
이 문서는 GasStation을 처음 맡는 Android 개발자가 제품 목적, 프로젝트 구조, 기술 선택 이유, 실제 로직 흐름, 수정 위치, 검증 방법을 순서대로 이해하도록 돕는 학습용 핸드북입니다.

이 문서는 기존 단일 출처를 대체하지 않습니다. 현재 계약 판단은 `docs/project-reading-guide.md`가 안내하는 live 문서와 실제 코드를 우선합니다. 세부 계약은 `docs/module-contracts.md`, 구조와 런타임 흐름은 `docs/architecture.md`, 상태는 `docs/state-model.md`, 오프라인 정책은 `docs/offline-strategy.md`, 테스트와 명령은 `docs/test-strategy.md`와 `docs/verification-matrix.md`를 우선합니다.

처음 읽을 때는 이 문서를 위에서 아래로 따라가도 됩니다. 실제 변경을 시작할 때는 아래 live 문서 표로 돌아가 현재 계약과 검증 범위를 다시 확인합니다.

| 상황 | 먼저 확인할 live 문서 |
| --- | --- |
| 작업 원칙과 금지선 확인 | `AGENTS.md` |
| 무엇을 읽을지 고르기 | `docs/project-reading-guide.md` |
| 작업 순서와 체크리스트 확인 | `docs/agent-workflow.md` |
| 모듈 위치 판단 | `docs/module-contracts.md` |
| 구조와 런타임 흐름 판단 | `docs/architecture.md` |
| 상태 ownership 판단 | `docs/state-model.md` |
| cache/stale/failure/watchlist fallback 판단 | `docs/offline-strategy.md` |
| 테스트 의미와 실행 명령 판단 | `docs/test-strategy.md`, `docs/verification-matrix.md` |
```

- [ ] **Step 3: Add a cross-reference before the first-change routes**

Find the paragraph immediately before `## 17. 처음 맡은 개발자의 3일 온보딩 루트`. Insert this exact paragraph before that heading:

```markdown
여기부터는 학습을 실제 변경으로 연결하는 구간입니다. 코드 수정 전에는 `docs/project-reading-guide.md`의 목적별 경로와 `docs/agent-workflow.md`의 절차를 다시 확인하고, 변경하려는 계층의 테스트를 먼저 읽습니다.
```

- [ ] **Step 4: Run onboarding checks**

Run:

```bash
rg -n "학습용 핸드북|현재 계약 판단|먼저 확인할 live 문서|실제 변경으로 연결하는 구간" docs/onboarding/developer-onboarding-guide.md
git diff --check -- docs/onboarding/developer-onboarding-guide.md
```

Expected: `rg` prints matches for all four patterns; `git diff --check` prints no output.

- [ ] **Step 5: Commit Task 3**

Run:

```bash
git add docs/onboarding/developer-onboarding-guide.md
git commit -m "docs: clarify onboarding guide scope"
```

Expected: commit succeeds with only `docs/onboarding/developer-onboarding-guide.md` staged.

---

### Task 4: Make Agent Workflow Documentation Updates Actionable

**Files:**
- Modify: `docs/agent-workflow.md`

**Interfaces:**
- Consumes: Task 1 live-vs-history routing and approved spec.
- Produces: A workflow that explicitly prioritizes live docs and maps change types to document owners.

- [ ] **Step 1: Re-read workflow pre-change and documentation sections**

Run:

```bash
sed -n '1,235p' docs/agent-workflow.md
```

Expected: output includes `## Before Any Change` and `## Documentation Updates`.

- [ ] **Step 2: Add live-doc priority to `## Before Any Change`**

In `## Before Any Change`, after the bullet `- 실제 활성 모듈은 \`settings.gradle.kts\` 기준으로 판단한다.`, add these bullets:

```markdown
- 현재 구조와 동작 판단은 live 문서와 실제 코드를 우선한다.
- `docs/superpowers/`, `docs/history/`, `docs/improvements/`는 설계/분석 이력이다. 사용자가 이력 분석을 요청했거나 결정 배경이 필요할 때 근거로 보되, 현재 계약으로 바로 사용하지 않는다.
```

- [ ] **Step 3: Replace the `## Documentation Updates` section**

Replace from `## Documentation Updates` up to but not including `## Final Review Checklist` with this exact text:

```markdown
## Documentation Updates

문서 업데이트 기준은 "설명이 현재 코드와 사용자가 겪는 흐름을 바꾸는가"입니다. 일회성 설계와 구현 계획은 `docs/superpowers/specs/`와 `docs/superpowers/plans/`에 남기지만, 현재 계약이 바뀌면 아래 live 문서도 함께 확인합니다.

| 변경 유형 | 확인하거나 갱신할 문서 |
| --- | --- |
| 모듈 책임, 의존 방향, 새 위치 판단 | `docs/module-contracts.md`, `docs/architecture.md` |
| 구조, 런타임 흐름, 데이터 흐름 | `docs/architecture.md`, 필요 시 `docs/project-reading-guide.md` |
| 상태 원천, lifecycle, UI effect 의미 | `docs/state-model.md` |
| 캐시, stale, refresh 실패, watchlist fallback | `docs/offline-strategy.md` |
| UI 정보 위계, 디자인 토큰, 공통 primitive | `README.md`, `.impeccable.md`, `docs/architecture.md` |
| 테스트 의미, 테스트 선택 기준 | `docs/test-strategy.md` |
| 실제 Gradle 명령, CI 범위, 검증 깊이 | `docs/verification-matrix.md`, `.github/workflows/android.yml` |
| 릴리스, 배포, 버전, 공개 배포 전 gate | `docs/deployment.md`, `CHANGELOG.md`, `docs/release-notes/` |
| 성능 측정, benchmark journey, baseline profile | `docs/performance.md`, `docs/verification-matrix.md`, `README.md`의 Performance Snapshot |
| 보안 결정, secret/key/proxy/backup trade-off | `docs/security-trade-offs.md`, `docs/adr/` |
| 새 학습 경로, 온보딩 흐름, 문서 라우팅 | `docs/project-reading-guide.md`, `docs/onboarding/developer-onboarding-guide.md`, `README.md` 문서 지도 |

문서만 바꿨더라도 현재 계약을 설명하는 문장이 바뀌면 실제 파일 경로, Gradle task, 모듈 include가 여전히 맞는지 확인합니다. 과거 이력 문서만 바꿨다면 수정한 파일을 명시해 `git diff --check -- <changed files>`를 우선 실행합니다.

AGENTS.md에는 모든 작업자가 항상 알아야 하는 원칙만 추가합니다. 특정 변경 유형에서만 필요한 긴 설명은 이 문서나 전문 문서로 보냅니다.
```

- [ ] **Step 4: Refine the final checklist**

In `## Final Review Checklist`, add this bullet after `- 새 코드나 문서가 현재 활성 모듈 기준과 맞는가?`:

```markdown
- 문서 설명이 현재 코드, `settings.gradle.kts`, live 문서 기준과 충돌하지 않는가?
```

- [ ] **Step 5: Run workflow checks**

Run:

```bash
rg -n "현재 구조와 동작 판단|설계/분석 이력|변경 유형|확인하거나 갱신할 문서|settings.gradle.kts" docs/agent-workflow.md
git diff --check -- docs/agent-workflow.md
```

Expected: `rg` prints matches for all five patterns; `git diff --check` prints no output.

- [ ] **Step 6: Commit Task 4**

Run:

```bash
git add docs/agent-workflow.md
git commit -m "docs: map workflow changes to live docs"
```

Expected: commit succeeds with only `docs/agent-workflow.md` staged.

---

### Task 5: Clarify Documentation Verification Cases

**Files:**
- Modify: `docs/verification-matrix.md`

**Interfaces:**
- Consumes: Task 1 through Task 4's live-vs-history vocabulary.
- Produces: Clear command selection for historical docs, live docs, README, release, and performance documentation changes.

- [ ] **Step 1: Re-read the current document verification section**

Run:

```bash
sed -n '1,70p' docs/verification-matrix.md
```

Expected: output includes `## 문서/계약 설명 갱신 확인`.

- [ ] **Step 2: Replace the `## 문서/계약 설명 갱신 확인` section**

Replace from `## 문서/계약 설명 갱신 확인` up to but not including `## 빠른 로컬 확인` with this exact text:

```markdown
## 문서/계약 설명 갱신 확인

문서 변경은 세 가지로 나눠 확인합니다.

### 1. 이력/근거 문서만 변경

`docs/superpowers/`, `docs/history/`, `docs/improvements/`, `docs/compose-metrics/`처럼 현재 계약이 아닌 이력이나 근거 문서만 바꿨다면 수정한 파일만 diff check합니다.

```bash
git diff --check -- <changed files>
```

이 경우 Gradle 테스트는 기본 필수가 아닙니다. 다만 문서가 현재 동작, 현재 모듈 경계, 현재 명령을 새로 주장한다면 아래 live 문서 변경 기준으로 올려 봅니다.

### 2. live 계약 문서 변경

코드를 바꾸지 않고 architecture, state, offline, module contract, workflow, test strategy, verification matrix 같은 live 문서를 갱신했을 때 최소 확인입니다.

```bash
git diff --check -- README.md AGENTS.md .impeccable.md CHANGELOG.md CONTRIBUTING.md docs/agent-workflow.md docs/project-reading-guide.md docs/architecture.md docs/state-model.md docs/offline-strategy.md docs/test-strategy.md docs/verification-matrix.md docs/module-contracts.md docs/security-trade-offs.md docs/performance.md docs/deployment.md docs/adr/*.md docs/release-notes/*.md
```

문서가 파일 경로, Gradle task, 활성 모듈, CI job을 언급한다면 실제 표면도 확인합니다.

```bash
sed -n '1,220p' settings.gradle.kts
find docs -maxdepth 3 -type f | sort
```

문서 갱신이 이미 구현된 key handling, cleartext, backup, cache/event/state, location, brand label 계약을 설명한다면 아래 관련 테스트도 선택합니다.

```bash
./gradlew \
  :core:model:test \
  :core:network:test \
  :domain:location:test \
  :domain:station:test \
  :core:observability:test \
  :core:database:testDebugUnitTest \
  :core:location:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :data:station:testDebugUnitTest \
  :feature:station-list:testDebugUnitTest \
  :feature:watchlist:testDebugUnitTest \
  :app:testDemoDebugUnitTest \
  :app:testProdDebugUnitTest
```

이 조합은 `StationEvent` 계약, retry/pruning 정책, station-list 상태 분리, watchlist event, 주소 lookup, 브랜드 label, cleartext resource, Android backup 비활성화, prod secret fail-fast 의미를 다시 확인합니다.

### 3. README, demo story, 릴리스, 성능 문서 변경

README, release notes, deployment, performance 문서가 현재 실행 결과나 측정값을 말한다면 diff check에 더해 해당 명령을 실행하거나 기존 증거를 명시합니다.

```bash
git diff --check -- README.md CHANGELOG.md CONTRIBUTING.md docs/deployment.md docs/performance.md docs/verification-matrix.md docs/release-notes/*.md
```

대표 기준:

- README의 빠른 검증 명령을 바꿨다면 같은 명령이나 더 좁은 관련 명령을 실행합니다.
- demo story나 screenshot 전제를 바꿨다면 `:app:assembleDemoDebug` 또는 관련 UI test/benchmark 전제를 확인합니다.
- 릴리스/배포 절차를 바꿨다면 `docs/deployment.md`의 절차와 이 문서의 릴리스/배포 확인 명령을 함께 봅니다.
- 성능 수치나 benchmark journey를 바꿨다면 `docs/performance.md`와 이 문서의 Hero Benchmark Evidence 기준을 함께 봅니다.
```

- [ ] **Step 3: Run verification-matrix checks**

Run:

```bash
rg -n "이력/근거 문서만 변경|live 계약 문서 변경|README, demo story, 릴리스, 성능 문서 변경|settings.gradle.kts|Hero Benchmark Evidence" docs/verification-matrix.md
git diff --check -- docs/verification-matrix.md
```

Expected: `rg` prints matches for all five patterns; `git diff --check` prints no output.

- [ ] **Step 4: Commit Task 5**

Run:

```bash
git add docs/verification-matrix.md
git commit -m "docs: clarify documentation verification paths"
```

Expected: commit succeeds with only `docs/verification-matrix.md` staged.

---

### Task 6: Final Consistency Review

**Files:**
- Modify: `README.md`
- Modify: `docs/project-reading-guide.md`
- Modify: `docs/onboarding/developer-onboarding-guide.md`
- Modify: `docs/agent-workflow.md`
- Modify: `docs/verification-matrix.md`

**Interfaces:**
- Consumes: All previous task commits.
- Produces: Final checked documentation set and one integration commit only if fixes are needed.

- [ ] **Step 1: Verify the expected files changed across the branch**

Run:

```bash
git diff --name-only HEAD~5..HEAD
```

Expected output includes these files and no app source files:

```text
README.md
docs/agent-workflow.md
docs/onboarding/developer-onboarding-guide.md
docs/project-reading-guide.md
docs/verification-matrix.md
```

- [ ] **Step 2: Check for incomplete markers without matching the command text itself**

Run:

```bash
rg -n "T[B]D|T[O]DO|F[I]XME|i[m]plement [a-z]+|f[i]ll in|h[a]ndle edge cases|s[i]milar to Task" README.md docs/project-reading-guide.md docs/onboarding/developer-onboarding-guide.md docs/agent-workflow.md docs/verification-matrix.md
```

Expected: no matches.

- [ ] **Step 3: Run final documentation diff check**

Run:

```bash
git diff --check -- README.md docs/project-reading-guide.md docs/onboarding/developer-onboarding-guide.md docs/agent-workflow.md docs/verification-matrix.md
```

Expected: no output.

- [ ] **Step 4: Confirm live-vs-history language appears in all routing documents**

Run:

```bash
rg -n "현재 계약|live 문서|docs/superpowers|docs/history|docs/improvements" README.md docs/project-reading-guide.md docs/onboarding/developer-onboarding-guide.md docs/agent-workflow.md docs/verification-matrix.md
```

Expected: matches appear in all five files.

- [ ] **Step 5: Confirm the working tree is clean after the task commits**

Run:

```bash
git status --short
```

Expected: no output. If there are edits from consistency fixes, commit only those doc fixes:

```bash
git add README.md docs/project-reading-guide.md docs/onboarding/developer-onboarding-guide.md docs/agent-workflow.md docs/verification-matrix.md
git commit -m "docs: align documentation navigation wording"
```

Expected: commit succeeds only if Step 5 found final consistency edits.

## Self-Review

**Spec coverage:** This plan covers all approved spec outputs: `docs/project-reading-guide.md` routing in Task 1, README doc map in Task 2, onboarding scope in Task 3, workflow live-doc mapping in Task 4, verification case split in Task 5, and consistency review in Task 6. It respects the non-goals by avoiding app code, Gradle changes, new scripts, `AGENTS.md`, and historical document movement.

**Incomplete marker scan:** The plan avoids open-ended implementation instructions. The final scan command uses character classes for common incomplete markers so the command can exist in this plan without causing a self-match.

**Path consistency:** All referenced files exist in the current repository and are within the approved implementation boundary. The plan uses `settings.gradle.kts` as the active module source of truth and keeps `docs/superpowers/`, `docs/history/`, and `docs/improvements/` as historical or evidence context.
