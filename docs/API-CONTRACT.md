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
4. 서버가 자체 JWT(`accessToken` 1h=3600s, `refreshToken` 14d=1209600s)를 **HttpOnly 쿠키**로 구운 뒤 프론트 URL로 리다이렉트한다.

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
  "totalAsset": 1000000,
  "onboardingDone": false,
  "themeMode": "LIGHT",
  "auroraTheme": "블루"
}
```

- `totalAsset`: **저장된 값이 아니라 계산값이다.** `users.total_asset`(온보딩 입력값)에 거래 변화량(수입 합계 − 지출 합계)을 더해 응답한다.
  - 거래를 수정·삭제해도 값이 어긋나지 않도록 조회 시점에 산출한다(A6-3 목표 달성액과 같은 방식).
  - 저축은 `EXPENSE` 라 차감된다. 홈 화면이 "목표와 별개인 나의 자산"으로 안내하고 목표 적립액을 따로 표시하므로, 차감해야 이중계산이 되지 않는다.
  - 온보딩 전이라 초기값이 없으면 0으로 보고 거래 변화량만 반영한다(음수 가능).

### PATCH /api/users/me · 인증 필요
Request: `{ "nickname": "새닉네임" }` (1~8자) → Response `data`: 갱신된 user 객체. 에러: VALIDATION_ERROR

### PATCH /api/users/me/onboarding — 온보딩 완료 처리 (A6-1)
Request: `{ "totalAsset": 1000000 }` (총자산 금액, 필수)
Response `data`: `{ "onboardingDone": true }`

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
- `occurredAt`·`createdAt` 등 모든 시각은 **오프셋 없는 한국 로컬 시각**이다(`"2026-07-01T21:30:00"`). UTC 로 변환해 보내지 않는다.
  - 프론트는 사용자가 고른 벽시계 값을 그대로 보낸다. `toISOString()` 을 태우면 9시간이 밀린다.
  - 서버도 같은 시계를 봐야 한다. 컨테이너가 UTC 로 뜨면 `@PastOrPresent` 검증이 방금 한 기록도
    미래로 판정해 거부한다. API 컨테이너는 `TZ=Asia/Seoul` 로 고정한다(#283).

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
  "occurredAt": "2026-07-01T21:30:00",
  "goalId": 1
}
```
- 필수: type, amount(>0 정수), categoryId, emotionId, occurredAt
- type: `EXPENSE` | `INCOME` 둘 중 하나
- memo: 생략 시 null 저장, 최대 200자
- goalId: 선택(null 허용)
- 서버: transactions 저장(단건)

본문이 가리키는 ID 는 저장 전에 검증하고, 어긋나면 **VALIDATION_ERROR(400)** 로 답한다 (#195).
DB 제약에 맡기면 FK 위반이 500 으로 새어나가 프론트가 원인을 알 수 없다.
- categoryId: 없거나 비활성이거나 **다른 사용자의 커스텀 카테고리**면 거부
- categoryId 의 type 과 거래 type 이 다르면 거부 (지출에 수입 카테고리 금지)
- emotionId: 없거나 비활성이면 거부
- goalId: 없거나 **타인의 목표**면 거부 — 404 가 아니라 400 이다.
  본문 값이 잘못된 것이지 요청한 리소스가 없는 게 아니다.

Response(201) `data`: 생성된 거래 객체. 에러: VALIDATION_ERROR

### GET /api/transactions/{transactionId} · 인증 필요 — 거래 객체 반환 (딥링크 대비용, 목록 재사용 가능하면 생략)
### PUT /api/transactions/{transactionId} · 인증 필요 — POST와 동일 필드. 에러: VALIDATION_ERROR·FORBIDDEN·NOT_FOUND
### DELETE /api/transactions/{transactionId} · 인증 필요 → `data`: `{ "deleted": true }` (확인 다이얼로그는 프론트 책임)
### DELETE /api/transactions → 지출 초기화 (토큰 필요) · `data`: `{ "deletedCount": 42 }` (모달 추가 예정)

### GET /api/transactions/patterns → 반복 소비 패턴 조회 (토큰 필요)
Response `data`:
```json
{
  "pattern": {
    "count": 5,
    "title": "우울일 때 배달 지출 패턴",
    "emotion": "우울",
    "category": "배달",
    "time": "밤",
    "desc": "스트레스로 인한 야식이 잦네요."
  },
  "evidence": [
    {
      "transactionId": 12,
      "type": "EXPENSE",
      "amount": 25000,
      "occurredAt": "2026-08-01T21:00:00",
      "emotion": { "emotionId": 1, "name": "우울", "color": "#123" },
      "category": { "categoryId": 2, "name": "배달" }
    }
  ]
}
```
- 가장 자주 반복되는(동일 감정+동일 카테고리+동일 시간대) 지출 패턴 1건과 그 원본 거래 내역(evidence)을 반환.
- 비동기 캐싱(A9-4, A9-5)을 통해 패턴 내역(evidence)은 루트에서 바로 사용 가능.
- 조건 불충족 시 `pattern.count: 0` 및 빈 `evidence` 배열 반환.

### GET /api/transactions/dutch-pay/pending · 인증 필요 — 미정산 더치페이 목록 조회 (F11-4)
Response `data`:
```json
{
  "transactions": [
    {
      "transactionId": 12,
      "type": "EXPENSE",
      "amount": 40000,
      "category": { "categoryId": 99, "name": "더치페이" },
      "emotion": { "emotionId": 7, "name": "평온", "color": "#83C9B0" },
      "memo": "고기집 N빵 대납",
      "occurredAt": "2026-07-20T19:00:00",
      "isSettled": false
    }
  ]
}
```
- 지출 내역 중 카테고리가 '더치페이'이면서 `is_settled = false`인 내역 반환.

### PATCH /api/transactions/{transactionId}/settle · 인증 필요 — 더치페이 정산 완료 처리 (F11-4)
Request: 바디 없음
Response(200) `data`: `{ "settled": true, "newIncomeTransactionId": 13 }`
- 서버 내부 로직: 
  1. 원본 지출의 `is_settled = true` 업데이트
  2. 원본 금액만큼의 `INCOME` 타입, '정산금' 카테고리 신규 거래 내역 자동 생성
  3. 유저의 `totalAsset` 해당 금액만큼 증가
- 에러: FORBIDDEN(타인 거래), NOT_FOUND, VALIDATION_ERROR(이미 정산된 거래 등)

### PATCH /api/transactions/{transactionId}/merge · 인증 필요 — 정산받은 금액 병합 처리 (A6-5)
Request:
```json
{
  "receivedAmount": 15000
}
```
- 필수: receivedAmount(>=0 정수)

Response(200) `data`: 병합된 거래 객체
- 서버 내부 로직:
  1. 원본 지출의 `is_settled = false` 여부 확인
  2. 원본 금액에서 `receivedAmount`를 차감한 `finalAmount` 계산
  3. `finalAmount <= 0`일 경우 `finalAmount`를 0으로 설정
  4. 금액을 `finalAmount`로 업데이트하고 `is_settled = true`로 변경 후 객체 반환
- 에러: FORBIDDEN(타인 거래), NOT_FOUND, VALIDATION_ERROR(이미 정산 완료된 건 등)

## 7. 목표 (Goals)

### 목표 객체
```json
{
  "goalId": 1,
  "name": "제주도 여행",
  "targetAmount": 2000000,
  "currentAmount": 0,
  "initialAmount": 0,
  "startDate": "2026-07-06",
  "dueDate": "2026-10-31",
  "isMain": true,
  "status": "ACTIVE"
}
```

- `GET /api/goals` – data: `{ "goals": [ ] }` (isMain은 항상 최대 1건, currentAmount는 [initialAmount + 해당 goal_id로 기록된 거래액 SUM]으로 자동 계산되어 반환됨)
- `POST /api/goals` – name, targetAmount(>0), dueDate 필수. initialAmount(초기 모은 돈) 설정 가능(기본 0). `isMain: true`면 기존 대표 목표를 일반으로 내림
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

### GET /api/summary/ai-comment · 인증 필요

Response `data`:
```json
{ "comment": "이번 달은 지난달보다 지출이 줄었어요. 지금의 흐름을 편안하게 이어가 보세요." }
```

- 조회일 기준 이번 달 총지출과 전월 총지출을 비교한 홈 소비 총평이다.
- 홈의 캘린더·감정 요약과 분리 호출하여 AI 지연이 홈 화면 로딩을 막지 않게 한다.
- 사용자별 하루 1회 생성하며 DB에 저장하지 않는다. 서버 재시작 시 당일에도 다시 생성될 수 있다.
- 당월 지출이 없거나 GPT가 실패·지연·빈 응답을 반환하면 `comment`는 `null`이며 API 자체는 200 성공한다.

### GET /api/summary/mallang-comment · 인증 필요

Response `data`:
```json
{
  "evaluation": "이번 달 320,000원 썼어. 예산의 78%야.",
  "encouragement": "이번 주는 배달을 두 번만 시켜볼까?",
  "status": "WARNING"
}
```

- 홈 말랑이가 건네는 코멘트다. `evaluation`(현황 평가)과 `encouragement`(다음 행동 독려) 두 문장으로 나뉜다.
- `evaluation`에는 **근거 수치를 최소 1개 포함**한다. 예산을 산출할 수 있으면 `지출액 + 소진율`, 없으면 `지출액`만 쓴다.
- `status`: `ZERO`(지출 없음) · `SAVING`(소진율 70% 미만) · `WARNING`(70% 이상 90% 미만) · `OVER`(90% 이상) · `NO_BUDGET`(활성 목표·전월 기록이 없어 소진율 산출 불가). 말랑이 표정·색을 고르는 데 쓴다.
- **판정(`status`)은 서버 자바 계산이며 AI가 바꾸지 않는다.** AI는 문장만 만든다. 수치도 집계값을 그대로 쓴다.
- 예산은 §9 `GET /api/analysis/budget`과 같은 로직(A6-4 동적 예산)으로 구한다.
- 기존 `GET /api/summary/ai-comment`(전월 대비 총평)와 별개 엔드포인트다. 서로 대체하지 않는다.
- 사용자별 하루 1회 생성하며 DB에 저장하지 않는다. 서버 재시작 시 당일에도 다시 생성될 수 있다.
- AI 비활성화·실패·타임아웃·빈 응답이면 규칙기반 문장으로 채워 응답한다. **`evaluation`·`encouragement`는 항상 비어 있지 않으며 API는 200 성공한다.**

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
- `byEmotion`은 **count 내림차순**, 건수가 같을 때만 **amount 내림차순**으로 정렬한다 → 가장 자주 소비한 감정이 맨 앞(긍정·부정 무관, "감정소비" 관점의 초점 감정).
  - 이 순위가 답하는 질문은 "**어떤 기분으로 자주 소비했나**"다. 금액이 아니라 빈도가 기준이므로,
    3건 쓴 감정이 1건이지만 금액이 큰 감정에게 밀리지 않는다. 금액은 건수가 같을 때의 동률 판정에만 쓴다.
  - 프론트도 같은 규칙으로 그린다. 반올림한 비율(percent)로 비교하면 건수가 다른데도 동률이 되어
    금액으로 뒤집히므로, 정렬은 반드시 **원시 건수**로 한다.
- `byTimeSlot.slot`: `occurred_at` 시(hour) 기준 4구간 — `DAWN`(0–5) · `MORNING`(6–11) · `AFTERNOON`(12–17) · `NIGHT`(18–23). `label`은 한글 표기.
- `insights`: `ai_insights` 테이블 매핑(`insight_type`→`type`, `content`→`content`), 0..n건. 문구는 감정 중립(긍정 감정도 대상).
  - 저장본이 있으면 그대로 반환하고, 없을 때만 생성해 저장한다. **지난 달 이전은 영구 캐시**, 이번 달만 `feelio.insight.ttl-hours`(기본 6) 경과 시 재생성한다.
  - 생성기는 `feelio.insight.provider`로 전환한다(`rule` 기본 · `gpt`). GPT 실패·타임아웃 시 규칙기반 결과로 폴백하므로 이 필드 때문에 응답이 실패하지 않는다.

### GET /api/analysis/ai-insights · 인증 필요

- 파라미터 없음. **호출 시점의 당월** 집계로 만든다. 항상 인증 주체 user_id 기준.

Response(200) `data`:
```json
{
  "aiQuickInsights": [
    { "label": "위험 루트",      "value": "새벽 · 무덤덤 · 패션/미용",       "note": "10건",              "color": "var(--sub)", "type": "default" },
    { "label": "팩트 리포트",    "value": "이번 달 지출 2,366,868원",        "note": "전월 대비 +32%",     "color": "#E87573",    "type": "fact"    },
    { "label": "소비 위험도",    "value": "보통",                          "note": "전월과 비슷한 수준",  "color": "#E87573",    "type": "risk"    },
    { "label": "AI 맞춤 챌린지", "value": "새벽에 '무덤덤' 소비 3일 참아보기", "note": "이번 주",           "color": "var(--sub)", "type": "default" }
  ],
  "emotionCards": [
    { "emotion": "무덤덤", "title": "'무덤덤'일 때의 소비", "desc": "2건, 1,579,394원 썼어요. 이번 달 지출의 67%예요." }
  ],
  "evidence": [],
  "pattern": { "count": 0, "title": null, "emotion": null, "category": null, "time": null, "desc": null }
}
```
- `aiQuickInsights`: **4건 고정**, 순서도 고정(위험 루트 → 팩트 리포트 → 소비 위험도 → AI 맞춤 챌린지).
  `label`·`color`·`type`은 프론트 표시 규격이므로 서버가 위 값 그대로 내려준다(`type`: `default`·`fact`·`risk` — `risk`는 신호등 UI).
  화면 배치는 `label`이 좌상단 캡션, `note`가 우상단 태그, `value`가 본문 한 줄이다.
- **지출 기록이 없으면 `aiQuickInsights`·`emotionCards` 모두 빈 배열**을 반환한다. 빈 상태 표시는 프론트 책임.
- `위험 루트`: 지출이 가장 큰 `시간대 · 감정 · 카테고리`를 잇는다. `note`는 해당 시간대 건수.
- `팩트 리포트`: 당월 지출 총액. `note`는 전월 대비 증감률(전월 지출이 0이면 `"전월 기록 없음"`).
- `소비 위험도`: **예산 소진율** 기준 `위험`(90% 이상) · `주의`(70% 이상 90% 미만) · `안전`(70% 미만, 지출 0원 포함).
  예산을 산출할 수 없으면(활성 목표 없음 등) `예산 미설정` — 비율 판정 자체가 불가능하다.
  `note`는 `"예산의 N% 사용"`, 지출 0원이면 `"이번 달 지출 없음"`, 예산 미설정이면 `"목표를 정하면 예산이 잡혀요"`.
  프론트 신호등은 이 `value`로 불을 고른다(위험=Red · 주의=Yellow · 안전=Green). `예산 미설정`은 등급이 아니라 판정 불가라 세 칸 모두 꺼진다.
- `emotionCards`: `byEmotion` **상위 3건까지**, 같은 순서. 감정 카드 뒷면 문구이며 앞면(감정명·비율·금액)은 프론트가 §9 `byEmotion`으로 그린다.
- `evidence`·`pattern`: 이 응답에서는 사용하지 않는다(빈 배열 / `count: 0`). 프론트는 `GET /api/transactions/patterns`에서 받아간다.

### GET /api/analysis/ai-report · 인증 필요

- 파라미터 없음. 호출 시점의 당월 지출과 동적 예산으로 계산한다.
- 숫자 필드(`totalExpense`·`totalBudget`·`budgetUsageRate`·`consumptionRisk`)는 자바 계산이라 AI를 타지 않는다.
  반면 `ai.fact`·`ai.challenge`·`ai.emotion` 세 문장은 A7-5/6/7에서 GPT가 연동됐다(아래 항목별 설명 참고).

Response(200) `data`:
```json
{
  "year": 2026,
  "month": 8,
  "totalExpense": 720000,
  "totalBudget": 1000000,
  "budgetUsageRate": 72.0,
  "consumptionRisk": "YELLOW",
  "ai": {
    "fact": "이번 달 카페 지출 폼 미쳤다, 지갑도 카페인 과다 섭취 중이네.",
    "challenge": "이번 주 배달은 2번까지만 주문하기",
    "emotion": "① 발견: 스트레스를 느낄 때 밤 시간대 배달 소비가 두드러졌어요. ② 의미: 지친 마음을 빠르게 달래려는 선택이었을 수 있어요. ③ 조언: 주문 전 따뜻한 물을 마시며 5분만 마음을 살펴보세요."
  }
}
```

- `consumptionRisk`: 예산 소진율 90% 이상 `RED`, 70% 이상 90% 미만 `YELLOW`, 70% 미만 `GREEN`.
- 지출이 없거나 예산을 산출할 수 없으면 `budgetUsageRate: 0.0`, `consumptionRisk: GREEN`이다.
- `ai.fact`: 예산 상태·당월 지출·최대 지출 카테고리를 반영한 MZ 팩트 폭격기 한 문장이다.
  `feelio.insight.provider=gpt`일 때 GPT를 호출하고, 비활성화·실패·타임아웃·빈 응답 시 `"팩트 분석을 준비 중이에요."`로 폴백한다.
- `ai.challenge`: 오늘을 포함한 최근 7일의 카테고리별 지출(금액·건수)을 반영한 측정 가능한 미션 한 문장이다.
  `feelio.insight.provider=gpt`일 때 GPT를 호출하고, 기록 없음·비활성화·실패·타임아웃·빈 응답 시 `"맞춤 챌린지를 준비 중이에요."`로 폴백한다.
- `ai.emotion`: 당월 감정별 지출 상위 3건과 최대 지출 카테고리·시간대를 반영하며
  `① 발견: ... ② 의미: ... ③ 조언: ...` 순서의 3단계 한 문장이다.
  `feelio.insight.provider=gpt`일 때 GPT를 호출하고, 기록 없음·비활성화·실패·타임아웃·형식 오류 시 `"감정 소비 분석을 준비 중이에요."`로 폴백한다.

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
