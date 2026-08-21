# Build Input Provenance

이 문서는 GasStation의 검토된 빌드 입력, strict dependency verification, 문서 검증 bridge, unsigned prod release 재현성의 운영 owner다. 정책의 단일 기계 기준은 `config/quality/build-inputs.json`, 실행기는 `scripts/quality/verify_build_inputs.py`, Gradle 진입점은 `scripts/quality/build_inputs/run_gradle.sh`다.

## 보장 범위와 정직한 경계

검증된 SHA-256은 검토한 URL·메타데이터에 대한 byte integrity를 증명한다. publisher identity, 서명 provenance, 취약점 부재, 라이선스 검토를 대신하지 않는다. raw `./gradlew`, Android Studio, 임의 init script와 developer helper는 개발에 사용할 수 있지만 Task 9 receipt나 release gate 증거가 아니다. Task 7·8의 device/runtime lane은 새 정규 실행이 없으면 계속 `NOT RUN`이며 host compile, parser, artifact upload로 승격하지 않는다.

## 고정 입력 갱신

한 변경에서는 wrapper, action, JDK, Android SDK, Maven graph 중 한 input family만 갱신한다. `config/quality/build-inputs.json`을 재생성하고 정책 diff, 공식 source, size, SHA-256, executable identity를 함께 검토한다.

- Gradle 9.6.1 distribution SHA와 현재 wrapper JAR SHA는 Gradle 공식 checksum 자료와 대조한다. distribution URL·wrapper property·wrapper JAR을 함께 검증한 뒤 strict matrix를 다시 수행한다.
- GitHub Action은 release tag의 annotated/lightweight 상태, peeled commit, tag membership을 공식 GitHub API로 확인한다. workflow의 `uses`는 full commit SHA만 허용한다. composite action은 재귀적으로 child `uses`와 각 manifest hash/runtime까지 닫는다. Dependabot PR도 같은 검토를 통과해야 한다.
- Linux x64 Temurin compile JDK는 `17.0.20+8`, runtime JDK는 `21.0.12.1+1`이다. 반드시 정책의 공식 versioned archive URL, exact byte size, SHA-256을 만족하고 closed extraction 뒤 `release` 및 `bin/java` identity를 확인한다. 단지 다운로드 가능한 이전 archive는 허용하지 않는다. CPU 또는 out-of-cycle security release는 정책 변경 review를 먼저 한다.
- protected environment는 `JAVA_HOME`, `JAVA_HOME_17_X64`, `JAVA_HOME_21_X64`, `PATH`, `GRADLE_USER_HOME`과 `GRADLE_OPTS`, `JAVA_OPTS`, `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`, `ORG_GRADLE_PROJECT_*`다. 특히 wrapper가 소비하는 `JAVA_OPTS`도 비어 있어야 한다.

## Dependency verification

`gradle/verification-metadata.xml`은 SHA-256만 사용한다. weak/alternate/ignored/trusted artifact 규칙을 추가하지 않는다. 동적 version/range/snapshot, verification disable API/property/environment, `-I`/`--init-script`는 governed 경로에서 전부 거부한다. init-script allowlist는 비어 있다. Lockfile은 version selection owner가 catalog와 build script이므로 현재 만들지 않는다.

메타데이터 생성은 정책의 closed generation matrix를 fresh Gradle home에서 실행한다. TestKit nested build는 outer build의 새 artifact를 자동 반영하지 않으므로 dedicated TestKit capture graph를 별도로 실행하고 추가 component/artifact/checksum을 검토한다. 생성 행을 두 번 수행해 XML hash가 변하지 않아야 하며, 이후 fresh cold home에서 complete strict matrix와 대표 offline row를 통과해야 한다. alternate checksum이 관측되면 즉시 중단하고 repository origin, redirect/CDN, component version, official checksum을 조사한다. 설명 없는 두 checksum을 함께 허용하지 않는다.

TestKit은 root metadata를 fixture에 byte-for-byte 복사하고 hash를 비교한 뒤, fresh home과 exact sanitized environment에서 strict mode로 실행한다. 한 필수 SHA를 fixture copy에서 제거한 negative test는 dependency-verification 진단으로 실패해야 한다.

## Android SDK와 Codecov

SDK package는 logical Task 8 image와 실제 sdkmanager coordinate를 구분한다. API 24는 `google_apis`, API 28 AOSP는 실제 `default`, API 36은 `google_apis` 좌표를 사용한다. package revision/hash는 reached lane의 실제 설치 metadata가 있을 때만 receipt에 기록하며 mutable repository 상태는 재검토 대상이다.

Codecov upload는 선택적·비차단이다. action full SHA만으로 충분하지 않고 정책에 고정한 Codecov CLI binary URL, size, SHA-256을 검증한 뒤 action의 `binary` input으로 전달한다. `use_pypi`와 임의 downloader는 허용하지 않으며 token은 coverage job에만 둔다.

## Receipt와 evidence session

Receipt는 current source commit, policy hash, wrapper/JDK/action/SDK/metadata identity, component·artifact·checksum count와 XML hash, opaque runner identity, 실행 결과만 allowlist로 기록한다. 절대 사용자 경로, 전체 environment, token/secret 값은 기록하지 않는다. canonical JSON duplicate key, symlink, stale source/event, dirty tree, duplicate evidence path는 거부한다. CI artifact는 source SHA가 포함된 exact name으로 업로드하고 release asset으로 공개하지 않는다.

Blocking phase에서 `build-inputs` job은 실패 완화 없이 strict matrix, configuration-cache 재사용, two-copy probe와 receipt capture를 모두 통과해야 한다. `release-assemble`과 `release-publish`는 이 job을 exact prerequisite로 두고 source SHA가 포함된 receipt artifact만 소비한다. step/job-level 실패 완화, 대체 command, latest artifact 이름은 허용하지 않는다.

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

Terminal `PASS`는 metadata no-diff replay, online cold와 same-home offline strict, product strict, configuration-cache reuse, 정확한 네 evidence session, 두 clean-tree APK equality, 별도 third APK release binding, negative mutation suite와 ordered cleanup이 모두 같은 source/policy/attempt에 묶일 때만 가능하다. cleanup은 live daemon에서 exact container와 두 volume 부재를 먼저 증명한 뒤 `colima delete gasstation-task9-linux-amd64 --data --force`를 실행하고 profile/context/runtime data 부재까지 증명한다. 일부 성공, stale/mixed ownership 또는 접근 불가 daemon은 PASS가 아니다.

`build/reports/build-inputs/local-linux-host.json`과 `local-linux-evidence-package.json`은 이 emulated local boundary만 나타낸다. Hosted evidence와 Task 8 device/emulator/ADB runtime은 별도 실행 전까지 `NOT RUN`이다.

## 문서 Gradle bridge와 Task 10 handoff

Governed 문서 검증은 byte-stable `scripts/quality/build_inputs/docs_gradle_validation_bridge.py`만 Gradle child를 소유한다. facade는 고정 경로 `scripts/docs/validate.py`, callable은 `validate_repository(root: pathlib.Path, *, discovered_gradle_tasks: frozenset[str] | None) -> list[str]`다. 직접 facade 실행은 local diagnostic일 뿐 accepted receipt를 만들지 않는다.

Bridge는 `scripts/docs/extensions/`를 repository-relative sorted order로 정확히 한 번 로드하고, 실행된 모든 `scripts/docs/**/*.py` production source를 dynamic closure receipt에 기록한다. tests/cache/bytecode는 제외한다. guarded import는 docs 밖 repository Python, 특히 `scripts/quality/**` import를 거부한다.

Task 10과 Documentation Phase 5는 bridge bytes와 정책 static hash를 그대로 두고 fixed facade의 bytes 및 docs-only extension/helper만 바꿀 수 있다. default와 bridge mode는 동일한 sorted extension/source set을 실행해야 한다. bridge, facade path/callable, allowed source roots를 바꾸거나 static hash를 새로 고쳐야 한다면 Task 9 review로 돌아온다.

## Unsigned prod release 재현성과 release binding

재현성 probe는 같은 source에서 두 clean Git tree, 서로 다른 Gradle user home·project cache·Kotlin cache를 사용해 `:app:assembleProdRelease`를 build-cache/configuration-cache 없이 rerun한다. 각 tree는 unsigned prod-release APK 정확히 하나를 내야 하며 size와 SHA-256이 같아야 한다. 서로 다른 APK를 normalize하지 않는다. 이 증거는 same-host/workspace-independent proof이며 cross-OS, runner-image, signed APK 재현성을 주장하지 않는다. demo-debug는 재현성 후보가 아니다.

Probe receipt는 source SHA와 policy hash, exact artifact relative path/name, 두 size/hash를 묶는다. `release-assemble`은 exact source-bound receipt를 내려받아 별도 조립한 prod APK의 unsigned 상태, size, SHA-256을 upload 전에 대조한다. `release-publish`도 receipt와 다운로드한 release artifact를 다시 대조한 뒤 checksum/release mutation을 수행한다. mismatch면 release를 멈추고 두 tree log, JDK/SDK/metadata receipt와 zip entry metadata 차이를 조사한다. receipt 자체는 public release asset이 아니다.

Rollback도 한 input family 단위로 수행한다. 정책과 metadata를 이전 reviewed bytes로 함께 되돌린 뒤 static checker, cold strict matrix, docs bridge, two-copy probe와 release-binding fixture를 다시 실행한다.
