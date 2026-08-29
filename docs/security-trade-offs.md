# 보안

각 항목은 지금 선택, 한계, 언제 바꿀지다.

## API 키

**지금:** `prod` Opinet 키는 Gradle property `opinet.apikey`로 들어가 `BuildConfig.OPINET_API_KEY`가 된다.

**한계:** APK를 열면 키를 꺼낼 수 있다. 서버 비밀이 아니다.

**왜 이렇게:** Open API는 공개 가격 데이터다. 유출 피해는 quota 소진 정도다.

**바꿀 때:** 공개 배포, quota 비용, 민감 데이터가 생기면 backend proxy + key restriction + quota monitoring으로 간다.

Android는 `gasstation.stationEndpointMode=proxy`와 `gasstation.proxyBaseUrl`로 proxy를 고를 수 있다. 기본은 direct다. proxy 서버는 아직 기본 배포되지 않는다. URL은 `/`로 끝나는 절대 http(s)만 통과한다. 자세한 조건은 [ADR](adr/2026-05-18-backend-proxy-escalation.md)이다.

## Cleartext HTTP

**지금:** `www.opinet.co.kr`에만 HTTP를 연다.

**한계:** 그 도메인은 중간자 공격에 노출될 수 있다.

**왜 이렇게:** Opinet Open API가 HTTPS를 안 준다. 응답은 공개 가격이고 인증 토큰이 없다.

**바꿀 때:** Opinet이 HTTPS를 주면 예외를 바로 뺀다.

## Android Backup

**지금:** `android:allowBackup="false"`.

로컬 캐시, 관심, 설정이 cloud backup으로 나가지 않는다. 복원된 stale 캐시보다 다시 받아오는 편이 낫다.

## Certificate pinning

**지금:** 쓰지 않는다. 유지 비용이 현재 위험보다 크다. 민감 데이터가 생기면 검토한다.

## CrashReporter

계약은 `core:observability`에 두고, 구현은 `app`이 flavor별로 붙인다. 지금 prod는 Logcat이다. 원격 리포터를 넣으면 수집 항목을 이 문서와 개인정보 처리방침에 적는다.

## 빌드 입력

CI는 검토한 action SHA, wrapper checksum, Temurin archive, SDK, Maven SHA를 본다. 이건 byte integrity다. publisher 서명이나 취약점 스캔이 아니다. 절차는 [Build Input Provenance](runbooks/build-input-provenance.md)다.
