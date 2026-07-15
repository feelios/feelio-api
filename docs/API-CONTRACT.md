# Feelio API-CONTRACT v1

> **이 문서가 프론트(feelio-web)와 백엔드(feelio-api)의 유일한 API 계약이다.**
> 여기 없는 엔드포인트·필드를 임의로 만들지 않는다. 변경이 필요하면 **코드보다 이 문서를 먼저 수정하고 팀에 공유**한 뒤 구현한다. 양쪽 repo의 이 파일은 항상 동일하게 유지한다.
> 근거: 기획 문서 STEP7(DB 설계서)·STEP8(API 명세서)·STEP9(화면-API 워크플로우)
> ⚠️ 로그인은 **소셜 로그인(Google/Kakao/Naver) 전용**이다. 이메일/비밀번호 로그인은 만들지 않는다 (팀 확정).

- Base URL: `/api` (프론트는 `VITE_API_BASE_URL` 환경변수 사용)
- 인증: **BFF 패턴**. 토큰은 `accessToken`·`refreshToken` **HttpOnly 쿠키**로만 오간다(브라우저 JS 노출 금지). 보호 API 호출 시 브라우저가 `accessToken` 쿠키를 자동 전송하며, 프론트는 요청에 `withCredentials`(쿠키 동봉)를 켠다
- JSON은 camelCase (DB snake_case ↔ 서버 매핑)
- 모든 개인 데이터는 **인증 주체의 user_id 기준으로만** 조회·변경 (클라이언트가 보낸 userId는 신뢰하지 않음)

---

## 1. 공통 응답 봉투

성공:
```json
{ "success": true, "data": { } }
```

실패:
```json
{ "success": false, "error": { "code": "VALIDATION_ERROR", "message": "금액은 1원 이상이어야 합니다." } }
```

### 에러 코드

| code | HTTP | 의미 |
|---|---|---|
| VALIDATION_ERROR | 400 | 필수 값 누락·형식 오류 (금액 ≤ 0, 존재하지 않는 emotionId 등) |
| INVALID_PROVIDER | 400 | 지원하지 않는 소셜 제공자 |
| UNAUTHORIZED | 401 | 토큰 없음·검증 실패 |
| TOKEN_EXPIRED | 401 | 액세스 토큰 만료 (프론트: refresh 후 재시도) |
| FORBIDDEN | 403 | 타인 리소스 접근 |
| NOT_FOUND | 404 | 대상 없음 |
| INTERNAL_ERROR | 500 | 서버 오류 |

프론트 공통 처리: `TOKEN_EXPIRED` → `POST /api/auth/token/refresh` → 원 요청 재시도 → 실패 시 로그인 화면. 요청 타임아웃 5초.

## 2. 코드 값 (마스터 시드 — 서버·프론트 공통 기준)

- **감정 8종 (고정, 커스텀 불가):** 신남, 설렘, 뿌듯함, 스트레스, 외로움, 화남, 평온, 무덤덤
- **카테고리:** EXPENSE — 식비, 배달, 카페, 교통, 쇼핑, 문화, 건강, 기타 / INCOME — 급여, 용돈, 기타
- 감정 색상·정렬의 원본은 웹 `src/styles/theme.js`의 emotionPalette → DB emotions 테이블 시드로 이관
- ⚠️ **감정소비 누수율 관련 API·필드는 만들지 않는다 (제거 확정 기능)**
- ⚠️ **상황(situation) 관련 API·필드·테이블은 만들지 않는다 (제거 확정 기능)**

## 3. 인증 (Auth)

> **BFF 패턴**(Spring Security `oauth2Login`). 프론트는 인가 코드(code)를 직접 다루지 않는다.
> 백엔드가 provider와 서버-투-서버로 교환·검증하고, 자체 JWT를 **HttpOnly 쿠키**로 발급한다.
> provider 토큰도 우리 JWT도 브라우저 JS에 노출되지 않는다.

### 소셜 로그인 (리다이렉트 플로우) · 인증 불필요

로그인 전용 `POST` 엔드포인트는 **없다**. 아래 리다이렉트 흐름으로 처리한다.

1. 프론트가 브라우저를 `GET /oauth2/authorization/{provider}`로 이동시킨다.
   - `{provider}`: `google` | `kakao` | `naver` (Spring Security registrationId, 소문자)
2. provider 로그인·동의 → provider가 백엔드 콜백(`/login/oauth2/code/{provider}`)으로 리다이렉트.
3. 서버: provider와 **서버-투-서버로 code 교환·검증**(client_secret 사용) → 프로필(식별자·이메일·닉네임·**프로필 이미지**) 수신 → `(provider, provider_user_id)` 조회, 없으면 신규 가입(users + social_accounts + notification_settings 기본값 + terms_agreements) → **provider 토큰은 검증 후 폐기(미저장)**.
4. 서버가 자체 JWT(`accessToken` 1h, `refreshToken` 14d)를 **HttpOnly 쿠키**로 구운 뒤 프론트 URL로 리다이렉트한다.

- ⚠️ accessToken·refreshToken은 **HttpOnly 쿠키로만** 내려온다. 응답 바디로 토큰을 주지 않으며, 브라우저 JS는 토큰 값을 읽을 수 없다.
- 로그인 후 사용자 정보는 `GET /api/users/me`(§4)로 조회한다(신규 가입 여부·온보딩 상태 포함). user 객체 구조는 §4 참조.
- 이후 보호 API는 브라우저가 `accessToken` 쿠키를 자동 전송해 인증한다(별도 Authorization 헤더 불필요).
- 지원하지 않는 provider·교환 실패는 로그인 실패로 처리되어 프론트 로그인 화면으로 리다이렉트된다.

### POST /api/auth/token/refresh — 토큰 재발급 · 인증 불필요

- Request: **바디 없음**. 브라우저가 `refreshToken` 쿠키를 자동 전송한다(`withCredentials`).
- Response(200): 새 `accessToken`·`refreshToken`을 **HttpOnly 쿠키로 재발급**(회전). 브라우저는 갱신된 쿠키로 원 요청을 재시도한다.
- 에러: UNAUTHORIZED(401 — 쿠키 없음·검증 실패·만료·재사용) → 프론트는 로그인 화면으로

### POST /api/auth/logout — 로그아웃 · 인증 필요

Request 없음 → Response(200) `data`: `{ "loggedOut": true }`
- 서버: refresh_token 폐기 + `accessToken`·`refreshToken` 쿠키를 만료(삭제)시킨다. **users.onboarding_done은 유지** (재로그인 시 온보딩 재표시 없음 — 팀 확정)

## 4. 사용자 (Users)

### GET /api/users/me · 인증 필요
Response `data` (로그인 리다이렉트 직후 프론트가 사용자 상태를 확인하는 기준 객체):
```json
{
  "userId": 1,
  "nickname": "서연",
  "email": "user@example.com",
  "profileImageUrl": "https://.../photo.jpg",
  "provider": "GOOGLE",
  "onboardingDone": false,
  "themeMode": "LIGHT",
  "auroraTheme": "블루"
}
```

### PATCH /api/users/me · 인증 필요
Request: `{ "nickname": "새닉네임" }` (1~8자) → Response `data`: 갱신된 user 객체. 에러: VALIDATION_ERROR

### PATCH /api/users/me/onboarding · 인증 필요
Request 없음 → Response `data`: `{ "onboardingDone": true }`

### PATCH /api/users/me/settings · 인증 필요
Request: `{ "themeMode": "DARK", "auroraTheme": "핑크" }` (부분 전송 허용) → Response `data`: 갱신된 설정.
- themeMode: `LIGHT` | `DARK` / auroraTheme: theme.js auroras 키

### DELETE /api/users/me — 회원탈퇴 · 인증 필요
Request: `{ "reason": "탈퇴 사유 (선택)" }` → Response `data`: `{ "withdrawn": true }`
- 서버: users.status=WITHDRAWN + 하위 데이터 CASCADE 삭제. 탈퇴 계정 재로그인 정책은 미확정(팀 확정 필요)

## 5. 마스터 (Meta)

### GET /api/meta · 인증 필요
Response `data`:
```json
{
  "emotions":   [ { "emotionId": 4, "name": "스트레스", "color": "#A68BEA", "sortOrder": 4 } ],
  "categories": [ { "categoryId": 3, "name": "카페", "type": "EXPENSE", "sortOrder": 3 } ]
}
```
- `is_active=true`만 반환. 프론트는 세션 캐시(TanStack Query staleTime 길게). 기록 입력 폼·필터 옵션·수정 폼이 사용.

## 6. 거래 기록 (Transactions)

### 거래 객체 (응답 공통)
```json
{
  "transactionId": 10,
  "type": "EXPENSE",
  "amount": 18600,
  "category":  { "categoryId": 3, "name": "카페" },
  "emotion":   { "emotionId": 4, "name": "스트레스", "color": "#A68BEA" },
  "memo": "달달한 라떼와 케이크",
  "occurredAt": "2026-07-01T21:30:00"
}
```
- type: `EXPENSE` | `INCOME` / 감정·카테고리는 단일

### GET /api/transactions · 인증 필요

| Query | 필수 | 설명 |
|---|---|---|
| year | Y | 연도 |
| month | N | 월 (없으면 연 전체) |
| day | N | 일 |
| emotionId | N | 복수 콤마: `4,5` |
| categoryId | N | 복수 콤마: `1,3` |
| query | N | 메모·카테고리명 부분 검색 |
| sort | N | `date_desc`(기본) `date_asc` `category_asc` `category_desc` `amount_desc` `amount_asc` |

Response `data`:
```json
{ "transactions": [ ], "totalIncome": 2600000, "totalExpense": 320000 }
```
- 서버는 **평면 목록 + 기간 합계**만 반환. 일별/월별/감정별 그룹핑은 프론트 책임.

### POST /api/transactions · 인증 필요
Request:
```json
{
  "type": "EXPENSE",
  "amount": 18600,
  "categoryId": 3,
  "emotionId": 4,
  "memo": "달달한 라떼와 케이크",
  "occurredAt": "2026-07-01T21:30:00"
}
```
- 필수: type, amount(>0 정수), categoryId, emotionId, occurredAt
- memo: 생략 시 **null 저장**(기본 문자열 저장 금지), 최대 200자
- 서버: transactions 저장(단건)

Response(201) `data`: 생성된 거래 객체. 에러: VALIDATION_ERROR

### GET /api/transactions/{transactionId} · 인증 필요 — 거래 객체 반환 (딥링크 대비용, 목록 재사용 가능하면 생략)
### PUT /api/transactions/{transactionId} · 인증 필요 — POST와 동일 필드. 에러: VALIDATION_ERROR·FORBIDDEN·NOT_FOUND
### DELETE /api/transactions/{transactionId} · 인증 필요 → `data`: `{ "deleted": true }` (확인 다이얼로그는 프론트 책임)
### DELETE /api/transactions — 전체 초기화 · 인증 필요 → `data`: `{ "deletedCount": 42 }` (프로필>데이터 관리 전용)

## 7. 목표 (Goals)

### 목표 객체
```json
{
  "goalId": 1,
  "name": "제주도 여행",
  "targetAmount": 2000000,
  "currentAmount": 0,
  "startDate": "2026-07-06",
  "dueDate": "2026-10-31",
  "isMain": true,
  "status": "ACTIVE"
}
```

- `GET /api/goals` – data: `{ "goals": [ ] }` (isMain은 항상 최대 1건, currentAmount는 [초기입력값 + 시작일 이후 누적 순저축액]으로 자동 계산되어 반환됨)
- `POST /api/goals` – name, targetAmount(>0) 필수. currentAmount(초기 모은 돈) 설정 가능. `isMain: true`면 기존 대표 목표를 일반으로 내리고 트랜잭션으로 묶음
- `PUT /api/goals/{goalId}` – POST와 동일 필드
- `DELETE /api/goals/{goalId}` → `data`: `{ "deleted": true }`
- 온보딩 완료: `POST /api/goals`(isMain=true) 성공 → `PATCH /api/users/me/onboarding` 순서 호출

## 8. 요약 (Summary) — 홈 화면용

### GET /api/summary/calendar?year&month · 인증 필요
Response `data`:
```json
{
  "days": [
    { "date": "2026-07-01", "dominantEmotion": { "emotionId": 4, "name": "스트레스", "color": "#A68BEA" }, "transactionCount": 2, "totalExpense": 50600 }
  ]
}
```
- 기록 없는 날짜는 배열에서 생략. 대표 감정 동률 시 최근 기록 우선(초안).

### GET /api/summary/emotions?year&month · 인증 필요
Response `data`:
```json
{
  "emotions":  [ { "emotionId": 4, "name": "스트레스", "count": 6, "amount": 140600 } ],
  "prevMonth": [ { "emotionId": 4, "name": "스트레스", "count": 4, "amount": 98000 } ]
}
```
- 지출 기록 기준 집계. 감정 능선(8종 전체 축)·홈 감정 신호(전월 대비)에 사용.

## 9. 분석·평행우주 (3순위 — 스키마 확정, A3-1)

> 스키마 확정 완료. A3-2(analysis)·A3-3(universe)는 아래 응답 형태를 기준으로 구현한다.
> 집계는 모두 **지출(EXPENSE) 기준**이며, 모든 접근은 인증 주체 user_id 기준.
> **"감정소비"의 정의**: 특정 **한 감정**에 소비가 지나치게 쏠리는 것을 짚어주는 개념이다. 긍정·부정을 가리지 않는다("설렘일 때 유독 많이 썼다"도 감정소비). 특정 부정 감정만 대상으로 삼지 않으며, 모든 지출을 무차별로 보지도 않는다 — **소비가 몰린 그 감정**에 초점을 둔다.
> ⚠️ 제거 확정된 "감정소비 누수율"(비율·점수)은 재도입하지 않는다. universe는 비율 지표가 아니라 **시나리오 비교**로만 표현한다.

### GET /api/analysis/monthly?year&month · 인증 필요

- month 필수. 해당 월의 카테고리·시간대·감정별 지출 집계 + 인사이트 문장.

Response(200) `data`:
```json
{
  "year": 2026,
  "month": 7,
  "totalIncome": 2600000,
  "totalExpense": 320000,
  "byCategory": [
    { "categoryId": 3, "name": "카페", "type": "EXPENSE", "amount": 48000, "count": 6 }
  ],
  "byEmotion": [
    { "emotionId": 4, "name": "스트레스", "color": "#A68BEA", "amount": 140600, "count": 6 }
  ],
  "byTimeSlot": [
    { "slot": "DAWN",      "label": "새벽", "amount": 12000,  "count": 1 },
    { "slot": "MORNING",   "label": "아침", "amount": 30000,  "count": 2 },
    { "slot": "AFTERNOON", "label": "오후", "amount": 88000,  "count": 4 },
    { "slot": "NIGHT",     "label": "밤",   "amount": 190000, "count": 8 }
  ],
  "insights": [
    { "type": "PATTERN", "content": "외로운 밤마다 배달 소비가 반복되고 있어요." }
  ]
}
```
- `byCategory`·`byEmotion`·`byTimeSlot`: 지출 기준 집계(금액 `amount`·건수 `count`). 기록 없는 항목은 배열에서 생략.
- `byEmotion`은 **amount 내림차순** 정렬 → 소비가 가장 몰린 감정이 맨 앞(긍정·부정 무관, "감정소비" 관점의 초점 감정).
- `byTimeSlot.slot`: `occurred_at` 시(hour) 기준 4구간 — `DAWN`(0–5) · `MORNING`(6–11) · `AFTERNOON`(12–17) · `NIGHT`(18–23). `label`은 한글 표기.
- `insights`: `ai_insights` 테이블 매핑(`insight_type`→`type`, `content`→`content`), 0..n건. 문구는 감정 중립(긍정 감정도 대상). 인사이트 생성 로직은 A3-2 소관.

### GET /api/universe/simulation?goalId · 인증 필요

- **goalId 필수**. 해당 목표에 대해 두 미래 시나리오(현재 소비 유지 / 소비를 줄임)를 비교한다.
- 목표 없음·타인 목표: `NOT_FOUND` / `FORBIDDEN`.

Response(200) `data`:
```json
{
  "goal": { "goalId": 1, "name": "제주도 여행", "targetAmount": 2000000, "currentAmount": 300000 },
  "monthlyIncome": 2600000,
  "monthlyExpense": 250000,
  "focusEmotion": { "emotionId": 2, "name": "설렘", "color": "#F28AB7", "monthlyAmount": 120000 },
  "reductionRate": 0.5,
  "scenarios": [
    { "key": "CURRENT", "title": "지금처럼 쓴다면",     "monthlyExpense": 250000, "monthlySaving": 150000, "monthsToGoal": 12, "estimatedAchieveDate": "2027-07", "narration": "지금 속도라면 약 12개월 걸려요." },
    { "key": "REDUCED", "title": "설렘 소비를 줄이면",   "monthlyExpense": 190000, "monthlySaving": 210000, "monthsToGoal": 9,  "estimatedAchieveDate": "2027-04", "narration": "설렘 소비를 절반 줄이면 3개월 빨라져요." }
  ]
}
```
- **감정소비 = 소비가 가장 몰린 한 감정**(긍정·부정 무관)에 초점. REDUCED는 월 지출 전체가 아니라 **그 감정의 지출만** 줄인 시나리오다.
- `focusEmotion`: 해당 기간 지출이 가장 큰 감정 1건 + 그 감정의 월 지출 `monthlyAmount`. 지출 기록이 전혀 없으면 `null`.
- `monthlyIncome`/`monthlyExpense`: 최근 활동 기준 월 수입·지출(산정 방식은 A3-3 구현 소관).
- `reductionRate`: 서버가 가정한 감축 비율(0~1, 예 `0.5`). 응답에 명시해 프론트 하드코딩을 피한다.
- `scenarios`: `CURRENT`(현행)·`REDUCED`(감축) 2건 고정. `REDUCED.title`은 focusEmotion 이름을 반영(예: "설렘 소비를 줄이면").
  - `REDUCED.monthlyExpense = monthlyExpense − round(focusEmotion.monthlyAmount × reductionRate)` (focusEmotion 이 `null`이면 CURRENT 와 동일).
  - `monthlySaving = monthlyIncome − 시나리오 monthlyExpense` (음수면 0 처리).
  - `monthsToGoal = ceil((targetAmount − currentAmount) / monthlySaving)`. `monthlySaving ≤ 0`이면 `monthsToGoal`·`estimatedAchieveDate` 모두 `null`(도달 불가).
- 에러: `NOT_FOUND`(목표 없음) · `FORBIDDEN`(타인 목표) · `VALIDATION_ERROR`(goalId 누락).

## 10. 캐시 무효화 규칙 (프론트 TanStack Query)

| 변경 | invalidate 대상 |
|---|---|
| 기록 생성/수정/삭제/전체 초기화 | transactions, summary/calendar, summary/emotions, analysis, universe |
| 목표 생성/수정/삭제/대표 변경 | goals, universe |
| 프로필/설정 변경 | users/me |

## 11. 구현 우선순위

| 차수 | 범위 |
|---|---|
| 1차 | auth(login/refresh/logout), meta, users/me(조회·수정·온보딩), transactions CRUD·목록 |
| 2차 | goals CRUD, summary 2종, users/me/settings |
| 3차 | analysis, universe, 회원탈퇴, 전체 초기화 |

## 12. 카테고리 설정 (Categories)

### GET /api/categories?type=EXPENSE · 인증 필요
- 인증된 사용자의 공통 카테고리와 커스텀 카테고리를 `category_orders` 순서대로 통합 반환.
- Response `data`:
```json
{
  "categories": [
    { "categoryId": 1, "name": "식비", "type": "EXPENSE", "isCustom": false, "sortOrder": 1 },
    { "categoryId": 4, "name": "해외직구", "type": "EXPENSE", "isCustom": true, "sortOrder": 2 }
  ]
}
```

### POST /api/categories/custom · 인증 필요
- 커스텀 카테고리 추가. 추가 즉시 자동으로 맨 뒤 정렬 순서를 부여.
- Request: `{ "name": "해외직구", "type": "EXPENSE" }`
- Response(201) `data`: 생성된 객체 반환. 에러: `VALIDATION_ERROR`

### DELETE /api/categories/custom/{customCategoryId} · 인증 필요
- 해당 커스텀 카테고리 삭제 (동시에 `category_orders`에서도 제거).
- Response(200) `data`: `{ "deleted": true }`
- 에러: `NOT_FOUND` (없음), `FORBIDDEN` (내 것이 아님)

### PUT /api/categories/order · 인증 필요
- 드래그 앤 드롭 등으로 변경된 카테고리 통합 순서를 일괄 저장.
- Request:
```json
{
  "type": "EXPENSE",
  "orders": [
    { "categoryId": 1, "isCustom": false, "sortOrder": 1 },
    { "categoryId": 4, "isCustom": true, "sortOrder": 2 }
  ]
}
```
- Response(200) `data`: `{ "updated": true }`
