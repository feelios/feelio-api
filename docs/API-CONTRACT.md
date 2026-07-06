# Feelio API-CONTRACT v1

> **이 문서가 프론트(feelio-web)와 백엔드(feelio-api)의 유일한 API 계약이다.**
> 여기 없는 엔드포인트·필드를 임의로 만들지 않는다. 변경이 필요하면 **코드보다 이 문서를 먼저 수정하고 팀에 공유**한 뒤 구현한다. 양쪽 repo의 이 파일은 항상 동일하게 유지한다.
> 근거: 기획 문서 STEP7(DB 설계서)·STEP8(API 명세서)·STEP9(화면-API 워크플로우)
> ⚠️ 로그인은 **소셜 로그인(Google/Kakao/Naver) 전용**이다. 이메일/비밀번호 로그인은 만들지 않는다 (팀 확정).

- Base URL: `/api` (프론트는 `VITE_API_BASE_URL` 환경변수 사용)
- 인증: 보호 API는 `Authorization: Bearer <accessToken>` 헤더 필수
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
- **상황:** 퇴근 후, 혼자 있음, 친구와, 보상, 습관, 이동 중, 아침, 밤
- 감정 색상·정렬의 원본은 웹 `src/styles/theme.js`의 emotionPalette → DB emotions 테이블 시드로 이관
- ⚠️ **감정소비 누수율 관련 API·필드는 만들지 않는다 (제거 확정 기능)**

## 3. 인증 (Auth)

### POST /api/auth/login — 소셜 로그인 · 인증 불필요

Request:
```json
{ "provider": "GOOGLE", "providerToken": "<OAuth로 받은 토큰>" }
```
- provider: `GOOGLE` | `KAKAO` | `NAVER`

Response(200) `data`:
```json
{
  "accessToken": "jwt-access-token",
  "refreshToken": "jwt-refresh-token",
  "user": {
    "userId": 1,
    "nickname": "서연",
    "email": "user@example.com",
    "profileImageUrl": "https://.../photo.jpg",
    "provider": "GOOGLE",
    "onboardingDone": false,
    "themeMode": "LIGHT",
    "auroraTheme": "블루"
  }
}
```
- 서버: providerToken 검증 → 제공자 프로필(식별자·이메일·닉네임·**프로필 이미지**) 수신 → `(provider, provider_user_id)` 조회, 없으면 신규 가입(users + social_accounts + notification_settings 기본값 + terms_agreements)
- 에러: INVALID_PROVIDER(400), UNAUTHORIZED(401)

### POST /api/auth/token/refresh — 토큰 재발급 · 인증 불필요

Request: `{ "refreshToken": "..." }`
Response(200) `data`: `{ "accessToken": "...", "refreshToken": "..." }`
- 에러: UNAUTHORIZED(401) → 프론트는 로그인 화면으로

### POST /api/auth/logout — 로그아웃 · 인증 필요

Request 없음 → Response(200) `data`: `{ "loggedOut": true }`
- 서버: refresh_token 폐기. **users.onboarding_done은 유지** (재로그인 시 온보딩 재표시 없음 — 팀 확정)

## 4. 사용자 (Users)

### GET /api/users/me · 인증 필요
Response `data`: login 응답의 `user` 객체와 동일 구조.

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
  "categories": [ { "categoryId": 3, "name": "카페", "type": "EXPENSE", "sortOrder": 3 } ],
  "situations": [ { "situationId": 1, "name": "퇴근 후", "sortOrder": 1 } ]
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
  "situations": [ { "situationId": 1, "name": "퇴근 후" }, { "situationId": 2, "name": "혼자 있음" } ],
  "memo": "달달한 라떼와 케이크",
  "occurredAt": "2026-07-01T21:30:00"
}
```
- type: `EXPENSE` | `INCOME` / 감정·카테고리는 단일, **상황은 복수(N:M)** — 팀 확정

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
  "situationIds": [1, 2],
  "memo": "달달한 라떼와 케이크",
  "occurredAt": "2026-07-01T21:30:00"
}
```
- 필수: type, amount(>0 정수), categoryId, emotionId, occurredAt
- situationIds: 생략·빈 배열 허용 / memo: 생략 시 **null 저장**(기본 문자열 저장 금지), 최대 200자
- 서버: transactions + transaction_situations **단일 트랜잭션** 저장

Response(201) `data`: 생성된 거래 객체. 에러: VALIDATION_ERROR

### GET /api/transactions/{transactionId} · 인증 필요 — 거래 객체 반환 (딥링크 대비용, 목록 재사용 가능하면 생략)
### PUT /api/transactions/{transactionId} · 인증 필요 — POST와 동일 필드, situationIds는 전량 교체. 에러: VALIDATION_ERROR·FORBIDDEN·NOT_FOUND
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

- `GET /api/goals` → `data`: `{ "goals": [ ] }` (isMain은 항상 최대 1건)
- `POST /api/goals` — name, targetAmount(>0) 필수. `isMain: true`면 기존 대표 목표를 서버가 같은 트랜잭션에서 해제
- `PUT /api/goals/{goalId}` — POST와 동일 필드
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

## 9. 분석·평행우주 (3순위 — 스키마 미확정)

- `GET /api/analysis/monthly?year&month` — 카테고리·시간대·감정 집계 + 인사이트 문장
- `GET /api/universe/simulation?goalId` — 두 미래 시나리오 수치·내레이션
- **응답 스키마 확정 전 구현 금지. 확정하면 이 문서를 먼저 갱신한다.**

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
