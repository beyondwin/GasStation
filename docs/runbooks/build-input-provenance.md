# Build Input Provenance

이 문서는 GasStation의 검토된 빌드 입력, 문서 검증 bridge, unsigned prod release 재현성의 운영 owner다. 정책의 단일 기계 기준은 `config/quality/build-inputs.json`, 실행기는 `scripts/quality/verify_build_inputs.py`, Gradle 진입점은 `scripts/quality/build_inputs/run_gradle.sh`다.

## 보장 범위와 정직한 경계

검증된 SHA-256은 검토한 URL·메타데이터에 대한 byte integrity를 증명한다. publisher identity, 서명 provenance, 취약점 부재, 라이선스 검토를 대신하지 않는다. raw `./gradlew`, Android Studio, 임의 init script와 developer helper는 개발에 사용할 수 있지만 Task 9 receipt나 release gate 증거가 아니다. Task 7·8의 device/runtime lane은 새 정규 실행이 없으면 계속 `NOT RUN`이며 host compile, parser, artifact upload로 승격하지 않는다.

## 고정 입력 갱신

한 변경에서는 wrapper, action, JDK, Android SDK, Maven graph 중 한 input family만 갱신한다. `config/quality/build-inputs.json`을 재생성하고 정책 diff, 공식 source, size, SHA-256, executable identity를 함께 검토한다.

- Gradle 9.6.1 distribution SHA와 현재 wrapper JAR SHA는 Gradle 공식 checksum 자료와 대조한다. distribution URL·wrapper property·wrapper JAR을 함께 검증한 뒤 일반 build/test/lint와 configuration-cache 검증을 다시 수행한다.
- GitHub Action은 release tag의 annotated/lightweight 상태, peeled commit, tag membership을 공식 GitHub API로 확인한다. workflow의 `uses`는 full commit SHA만 허용한다. composite action은 재귀적으로 child `uses`와 각 manifest hash/runtime까지 닫는다. Dependabot PR도 같은 검토를 통과해야 한다.
- Linux x64 Temurin compile JDK는 `17.0.20+8`, runtime JDK는 `21.0.12.1+1`이다. 반드시 정책의 공식 versioned archive URL, exact byte size, SHA-256을 만족하고 closed extraction 뒤 `release` 및 `bin/java` identity를 확인한다. 단지 다운로드 가능한 이전 archive는 허용하지 않는다. CPU 또는 out-of-cycle security release는 정책 변경 review를 먼저 한다.
- Temurin archive는 query 없는 exact `github.com/adoptium/...` URL에서 시작해 정책에 고정된 role별 `release-assets.githubusercontent.com/github-production-release-asset/...` path로 가는 `302 -> 200` 한 hop만 허용한다. Signed query는 17개 key의 exact 집합, 고정 응답 filename/content type 값, JWT·signature·UUID·UTC-second grammar와 `skt <= se <= ske` 순서를 모두 검사한다. 최종 `Content-Length`, `Content-Type`, `Accept-Ranges`, streamed byte count와 SHA-256도 정책과 일치해야 하며 완료 archive를 extraction 직전에 다시 hash한다. Receipt에는 query 없는 초기 URL, status/hop, scheme·host·path, sorted key 이름, 문법 검사 결과, 안전한 최종 header, raw Location/effective URL의 size/hash, 최종 archive size/hash만 남긴다. Signed URL/query, JWT, signature, UUID, timestamp, cookie, authorization은 기록·재사용·role 간 공유하지 않는다.
- protected environment는 `JAVA_HOME`, `JAVA_HOME_17_X64`, `JAVA_HOME_21_X64`, `PATH`, `GRADLE_USER_HOME`과 `GRADLE_OPTS`, `JAVA_OPTS`, `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`, `ORG_GRADLE_PROJECT_*`다. 특히 wrapper가 소비하는 `JAVA_OPTS`도 비어 있어야 한다.

## Dependency resolution boundary

이 샘플 프로젝트는 Gradle dependency verification metadata를 운영하지 않는다. IDE가 source/Javadoc artifact를 받을 때마다 개별 checksum을 수집하는 비용이 샘플의 목적에 비해 크기 때문이다. 이 선택은 Maven artifact bytes를 저장소 밖의 별도 SHA-256 목록과 대조하지 않는다는 뜻이며, 공급망 위험이 0이라는 주장이 아니다.

대신 dependency version은 version catalog와 build script에 명시하고, repository는 `google()`, `mavenCentral()`, Gradle Plugin Portal로 제한하며, 동적 version/range/snapshot은 build-input verifier가 거부한다. Dependabot이 Gradle과 GitHub Actions 갱신을 제안하고, 일반 CI build/test/lint가 실제 호환성을 검증한다. Gradle wrapper distribution은 계속 `distributionSha256Sum`으로 검증하며 governed Gradle 진입점의 init script와 toolchain 환경 봉인도 유지한다.

## Android SDK와 Codecov

SDK package는 logical Task 8 image와 실제 sdkmanager coordinate를 구분한다. API 24는 `google_apis`, API 28 AOSP는 실제 `default`, API 36은 `google_apis` 좌표를 사용한다. package revision/hash는 reached lane의 실제 설치 metadata가 있을 때만 receipt에 기록하며 mutable repository 상태는 재검토 대상이다.

API 37 platform은 integer `compileSdk=37`에서 package path를 추정하지 않는다. Policy capture 직전에 exact `https://dl.google.com/android/repository/repository2-3.xml`을 새로 받아 reviewed body SHA-256과 old exact `platforms;android-37` 부재를 확인하고, `platforms;android-37.0` API `37.0`, extension `22`, layoutlib `15`, revision `2`, `Android SDK Platform 37.0`, `channel-0`, `platform-37.0_r02.zip`/`67281901`/repository SHA-1 record를 source receipt에 묶는다. SHA-1은 Google XML 필드를 보존한 source metadata일 뿐 Task 9의 archive authentication 주장이 아니다. XML body나 inventory가 바뀌면 old coordinate로 fallback하지 말고 policy review로 돌아간다.

Local Linux command-line tools는 `commandlinetools-linux-15859902_latest.zip`의 exact size/SHA-256과 141-member listing SHA-256, one `cmdline-tools/source.properties`, zero archive `package.xml`로 인증한다. Safe extraction 뒤 identity는 `cmdline-tools/latest/source.properties`의 exact 86 bytes, SHA-256, mode `0644`, LF-terminated `Pkg.Revision=22.0`, `Pkg.Path=cmdline-tools;22.0`, `Pkg.Desc=Android SDK Command-line Tools` 세 field가 소유한다. ZIP stored mode `100755`는 input fact이고 installed mode가 아니다. `cmdline-tools/latest/package.xml`을 만들거나 복사해 증거로 쓰는 것은 금지한다.

`sdkmanager`는 local evidence SDK에 `build-tools;36.0.0`, `platforms;android-37.0`, `platform-tools`만 설치한다. Installed receipt는 이 세 root의 `package.xml`만 허용하고 각 relative path, coordinate, owner role, size, SHA-256, mode `0644`를 기록한다. Selected binary는 command-line-tools role의 `sdkmanager`/`avdmanager`, build-tools role의 `aapt2`/`apksigner`/`zipalign`, platform-tools role의 `adb` 정확히 여섯 개이며 각 path, owner role, size, SHA-256, mode `0755`를 기록한다. Missing/extra file, source/package/binary role swap, fake command-line-tools XML, emulator/system-image install은 실패한다. Emulator와 Task-8 system-image coordinate는 실제 lane 실행 전까지 `runtimeEvidence: NOT RUN`을 유지한다.

Codecov upload는 선택적·비차단이다. action full SHA만으로 충분하지 않고 정책에 고정한 Codecov CLI binary URL, size, SHA-256을 검증한 뒤 action의 `binary` input으로 전달한다. `use_pypi`와 임의 downloader는 허용하지 않으며 token은 coverage job에만 둔다.

## Receipt와 evidence session

Receipt는 current source commit, policy hash, wrapper/JDK/action/SDK identity, opaque runner identity, 실행 결과만 allowlist로 기록한다. 절대 사용자 경로, 전체 environment, token/secret 값은 기록하지 않는다. canonical JSON duplicate key, symlink, stale source/event, dirty tree, duplicate evidence path는 거부한다. CI artifact는 source SHA가 포함된 exact name으로 업로드하고 release asset으로 공개하지 않는다.

Blocking phase에서 `build-inputs` job은 실패 완화 없이 정적 build-input 검증, configuration-cache 재사용, two-copy probe와 receipt capture를 모두 통과해야 한다. `release-assemble`과 `release-publish`는 이 job을 exact prerequisite로 두고 source SHA가 포함된 receipt artifact만 소비한다. step/job-level 실패 완화, 대체 command, latest artifact 이름은 허용하지 않는다.

Governed evidence session은 정책의 정확한 네 명령만 받으며 suffix나 fifth command를 허용하지 않는다.

1. `python3 scripts/quality/build_inputs/docs_gradle_validation_bridge.py --check-gradle-tasks`
2. `scripts/agent/verify-room-schemas.sh`
3. `scripts/agent/verify.sh auto`
4. `scripts/agent/verify.sh docs`

정확한 실행 명령은 [검증 매트릭스](../verification-matrix.md)에만 둔다.

## 전용 로컬 Linux evidence host

macOS arm64 controller에서 필수 Linux x64 evidence를 만들 수 있는 유일한 예외는 정책의 `gasstation-task9-linux-amd64` Colima profile이다. 이 경계는 VZ aarch64 guest를 transport로만 쓰고, Rosetta-backed `linux/amd64` Ubuntu digest container 안에서만 Gradle/JDK/Android host-tool evidence를 실행한다. native x64, hosted runner, 일반 container build 또는 hermetic proof로 부르지 않는다.

Ubuntu identity는 한 digest로 축약하지 않는다. 정책과 receipt는 official OCI index descriptor, 그 index에서 선택한 `linux/amd64` manifest descriptor, manifest의 OCI config descriptor, sole compressed rootfs layer descriptor를 각각 digest/media type/size와 함께 기록한다. Docker 29.2.1 containerd store의 index `Id`, 빈 `Architecture`/`Os`, empty `Config`/`RootFS`, `Size=7112`, familiar singleton `RepoDigests=ubuntu@sha256:…`는 관측값일 뿐 amd64 선택 증명이 아니며 full pull name으로 정규화해도 실패한다. Exact index reference에 `--platform linux/amd64`로 container를 만든 뒤 별도 container inspect의 `.Image=index digest`, `.Config.Image=full index reference`, `.Platform=linux`를 검사한다. Container label은 marker-bound owned 6개와 inherited `org.opencontainers.image.version=24.04` 하나만 허용하고, volume은 owned 6개만 허용한다. 이어서 container 내부 `uname=x86_64`, `dpkg=amd64`와 나머지 JDK/SDK x64 사실을 독립적으로 증명한다. OCI config identity는 manifest의 `OCIManifest.config`만 소유한다.

오케스트레이터는 clean full source SHA와 정책만 입력받는다. attempt 번호·경로·profile·context·image·명령·복구 mode는 호출자가 선택할 수 없다. host mount는 없고 source는 exact `HEAD`와 고정 `refs/heads/main=7b8c149c9f792aaf43cc00a94ba671929008979e` 두 ref의 Git bundle 하나만 `docker cp`로 전달한다. default/shared Colima profile과 Docker 자원은 검사·변경·정리 대상이 아니다.

Terminal `PASS`는 정적 build-input 검증, configuration-cache reuse, 정확한 네 evidence session, 두 clean-tree APK equality, 별도 third APK release binding, negative mutation suite와 ordered cleanup이 모두 같은 source/policy/attempt에 묶일 때만 가능하다. cleanup은 live daemon에서 exact container와 두 volume 부재를 먼저 증명한 뒤 `colima delete gasstation-task9-linux-amd64 --data --force`를 실행하고 profile/context/runtime data 부재까지 증명한다. 일부 성공, stale/mixed ownership 또는 접근 불가 daemon은 PASS가 아니다.

각 governed Docker/Gradle 단계는 Docker 실행 전에 command name과 shell SHA-256을 immutable `STARTED` receipt로 만들고, 종료 후 exit code, redacted combined stdout/stderr log의 size/SHA-256, truncation 여부를 별도 result receipt로 묶는다. 로그는 terminal cause를 보존하는 최대 64 KiB tail이며 host/container absolute path, secret assignment, signed redirect query value를 내보내지 않는다. Nonzero, missing log, hash/size drift, STARTED/result 불일치, Gradle의 generic-only 한 줄로 축약된 실패는 모두 fail closed다. 단계 receipt에 도달하기 전 실패해도 host attempt의 `failure-package/`가 지금까지의 command evidence, terminal `FAIL`, manifest를 보존한다. 성공 package도 같은 command evidence를 포함한다.

전용 profile을 만들기 전에 host는 `/usr/sbin/sysctl -n hw.logicalcpu hw.physicalcpu hw.memsize`의 정확한 세 값을 새로 읽는다. 정책 최소치는 logical CPU 14, physical CPU 14, physical memory `51539607552` bytes이고, 전용 guest만 exact `--cpus 14 --memory 32`와 persisted `cpu=14`, `memory=32`를 사용한다. Host 관측값과 최소치는 local-host receipt에서 분리해 기록하며 default/shared profile에서 추론하거나 그 profile을 resize하지 않는다. 120 GiB data disk와 40 GiB root disk는 그대로다.

Repository/default/CI/ordinary-local과 nested TestKit timeout은 15분이다. Convention suite는 `maxParallelForks=5`, 전체 90 tests, no retry/shard/skip 계약을 유지한다. TestKit fixture는 필요한 dependency cache seed만 공유하며 dependency verification metadata를 복사하거나 별도 capture graph를 실행하지 않는다.

`build/reports/build-inputs/local-linux-host.json`과 `local-linux-evidence-package.json`은 이 emulated local boundary만 나타낸다. Hosted evidence와 Task 8 device/emulator/ADB runtime은 별도 실행 전까지 `NOT RUN`이다.

## 문서 Gradle bridge와 Task 10 handoff

Governed 문서 검증은 byte-stable `scripts/quality/build_inputs/docs_gradle_validation_bridge.py`만 Gradle child를 소유한다. facade는 고정 경로 `scripts/docs/validate.py`, callable은 `validate_repository(root: pathlib.Path, *, discovered_gradle_tasks: frozenset[str] | None) -> list[str]`다. 직접 facade 실행은 local diagnostic일 뿐 accepted receipt를 만들지 않는다.

Bridge는 `scripts/docs/extensions/`를 repository-relative sorted order로 정확히 한 번 로드하고, 실행된 모든 `scripts/docs/**/*.py` production source를 dynamic closure receipt에 기록한다. tests/cache/bytecode는 제외한다. guarded import는 docs 밖 repository Python, 특히 `scripts/quality/**` import를 거부한다.

Task 10과 Documentation Phase 5는 bridge bytes와 정책 static hash를 그대로 두고 fixed facade의 bytes 및 docs-only extension/helper만 바꿀 수 있다. default와 bridge mode는 동일한 sorted extension/source set을 실행해야 한다. bridge, facade path/callable, allowed source roots를 바꾸거나 static hash를 새로 고쳐야 한다면 Task 9 review로 돌아온다.

## Unsigned prod release 재현성과 release binding

재현성 probe는 같은 source에서 두 clean Git tree, 서로 다른 Gradle user home·project cache·Kotlin cache를 사용해 `:app:assembleProdRelease`를 build-cache/configuration-cache 없이 rerun한다. 각 tree는 unsigned prod-release APK 정확히 하나를 내야 하며 size와 SHA-256이 같아야 한다. 서로 다른 APK를 normalize하지 않는다. 이 증거는 same-host/workspace-independent proof이며 cross-OS, runner-image, signed APK 재현성을 주장하지 않는다. demo-debug는 재현성 후보가 아니다.

Probe receipt는 source SHA와 policy hash, exact artifact relative path/name, 두 size/hash를 묶는다. `release-assemble`은 exact source-bound receipt를 내려받아 별도 조립한 prod APK의 unsigned 상태, size, SHA-256을 upload 전에 대조한다. `release-publish`도 receipt와 다운로드한 release artifact를 다시 대조한 뒤 checksum/release mutation을 수행한다. mismatch면 release를 멈추고 두 tree log, JDK/SDK receipt와 zip entry metadata 차이를 조사한다. receipt 자체는 public release asset이 아니다.

Rollback도 한 input family 단위로 수행한다. 정책을 이전 reviewed bytes로 되돌린 뒤 static checker, configuration-cache check, 일반 build/test/lint, docs bridge, two-copy probe와 release-binding fixture를 다시 실행한다.
