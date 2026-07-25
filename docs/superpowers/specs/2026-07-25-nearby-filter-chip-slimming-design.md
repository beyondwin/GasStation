# Nearby Filter Chip Slimming

## Goal

주변 화면 상단의 네 필터가 현재의 검정·노랑 Urban Signal 정체성과 빠른 비교 흐름은 유지하면서, 연속된 큰 pill 네 개처럼 무겁고 뚱뚱해 보이지 않도록 시각 높이와 모서리를 정리한다.

이 문서는 `2026-07-17-nearby-ui-density-and-filter-clarity-design.md`의 필터 chip 시각 표현만 조정한다. 필터 순서, 메뉴 동작, 저장 및 refresh 흐름, 접근성 의미는 바꾸지 않는다.

## Approved Direction

승인된 방향은 **검정 면을 유지하는 슬림형 chip**이다.

- 바깥 interaction 영역은 최소 48dp를 유지한다.
- 실제로 보이는 검정 surface는 기본 글꼴에서 최소 40dp 높이를 사용한다.
- 완전한 capsule 대신 14dp rounded rectangle을 사용한다.
- 좌우 내부 여백은 현재 12dp를 유지한다.
- chip 사이 8dp 간격과 노란 label·chevron은 유지한다.
- 열린 menu의 2dp 노란 focus border는 유지한다.

검정 면을 없애는 outline형은 화면의 무게를 줄이는 효과가 더 크지만, 현재 Station List의 검정 summary strip 및 노란 signal과 연결되는 Urban Signal 정체성을 약화하므로 선택하지 않는다.

## Layout And Interaction

`FilterActionChip`은 시각 surface와 interaction target을 분리한다.

1. 바깥 container가 click, enabled 상태, semantics, test tag와 최소 48dp target을 소유한다.
2. 안쪽 검정 surface가 기본 40dp visual height, 14dp corner, label과 선택 chevron을 소유한다.
3. 글꼴 배율로 내용에 40dp보다 큰 높이가 필요하면 surface와 바깥 target이 함께 자연스럽게 확장한다. 텍스트를 축소하거나 자르지 않는다.

정렬 chip과 반경·유종·브랜드 menu chip은 같은 visual height와 corner를 사용한다. menu chip만 기존 chevron과 expand/collapse description을 유지한다.

360dp 기본 화면에서는 네 chip이 모두 온전히 보이고 마지막 브랜드 chip 뒤에 최소 8dp의 아이보리 여백이 남아야 한다. 320dp 또는 큰 글꼴처럼 네 chip이 한 줄에 들어가지 않는 경우에는 기존 horizontal scroll을 유지한다.

## Accessibility

- 각 chip의 실제 터치 영역은 최소 48dp다.
- 기존 `station-list-filter-*` test tag와 click semantics를 바깥 interaction container에 유지한다.
- 반경·유종·브랜드 chevron의 expand/collapse content description을 유지한다.
- disabled 및 pending preference write 상태에서는 기존과 동일하게 interaction을 차단한다.
- 200% font scale에서 label이나 chevron을 clip하지 않는다.

## Scope

주요 구현 소유자는 `feature:station-list`의 `StationListFilterRail.kt`다. 새 디자인시스템 primitive나 dependency는 추가하지 않는다.

다음은 변경하지 않는다.

- filter option, 순서, label과 brand mapping
- anchored menu의 크기, 위치, row 디자인과 dismiss 동작
- `StationListAction`, settings use case, active-query refresh
- summary strip, station row, bottom navigation
- demo/prod 데이터 및 cache·location·watchlist·external map 동작

## Testing

- 기존 Compose test로 모든 chip의 최소 48dp touch target을 유지한다.
- 기본 360dp Roborazzi populated snapshot에서 40dp visual surface와 14dp corner를 검증한다.
- 마지막 브랜드 chip 뒤 8dp canvas clearance pixel regression을 유지한다.
- light, dark, empty, loading, stale 및 세 menu-open snapshot을 갱신하고 직접 확인한다.
- 320dp menu containment와 200% font-scale filter/menu 계약을 유지한다.
- 최종 검증은 `scripts/agent/verify.sh auto`와 `git diff --check`를 사용한다.

## Acceptance Criteria

- 기본 360dp 주변 화면에서 필터 row가 이전보다 가볍고 낮게 보인다.
- 네 chip의 검정 visual surface는 기본 글꼴에서 최소 40dp이며 모서리는 14dp다.
- 모든 chip은 최소 48dp 터치 영역을 유지한다.
- `전체` chip의 오른쪽 곡면과 최소 8dp 끝 여백이 온전히 보인다.
- filter 선택, menu dismiss, pending write, 접근성, demo/prod 동작에 회귀가 없다.
- 200% font scale에서는 surface가 필요한 만큼 커지며 텍스트와 chevron을 자르지 않는다.
