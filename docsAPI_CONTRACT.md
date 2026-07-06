# API CONTRACT v1

## POST /api/auth/login
요청: { "email": "string", "password": "string" }
응답(200): { "success": true, "data": { "accessToken": "string", "user": {...} } }
실패(401): { "success": false, "error": { "code": "INVALID_CREDENTIALS", "message": "..." } }

## GET /api/users/me  (인증 필요)
헤더: Authorization: Bearer <accessToken>
응답(200): { "success": true, "data": { "id": 1, "email": "...", "name": "..." } }