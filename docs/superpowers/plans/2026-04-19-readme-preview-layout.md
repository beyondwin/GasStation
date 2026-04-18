# README Preview Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `README.md` 상단에서 `prod` 기준 GIF 1개와 핵심 스크린샷 2개를 한 줄로 보여 주도록 미리보기 구간을 압축해, 저장소 방문자가 핵심 기능을 즉시 볼 수 있게 만든다.

**Architecture:** 새로운 자산을 만들지 않고 기존 `docs/readme-assets` 파일만 재사용한다. `README.md`의 제목과 한 줄 소개 아래 `미리보기` 구간만 HTML `img` 기반 3열 레이아웃으로 정리하고, 이후 정보 구조는 그대로 유지한다.

**Tech Stack:** Markdown, GitHub README HTML image tags

---

### Task 1: Compress the README preview into a single-row showcase

**Files:**
- Modify: `README.md`
- Reference: `docs/superpowers/specs/2026-04-19-readme-preview-layout-design.md`

- [ ] **Step 1: Inspect the current README diff before editing**

Run:

```bash
git diff -- README.md
```

Expected: current local changes are shown so the edit can be layered on top without discarding unrelated work.

- [ ] **Step 2: Replace the split preview blocks with one centered row**

Update the `## 미리보기` section in `README.md` so it becomes:

```md
## 미리보기

`prod` 기준 주요 화면입니다.

<p align="center">
  <img width="32%" alt="주유주유소 prod flow" src="docs/readme-assets/prod-flow.gif">
  <img width="32%" alt="주유주유소 prod station list" src="docs/readme-assets/prod-station-list.png">
  <img width="32%" alt="주유주유소 prod watchlist" src="docs/readme-assets/prod-watchlist.png">
</p>
```

Delete the old standalone markdown image line for `prod-flow.gif` and remove the `prod-settings.png` image from the screenshot row.

- [ ] **Step 3: Verify the preview assets are still the intended ones**

Run:

```bash
rg -n "prod-flow|prod-station-list|prod-watchlist|prod-settings" README.md
```

Expected:

- `prod-flow`
- `prod-station-list`
- `prod-watchlist`

Expected absent:

- `prod-settings`

- [ ] **Step 4: Review the rendered markdown structure around the edit**

Run:

```bash
sed -n '1,40p' README.md
```

Expected: title, one-line description, `미리보기`, then a single `<p align="center">` block containing exactly three `img` tags before `## 빠른 포인트`.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/superpowers/plans/2026-04-19-readme-preview-layout.md docs/superpowers/specs/2026-04-19-readme-preview-layout-design.md
git commit -m "docs: tighten readme preview layout"
```
