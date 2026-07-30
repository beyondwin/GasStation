# 배포 절차

이 문서는 GasStation 새 버전을 발행할 때 확인할 배포 흐름의 단일 출처입니다. `docs/verification-matrix.md`는 검증 명령을, `CHANGELOG.md`와 `docs/release-notes/`는 버전별 변경 설명을 소유합니다.

## 현재 배포 경계

- 공식 실행 경로는 `demo`와 `prod`입니다. 둘 다 release 전에 빌드 가능해야 합니다.
- GitHub Actions는 PR에서 agent contract, static analysis, unit, screenshot, debug/benchmark assemble을 실행하고, `main`/`v*` tag push에서 `release-assemble`과 coverage를 추가 실행합니다.
- `v*` tag에서는 위 job이 모두 성공한 뒤에만 `release-publish`가 GitHub Release를 만들거나 갱신하고 demo debug APK, unsigned prod release APK, `SHA256SUMS.txt`를 게시합니다.
- 저장소의 기본 workflow token 권한은 read-only입니다. GitHub Release에 필요한 `contents: write`는 tag-only `release-publish` job에만 부여합니다.
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
git diff --check -- README.md CHANGELOG.md CONTRIBUTING.md app/build.gradle.kts .github/workflows/android.yml docs/deployment.md docs/test-strategy.md docs/verification-matrix.md docs/release-notes/*.md
scripts/agent/tests/check_contracts_test.sh
scripts/agent/check-contracts.sh
scripts/agent/verify.sh release
```

릴리스 내용이 앱 동작, cache, 위치, watchlist, startup hook, benchmark 기준을 바꾸면 `docs/verification-matrix.md`의 머지 전 권장 회귀 세트까지 확장합니다.

## Merge 후 tag

PR이 merge되거나 release commit이 `main`에 push된 뒤, 해당 SHA의 GitHub Actions `Android CI`가 성공한 것을 먼저 확인합니다. `main` 검증이 끝난 같은 SHA에만 태그를 만들고 push합니다.

```bash
git switch main
git pull --ff-only
gh run list --workflow android.yml --branch main --limit 5
gh run watch <run-id> --exit-status
git tag vX.Y.Z
git push origin vX.Y.Z
gh run list --workflow android.yml --branch vX.Y.Z --limit 1
gh run watch <tag-run-id> --exit-status
gh release view vX.Y.Z --json url,assets
```

`gh`를 사용할 수 없는 환경에서는 GitHub Actions 웹 화면에서 `main`의 정확한 commit SHA와 모든 job 성공을 확인합니다. 실패하거나 아직 실행 중이면 태그를 만들지 않습니다.

`v*` tag push는 GitHub Actions에서 PR 범위 검증에 더해 `:app:assembleProdRelease`와 `coverageXmlReport`를 다시 실행합니다. `release-publish`는 모든 선행 job 성공 후 `docs/release-notes/*-vX.Y.Z.md`를 body로 사용해 GitHub Release를 게시합니다. tag와 `versionName`이 다르거나 release note가 정확히 하나가 아니거나 APK가 두 개가 아니면 발행 전에 실패합니다.

Workflow는 재실행에도 안전합니다. 같은 tag의 Release가 이미 있으면 note를 갱신하고 자산을 `--clobber`로 다시 올리며, tag 자체를 이동하거나 다시 만들지 않습니다. 자동화 도입 전에 만들어진 기존 tag를 보강할 때는 그 tag의 CI 성공과 release note를 확인한 뒤 `gh release create <tag> --verify-tag --notes-file <note>`로 Release record만 추가합니다.

태그 push와 GitHub Release는 Play Store 업로드를 수행하지 않습니다.

## Android 산출물

로컬 release APK 확인:

```bash
./gradlew :app:assembleProdRelease
ls -l app/build/outputs/apk/prod/release/
```

현재 Gradle 설정은 release build에서 R8 minification을 켭니다. 공개 배포용 signed artifact가 필요하면 저장소 밖 keystore로 Android Studio 또는 별도 release job에서 서명합니다. keystore, store password, key password, service account JSON은 저장소에 두지 않습니다.

GitHub Release 자산 이름은 다음 계약을 따릅니다.

| 자산 | 의미 |
| --- | --- |
| `GasStation-X.Y.Z-demo-debug.apk` | 키 없이 설치 가능한 deterministic demo 검증용 APK |
| `GasStation-X.Y.Z-prod-release-unsigned.apk` | R8/minify release 결과 확인용 unsigned APK. 설치·스토어 배포 전 외부 signing 필요 |
| `SHA256SUMS.txt` | 위 두 APK의 SHA-256 checksum |

## 공개 배포 전 보안 gate

현재 `prod`는 Opinet API key를 Android 클라이언트 `BuildConfig`로 주입합니다. 이 방식은 reference/portfolio 범위에서는 단순하고 재현 가능하지만, APK에서 키를 완전히 숨기는 secret boundary가 아닙니다.

아래 조건 중 하나라도 참이면 release 전에 backend proxy 승격을 먼저 설계합니다.

- 공개 배포로 active install이나 API quota 비용이 의미 있게 커집니다.
- 키 abuse, quota exhaustion, key rotation 운영 요구가 생깁니다.
- 민감 데이터 엔드포인트나 사용자 식별 흐름이 추가됩니다.

승격 기준과 Android 영향 범위는 [`docs/security-trade-offs.md`](security-trade-offs.md)와 [`docs/adr/2026-05-18-backend-proxy-escalation.md`](adr/2026-05-18-backend-proxy-escalation.md)를 따릅니다.
