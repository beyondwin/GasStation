# README Preview Layout Design

**Date:** 2026-04-19

## Goal

`README.md` 상단에서 앱의 핵심 기능이 텍스트 설명보다 먼저 보이도록, `prod` 기준 GIF 1개와 핵심 스크린샷 2개를 한 줄에 배치한 쇼케이스 구간으로 재정렬한다.

이번 작업은 README 전체를 다시 쓰는 것이 아니라, 저장소 방문자가 첫 화면에서 아래 두 가지를 즉시 이해하게 만드는 데 집중한다.

- 이 프로젝트가 실제로 어떤 앱 흐름을 다루는지
- 가장 중요한 사용자 가치가 어떤 화면으로 드러나는지

## Audience and Success Bar

1차 독자는 저장소에 처음 들어온 검토자다.

성공 기준은 아래와 같다.

- 제목과 한 줄 소개를 읽은 직후, 별도 스크롤 없이 핵심 기능 데모를 볼 수 있다.
- 상단 미리보기만 봐도 "위치 기반 조회"와 "관심 주유소 비교" 흐름이 드러난다.
- 기존 README의 구조 요약, 실행 모드, 문서 링크는 그대로 유지되어 레퍼런스 프로젝트 설명력은 떨어지지 않는다.

## Current State Summary

현재 README는 이미 `prod` 기준 GIF와 3장의 스크린샷을 포함하고 있다.

- `docs/readme-assets/prod-flow.gif`
- `docs/readme-assets/prod-station-list.png`
- `docs/readme-assets/prod-watchlist.png`
- `docs/readme-assets/prod-settings.png`

하지만 현재 구성은 아래 한계가 있다.

- GIF가 한 줄을 단독으로 차지하고, 스크린샷은 다음 줄에 별도 묶음으로 보여 시선이 분산된다.
- 3장 구성은 상단 랜딩 구간 기준으로는 다소 많아 첫 인상 밀도가 높다.
- `settings` 화면은 앱 존재를 설명하는 보조 정보라서, 핵심 기능을 즉시 보여주는 목적에는 우선순위가 낮다.

## Design Principles

- README 상단은 설명보다 시연이 먼저다.
- `prod` 자산만 사용해 현재 런타임 경로를 일관되게 보여준다.
- 핵심 기능을 설명하는 데 직접 기여하지 않는 화면은 상단 쇼케이스에서 제외한다.
- 기존 README의 정보 구조는 가능한 한 유지하고, 랜딩 구간만 더 빠르게 읽히게 만든다.

## Preview Layout

상단 레이아웃은 아래 순서로 유지한다.

1. 프로젝트 제목
2. 한 줄 소개
3. `미리보기` 섹션
4. 이후 `빠른 포인트`, `이 프로젝트가 보여주는 것`, 아키텍처 이하 기존 섹션

`미리보기` 섹션은 아래 구성을 사용한다.

- 첫 번째 카드: `prod-flow.gif`
- 두 번째 카드: `prod-station-list.png`
- 세 번째 카드: `prod-watchlist.png`

세 자산은 한 줄에 나란히 배치한다. 각 자산 아래에 별도 캡션은 두지 않는다.

## Asset Selection Rationale

### `prod-flow.gif`

앱의 전체 상호작용 흐름을 가장 빠르게 전달한다. README 방문자가 가장 먼저 봐야 할 자산이다.

### `prod-station-list.png`

위치 기반 주유소 조회라는 앱의 진입 가치를 정적인 한 장으로 보완한다.

### `prod-watchlist.png`

단순 조회 앱이 아니라 관심 주유소 저장과 비교 흐름까지 포함한다는 점을 보여준다.

### Excluding `prod-settings.png`

설정 화면은 앱 완성도를 보강하는 정보이지만, 상단에서 핵심 기능을 즉시 전달하는 목적에는 우선순위가 낮다. 설정은 README 하단 설명 또는 실제 앱 탐색에서 충분히 확인할 수 있다.

## Markdown Strategy

GitHub README에서 GIF와 PNG를 같은 줄에 안정적으로 배치하려면 HTML `img` 태그를 사용한다.

구현 원칙:

- 세 자산 모두 동일한 폭 비율로 배치한다.
- 가운데 정렬을 유지한다.
- alt 텍스트는 각 자산의 의미를 드러내도록 남긴다.
- 기존처럼 GIF와 스크린샷을 별도 블록으로 분리하지 않는다.

## Non-Goals

이번 작업에서는 아래를 하지 않는다.

- README 전체 문안 재작성
- 새로운 스크린샷/GIF 생성
- `demo` 자산 추가
- 문서 구조나 실행 모드 설명 변경

## Validation

완료 후 아래를 확인한다.

- GitHub Markdown 렌더링 기준으로 세 자산이 상단에서 한 줄 묶음으로 읽힌다.
- 링크나 이후 섹션 순서가 깨지지 않는다.
- 기존 자산 경로만 사용하므로 새 파일 추가 없이 README만 수정된다.
