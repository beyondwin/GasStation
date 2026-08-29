# 검증과 전달

변경에 맞는 검증을 고르고, 돌린 것과 안 돌린 것을 나눠 남긴다. 명령은 [검증 매트릭스](../verification-matrix.md)와 각 런북이 맞는다.

## 범위

많을수록 좋은 것이 아니다. 바뀐 계약을 보호한다.

1. [검증 매트릭스](../verification-matrix.md)에서 명령을 고른다.
2. domain/data/core의 작은 테스트부터 feature, app 조립으로 넓힌다.
3. 문서 변경은 live인지 이력인지 가른다.
4. 기기, 릴리스, 빌드 입력, 성능은 각 런북을 따른다.
5. `demo`와 `prod` 중 안 본 경로를 결과에 적는다.

테스트 의미는 [테스트 전략](../test-strategy.md), 기기는 [기기 검증](../runbooks/device-verification.md), 릴리스는 [배포](../deployment.md), 성능은 [성능](../performance.md), 빌드 입력은 [Build Input Provenance](../runbooks/build-input-provenance.md)다.

## 남길 것

- 기준 HEAD와 최종 HEAD
- 변경 파일과 각 파일이 맡은 의미
- 실행한 명령과 결과
- 실패 후 고쳤다면 원인과 다시 돌린 범위
- 안 돌린 기기, hosted, 네트워크, 릴리스
- tracked/untracked/ignored, remote를 바꿨는지

과거 PASS나 다른 HEAD 결과를 이번 증거로 쓰지 않는다. 물리 기기 성능 숫자는 JSON/trace 없이 바꾸지 않는다. `prod` 실서버 호출도 별도 권한 없이 smoke로 쓰지 않는다.

## 커밋과 전달

diff를 읽고 관련 없는 파일이 섞이지 않았는지 본다. conventional subject를 쓰고, 되돌릴 수 있는 단위로 stage한다. push, PR, tag, release는 요청에 있을 때만 한다.

전달할 때 이 순서가 분명해야 한다.

1. 결과와 commit SHA
2. 돌린 검증과 결과
3. 안 돌린 범위
4. local branch/worktree와 remote
5. 다음 사람이 재현할 시작점

사용자 흐름, 모듈 책임, 캐시, 검증 명령, 릴리스 경계가 바뀌면 같은 변경에서 live 문서도 본다. PR에는 문서 영향 yes/no와 고친 경로를 남긴다.

이력 본문은 그때의 기록으로 둔다.

작업 중 소유자가 안 보이면 [변경 플레이북](change-playbook.md)으로 돌아간다. 전체 문서는 [문서 허브](../README.md)다.
