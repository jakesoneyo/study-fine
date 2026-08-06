# REVIEW — study-fine (배포 전 검수)

검수일: 2026-08-07 · 검수 범위: C0~C5 (백엔드 전체 + 프론트 전체)
기준 문서: `SPEC.md` / `API.md` / `ARCHITECTURE.md` / `DATA-MODEL.md` / 워크스페이스 `CLAUDE.md`

## 재현 확인한 빌드/테스트 결과 (보고가 아니라 직접 실행)

| 항목                                  | 결과                                     |
| ------------------------------------- | ---------------------------------------- |
| `server` `./mvnw test`                | ✅ Tests run: **26**, Failures 0, Errors 0 |
| `client` `npx tsc --noEmit`           | ✅ 0 error                                |
| `client` `npm run build`              | ✅ built (423KB js / 23KB css)            |
| `git status`                          | ✅ clean (미커밋 변경 없음)                |
| `git log --all --full-history -- '**/.env'` | ✅ 이력 없음 — `.env` 커밋된 적 없음   |
| `git ls-files \| grep env`            | ✅ `.env.example` 2개만 추적됨            |

> ⚠️ 로컬 셸에 `java`가 PATH에 없어 `./mvnw test`가 "Unable to locate a Java Runtime"으로 즉시 실패한다.
> `JAVA_HOME=/opt/homebrew/opt/openjdk@21` 를 붙여야 실행된다. CI(temurin 21)는 정상.
> → README에 로컬 실행 전제(JDK 21)를 반드시 적을 것.

**판정: 🔴 blocker 3건 — 배포 승인 불가.** blocker 해소 후 재검수 필요.

---

## 🔴 Blocker (배포 전 필수 수정)

### 🔴-1 멤버 수정 모달이 역할을 조용히 강등시키고, 이름 미입력 시 400을 낸다

- **관련 파일/서브시스템**: `client/src/pages/Members.tsx` (프론트 · 멤버 관리) — 서버 무관, 독립 수정 가능

`EditMemberModal`은 `Members` 안에서 **항상 렌더링**되고 있는데(`<EditMemberModal member={editTarget} … />`), `key`가 컴포넌트가 아니라 **내부 `<Modal>`에 걸려 있다**(`key={member?.id ?? "none"}`). 그래서 `EditMemberModal` 자신은 최초 1회만 마운트되고, 그 시점의 `member`는 `null`이다.

```tsx
const [name, setName] = useState(member?.name ?? "");          // → "" 로 고정
const [role, setRole] = useState<"MEMBER"|"ORGANIZER">(member?.role ?? "MEMBER"); // → "MEMBER" 로 고정
```

입력창은 `defaultValue={member?.name}` / `defaultValue={member?.role}`(비제어)라 **화면에는 올바른 값이 보이지만 state는 계속 초기값**이다. 제출 시 `request: { name, role }` 이 그대로 나간다.

**재현 A (400)**: 멤버 관리 → 아무 멤버 "수정" → 아무것도 안 고치고 "저장"
→ `PATCH /api/members/{id} {"name":"","role":"MEMBER"}` → 서버 `@Size(min=1)` 위반 → **400 "요청 본문 검증에 실패했습니다"**

**재현 B (권한 강등 / 데이터 손상)**: ORGANIZER 멤버의 "수정"에서 이름만 바꾸고 저장
→ `role: "MEMBER"` 가 함께 전송 → **운영자가 멤버로 강등**된다. 데모 시드는 운영자가 admin 하나뿐이라 `MemberService.update`의 최소 1명 가드에 걸려 **409 "운영자가 최소 1명 필요합니다"** 라는, 이름 수정과 무관한 엉뚱한 에러가 뜬다. 운영자를 2명 이상 만들어 두면 가드가 안 걸리고 **실제로 강등이 성공**한다.

즉 SPEC O-1/O-7의 "멤버 수정" 경로가 **정상 동작 자체를 못 한다.**

**수정 방향**: `EditMemberModal`을 `Members`에서 `member &&` 로 조건부 렌더하거나 `key={editTarget?.id}` 를 **`EditMemberModal` 자체에** 붙여 대상마다 재마운트시킬 것. 그리고 입력을 `defaultValue`(비제어)가 아니라 `value`(제어)로 통일해 화면과 state가 갈라지지 않게 할 것. 부분 수정 PATCH이므로 **변경되지 않은 필드는 아예 보내지 않는** 편이 API.md #8(부분 수정) 의미와도 맞는다.

---

### 🔴-2 비활성 멤버가 있는 회차는 출석을 다시 저장할 수 없다 (SPEC O-7 위반)

- **관련 파일/서브시스템**: `client/src/pages/SessionCheckIn.tsx` + `server/.../attendance/AttendanceService.java` (프론트·백엔드 계약 충돌 — 🔴-1과 독립)

API.md #12는 회차 상세에 **"이미 기록이 있는 비활성 멤버는 포함"** 이라고 정의했고, 서버 `StudySessionService.detail()`도 그대로 구현했다. 그런데 API.md #14(bulk upsert)의 검증은 **활성 멤버만 허용**한다.

```java
// AttendanceService.checkIn
boolean allActiveAndExist = requestedIds.stream()
    .allMatch(id -> membersById.containsKey(id) && membersById.get(id).isActive());
if (!allActiveAndExist) throw new BadRequestException("존재하지 않거나 비활성인 멤버가 포함되어 있습니다");
```

프론트는 저장 시 **화면에 뜬 전원**(=비활성 포함)을 그대로 실어 보낸다.

```tsx
attendances: session.attendances.map((attendance) => ({ memberId: attendance.memberId, ... }))
```

**재현**: 데모 로그인 → 멤버 관리에서 `김스터디` 비활성화 → 회차 관리 → `2주차 — 스프링`(김스터디 기록 있음) → 아무 상태나 바꾸고 "출석 저장"
→ **400 "존재하지 않거나 비활성인 멤버가 포함되어 있습니다"**. 이 회차는 이후 **영구히 저장 불가**가 된다.

SPEC O-7("비활성 멤버는 출석 체크 화면에 안 뜨지만 과거 기록은 그대로 조회된다")과 O-3("멤버 전원 상태를 한 번에 저장")이 동시에 깨진다.

**수정 방향(택1, 서버 쪽이 안전)**:
- (권장) 서버 검증을 "활성 멤버 **또는 해당 회차에 이미 기록이 있는 멤버**"로 완화. 어차피 기존 기록 맵(`existingByMember`)을 이미 1회 조회하므로 추가 쿼리 없이 판정 가능하다.
- (또는) 프론트에서 비활성 멤버 행을 읽기 전용으로 렌더하고 payload에서 제외. 단 이 경우 서버 계약(#12 ↔ #14)의 불일치는 그대로 남으므로 API.md에 명시할 것.

어느 쪽을 택하든 **API.md #12/#14에 "비활성 멤버 취급" 규칙을 한 문장으로 못박아** 두 문서가 서로 다른 말을 하지 않게 할 것.

---

### 🔴-3 루트 `README.md` 부재 — CLAUDE.md S티어 필수 산출물 누락

- **관련 파일/서브시스템**: 저장소 루트 (문서 — 코드와 완전 독립)

```
$ ls /Users/son/Desktop/2026손영선/포폴/study-fine
API.md ARCHITECTURE.md DATA-MODEL.md DESIGN.md PLAN.md SPEC.md UBIQUITOUS_LANGUAGE.md client server
$ find . -maxdepth 2 -iname 'readme*'   → ./client/README.md (Vite 기본 템플릿) 뿐
```

`CLAUDE.md` 품질표 S티어는 **README + 아키텍처 다이어그램**을 필수로 요구하고, `SPEC.md §6 DoD`도 "README: 실행법(로컬/Docker), 아키텍처 다이어그램, 데모 계정 안내, 스크린샷/GIF"를 명시한다. `git log`에도 `docs: README …` 커밋이 없다(PLAN.md C6 항목이 미착수).

**포폴에서 면접관이 가장 먼저 여는 파일이 없는 상태로는 배포/공개가 무의미하다.**

**수정 방향** — README에 최소한 다음을 담을 것:
1. 한 줄 소개 + 스크린샷/GIF(출석 체크 화면 우선)
2. **로컬 실행 전제: JDK 21 필요**(현 환경에서 `./mvnw test`가 JAVA_HOME 없이 실패함) + `server/.env` 세팅(`.env.example` 복사) + Docker 실행법
3. 데모 계정 안내(`admin` / `admin`)
4. 아키텍처 다이어그램(ARCHITECTURE.md의 Mermaid 재사용)
5. ARCHITECTURE.md §7에서 "README에 명시한다"고 약속한 **localStorage 토큰 보관 트레이드오프**
6. 백엔드 로컬 쇼케이스 정책 + Vercel 프론트가 로컬 백엔드를 필요로 한다는 안내(PLAN.md C6)

---

## 🟡 개선 권장 (배포 자체를 막지는 않음)

### 🟡-1 회차 API 권한 가드 테스트가 통째로 없다
- `server/src/test/.../session/` 디렉터리 자체가 없음. `POST /api/sessions`, `GET /api/sessions/{id}`, `DELETE /api/sessions/{id}` 의 401/403/2xx 미검증.
- API.md §16은 "**이 표가 권한 가드 단위 테스트의 명세다**"라고 선언했고 SPEC DoD도 권한 가드 테스트를 요구한다. 코드상 `@PreAuthorize`는 3개 모두 붙어 있으나(확인함) **테스트로 잠기지 않았다** — 리팩터 시 조용히 풀린다.
- 함께 빠진 것: `GET /api/members/{id}/attendances` 의 MEMBER→403 (M-3 수평 권한 상승 방지의 핵심 케이스인데 미검증).
- 수정 방향: 기존 `StudyRoomControllerSecurityTest` 패턴 복사로 `StudySessionControllerSecurityTest` 추가 + `AttendanceControllerSecurityTest`에 `memberHistory` 403 케이스 1개 추가.

### 🟡-2 로그인 도메인 로직(AuthService) 자체가 테스트되지 않음
- `AuthControllerLoginTest`는 `AuthService`를 **목**으로 대체한다. 즉 "admin이 bcrypt 정상 경로로 통과하는지", "비활성 계정이 401인지", "세 실패 사유가 동일 메시지인지"가 자동으로 검증되지 않는다.
- 코드 리뷰로는 우회 없음을 확인했다(🟢-1 참고). 다만 CLAUDE.md 데모 계정 규정의 핵심 보증이라 **테스트로 못박는 게 맞다**.
- 수정 방향: `MemberRepository`/`PasswordEncoder` 목만 쓰는 순수 `AuthServiceTest` 3~4케이스. Testcontainers 불필요.

### 🟡-3 운영자 대시보드 "스터디룸 누적 벌금 합계"가 실제보다 적게 나온다
- `client/src/pages/Dashboard.tsx` — `useMembersQuery(false)`(활성 멤버만)의 결과를 클라이언트에서 `reduce` 합산한다. 비활성화된 멤버의 과거 벌금이 총액에서 빠진다.
- SPEC은 "과거 기록·벌금은 보존"이 핵심 가치인데, 대표 숫자가 그 원칙과 모순된다.
- 수정 방향: `useMembersQuery(true)` 로 합산하거나(표시용 랭킹은 활성만 유지), 서버가 총합을 내려주도록 `GET /api/study-room` 응답 확장. 엔드포인트를 늘리지 않는 쪽이 API.md 방침과 맞는다.

### 🟡-4 출석 체크 화면이 "미체크"를 "정상"으로 표시한다
- `client/src/pages/SessionCheckIn.tsx` — `initial[memberId] = attendance.status ?? "PRESENT"`.
- API.md #12는 미기록 멤버를 `status: null` 로 내려주는데(체크 여부 구분이 목적), 화면에서는 즉시 "정상" 배지로 칠해지고 "예상 정산 합계"에도 0원으로 계산된다. 운영자가 **한 번도 체크 안 한 회차와 전원 정상 처리한 회차를 구분할 수 없다.**
- 같은 `useEffect`가 `if (Object.keys(prev).length > 0) return prev;` 로 최초 1회만 초기화하므로, 저장 후 새 멤버가 추가되거나 다른 회차로 이동해도 반영되지 않을 수 있다(라우트 파라미터 변경 시 컴포넌트 재사용).
- 수정 방향: `statusMap` 값 타입을 `AttendanceStatus | null` 로 두고 미선택은 배지/금액을 비워둘 것. 초기화 조건은 `Object.keys(prev).length` 대신 `sessionId` 를 의존성으로.

### 🟡-5 동시 출석 체크가 500으로 떨어진다
- `AttendanceService.checkIn`은 "기존 기록 조회 → 없으면 insert" 구조라, 같은 회차에 두 요청이 동시에 들어오면 `uq_attendance_session_member` 유니크 제약 위반이 `DataIntegrityViolationException` 으로 튀고 `ApiExceptionHandler`의 마지막 `Exception` 핸들러가 **500**으로 응답한다(API.md 에 없는 상태코드).
- 실서비스 확률은 낮지만(운영자 1명), "유니크 제약이 최종 방어선"이라고 마이그레이션 주석에 써둔 만큼 그 방어선이 어떤 응답이 되는지는 정의돼야 한다.
- 수정 방향: `DataIntegrityViolationException` 핸들러를 추가해 409로 매핑(+ API.md #14에 409 한 줄 추가).

### 🟡-6 사용되지 않는 코드 — `Member.deactivate()`
- `server/.../member/Member.java` 의 `deactivate()` 는 어디에서도 호출되지 않는다(비활성화는 `updateProfile(null, null, false)` 경로로 처리됨). ponytail 원칙(필요 없는 코드 만들지 않기) 위반.
- 수정 방향: 삭제하거나, `MemberService.update`가 `active == false` 일 때 이 메서드를 쓰도록 통일.

### 🟡-7 데모 샘플 멤버 비밀번호가 소스에 하드코딩
- `server/.../seed/DemoDataSeeder.java` 의 `SAMPLE_MEMBER_PASSWORD = "studyfine-demo-2024"` 가 공개 저장소에 그대로 올라간다. `member1@studyfine.dev` 등 3계정으로 실제 로그인이 가능하다(MEMBER 권한).
- 데모 목적이고 권한이 MEMBER라 실질 위험은 낮지만, `APP_SEED_ENABLED` 가 `matchIfMissing = true` 라 **환경변수를 안 넣으면 어디서든(운영 포함) 자동 시드된다.**
- 수정 방향: 최소한 README에 "이 계정들은 데모 전용"임을 명시하고, 실배포 프로필에서는 `APP_SEED_ENABLED=false` 를 기본으로 둘 것. (MEMBER 화면 시연용으로 남길 거면 README에 계정을 공개하는 편이 오히려 정직하다.)

### 🟡-8 문서 드리프트 — SPEC.md DoD가 Gradle 기준
- `SPEC.md §6`: "`./gradlew build` 그린" → 실제 프로젝트는 **Maven**(`mvnw`, `pom.xml`). CI도 Maven.
- 면접관이 문서와 코드 불일치를 먼저 발견하는 상황을 만들 필요 없다. `./mvnw -B verify` 로 수정.

### 🟡-9 로그인 응답 시간으로 계정 존재 여부가 새어나갈 수 있다
- `AuthService.login` 은 이메일이 없으면 **bcrypt 비교 없이 즉시** 예외를 던지고, 존재하면 bcrypt(수십~수백 ms)를 돈다. 메시지는 동일하게 통일했지만 **응답 시간 차이로 계정 존재 여부가 구분된다.**
- 메시지 통일까지 신경 쓴 코드이므로 마무리로 언급할 가치가 있다. S티어 필수는 아님.
- 수정 방향: 계정이 없을 때도 더미 해시로 `matches()` 를 한 번 돌린 뒤 실패시키기.

---

## 🟢 확인 완료 — 잘 되어 있는 부분 (지시 항목별 검증 결과)

### 🟢-1 데모 계정에 우회·백도어 없음 (CLAUDE.md 규정 정확히 준수)
- `AuthService.login` 은 admin에 대한 분기가 **하나도 없다**. `passwordEncoder.matches()` 를 무조건 통과해야 한다. 시드도 `passwordEncoder.encode("admin")` 으로 실제 bcrypt 해시를 저장한다.
- 인증 없이 토큰을 발급하는 엔드포인트 없음(`/api/auth/login` 만 `permitAll`, 나머지 `anyRequest().authenticated()`).
- 프론트 데모 버튼도 `submit("admin","admin")` 으로 **정규 로그인 API를 그대로 호출**한다 — 별도 경로 없음.

### 🟢-2 `@LoginEmail` 예외가 리터럴 1건·로그인 DTO 1곳으로 정확히 격리됨
- `grep -rn "@LoginEmail"` → `LoginRequest.java` **단 1곳**.
- `MemberCreateRequest` 는 표준 `@Email` (예외 없음) — `admin` 같은 문자열은 멤버 생성에서 400으로 거부된다.
- 프론트도 대칭: `schemas/auth.ts` 만 `value === "admin"` 예외, `schemas/member.ts` 는 `z.email()`.
- 검증 로직: `DEMO_ACCOUNT_EMAIL.equals(value) || EMAIL_PATTERN.matches(value)` — 접두/부분일치 아님, 완전일치 1건.

### 🟢-3 권한 가드 15개 엔드포인트 전수 대조 — API.md §16 매트릭스와 100% 일치
| 엔드포인트 | 문서 | 구현 |
|---|---|---|
| `GET /health` `POST /api/auth/login` | 🔓 | SecurityConfig `permitAll` ✅ |
| `GET /api/auth/me` `GET /api/study-room` `GET /api/sessions` `GET /api/me/attendances` | 🔑 | `anyRequest().authenticated()` ✅ |
| `PATCH /api/study-room` | 👑 | `@PreAuthorize` (메서드) ✅ |
| `GET/POST /api/members`, `PATCH /api/members/{id}` | 👑 | `@PreAuthorize` (**클래스 레벨**) ✅ |
| `GET /api/members/{id}/attendances`, `PUT /api/sessions/{id}/attendances` | 👑 | `@PreAuthorize` ✅ |
| `POST /api/sessions`, `GET/DELETE /api/sessions/{id}` | 👑 | `@PreAuthorize` ✅ |
- `@EnableMethodSecurity` 활성 확인. 누락된 핸들러 없음(매핑 15개 전수 grep 대조).

### 🟢-4 소유권 격리가 "비교"가 아니라 "구조"로 되어 있음
- `GET /api/me/attendances` 는 `@CurrentMemberId`(= 토큰 `sub` claim)만 받고 **id 파라미터가 아예 없다.** 클라이언트가 남의 id를 지칭할 문법 자체가 존재하지 않는다.
- id를 받는 `GET /api/members/{id}/attendances` 는 통째로 ORGANIZER 전용 → "principal과 id 비교"를 빠뜨려 생기는 수평 권한 상승이 구조적으로 불가능. ARCHITECTURE.md §4 설계 의도대로 구현됨.

### 🟢-5 CSRF / CORS / 시크릿 / 에러 노출
- **CSRF**: stateless JWT + `csrf(disable)` — 세션 쿠키를 안 쓰므로 정상 판단. 주석에 이유가 적혀 있음.
- **CORS**: `setAllowedOrigins(명시 목록)`, 와일드카드 없음. `allowCredentials` 미설정(false) → 오리진 목록 + 자격증명 미전송 조합으로 안전. 메서드/헤더도 화이트리스트.
- **JWT 시크릿**: `application.yml` 전체가 환경변수 참조, 하드코딩 0건. `JwtProperties.@PostConstruct` 가 **32바이트 미만이면 기동 자체를 실패**시킨다(늦은 실패 방지). `.env` 는 `.gitignore`(루트+client 이중) + **커밋 이력에도 없음**(full-history 확인).
- **에러 노출**: `ApiExceptionHandler.handleUnexpected` 가 스택트레이스/예외 메시지를 절대 응답에 싣지 않고 `traceId`(UUID)만 내려준 뒤 서버 로그에 기록. 필터 단계 401/403도 `ProblemTitles` 로 동일 포맷·동일 문구 사용(문구 이원화 방지).
- **계정 열거 방지**: 로그인 실패 3종을 `InvalidCredentialsException` 하나로 통일(메시지 동일). ※ 타이밍만 🟡-9.

### 🟢-6 벌금 스냅샷 — 소급 오염 경로가 코드상 존재하지 않음
- `StudyRoomService.update` → `room.updateRates(...)` 만 호출. `AttendanceRepository`/`AttendanceRecord` 를 **참조조차 하지 않는다**(import 없음). 단가 변경이 과거 기록에 닿을 코드 경로가 없다.
- `AttendanceRecord.fineAmount` 를 바꾸는 유일한 통로는 `updateStatus(status, fineAmount)` 이고, 호출부는 `AttendanceService.checkIn` 한 곳뿐(신규 출석 체크).
- 누적 벌금은 컬럼이 아니라 `SUM(ar.fine_amount)` 집계 — SPEC §7-2 "이중 갱신 버그 원천 차단" 그대로.
- `FinePolicyTest.snapshot_pastResultDoesNotChangeWhenRateChangesLater` 가 이 불변식을 테스트로 잠가둠. 26개 테스트 중 FinePolicy 케이스가 다수 — 핵심 도메인 규칙 커버리지는 충분.

### 🟢-7 N+1 — 지시하신 3개 지점 모두 통과
- **멤버별 집계**: `findAllWithFineSummary` — `LEFT JOIN + GROUP BY + FILTER` 단일 네이티브 쿼리 인터페이스 projection. 멤버 N명이어도 쿼리 1회.
- **회차별 집계**: `findAllWithSummary` — 동일 패턴. 회차마다 합계 쿼리 없음.
- **출석 체크 API**: 멤버 루프(`for (Item item : request.attendances())`) 안에 **리포지토리 호출이 0건**. 루프 진입 전에 ① `findAllById(requestedIds)` 1회(존재·활성 검증) ② `studyRoomRepository.findById` 1회(단가) ③ `findByStudySessionId` 1회(기존 기록 Map)로 끝내고, 루프는 순수 계산 + `saveAll` 1회. 설계 문서(ARCHITECTURE.md §5.4) 순서 그대로.
- **지연로딩 누수 차단**: `findByStudySessionId` 에 `@EntityGraph(attributePaths="member")`, 내 출석 내역은 `join fetch ar.studySession`. `open-in-view: false` 로 뷰 렌더 단계 지연로딩도 봉쇄.
- **인덱스**: `uq_member_email`, `uq_study_session_date`, `uq_attendance_session_member`(복합), `ix_attendance_member`(단독 필터용) — 복합 유니크 선두 컬럼 한계까지 주석으로 설명하고 별도 인덱스를 둔 점이 특히 좋다.
- **트랜잭션 경계**: 쓰기 `@Transactional`, 읽기 `@Transactional(readOnly=true)` 일관. bulk upsert는 단일 트랜잭션(부분 저장 없음).

### 🟢-8 API.md ↔ 구현 대조 (샘플링)
- 경로·메서드 15개 전수 일치(위 표).
- 상태코드: `POST /api/members` `@ResponseStatus(CREATED)` ✅ / `POST /api/sessions` CREATED ✅ / `DELETE /api/sessions/{id}` NO_CONTENT ✅ / 이메일 중복 409 ✅ / 회차 날짜 중복 409 ✅ / 마지막 운영자 409 ✅ / bulk upsert 중복·비활성 400 ✅.
- 응답 스키마: `MemberSummaryResponse`(#6·#7·#8 공통), `StudySessionSummaryResponse`(#10·#11), `StudySessionDetailResponse`(#12·#14 동일 스키마), `AttendanceHistoryResponse`(#9·#15 동일 스키마) — 문서가 "동일 형태"라고 쓴 곳이 실제로 **같은 record를 재사용**한다. 프론트 Zod 스키마도 필드 단위로 일치(`presentCount` 가 멤버 목록엔 없고 내 출석 내역엔 있는 것까지 문서와 동일).
- `PUT /api/sessions/{id}/attendances` 요청에 `fineAmount` 없음 — 서버 DTO(`AttendanceCheckInRequest.Item`)와 프론트 Zod(`AttendanceCheckInRequestSchema`) 양쪽 모두 금액 필드 부재. 클라이언트 금액 조작 불가.
- 에러 포맷: RFC 9457 `ProblemDetail` + `errors[]` 배열, 프론트 `ProblemDetailSchema` 가 그대로 파싱.

### 🟢-9 CLAUDE.md 품질 기준
- `/health`: Actuator `base-path: /` + `exposure.include: health` + `permitAll`. show-details 미설정이라 `{"status":"UP"}` 만 노출(내부 정보 없음). ✅
- 환경변수 분리: ✅ (🟢-5 참고). `.env.example` 2개가 키·설명 주석까지 갖춰져 있음.
- **데모 로그인 버튼 문구 — 규정과 글자 단위로 일치** ✅
  - 버튼: `회원가입 없이 둘러보기`
  - 보조 설명: `회원가입 없이 체험해 볼 수 있습니다.`
  - 숨김/토글 없이 로그인 폼 바로 아래 정식 노출.
- 주석 밀도: 전 파일 상단에 역할 1~2줄, 공개 핸들러에 `@Operation`, 비자명 로직에 **"왜"**가 적혀 있다(HS256 JWK 헤더 명시 이유, `ddl-auto: validate` 를 고른 이유, `open-in-view: false` 이유, 복합 인덱스 선두 컬럼 한계, native SUM이 bigint로 오는 이유). `i++ // 증가` 류 자명 주석 **0건**. 이 항목은 기준을 상회한다.
- 커밋: Conventional Commits 16개, 한국어 일관, 의미 단위 분리(`feat:` 단위가 청크와 1:1). 시크릿 커밋 0건.
- CI: 백엔드(build→test) + 프론트(lint→build) 2잡, 캐시 설정 포함. Dockerfile 멀티스테이지(JDK 빌드 → JRE 런타임) + 힙 제한 주석.

### 🟢-10 프론트 상태 관리 경계
- Zustand는 토큰/현재 멤버만, 서버 데이터는 전부 TanStack Query — CLAUDE.md 표준 스택 분담 그대로. 401 인터셉터가 로그인 요청 자체는 제외 처리하는 디테일도 정확.
- `RequireOrganizer` 주석에 "UX 편의일 뿐 실제 보안 경계는 서버 `@PreAuthorize`" 라고 명시 — 프론트 가드를 보안으로 착각하지 않았다는 신호.

---

## 재검수 체크리스트 (blocker 수정 후)

- [ ] 🔴-1 멤버 "수정" → 아무것도 안 고치고 저장 → **200**, 필드 변경 없음
- [ ] 🔴-1 ORGANIZER 이름만 수정 → 저장 후 **역할이 ORGANIZER 유지**
- [ ] 🔴-2 멤버 비활성화 후 과거 회차 출석 재저장 → **200**, 비활성 멤버 기록 보존
- [ ] 🔴-3 루트 `README.md` 존재 + JDK 21 전제·데모 계정·다이어그램·localStorage 트레이드오프 포함
- [ ] `./mvnw test` / `npm run build` 재실행 그린
