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
- 최소 1명 리뷰 승인 후 머지
- 자기 코드는 자기가 머지하지 않는다 (리뷰어가 머지)