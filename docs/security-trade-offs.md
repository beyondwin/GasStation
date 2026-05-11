# Security Trade-offs

이 문서는 GasStation의 보안 결정 단일 출처입니다. 각 항목은 현재 선택, 한계, 승격 조건을 함께 기술합니다.

## API Key in Client BuildConfig

**현재 선택:** `prod` Opinet API key는 Gradle property(`opinet.apikey`)로 주입되어 `BuildConfig.OPINET_API_KEY`로 컴파일됩니다.

**한계:** APK를 역컴파일하면 키를 추출할 수 있습니다. 완전한 서버 측 비밀 경계가 아닙니다.

**수용 근거:** Opinet Open API는 공개 데이터(주유소 가격)를 반환하는 저위험 엔드포인트입니다. 키 유출로 인한 피해는 quota 소진에 국한됩니다.

**승격 조건:** 공개 배포, quota 비용이 큰 운영 환경, 또는 민감 데이터 엔드포인트 추가 시 backend proxy + key restriction + quota monitoring 설계로 전환합니다.

## Cleartext HTTP Whitelist

**현재 선택:** `prod` Network Security Config에서 Opinet API 도메인(`www.opinet.co.kr`)에 한해 cleartext HTTP를 허용합니다.

**한계:** 해당 도메인 트래픽은 중간자 공격에 노출될 수 있습니다.

**수용 근거:** Opinet Open API가 HTTPS를 지원하지 않습니다. 반환 데이터는 공개 가격 정보이며 인증 토큰을 포함하지 않습니다.

**승격 조건:** Opinet API가 HTTPS를 지원하면 cleartext 예외를 즉시 제거합니다.

## Android Backup Disabled

**현재 선택:** `AndroidManifest.xml`에서 `android:allowBackup="false"`, `android:fullBackupContent="false"` 설정으로 Android cloud backup을 비활성화합니다.

**보호 범위:** 로컬 캐시(`station_cache`, `station_cache_snapshot`, `station_price_history`), watchlist(`watched_station`), 설정(DataStore)이 Android backup/data extraction 대상으로 내보내지지 않습니다.

**수용 근거:** 백업 복원 시 stale 캐시나 오래된 설정이 복원되어 예측하기 어려운 상태를 만들 수 있습니다. 앱 데이터는 항상 fresh fetch로 복원 가능합니다.

## Certificate Pinning

**현재 선택:** 인증서 피닝을 적용하지 않습니다.

**한계:** 중간자 공격 시 신뢰 체인이 시스템 CA에 의존합니다.

**수용 근거:** 반환 데이터가 공개 가격 정보이며, 피닝 유지 비용(인증서 갱신 시 강제 업데이트)이 현재 위험 수준보다 큽니다.

**승격 조건:** 민감 데이터 엔드포인트 추가 시 피닝 도입을 검토합니다.

## CrashReporter Abstraction

**현재 선택:** `CrashReporter` 인터페이스를 `app` 모듈에 두고, `feature`/`domain`/`data`/`core` 모듈은 이 인터페이스에만 의존합니다. 프로덕션 구현은 Hilt로 주입하며, 현재 기본 구현은 Timber 기반입니다.

**보안 관련성:** 크래시 리포터가 사용자 데이터나 민감 정보를 원격 서버로 전송하는 경우, 인터페이스 교체만으로 전송 범위를 제어할 수 있습니다.

**승격 조건:** Firebase Crashlytics 등 원격 크래시 리포터 도입 시, 수집 데이터 항목을 이 문서에 추가하고 개인정보 처리방침을 갱신합니다.
