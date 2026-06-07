# 배포 절차

이 문서는 GasStation 새 버전을 발행할 때 확인할 배포 흐름의 단일 출처입니다. `docs/verification-matrix.md`는 검증 명령을, `CHANGELOG.md`와 `docs/release-notes/`는 버전별 변경 설명을 소유합니다.

## 현재 배포 경계

- 공식 실행 경로는 `demo`와 `prod`입니다. 둘 다 release 전에 빌드 가능해야 합니다.
- GitHub Actions는 PR에서 static analysis, unit tests, screenshot tests, debug assemble을 실행하고, `main`/`v*` tag push에서 `release-assemble`과 coverage를 추가 실행합니다.
- 저장소에는 Play Store 자동 배포, signing keystore, 배포 credential을 두지 않습니다.
- `prodRelease` APK/AAB signing은 저장소 밖의 keystore와 배포자 계정에서 처리합니다.
- `prod` 런타임에는 사용자 로컬 `opinet.apikey`가 필요합니다. 키는 `~/.gradle/gradle.properties` 또는 `-Popinet.apikey=<issued-key>`로 전달하고 저장소에 커밋하지 않습니다.
- 원격 조회 endpoint는 기본값이 `gasstation.stationEndpointMode=direct`(direct Opinet)입니다. proxy 빌드는 `-Pgasstation.stationEndpointMode=proxy -Pgasstation.proxyBaseUrl=<https-url>`로 전환하며(`STATION_ENDPOINT_MODE`/`PROXY_BASE_URL` BuildConfig로 주입), proxy 서버 배포는 [`docs/adr/2026-05-18-backend-proxy-escalation.md`](adr/2026-05-18-backend-proxy-escalation.md) 조건을 따릅니다.

## 릴리스 PR 준비

1. `main`에서 새 release branch를 만듭니다.
2. `app/build.gradle.kts`의 `versionCode`를 1 올리고 `versionName`을 다음 버전으로 갱신합니다.
3. `CHANGELOG.md`의 `Unreleased` 항목을 새 버전 섹션으로 이동하고, 새 `Unreleased`는 비워 둡니다.
4. `docs/release-notes/YYYY-MM-DD-vX.Y.Z.md`를 작성합니다.
5. `README.md`의 현재 앱 버전과 릴리즈 인덱스를 갱신합니다.
6. 배포/검증 절차가 바뀌면 이 문서와 `docs/verification-matrix.md`를 같은 PR에서 갱신합니다.

## 필수 검증

문서와 버전 메타데이터를 갱신한 릴리스 PR의 최소 확인입니다.

```bash
git diff --check -- README.md CHANGELOG.md CONTRIBUTING.md app/build.gradle.kts docs/deployment.md docs/verification-matrix.md docs/release-notes/*.md
./gradlew :app:assembleDemoDebug :app:assembleProdDebug :benchmark:assemble
./gradlew :app:assembleProdRelease
```

릴리스 내용이 앱 동작, cache, 위치, watchlist, startup hook, benchmark 기준을 바꾸면 `docs/verification-matrix.md`의 머지 전 권장 회귀 세트까지 확장합니다.

## Merge 후 tag

PR이 merge된 뒤 `main`에서 태그를 만들고 push합니다.

```bash
git switch main
git pull --ff-only
git tag vX.Y.Z
git push origin vX.Y.Z
```

`v*` tag push는 GitHub Actions에서 PR 범위 검증에 더해 `:app:assembleProdRelease`와 `koverXmlReport`를 실행합니다. 태그 push 자체가 Play Store 업로드를 수행하지는 않습니다.

## Android 산출물

로컬 release APK 확인:

```bash
./gradlew :app:assembleProdRelease
ls -l app/build/outputs/apk/prod/release/
```

현재 Gradle 설정은 release build에서 R8 minification을 켭니다. 공개 배포용 signed artifact가 필요하면 저장소 밖 keystore로 Android Studio 또는 별도 release job에서 서명합니다. keystore, store password, key password, service account JSON은 저장소에 두지 않습니다.

## 공개 배포 전 보안 gate

현재 `prod`는 Opinet API key를 Android 클라이언트 `BuildConfig`로 주입합니다. 이 방식은 reference/portfolio 범위에서는 단순하고 재현 가능하지만, APK에서 키를 완전히 숨기는 secret boundary가 아닙니다.

아래 조건 중 하나라도 참이면 release 전에 backend proxy 승격을 먼저 설계합니다.

- 공개 배포로 active install이나 API quota 비용이 의미 있게 커집니다.
- 키 abuse, quota exhaustion, key rotation 운영 요구가 생깁니다.
- 민감 데이터 엔드포인트나 사용자 식별 흐름이 추가됩니다.

승격 기준과 Android 영향 범위는 [`docs/security-trade-offs.md`](security-trade-offs.md)와 [`docs/adr/2026-05-18-backend-proxy-escalation.md`](adr/2026-05-18-backend-proxy-escalation.md)를 따릅니다.
