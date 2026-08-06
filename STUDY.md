# STUDY — study-fine 학습 가이드

> 이 문서는 **면접에서 이 프로젝트를 방어하기 위한 공부 자료**다.
> "코드가 이렇게 생겼다"가 아니라 **"이 기술이 뭐고, 왜 이걸 골랐고, 핵심 원리가 뭔지"** 를 신입 눈높이에서 설명한다.
> 이 워크스페이스의 **첫 Spring 프로젝트**이므로, Java/Spring 생태계 기초부터 다시 깐다.

---

## 목차

1. [프로젝트 요약](#1-프로젝트-요약)
2. [아키텍처와 데이터 흐름](#2-아키텍처와-데이터-흐름)
3. [사용 기술 해설 (신입 눈높이)](#3-사용-기술-해설-신입-눈높이)
4. [핵심 설계 결정 딥다이브](#4-핵심-설계-결정-딥다이브)
5. [실제로 겪은 함정 10선 (면접 스토리 원재료)](#5-실제로-겪은-함정-10선-면접-스토리-원재료)
6. [검수에서 발견된 버그 3건 해부](#6-검수에서-발견된-버그-3건-해부)
7. [남아 있는 한계 (알고 남긴 것)](#7-남아-있는-한계-알고-남긴-것)
8. [학습 로드맵 체크리스트](#8-학습-로드맵-체크리스트)
9. [직접 해보는 실습 과제](#9-직접-해보는-실습-과제)

---

## 1. 프로젝트 요약

### 한 문장

**스터디모임의 회차별 출석을 기록하면, 그 시점의 벌금 단가로 벌금이 자동 계산되어 "확정 저장"되는 출석·벌금 관리 앱.**

### 무엇을 왜 만들었나

소규모 스터디는 지각·결석에 벌금을 매기지만, 운영은 대개 카톡방 + 엑셀 + 사람의 기억이다. 여기서 세 가지가 반복적으로 깨진다.

| 깨지는 것     | 현실                                                      | study-fine의 해결                                                 |
| ------------- | --------------------------------------------------------- | ----------------------------------------------------------------- |
| **집계 누락** | 누가 몇 번 지각했는지 운영자 한 명의 수기 기록에만 남는다 | 회차 × 멤버 단위로 DB에 기록. 누적 벌금은 집계 쿼리로 항상 산출   |
| **계산 실수** | 벌금 단가가 바뀌면 지난 회차까지 잘못 소급 계산된다       | **벌금 스냅샷** — 기록 시점 금액을 확정 저장. 소급 오염 경로 차단 |
| **불투명성**  | 멤버는 자기 벌금이 왜 그 금액인지 근거를 확인할 수 없다   | 멤버 전용 조회(`GET /api/me/attendances`)로 회차별 근거 제공      |

### 이 프로젝트의 "핵심 가치" 3줄 (면접에서 이걸 먼저 말한다)

1. **벌금은 파생 계산값이 아니라 "그 시점에 확정된 사실"** 이라는 도메인 판단을 코드 구조로 강제했다.
2. **N+1을 세 가지 다른 방식**(GROUP BY projection / `@EntityGraph` / 사전 조회 + Map)으로 지점별로 막았다.
3. **권한 격리를 "비교문"이 아니라 "URL 구조"로** 했다 — 실수로 비교를 빠뜨릴 여지 자체를 없앴다.

### 스펙 한눈에

| 항목        | 값                                                                          |
| ----------- | --------------------------------------------------------------------------- |
| 유형 태그   | **CRUD** (워크스페이스 커버리지 공백이던 유형)                              |
| 난이도 티어 | **S** (하루 완성)                                                           |
| 백엔드      | Java 21 · Spring Boot 4.1.0 · Maven · Spring Data JPA(Hibernate 7) · Flyway |
| 인증        | Spring Security 7 + OAuth2 Resource Server + Nimbus JWT (HS256)             |
| 프론트      | Vite · React 19 · TypeScript · Tailwind v4 · Zustand · TanStack Query · Zod |
| DB          | Neon Postgres (테이블 4개)                                                  |
| API         | 엔드포인트 15개, 전부 `ProblemDetail`(RFC 9457) 에러 포맷                   |
| 테스트      | 28개 (벌금 계산 · 권한 가드 · bulk upsert 비활성 멤버 규칙)                 |
| 배포        | 프론트 Vercel 라이브 · **백엔드 Render 라이브(Docker, 512MB 무료 티어)**    |

---

## 2. 아키텍처와 데이터 흐름

### 2.1 시스템 구성도

```mermaid
graph TB
    subgraph Browser["브라우저"]
        UI["React SPA<br/>Vite + TS + Tailwind v4"]
    end

    subgraph Vercel["Vercel (라이브)"]
        Static["정적 번들<br/>index.html + js/css"]
    end

    subgraph Render["Render (라이브, Docker 512MB)"]
        API["Spring Boot 4 API :8080<br/>-Xmx256m -XX:MaxMetaspaceSize=180m"]
    end

    subgraph Neon["Neon Postgres"]
        DB[("study_fine DB<br/>4 tables")]
    end

    UI -->|"HTTPS 정적 자산"| Static
    UI -->|"REST + Authorization: Bearer JWT"| API
    API -->|"JDBC / HikariCP 커넥션 풀"| DB
    API -.->|"기동 시 Flyway 마이그레이션"| DB
```

**읽는 법.** 프론트와 백엔드는 완전히 분리된 두 서버다. 브라우저는 Vercel에서 HTML/JS를 받아 실행하고, 그 JS가 별도 도메인(Render)의 API를 호출한다. 도메인이 다르므로 **CORS** 설정이 필수고, 로그인 상태는 쿠키가 아니라 **JWT 토큰**으로 유지한다.

### 2.2 백엔드 레이어 구조

```mermaid
graph LR
    C["Controller<br/>@RestController"] --> S["Service<br/>@Service, @Transactional"]
    S --> D["Domain<br/>Entity + FinePolicy"]
    S --> R["Repository<br/>Spring Data JPA"]
    R --> DB[("Postgres")]
```

| 레이어         | 하는 일                                                                   | 절대 안 하는 일                  |
| -------------- | ------------------------------------------------------------------------- | -------------------------------- |
| **Controller** | 요청 DTO 검증(`@Valid`), 인증 주체 주입, 권한 선언(`@PreAuthorize`), 변환 | 비즈니스 규칙, 엔티티 직접 노출  |
| **Service**    | `@Transactional` 경계, 유스케이스 조합, 도메인 규칙 호출                  | HTTP 타입(`ResponseEntity`) 참조 |
| **Domain**     | 엔티티 불변식, `FinePolicy` 계산                                          | 스프링 빈 의존, DB 접근          |
| **Repository** | 쿼리 정의, fetch join / projection                                        | 비즈니스 분기                    |

**"엔티티는 컨트롤러 밖으로 나가지 않는다"** 는 규칙이 중요하다. 응답은 전부 Java 21 `record` DTO다. 이유가 두 개다.

1. **`LazyInitializationException` 방지.** JPA 엔티티를 그대로 JSON으로 직렬화하면, 직렬화기가 아직 로딩 안 된 연관 필드(`record.getMember()`)를 건드리는 순간 DB 세션이 이미 닫혀 있어 예외가 터진다.
2. **응답 스펙이 DB 스키마에 끌려다니지 않는다.** 컬럼 하나 추가했다고 API 응답이 조용히 바뀌는 일이 없다.

### 2.3 데이터 흐름 ① — 로그인

```mermaid
sequenceDiagram
    participant U as React (Login.tsx)
    participant A as AuthController
    participant S as AuthService
    participant E as BCryptPasswordEncoder
    participant T as TokenService (NimbusJwtEncoder)

    U->>A: POST /api/auth/login {email:"admin", password:"admin"}
    Note over A: @Valid — @LoginEmail 제약<br/>('admin' 리터럴만 형식 예외)
    A->>S: login(request)
    S->>S: memberRepository.findByEmail → 없으면 401
    S->>E: matches(raw, passwordHash)
    Note over E: ★ admin도 예외 없이 bcrypt 통과 필수<br/>우회 분기 0건
    E-->>S: true / false(→401)
    S->>S: member.isActive() 아니면 401
    S->>T: issue(memberId, role, name)
    T-->>S: JWT (sub, role, name, exp 12h)
    S-->>U: {accessToken, expiresIn, member}
    Note over U: Zustand authStore에 저장<br/>→ localStorage 영속
```

핵심: **실패 3종(계정 없음 / 비밀번호 불일치 / 비활성)이 전부 같은 `InvalidCredentialsException`** 이다. 메시지가 다르면 "이 이메일은 존재한다"는 정보가 새어나가 **사용자 열거(user enumeration) 공격**의 재료가 된다.

### 2.4 데이터 흐름 ② — 보호된 요청

```mermaid
sequenceDiagram
    participant U as axios (요청 인터셉터)
    participant F as BearerTokenAuthenticationFilter
    participant D as NimbusJwtDecoder
    participant CV as JwtAuthenticationConverter
    participant C as Controller (@PreAuthorize)

    U->>F: GET /api/members (Authorization: Bearer eyJ...)
    F->>D: 서명(HS256) + 만료 검증
    D-->>F: 실패 → AuthenticationEntryPoint → 401 ProblemDetail<br/>(컨트롤러에 도달조차 안 함)
    F->>CV: 성공 → Jwt 객체
    CV-->>F: authorities = [ROLE_ORGANIZER] (role 클레임 → ROLE_ 접두)
    F->>C: JwtAuthenticationToken 을 SecurityContext에 세팅
    Note over C: hasRole('ORGANIZER') 불충족<br/>→ AccessDeniedHandler → 403 ProblemDetail
    C-->>U: 200 + DTO
```

여기서 배울 점: **커스텀 JWT 필터를 직접 만들지 않았다.** `spring-boot-starter-security-oauth2-resource-server`가 제공하는 `BearerTokenAuthenticationFilter`가 헤더 파싱 → 서명 검증 → 401 응답까지 다 한다. 우리가 한 건 "role 클레임을 권한으로 바꾸는 컨버터" 설정 한 개뿐이다.

### 2.5 데이터 흐름 ③ — 출석 체크 (이 프로젝트의 심장)

```mermaid
sequenceDiagram
    participant U as SessionCheckIn.tsx
    participant C as AttendanceController
    participant S as AttendanceService (@Transactional)
    participant R as Repositories
    participant FP as FinePolicy

    U->>C: PUT /api/sessions/2/attendances<br/>{attendances:[{memberId,status}...]}  ※ fineAmount 없음
    C->>S: checkIn(2, request)
    S->>R: ① studySessionRepository.findById(2)   (쿼리 1)
    S->>S: ② 요청 배열 내 memberId 중복 검사 (순수 자바)
    S->>R: ③ memberRepository.findAllById(ids)    (쿼리 1)
    S->>R: ④ attendanceRepository.findByStudySessionId(2) → Map  (쿼리 1)
    S->>S: ⑤ 검증: 존재 && (활성 || 이 회차에 기존 기록 있음)
    S->>R: ⑥ studyRoomRepository.findById(1)      (쿼리 1) — 단가 스냅샷 소스
    loop 멤버마다 (리포지토리 호출 0건)
        S->>FP: calculate(status, room)
        FP-->>S: 0 / lateFineAmount / absentFineAmount
        S->>S: 기존 있으면 record.updateStatus(...) ← 더티체킹
        S->>S: 없으면 new AttendanceRecord(...) → 리스트에 적재
    end
    S->>R: ⑦ saveAll(newRecords) (INSERT 배치)
    S->>S: ⑧ studySessionService.detail(2) 로 저장 후 상태 반환
    Note over S: 커밋 — 부분 저장 없음
    C-->>U: 200 + 회차 상세(서버가 확정한 fineAmount 포함)
```

**멤버가 4명이든 40명이든 쿼리 개수는 상수다.** 이게 N+1을 안 만드는 구조의 실물이다.

### 2.6 프론트 구조

```mermaid
graph TB
    R["routes.tsx"] --> G1["RequireAuth (토큰 없으면 /login)"]
    G1 --> G2["RequireOrganizer (MEMBER면 차단)"]
    G1 --> P1["Dashboard"]
    G2 --> P2["Members"] & P3["Sessions"] & P4["SessionCheckIn"] & P5["Settings"]
    R --> P0["Login (데모 버튼)"]

    P1 & P2 & P3 & P4 & P5 --> Q["TanStack Query 훅<br/>= 서버 상태의 유일한 소유자"]
    Q --> AX["axios 인스턴스<br/>요청: Bearer 주입 / 응답: 401 → 로그아웃"]
    AX --> Z["Zod 응답 파싱"]
    P0 --> ST["Zustand authStore<br/>(token, member) → localStorage"]
    ST --> AX
```

**상태 분리 원칙**: 서버에서 온 데이터는 **전부** TanStack Query가 소유한다. Zustand에는 토큰과 로그인 멤버만 둔다. 서버 데이터를 Zustand에 복사하는 순간 캐시가 두 개가 되고 무효화 타이밍이 어긋난다.

**역할 기반 라우팅은 UX일 뿐 보안이 아니다.** MEMBER로 로그인하면 운영자 메뉴가 안 보이지만, 실제 방어선은 서버의 `@PreAuthorize`다. 브라우저 JS는 사용자가 얼마든지 조작할 수 있다.

---

## 3. 사용 기술 해설 (신입 눈높이)

> 이 절은 **개념 → 왜 이걸 썼나 → 핵심 원리** 순서로 간다.
> 기존에 NestJS(TypeScript)를 주로 다뤘다면, **"NestJS로 치면 이것"** 대응표를 곳곳에 붙였다.

### 3.0 NestJS ↔ Spring 대응표 (먼저 보고 시작)

| 개념           | NestJS                      | Spring Boot                                       |
| -------------- | --------------------------- | ------------------------------------------------- |
| 진입점         | `main.ts` + `AppModule`     | `StudyFineApplication` + `@SpringBootApplication` |
| 컨트롤러       | `@Controller` `@Get`        | `@RestController` `@GetMapping`                   |
| 서비스         | `@Injectable`               | `@Service`                                        |
| DI             | 생성자 주입 (동일)          | 생성자 주입 (동일)                                |
| 모듈 시스템    | `@Module` imports/providers | **없음** — 컴포넌트 스캔이 자동으로 함            |
| ORM            | Prisma / TypeORM            | Spring Data JPA (Hibernate)                       |
| 마이그레이션   | `prisma migrate`            | Flyway (`V1__init.sql`)                           |
| 검증           | class-validator `@IsEmail`  | Bean Validation `@Email`                          |
| 전역 예외 처리 | `ExceptionFilter`           | `@RestControllerAdvice`                           |
| 인증 가드      | `@UseGuards(JwtAuthGuard)`  | `SecurityFilterChain` + `@PreAuthorize`           |
| DTO            | class + decorator           | `record` + annotation                             |
| API 문서       | `@nestjs/swagger`           | springdoc-openapi                                 |
| 패키지 매니저  | npm (`package.json`)        | Maven (`pom.xml`)                                 |

가장 큰 차이 하나만 꼽으면: **NestJS는 모듈에 provider를 등록해야 주입되지만, Spring은 `@Service`/`@Component`가 붙어 있으면 패키지 스캔으로 자동 등록된다.** Spring이 "설정보다 관례(convention over configuration)"를 택했기 때문이다.

---

### 3.1 Java 21 — 언어 기초 (이 프로젝트에 실제로 쓰인 것만)

**이게 뭔가.** Java의 LTS(장기지원) 버전. Spring Boot 4의 **최소 요구 버전**이라 선택지가 없었다.

**왜 21인가.** Spring Boot 4는 Java 17을 지원하지 않고 21 이상을 요구한다. 동시에 21은 2031년까지 지원되는 LTS라 신규 프로젝트에 안전하다.

**이 프로젝트에서 실제로 쓴 21(+17) 문법 4개.**

#### (1) `record` — 불변 데이터 캐리어

```java
public record LoginResponse(String accessToken, long expiresIn, AuthenticatedMember member) {}
```

이 한 줄이 자동으로 만들어주는 것: `private final` 필드 3개, 생성자, `accessToken()` 같은 접근자, `equals()`, `hashCode()`, `toString()`.

- **TS로 치면** `type LoginResponse = { accessToken: string; ... }` + 불변성.
- **왜 DTO에 쓰나**: DTO는 "값 덩어리"일 뿐 상태를 바꿀 이유가 없다. record는 필드가 전부 `final`이라 실수로 변경할 수 없고, 코드가 1/10로 줄어든다.
- **주의**: 접근자 이름이 `getAccessToken()`이 아니라 **`accessToken()`** 이다(자바빈 규약과 다름). Jackson은 record를 알아서 직렬화한다.

#### (2) switch 표현식 + 패턴 매칭

```java
public static int calculate(AttendanceStatus status, StudyRoom room) {
    return switch (status) {
        case PRESENT -> 0;
        case LATE    -> room.getLateFineAmount();
        case ABSENT  -> room.getAbsentFineAmount();
    };
}
```

- `break`가 없다. `->` 형태는 fall-through(다음 case로 흘러내림)가 원천적으로 없다.
- **enum에 대한 switch 표현식은 "빠짐없음(exhaustiveness)" 검사를 컴파일러가 한다.** 나중에 `AttendanceStatus`에 `EXCUSED`(사유결석)를 추가하면 **이 코드가 컴파일 에러**가 난다. 런타임에 조용히 0원이 되는 대신 빌드가 깨지는 것 — 이게 `if-else`보다 나은 이유다.

> **면접 포인트**: "enum이 늘어나면 어떻게 되나요?" → "컴파일이 깨집니다. 그게 의도입니다. 벌금 규칙에 구멍이 뚫린 채 배포되는 것보다 낫습니다."

#### (3) 텍스트 블록 (`"""`)

```java
@Query("""
    select ar from AttendanceRecord ar
    join fetch ar.studySession s
    where ar.member.id = :memberId
    order by s.sessionDate desc
    """)
```

여러 줄 문자열을 `+`와 `\n` 없이 쓴다. SQL/JPQL이 코드 안에서 SQL처럼 읽힌다.

#### (4) `var`, `Optional`, Stream API

```java
Map<Long, Member> membersById = memberRepository.findAllById(requestedIds).stream()
        .collect(Collectors.toMap(Member::getId, m -> m));
```

`Optional<T>`은 "값이 없을 수도 있음"을 타입으로 표현한다. TS의 `T | null`에 대응하되, `.orElseThrow(...)`처럼 **없을 때의 처리를 강제**한다.

```java
StudySession session = studySessionRepository.findById(sessionId)
    .orElseThrow(() -> new NotFoundException("회차를 찾을 수 없습니다"));
```

---

### 3.2 Spring Boot 4.1 — 프레임워크의 뼈대

**이게 뭔가.** Spring Framework(자바 표준 애플리케이션 프레임워크) 위에 **자동 설정(auto-configuration)** 과 **스타터(starter)** 를 얹어, "설정 파일 수백 줄" 없이 앱을 띄우게 해주는 도구.

**핵심 개념 3개.**

#### (1) IoC 컨테이너 / DI (제어의 역전 / 의존성 주입)

객체를 `new`로 직접 만들지 않고, 스프링이 만들어서 넣어준다.

```java
@Service
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(MemberRepository r, PasswordEncoder e, TokenService t) { ... }
}
```

- `@Service`가 붙어 있으니 스프링이 이 클래스를 **빈(bean)** 으로 등록한다.
- 생성자에 필요한 것들을 적어두면 스프링이 타입을 보고 알아서 주입한다.
- **왜 생성자 주입인가**: 필드가 `final`이 되어 불변이고, 테스트에서 `new AuthService(mock1, mock2, mock3)`로 직접 만들 수 있다. `@Autowired` 필드 주입은 테스트에서 리플렉션이 필요해진다.

> **NestJS 경험자 팁**: 개념이 거의 같다. 차이는 NestJS가 `@Module`의 `providers`에 등록해야 하는 반면, Spring은 `@SpringBootApplication`이 있는 패키지 하위를 **자동 스캔**한다는 것.

#### (2) 자동 설정 (Auto-configuration)

클래스패스에 무엇이 있는지 보고 스프링이 알아서 설정한다.

- `spring-boot-starter-data-jpa`가 있고 `spring.datasource.url`이 설정돼 있다 → **HikariCP 커넥션 풀 + EntityManagerFactory + 트랜잭션 매니저**를 자동 구성
- `postgresql` 드라이버가 클래스패스에 있다 → PostgreSQL 방언(dialect) 자동 선택
- `spring-boot-starter-actuator` → `/health` 엔드포인트 자동 노출

**"마법"처럼 보이는 게 단점이기도 하다.** 그래서 이 프로젝트는 마법에 의존하지 않고 중요한 것들(`ddl-auto`, `open-in-view`, `base-path`)을 `application.yml`에 **명시**하고 이유를 주석으로 달았다.

#### (3) 스타터 (Starter)

"이거 하나 넣으면 필요한 의존성 세트가 다 딸려온다"는 묶음 아티팩트.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
```

버전을 안 적는 게 보이는가? **부모 POM(`spring-boot-starter-parent`)이 버전을 관리**하기 때문이다. Spring 생태계 라이브러리 수십 개의 버전 호환성을 부모가 책임진다.

#### ★ Spring Boot 3 → 4 변경점 (이 프로젝트가 실제로 부딪힌 것)

| 변경          | Boot 3.x                              | Boot 4.x                                                             |
| ------------- | ------------------------------------- | -------------------------------------------------------------------- |
| 최소 Java     | 17                                    | **21**                                                               |
| 웹 스타터     | `spring-boot-starter-web`             | **`spring-boot-starter-webmvc`**                                     |
| 테스트 스타터 | `spring-boot-starter-test` 하나       | **기능별로 분리** (`-webmvc-test`, `-data-jpa-test`, …)              |
| JSON          | Jackson 2 (`com.fasterxml.jackson.*`) | **Jackson 3 (`tools.jackson.*`)**                                    |
| Flyway 스타터 | `flyway-core` 직접 추가               | `spring-boot-starter-flyway` + **`flyway-database-postgresql` 필수** |
| Security      | 6.x                                   | **7.x** (§3.6 참조)                                                  |
| springdoc     | 2.x                                   | **3.x** (2.x는 Boot 4와 호환 불가)                                   |

우리 `pom.xml`이 실제로 이렇게 생겼다:

```xml
<artifactId>spring-boot-starter-webmvc</artifactId>                       <!-- web 아님 -->
<artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
<artifactId>spring-boot-starter-webmvc-test</artifactId>                  <!-- test 스코프 -->
<artifactId>spring-boot-starter-security-oauth2-resource-server-test</artifactId>
```

**Jackson 3 전환이 왜 중요한가.** 패키지 루트가 `com.fasterxml.jackson` → `tools.jackson`으로 바뀌었다. 그래서 우리 `SecurityConfig`의 import가 이렇게 생겼다:

```java
import tools.jackson.databind.ObjectMapper;   // ← com.fasterxml 아님!
```

이걸 습관대로 `com.fasterxml.jackson.databind.ObjectMapper`로 쓰면 **빈 주입 실패로 애플리케이션이 아예 안 뜬다**(스프링이 등록한 `ObjectMapper` 빈은 `tools.jackson` 타입이므로 타입이 안 맞는다). §5에 실제 겪은 스토리로 정리했다.

---

### 3.3 Maven — 빌드 도구

**이게 뭔가.** 자바의 의존성 관리 + 빌드 도구. npm + package.json에 대응한다.

| npm                 | Maven                              |
| ------------------- | ---------------------------------- |
| `package.json`      | `pom.xml`                          |
| `npm install`       | `./mvnw dependency:resolve` (자동) |
| `node_modules/`     | `~/.m2/repository/` (전역 캐시)    |
| `npm run build`     | `./mvnw package`                   |
| `npm test`          | `./mvnw test`                      |
| `package-lock.json` | 없음 — 버전을 pom에 직접 고정      |

**Maven 라이프사이클** (앞 단계가 자동으로 먼저 실행됨):

```
validate → compile → test → package → verify → install → deploy
```

- `./mvnw test` → 컴파일 후 테스트 실행
- `./mvnw -DskipTests package` → 테스트 건너뛰고 JAR 생성
- 산출물: `target/study-fine-0.0.1-SNAPSHOT.jar` — **실행 가능한 fat JAR** (톰캣까지 안에 들어 있음)

**Maven Wrapper(`./mvnw`)가 뭔가.** 저장소에 포함된 스크립트로, Maven이 설치돼 있지 않아도 지정된 버전을 자동으로 내려받아 실행한다. **"클론하고 바로 빌드된다"** 를 보장하는 장치. npm 세계의 `npx`와 비슷한 역할.

**왜 Gradle이 아니라 Maven인가** (압박 질문 대비):

- Spring Initializr 기본값이고, XML이라 구조가 명시적이라 처음 보는 사람도 읽는다.
- Gradle은 빌드 스크립트가 코드(Kotlin/Groovy)라 유연하지만, S티어 하루짜리 프로젝트에서 그 유연성이 필요 없다.
- 정직한 추가 답변: **"Gradle 경험은 아직 없습니다. 다음 Spring 프로젝트에서 Gradle + Kotlin DSL로 해볼 계획입니다."**

---

### 3.4 Spring Data JPA / Hibernate 7 — ORM

**이게 뭔가.**

- **JPA**: 자바의 ORM **표준 명세**(인터페이스 규격). `jakarta.persistence.*`
- **Hibernate**: 그 명세의 **구현체**. 실제로 SQL을 만들어 실행하는 놈.
- **Spring Data JPA**: Hibernate를 편하게 쓰게 해주는 **한 겹 더 위**의 스프링 모듈. 인터페이스만 선언하면 구현체를 런타임에 만들어준다.

```java
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);        // 구현 안 씀 — 메서드 이름으로 쿼리 생성
    boolean existsByEmail(String email);
    long countByRoleAndActiveTrue(MemberRole role);
}
```

`findByEmail`이라는 **이름을 파싱해서** `SELECT * FROM member WHERE email = ?`를 만든다. 이걸 **쿼리 메서드(derived query)** 라고 한다.

#### 핵심 개념 ① 영속성 컨텍스트 (Persistence Context)와 더티 체킹

이게 JPA에서 가장 중요하고, 신입이 가장 많이 헷갈리는 부분이다.

**영속성 컨텍스트 = 트랜잭션 동안 엔티티들을 담아두는 1차 캐시.**

```java
@Transactional
public StudySessionDetailResponse checkIn(...) {
    Map<Long, AttendanceRecord> existingByMember =
        attendanceRepository.findByStudySessionId(sessionId)...;  // ← 여기서 조회한 엔티티는 "관리 상태(managed)"

    ...
    existing.updateStatus(item.status(), fineAmount);   // ← 그냥 자바 객체 필드를 바꿨을 뿐

    attendanceRepository.saveAll(newRecords);           // ← 새 것만 save. 기존 것은 save 안 함!
    // 커밋 시점에 Hibernate가 "조회 때 스냅샷"과 "현재 값"을 비교해서
    // 바뀐 것에 대해서만 자동으로 UPDATE를 날린다 = 더티 체킹(dirty checking)
}
```

**왜 `save()`를 안 불렀는데 DB가 바뀌나?** 이게 더티 체킹이다. 트랜잭션 커밋(또는 flush) 시점에 Hibernate가 관리 중인 엔티티들의 현재 상태를 조회 당시 스냅샷과 대조해, 달라진 필드만 UPDATE 문으로 만든다.

> **면접 단골 질문**: "`existing.updateStatus()` 뒤에 `save()`를 안 부르는데 어떻게 저장되나요?"
> **답**: "`@Transactional` 안에서 리포지토리로 조회한 엔티티는 영속성 컨텍스트가 관리하는 상태입니다. 커밋 시점에 Hibernate가 더티 체킹으로 변경을 감지해 UPDATE를 발행합니다. 반대로 새로 `new`한 객체는 비영속(transient) 상태라 명시적으로 `save()`를 해줘야 해서, 코드에서도 신규 레코드만 `saveAll()`에 모아 넣었습니다."

**꼬리질문 대비 — 저장 직후 `detail()` 재조회가 왜 최신값을 보나?**
JPQL/네이티브 쿼리를 실행하기 직전에 Hibernate가 **자동 flush**를 한다(쿼리 결과가 아직 안 밀어넣은 변경을 놓치지 않게 하기 위해). 그래서 같은 트랜잭션 안에서 재조회해도 방금 바꾼 값이 보인다.

#### 핵심 개념 ② 지연 로딩(LAZY)과 N+1

```java
@ManyToOne(fetch = FetchType.LAZY)   // ← 반드시 명시
@JoinColumn(name = "member_id", nullable = false)
private Member member;
```

**JPA의 `@ManyToOne` 기본값은 EAGER(즉시 로딩)다.** 그냥 두면 출석 기록 하나를 조회할 때마다 회차와 멤버까지 매번 SELECT가 따라붙는다. 그래서 **모든 `@ManyToOne`에 LAZY를 명시**했다.

LAZY로 두면 `record.getMember()`를 실제로 호출하는 순간 그때 SELECT가 나간다. 여기서 **N+1 문제**가 생긴다.

```
1) SELECT * FROM attendance_record WHERE session_id = 2      → 4건
2) SELECT * FROM member WHERE id = 1     ← 반복 1
3) SELECT * FROM member WHERE id = 2     ← 반복 2
4) SELECT * FROM member WHERE id = 3     ← 반복 3
5) SELECT * FROM member WHERE id = 4     ← 반복 4
= 1 + N 번의 쿼리
```

목록 1번 + 항목마다 1번 = **1 + N**. 항목이 100개면 쿼리 101번. 이 프로젝트는 이걸 지점별로 세 가지 방법으로 막았다(§4.2).

#### 핵심 개념 ③ `open-in-view: false`

```yaml
spring:
  jpa:
    open-in-view: false
```

**OSIV(Open Session In View)** 는 스프링 부트의 기본값이 `true`다. HTTP 요청이 끝날 때까지 영속성 컨텍스트와 DB 커넥션을 열어둔다는 뜻이다.

**왜 껐나** (두 가지 이유):

1. **커넥션 낭비.** 컨트롤러가 JSON을 만드는 동안에도 DB 커넥션을 붙들고 있다. 트래픽이 늘면 커넥션 풀이 먼저 마른다.
2. **N+1을 숨긴다.** OSIV가 켜져 있으면 컨트롤러/뷰 단계에서 지연 로딩이 "그냥 동작"해버려서, 개발 중에는 문제를 못 느끼다가 운영에서 터진다. 꺼두면 서비스 레이어 밖에서 LAZY 필드를 만지는 순간 `LazyInitializationException`으로 **즉시 알려준다**.

> **면접 답변 팁**: "OSIV를 끄면 트랜잭션 밖에서 지연 로딩이 안 되니 불편하지 않나요?" → "불편한 게 목적입니다. 필요한 데이터를 서비스 레이어에서 **명시적으로** fetch join하거나 DTO로 변환하게 강제해서, 지연 로딩이 어디서 일어나는지 통제 가능해집니다."

#### 핵심 개념 ④ Projection (인터페이스 프로젝션)

집계 결과를 엔티티가 아닌 **필요한 컬럼만 담은 인터페이스**로 받는다.

```java
@Query(value = """
    SELECT m.id AS id, m.name AS name, ...
           COALESCE(SUM(ar.fine_amount), 0) AS accumulatedFine,
           COUNT(ar.id) FILTER (WHERE ar.status = 'LATE') AS lateCount
    FROM member m
    LEFT JOIN attendance_record ar ON ar.member_id = m.id
    WHERE (:includeInactive = TRUE OR m.active = TRUE)
    GROUP BY m.id
    ORDER BY accumulatedFine DESC, m.name
    """, nativeQuery = true)
List<MemberFineSummaryProjection> findAllWithFineSummary(@Param("includeInactive") boolean includeInactive);
```

- `AS accumulatedFine` 별칭과 프로젝션 인터페이스의 `getAccumulatedFine()`이 **이름으로 매핑**된다.
- **`nativeQuery = true`인 이유**: `COUNT(...) FILTER (WHERE ...)` 는 PostgreSQL 전용 문법이고 JPQL에 없다. 상태별 카운트 3개를 한 번에 뽑는 가장 짧은 방법이라 채택했다. 대가는 **DB 종속성**(다른 DB로 못 옮김) — 알고 고른 트레이드오프다.
- **타입 함정**: PostgreSQL의 `SUM(integer)` 결과는 `bigint`다. Java 쪽 프로젝션을 `int`로 잡으면 매핑 예외가 난다 → **`long`으로 받아야 한다.**

#### 핵심 개념 ⑤ `@EntityGraph` — 선언적 fetch join

```java
@EntityGraph(attributePaths = "member")
List<AttendanceRecord> findByStudySessionId(Long studySessionId);
```

"이 쿼리를 실행할 때는 `member`를 **같이 조인해서 한 번에** 가져와라"는 지시. 쿼리 메서드 이름은 그대로 두고 fetch 전략만 얹는다.

`join fetch`(JPQL 직접 작성)와 결과는 같다. 어느 쪽이 나은가?

- `@EntityGraph`: 쿼리를 안 써도 되고 재사용이 쉽다 → 단순 fetch에 적합
- `join fetch`: 조건·정렬과 함께 세밀히 제어 가능 → 우리 `findByMemberIdOrderBySessionDateDesc`는 정렬까지 필요해서 이쪽

---

### 3.5 Flyway — DB 마이그레이션

**이게 뭔가.** `V1__init.sql`, `V2__add_column.sql` 같은 SQL 파일을 **버전 순서대로 한 번씩만** 실행하고, 어디까지 적용했는지를 `flyway_schema_history` 테이블에 기록하는 도구.

**왜 필요한가.** DB 스키마도 코드처럼 버전 관리돼야 한다. "내 로컬 DB에는 컬럼이 있는데 운영에는 없다" 사고를 막는다.

**핵심 규칙 3개.**

1. **적용된 마이그레이션 파일은 절대 수정하지 않는다.** Flyway가 파일의 **체크섬**을 저장해두고, 다음 기동 때 비교한다. 한 글자만 고쳐도 체크섬이 달라져 **기동이 실패**한다. 변경이 필요하면 항상 새 버전 파일을 추가한다.
2. **`ddl-auto: validate` 고정.**

   ```yaml
   spring.jpa.hibernate.ddl-auto: validate
   ```

   | 값            | 동작                       | 평가                                       |
   | ------------- | -------------------------- | ------------------------------------------ |
   | `none`        | 아무것도 안 함             | 검증도 안 함                               |
   | `validate`    | 엔티티 ↔ 스키마 **대조만** | **✅ 이 프로젝트가 쓰는 값**               |
   | `update`      | 차이나면 알아서 ALTER      | ⛔ 컬럼 삭제/타입 변경을 **조용히 무시**함 |
   | `create-drop` | 매번 지우고 새로 만듦      | ⛔ 데이터 증발                             |

   `validate` 덕분에 엔티티와 실제 스키마가 어긋나면 **애플리케이션 기동 시점에 즉시 실패**한다. 런타임에 이상한 에러로 만나는 것보다 훨씬 낫다.

3. **Boot 4 함정**: `spring-boot-starter-flyway`만 넣으면 안 되고 **`flyway-database-postgresql` 아티팩트를 반드시 함께** 명시해야 한다. 누락하면 기동 시 `Unsupported Database: PostgreSQL <버전>`으로 실패한다(Flyway 10부터 DB별 모듈이 분리됐다).

**"시드 데이터는 Flyway가 아니다"** 는 판단도 중요하다.

| 대상                            | 어디에                             | 왜                                                             |
| ------------------------------- | ---------------------------------- | -------------------------------------------------------------- |
| `study_room` id=1 행            | **`V1__init.sql`** (스키마의 일부) | 앱이 뜨려면 항상 있어야 하는 설정 행. 운영에서도 필요          |
| admin 계정, 샘플 멤버·회차·출석 | **`DemoDataSeeder`** (자바 코드)   | BCrypt 해시를 SQL 리터럴로 못 박기 싫고, 스위치로 끌 수 있어야 |

`DemoDataSeeder`는 `ApplicationRunner`(기동 완료 후 1회 실행)이고, `existsByEmail("admin")`이면 통째로 return해서 **멱등**하다. `APP_SEED_ENABLED=false`로 끌 수도 있다(Flyway 마이그레이션은 못 끈다).

---

### 3.6 ★ Spring Security 7 — 이 프로젝트의 첫 경험 영역

**이게 뭔가.** 서블릿 **필터 체인** 기반의 보안 프레임워크. 요청이 컨트롤러에 도달하기 **전에** 필터들이 줄줄이 검사한다.

```
요청 → [CorsFilter] → [CsrfFilter] → [BearerTokenAuthenticationFilter] → ... → [AuthorizationFilter] → DispatcherServlet → Controller
```

이 구조를 이해하는 게 왜 중요하냐면 — **필터 단계에서 막힌 401/403은 `@RestControllerAdvice`가 못 잡는다.** 컨트롤러에 도달조차 안 했으니 당연하다. 그래서 이 프로젝트는 두 군데에서 같은 에러 포맷을 만든다.

```java
// SecurityConfig — 필터 단계용
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
    .authenticationEntryPoint(problemDetailEntryPoint())   // 토큰 없음/무효 → 401 ProblemDetail
)
.exceptionHandling(e -> e.accessDeniedHandler(problemDetailAccessDeniedHandler()));  // 권한 부족 → 403
```

`ProblemTitles`라는 공용 클래스로 문구를 한 곳에 모아, 필터 단계 401과 컨트롤러 단계 401이 **다른 문구를 뱉는 일이 없게** 했다.

#### ★★ Security 7 함정 4개 (Boot 3 습관으로 하면 다 막힌다)

##### (1) CSRF — stateless API도 명시적으로 꺼야 한다

```java
.csrf(AbstractHttpConfigurer::disable)
```

**증상**: `POST`/`PUT`/`PATCH`/`DELETE`가 **전부 403**. GET은 멀쩡하다.
**함정인 이유**: 403은 "권한 문제"처럼 보인다. `@PreAuthorize`와 롤 매핑을 몇 시간씩 뒤지게 된다. **쓰기 요청만 403이면 CSRF부터 의심하는 게 정답이다.**

**CSRF가 뭔가.** Cross-Site Request Forgery. 다른 사이트가 사용자의 **브라우저가 자동으로 붙이는 쿠키**를 이용해 몰래 요청을 보내는 공격. 방어는 "쿠키만으로는 알 수 없는 토큰"을 폼에 심어 함께 보내게 하는 것.

**우리가 꺼도 되는 이유** (면접에서 반드시 이 논리로 답해야 한다):

> CSRF는 **브라우저가 자동으로 인증 정보를 실어 보낼 때** 성립하는 공격입니다. 이 API는 세션 쿠키를 전혀 쓰지 않고, 인증을 `Authorization: Bearer` 헤더로만 합니다. 헤더는 자바스크립트가 **명시적으로 넣어야** 붙고, 다른 오리진의 스크립트는 우리 `localStorage`에 접근할 수 없습니다. 즉 공격자가 위조 요청을 만들어도 토큰을 실을 방법이 없으므로 CSRF가 성립하지 않습니다. 반대로 httpOnly 쿠키 방식으로 바꾼다면 CSRF를 **반드시 다시 켜야** 합니다.

> **정확성 주의(중요).** "Security 6까지는 CSRF가 폼 기반에만 적용됐다"고 단정하지 말 것. Spring Security의 `HttpSecurity` 기본값은 이전부터 CSRF를 켜왔고, Boot 4/Security 7의 마이그레이션 가이드들이 "REST API가 업그레이드 후 403이 되는 가장 흔한 원인"으로 CSRF 기본값을 지목하는 것이다.
> 안전한 표현: **"Boot 4/Security 7로 오면서 stateless REST 구성에서도 CSRF를 명시적으로 꺼주지 않으면 쓰기 요청이 전부 403이 됩니다. 실제로 이걸로 막혔고, 명시적으로 disable하면서 '왜 꺼도 되는지'를 코드 주석에 남겼습니다."**
> 만약 면접관이 "그건 원래 그랬는데요?"라고 하면 → **"제가 Boot 4 마이그레이션 문서를 보고 대비했던 항목인데, 정확히는 Spring Security의 오래된 기본 동작이 맞습니다. 어느 쪽이든 stateless JWT API에서는 명시적 disable + 그 근거를 남기는 게 맞다고 봤습니다."** 로 받으면 오히려 점수가 된다.

##### (2) `authorizeRequests()` 완전 제거

```java
// ⛔ Security 7에서 삭제됨 (컴파일 에러)
http.authorizeRequests().antMatchers("/health").permitAll()

// ✅ 현재 방식
http.authorizeHttpRequests(authorize -> authorize
    .requestMatchers("/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
    .requestMatchers("/api/auth/login").permitAll()
    .anyRequest().authenticated()
);
```

`antMatchers()`/`mvcMatchers()`도 없어지고 **`requestMatchers()` 하나로 통합**됐다. 인터넷에 널린 옛날 예제 코드를 그대로 복붙하면 컴파일부터 안 된다.

##### (3) 세션 정책 STATELESS 명시

```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

"HTTP 세션을 만들지도, 쓰지도 마라." JWT는 요청마다 토큰으로 신원을 증명하므로 서버가 상태를 들고 있을 필요가 없다. 이게 **수평 확장(서버 여러 대)** 을 쉽게 만든다 — 어느 서버로 요청이 가도 토큰만 검증하면 되니까.

##### (4) `@EnableMethodSecurity` 없으면 `@PreAuthorize`가 조용히 무시된다

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // ← 이게 없으면 @PreAuthorize가 아무 일도 안 한다
public class SecurityConfig { ... }
```

**가장 무서운 함정.** 에러가 안 나고 그냥 **모든 요청이 통과**한다. 즉 권한 검사가 통째로 사라진 채 배포된다. 그래서 이 프로젝트는 권한 매트릭스(API.md §16)를 **테스트로 잠갔다**.

#### JWT 인증 구성

**JWT(JSON Web Token)가 뭔가.** `헤더.페이로드.서명` 세 부분을 점으로 이은 문자열.

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiIxIiwicm9sZSI6Ik9SR0FOSVpFUiIsImV4cCI6...} . 서명
   {"alg":"HS256"}        {"sub":"1","role":"ORGANIZER","name":"굴리자","exp":...}
```

**중요**: 페이로드는 **암호화가 아니라 Base64 인코딩**이다. 누구나 디코딩해서 읽을 수 있다. 서명이 보장하는 건 **위변조 불가**이지 **기밀성이 아니다.** → 그래서 비밀번호 같은 민감 정보는 절대 넣지 않는다. 우리는 `sub`(memberId), `role`, `name`, `exp`만 넣었다.

**HS256 vs RS256.**

| 방식      | 키                             | 언제 쓰나                                               |
| --------- | ------------------------------ | ------------------------------------------------------- |
| **HS256** | 대칭키 1개 (발급=검증 같은 키) | **발급자와 검증자가 같은 앱** ← 우리 경우               |
| RS256     | 비대칭 키쌍 (개인키/공개키)    | 인증 서버와 리소스 서버가 분리된 MSA, 제3자 검증 필요시 |

우리는 한 애플리케이션이 발급하고 같은 애플리케이션이 검증하므로 RSA 키쌍 관리는 **과설계**다.

**왜 jjwt 대신 Nimbus(Spring 내장)인가 (ADR-1).**

```
문제: Spring Boot 4는 Jackson 3(tools.jackson.*)로 이행
     흔히 쓰는 jjwt-jackson은 Jackson 2(com.fasterxml.jackson.*) 바인딩
     → 클래스패스에 Jackson 2를 되살리거나 jjwt-gson으로 우회해야 함

결정: spring-boot-starter-security-oauth2-resource-server 를 쓰고
     HS256 대칭키로 NimbusJwtEncoder / NimbusJwtDecoder 빈 등록

결과: - 의존성 1개 감소, JSON 바인딩 충돌 없음
     - 커스텀 OncePerRequestFilter 를 작성하지 않음
       (BearerTokenAuthenticationFilter 가 파싱·검증·401 응답까지 처리)
     - JwtAuthenticationConverter 로 role 클레임 → ROLE_ORGANIZER 매핑만 설정
```

**`ROLE_` 접두사의 정체.**

```java
authoritiesConverter.setAuthorityPrefix("ROLE_");
authoritiesConverter.setAuthoritiesClaimName("role");
```

`@PreAuthorize("hasRole('ORGANIZER')")`는 내부적으로 권한 문자열 **`ROLE_ORGANIZER`** 를 찾는다. `hasRole`은 접두사를 자동으로 붙이고, `hasAuthority`는 안 붙인다. 토큰의 `role: "ORGANIZER"` 클레임에 `ROLE_`을 붙여주는 게 이 컨버터의 역할이다. **이 설정을 빠뜨리면 ORGANIZER 계정도 403이 난다.**

**BCrypt.**

```java
@Bean
public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
```

- 비밀번호는 절대 평문 저장 안 한다. **단방향 해시**로 저장하고 로그인 때 `matches(raw, hash)`로 비교한다.
- BCrypt는 **의도적으로 느린** 알고리즘이고, **salt를 해시 문자열 안에 포함**한다(그래서 같은 비밀번호도 매번 다른 해시가 나오고, 별도 salt 컬럼이 필요 없다).
- 해시 결과가 60자라 컬럼을 `VARCHAR(72)`로 잡았다.

**CORS.**

```java
configuration.setAllowedOrigins(allowedOrigins);   // 환경변수 CORS_ALLOWED_ORIGINS
configuration.setAllowedMethods(List.of("GET","POST","PATCH","PUT","DELETE","OPTIONS"));
configuration.setAllowedHeaders(List.of("Authorization","Content-Type"));
```

- **와일드카드 `*` 금지.** 명시적 오리진 목록만.
- `allowCredentials`를 켜지 않았다(기본 false). 쿠키를 안 쓰니 필요 없고, `*`와 `credentials: true`의 위험한 조합도 원천 차단된다.
- 실제 배포에서는 `CORS_ALLOWED_ORIGINS=https://study-fine.vercel.app` 를 Render 환경변수로 넣었다.

---

### 3.7 Bean Validation — 입력 검증

**이게 뭔가.** `@NotBlank`, `@Email`, `@Size` 같은 애노테이션으로 DTO 필드 제약을 선언하면, `@Valid`가 붙은 컨트롤러 파라미터에 대해 자동 검증하는 자바 표준(Jakarta Validation).

```java
public record MemberCreateRequest(
    @NotBlank @Size(min = 1, max = 50) String name,
    @NotBlank @Email @Size(max = 255) String email,     // ← 예외 없는 표준 @Email
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotNull MemberRole role
) {}
```

검증 실패 시 `MethodArgumentNotValidException`이 던져지고, `ApiExceptionHandler`가 이를 400 + `errors[]` 배열로 변환한다.

#### 커스텀 제약 `@LoginEmail` — 데모 계정 규정의 정확한 구현

```java
public class LoginEmailValidator implements ConstraintValidator<LoginEmail, String> {
    private static final String DEMO_ACCOUNT_EMAIL = "admin";
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;   // null/blank는 @NotBlank 책임
        return DEMO_ACCOUNT_EMAIL.equals(value) || EMAIL_PATTERN.matcher(value).matches();
    }
}
```

**왜 이렇게까지 하나.** CLAUDE.md 규정은 "`admin`이라는 **딱 하나의 리터럴 값**에 한해 이메일 형식 검증을 우회"다.

- 로그인 DTO에서 `@Email`을 **그냥 빼버리면** → `asdf`, `<script>`, `' OR 1=1--` 등 **모든 비이메일 문자열**이 통과한다. 규정 위반이자 실제로 검증 표면이 넓어진다.
- 그래서 **완전 일치(`equals`)** 로 예외를 리터럴 1건에 가둔다. 접두사 매칭(`startsWith`)이나 부분 일치가 아니다.

**격리 검증(검수에서 실제 확인함):**

| 위치                        | 검증                     | 결과                           |
| --------------------------- | ------------------------ | ------------------------------ |
| `LoginRequest.email`        | `@LoginEmail`            | `admin` 통과 (**유일한 예외**) |
| `MemberCreateRequest.email` | 표준 `@Email`            | `admin` **400으로 거부**       |
| 프론트 `schemas/auth.ts`    | `value === "admin" \|\|` | 서버와 대칭                    |
| 프론트 `schemas/member.ts`  | `z.email()`              | 예외 없음                      |

`grep -rn "@LoginEmail"` 결과가 **`LoginRequest.java` 단 1곳**이라는 게 이 설계가 새지 않았다는 증거다.

**그리고 비밀번호는 절대 우회하지 않는다.** `AuthService.login`에는 admin에 대한 `if` 분기가 **하나도 없다**. `passwordEncoder.matches()`를 무조건 통과해야 하고, 시드도 `passwordEncoder.encode("admin")`으로 진짜 BCrypt 해시를 저장한다. `/api/auth/demo-login` 같은 **미인증 토큰 발급 엔드포인트는 존재하지 않는다** — 그건 백도어다.

---

### 3.8 ProblemDetail (RFC 9457) — 표준 에러 응답

**이게 뭔가.** "HTTP API 에러를 이런 JSON 모양으로 주자"는 IETF 표준. Spring 6부터 `org.springframework.http.ProblemDetail`로 내장돼 있다.

```json
{
  "type": "about:blank",
  "title": "입력값이 올바르지 않습니다",
  "status": 400,
  "detail": "요청 본문 검증에 실패했습니다",
  "instance": "/api/members",
  "errors": [{ "field": "email", "message": "올바른 이메일 형식이 아닙니다" }]
}
```

**왜 커스텀 `ErrorResponse` 클래스를 안 만들었나.** 표준이 이미 있고 Spring이 제공하는데 똑같은 걸 또 만들 이유가 없다(ponytail 원칙 — 필요 없는 코드 만들지 않기). 표준 포맷이라 Swagger 문서화와 프론트 파싱이 양쪽에서 일관된다.

**500 응답에 절대 넣지 않는 것**:

```java
@ExceptionHandler(Exception.class)
public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
    String traceId = UUID.randomUUID().toString();
    log.error("처리되지 않은 예외 [traceId={}]", traceId, ex);   // 스택트레이스는 서버 로그에만

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요");
    problem.setProperty("traceId", traceId);   // 클라이언트에는 추적용 ID만
    return problem;
}
```

**스택트레이스·예외 메시지를 응답에 실으면** 내부 클래스 구조, 라이브러리 버전, SQL 문장이 새어나가 공격자에게 지도를 그려주는 셈이다. 대신 `traceId`를 주면 사용자가 "이 ID로 문의드립니다"라고 할 수 있고 서버 로그에서 정확히 찾을 수 있다.

---

### 3.9 springdoc-openapi — API 문서 자동화

**이게 뭔가.** 컨트롤러 코드를 스캔해서 OpenAPI 3 스펙(`/v3/api-docs`)을 만들고, Swagger UI(`/swagger-ui/index.html`)로 브라우저에서 눌러볼 수 있게 해주는 라이브러리.

**버전 선택이 이 프로젝트의 실제 이슈였다.**

- springdoc **2.x는 Spring Boot 3용**이다. Boot 4에 얹으면 기동 자체가 안 된다(Spring Framework 7 / Jackson 3 변경 때문).
- Boot 4용은 **springdoc 3.x 라인**. Maven Central에서 실제 존재하는 최신 안정 버전을 조사해서 **3.1.0으로 핀**했다.
- 부모 POM이 버전을 관리해주지 않는 서드파티라 **`<version>3.1.0</version>`을 직접 명시**했다.

```java
// OpenApiConfig — Swagger UI에서 토큰을 붙여 바로 호출할 수 있게 bearerAuth 스키마 등록
```

`/swagger-ui/**`, `/v3/api-docs/**`는 SecurityConfig에서 `permitAll`이다(문서를 보려고 로그인해야 하면 데모 가치가 없다). 다만 **엔드포인트 자체는 여전히 보호**되므로 문서만 보이고 호출은 토큰이 있어야 된다.

---

### 3.10 Actuator — 헬스체크

```yaml
management:
  endpoints:
    web:
      base-path: / # 기본 /actuator 접두사 제거 → /health 로 바로 노출
      exposure:
        include: health # health 만 노출 (env, beans 등은 절대 열지 않는다)
```

**커스텀 `HealthController`를 만들지 않았다.** 설정 두 줄이면 Actuator가 `/health` → `{"status":"UP"}`를 준다. `show-details`를 설정하지 않았으므로 DB 커넥션 상태 같은 **내부 정보는 노출되지 않는다.**

**왜 헬스체크가 필요한가.** Render/Kubernetes 같은 플랫폼이 "이 컨테이너가 살아있나"를 주기적으로 확인하는 데 쓴다. 죽었으면 재시작하거나 트래픽을 안 보낸다.

---

### 3.11 테스트 — JUnit 5 + Mockito + 슬라이스 테스트

이 프로젝트의 테스트는 **28개**이고, 세 종류다.

#### (1) 순수 단위 테스트 — `FinePolicyTest`

```java
public static int calculate(AttendanceStatus status, StudyRoom room)
```

`FinePolicy`는 **스프링 빈이 아니고 DB도 모르는 static 메서드**다. 그래서 스프링 컨텍스트도, 목(mock)도 없이 테스트된다. 실행이 밀리초 단위다.

**이게 설계의 결과라는 점이 중요하다.** 핵심 도메인 규칙을 프레임워크에서 떼어놓으면 테스트 비용이 거의 0이 된다. S티어(하루)에서 핵심 로직 커버리지를 확보할 수 있었던 이유다.

특히 이 테스트가 있다:

```java
snapshot_pastResultDoesNotChangeWhenRateChangesLater()
```

계산 결과를 받아둔 뒤 `room`의 단가를 바꿔도 **이전 결과값이 변하지 않음**을 검증한다. "계산이 순수하다 = 스냅샷이 가능하다"는 성질을 테스트로 문서화한 것.

#### (2) 슬라이스 테스트 — `@WebMvcTest` + Spring Security Test

```java
@WebMvcTest(StudyRoomController.class)
@Import({SecurityConfig.class, SecurityTestSupportConfig.class})
class StudyRoomControllerSecurityTest {

    @MockitoBean private StudyRoomService studyRoomService;   // 서비스는 목으로

    @Test void patch_withoutToken_returns401() { ... }
    @Test void patch_withMemberRole_returns403() {
        mockMvc.perform(patch("/api/study-room")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
            .andExpect(status().isForbidden());
    }
    @Test void patch_withOrganizerRole_returns200() { ... }
}
```

- **`@WebMvcTest`** = 웹 계층만 띄우는 슬라이스. DB·서비스는 안 올라온다 → 빠르다.
- **`@Import(SecurityConfig.class)` 가 필수인 이유**: `@WebMvcTest`는 우리가 만든 `SecurityConfig`를 자동으로 올려주지 않는다. 명시하지 않으면 스프링의 기본 보안 설정으로 테스트하게 되어 **아무것도 검증하지 못한다.**
- **`jwt()` 요청 후처리기**: `spring-security-test`가 제공. 실제 토큰을 만들지 않고 "이 권한을 가진 인증 주체가 요청했다"를 시뮬레이션한다.
- **`@MockitoBean`**: Boot 3.4+에서 `@MockBean`을 대체한 새 애노테이션.

**API.md §16의 권한 매트릭스가 곧 이 테스트들의 명세다.** 문서와 테스트가 1:1로 대응한다.

#### (3) 목 기반 서비스 테스트 — `AttendanceServiceTest`

```java
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {
    @Mock private AttendanceRepository attendanceRepository;
    // ... 리포지토리 5개 전부 목

    @Test void checkIn_allowsInactiveMember_whenSessionAlreadyHasRecordForThem() { ... }
    @Test void checkIn_rejectsInactiveMember_whenNoExistingRecordForThisSession() { ... }
}
```

검수에서 발견된 🔴-2 버그(§6.2)를 수정하면서 **회귀 방지용으로 추가한 테스트**다. "고쳤다"로 끝내지 않고 "다시 깨지면 빨간불이 켜진다"까지 간 것.

`ReflectionTestUtils.setField(session, "id", 1L)` 는 JPA 엔티티의 `id`가 DB가 채워주는 값이라 테스트에서 직접 넣을 방법이 없어서 쓴 기법이다.

---

### 3.12 프론트엔드 스택

#### Vite

번들러 겸 개발 서버. 개발 중에는 브라우저의 **네이티브 ES 모듈**을 그대로 쓰고 필요한 파일만 변환해서 서버가 즉시 뜬다. 프로덕션 빌드는 Rollup으로 번들링한다.

- 환경변수는 **`VITE_` 접두사**가 있는 것만 클라이언트 번들에 주입된다(`import.meta.env.VITE_API_BASE_URL`). 실수로 시크릿이 번들에 섞이는 걸 막는 장치.
- **주의**: 프론트에 들어가는 값은 전부 공개된다. `VITE_` 변수에 비밀을 넣으면 안 된다.

#### React 19 + TypeScript

컴포넌트 = 상태를 받아 UI를 반환하는 함수. `useState`로 상태를, `useEffect`로 부수효과를 다룬다.
§6.1의 버그가 정확히 **React의 마운트/재마운트와 제어/비제어 컴포넌트**에 관한 것이므로 그 절에서 깊게 다룬다.

#### Tailwind v4

유틸리티 CSS. **v3와 설정 방식이 다르다**:

|      | v3                             | v4                                 |
| ---- | ------------------------------ | ---------------------------------- |
| 설정 | `tailwind.config.js` + PostCSS | **`@tailwindcss/vite` 플러그인**   |
| 진입 | `@tailwind base;` 등 3줄       | **`@import "tailwindcss";` 한 줄** |
| 토큰 | config의 `theme.extend`        | CSS의 **`@theme`** 블록            |

이 프로젝트는 `var(--text-strong)`, `var(--accent)` 같은 CSS 변수로 다크모드까지 처리한다.

#### Zustand vs TanStack Query — 상태의 두 종류

**신입이 가장 자주 틀리는 지점이라 정확히 이해해야 한다.**

|           | **클라이언트 상태**                    | **서버 상태**                                   |
| --------- | -------------------------------------- | ----------------------------------------------- |
| 정의      | 이 브라우저에만 존재. 내가 유일한 주인 | 서버가 진짜 주인. 브라우저에 있는 건 **복사본** |
| 예        | 모달 열림 여부, 토큰, 다크모드         | 멤버 목록, 회차 상세, 누적 벌금                 |
| 도구      | **Zustand**                            | **TanStack Query**                              |
| 필요한 것 | 그냥 저장                              | 캐싱, 재검증, 로딩/에러 상태, **무효화**        |

```ts
export const useAuthStore = create<AuthState>()(
  persist((set) => ({ token: null, member: null, ... }), { name: "study-fine-auth" })
);
```

Zustand에는 **토큰과 로그인 멤버만** 둔다. `persist` 미들웨어가 localStorage 동기화를 해준다.

TanStack Query의 핵심은 **무효화(invalidation)** 다. 출석을 저장하면:

```
mutate 성공 → queryClient.invalidateQueries(['sessions']) 등
           → 관련 쿼리들이 "낡음(stale)"으로 표시됨
           → 화면에 있는 것부터 자동 재요청
           → 멤버 목록 누적 벌금, 회차 목록 합계가 저절로 최신화
```

**만약 서버 데이터를 Zustand에도 복사해두면**, 이 무효화가 Zustand까지 도달하지 못해 화면 두 곳이 다른 숫자를 보여주게 된다. 그래서 "서버 데이터는 TanStack Query만 소유"가 규칙이다.

#### Zod — 런타임 스키마 검증

TypeScript 타입은 **컴파일 타임에만** 존재한다. 런타임에 서버가 다른 모양의 JSON을 주면 TS는 아무것도 못 막는다.

```ts
export const LoginResponseSchema = z.object({
  accessToken: z.string(),
  expiresIn: z.number(),
  member: AuthMemberSchema,
});
export type LoginResponse = z.infer<typeof LoginResponseSchema>;
```

- 응답을 `parse`하면 **백엔드 스펙 변경을 즉시** 잡는다(엉뚱한 데서 `undefined`가 터지는 대신, 파싱 지점에서 명확한 에러).
- `z.infer`로 **스키마에서 타입을 뽑아내므로** 스키마와 타입이 절대 어긋나지 않는다.
- 프론트 폼 검증과 응답 파싱에 같은 도구를 쓴다.

#### axios 인터셉터

```ts
apiClient.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token) config.headers.set("Authorization", `Bearer ${token}`);
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const isLoginRequest = error.config?.url?.includes("/api/auth/login");
      if (!isLoginRequest) {
        // ← 이 디테일이 중요
        useAuthStore.getState().logout();
        if (window.location.pathname !== "/login")
          window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  },
);
```

**로그인 요청의 401을 제외하는 이유**: 로그인 시도에서 비밀번호가 틀리면 401이 오는데, 이때 "세션 만료" 리다이렉트를 하면 사용자가 방금 입력한 폼이 날아가고 "왜 튕기지?" 하게 된다. 이 401은 **로그인 폼이 에러 메시지로 처리해야 할** 것이지 세션 만료가 아니다.

---

### 3.13 Docker 멀티스테이지 빌드 + ★ JVM 메모리 구조

#### 멀티스테이지 빌드

```dockerfile
# ── 1단계: 빌드 (JDK 필요) ──
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline    # ← 의존성만 먼저 받는 레이어
COPY src ./src
RUN ./mvnw -q -DskipTests package

# ── 2단계: 실행 (JRE만 있으면 됨) ──
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:MaxMetaspaceSize=180m -XX:+UseSerialGC"
CMD ["java", "-jar", "app.jar"]
```

**왜 두 단계인가.**

1. **이미지 크기**: 최종 이미지에 JDK(컴파일러)·Maven·소스코드가 안 들어간다. JRE + JAR만. 수백 MB 절약.
2. **보안 표면**: 운영 컨테이너에 컴파일러와 빌드 도구가 없다.
3. **레이어 캐싱**: `pom.xml`만 먼저 복사해 `dependency:go-offline`을 돌리는 순서가 핵심이다. 소스만 바뀌면 **의존성 다운로드 레이어는 캐시가 재사용**되어 빌드가 훨씬 빨라진다. (npm 프로젝트에서 `package.json`만 먼저 COPY 하고 `npm ci` 하는 것과 같은 패턴)

#### ★ JVM 메모리 구조 — "왜 자바가 Node보다 무거운가"

이걸 정확히 설명할 수 있으면 인프라 이해도에서 점수를 딴다.

```
컨테이너에 할당된 메모리 (Render 무료 = 512MB)
├── ① 힙(Heap)              -Xmx256m
│   └─ new 로 만든 모든 객체. GC의 대상.
├── ② 메타스페이스(Metaspace) -XX:MaxMetaspaceSize=180m
│   └─ ★ 로드된 "클래스 자체"의 메타데이터. 힙 밖(네이티브 메모리)!
├── ③ 스레드 스택           스레드당 ~1MB × N
│   └─ 톰캣 워커 스레드, GC 스레드 등
├── ④ 코드 캐시(JIT)        컴파일된 네이티브 코드
├── ⑤ GC 자체의 오버헤드    알고리즘마다 다름
└── ⑥ 네이티브 버퍼         NIO 다이렉트 버퍼, JDBC 드라이버 등
```

**핵심 통찰: `-Xmx`는 ①만 제한한다.** ②~⑥은 힙 밖이다. 그래서 "힙을 256MB로 줄였는데 왜 컨테이너가 죽지?"라는 상황이 생긴다.

**Node.js와의 차이**:

|             | Node.js                | JVM                                          |
| ----------- | ---------------------- | -------------------------------------------- |
| 코드 로딩   | JS 소스를 파싱 → V8 힙 | **클래스 파일을 메타스페이스에 로드**        |
| 프레임워크  | 필요한 것만 require    | Spring이 **수천 개 클래스를 스캔·로드**      |
| 부팅 메모리 | 30~80MB                | **250~400MB**                                |
| 이유        | —                      | 메타스페이스 + JIT + 리플렉션 기반 DI 초기화 |

Spring Boot가 뜰 때 하는 일: 컴포넌트 스캔(클래스패스 전수 조사) → 자동 설정 평가 → 빈 생성 → **프록시 클래스 동적 생성**(`@Transactional`, `@PreAuthorize`가 다 프록시다). 여기에 Hibernate가 엔티티 메타모델을 만들고, springdoc이 컨트롤러를 전부 스캔한다. **전부 클래스 로딩 = 메타스페이스 소비**다.

#### 실제 겪은 트러블슈팅 — 메타스페이스 96MB → 180MB

```
시도 1: -Xmx256m -XX:MaxMetaspaceSize=96m
결과:   기동 도중 java.lang.OutOfMemoryError: Metaspace 로 사망
        (힙은 멀쩡한데 클래스 로딩 공간이 부족)

원인:   springdoc(컨트롤러 전수 스캔) + Hibernate(엔티티 메타모델)
        + Spring Security(필터 체인·프록시) 가 클래스를 대량 로드

시도 2: -Xmx256m -XX:MaxMetaspaceSize=180m -XX:+UseSerialGC
결과:   기동 성공. API 반복 호출 후 RSS ~396MB 에서 안정 → 512MB 안에 들어옴
```

**계산**: 256(힙) + 180(메타) = 436MB. 나머지 ~76MB를 스레드 스택·JIT 코드 캐시·네이티브 버퍼 여유로 남겼다.

**왜 `-XX:+UseSerialGC`인가.**

| GC          | 특징                         | 이 상황에서                                        |
| ----------- | ---------------------------- | -------------------------------------------------- |
| G1GC (기본) | 병렬·리전 기반, 큰 힙에 최적 | **자체 자료구조와 GC 스레드가 메모리를 더 먹는다** |
| SerialGC    | 단일 스레드, 자료구조 최소   | ✅ **작은 힙(<300MB)·단일 코어 환경에 유리**       |

무료 티어는 CPU도 공유 1코어 수준이라 GC 병렬화의 이득이 거의 없고, GC 자체의 메모리 오버헤드만 커진다. **작은 컨테이너에서는 SerialGC가 합리적**이다.

**왜 `ENV JAVA_TOOL_OPTIONS`인가.** `CMD`의 `java -jar`에 직접 붙일 수도 있지만, 환경변수로 두면 배포 플랫폼에서 재빌드 없이 오버라이드할 수 있고 JVM이 시작 시 "Picked up JAVA_TOOL_OPTIONS: ..."를 로그에 찍어줘서 **어떤 옵션으로 떴는지 로그로 확인**된다.

> **면접 예상 질문**: "그냥 `-Xmx`만 줄이면 되는 거 아닌가요?"
> **답**: "아닙니다. `-Xmx`는 힙만 제한합니다. Spring은 클래스를 대량 로드하기 때문에 힙 밖의 **메타스페이스**가 실제 병목이었고, 96MB로 잡았을 때 `OutOfMemoryError: Metaspace`로 죽는 걸 실측했습니다. 180MB로 올려서 기동에 성공했고, 힙 256 + 메타 180 = 436MB에 스레드·JIT 여유 76MB를 남기는 배분으로 512MB 안에 맞췄습니다."

---

### 3.14 Neon Postgres + 인덱스 설계

**Neon이 뭔가.** 서버리스 PostgreSQL 호스팅. 컴퓨트와 스토리지가 분리돼 있고, 브랜치(DB 복제)를 만들 수 있다. 무료 티어가 있어 프로젝트당 DB 하나씩 쓰기 좋다.

**연결 문자열 변환 문제 (실제 겪은 이슈).**

Neon이 주는 URL은 이 형태다:

```
postgresql://user:password@ep-xxx.neon.tech/study_fine?sslmode=require
```

그런데 **PostgreSQL JDBC 드라이버(pgjdbc)는 `user:pass@host` 부분을 파싱하지 않는다.** 필요한 형태는:

```
jdbc:postgresql://ep-xxx.neon.tech/study_fine?sslmode=require   + username/password 분리
```

그래서 `DatabaseUrlEnvironmentPostProcessor`를 만들었다.

```java
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String rawUrl = environment.getProperty("DATABASE_URL");
        if (rawUrl == null || rawUrl.isBlank()) return;

        URI uri = URI.create(rawUrl);
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ... ;

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.url", jdbcUrl);
        // userInfo 를 username/password 로 분리
        environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
    }
}
```

**`EnvironmentPostProcessor`가 뭔가.** 스프링 애플리케이션 컨텍스트가 **만들어지기 전**, 환경(설정 프로퍼티)이 준비되는 단계에 끼어드는 확장 지점. `@Component`로는 안 되고(아직 빈 컨테이너가 없다) **`META-INF/spring.factories`에 등록**해야 한다.

```
org.springframework.boot.env.EnvironmentPostProcessor=\
com.jakesoneyo.studyfine.config.DatabaseUrlEnvironmentPostProcessor
```

> 참고: Boot 3부터 자동 설정 등록은 `AutoConfiguration.imports`로 옮겨갔지만, **`EnvironmentPostProcessor`는 여전히 `spring.factories`** 를 쓴다.

#### 인덱스 4개와 그 근거

```sql
CREATE UNIQUE INDEX uq_member_email            ON member (email);
CREATE UNIQUE INDEX uq_study_session_date      ON study_session (session_date);
CREATE UNIQUE INDEX uq_attendance_session_member ON attendance_record (session_id, member_id);
CREATE INDEX        ix_attendance_member       ON attendance_record (member_id);
```

**★ 왜 `member_id` 인덱스를 따로 만드는가 (강력한 면접 소재).**

두 가지 오해를 동시에 짚는다.

1. **"FK를 걸면 인덱스가 자동으로 생기지 않나?"** → **PostgreSQL은 안 만든다.** 참조되는 쪽(`member.id`, PK)에는 인덱스가 있지만, 참조하는 쪽 컬럼(`attendance_record.member_id`)에는 없다. MySQL은 InnoDB가 자동 생성해줘서 습관적으로 착각하기 쉽다.
2. **"복합 유니크 `(session_id, member_id)`가 있으니 `member_id` 조회도 커버되지 않나?"** → **안 된다.** 복합 인덱스는 **선두 컬럼(leftmost prefix)** 부터 써야 유효하다. 전화번호부가 (성, 이름)으로 정렬돼 있으면 "김"으로 시작하는 사람은 빨리 찾지만 "영선"이라는 **이름만**으로는 처음부터 다 훑어야 하는 것과 같다. `WHERE member_id = ?`는 이 인덱스를 못 타고 풀스캔한다.

`GET /api/me/attendances`와 멤버별 누적 벌금 집계가 정확히 `member_id`로 필터링하므로 단독 인덱스가 필요하다.

**반대로 만들지 않은 것들** (인덱스는 공짜가 아니다 — 쓰기 비용 + 용량):

| 안 만든 인덱스                      | 이유                                                  |
| ----------------------------------- | ----------------------------------------------------- |
| `attendance_record.session_id` 단독 | 복합 유니크의 **선두 컬럼**이라 이미 커버됨 (중복)    |
| `member.role`, `member.active`      | 카디널리티 2~3. 수십 행 테이블에선 풀스캔이 더 빠르다 |
| `attendance_record.status`          | 집계는 어차피 전체 스캔                               |

**CHECK 제약과 enum 저장 방식.**

```sql
role VARCHAR(20) NOT NULL CHECK (role IN ('ORGANIZER', 'MEMBER'))
```

- `@Enumerated(EnumType.ORDINAL)`은 **금지**. enum 상수의 **순서만 바꿔도** 기존 데이터의 의미가 통째로 뒤집힌다(`MEMBER=0, ORGANIZER=1`이었는데 순서를 바꾸면 모든 멤버가 운영자가 된다).
- PostgreSQL 네이티브 `ENUM` 타입은 값 추가/삭제 마이그레이션이 번거롭다.
- **`VARCHAR` + `CHECK`** 가 가독성(DB를 직접 열어봐도 읽힌다)과 변경 용이성의 균형점.

**FK 삭제 정책이 좌우로 다른 이유.**

```sql
session_id BIGINT NOT NULL REFERENCES study_session (id) ON DELETE CASCADE
member_id  BIGINT NOT NULL REFERENCES member (id)        ON DELETE RESTRICT
```

- **회차 삭제 → CASCADE**: 그 회차의 출석 기록은 존재 의미가 없다.
- **멤버 삭제 → RESTRICT**: 벌금 근거가 날아간다. 앱은 항상 `active=false`(soft delete)로 처리하므로 이 제약에 걸릴 일이 없고, **걸린다면 그건 잘못된 코드라는 신호**다.

**금액 타입이 `integer`인 이유.** KRW는 소수점이 없다. `BigDecimal`은 과설계고, `integer` 최대 21억 원이면 스터디 벌금엔 차고 넘친다. 단, **합계는 `long`** 으로 받아야 한다(`SUM(integer)` → `bigint`).

---

## 4. 핵심 설계 결정 딥다이브

### 4.1 ★ 벌금 스냅샷 — 이 프로젝트의 제1 설계 결정

#### 문제 상황

```mermaid
sequenceDiagram
    participant O as 운영자
    participant AS as AttendanceService
    participant FP as FinePolicy
    participant AR as attendance_record

    Note over O: 1월 — 지각 단가 3,000원
    O->>AS: 1월 회차 출석 체크 (LATE)
    AS->>FP: calculate(LATE, room{late:3000})
    FP-->>AS: 3000
    AS->>AR: INSERT fine_amount = 3000  ← 확정 저장

    Note over O: 2월 — 단가를 5,000원으로 인상
    O->>AS: PATCH /api/study-room {lateFineAmount:5000}
    Note over AR: 1월 기록은 3000 그대로 (건드리지 않음)

    O->>AS: 2월 회차 출석 체크 (LATE)
    AS->>FP: calculate(LATE, room{late:5000})
    FP-->>AS: 5000
    AS->>AR: INSERT fine_amount = 5000
```

**만약 `fine_amount`를 저장하지 않고 조회할 때마다 현재 단가로 계산한다면?** 운영자가 단가를 올리는 순간 **과거 벌금이 전부 소급 인상**된다. 1월에 3,000원 내기로 하고 지각한 사람이 갑자기 5,000원을 내야 한다. 이건 명백한 도메인 규칙 위반이다.

#### "그건 비정규화 아닌가요?"에 대한 답 (압박 질문 대비)

이 질문은 반드시 나온다. 답변 논리를 세 단계로 준비한다.

**① 정규화가 금지하는 건 "중복"이지 "사실의 기록"이 아니다.**

정규화의 목적은 **갱신 이상(update anomaly)** 방지다. 같은 사실이 두 곳에 있으면 한쪽만 고쳐 불일치가 생기는 것. 그런데 `attendance_record.fine_amount`는 `study_room.late_fine_amount`의 **복사본이 아니다.** 서로 다른 두 개념이다:

| 컬럼                            | 의미                                    | 시간축      |
| ------------------------------- | --------------------------------------- | ----------- |
| `study_room.late_fine_amount`   | **현재** 벌금 단가 (설정값, FineRate)   | 항상 "지금" |
| `attendance_record.fine_amount` | 그 출석 기록에 **확정된 부과액** (Fine) | "그 시점"   |

용어집(`UBIQUITOUS_LANGUAGE.md`)에서 이 둘을 **FineRate(벌금 단가) vs Fine(벌금)** 으로 이름부터 분리해둔 이유다. 이름이 같으면 개발자가 헷갈리고, 헷갈리면 코드가 틀린다.

**② 실무의 표준 패턴이다.**

주문 라인아이템에 **주문 당시 상품 가격**을 박아두는 것과 정확히 같다. 상품 가격이 오른다고 작년 영수증 금액이 바뀌면 그건 회계 사고다. 계약서에 서명 당시 조건을 적어두는 것, 급여 명세서에 그 달의 세율을 적어두는 것 — 전부 같은 패턴이다.

**③ "그럼 이력 테이블(rate history)을 만들면 되지 않나?"**

가능하지만 더 복잡하다. `fine_rate_history(effective_from, late_amount, absent_amount)`를 두고 조회할 때마다 회차 날짜에 맞는 단가를 찾아 조인해야 한다. 얻는 것: 단가 변경 이력 조회. 잃는 것: 모든 조회 쿼리에 시간 범위 조인 추가.

**S티어 범위에서는 스냅샷 하나로 충분하다.** 단가 변경 이력 자체가 요구사항이 아니기 때문이다. 나중에 "언제 단가를 올렸는지 보고 싶다"가 요구사항이 되면 그때 이력 테이블을 추가하면 되고, **기존 `fine_amount`는 그대로 두면 되므로 확장 경로가 막히지 않는다.**

#### 소급 오염 경로가 코드상 존재하지 않는다는 증명

검수에서 확인한 사실:

- `StudyRoomService.update`는 `room.updateRates(...)`만 호출한다. **`AttendanceRepository`/`AttendanceRecord`를 import조차 하지 않는다.** 단가 변경이 과거 기록에 닿을 코드 경로가 물리적으로 없다.
- `AttendanceRecord.fineAmount`를 바꾸는 유일한 통로는 `updateStatus(status, fineAmount)`이고, 호출부는 `AttendanceService.checkIn` **한 곳뿐**이다(= 신규 출석 체크).
- `FinePolicyTest.snapshot_pastResultDoesNotChangeWhenRateChangesLater`가 이 불변식을 테스트로 잠갔다.

> **면접에서 이렇게 말한다**: "설계 원칙을 문서에만 적어두면 나중에 누가 깹니다. 그래서 ① 단가 변경 서비스가 출석 리포지토리를 참조조차 못 하게 의존 방향을 잘랐고, ② `fineAmount`를 바꾸는 메서드를 하나로 좁혔고, ③ 그 불변식을 테스트로 고정했습니다. 세 겹입니다."

### 4.2 누적 벌금은 저장하지 않는다

반대로 `member.total_fine` 같은 **합계 컬럼은 두지 않았다.**

**왜.** 출석 기록이 갱신될 때마다 두 곳을 맞춰야 한다. 어긋나면 **진실이 두 개**가 된다. 특히 회차 삭제(CASCADE)가 일어나면 합계 컬럼도 보정해야 하는데, 이런 보정 로직은 반드시 어딘가에서 빠진다.

**성능은?** 스터디 규모는 멤버 수십 명 × 회차 수십 개 = **수천 행**. 집계 비용은 무시 가능하다. 성능이 문제되는 규모(수십만 행)가 오면 그때 구체화 뷰(materialized view)나 집계 테이블을 얹는 게 순서다. **지금 넣는 건 과설계다.**

**부수 효과 하나 더**: 회차를 삭제하면 그 회차분 벌금이 누적에서 **자연히 빠진다.** 보정 로직이 아예 필요 없다.

### 4.3 ★ N+1 방지 3종 세트

| 지점                         | 나이브 구현의 문제                                   | 이 프로젝트의 대응                            |
| ---------------------------- | ---------------------------------------------------- | --------------------------------------------- |
| 멤버 목록 + 누적 벌금        | 멤버 N명 → 벌금 합계 쿼리 N번                        | **① GROUP BY projection 단일 쿼리**           |
| 회차 목록 + 회차별 벌금 합계 | 회차 N개 → 합계 N번                                  | ① 동일 패턴 (`findAllWithSummary`)            |
| 회차 상세 (출석 현황)        | 출석 M건 → `record.getMember().getName()`마다 SELECT | **② `@EntityGraph(attributePaths="member")`** |
| 내 출석 내역                 | 기록마다 회차 SELECT                                 | ② `join fetch ar.studySession`                |
| 출석 체크 bulk 저장          | 멤버마다 조회/저장                                   | **③ 사전 1회 조회 + Map 패턴**                |
| 전역                         | 뷰 렌더 단계 지연 로딩이 문제를 숨김                 | **④ `open-in-view: false`**                   |

#### ① GROUP BY projection

```sql
SELECT m.id AS id, m.name AS name, ...,
       COALESCE(SUM(ar.fine_amount), 0)                 AS accumulatedFine,
       COUNT(ar.id) FILTER (WHERE ar.status = 'LATE')   AS lateCount,
       COUNT(ar.id) FILTER (WHERE ar.status = 'ABSENT') AS absentCount
FROM member m
LEFT JOIN attendance_record ar ON ar.member_id = m.id
GROUP BY m.id
ORDER BY accumulatedFine DESC, m.name
```

- **`LEFT JOIN`이어야** 출석 기록이 없는 신규 멤버도 0원으로 나온다. `INNER JOIN`이면 신규 멤버가 목록에서 사라진다.
- **`COALESCE(SUM(...), 0)`**: 매칭되는 행이 없으면 `SUM`은 `NULL`이다. 0으로 바꿔줘야 한다.
- **`FILTER (WHERE ...)`**: PostgreSQL 문법. 한 번의 스캔으로 조건별 카운트를 동시에 뽑는다.
- **엔티티로 받아서 자바에서 합계를 내면 그게 N+1이다.** 프로젝션으로 받는 게 핵심.

#### ③ 사전 조회 + Map 패턴 (bulk upsert)

```java
// ⛔ 이렇게 하면 N+1
for (Item item : request.attendances()) {
    AttendanceRecord existing = repo.findBySessionAndMember(sessionId, item.memberId());  // 항목마다 쿼리!
    ...
}

// ✅ 실제 구현 — 루프 진입 전에 1회 조회해서 Map으로
Map<Long, AttendanceRecord> existingByMember =
    attendanceRepository.findByStudySessionId(sessionId).stream()
        .collect(Collectors.toMap(r -> r.getMember().getId(), r -> r));

for (Item item : request.attendances()) {
    int fineAmount = FinePolicy.calculate(item.status(), room);
    AttendanceRecord existing = existingByMember.get(item.memberId());   // O(1) 메모리 조회
    if (existing != null) existing.updateStatus(item.status(), fineAmount);   // 더티 체킹
    else newRecords.add(new AttendanceRecord(session, membersById.get(item.memberId()), ...));
}
attendanceRepository.saveAll(newRecords);
```

**추가 최적화 하나 더**: 이 `existingByMember` 맵을 **검증 단계에서도 재사용**한다.

```java
boolean allExistAndAllowed = requestedIds.stream()
    .allMatch(id -> membersById.containsKey(id)
        && (membersById.get(id).isActive() || existingByMember.containsKey(id)));
```

"비활성 멤버라도 이 회차에 이미 기록이 있으면 허용"이라는 규칙을 **추가 쿼리 없이** 판정한다. 이미 가져온 자료구조를 두 목적에 쓴 것.

> **면접 검증 포인트**: "루프 안에 리포지토리 호출이 0건"이라고 말할 수 있는 게 이 코드의 자랑이다. 실제로 `checkIn` 메서드의 for 루프 안에는 `FinePolicy.calculate`(순수 계산)와 컬렉션 조작만 있다.

#### 검증 방법

```yaml
# local 프로파일에서만
spring.jpa.properties.hibernate.generate_statistics: true
```

Hibernate가 실행한 쿼리 수를 로그로 찍어준다. 멤버 수를 늘려도 쿼리 수가 그대로인지 눈으로 확인했다.

> **한계 인정 (정직하게)**: S티어라 **자동화된 쿼리 카운트 테스트는 없다.** 실제 DB가 필요해서 Testcontainers가 있어야 한다. "다음 M티어 프로젝트에서는 쿼리 수를 어서션으로 고정하겠다"가 정직한 답변이다. (참고: 이 워크스페이스의 `match-mate`는 Testcontainers로 쿼리 수를 실측해 고정했다 — 두 프로젝트를 엮어 말할 수 있다.)

### 4.4 ★ 소유권 격리를 "비교"가 아니라 "구조"로

#### 문제: 수평 권한 상승 (Horizontal Privilege Escalation)

`@PreAuthorize("hasRole('MEMBER')")`만으로는 **"MEMBER가 남의 id를 넣어 조회"** 를 못 막는다. 롤은 맞으니까 통과한다.

**흔한 대응 (이 프로젝트가 피한 방식)**:

```java
// ⛔ 이 방식의 위험
@GetMapping("/api/members/{id}/attendances")
public ... history(@PathVariable Long id, @CurrentMemberId Long currentId, Authentication auth) {
    if (!isOrganizer(auth) && !id.equals(currentId)) {   // ← 이 한 줄을 빠뜨리면 즉시 취약점
        throw new AccessDeniedException();
    }
    ...
}
```

엔드포인트가 늘어날 때마다 이 비교를 반복해야 하고, **한 곳에서 빠뜨리면 그게 바로 정보 유출**이다. 코드 리뷰로 매번 잡아야 한다.

#### 이 프로젝트의 해법: 경로를 두 개로 나눈다

| 용도          | 경로                                | 권한         | id 파라미터           |
| ------------- | ----------------------------------- | ------------ | --------------------- |
| **본인 조회** | `GET /api/me/attendances`           | 🔑 AUTH      | **없음** (토큰 `sub`) |
| **타인 조회** | `GET /api/members/{id}/attendances` | 👑 ORGANIZER | 있음                  |

```java
// AttendanceController — 본인 조회
@GetMapping("/api/me/attendances")
public AttendanceHistoryResponse myHistory(@CurrentMemberId Long memberId) { ... }
```

`@CurrentMemberId`는 JWT의 `sub` 클레임을 꺼내주는 커스텀 애노테이션이다. **클라이언트가 id를 보낼 문법 자체가 URL에 없다.** 남의 데이터를 지칭할 방법이 존재하지 않는다.

그리고 id를 받는 경로는 **통째로 ORGANIZER 전용**이다. MEMBER는 `{id}`에 **자기 id를 넣어도 403**이다.

> **"자기 id인데 왜 403이죠? 불친절하지 않나요?"** (예상 꼬리질문)
> **답**: "본인 조회는 `/api/me/attendances`라는 전용 경로가 있으니 기능 손실이 없습니다. 대신 id를 받는 경로를 예외 없이 ORGANIZER 전용으로 유지하면, 나중에 이 컨트롤러에 엔드포인트를 추가하는 사람이 소유권 비교를 잊어도 취약점이 생기지 않습니다. **실수할 수 있는 자리를 없앤 것**이 이 설계의 요점입니다."

이건 보안 설계 원칙 중 **"안전한 기본값(secure by default)"** 과 **"실수하기 어려운 API"** 의 사례다.

### 4.5 그 외 설계 결정 요약

| 결정                                   | 이유                                                                                             |
| -------------------------------------- | ------------------------------------------------------------------------------------------------ |
| **StudyRoom = 단일 행 테이블**         | 벌금 단가를 `application.yml`이나 상수로 두면 운영자가 못 바꾼다. 재배포 없이 런타임 수정 필요   |
| `CHECK (id = 1)`                       | 두 번째 행 생성을 **DB 레벨**에서 차단. 멀티테넌시 확장 시 이 CHECK만 떼면 되어 경로가 안 막힌다 |
| `member`에 `study_room_id` 컬럼 없음   | 값이 항상 1이라 **정보량 0**. 지금 넣으면 모든 쿼리에 무의미한 조건이 붙는다 (ponytail)          |
| **Member = 참여자 + 로그인 주체 통합** | 단일 스터디룸 전제에서 `User`/`Member` 분리는 과설계. 멀티룸 확장 시 비로소 분리가 필요해진다    |
| **멤버는 soft delete**                 | 물리 삭제하면 출석 이력·벌금 근거가 사라지거나 FK 위반. `active=false`로 명단에서만 내린다       |
| **회차를 별도 테이블로**               | 출석 기록에 날짜를 직접 두면 "만들어는 뒀는데 아직 체크 안 한 회차"가 표현 불가. 제목도 못 붙임  |
| `session_date`가 `date` 타입           | 모임은 "그 날"의 일이지 시각의 일이 아니다. `timestamptz`면 타임존 때문에 날짜가 하루 밀린다     |
| **공개 회원가입 없음**                 | 닫힌 스터디 모임이라는 도메인 특성. 멤버 계정은 운영자가 생성                                    |
| **마지막 운영자 강등/비활성 차단**     | 스스로를 잠가버리는 사고 방지 → 409 `운영자가 최소 1명 필요합니다`                               |

---

## 5. 실제로 겪은 함정 10선 (면접 스토리 원재료)

> 각 항목을 **증상 → 원인 → 해결 → 배운 것** 형태로 정리했다. 면접에서는 이 구조 그대로 말하면 된다.

### ① Jackson 3 전환 — `com.fasterxml.jackson` import로 빈 생성 실패

- **증상**: 애플리케이션 기동 시 빈 생성 실패. `SecurityConfig`가 `ObjectMapper`를 주입받지 못함.
- **원인**: Spring Boot 4는 **Jackson 3(`tools.jackson.*`)** 를 쓴다. 스프링이 등록한 `ObjectMapper` 빈은 `tools.jackson.databind.ObjectMapper` 타입인데, 습관대로 `com.fasterxml.jackson.databind.ObjectMapper`를 import하니 **타입이 달라 주입 대상이 없었다.**
- **해결**: import를 `tools.jackson.databind.ObjectMapper`로 교체.
- **배운 것**: 메이저 버전 업그레이드에서 **패키지 루트가 바뀌는 변경**은 IDE 자동완성이 오히려 함정이 된다(옛 패키지도 여전히 클래스패스에 있을 수 있다). 그리고 이 사실은 **jjwt를 배제한 결정(ADR-1)의 근거**이기도 하다 — `jjwt-jackson`이 Jackson 2 바인딩이라 같은 충돌을 일으켰을 것이다.

### ② `NimbusJwtEncoder`가 기본 RS256을 기대 — "Failed to select a JWK signing key"

- **증상**: 로그인 API 호출 시 `Failed to select a JWK signing key` 예외.
- **원인 (두 겹)**:
  1. `OctetSequenceKey`(대칭키 JWK)를 만들 때 **`algorithm(JWSAlgorithm.HS256)`을 지정하지 않으면** 인코더가 어떤 키를 써야 할지 못 고른다.
  2. `JwtEncoderParameters.from(claims)`만 쓰면 **JWS 헤더의 알고리즘 기본값이 RS256**으로 잡힌다. HS256 대칭키 JWK와 매칭되지 않는다.
- **해결**: 양쪽 모두 HS256을 명시.
  ```java
  // JwtConfig — JWK에 alg 명시
  JWK jwk = new OctetSequenceKey.Builder(jwtSecretKey)
      .algorithm(JWSAlgorithm.HS256)
      .keyID(UUID.randomUUID().toString())
      .build();

  // TokenService — 헤더에도 HS256 명시
  JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
  jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
  ```
- **배운 것**: Spring Security의 JWT 지원은 **RSA(비대칭) 시나리오를 1급으로 가정**하고 만들어져 있다. HS256 대칭키를 쓰려면 알고리즘을 두 지점에서 명시해야 한다. 편의 API의 기본값이 내 시나리오와 다를 수 있다는 걸 배웠다.

### ③ springdoc 2.x → 3.1.0 버전 조사

- **증상**: 습관적으로 springdoc 2.x를 넣으면 Boot 4에서 기동 불가.
- **원인**: springdoc 2.x 라인은 Spring Boot 3(Spring Framework 6) 전용. Boot 4는 Framework 7 + Jackson 3이라 호환되지 않는다.
- **해결**: Boot 4 지원 라인이 springdoc **3.x**임을 확인하고, Maven Central에서 실제 존재하는 최신 안정 버전(**3.1.0**)을 조사해 `<version>`으로 핀했다. 부모 POM이 관리 안 해주는 서드파티라 명시가 필수.
- **배운 것**: "라이브러리 최신 버전 = 내 프레임워크와 호환"이 아니다. 프레임워크 메이저 업그레이드 시 **모든 서드파티의 호환 라인을 개별 확인**해야 한다. 추측하지 말고 저장소에서 실제 버전을 확인할 것.

### ④ Spring Security 7 — CSRF로 쓰기 요청 전부 403

- **증상**: `GET`은 200인데 `POST`/`PUT`/`PATCH`/`DELETE`가 **전부 403**.
- **함정성**: 403은 "권한 문제"로 보인다. `@PreAuthorize`, 롤 접두사, 토큰 클레임을 한참 뒤지게 만든다.
- **해결**: `.csrf(AbstractHttpConfigurer::disable)` + **왜 꺼도 되는지를 주석으로**.
- **진단 규칙 (외워둘 것)**: **"GET은 되는데 쓰기만 403이면 CSRF부터 의심한다."** 롤 문제라면 GET도 같이 막혔을 것이다.
- **배운 것**: 프레임워크 업그레이드에서는 **증상이 원인과 다른 이름으로 나타난다**. 마이그레이션 가이드를 먼저 읽는 게 디버깅보다 빠르다.

### ⑤ `authorizeRequests()` 제거 — 인터넷 예제가 컴파일 안 됨

- **증상**: 블로그·StackOverflow의 Security 설정 코드가 그대로는 컴파일조차 안 됨.
- **원인**: Security 7에서 `authorizeRequests()`, `antMatchers()`, `mvcMatchers()` 전부 제거.
- **해결**: `authorizeHttpRequests()` + `requestMatchers()` 조합.
- **배운 것**: Spring Security는 5→6→7로 오면서 DSL이 크게 바뀌었다. 검색 결과의 코드를 쓸 때는 **버전을 먼저 확인**해야 한다. 공식 마이그레이션 문서가 가장 빠른 길.

### ⑥ Flyway — `Unsupported Database: PostgreSQL`

- **증상**: 기동 시 Flyway가 `Unsupported Database: PostgreSQL <버전>`으로 실패.
- **원인**: Flyway 10부터 **DB별 지원 모듈이 분리**됐다. `spring-boot-starter-flyway`만으로는 PostgreSQL을 못 다룬다.
- **해결**: `flyway-database-postgresql` 아티팩트를 명시적으로 추가.
- **배운 것**: 스타터가 "다 해준다"고 가정하면 안 된다. 특히 플러그인 아키텍처를 쓰는 라이브러리는 코어 + 어댑터를 함께 넣어야 한다.

### ⑦ Neon URL 형식 ↔ pgjdbc 형식 불일치

- **증상**: `DATABASE_URL`을 그대로 `spring.datasource.url`에 넣으면 커넥션 실패.
- **원인**: Neon은 `postgresql://user:pass@host/db` (URI 스타일)를 주는데, **pgjdbc는 URI의 `user:pass@` 부분을 파싱하지 않는다.** `jdbc:postgresql://host/db` + username/password 분리가 필요하다.
- **해결**: `EnvironmentPostProcessor`로 애플리케이션 컨텍스트 생성 **이전**에 변환해서 `spring.datasource.*` 프로퍼티로 주입. `META-INF/spring.factories`에 등록.
- **배운 것**: 스프링의 확장 지점은 생명주기 단계별로 다르다. 빈이 만들어지기 전에 개입해야 하는 일은 `EnvironmentPostProcessor`, 빈 생성 후는 `BeanPostProcessor`, 기동 완료 후는 `ApplicationRunner`.

### ⑧ ★ Render 512MB에 JVM 앱 배포 — 메타스페이스 OOM

- **증상**: 컨테이너가 기동 도중 `java.lang.OutOfMemoryError: Metaspace`로 죽음. "떴다가 죽는" 상태.
- **원인**: `-Xmx256m -XX:MaxMetaspaceSize=96m`로 시작했는데, **힙이 아니라 메타스페이스가 먼저 말랐다.** springdoc(컨트롤러 전수 스캔) + Hibernate(엔티티 메타모델) + Spring Security(필터·프록시 동적 생성)가 클래스를 대량 로드한다.
- **해결**: 메타스페이스 **180MB**로 상향 + **SerialGC**. 기동 성공, API 반복 호출 후 RSS **~396MB에서 안정** → 512MB 안에 들어옴.
- **왜 워크스페이스 규칙(Spring은 로컬 쇼케이스)의 예외가 됐나**: 규칙이 "Spring은 512MB에서 OOM 위험"이었는데, **실측으로 안 죽는 배분을 찾아냈기 때문에** 예외적으로 라이브 배포했다. 규칙을 무시한 게 아니라 규칙의 전제를 검증한 것.
- **배운 것**: `-Xmx`는 힙만 제한한다. 컨테이너 메모리 = 힙 + **메타스페이스** + 스레드 스택 + JIT 코드 캐시 + 네이티브 버퍼. JVM을 작은 컨테이너에 넣을 때는 **힙 밖의 영역이 진짜 병목**일 수 있다.

### ⑨ Vercel(프론트) ↔ Render(백엔드) 엔드투엔드 연결

- **문제 1 — CORS**: 도메인이 다르므로 브라우저가 preflight(`OPTIONS`)를 보내고, 서버가 허용하지 않으면 요청이 차단된다.
  - **해결**: `CORS_ALLOWED_ORIGINS`를 Render 환경변수로 주입하고 **명시적 화이트리스트**로만 허용. 와일드카드 `*` 금지. 허용 메서드/헤더도 목록으로 제한.
  - **왜 `*`가 안 되나**: `*`는 "인터넷의 모든 사이트가 이 API를 호출해도 된다"는 뜻이다. 게다가 `allowCredentials: true`와 `*`는 조합 자체가 스펙상 금지다.
- **문제 2 — 콜드스타트**: Render 무료 티어는 **15분 미접속 시 슬립**한다. 다음 요청에서 깨어나는 데 **30~60초**. JVM 부팅까지 겹치면 첫 로그인이 느리다.
  - **대응**: README에 명시했다. 숨기지 않고 "이건 무료 티어의 트레이드오프"라고 쓴 것.
  - **개선안 (면접에서 말할 것)**: 유료 티어로 상시 가동 / 외부 cron으로 주기적 핑(무료 인스턴스 시간을 소진하므로 계정 전체 750시간 한도와 트레이드오프) / GraalVM 네이티브 이미지로 부팅 시간·메모리 대폭 절감.
- **배운 것**: "로컬에서 되는 것"과 "두 클라우드에 나눠 배포된 것"은 다른 문제다. 오리진·프로토콜·콜드스타트·환경변수 주입 시점이 전부 변수가 된다.

### ⑩ 로컬 JDK PATH 부재 — `Unable to locate a Java Runtime`

- **증상**: `./mvnw test`가 즉시 실패.
- **원인**: 개발 머신의 `java`가 PATH에 없었다(Homebrew openjdk는 기본적으로 PATH에 안 걸린다).
- **해결**: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test`. **그리고 이 전제를 README에 명시했다.**
- **배운 것**: "내 환경에서만 되는 실행법"은 문서화되지 않으면 남이 클론했을 때 첫 5분에 막힌다. CI(temurin 21)는 정상 동작했기 때문에 **CI 그린 ≠ 로컬 재현 가능**이라는 것도 확인했다.

---

## 6. 검수에서 발견된 버그 3건 해부

> 배포 전 검수(`REVIEW.md`)에서 🔴 blocker 3건이 나왔고 전부 수정 후 배포했다.
> 특히 🔴-1은 **프론트 개발자가 흔히 겪는 실수**라 면접 소재로 아주 좋다.

### 6.1 🔴-1 — 멤버 수정 모달이 역할을 조용히 강등시킨 버그 (React)

#### 증상 두 가지

- **재현 A**: 멤버 "수정" → 아무것도 안 고치고 "저장" → **400 "요청 본문 검증에 실패했습니다"**
- **재현 B**: ORGANIZER 멤버의 이름만 바꾸고 저장 → **운영자가 멤버로 강등됨** (데이터 손상)

#### 원인 — 두 개의 React 실수가 겹쳤다

**실수 1: `key`를 잘못된 컴포넌트에 걸었다.**

```tsx
// ⛔ 버그 버전
<EditMemberModal member={editTarget} onClose={...} />   // 항상 렌더링됨
//   내부에서:  <Modal key={member?.id ?? "none"} ...>  // key가 안쪽 Modal에 붙어 있음
```

`EditMemberModal`이 **조건 없이 항상 렌더링**되고 있었다. 그래서 `EditMemberModal` 자신은 **최초 1회만 마운트**되고, 그 시점의 `member`는 `null`이었다.

```tsx
const [name, setName] = useState(member?.name ?? ""); // → "" 로 고정
const [role, setRole] = useState<"MEMBER" | "ORGANIZER">(
  member?.role ?? "MEMBER",
); // → "MEMBER" 로 고정
```

**`useState`의 인자는 "초기값"이지 "현재값"이 아니다.** 컴포넌트가 처음 마운트될 때 한 번만 평가된다. 이후 `member` prop이 바뀌어도 state는 **절대 자동으로 따라가지 않는다.** 이게 React 신입이 가장 많이 틀리는 지점이다.

**실수 2: 제어 컴포넌트와 비제어 컴포넌트를 섞었다.**

```tsx
// ⛔ 버그 버전 — 비제어(defaultValue)
<input defaultValue={member?.name} />
<select defaultValue={member?.role}>
```

| 방식                     | 코드                          | 값의 진짜 주인  |
| ------------------------ | ----------------------------- | --------------- |
| **제어(controlled)**     | `value={name} onChange={...}` | **React state** |
| **비제어(uncontrolled)** | `defaultValue={...}`          | **DOM 자신**    |

`defaultValue`도 **초기값일 뿐**이지만, 렌더링 타이밍상 화면에는 올바른 값이 보였다. **그래서 화면은 멀쩡한데 state는 계속 초기값(`""`, `"MEMBER"`)** 이었고, 제출 시 `{ name: "", role: "MEMBER" }`가 그대로 전송됐다.

- `name: ""` → 서버 `@Size(min=1)` 위반 → **400**
- `role: "MEMBER"` → ORGANIZER를 **강등**

데모 시드에는 운영자가 admin 하나뿐이라 서버의 "마지막 운영자 보호" 가드에 걸려 **409 "운영자가 최소 1명 필요합니다"** 라는, 이름 수정과 무관한 엉뚱한 에러가 떴다. 운영자를 2명 이상 만들면 가드가 안 걸리고 **실제로 강등이 성공**한다.

#### 수정

```tsx
// ✅ ① 조건부 렌더 + key를 컴포넌트 자체에
{editTarget && (
  <EditMemberModal
    key={editTarget.id}          // ← 대상이 바뀌면 완전히 새 인스턴스로 재마운트
    member={editTarget}
    onClose={() => setEditTarget(null)}
  />
)}

// ✅ ② prop 타입을 non-nullable 로 (member: MemberSummary — 이제 null 불가)
const [name, setName] = useState(member.name);
const [role, setRole] = useState<"MEMBER" | "ORGANIZER">(member.role);

// ✅ ③ 전부 제어 컴포넌트로 통일
<input value={name} onChange={(e) => setName(e.target.value)} />
<select value={role} onChange={(e) => setRole(e.target.value as ...)}>

// ✅ ④ PATCH 의미에 맞게 "실제로 바뀐 필드만" 전송
const request: { name?: string; role?: "MEMBER" | "ORGANIZER" } = {};
if (trimmed !== member.name) request.name = trimmed;
if (role !== member.role) request.role = role;
if (Object.keys(request).length === 0) { onClose(); return; }   // 변경 없으면 요청 자체를 안 보냄
```

#### 배운 것 (면접에서 이렇게 정리한다)

> "React의 `key`는 리스트 렌더링용 최적화 힌트로만 알고 있었는데, 사실은 **'이 컴포넌트 인스턴스가 같은 것인가 다른 것인가'를 React에게 알려주는 정체성(identity)** 입니다. `key`가 바뀌면 React는 기존 인스턴스를 버리고 새로 마운트하므로 `useState` 초기값이 **다시 평가**됩니다. '모달마다 다른 대상을 편집한다'는 요구사항에는 이 재마운트가 정확히 맞는 도구였고, `key`를 안쪽 `Modal`이 아니라 **상태를 가진 컴포넌트 자체**에 걸어야 했습니다.
> 그리고 `defaultValue`(비제어)와 state를 섞으면 **화면에 보이는 값과 전송되는 값이 갈라집니다.** 이 버그가 무서웠던 건 400 에러가 아니라, 화면상으로는 아무 문제가 없어 보이는데 **권한이 조용히 강등되는 데이터 손상**이었다는 점입니다."

**꼬리질문 대비**:

- _"`useEffect`로 prop이 바뀔 때 state를 동기화하면 안 되나요?"_ → "가능하지만 렌더 한 번 뒤에 동기화되므로 중간에 낡은 값이 보이는 순간이 생기고, 의존성 배열 관리가 늘어납니다. React 공식 문서도 '**key로 상태를 리셋하는 것**'을 우선 권장합니다. 여기선 편집 대상이 바뀌면 폼 전체가 새로 시작되는 게 맞으므로 key가 더 정확한 표현입니다."
- _"왜 PATCH에서 변경된 필드만 보내나요?"_ → "PATCH는 부분 수정 의미이고, 안 보낸 필드는 서버가 건드리지 않습니다(`updateProfile`이 null 체크로 처리). 전부 보내면 '안 바꾼 필드'가 사실은 '현재 화면 값으로 덮어쓰기'가 되어, 이번 버그처럼 **의도치 않은 변경**이 섞일 수 있습니다."

### 6.2 🔴-2 — API 계약 불일치: 비활성 멤버가 있는 회차는 재저장 불가

#### 증상

멤버 관리에서 `김스터디`를 비활성화 → 그가 기록을 가진 과거 회차의 출석을 다시 저장 → **400 "존재하지 않거나 비활성인 멤버가 포함되어 있습니다"**. 그 회차는 **영구히 저장 불가** 상태가 된다.

#### 원인 — 두 엔드포인트가 서로 다른 말을 하고 있었다

| 엔드포인트                                       | 비활성 멤버 규칙                                                                  |
| ------------------------------------------------ | --------------------------------------------------------------------------------- |
| `GET /api/sessions/{id}` (#12, 회차 상세)        | **"이미 기록이 있는 비활성 멤버는 포함"** — 과거 기록을 화면에서 지우지 않기 위해 |
| `PUT /api/sessions/{id}/attendances` (#14, 저장) | **"활성 멤버만 허용"**                                                            |

프론트는 저장 시 **화면에 뜬 전원**(비활성 포함)을 그대로 실어 보낸다. 즉 **"화면에 보이는데 저장은 안 되는"** 상태.

```java
// ⛔ 버그 버전
boolean allActiveAndExist = requestedIds.stream()
    .allMatch(id -> membersById.containsKey(id) && membersById.get(id).isActive());
```

SPEC의 O-7("비활성 멤버는 출석 체크 화면에 안 뜨지만 과거 기록은 그대로 조회된다")과 O-3("멤버 전원 상태를 한 번에 저장")이 **동시에 깨졌다.**

#### 수정 — 서버 쪽을 택했다 (프론트 아님)

```java
// ✅ "활성 멤버 또는 이 회차에 이미 기록이 있는 멤버"
boolean allExistAndAllowed = requestedIds.stream()
    .allMatch(id -> membersById.containsKey(id)
        && (membersById.get(id).isActive() || existingByMember.containsKey(id)));
```

**왜 서버인가.**

1. **어차피 `existingByMember` 맵을 이미 1회 조회해뒀다** → 추가 쿼리 0건으로 판정 가능.
2. 프론트에서 payload를 거르는 방식은 화면 하나를 고칠 뿐, **서버 계약(#12 ↔ #14)의 불일치는 그대로 남는다.** 다른 클라이언트(모바일 앱, 스크립트)가 붙으면 같은 문제가 재발한다.
3. **보안적으로도 서버가 최종 판정자여야 한다.**

**중요: 규칙을 느슨하게 푼 게 아니다.** "이 회차에 기록이 **없는** 비활성 멤버를 새로 체크"하는 건 여전히 400으로 막힌다. 두 케이스를 정확히 구분하는 테스트를 추가했다:

```java
checkIn_allowsInactiveMember_whenSessionAlreadyHasRecordForThem()   // ✅ 허용
checkIn_rejectsInactiveMember_whenNoExistingRecordForThisSession()  // ✅ 거부
```

그리고 **API.md #12·#14 양쪽에 "비활성 멤버 취급 규칙"을 한 문장으로 못박아** 두 문서가 서로 다른 말을 하지 않게 했다.

#### 배운 것

> "이건 코드 버그이기 전에 **명세 버그**였습니다. 같은 도메인 개념(비활성 멤버)에 대해 조회 API와 저장 API가 서로 다른 규칙을 갖고 있었고, 각각만 보면 둘 다 타당해 보였습니다. **엔드포인트 단위로 리뷰하면 절대 안 잡히고, '한 사용자 시나리오를 끝까지 따라가야'** 발견됩니다. 수정할 때 코드만 고치지 않고 **API 문서 두 곳에 규칙을 명시**한 이유가 그겁니다."

**꼬리질문 대비**: _"프론트에서 비활성 멤버를 읽기 전용으로 렌더하고 payload에서 빼는 방법도 있었을 텐데요?"_ → "가능한 선택지였고 REVIEW에도 대안으로 적혀 있었습니다. 하지만 그러면 서버 계약의 불일치가 그대로 남고, 서버는 여전히 '화면에 보이는 걸 저장하려는 정당한 요청'을 거부합니다. **계약의 모순은 계약 쪽에서 해소하는 게 맞다**고 판단했습니다."

### 6.3 🔴-3 — 루트 README 부재

#### 증상

프로젝트 루트에 `README.md`가 없었다. `client/README.md`(Vite 기본 템플릿)만 있었다.

#### 왜 blocker인가

**포트폴리오에서 면접관이 가장 먼저 여는 파일이다.** GitHub 저장소를 열면 README가 랜딩 페이지다. 이게 없으면 아무리 코드가 좋아도 전달되지 않는다. CLAUDE.md S티어 필수 산출물이자 SPEC의 DoD 항목이기도 했다(PLAN.md의 C6 청크가 미착수 상태였다).

#### 수정 — README에 반드시 넣은 것들

1. 한 줄 소개 + **라이브 URL 2개**(프론트/백엔드)
2. **데모 계정 `admin`/`admin`** 안내
3. 기술 스택 + **아키텍처 다이어그램**(Mermaid 재사용)
4. **로컬 실행 전제: JDK 21 필요** (`JAVA_HOME` 명시 예시까지 — ⑩번 함정의 결과물)
5. **기술적 하이라이트 3개**(벌금 스냅샷 / N+1 방지 / 소유권 격리) — 면접관이 3줄만 읽어도 핵심이 전달되게
6. **알려진 트레이드오프**: localStorage 토큰 보관 — ARCHITECTURE.md §7에서 "README에 명시한다"고 약속했던 항목
7. 배포 정책 + **콜드스타트 안내**

#### 배운 것

> "설계 문서(SPEC/ARCHITECTURE/API/DATA-MODEL)를 7개나 썼는데 정작 **입구가 없었습니다.** 문서를 많이 쓰는 것과 '읽는 사람의 동선'을 설계하는 건 다른 일이라는 걸 배웠습니다. 그리고 이건 **자동화된 검증에 안 걸리는 종류의 결함**입니다 — 테스트도 CI도 README 부재를 잡아주지 않습니다. 그래서 배포 전에 사람이 체크리스트로 검수하는 단계가 따로 필요합니다."

**보너스**: README에 `localStorage` 트레이드오프를 적은 것 자체가 면접 자산이다. **"모르고 한 게 아니라 알고 고른 것"** 이라는 신호다.

---

## 7. 남아 있는 한계 (알고 남긴 것)

> 검수의 🟡 항목들. 배포를 막지는 않지만 **"이런 한계가 있다는 걸 알고 있다"** 는 답변 소재가 된다.
> 면접에서 한계를 먼저 말하면 신뢰도가 올라간다. **단, "왜 지금 안 고쳤는지"와 "어떻게 고칠지"를 반드시 함께 말한다.**

| #    | 한계                                                                                                                                                  | 왜 남겼나                                                          | 어떻게 고칠까                                                                                                                                                                                              |
| ---- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 🟡-1 | **회차 API 권한 가드 테스트 부재** — `POST/GET/DELETE /api/sessions/{id}`의 401/403 미검증. `GET /api/members/{id}/attendances`의 MEMBER→403도 미검증 | 시간 배분. `@PreAuthorize`는 코드상 3개 다 붙어 있음을 검수로 확인 | 기존 `StudyRoomControllerSecurityTest` 패턴 복사로 `StudySessionControllerSecurityTest` 추가. **"테스트로 잠기지 않은 권한은 리팩터 시 조용히 풀린다"** 는 게 핵심 리스크                                  |
| 🟡-2 | **`AuthService` 자체가 테스트되지 않음** — 컨트롤러 테스트는 `AuthService`를 목으로 대체                                                              | 코드 리뷰로 우회 없음은 확인                                       | `MemberRepository`/`PasswordEncoder` 목만 쓰는 순수 `AuthServiceTest` 3~4케이스. **데모 계정 규정의 핵심 보증(bcrypt 정상 경로 통과)이라 테스트로 못박는 게 맞다**                                         |
| 🟡-3 | **운영자 대시보드 총액이 실제보다 적게 나옴** — `useMembersQuery(false)`(활성만) 결과를 클라이언트에서 `reduce` 합산                                  | 발견은 검수 시점, blocker는 아님                                   | `useMembersQuery(true)`로 합산하거나 서버가 총합을 내려주도록 `GET /api/study-room` 응답 확장. **"과거 기록 보존"이 핵심 가치인데 대표 숫자가 그 원칙과 모순된다는 게 문제**                               |
| 🟡-4 | **출석 체크 화면이 "미체크"를 "정상"으로 표시** — `attendance.status ?? "PRESENT"`                                                                    | UX 단순화                                                          | `statusMap` 타입을 `AttendanceStatus \| null`로. **API는 `status: null`로 구분을 내려주는데 화면이 그 정보를 버리고 있다** — 한 번도 체크 안 한 회차와 전원 정상 회차를 운영자가 구분 못 함                |
| 🟡-5 | **동시 출석 체크가 500** — "조회 후 없으면 insert" 구조라 동시 요청 시 유니크 제약 위반이 `DataIntegrityViolationException`으로 500                   | 운영자 1명이라 실 발생 확률 낮음                                   | `DataIntegrityViolationException` 핸들러를 추가해 **409로 매핑**. "유니크 제약이 최종 방어선"이라고 써둔 만큼 **그 방어선이 어떤 응답이 되는지는 정의돼야 한다**                                           |
| 🟡-6 | **`Member.deactivate()` 미사용** — 비활성화는 `updateProfile(null,null,false)` 경로로 처리                                                            | —                                                                  | 삭제하거나 `MemberService.update`가 이 메서드를 쓰도록 통일 (ponytail 원칙)                                                                                                                                |
| 🟡-7 | **데모 샘플 멤버 비밀번호 하드코딩** — `SAMPLE_MEMBER_PASSWORD = "studyfine-demo-2024"`                                                               | 데모용, 권한 MEMBER                                                | `APP_SEED_ENABLED`가 `matchIfMissing = true`라 **환경변수를 안 넣으면 어디서든 자동 시드**된다는 게 진짜 리스크. 실배포 프로필에서 기본 `false`로 두거나, MEMBER 화면 시연용이면 README에 공개하는 게 정직 |
| 🟡-9 | **로그인 타이밍 사이드 채널** — 계정이 없으면 bcrypt 없이 즉시 실패, 있으면 bcrypt(수십~수백 ms) 실행 → **응답 시간 차이로 계정 존재 여부 추론 가능** | 메시지는 이미 통일함. S티어 필수 아님                              | 계정이 없을 때도 **더미 해시로 `matches()`를 한 번 돌린 뒤** 실패시키기                                                                                                                                    |

> **면접 활용법**: 🟡-9는 특히 좋은 소재다. "메시지 통일까지는 했는데 **타이밍 채널이 남아 있다는 걸 검수에서 알았습니다.** 사이드 채널 공격은 응답 내용뿐 아니라 **응답 시간·크기**도 정보라는 걸 배웠고, 더미 해시 비교로 시간을 균일화하는 게 표준 대응입니다."라고 말할 수 있다.
>
> 🟡-8(SPEC의 `./gradlew` 오기)은 검수 후 `./mvnw`로 수정 완료.

---

## 8. 학습 로드맵 체크리스트

> 이 프로젝트를 방어하려면 아래를 **설명할 수 있어야** 한다. 읽어서 아는 것과 말로 설명하는 건 다르다.
> **각 항목을 소리 내어 30초 설명해보고, 막히면 해당 절로 돌아간다.**

### A. Java 21 기초

- [ ] `record`가 자동 생성하는 것 5가지 (필드/생성자/접근자/equals/hashCode/toString)
- [ ] record 접근자가 `getX()`가 아니라 `x()`인 것
- [ ] switch 표현식이 enum에 대해 **빠짐없음 검사**를 한다는 것과, 그게 왜 안전장치인지
- [ ] `Optional`의 목적과 `orElseThrow` 패턴
- [ ] 텍스트 블록(`"""`)
- [ ] Stream API 기본 (`map` / `filter` / `collect(toMap)` / `allMatch`)
- [ ] `final` 필드 + 생성자 주입이 주는 이점

### B. Spring Boot 4 / Spring Framework

- [ ] IoC 컨테이너와 DI — **왜 생성자 주입인가**
- [ ] `@Component` / `@Service` / `@Repository` / `@RestController` 차이
- [ ] 자동 설정(auto-configuration)이 동작하는 원리 (조건부 빈 등록)
- [ ] 스타터의 역할, 부모 POM이 버전을 관리하는 구조
- [ ] **Boot 3 → 4 변경점**: Java 21 최소, `starter-web` → `starter-webmvc`, 테스트 스타터 분리, **Jackson 3(`tools.jackson`)**
- [ ] `@ConfigurationProperties` (`JwtProperties`) + `@PostConstruct`로 기동 시 검증
- [ ] `EnvironmentPostProcessor` — 왜 `@Component`로는 안 되고 `spring.factories` 등록이 필요한가
- [ ] `ApplicationRunner` — 기동 완료 후 1회 실행
- [ ] `@ConditionalOnProperty` (+ `matchIfMissing`의 함정)
- [ ] `spring.config.import: optional:file:.env[.properties]` — `.env`를 스프링이 직접 읽는 트릭

### C. JPA / Hibernate

- [ ] JPA(명세) / Hibernate(구현체) / Spring Data JPA(추상화) 3층 관계
- [ ] **영속성 컨텍스트**와 엔티티 생명주기 (transient / managed / detached / removed)
- [ ] **더티 체킹** — `save()` 없이 UPDATE가 나가는 이유
- [ ] 트랜잭션 커밋 전 **자동 flush** — 저장 직후 재조회가 최신값을 보는 이유
- [ ] `@ManyToOne` 기본값이 **EAGER**이고 반드시 LAZY를 명시해야 하는 이유
- [ ] **N+1 문제**를 SQL 로그 수준에서 설명하기
- [ ] `@EntityGraph` vs `join fetch` — 언제 어느 것
- [ ] **인터페이스 프로젝션** — 별칭↔getter 매핑, `nativeQuery = true`의 대가(DB 종속)
- [ ] `SUM(integer)` → `bigint` → Java `long` (타입 함정)
- [ ] `open-in-view: false` — 두 가지 이유(커넥션 낭비 / N+1 은폐)
- [ ] `@Transactional(readOnly = true)` — 무엇이 좋아지는가
- [ ] `@Enumerated(STRING)` vs `ORDINAL` — ORDINAL이 위험한 이유
- [ ] 쿼리 메서드(derived query) 이름 규칙

### D. Flyway / DB 설계

- [ ] Flyway 버전 파일 규칙(`V1__`)과 **체크섬 불변 규칙**
- [ ] `ddl-auto` 4가지 값과 **왜 `validate`인가** (`update`가 위험한 이유 2가지)
- [ ] `flyway-database-postgresql` 아티팩트가 왜 별도로 필요한가
- [ ] "스키마의 일부인 데이터(study_room 행)" vs "데모 시드"의 구분 기준
- [ ] 시드의 **멱등성** 확보 방법
- [ ] **인덱스: 복합 인덱스의 leftmost prefix 규칙** — 왜 `(session_id, member_id)`가 `member_id` 조회를 못 커버하나
- [ ] **PostgreSQL은 FK에 인덱스를 자동 생성하지 않는다** (MySQL과 다름)
- [ ] 인덱스를 **안 만드는** 판단 기준 (카디널리티, 테이블 크기, 중복)
- [ ] `ON DELETE CASCADE` vs `RESTRICT` — 언제 어느 쪽
- [ ] soft delete의 이유와 대가
- [ ] `CHECK` 제약으로 enum 강제하기
- [ ] 금액을 `integer`로 두는 판단 (KRW엔 소수점이 없다)

### E. Spring Security 7 / 인증

- [ ] **서블릿 필터 체인** — 요청이 컨트롤러에 닿기 전 무슨 일이 일어나는가
- [ ] 필터 단계 401/403은 왜 `@RestControllerAdvice`가 못 잡는가 → `AuthenticationEntryPoint` / `AccessDeniedHandler`
- [ ] **CSRF가 뭔지**, 왜 stateless JWT API에서는 꺼도 되는지 (쿠키 vs 헤더)
- [ ] **"쓰기 요청만 403이면 CSRF부터 의심"** 진단 규칙
- [ ] `authorizeHttpRequests()` / `requestMatchers()` (구 API는 제거됨)
- [ ] `SessionCreationPolicy.STATELESS`의 의미와 수평 확장과의 관계
- [ ] `@EnableMethodSecurity`가 없으면 `@PreAuthorize`가 **조용히 무시**된다는 것
- [ ] **JWT 구조** — 헤더/페이로드/서명, **페이로드는 암호화가 아니라 인코딩**
- [ ] **HS256 vs RS256** — 언제 어느 것을 고르나
- [ ] `NimbusJwtEncoder`/`Decoder`, `BearerTokenAuthenticationFilter`가 대신 해주는 일
- [ ] `JwtAuthenticationConverter`와 **`ROLE_` 접두사** — `hasRole` vs `hasAuthority`
- [ ] **BCrypt** — 왜 느린 게 장점인가, salt가 해시에 포함된다는 것
- [ ] **CORS** — preflight, 와일드카드 금지 이유, `allowCredentials`와의 조합 위험
- [ ] **사용자 열거(enumeration) 방지** — 실패 3종 메시지 통일
- [ ] **타이밍 사이드 채널** (🟡-9) — 메시지를 통일해도 응답 시간이 정보를 흘린다
- [ ] **수평 권한 상승** — `/api/me/**` 구조적 차단 설계
- [ ] localStorage vs httpOnly 쿠키 트레이드오프 (XSS vs CSRF+도메인 설정)

### F. API 설계 / 검증

- [ ] Bean Validation 애노테이션과 `@Valid` 동작
- [ ] `ConstraintValidator`로 커스텀 제약 만들기, null 처리 책임 분리(`@NotBlank`가 담당)
- [ ] **`ProblemDetail`(RFC 9457)** — 표준 에러 포맷, 왜 커스텀 에러 DTO를 안 만드나
- [ ] **500에 스택트레이스를 넣으면 안 되는 이유** + `traceId` 패턴
- [ ] HTTP 상태코드 선택 근거 (400/401/403/404/409/204)
- [ ] **PUT의 멱등성** — 왜 출석 체크가 PUT인가
- [ ] **PATCH 부분 수정** — 안 보낸 필드는 안 건드린다
- [ ] `fineAmount`를 요청에서 받지 않는 이유 (클라이언트 금액 조작 방지)
- [ ] springdoc-openapi 버전 호환 (Boot 4 = 3.x)

### G. 프론트엔드

- [ ] **`useState` 초기값은 최초 마운트에서 한 번만 평가된다**
- [ ] **`key`는 컴포넌트의 정체성** — key가 바뀌면 재마운트되고 state가 리셋된다
- [ ] **제어 vs 비제어 컴포넌트** (`value` vs `defaultValue`) — 섞으면 화면과 state가 갈라진다
- [ ] **클라이언트 상태 vs 서버 상태** — Zustand vs TanStack Query 분담 기준
- [ ] TanStack Query의 **무효화(invalidation)** 흐름
- [ ] 서버 데이터를 Zustand에 복사하면 안 되는 이유
- [ ] axios 인터셉터 — Bearer 주입 / 401 처리, **로그인 요청 401을 제외하는 이유**
- [ ] **Zod** — TS 타입은 컴파일 타임, 런타임 검증은 별개. `z.infer`로 단일 출처
- [ ] Vite 환경변수 `VITE_` 접두사 규칙 + **프론트 값은 전부 공개된다**
- [ ] Tailwind v4 설정 방식 (v3와의 차이)
- [ ] **프론트 라우트 가드는 UX일 뿐 보안 경계가 아니다**

### H. 테스트

- [ ] 순수 단위 테스트 vs 슬라이스 테스트 vs 통합 테스트
- [ ] `@WebMvcTest` — 무엇이 올라오고 무엇이 안 올라오나
- [ ] **`@Import(SecurityConfig.class)`가 왜 필수인가**
- [ ] `spring-security-test`의 `jwt()` 후처리기
- [ ] `@MockitoBean`(구 `@MockBean`)과 `@Mock`의 차이
- [ ] **FinePolicy가 static이라 테스트가 싼 이유** — 설계가 테스트 비용을 결정한다
- [ ] 버그 수정 시 **회귀 테스트를 함께 넣는 습관**
- [ ] "권한 매트릭스 문서 = 테스트 명세"라는 대응 관계

### I. 인프라 / 배포

- [ ] **Docker 멀티스테이지** — 3가지 이득(크기/보안/캐싱)
- [ ] `pom.xml`만 먼저 COPY 하는 **레이어 캐싱** 순서
- [ ] **JVM 메모리 구조** — 힙 / 메타스페이스 / 스레드 스택 / JIT 코드 캐시 / 네이티브
- [ ] **`-Xmx`는 힙만 제한한다** — 컨테이너 OOM의 진짜 원인이 메타스페이스일 수 있다
- [ ] **왜 JVM이 Node보다 무거운가** — 클래스 로딩 + 컴포넌트 스캔 + 프록시 생성
- [ ] **SerialGC를 고른 이유** — 작은 힙 + 저사양 CPU에서 GC 오버헤드 최소화
- [ ] Render 무료 티어 콜드스타트(15분 슬립, 30~60초 기상)와 대응책
- [ ] `JAVA_TOOL_OPTIONS`를 환경변수로 두는 이점
- [ ] GitHub Actions CI 잡 분리 + 의존성 캐싱
- [ ] `.env` 절대 커밋 금지, `.env.example`만 저장소에
- [ ] `/health` 헬스체크의 용도 (`show-details` 미노출)

### J. 도메인 설계 / 판단력

- [ ] **벌금 스냅샷** — "왜 비정규화가 아닌가"를 3단 논리로 설명
- [ ] **누적 합계를 저장하지 않는 이유** — 이중 갱신, 진실이 두 개
- [ ] "지금 넣는 건 과설계" 판단 기준 (규모 전제를 수치로)
- [ ] **Ubiquitous Language** — FineRate vs Fine을 이름부터 분리한 이유
- [ ] 확장 경로를 막지 않는 설계 (`CHECK (id=1)`만 떼면 멀티테넌시)
- [ ] SPEC의 **비범위(Out of Scope) 표**가 스코프 크리프를 막는 도구라는 것

---

## 9. 직접 해보는 실습 과제

> **읽기만 하면 면접에서 안 나온다. 손으로 해봐야 한다.**
> 난이도 순으로 배치했다. 각 과제 옆의 → 는 "이걸 하면 어떤 면접 질문에 답할 수 있게 되는지"다.

### 레벨 1 — 동작 확인 (30분)

1. **로컬 기동 후 Swagger로 15개 엔드포인트 전부 호출해보기**
   → "이 API 직접 다 써보셨나요?"
2. **벌금 스냅샷 수동 검증** — 단가 3000에서 지각 체크 → 단가를 5000으로 변경 → 그 회차 재조회 → **여전히 3000인지 확인**
   → 프로젝트 제1 설계 결정을 **내 눈으로 봤다**고 말할 수 있게 된다
3. **MEMBER 계정으로 로그인**(`member1@studyfine.dev` / `studyfine-demo-2024`)해서 `/api/members`를 호출 → 403 확인, `/api/members/2/attendances`에 **자기 id를 넣어도 403**인지 확인
   → §4.4 소유권 격리 설계를 체감

### 레벨 2 — 관찰 (1시간)

4. **`generate_statistics` 켜고 쿼리 수 세기** — `local` 프로파일로 기동 → 멤버 목록 조회 → 로그에서 실행 쿼리 수 확인 → 멤버를 5명 더 추가하고 **쿼리 수가 그대로인지** 확인
   → "N+1을 어떻게 검증했나요?"에 실측 답변
5. **일부러 N+1 만들어보기** — `AttendanceRepository.findByStudySessionId`의 `@EntityGraph`를 잠깐 주석 처리하고 회차 상세 조회 → 쿼리가 몇 개로 늘어나는지 로그로 확인 → 복구
   → N+1을 **말이 아니라 숫자로** 설명할 수 있게 된다
6. **`ddl-auto: validate` 깨뜨려보기** — 엔티티에 아무 필드나 `@Column`으로 추가하고 기동 → **기동 실패 메시지 확인** → 복구
   → "validate를 왜 쓰나요?"에 경험 기반 답변
7. **CSRF 되살려보기** — `.csrf(...disable)` 줄을 주석 처리하고 기동 → POST가 403이 되는지 확인 → 복구
   → §5-④ 스토리를 재현으로 뒷받침

### 레벨 3 — 만들기 (2~3시간)

8. **🟡-1 해결: 회차 API 권한 가드 테스트 추가**
   `StudyRoomControllerSecurityTest`를 복사해서 `StudySessionControllerSecurityTest`를 만든다. `POST /api/sessions`, `GET /api/sessions/{id}`, `DELETE /api/sessions/{id}`에 대해 401/403/2xx 3케이스씩.
   → "테스트는 얼마나 짜보셨나요?" + `@WebMvcTest` 슬라이스 이해도
9. **🟡-2 해결: `AuthServiceTest` 작성**
   `MemberRepository`와 `PasswordEncoder`를 목으로 두고: ① 정상 로그인 ② 없는 계정 ③ 비밀번호 불일치 ④ 비활성 계정 — **네 경우 모두 메시지가 동일한지** 검증.
   → 데모 계정 보안 원칙을 "테스트로 보증했다"고 말할 수 있게 된다
10. **🟡-5 해결: `DataIntegrityViolationException` → 409 매핑**
    `ApiExceptionHandler`에 핸들러 추가 + API.md #14에 409 한 줄 추가.
    → "동시성은 어떻게 처리했나요?"에 답변 가능
11. **🟡-4 해결: 미체크 상태를 `null`로 유지**
    `SessionCheckIn.tsx`의 `statusMap` 타입을 `AttendanceStatus | null`로 바꾸고, 미선택은 배지/금액을 비운다. `useEffect` 의존성도 `sessionId` 기준으로.
    → §6.1에서 배운 React 상태 관리를 실제로 적용

### 레벨 4 — 확장 (반나절+)

12. **`EXCUSED`(사유 결석, 벌금 0원) 상태 추가하기**
    - `AttendanceStatus` enum에 값 추가 → **`FinePolicy.calculate`가 컴파일 에러**가 나는지 확인 (§3.1의 빠짐없음 검사!)
    - Flyway **새 마이그레이션 파일** `V2__add_excused_status.sql`로 CHECK 제약 수정 (**V1은 절대 수정 금지**)
    - 프론트 `STATUS_OPTIONS`, Zod 스키마, 배지 컴포넌트 반영
      → 마이그레이션 규칙 + enum 안전성 + 풀스택 변경 전파를 한 번에 경험
13. **쿼리 카운트 테스트 (Testcontainers)**
    실제 Postgres를 띄우고 Hibernate `Statistics`로 실행 쿼리 수를 어서션한다. 멤버 3명일 때와 30명일 때 **쿼리 수가 같은지** 검증.
    → "N+1이 없다는 걸 어떻게 보장하나요?"에 최상급 답변 + `match-mate`와 엮어서 말할 수 있게 됨
14. **벌금 납부 관리로 확장 (M티어 backlog)**
    `fine_payment` 테이블 추가, 미납/완납 상태, 정산 마감. **스냅샷 원칙이 여기서 어떻게 확장되는지** 생각해보기.
    → "이 프로젝트를 더 발전시킨다면?"에 구체적 답변

---

## 부록 A. 자주 헷갈리는 것 빠른 참조

| 헷갈리는 것                            | 정답                                                                                         |
| -------------------------------------- | -------------------------------------------------------------------------------------------- |
| `hasRole('X')` vs `hasAuthority('X')`  | `hasRole`은 **`ROLE_` 접두사를 자동으로 붙인다**. `hasAuthority`는 안 붙인다                 |
| `@Mock` vs `@MockitoBean`              | `@Mock`은 순수 Mockito(스프링 없음). `@MockitoBean`은 **스프링 컨텍스트의 빈을 목으로 교체** |
| `save()`가 필요한 때                   | **새로 `new`한 엔티티만.** 조회해온 엔티티는 더티 체킹으로 자동 UPDATE                       |
| `@EntityGraph` vs `join fetch`         | 단순 fetch는 `@EntityGraph`, 조건·정렬까지 필요하면 `join fetch`                             |
| `value` vs `defaultValue` (React)      | `value`=제어(state가 주인), `defaultValue`=비제어(DOM이 주인). **섞지 말 것**                |
| `-Xmx` vs 컨테이너 메모리              | `-Xmx`는 **힙만**. 컨테이너는 힙+메타스페이스+스택+JIT+네이티브 전부                         |
| 401 vs 403                             | 401=**누구인지 모름**(인증 실패), 403=**누군지는 아는데 권한 없음**(인가 실패)               |
| 409를 쓰는 때                          | 이메일 중복, 회차 날짜 중복, 마지막 운영자 강등 — **현재 리소스 상태와 충돌**                |
| Zustand vs TanStack Query              | 브라우저가 주인이면 Zustand, **서버가 주인이면 TanStack Query**                              |
| `ddl-auto: update`를 쓰면 안 되는 이유 | 컬럼 삭제·타입 변경을 **조용히 무시**하고, 예고 없이 스키마를 건드린다                       |

## 부록 B. 이 프로젝트를 다른 프로젝트와 엮어 말하기

면접에서 "여러 프로젝트를 관통하는 이야기"를 만들면 인상이 강해진다.

| 축                    | study-fine                                                           | 다른 프로젝트                                                                                                                   |
| --------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| **스택 다양성**       | **Spring Boot 4 + JPA** (첫 Java 프로젝트)                           | NestJS 5개(Prisma 3 / TypeORM 2) → "ORM 3종·언어 2종을 다뤄봤다"                                                                |
| **N+1 / 쿼리 최적화** | GROUP BY projection + `@EntityGraph` + Map 패턴                      | `yuhyeon`(groupBy N+1 제거), `match-mate`(**Testcontainers로 쿼리 수 실측 고정**) → "이번엔 실측 자동화까지 못 간 게 다음 과제" |
| **인증 스펙트럼**     | Spring Security 7 + Nimbus JWT(HS256) + 롤 기반                      | `newGym`(JWT+카카오 OAuth), `yuhyeon`(**HMAC 기계 인증**), `pingboard`(소켓 JWT) → "대상·채널별로 인증을 나눠본 경험"           |
| **소유권 격리**       | **URL 구조로 차단**(`/api/me/**`)                                    | `linkstash-ai`·`match-mate`(소유권 비교 기반, 팀 스코프 404) → "비교 방식과 구조 방식을 둘 다 해보고 차이를 안다"               |
| **보안 결함 발견**    | 데모 계정 규정 준수 검증(우회 0건), 🟡-9 타이밍 채널 **스스로 지적** | `newGym`(백도어 엔드포인트 **제거**), `pingboard`(공개 API PII 노출) → "남의 코드에서도, 내 코드에서도 찾는다"                  |
| **배포 트러블슈팅**   | **JVM 메모리 튜닝으로 512MB 벽 돌파**                                | `match-mate`(빌드 타임 시크릿), `newGym`(SSL·스키마), `linkstash-ai`(서버리스 트랜잭션) → "런타임마다 다른 제약을 만나봤다"     |
| **도메인 판단력**     | **스냅샷 vs 파생값** 트레이드오프                                    | `match-mate`(최적해 대신 휴리스틱), `yuhyeon`(멱등성 설계) → "정답이 없는 선택에서 근거를 대는 훈련"                            |

---

_이 문서는 학습용이다. 면접 질문·모범답안은 [INTERVIEW.md](./INTERVIEW.md)를 본다._
