# 시작하기

로컬에서 저장소를 열고 `demo`/`prod`를 처음 빌드한다. 규칙은 [`AGENTS.md`](../../AGENTS.md), 전체 경로는 [온보딩](developer-onboarding-guide.md)이다.

## 준비물

- Java 21 이상. bytecode target은 JVM 17이다.
- Android SDK 37, 저장소 Gradle wrapper.
- agent script용 Python 3.9 이상.
- `prod`를 실제로 켤 때만 `opinet.apikey`와 위치·네트워크.

API 키 없이 시작하는 기본은 `demo`다. `demo`는 임시 mock이 아니다. seed DB, 기본 설정, 권한 허용 뒤 고정 좌표로 같은 상태를 반복한다.

## checkout

```bash
git clone https://github.com/beyondwin/GasStation.git
cd GasStation
git status --short
scripts/agent/preflight.sh
```

이미 worktree가 있으면 새로 만들기 전에 `git worktree list`와 그 공간의 status, diff를 본다. 기존 변경을 자동으로 stash/reset/clean하지 않는다.

## 첫 `demo`

```bash
./gradlew :app:assembleDemoDebug
```

APK는 `app/build/outputs/apk/demo/debug/`에 생긴다. Android Studio에서 `demoDebug`를 고르거나 아래처럼 설치한다.

```bash
./gradlew :app:installDemoDebug
```

권한을 허용하기 전에는 고정 좌표나 캐시 목록으로 안내를 건너뛰지 않는다. approximate 또는 precise 권한이 허용된 뒤에만 강남역 2번 출구 좌표와 seed가 들어온다.

빠른 unit 확인:

```bash
./gradlew :app:testDemoDebugUnitTest
```

실제 검증 범위는 [검증 매트릭스](../verification-matrix.md)에서 고른다.

## `prod`

`prod`는 실제 위치와 Opinet(또는 proxy)을 쓴다. 빈 키로 빌드는 되지만 앱 시작 시 `ProdSecretsStartupHook`가 바로 실패한다.

키는 저장소 `gradle.properties`에 쓰지 않는다. 넣는 위치와 한계는 [README 실행](../../README.md#실행)과 [보안](../security-trade-offs.md)을 본다.

```bash
./gradlew :app:assembleProdDebug
```

공개 배포 전 proxy 승격은 ADR과 보안 문서를 본다. `prod` 실서버 호출은 로컬 검증의 전제가 아니다.

## 끝나면

- wrapper가 Java와 SDK를 찾는다.
- 키 없이 `demoDebug`가 빌드된다.
- `demo`의 권한 gate와 seed를 설명할 수 있다.
- `prod` 키를 저장소에 커밋하지 않는다.

다음은 [아키텍처 둘러보기](architecture-tour.md) 또는 [변경 플레이북](change-playbook.md). 전체 지도는 [문서 허브](../README.md)다.
