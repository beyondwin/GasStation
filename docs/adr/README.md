# Architecture Decision Records

이 디렉터리는 중요한 기술·운영 선택의 배경, 대안, 결정과 승격 조건을 남긴 ADR을 보관합니다. ADR의 날짜나 파일명만으로 그 결정이 지금도 승인되었거나 구현·배포가 완료되었다고 판단하지 않습니다.

현재 사실은 실제 코드와 `settings.gradle.kts`를 먼저 확인하고, 구조는 [아키텍처](../architecture.md), 보안 결정은 [보안 트레이드오프](../security-trade-offs.md), live 문서 소유자는 [documentation catalog](../documentation-catalog.json)를 따릅니다.

작은 진입점:

- [Backend proxy 승격 ADR](2026-05-18-backend-proxy-escalation.md)
- [보안 트레이드오프](../security-trade-offs.md)
- [현재 문서 허브로 돌아가기](../README.md)

새 ADR을 읽을 때는 status 문구만 보지 말고 해당 코드와 현재 live owner를 대조합니다. 이 README는 exhaustive index가 아닙니다.
