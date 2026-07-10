# Feelio 백엔드 기능 이슈 표 (SSOT)

> **Claude / Gemini 어떤 도구로 작업하든 이 표를 공통 기준으로 삼는다.**
> 이슈 코드(예: A1-1)로 브랜치·계약섹션·슬롯·테이블·완료기준을 확정한다.
> 규칙 전체는 [AGENTS.md](../AGENTS.md), API 계약은 [docs/API-CONTRACT.md](./API-CONTRACT.md)가 SSOT.
> 코드 체계: A1=1차, A2=2차, A3=3차 (계약 §11 우선순위와 일치).

| 코드 | 제목 | 브랜치 | 계약 | 슬롯 | 테이블 | 완료기준(핵심) |
|---|---|---|---|---|---|---|
| A1-1 | 소셜 로그인(code 교환) | feat/auth-login | §3 | Ctrl·Svc·DTO·Entity·Mapper+XML | users, social_accounts, refresh_tokens, notification_settings, terms_agreements | code+redirectUri로 provider 서버교환→검증→프로필→조회/가입(users+social_accounts+notification_settings 기본값+terms_agreements)→JWT 발급, provider 토큰 미저장 |
| A1-2 | 토큰 재발급 | feat/auth-refresh | §3 | Ctrl·Svc·DTO·Mapper | refresh_tokens | refresh 검증→신규 access·refresh, UNAUTHORIZED 처리 |
| A1-3 | 로그아웃 | feat/auth-logout | §3 | Ctrl·Svc·Mapper | refresh_tokens | refresh 폐기, onboarding_done 유지 |
| A1-4 | 메타 조회 | feat/meta | §5 | Ctrl·Svc·DTO·Entity·Mapper+XML | emotions, categories | is_active=true만, 감정·카테고리 2종(상황 없음) |
| A1-5 | 내 정보 조회·수정 | feat/users-me | §4 | Ctrl·Svc·DTO·Entity·Mapper+XML | users | user_id 기준, 닉네임 1~8자 검증 |
| A1-6 | 온보딩 완료 | feat/users-onboarding | §4 | Ctrl·Svc·Mapper | users | {onboardingDone:true} |
| A1-7 | 거래 목록 조회 | feat/tx-list | §6 | Ctrl·Svc·DTO·Entity·Mapper+XML | transactions(+join) | year 필수, 필터·검색·정렬 동적SQL, 평면목록+기간합계 |
| A1-8 | 거래 생성(단건) | feat/tx-create | §6 | Ctrl·Svc·DTO·Mapper+XML | transactions | 필수값 검증, 단건 저장, memo 생략시 null |
| A1-9 | 거래 상세·수정·삭제 | feat/tx-crud | §6 | Ctrl·Svc·DTO·Mapper+XML | transactions | user_id 기준, 타인 FORBIDDEN·NOT_FOUND |
| A2-1 | 목표 CRUD | feat/goals | §7 | Ctrl·Svc·DTO·Entity·Mapper+XML | goals | name·targetAmount(>0) 필수, isMain 최대1건(같은 트랜잭션서 해제) |
| A2-2 | 홈 캘린더 요약 | feat/summary-calendar | §8 | Ctrl·Svc·DTO·Mapper+XML | transactions(집계) | 일별 대표감정·건수·지출합, 기록없는 날 생략 |
| A2-3 | 감정 요약 | feat/summary-emotions | §8 | Ctrl·Svc·DTO·Mapper+XML | transactions(집계) | 감정별 count·amount + prevMonth 비교, 지출 기준 |
| A2-4 | 사용자 설정 | feat/users-settings | §4 | Ctrl·Svc·DTO·Mapper | users | themeMode/auroraTheme 부분전송 허용 |
| A3-1 | analysis/universe 스키마 계약 확정 | docs/contract-p3 | §9 | (문서) | — | §9 응답 스키마 확정·문서 갱신(구현 아님) |
| A3-2 | 월간 분석 | feat/analysis | §9 | Ctrl·Svc·DTO·Mapper+XML | transactions(집계) | 확정된 §9 스키마대로 |
| A3-3 | 평행우주 시뮬 | feat/universe | §9 | Ctrl·Svc·DTO·Mapper+XML | goals, transactions | 확정된 §9 스키마대로 |
| A3-4 | 회원탈퇴 | feat/user-withdraw | §4 | Ctrl·Svc·Mapper+XML | users(+CASCADE) | status=WITHDRAWN + 하위 CASCADE 삭제 |
| A3-5 | 거래 전체 초기화 | feat/tx-reset | §6 | Ctrl·Svc·Mapper | transactions | 본인 거래 전체 삭제 → {deletedCount} |
| A4-1 | 커스텀 카테고리 설정 | feat/custom-category-order | 신규 | Ctrl·Svc·DTO·Entity·Mapper+XML | custom_categories, category_orders | 커스텀 카테고리 추가/삭제, 공통+커스텀 통합 정렬 순서 저장 및 반환 |
## 병렬 작업 규칙 (Claude ↔ Gemini 충돌 방지)
- **도메인 단위로 분할**한다. 같은 도메인(auth/users/transactions…) 이슈를 둘이 쪼개 갖지 않는다.
- `refresh_tokens`를 공유하는 auth 3종(A1-1/2/3)은 **한 사람**이 맡는다.
- `global/` 패키지는 이슈 #2·#3에서 완료 — **수정 금지, 재사용만**(ApiResponse/ErrorCode/BusinessException).
- 각자 위 표의 **브랜치명 그대로** 사용해 브랜치 충돌을 막는다. 한 이슈 = 한 브랜치 = 한 PR.
- 스키마/계약 변경은 먼저 보고·승인. `docs/API-CONTRACT.md`·이 표를 코드보다 먼저 갱신한다.
