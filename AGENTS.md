# AGENTS.md — API (Spring Boot)

## 실행 명령어
- 빌드: `./gradlew build`
- 실행: `./gradlew bootRun`
- 테스트: `./gradlew test`
> 작업 종료 전 `./gradlew test`가 통과해야 한다.

## 기술 스택 (버전 고정)
- Java 21 + Spring Boot 4.0.x
- Spring Security 6 + JWT (jjwt 0.12.x — 0.11 이하 deprecated API 금지)
- 데이터 접근: MyBatis (mybatis-spring-boot-starter 3.x) + MySQL 8
- 빌드: Gradle
> JPA(Spring Data JPA, @Entity, JpaRepository)를 절대 사용하지 않는다.
> SQL은 직접 작성하며 ORM 자동 쿼리에 의존하지 않는다.

## 패키지 구조 (도메인형)
- domain/user/
  - controller/  — UserController
  - service/     — UserService
  - mapper/      — UserMapper (인터페이스, @Mapper)
  - dto/         — 요청/응답 DTO
  - entity/      — DB 행 매핑 클래스 (순수 POJO, JPA 아님)
- global/        — 공통 설정, 예외처리, 응답 포맷
- SQL XML: src/main/resources/mapper/UserMapper.xml
> entity는 DB 행을 담는 순수 자바 클래스다. @Entity·@Id·@Column 등 JPA 어노테이션 금지.
> Mapper 인터페이스와 XML의 namespace/메서드명/id는 정확히 일치시킨다.

## MyBatis 규칙 (반드시 지킬 것)
- SQL은 XML Mapper에 작성한다 (간단한 단건만 어노테이션 허용)
- 파라미터 바인딩은 #{} 만 사용한다 (${} 는 SQL 인젝션 위험 — 원칙적으로 금지)
- snake_case ↔ camelCase 자동 매핑 사용
  → application.yml: mybatis.configuration.map-underscore-to-camel-case: true
- 컬럼/별칭이 복잡하면 <resultMap>을 명시한다
- 조건/반복은 <if>, <foreach>, <choose> 동적 SQL로 처리한다
- 비즈니스 로직은 Service에, Mapper는 순수 데이터 접근만 담당

## 코딩 컨벤션
- entity(DB 행)와 응답 DTO를 분리한다 (DB 구조를 그대로 API에 노출 금지)
- Controller는 얇게, 트랜잭션은 Service에 @Transactional로 건다

## API 응답 규약
- 성공: { "success": true, "data": {...} }
- 실패: { "success": false, "error": { "code": "...", "message": "..." } }
- 명세는 docs/API-CONTRACT.md를 단일 기준으로 삼고, 변경 시 이 파일을 먼저 수정한다

## 공통 규칙 (web 레포와 동일) — Part 4 참조

## 에이전트 행동 규칙
- DB 스키마/테이블 변경은 먼저 보고하고 승인받는다
- application.yml의 시크릿/DB 비밀번호를 만지지 않는다
- 한 번에 하나의 도메인만 작업한다
- SQL을 작성하면 어떤 쿼리를 왜 그렇게 짰는지 한 줄로 설명한다

## 설정·시크릿 규칙
- 설정은 두 파일로 분리한다.
  - `application.yaml` (커밋 대상): 비밀 아닌 값 + `spring.config.import: optional:secret.yaml`.
    비밀값 자리는 `${DB_PASSWORD}` 식 플레이스홀더로만 참조한다.
  - `secret.yaml` (**.gitignore, 커밋 금지**): DB 접속·JWT secret·OAuth client-secret 등 실제 값만.
> 시크릿 실제 값을 커밋 대상 파일에 하드코딩하지 않는다.

## 인증·데이터 접근 (확정)
- 로그인은 **소셜 로그인 전용**(Google/Kakao/Naver). 이메일/비밀번호 로그인 금지.
- OAuth는 **A안**: 프론트가 인가 코드(code)를 전달 → 백엔드가 client_secret으로 서버-투-서버 교환·검증한다.
  provider access token은 브라우저에 노출하지 않는다. (계약 §3 기준)
- 모든 개인 데이터는 인증 주체 **user_id 기준으로만** 조회·변경한다(클라이언트가 보낸 userId는 신뢰하지 않음).

## 확정 제거 기능 (구현 금지)
- 감정소비 누수율 / 상황(situation) / 이메일·비밀번호 로그인 관련 API·필드·테이블을 만들지 않는다.
> 계약(docs/API-CONTRACT.md)에 없는 엔드포인트·필드는 임의로 생성하지 않는다.

## 작업 진행 방식 (루프)
기능 이슈는 아래 루프로 진행한다. 한 단계씩, 검증을 통과하며 수렴시킨다.
1. 현재 코드 + 먼저 완성된 유사 도메인 패턴 분석
2. `docs/API-CONTRACT.md` 해당 섹션을 인용해 요구사항 확정
3. 애매하면 먼저 질문(아래 '질문 우선 원칙')
4. 계획(건드릴 파일 목록)을 제시하고 멈춰 승인 대기
   (단, 오타·한 줄·문구 수정 등 단순 이슈는 계획 생략하고 바로 구현 가능)
5. 슬롯 순서로 구현: Controller → Service → DTO → Entity → Mapper + XML
6. `./gradlew test` 검증
7. 실패 시 원인(규칙위반/계약불일치/논리오류) 분류 → 최소 수정 → 재검증
8. 변경 파일 정리 후 보고
> 검증(test) 통과 전에는 "완료"라고 하지 않는다.
> 한 이슈 = 한 브랜치 = 한 PR. 이슈 범위 밖 파일은 만들지도 고치지도 않는다.
> 테스트는 **Service 단위를 기본**으로 한다(Controller 슬라이스·통합 테스트는 필요할 때만).

## 질문 우선 원칙 (추측 금지)
다음 중 하나라도 불명확하면 **코드 작성 전에 먼저 질문한다.**
- API 경로가 계약서에 있는지 / 신규인지
- 인증 주체(user_id) 기준으로 어디까지 조회·변경하는지
- DB 테이블·컬럼 구조 (특히 확정 전 스키마)
- 응답이 공통 봉투 `{success,data|error}`를 따르는지
- 예외 처리 방식 (어떤 에러코드로 매핑할지)
- 프론트-백 요청/응답 형식이 맞는지
- 테스트 범위 (기본 Service 단위 — 이 이슈는 더 넓혀야 하는지)
- 새 파일 생성 vs 기존 파일 수정

## 공통 협업 규칙

### 브랜치 전략 (GitHub Flow)
- main 브랜치는 항상 동작하는 상태로 유지 (직접 push 금지)
- 모든 작업은 feature 브랜치 → PR로 머지
- 브랜치 이름: feat/login, fix/token-expire, docs/readme

### 커밋 메시지 (Conventional Commits)
- 형식: type: 한 줄 요약
- type: feat / fix / docs / refactor / test / chore
- 예: "feat: 로그인 API 연동", "fix: 토큰 만료 시 401 처리"

### PR 규칙
- PR 제목도 커밋 규칙을 따른다
- 본문에 "무엇을 / 왜 / 어떻게 테스트했는지" 작성
- 팀 협업 구간: 최소 1명 리뷰 승인 후 머지, 자기 코드는 자기가 머지하지 않는다 (리뷰어가 머지)
- 혼자 작업 구간: 셀프 머지 허용 (단, main 직접 push 금지 — 반드시 브랜치 → PR을 거친다)