| [ ] | #151 | A9-1 | 패턴 분석 데이터 확장 및 AI 위험루트 프롬프트 연계 | eat/pattern-ai-integration | §9 | Svc | - | 신규 | 패턴 응답에 내역 번호 추가 및 AI 위험루트 생성 시 패턴 분석 결과 주입 |
﻿# Feelio 백엔드 기능 이슈 표 (SSOT)

> **Claude / Gemini 어떤 도구로 작업하든 이 표를 공통 기준으로 삼는다.**
> 이슈 코드(예: A1-1)로 브랜치·계약섹션·슬롯·테이블·완료기준을 확정한다.
> 규칙 전체는 [AGENTS.md](../AGENTS.md), API 계약은 [docs/API-CONTRACT.md](./API-CONTRACT.md)가 SSOT.
> 코드 체계: 
> - A1=1차, A2=2차, A3=3차 (계약 §11 우선순위와 일치)
> - A4=API 추가 연동 (기존 구현 외 확장)
> - **A5=트랜잭션 관리 및 고급 API 연동**: 다중 삭제 및 반복 패턴 분석 등 새로운 로직 구현 (마일스톤 3)
> - **A6=동적 예산 및 자산 관리 고도화**: 총자산 개념 도입 및 목표 동적 예산 산출(봉투 예산법) 로직 개편 (마일스톤 6)

| 체크 | 이슈# | 코드 | 제목 | 브랜치 | 계약 | 슬롯 | 테이블 | 상태 | 완료기준(핵심) |
|---|---|---|---|---|---|---|---|---|---|
| [x] | - | A1-1 | 소셜 로그인(code 교환) | feat/auth-login | §3 | Ctrl·Svc·DTO·Entity·Mapper+XML | users, social_accounts, refresh_tokens, notification_settings, terms_agreements | 완료 | code+redirectUri로 provider 서버교환→검증→프로필→조회/가입(users+social_accounts+notification_settings 기본값+terms_agreements)→JWT 발급, provider 토큰 미저장 |
| [x] | - | A1-2 | 토큰 재발급 | feat/auth-refresh | §3 | Ctrl·Svc·DTO·Mapper | refresh_tokens | 완료 | refresh 검증→신규 access·refresh, UNAUTHORIZED 처리 |
| [x] | - | A1-3 | 로그아웃 | feat/auth-logout | §3 | Ctrl·Svc·Mapper | refresh_tokens | 완료 | refresh 폐기, onboarding_done 유지 |
| [x] | - | A1-4 | 메타 조회 | feat/meta | §5 | Ctrl·Svc·DTO·Entity·Mapper+XML | emotions, categories | 완료 | is_active=true만, 감정·카테고리 2종(상황 없음) |
| [x] | - | A1-5 | 내 정보 조회·수정 | feat/users-me | §4 | Ctrl·Svc·DTO·Entity·Mapper+XML | users | 완료 | user_id 기준, 닉네임 1~8자 검증 |
| [x] | - | A1-6 | 온보딩 완료 | feat/users-onboarding | §4 | Ctrl·Svc·Mapper | users | 완료 | {onboardingDone:true} |
| [x] | - | A1-7 | 거래 목록 조회 | feat/tx-list | §6 | Ctrl·Svc·DTO·Entity·Mapper+XML | transactions(+join) | 완료 | year 필수, 필터·검색·정렬 동적SQL, 평면목록+기간합계 |
| [x] | - | A1-8 | 거래 생성(단건) | feat/tx-create | §6 | Ctrl·Svc·DTO·Mapper+XML | transactions | 완료 | 필수값 검증, 단건 저장, memo 생략시 null |
| [x] | - | A1-9 | 거래 상세·수정·삭제 | feat/tx-crud | §6 | Ctrl·Svc·DTO·Mapper+XML | transactions | 완료 | user_id 기준, 타인 FORBIDDEN·NOT_FOUND |
| [x] | - | A2-1 | 목표 CRUD | feat/goals | §7 | Ctrl·Svc·DTO·Entity·Mapper+XML | goals | 완료 | name·targetAmount(>0) 필수, isMain 최대1건(같은 트랜잭션서 해제) |
| [x] | - | A2-2 | 홈 캘린더 요약 | feat/summary-calendar | §8 | Ctrl·Svc·DTO·Mapper+XML | transactions(집계) | 완료 | 일별 대표감정·건수·지출합, 기록없는 날 생략 |
| [x] | - | A2-3 | 감정 요약 | feat/summary-emotions | §8 | Ctrl·Svc·DTO·Mapper+XML | transactions(집계) | 완료 | 감정별 count·amount + prevMonth 비교, 지출 기준 |
| [x] | - | A2-4 | 사용자 설정 | feat/users-settings | §4 | Ctrl·Svc·DTO·Mapper | users | 완료 | themeMode/auroraTheme 부분전송 허용 |
| [x] | - | A3-1 | analysis/universe 스키마 계약 확정 | docs/contract-p3 | §9 | (문서) | — | 완료 | §9 응답 스키마 확정·문서 갱신(구현 아님) |
| [x] | - | A3-2 | 월간 분석 | feat/analysis | §9 | Ctrl·Svc·DTO·Mapper+XML | transactions(집계) | 완료 | 확정된 §9 스키마대로 |
| [x] | - | A3-3 | 평행우주 시뮬 | feat/universe | §9 | Ctrl·Svc·DTO·Mapper+XML | goals, transactions | 완료 | 확정된 §9 스키마대로 |
| [x] | - | A3-4 | 회원탈퇴 | feat/user-withdraw | §4 | Ctrl·Svc·Mapper+XML | users(+CASCADE) | 완료 | status=WITHDRAWN + 하위 CASCADE 삭제 |
| [x] | - | A3-5 | 거래 전체 초기화 | feat/tx-reset | §6 | Ctrl·Svc·Mapper | transactions | 완료 | 본인 거래 전체 삭제 → {deletedCount} |
| [x] | - | A4-1 | 커스텀 카테고리 설정 | feat/custom-category-order | 신규 | Ctrl·Svc·DTO·Entity·Mapper+XML | custom_categories, category_orders | 완료 | 커스텀 카테고리 추가/삭제, 공통+커스텀 통합 정렬 순서 저장 및 반환 |
| [x] | - | A4-2 | 프론트 연동용 CORS 설정 | feat/cors-credentials | §14 | config | - | 완료 | Allow-Credentials: true 활성화 및 Allow-Origin에 프론트 도메인 매핑 |
| [x] | - | A4-3 | AI 멘트 API 설계 및 Mock 연동 | feat/analysis-ai-insights-api | 신규 API | Ctrl·DTO | - | 완료 | `GET /api/analysis/ai-insights` 신설 → 응답 DTO(`aiQuickInsights`, `emotionCards`) 설계 → DB 없이 Mock 객체 반환(200 OK) |
| [x] | - | A4-4 | 최근 7개월 지출 추이 API 설계 | feat/analysis-trend-api | 신규 API | Ctrl·Svc·DTO·Mapper | transactions | 완료 | 1. `GET /api/analysis/trend` 엔드포인트 신설.<br>2. 호출 시점 기준 최근 7개월(당월 포함)간 월별 총 지출액 Group By 집계 (데이터 없는 달은 금액 `0`으로 채워 총 7개 요소 반환 보장).<br>3. 당월 및 전월 총 지출액을 비교하여 증감률(%) 계산 후 프론트 규격에 맞춰 JSON 반환. |
| [x] | - | A4-5 | 목표 예산 현황 API 설계 | feat/analysis-budget-api | 신규 API | Ctrl·Svc·DTO·Mapper | transactions | 완료 | 1. `GET /api/analysis/budget` 엔드포인트 신설 및 응답 DTO(`budgetItems`) 설계.<br>2. 당월 소비 카테고리 기준 저번 달 지출 금액(`prevAmount`) DB 조회 로직 구현.<br>3. `prevAmount` 값에 `0.95`를 곱해 이번 달 목표 예산을 자동 산출하는 비즈니스 로직 적용.<br>4. 카테고리별 소비 내역에서 지배적 "감정 태그"(예: 스트레스) 추출 및 동반 반환. |
| [x] | - | A5-1 | 다중 거래내역 삭제 API | feat/transaction-bulk-delete-api | 신규 API | Ctrl·Svc·Mapper | transactions | 완료 | 다중 거래내역 ID 배열을 받아 DB에서 일괄 삭제 처리 |
| [x] | - | A5-2 | 반복 소비 패턴 분석 API | feat/recurring-pattern-api | 신규 API | Ctrl·Svc·DTO·Mapper | transactions | 완료 | 동일 감정/시간대/사용처 소비 패턴 반환, 5분 이내 중복 결제 병합 필터링 포함 |
| [x] | - | A6-1 | 온보딩 '총자산' 필드 추가 | feat/onboarding-total-asset | 신규 API | Ctrl·Svc·DTO·Mapper | users | 완료 | 온보딩 API 호출 시 사용자의 총자산(totalAsset) 금액 입력받아 DB 저장 |
| [ ] | - | A6-2 | 거래내역 저축-목표 매핑 (FK) | feat/transaction-goal-mapping | 신규 API | Ctrl·Svc·DTO·Mapper+XML | transactions | 신규 | 카테고리가 '저축'인 지출 생성 시 goal_id(Nullable) 함께 매핑하여 저장 처리 |
| [x] | - | A6-3 | 목표 달성액(모은 돈) 동적 산출 | feat/goal-amount-dynamic-calc | 신규 API | Ctrl·Svc·Mapper+XML | goals, transactions | 완료 | 목표의 현재 금액을 단순 DB 값이 아닌 `초기금액 + SUM(해당 goal_id 거래액)`으로 산출 반환 |
| [x] | - | A6-4 | 동적 예산 분석 로직 개편 | feat/analysis-dynamic-budget | 신규 API | Ctrl·Svc·DTO | goals | 완료 | 기존 5% 로직 폐기, 모든 활성 목표의 월별 필요 저축액을 합산하여 이번 달 최종 예산으로 산출 및 초과/미달 판단 |
| [ ] | - | A6-5 | 거래내역 정산금액 합치기(Merge) API | feat/transaction-merge | 신규 API | Ctrl·Svc·Mapper | transactions | 신규 | 거래내역 수정 모달에서 정산받은 금액(receivedAmount) 입력 시 원본 지출 금액에서 차감하여 단일 거래로 덮어씌우는 물리적 병합 API |
| [x] | - | FIX-1 | OAuth2 Stateless 세션 충돌 및 설정 오류 수정 | fix/oauth2-stateless | §3 | config | - | 완료 | 1. 쿠키 기반 인증 요청 저장소(HttpCookieOAuth2AuthorizationRequestRepository) 구현<br>2. SecurityConfig 권한 및 저장소 주입<br>3. OAuth2SuccessHandler ResponseCookie(SameSite=Lax) 적용<br>4. application.yaml 카카오/네이버 `client_secret_post` 및 `user-name-attribute` 오타 수정 |
| [x] | 108 | A7-1 | 홈 화면 AI 멘트 — 계약 확정 및 구현 | feat/summary-ai-comment | §8 | Ctrl·Svc·DTO·Mapper | transactions(집계) | 완료 | `GET /api/summary/ai-comment` 분리 → 당월·전월 지출 비교 멘트, 사용자별 일 1회 메모리 캐시. 거래 없음·GPT 실패 시 `comment: null` 200 응답으로 홈 로딩과 분리 |
| [x] | 106 | A7-2 | AI 분석 인사이트 GPT 연동 | feat/analysis-gpt-insights | §9 | Svc·Entity·Mapper+XML | ai_insights | 완료 (PR #121) | 1. `InsightCardGenerator` 인터페이스 + 규칙기반·GPT 구현체 2종<br>2. `AiQuickInsightAssembler`로 계약 §9 4건 고정·순서 고정 조립, `SpendStatus`로 전월 대비 소비 위험도<br>3. `AiInsight` 엔티티 + `AiInsightStore` + Mapper XML — `ai_insights` 저장(최초 생성 후 DB 조회, 당월 `INSIGHT_TTL_HOURS` 주기 재생성)<br>4. GPT 실패 시 규칙기반 폴백. **`INSIGHT_PROVIDER` 기본값이 `rule`이라 머지만으로는 GPT가 켜지지 않는다** — 활성화는 배포 `.env`에 `INSIGHT_PROVIDER=gpt` + `OPENAI_KEY` 필요 |
| [x] | 107 | A7-3 | 평행우주 narration GPT 연동 | feat/universe-gpt-narration | §9 | Svc·DTO | goals, transactions | 신규 | `scenarios[].narration`을 템플릿에서 GPT 생성으로 전환. **숫자 필드는 계약 §9 계산식 그대로 두고 문장만 생성.** GPT 실패 시 기존 템플릿 폴백 |
| [x] | - | A8-1 | FCM 웹 푸시 서버 연동 | feat/fcm-push | - | Svc·Ctrl·DB | users | 신규 | FCM 토큰 저장 및 Firebase Admin SDK를 활용한 결제 직후 data-only 푸시 발송 |
| [x] | 114 | A7-4 | 분석 리포트 API 뼈대 및 소비위험도 로직 | feat/analysis-api-skeleton | §9 | Ctrl·Svc·DTO | transactions | 완료 | `GET /api/analysis/ai-report` → 예산 소진율 기반 `RED/YELLOW/GREEN` 소비 위험도와 팩트·챌린지·감정 Mock 문구 반환. AI 호출 없음 |
| [x] | 115 | A7-5 | [MZ 팩트 폭격기] 페르소나 연동 | feat/fact-bomber-ai | §9 | Svc | - | 완료 | `FactReportService` 신설 → 예산 상태·당월 지출·최대 지출 카테고리 기반 GPT 프롬프트. `ai-report.ai.fact`에 병합하고 비활성화·AI 실패 시 준비 중 문구 폴백 |
| [x] | 116 | A7-6 | [챌린지 마스터] 맞춤 챌린지 연동 | feat/challenge-master-ai | §9 | Svc·Mapper+XML | transactions | 완료 | `ChallengeService` 신설 → 최근 7일 카테고리별 지출 기반 측정 가능한 GPT 미션 생성. `ai-report.ai.challenge`에 병합하고 기록 없음·비활성화·AI 실패 시 준비 중 문구 폴백 |
| [x] | 117 | A7-7 | [다정한 심리 상담사] 감정소비 분석 연동 | feat/emotion-counselor-ai | §9 | Svc | - | 완료 | `EmotionAnalysisService` 신설 → 당월 감정별 지출·대표 카테고리·시간대 기반 발견→의미→조언 3단계 GPT 분석. `ai-report.ai.emotion`에 병합하고 기록 없음·비활성화·AI 실패·형식 오류 시 준비 중 문구 폴백 |
| [ ] | - | A8-2 | AI 팩트 리포트 프롬프트 조정 (강력한 경고 톤) | `feat/ai-fact-report-prompt` | - | - | - | 신규 | 팩트 리포트를 생성하는 AI 프롬프트를 수정하여 지출 위험에 대해 강력하고 직관적인 어조로 변경 |
| [ ] | - | A8-3 | 홈 화면 말랑이 코멘트용 AI 생성 API 구현 | `feat/home-mallang-ai-api` | - | - | - | 신규 | 홈 말랑이 코멘트용 프롬프트를 추가하여 칭찬/경고 수치 및 독려 멘트를 생성 반환 |
| [ ] | - | A8-4 | 거래내역 생성 및 수정 시 시간(Time) 바인딩 버그 수정 | `fix/transaction-time-binding` | - | - | - | 신규 | 거래내역 생성/수정 시 createdAt/updatedAt 중 올바른 시간이 반영되고 정확히 반환되는지 점검 및 수정 |
<<<<<<< Updated upstream
| [ ] | - | A8-5 | 회원 탈퇴 API(Delete User) 오류 원인 분석 및 수정 | `fix/account-deletion-backend` | - | - | - | 신규 | 계정 탈퇴 시 정상적으로 처리되지 않는 원인을 파악하여 데이터가 안전하게 삭제되도록 API 수정 |
| [x] | - | A9-1 | 백엔드: 패턴 분석 데이터 확장 및 AI 위험루트 프롬프트 연계 | feat/pattern-ai-integration | - | api | - | 신규 | 패턴 응답에 내역 번호 추가 및 AI 위험루트에 패턴 분석 주 |
=======
| [ ] | - | A8-5 | 회원 탈퇴 API(Delete User) 오류 현상 분석 및 수정 | `fix/account-deletion-backend` | - | - | - | 대기 | 계정 탈퇴 시 연관 데이터가 제대로 처리되지 않는 문제를 파악하여 무결성 유지하도록 백엔드 API 수정 |
| [ ] | - | A9-1 | 패턴 분석 데이터 확장 및 AI 위험루트 프롬프트 연계 | feat/pattern-ai-integration | §9 | Svc | - | 신규 | 패턴 응답에 내역 번호 추가 및 AI 위험루트 생성 시 패턴 분석 결과 주입 
| [ ] | 153 | A9-2 | [EDA] 패턴 분석용 RDBMS 집계 쿼리 최적화 | `feat/pattern-db-query` | - | Mapper+XML | transactions | 대기 | 메모리 로드 방식 탈피: 시간대·감정·카테고리 기반 GROUP BY 및 원본 내역(evidence) 쿼리 작성 |
| [ ] | 154 | A9-3 | [EDA] 거래 내역 CUD 비동기 이벤트 퍼블리싱 | `feat/pattern-event-publish` | - | Config·Svc | transactions | 대기 | 사용자 응답 속도 보장을 위해 거래 내역 CUD 직후 ApplicationEventPublisher를 통한 이벤트 발행 |
| [ ] | 155 | A9-4 | [EDA] 비동기 AI 분석 및 결과 캐싱 | `feat/pattern-async-caching` | - | Svc | ai_insights | 대기 | @Async 리스너에서 이벤트를 수신하여 DB 집계 + GPT 조언을 구한 뒤 ai_insights에 캐싱. API는 단일 캐시 행만 반환. |
>>>>>>> Stashed changes

> **A7 = AI 연동 (마일스톤 7).** 세 이슈 공통: AI 호출 실패·타임아웃이 화면 장애로 이어지지 않도록 폴백을 반드시 둔다.
> A7-2를 먼저 진행해 폴백·설정 구조를 잡고, A7-3이 그 구조를 재사용하는 순서를 권한다. A7-1은 계약 확정 전까지 `blocked`.

## 병렬 작업 규칙 (Claude ↔ Gemini 충돌 방지)
- **도메인 단위로 분할**한다. 같은 도메인(auth/users/transactions…) 이슈를 둘이 쪼개 갖지 않는다.
- `refresh_tokens`를 공유하는 auth 3종(A1-1/2/3)은 **한 사람**이 맡는다.
- `global/` 패키지는 이슈 #2·#3에서 완료 — **수정 금지, 재사용만**(ApiResponse/ErrorCode/BusinessException).
- 각자 위 표의 **브랜치명 그대로** 사용해 브랜치 충돌을 막는다. 한 이슈 = 한 브랜치 = 한 PR.
- 스키마/계약 변경은 먼저 보고·승인. `docs/API-CONTRACT.md`·이 표를 코드보다 먼저 갱신한다.

## [참고] 동적 예산 및 자산 관리 고도화 (백엔드 M6, 프론트엔드 M7) 핵심 비즈니스 로직
> **⚠️ AI 에이전트(Claude, Gemini 등) 필독:** A6(백엔드) 및 F7(프론트엔드) 작업을 수행할 때 아래의 회계 룰을 반드시 준수해야 합니다.

### 1. 기획 배경 및 핵심 목표
*   **기존의 한계:** 무조건 전월 지출의 5%를 절약하도록 예산 고정됨.
*   **변경되는 아키텍처:** 사용자가 설정한 모든 목표의 '월별 필요 저축액'을 합산하여 그 총액만큼 덜 쓰도록 **동적 예산** 편성.
*   **수동 할당(봉투 예산법) 도입:** 예산이 모자랄 때 사용자가 직접 어떤 목표에 우선적으로 돈을 넣을지 선택(수동 저금)하는 자산 관리 UX 구현.

### 2. 총자산(Total Asset)과 목표(Goal)의 회계 규칙
*   '총자산'은 사용자의 실제 전체 자산이며, '목표 금액'은 총자산 안에서 "안 쓸 돈"으로 꼬리표를 달아둔 돈입니다.
*   **이동(Transfer) 개념 적용:** 목표에 10만 원을 저축한다고 해서 '총자산'에서 돈을 빼버리면 안 됩니다. 내부적으로 `총자산(500만) = 미할당 자산(490만) + 묶인 자산(목표 10만)`으로 상태만 변경되어야 하며, 전체 총자산 수치는 유지됩니다.
*   **시나리오별 예산 동적 대응:**
    1. **예산 달성:** 계획된 예산만큼 절약 성공 시, 목표치 그래프 정상 성장.
    2. **예산 초과 달성:** 목표 저축액을 모두 채우고 남은 돈은 '나의 총자산(미할당 자산)'에 플러스 누적.
    3. **예산 미달:** 목표를 채울 수 없는 상태이므로 자동 성장이 멈추며, 사용자가 수동으로 홈 화면에서 [저금하기]를 눌러 거래 내역과 목표를 매핑(goal_id)해야 함.
