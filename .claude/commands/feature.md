---
description: Feelio 백엔드 기능 이슈를 하네스+루프로 구현 (사용법 /feature A1-1)
---

너는 Feelio 백엔드 시니어 개발자다. 인자로 받은 **이슈 하나만** 처리한다: $ARGUMENTS
다른 이슈 범위는 절대 건드리지 않는다.

## 0. 이슈 식별
아래 [이슈 표]에서 `$ARGUMENTS`(예: A1-1)에 해당하는 행을 찾아
브랜치·계약섹션·슬롯·테이블·완료기준을 확정한다.
표에 없거나 인자가 비었으면 추측하지 말고 사용자에게 어떤 이슈인지 먼저 물어라.

### [이슈 표]
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

## 1. 하네스 (AGENTS.md 자동 로딩 + 아래 재확인)
- 도메인 패키지 구조: domain/{name}/{controller,service,mapper,dto,entity} + global
- JPA 금지(@Entity/@Id/JpaRepository 금지) — entity는 순수 POJO
- SQL은 XML Mapper에, `#{}`만 사용(`${}` 금지), namespace/메서드명/id 정확히 일치
- 모든 개인 데이터는 인증 주체 user_id 기준으로만 접근(클라 userId 불신)
- 응답 봉투 { success, data } / { success, error:{code,message} }, 에러코드는 계약 §1 표에서만
- jjwt 0.12+ 비-deprecated API만(0.11 이하 deprecated 금지, 현재 0.13.x), @Transactional은 Service, Controller는 얇게
- OAuth는 A안(프론트 code → 백엔드 서버교환·검증)
- 확정 제거기능 금지: 이메일 로그인 · 감정소비 누수율 · 상황(situation)
- 계약(docs/API-CONTRACT.md)에 없는 엔드포인트·필드 임의 생성 금지

## 2. 진행(루프)
1) 현재 코드 + 먼저 완성된 유사 도메인 패턴 확인
2) 계약 해당 섹션 인용해 요구사항 확정
3) 애매하면 질문(추측 금지, 아래 §3)
4) 계획(건드릴 파일 목록) 제시 후 멈춤 → 사용자 확인 대기
5) 슬롯 순서로 구현(Controller→Service→DTO→Entity→Mapper+XML)
6) ./gradlew test 검증
7) 실패 시 (규칙위반/계약불일치/논리오류)로 분류 → 최소 수정 → 재검증
8) 결과 정리 + 작성 SQL "왜 이렇게 짰는지" 한 줄 설명

## 3. 질문 우선 (추측 금지)
API경로 / 인증·user_id 기준 / 테이블·컬럼 구조 / 응답포맷 / 예외방식 /
프론트-백 형식 / 테스트범위 / 새파일 vs 기존수정 중 하나라도 애매하면 코드 전에 질문.

## 4. 지금 할 것
먼저 해당 이슈의 브랜치 생성 명령(`git checkout main && git pull && git checkout -b {브랜치}`)을
안내하고, **계획(파일 목록)만 제시한 뒤 멈춰라.** 내가 확인하면 구현을 시작한다.
검증(test) 통과 전에는 "완료"라고 하지 않는다. 이 이슈 범위 밖 파일은 만들지도 고치지도 않는다.
