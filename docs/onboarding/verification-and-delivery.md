# GasStation 검증과 전달

이 문서는 변경에 맞는 검증을 선택하고 결과, 미검증 범위와 local/remote 상태를 분리해 전달하는 방법을 설명합니다. 명령의 단일 소유자는 [검증 매트릭스](../verification-matrix.md)와 각 specialist runbook입니다.

## 검증 범위 선택

검증은 많을수록 좋은 것이 아니라 변경된 계약을 보호해야 합니다.

1. [검증 매트릭스](../verification-matrix.md)에서 변경 유형과 위험에 맞는 명령 owner를 선택합니다.
2. domain/data/core의 작은 계약 테스트에서 시작해 feature integration, app flavor 조립으로 확장합니다.
3. 문서 변경은 live 계약인지 historical evidence인지 구분합니다.
4. device, release, build-input과 performance 증거는 각각의 specialist runbook을 따릅니다.
5. `demo`와 `prod` 중 확인하지 않은 경로를 결과에서 명시합니다.

선택 기준과 테스트 의미는 [테스트 전략](../test-strategy.md), API 24/28/36 runtime은 [기기 검증](../runbooks/device-verification.md), release는 [배포](../deployment.md), physical-device benchmark는 [성능](../performance.md), governed dependency/reproducibility는 [Build Input Provenance](../runbooks/build-input-provenance.md)가 소유합니다.

## 증거 기록

handoff에는 다음을 남깁니다.

- 기준 HEAD와 최종 HEAD.
- 변경 파일과 각 파일이 담당하는 의미.
- 실제 실행한 명령과 exit/result 요약.
- 실패 후 수정했다면 실패 원인과 다시 실행한 범위.
- 실행하지 않은 device, hosted, network, release 또는 remote 범위.
- tracked/untracked/ignored 상태와 remote mutation 여부.

과거 PASS, 다른 HEAD의 결과, emulator smoke나 문서상의 계획을 현재 실행 증거로 승격하지 않습니다. physical-device 성능 수치는 실제 기기 JSON/trace 근거 없이 갱신하지 않고, `prod` 실서버·Opinet 호출도 별도 권한 없이 smoke 대상으로 사용하지 않습니다.

## Commit과 handoff

commit 전에는 관련 diff를 읽고 사용자 변경이나 unrelated file이 섞이지 않았는지 확인합니다. 저장소의 conventional subject를 사용하고 목적별로 되돌릴 수 있는 단위로 stage합니다. push, PR, tag, release, publish와 deploy는 요청 범위에 포함된 경우에만 수행합니다.

handoff 순서는 다음이 명확합니다.

1. 변경 결과와 commit SHA.
2. 실행한 검증과 결과.
3. 미검증·`NOT RUN` 범위.
4. local branch/worktree와 remote 상태.
5. 다음 검토자가 재현할 진입점.

## 문서 영향 전달

현재 사용자 흐름, 모듈 책임, 상태·cache 정책, 검증 명령이나 release 경계가 바뀌면 관련 live owner 문서를 같은 변경에서 확인합니다. PR에는 문서 영향 yes/no, 영향을 받은 catalog owner, 갱신한 경로, 현재 문서를 바꾸지 않았다면 그 이유를 남깁니다.

이력 문서는 당시 판단과 측정의 body를 보존합니다. 새 README 같은 navigation surface를 추가해도 기존 이력 파일명에서 승인·완료 상태를 추론하거나 오래된 claim을 현재 값에 맞춰 다시 쓰지 않습니다.

## 완료 체크

- 관련 source와 test diff를 직접 읽었다.
- 현재 활성 모듈과 owner 경계를 확인했다.
- 변경 유형에 맞는 검증을 새 결과로 실행했다.
- 문서 영향과 catalog owner를 확인했다.
- 미실행 범위를 PASS처럼 표현하지 않았다.
- local/remote 상태와 다음 검토 진입점을 남겼다.

작업 중 소유자가 불명확하면 [변경 플레이북](change-playbook.md)으로 돌아갑니다. 전체 현재 문서는 [문서 허브](../README.md)에서 찾습니다.
