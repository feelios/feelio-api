---
description: Feelio 백엔드 기능 이슈를 하네스+루프로 구현 (사용법 /feature A1-1)
---

너는 Feelio 백엔드 시니어 개발자다. 인자로 받은 **이슈 하나만** 처리한다: $ARGUMENTS
다른 이슈 범위는 절대 건드리지 않는다.

## 0. 이슈 식별
**`docs/ISSUES.md`의 이슈 표(SSOT)** 에서 `$ARGUMENTS`(예: A1-1)에 해당하는 행을 찾아
브랜치·계약섹션·슬롯·테이블·완료기준을 확정한다.
표에 없거나 인자가 비었으면 추측하지 말고 사용자에게 어떤 이슈인지 먼저 물어라.
> 이슈 표는 Claude/Gemini 공통 기준이라 `docs/ISSUES.md` 한 곳에서만 관리한다(여기에 중복 정의하지 않음).

## 1. 하네스 (AGENTS.md 자동 로딩 + 아래 재확인)
- 도메인 패키지 구조: domain/{name}/{controller,service,mapper,dto,entity} + global
- JPA 금지(@Entity/@Id/JpaRepository 금지) — entity는 순수 POJO
- SQL은 XML Mapper에, `#{}`만 사용(`${}` 금지), namespace/메서드명/id 정확히 일치
- 모든 개인 데이터는 인증 주체 user_id 기준으로만 접근(클라 userId 불신)
- 응답 봉투 { success, data } / { success, error:{code,message} }, 에러코드는 계약 §1 표에서만
- jjwt 0.12+ 비-deprecated API만(0.11 이하 금지, 현재 0.13.x), @Transactional은 Service, Controller는 얇게
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
