# 아키텍처 둘러보기

제품을 놓치지 않고 레이어와 흐름을 읽는다. 모듈 그래프는 [아키텍처](../architecture.md), 어디에 둘지는 [모듈 계약](../module-contracts.md)이다.

## 제품

가까운 주유소를 가격, 거리, 브랜드, 유종, 관심, 외부 지도로 비교한다. 목록을 많이 보여주는 것이 목표가 아니다. 지금 어디로 갈지 빨리 정하게 한다.

- 가격이 첫 읽기 대상이다.
- 거리, 역명, 브랜드, 유종, freshness, 관심은 그 결정을 돕는다.
- 네트워크가 실패해도 마지막 성공 스냅샷을 버리지 않는다.
- `demo`와 `prod`는 둘 다 정식 경로다.
- yellow, black, white와 가격 우선을 지킨다.

흐름은 `위치 → 주변 비교 → 조건 조정 → 관심 저장 → 관심 비교 → 외부 지도`다.

## 레이어

| 레이어 | 맡는 것 | 두지 않는 것 |
| --- | --- | --- |
| `app` | 조립, startup, navigation, flavor, 외부 앱 | 캐시·정렬 정책, 화면 상태 |
| `feature:*` | Route, ViewModel, UI state/command, 화면 | Room, Retrofit, DataStore, 위치 구현 |
| `domain:*` | 계약, use case, 순수 모델 | Android, Compose, storage DTO |
| `data:*` | 저장소 구현, remote/DB/cache 조합 | Compose 상태, 화면 문구 |
| `core:*` | 값 객체, 공통 UI, 플랫폼 인프라 | feature 전용 정책 |
| `tools:demo-seed` | demo seed CLI | 앱 런타임 우회 |
| `benchmark` | macrobenchmark, baseline profile | 앱 기능 |

활성 모듈과 직접 의존은 외우지 않는다. `settings.gradle.kts`와 [모듈 그래프](../architecture.md#모듈-그래프)를 본다.

## 시작과 flavor

`MainActivity`가 Compose root를 연다. `demo`는 seed를 DB에 넣고 설정을 기본값으로 돌린다. 권한을 허용한 뒤에만 고정 좌표가 온다. 권한 거부를 기존 위치나 캐시로 우회하지 않는다.

`prod`는 실제 위치와 네트워크를 쓰고, 키가 없으면 시작에서 실패한다. direct/proxy 선택은 `app`이 하고 fetcher가 고르지 않는다. 클라이언트 키는 완전한 비밀이 아니다. 승격은 [보안](../security-trade-offs.md)과 [proxy ADR](../adr/2026-05-18-backend-proxy-escalation.md)을 본다.

## Nearby

ViewModel 하나에 모든 책임을 모으지 않는다.

- `LocationStateMachine` — 권한, GPS, 위치, 주소. 늦은 결과는 버린다.
- `StationSearchOrchestrator` — 현재 query 관찰, 같은 query 재시작.
- `RefreshCoordinator` — 최신 query로 새로고침.
- `StationListCommandQueue` — snackbar, 지도 열기 같은 FIFO.
- `StationListStateAssembler` — 입력을 최종 UI로 투영. I/O 없음.
- `StationListViewModel` — action 연결, 수집, 게시.

설정 첫 값, 권한, GPS, 좌표가 준비돼야 `StationQuery`가 생긴다. 좌표가 있는 상태에서 반경·유종·브랜드·정렬이 바뀌면 query를 바꾸고 새로고침한다.

캐시 있음은 `hasCachedSnapshot`이다. 성공한 빈 결과와 캐시 없음은 다르다. 자세한 상태는 [상태 모델](../state-model.md), 캐시 정책은 [오프라인 전략](../offline-strategy.md)이다.

## Settings, 관심, UI

설정 쓰기는 `domain:settings` update use case만 탄다. 상세 화면은 저장이 성공한 뒤에만 돌아간다.

관심은 현재 목록의 복제가 아니다. 저장 항목을 선택 유종으로 비교한다. 최신 캐시가 없어도 행을 지우지 않고 `선택 유종 가격 없음`을 보여준다. station ID별 관심 변경은 마지막 탭만 반영한다.

공통 색은 canvas `#FFFCF2`, chrome `#222222`, signal `#FFDC00`이다. 화면 문구와 분기는 feature가 맡는다.

질문별 파일은 [읽기 가이드](../project-reading-guide.md), 변경 순서는 [변경 플레이북](change-playbook.md), 전체 구조는 [아키텍처](../architecture.md)다. 지도는 [문서 허브](../README.md)다.
