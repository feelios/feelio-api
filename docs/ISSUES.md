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
| [-] | - | A6-5 | ~~거래내역 정산금액 합치기(Merge) API~~ | feat/transaction-merge | 신규 API | Ctrl·Svc·Mapper | transactions | **제거** | **더치페이 정산 기능 자체가 팀 결정으로 제거됨.** API·`is_settled` 컬럼 모두 없으며 다시 만들지 않는다. 계약서 §6 참조 |
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
| [ ] | - | A8-5 | 회원 탈퇴 API(Delete User) 오류 현상 분석 및 수정 | `fix/account-deletion-backend` | - | - | - | 대기 | 계정 탈퇴 시 연관 데이터가 제대로 처리되지 않는 문제를 파악하여 무결성 유지하도록 백엔드 API 수정 |
| [ ] | - | A9-1 | 패턴 분석 데이터 확장 및 AI 위험루트 프롬프트 연계 | feat/pattern-ai-integration | §9 | Svc | - | 신규 | 패턴 응답에 내역 번호 추가 및 AI 위험루트 생성 시 패턴 분석 결과 주입 
| [ ] | 153 | A9-2 | [EDA] 패턴 분석용 RDBMS 집계 쿼리 최적화 | `feat/pattern-db-query` | - | Mapper+XML | transactions | 대기 | 메모리 로드 방식 탈피: 시간대·감정·카테고리 기반 GROUP BY 및 원본 내역(evidence) 쿼리 작성 |
| [ ] | 154 | A9-3 | [EDA] 거래 내역 CUD 비동기 이벤트 퍼블리싱 | `feat/pattern-event-publish` | - | Config·Svc | transactions | 대기 | 사용자 응답 속도 보장을 위해 거래 내역 CUD 직후 ApplicationEventPublisher를 통한 이벤트 발행 |
| [x] | 155 | A9-4 | [EDA] 비동기 AI 분석 및 결과 캐싱 | `feat/pattern-async-caching` | - | Svc | ai_insights | 신규 | @Async 리스너와 이벤트를 활용하여 DB 연산 + GPT 호출 결과를 ai_insights에 캐싱. API는 단 1줄의 캐시 결과만 반환. |
| [ ] | 160 | A9-5 | 패턴 분석 원본 내역(Evidence) 응답 추가 | `feat/pattern-evidence-ui` | - | DTO | - | 신규 | 프론트엔드 UI용 패턴 원본 내역(Evidence) 데이터를 TransactionPatternResponse DTO에 추가 및 캐싱 시 포함. |
| [ ] | 167 | A9-6 | AI 감정 분석 카드 백엔드 응답에 고유 식별자(emotion) 추가 | `feat/emotion-card-identifier` | - | DTO | - | 신규 | AI 리포트 반환 시 감정 카드(EmotionCard) DTO에 emotion 필드를 추가하여 앞면-뒷면 매핑 오류 방지 |
| [ ] | 169 | A10-1 | 로컬 서버 포트 충돌(좀비 프로세스) 운영 가이드 및 재현 방지 대책 | `docs/zombie-process-guide` | - | 운영 | - | 신규 | 홈 화면 500 에러의 근본 원인인 포트 8080 좀비 프로세스 문제를 문서화하고, 로컬 개발 시 재발 방지를 위한 서버 재시작 가이드 정리 |
| [ ] | 170 | A10-2 | AI 감정 소비 분석 프롬프트 페르소나 및 제약 조건 강화 | `fix/emotion-analysis-persona` | - | Svc | - | 신규 | 감정 분석 AI가 팩트 폭행 컨셉을 준수하도록 System Prompt의 페르소나·규제 강화, 파싱 실패 방지 Fallback 점검 및 응답 안정성 확보 |
| [ ] | 173 | A10-3 | 배포 워크플로에 앱 기동 헬스체크 추가 | `ci/deploy-healthcheck` | - | CI | - | 신규 | 워크플로가 `docker compose up -d`까지만 확인해 크래시 루프에도 success로 뜬다. 배포 스텝 뒤 헬스체크 재시도를 붙여 기동 실패를 즉시 드러낸다 |
| [ ] | 174 | A10-4 | 스프링 컨텍스트 로드 스모크 테스트 추가 | `test/context-load-smoke` | - | Test | - | 신규 | 단위 테스트(Mockito)는 컨텍스트를 안 띄워 빈 누락을 못 잡는다. `@SpringBootTest` 스모크 1개로 머지 전에 걸리게 한다 |
| [ ] | 175 | A10-5 | api-docker-compose.yaml 레포로 이관 | `chore/compose-to-repo` | - | 운영 | - | 신규 | compose·nginx conf가 VM에만 있어 실행 구성을 추적할 수 없다. 레포로 옮기고 시크릿 하드코딩을 제거해 값의 출처를 `.env` 하나로 통일한다 |
| [ ] | 176 | A10-6 | .dockerignore 브랜치 불일치 정리 | `fix/dockerignore-align` | - | 운영 | - | 신규 | `127556d`가 deploy-kmj에만 있어 환경마다 시크릿 출처가 갈린다. 원래 설계(이미지 제외)로 통일하고 레지스트리 private 전환도 검토 |
| [ ] | 177 | A10-7 | /login 라우팅으로 에러 JSON이 노출됨 | `fix/login-route-leak` | §3 | 운영·config | - | 신규 | nginx `location /login`이 백엔드로 프록시돼 OAuth 실패 시 안내 화면 대신 UNAUTHORIZED JSON이 그대로 보인다. 경로를 `/login/oauth2/`로 좁히고 SecurityConfig 방어선도 검토 |
| [ ] | 178 | A10-8 | 토큰 재발급 쿠키 없을 때 500 → 401 | `fix/refresh-missing-cookie` | §3 | Ctrl | - | 신규 | `@CookieValue` required=true라 쿠키가 없으면 INTERNAL_ERROR(500)가 나간다. 계약상 UNAUTHORIZED(401)이며 프론트 인터셉터도 401을 기대한다 |
| [ ] | 179 | A10-9 | API-CONTRACT 와 코드 불일치 2건 수정 | `docs/contract-sync` | §9 | docs | - | 신규 | 소비 위험도 판정 기준(377행)과 ai-report의 AI 호출 여부(383행)가 현재 코드와 다르다. 낡은 서술이 이슈 작성 근거를 잘못 잡게 한다 |
| [ ] | 180 | A10-10 | AI 문장 생성 경로 이중화 정리 | `refactor/unify-ai-generator` | §9 | Svc | - | 신규 | `GptInsightCardGenerator`와 `FactReportService`/`ChallengeService`가 같은 성격의 문장을 각각 만든다. 정본을 정하고 나머지를 제거하거나 위임 구조로 정리 |
| [ ] | - | A11-1 | 과거 달의 AI 분석 결과 연동 (월별 조회) | `feat/historical-ai-analysis` | - | Svc·Ctrl | - | 신규 | /api/analysis API들에 year, month 파라미터 추가 및 지출 발생 시 해당 월 AI 데이터 갱신 로직 구현 |
| [ ] | - | A11-2 | 목표 저금 시 예산/총자산 연동 비즈니스 로직 | `feat/goal-saving-asset-deduction` | - | Svc | - | 신규 | 예산 세이브 금액 확인 후 차감, 부족분은 총자산에서 차감하도록 TransactionService 비즈니스 로직 구현 |
| [ ] | - | A11-3 | 목표 트랜잭션 등록 500 에러 및 유효성 검사 강화 | `fix/goal-transaction-500` | - | Ctrl·Svc | - | 신규 | 카테고리 매핑 누락, goalId 부재 시 발생하는 500 에러 해결 및 예외 상황 400 반환 등 엣지케이스 방어 |
| [ ] | - | A11-4 | 예산 산출 로직 개편 (성과 기반 보완법 및 저축 불가 엣지 케이스) | `feat/budget-calc-performance-based` | - | Svc | - | 신규 | 전월 절약 항목에만 예산 삭감 적용 (평균의 함정 방지). 원천적으로 저축할 자산/수입이 없는 경우 예산 0원 배정 방지 로직 추가 |
| [ ] | - | A11-5 | AI API 타임아웃 및 폴백(Fallback) 방어 시스템 구축 | `feat/ai-timeout-fallback` | - | Svc | - | 신규 | OpenAI 등 외부 API 3~5초 응답 지연 시 500 에러 대신 200 OK와 기본 문구(Fallback Text) 반환하는 서킷 브레이커 도입 |
| [ ] | - | A11-6 | 과거 지출/수입 데이터 CUD 시 총자산 정합성 롤백 및 동기화 | `feat/past-transaction-sync` | - | Svc | - | 신규 | 과거 내역 수정/삭제 시 당시 예산/총자산 변동분을 역산하여 현재 총자산을 안전하게 재계산하는 트랜잭션 이벤트 구축 |
| [ ] | - | A11-7 | 목표 100% 달성 시 생명주기 완결 및 잔여 자산 환급 처리 | `feat/goal-lifecycle-completed` | - | Svc | - | 신규 | 목표 금액 100% 도달 시 IN_PROGRESS -> COMPLETED로 전환 및 초과 저축액 총자산 환급 로직 분리 |
| [ ] | - | A11-8 | 과거 달 예산 현황(Budget Status) 동적 조회 API 확장 | `feat/historical-budget-api` | - | Ctrl·Svc | - | 신규 | GET /api/analysis/budget API에 year, month 파라미터 추가 및 과거 지출 기반 예산 동적 산출 구현 (F17-5 연계) |
| [ ] | - | A12-1 | AI 챌린지 주간 단위 갱신 적용 및 스키마 수정 | `feat/ai-challenge-weekly-update` | - | Svc·DB | - | 신규 | ai_insights 캐싱 기준을 year-month-week로 세분화하여 매주 새로운 챌린지를 반환하도록 로직 및 DB 유니크 키 확장 |
| [ ] | - | A12-2 | 시스템 기본 카테고리 DB 리스트 전면 수정 | `feat/default-category-update` | - | DB | - | 신규 | 앱 초기화 시 제공되는 카테고리 17종(식비, 배달 등)으로 데이터베이스 기본 데이터 마이그레이션 |
| [ ] | #240 | A12-3 | 말랑이 코멘트 당월 대표 감정 기반 개인화 | `feat/mallang-emotion-comment` | §7 | Svc·DTO | emotions, transactions | 신규 | 당월 최다 감정을 프롬프트에 주입해 감정 기반 문구 생성. 감정명·수치는 서버가 확정하고 AI는 문장만 생성. 규칙기반 폴백도 감정별로 분기 |



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
