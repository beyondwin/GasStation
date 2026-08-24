# GasStation 시작하기

이 문서는 새 기여자의 로컬 준비, 저장소 checkout, `demo`/`prod` 첫 실행과 첫 성공 build를 안내합니다. 작업 원칙은 [`AGENTS.md`](../../AGENTS.md), 전체 온보딩 경로는 [개발자 온보딩 가이드](developer-onboarding-guide.md)를 먼저 확인합니다.

## 준비물

- Java 21 이상. Android 앱과 라이브러리의 bytecode target은 JVM 17입니다.
- Android SDK 37과 저장소에 포함된 Gradle wrapper.
- agent script를 실행할 Python 3.9 이상.
- `prod`를 실제 실행할 때만 발급받은 `opinet.apikey`와 위치·네트워크 환경.

API key 없이 시작할 수 있는 `demo`가 첫 실행의 기본 경로입니다. `demo`와 `prod`는 모두 정식 경로이며, `demo`는 임시 mock 화면이 아니라 seed DB, 초기 선호값과 권한 허용 뒤 고정 좌표로 문서·테스트·benchmark가 같은 상태를 재현하는 경로입니다.

## Checkout과 사전 확인

```bash
git clone https://github.com/beyondwin/GasStation.git
cd GasStation
git status --short
scripts/agent/preflight.sh
```

기존 worktree를 이어서 작업한다면 새 worktree를 만들기 전에 `git worktree list`, 대상 worktree의 status와 diff, 관련 SDD progress를 확인합니다. 기존 변경을 자동으로 stash, reset, clean하지 않습니다.

## 첫 `demo` build

```bash
./gradlew :app:assembleDemoDebug
```

성공하면 APK는 `app/build/outputs/apk/demo/debug/` 아래에 생성됩니다. Android Studio에서 `demoDebug` variant를 선택하거나 연결된 개발 기기에 아래 명령으로 설치할 수 있습니다.

```bash
./gradlew :app:installDemoDebug
```

위치 권한을 허용하기 전에는 고정 좌표나 캐시 목록으로 권한 안내를 우회하지 않습니다. approximate 또는 precise 권한이 허용된 뒤 강남역 2번 출구 기준 고정 좌표와 승인된 seed가 공급됩니다.

첫 build 뒤 빠른 app-level unit 확인은 다음과 같습니다.

```bash
./gradlew :app:testDemoDebugUnitTest
```

변경 작업의 실제 검증 범위는 이 짧은 시작 명령으로 정하지 않습니다. [검증 매트릭스](../verification-matrix.md)에서 변경 유형에 맞는 범위를 선택합니다.

## `prod` 준비와 첫 build

`prod`는 실제 위치 provider와 direct Opinet 또는 proxy endpoint를 사용합니다. build 자체는 빈 key로 가능하지만 앱 시작 시 `ProdSecretsStartupHook`가 key 누락을 즉시 실패로 처리합니다.

개인 key는 version control 대상인 저장소 루트 `gradle.properties`에 쓰지 않습니다. 사용자별 Gradle properties나 실행 시 Gradle property로 전달하는 현재 경계와 Android client `BuildConfig`의 한계는 [루트 README의 실행 모드](../../README.md#실행-모드)와 [보안 트레이드오프](../security-trade-offs.md)를 따릅니다.

```bash
./gradlew :app:assembleProdDebug
```

공개 배포 전에는 backend proxy, quota, key restriction과 abuse monitoring 승격 조건을 현재 ADR 및 보안 문서에서 다시 확인합니다. `prod` 실서버 호출은 일반 로컬 검증의 전제가 아닙니다.

## 완료 기준과 다음 경로

다음을 확인하면 첫 실행 단계가 끝납니다.

- Gradle wrapper가 Java와 Android SDK를 찾는다.
- key 없이 `demoDebug`가 build된다.
- `demo`의 권한 gate와 deterministic seed 역할을 설명할 수 있다.
- `prod`의 key·실위치·실네트워크 경계를 알고, 로컬 secret을 저장소에 커밋하지 않는다.

다음은 [아키텍처 둘러보기](architecture-tour.md)에서 제품 원칙과 runtime 흐름을 읽거나 [변경 플레이북](change-playbook.md)으로 첫 작업을 준비합니다. 전체 문서로 돌아가려면 [문서 허브](../README.md)를 사용합니다.
