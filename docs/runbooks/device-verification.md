# Android 기기 검증

API 24/28/36 에뮬레이터 운영의 기준이다. 테스트 의미는 [테스트 전략](../test-strategy.md), 명령 조합은 [검증 매트릭스](../verification-matrix.md)다. `demo` 앱, Room migration, API 36 Geocoder를 본다. `prod` 실통신이나 물리 기기 성능은 여기서 증명하지 않는다.

## 지원 호스트와 사전 조건

호스팅 경로는 Linux x86_64, JDK 21, Android SDK command-line tools, `platform-tools`, emulator, KVM 읽기/쓰기 권한, 충분한 디스크를 전제로 합니다. `scripts/quality/device/verify_host.sh`가 lane별 도구와 KVM, 디스크 조건을 변경 전에 검사합니다. API 24 connected lane은 `system-images;android-24;google_apis;x86_64`를 명시적으로 설치하고, API 28/36은 Gradle Managed Device가 각각 AOSP/Google 이미지를 관리합니다.

Darwin arm64, KVM이 없는 호스트, 예약 serial `emulator-5554`가 이미 사용 중인 호스트에서는 runtime lane을 실행하지 않습니다. host unit test, Android-test compile, Gradle task discovery는 구현 준비 증거일 뿐 device `PASS`가 아닙니다.

## 닫힌 lane 매트릭스

| Lane | 기기와 이미지 | 정확한 Gradle 작업 | 정규 inventory | 역할 |
| --- | --- | --- | ---: | --- |
| `api28-pr-smoke` | GMD `gasstationPixel2Api28`, Pixel 2, API 28 AOSP | `:app:gasstationPixel2Api28DemoDebugAndroidTest` + `com.gasstation.test.DevicePrSmoke` filter | app 5, skip 0 | PR report-only |
| `api24-scheduled` | connected AVD `gasstation_api24`, Pixel 2, API 24 Google APIs x86_64, `emulator-5554` | `:app:connectedDemoDebugAndroidTest`, `:core:database:connectedDebugAndroidTest` | app 10 + Room 6, skip 0 | scheduled/manual blocking diagnostic |
| `api28-scheduled` | GMD `gasstationPixel2Api28`, Pixel 2, API 28 AOSP | `:app:gasstationPixel2Api28DemoDebugAndroidTest`, `:core:database:gasstationPixel2Api28DebugAndroidTest` | app 10 + Room 6, skip 0 | scheduled/manual blocking diagnostic |
| `api36-scheduled` | GMD `gasstationPixel2Api36`, Pixel 2, API 36 Google | `:app:gasstationPixel2Api36DemoDebugAndroidTest`, `:core:database:gasstationPixel2Api36DebugAndroidTest`, `:core:location:gasstationPixel2Api36DebugAndroidTest` | app 10 + Room 6 + Geocoder 1, skip 0 | scheduled/manual blocking diagnostic |

Task 이름은 AGP 9.3 task discovery에서 확인한 값입니다. GMD group이나 shard는 만들지 않으며, 각 Gradle 작업은 software GPU, 단일 worker, `--no-parallel`, `--rerun-tasks`, configuration cache, warning failure로 한 번만 실행됩니다.

## 정규 실행 명령

깨끗한 checkout과 event SHA/run identity가 있는 지원 호스트에서만 다음 wrapper를 사용합니다.

<!-- command-owner: verification.device -->

```bash
scripts/quality/device/run_gmd_lane.sh --lane api28-pr-smoke
scripts/quality/device/run_api24_avd.sh --lane api24-scheduled
scripts/quality/device/run_gmd_lane.sh --lane api28-scheduled
scripts/quality/device/run_gmd_lane.sh --lane api36-scheduled
```

API 24 wrapper는 host preflight 뒤 SDK/AVD를 설치하고 전용 `ANDROID_AVD_HOME`을 만들며, `emulator-5554`의 boot/API/fingerprint/ABI를 확인합니다. app과 Room 작업을 각각 bounded invocation으로 실행하며 이 connected 경로의 TestWatcher는 failure PNG/diagnostic은 유지하되 GMD 전용 device receipt를 만들지 않습니다. logcat/emulator 종료와 전용 AVD 디렉터리 제거는 분리되어 provision 중 PID가 생기기 전 실패해도 검증된 task-owned 디렉터리는 제거됩니다. trap, cleanup, completion receipt 중 하나라도 실패하면 `PASS`가 아닙니다. API 28/36 wrapper는 AGP/UTP가 관리하는 각 GMD 작업의 setup과 teardown을 해당 작업 timeout 안에서 소유합니다.

정책의 `phaseSeconds`는 선언용 표가 아니라 wrapper가 직접 사용하는 실행 한도입니다. API 24의 17분 preflight는 prepare 30초 + host 30초 + SDK/AVD provision 660초 + boot/원시 adb 수집 300초로 닫히며, GMD는 prepare 30초 + host 150초 뒤 setup/boot/teardown을 각 Gradle task 한도에 포함합니다. 모든 lane의 collection, cleanup, completion, strict verify, terminal receipt도 각각 GNU `timeout`으로 제한됩니다. workflow는 checkout/Java/Gradle/KVM 8분, wrapper의 정책 합계, upload 3분 + summary 1분을 따로 제한하고 나머지 9/10/14분을 hard job timeout 전 reserve로 남깁니다.

Repository, Gradle, shell, workflow retry와 shard는 금지합니다. polling은 동일한 emulator의 boot/종료 상태만 확인합니다. timeout, cached/up-to-date task, 필수 test skip, platform error, 불완전 cleanup은 모두 실패입니다.

## receipt와 artifact

각 attempt는 `build/device-evidence/<lane>/<run-id>-<attempt>/` 아래에 시작 전 `attempt.json`, 실행 후 `completion.json`, `verification.json` 또는 실패 시 `terminal.json`을 둡니다. receipt는 checkout/event SHA, 선택 lane의 현재 canonical wrapper와 verifier SHA-256, exact task/filter, command exit/outcome, device facts, canonical JUnit identity/counter, APK와 산출물의 상대 경로/SHA-256, cleanup을 결합합니다. artifact 종류는 caller가 선언하지 않고 정책의 task별 result/APK root와 닫힌 raw 경로에서 도출합니다. 각 task는 자신의 Gradle log, JUnit, HTML, APK/test APK, raw receipt를 가져야 하며 빈 파일과 잘못된 JSON/XML/HTML/ZIP(APK)/PNG magic은 거부됩니다. 기존 result와 APK root는 실행 전에 지우며 기존 attempt root는 덮어쓰지 않습니다.

GMD의 actual API/profile/image/package/serial/ABI/fingerprint/locale/permission-controller revision은 각 instrumentation task가 Test Storage로 쓴 device-originated JSON을 AGP/UTP가 host로 pull한 뒤 `raw/gmd-task-<index>.json` 하나로 결합한 값입니다. 정책 문자열이나 Gradle 로그 substring은 actual fact가 아닙니다. task 간 fact 충돌, field 누락, 추가 receipt는 실패합니다. API 24는 `raw/adb-devices.txt`, `getprop.txt`, AVD config, permission-controller package/revision을 다시 파싱하며 online/authorized `emulator-5554` 하나와 x86_64만 허용합니다.

cleanup도 caller 문자열을 신뢰하지 않습니다. GMD baseline, task 종료 관측, cleanup은 하나의 정확한 process discovery를 공유하고 PID, executable, 관측된 AVD 이름을 함께 기록합니다. 각 Gradle 실행은 daemon을 재사용하지 않고 attempt ID owner token을 환경으로 상속하며, Linux `/proc/<pid>/environ`에서 그 exact token이 관측되고 선택 정책의 GMD AVD 이름과 일치하는 process만 signal 직전에 같은 identity/token으로 재확인해 종료합니다. 이름만 같거나 PID가 재사용된 process, pre-existing emulator launcher/qemu child, baseline 이후 시작된 다른 AVD process는 소유하지 않습니다. task별 teardown, process identity와 최종 adb target은 `raw/gmd-teardown.json`, API 24의 emu-kill/logcat exit, live PID/serial, 5554/5555 port, AVD 제거는 `raw/teardown.json`에서 `cleanupStatus`를 도출합니다. 영수증 누락, timeout, kill 실패, live owner process/serial, occupied port는 completion/PASS를 막습니다. 실제 테스트 실패로 Gradle이 nonzero여도 JUnit과 정확한 PNG/diagnostic이 모두 수집되면 `verification.json`은 구조화된 `FAIL`을 남기며, output 누락/불일치는 별도 collection failure입니다. parser가 읽는 raw JSON/text와 log는 UTF-8 decode 전에 크기 제한을 적용합니다.

원본 AGP 경로는 module별 `build/outputs/androidTest-results/{managedDevice,connected}`, `build/reports/androidTests/{managedDevice,connected}`, allowlisted APK root입니다. workflow artifact는 attempt root와 도달한 AGP root를 함께 올립니다. PR artifact 이름은 `device-api28-pr-<run-id>-<attempt>`이고 14일 보존합니다. scheduled artifact는 lane별 고유 이름으로 30일 보존합니다. upload step의 artifact ID, URL, archive digest는 job summary에 남고 member hash는 `completion.json`이 소유합니다. 파일이 없으면 upload도 실패합니다.

실패 시 먼저 `terminal.json`/`verification.json`, `raw/commands.json`, `logs/gradle-*.log`, JUnit XML, logcat/UTP receipt를 확인합니다. 앱 UI test 실패는 `failure-<attempt>-<class>-<method>-api<level>.png/.txt` 쌍이 Platform Test Storage를 통해 host output에 있어야 합니다. controlled API-28 failure transport와 복구된 성공 attempt가 모두 없으면 이 transport는 `PASS`가 아닙니다.

## 판정, 승격, 격리

`PASS`는 정규 wrapper가 정확한 source commit에서 모든 identity를 zero failure/error/skip으로 실행하고, device/receipt/hash/cleanup 검증까지 통과했을 때만 사용합니다. `FAIL`은 실행했지만 어느 계약이든 실패한 상태, `QUARANTINED`는 활성 overlay로 인한 non-PASS 상태, `NOT RUN`은 지원·권한 있는 attempt 자체가 없는 상태입니다.

초기 PR job만 job-level `continue-on-error: true`인 report-only입니다. 개별 step은 실패를 숨기지 않습니다. scheduled/manual job은 실패 시 red이지만 Task 8에서는 `release-publish` 선행 조건이 아닙니다. PR job을 blocking으로 승격하려면 최신 정책/selector/workflow/test 변경 뒤 세 번 연속 weekly 전체 매트릭스 PASS와 세 번의 서로 다른 PR smoke PASS, 완전한 artifact, zero skip, qualification 기간 전체 무격리, 재시도/폐기 없음, source SHA와 run/artifact identity를 인용한 별도 리뷰 커밋이 필요합니다.

격리는 `config/quality/device-evidence-quarantine.json`에 정규 test identity 하나, owner, issue, reason, created/expiry를 기록하며 최대 7일입니다. wildcard/class/API 단위 격리, `@Ignore`, inventory 삭제, `|| true`는 허용하지 않습니다. 활성 격리는 canonical expected set을 바꾸지 않고 항상 `QUARANTINED`/DEGRADED입니다.

## 현재 runtime 증거와 한계

2026-08-21 Task-8 구현 준비 시점의 상태는 다음과 같습니다.

- API-28 failure-artifact transport: `NOT RUN` — 현재 작업은 Darwin arm64/no-push/no-hosted-run 권한 경계이며 controlled device probe를 실행하지 않았습니다.
- `api24-scheduled`, `api28-pr-smoke`, `api28-scheduled`, `api36-scheduled`: 모두 `NOT RUN` — host parser/TestKit/compile 증거만 있으며 지원 Linux x86_64/KVM runtime attempt가 없습니다.
- hosted workflow: `HOSTED NOT RUN` — workflow dispatch, push, 원격 run을 수행하지 않았습니다. PR은 report-only 상태를 유지합니다.

사용하는 AOSP/Google emulator 및 hosted runner/SDK package는 mutable하고 OEM permission-controller를 대표하지 않습니다. 이 매트릭스에는 OEM/물리 API 24/28/36, API 37 lane, live `prod`/Opinet, 실네트워크 주소 품질, 물리 기기 Macrobenchmark 증거가 없습니다. 에뮬레이터 UI smoke 결과로 `docs/performance.md`나 README의 물리 기기 수치를 갱신하지 않습니다.
