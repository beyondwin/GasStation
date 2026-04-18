# 테스트 전략

GasStation은 현재 지원하는 `demo`/`prod` 경로를 기준으로 테스트를 설계한다. 과거 버전 호환이나 레거시 직렬화 포맷 보존은 테스트 목표가 아니다.

## 빠른 요약

| 질문 | 답 |
| --- | --- |
| 무엇을 가장 세게 검증하나 | 저장소 조합, ViewModel 상태 전이, demo startup 경로 |
| 무엇을 의도적으로 제외하나 | 과거 버전 호환, 폐기된 provider, 현재 제품 경로 밖 분기 |
| 왜 이런 전략을 택했나 | 현재 제품 가치와 공식 지원 경로에 직접 연결되는 경로에 검증을 집중하기 위해 |

## 계층별 목적

- `domain`
  값 객체 불변식, 정렬/필터/캐시 키 규칙, 저장소 계약을 검증한다.
- `data` / `core infra`
  Room DAO, 캐시 정책, serializer, 네트워크 매핑, 좌표 변환을 검증한다.
- `feature`
  ViewModel 상태 전이, effect 방출, 화면 계약을 검증한다.
- `app integration`
  startup hook, demo seed 적재, 앱 리소스와 그래프 구성을 검증한다.
- `benchmark`
  cold start와 대표 이동 흐름의 성능 기준을 확인한다.

## 제외하는 것

- 과거 앱 버전 호환을 위한 serializer regression
- 더 이상 사용하지 않는 네트워크 provider
- 현재 제품 경로에 포함되지 않는 실험적 분기

## 회귀 위험이 큰 영역

- `DefaultStationRepository`의 캐시/히스토리 조합
- `StationListViewModel`의 권한/위치/refresh 상태 전이
- demo seed 로딩과 startup hook
- 문서와 CI 검증 범위의 불일치
