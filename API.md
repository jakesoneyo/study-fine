# API — study-fine

Base URL: `http://localhost:8080` · 문서: `/swagger-ui.html` · OpenAPI: `/v3/api-docs`
용어는 [`UBIQUITOUS_LANGUAGE.md`](./UBIQUITOUS_LANGUAGE.md) 기준.

---

## 0. 공통

### 인증

로그인을 제외한 모든 `/api/**` 는 헤더 필요.

```
Authorization: Bearer <accessToken>
```

토큰 클레임: `sub`(memberId), `role`(`ORGANIZER`|`MEMBER`), `name`, `exp`(12h)

### 권한 표기

| 표기         | 의미                               |
| ------------ | ---------------------------------- |
| 🔓 PUBLIC    | 인증 불필요                        |
| 🔑 AUTH      | 로그인한 멤버 누구나               |
| 👑 ORGANIZER | `ORGANIZER` 롤만 (`@PreAuthorize`) |

### 에러 응답 — RFC 9457 `ProblemDetail` 통일

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

| status | 발생 조건                                           |
| ------ | --------------------------------------------------- |
| 400    | Bean Validation 실패 (`errors` 배열 동봉)           |
| 401    | 토큰 없음/만료/서명 불일치, 로그인 자격증명 불일치  |
| 403    | 롤 부족 (MEMBER가 ORGANIZER 리소스 접근)            |
| 404    | 대상 없음                                           |
| 409    | 이메일 중복, 회차 날짜 중복                         |
| 500    | 서버 오류 — **스택트레이스/예외 메시지 노출 안 함** |

### 엔드포인트 전체 (15개)

| #   | Method   | Path                             | 권한 | 설명                         |
| --- | -------- | -------------------------------- | ---- | ---------------------------- |
| 1   | `GET`    | `/health`                        | 🔓   | 헬스체크                     |
| 2   | `POST`   | `/api/auth/login`                | 🔓   | 로그인 → JWT                 |
| 3   | `GET`    | `/api/auth/me`                   | 🔑   | 현재 로그인 멤버             |
| 4   | `GET`    | `/api/study-room`                | 🔑   | 스터디룸 + 벌금 단가 조회    |
| 5   | `PATCH`  | `/api/study-room`                | 👑   | 벌금 단가 수정               |
| 6   | `GET`    | `/api/members`                   | 👑   | 멤버 목록 + 누적 벌금        |
| 7   | `POST`   | `/api/members`                   | 👑   | 멤버 생성                    |
| 8   | `PATCH`  | `/api/members/{id}`              | 👑   | 멤버 수정 / 비활성화         |
| 9   | `GET`    | `/api/members/{id}/attendances`  | 👑   | 특정 멤버 출석 내역          |
| 10  | `GET`    | `/api/sessions`                  | 🔑   | 회차 목록                    |
| 11  | `POST`   | `/api/sessions`                  | 👑   | 회차 생성                    |
| 12  | `GET`    | `/api/sessions/{id}`             | 👑   | 회차 상세 (전체 출석 현황)   |
| 13  | `DELETE` | `/api/sessions/{id}`             | 👑   | 회차 삭제 (출석 cascade)     |
| 14  | `PUT`    | `/api/sessions/{id}/attendances` | 👑   | **출석 체크 (bulk upsert)**  |
| 15  | `GET`    | `/api/me/attendances`            | 🔑   | **내** 출석 내역 + 누적 벌금 |

> 멤버 물리 삭제(`DELETE /api/members/{id}`)는 없다. 비활성화는 #8 의 `active: false`. 출석 이력 보존이 목적 (DATA-MODEL.md §2.4).
> 별도 `/api/fines/**` 도 없다. 누적 벌금은 #6(전체) / #15(본인) 응답에 포함 — 엔드포인트를 늘리지 않는다.

---

## 1. `GET /health` 🔓

Actuator (`management.endpoints.web.base-path: /`). 커스텀 컨트롤러 아님.

**200**

```json
{ "status": "UP" }
```

---

## 2. `POST /api/auth/login` 🔓

**Request**

```json
{ "email": "admin", "password": "admin" }
```

| 필드       | 검증                                    |
| ---------- | --------------------------------------- |
| `email`    | `@NotBlank`, **`@LoginEmail`**, max 255 |
| `password` | `@NotBlank`, max 100                    |

> **`@LoginEmail` 커스텀 제약** — `"admin".equals(value) || 표준 이메일 형식`.
> 데모 계정 `admin` 하나만 형식 검증을 통과시키기 위한 것이며, **이 제약은 로그인 요청에만 붙는다.** 멤버 생성(#7)은 표준 `@Email` 이라 예외가 없다.
> 비밀번호는 어떤 계정이든 `BCryptPasswordEncoder.matches()` 를 정상 통과해야 한다. **우회 분기 없음, 미인증 토큰 발급 엔드포인트 없음.**

**200**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "expiresIn": 43200,
  "member": { "id": 1, "name": "굴리자", "email": "admin", "role": "ORGANIZER" }
}
```

**401** — 이메일 없음 / 비밀번호 불일치 / `active=false`
→ 세 경우 모두 **동일한 메시지**(`이메일 또는 비밀번호가 올바르지 않습니다`). 계정 존재 여부를 알려주면 사용자 열거(enumeration) 공격에 쓰인다.

---

## 3. `GET /api/auth/me` 🔑

토큰의 `sub` 로 조회. 새로고침 시 세션 복구용.

**200**

```json
{ "id": 1, "name": "굴리자", "email": "admin", "role": "ORGANIZER" }
```

**401** — 토큰 없음/만료

---

## 4. `GET /api/study-room` 🔑

벌금 단가는 멤버도 알아야 한다(내 벌금이 왜 이 금액인지의 근거).

**200**

```json
{
  "id": 1,
  "name": "스터디룸",
  "lateFineAmount": 3000,
  "absentFineAmount": 5000
}
```

---

## 5. `PATCH /api/study-room` 👑

**Request** — 부분 수정. 보낸 필드만 반영.

```json
{ "lateFineAmount": 5000 }
```

| 필드               | 검증                                 |
| ------------------ | ------------------------------------ |
| `name`             | optional, `@Size(1,60)`              |
| `lateFineAmount`   | optional, `@Min(0)`, `@Max(1000000)` |
| `absentFineAmount` | optional, `@Min(0)`, `@Max(1000000)` |

**200** — #4 와 동일 형태

> ⚠️ **이 요청은 기존 출석 기록의 `fineAmount` 를 절대 건드리지 않는다.** 새 단가는 이후의 출석 체크부터 적용된다 (ARCHITECTURE.md §5.2). 프론트 설정 화면에도 이 문구를 노출한다.

**403** — MEMBER

---

## 6. `GET /api/members` 👑

**Query**

| 파라미터          | 기본값  | 설명                         |
| ----------------- | ------- | ---------------------------- |
| `includeInactive` | `false` | `true` 면 비활성 멤버도 포함 |

**200** — 누적 벌금 내림차순. 페이지네이션 없음(스터디 규모 수십 명, DATA-MODEL.md §4.1 단일 집계 쿼리).

```json
[
  {
    "id": 2,
    "name": "김스터디",
    "email": "member1@studyfine.dev",
    "role": "MEMBER",
    "active": true,
    "accumulatedFine": 8000,
    "lateCount": 1,
    "absentCount": 1
  }
]
```

**403** — MEMBER (유저 스토리 M-3)

---

## 7. `POST /api/members` 👑

공개 회원가입은 없다. 운영자가 명단에 추가한다.

**Request**

```json
{
  "name": "이신입",
  "email": "newbie@studyfine.dev",
  "password": "initPass123!",
  "role": "MEMBER"
}
```

| 필드       | 검증                                                    |
| ---------- | ------------------------------------------------------- |
| `name`     | `@NotBlank`, `@Size(1,50)`                              |
| `email`    | `@NotBlank`, **`@Email`** (예외 없음), `@Size(max=255)` |
| `password` | `@NotBlank`, `@Size(8,100)`                             |
| `role`     | `@NotNull`, `ORGANIZER` \| `MEMBER`                     |

**201**

```json
{
  "id": 5,
  "name": "이신입",
  "email": "newbie@studyfine.dev",
  "role": "MEMBER",
  "active": true,
  "accumulatedFine": 0,
  "lateCount": 0,
  "absentCount": 0
}
```

**409** — 이메일 중복 (`이미 등록된 이메일입니다`)
**400** — 이메일 형식 위반. `admin` 같은 비이메일 문자열은 여기서 **거부된다**

---

## 8. `PATCH /api/members/{id}` 👑

**Request** — 부분 수정

```json
{ "active": false }
```

| 필드     | 검증                                   |
| -------- | -------------------------------------- |
| `name`   | optional, `@Size(1,50)`                |
| `role`   | optional, enum                         |
| `active` | optional, boolean — `false` = 비활성화 |

> 이메일·비밀번호 변경은 범위 밖(SPEC 비범위: 프로필 수정 플로우).
> `active: false` 는 물리 삭제가 아니다. 명단·출석 체크 화면에서만 빠지고 과거 기록은 유지된다.

**200** — #6 항목과 동일 형태
**404** — 없는 id
**409** — 마지막 ORGANIZER 를 비활성화/강등하려는 경우 (`운영자가 최소 1명 필요합니다`) — 스스로를 잠가버리는 사고 방지

---

## 9. `GET /api/members/{id}/attendances` 👑

**200** — #15 와 동일 스키마 (대상만 다름)

**403** — MEMBER. `{id}` 에 본인 id를 넣어도 403이다. **본인 조회는 #15 전용 경로를 쓴다** — id를 받는 경로를 통째로 ORGANIZER 전용으로 두면 "principal과 id 비교"를 빠뜨려 생기는 수평 권한 상승이 구조적으로 불가능해진다 (ARCHITECTURE.md §4).

---

## 10. `GET /api/sessions` 🔑

**200** — `sessionDate` 내림차순

```json
[
  {
    "id": 2,
    "sessionDate": "2026-08-05",
    "title": "3주차 — 알고리즘",
    "checkedIn": true,
    "totalFine": 8000,
    "presentCount": 2,
    "lateCount": 1,
    "absentCount": 1
  }
]
```

`checkedIn` = 출석 기록이 1건이라도 있는가. 운영자 화면에서 "아직 체크 안 한 회차" 배지에 쓴다.
집계는 단일 GROUP BY projection (회차마다 합계 쿼리 = N+1 금지).

---

## 11. `POST /api/sessions` 👑

**Request**

```json
{ "sessionDate": "2026-08-12", "title": "4주차 — DB 인덱스" }
```

| 필드          | 검증                                     |
| ------------- | ---------------------------------------- |
| `sessionDate` | `@NotNull`, ISO `yyyy-MM-dd` (LocalDate) |
| `title`       | `@NotBlank`, `@Size(1,100)`              |

**201** — #10 항목 형태 (`checkedIn: false`, 카운트 0)
**409** — 같은 날짜 회차 존재 (`해당 날짜의 회차가 이미 있습니다`)

---

## 12. `GET /api/sessions/{id}` 👑

출석 체크 화면의 로딩 데이터. **활성 멤버 전원**이 나오며, 아직 기록이 없는 멤버는 `status: null` 로 온다.

**200**

```json
{
  "id": 2,
  "sessionDate": "2026-08-05",
  "title": "3주차 — 알고리즘",
  "totalFine": 8000,
  "attendances": [
    {
      "memberId": 1,
      "memberName": "굴리자",
      "status": "PRESENT",
      "fineAmount": 0
    },
    {
      "memberId": 2,
      "memberName": "김스터디",
      "status": "LATE",
      "fineAmount": 3000
    },
    {
      "memberId": 3,
      "memberName": "박열공",
      "status": "ABSENT",
      "fineAmount": 5000
    },
    { "memberId": 4, "memberName": "이신입", "status": null, "fineAmount": 0 }
  ]
}
```

> 응답에 `fineAmount` 를 담는 이유: 프론트가 벌금을 다시 계산하지 않게 한다. 계산 규칙이 두 곳에 존재하면 반드시 어긋난다. **벌금 계산의 단일 출처는 서버의 `FinePolicy` 다.**
> 비활성 멤버는 목록에서 제외되지만, 이미 기록이 있는 비활성 멤버는 **포함**한다(과거 기록을 화면에서 지워버리지 않기 위해).
> **비활성 멤버 취급 규칙(#14 와 일치)**: 여기서 포함된, 즉 이미 이 회차에 기록이 있는 비활성 멤버는 #14 의 저장 요청에도 그대로 실어 보낼 수 있다 — 화면에 뜬 멤버는 항상 저장 가능해야 한다.

**404** — 없는 회차

---

## 13. `DELETE /api/sessions/{id}` 👑

**204** — 출석 기록도 FK `ON DELETE CASCADE` 로 함께 삭제. 해당 회차분 벌금은 누적에서 자연히 빠진다(누적 벌금을 저장하지 않으므로 보정 로직 불필요).
**404** — 없는 회차

---

## 14. `PUT /api/sessions/{id}/attendances` 👑 ★ 핵심

한 회차의 출석을 **한 번의 요청·단일 트랜잭션**으로 저장한다. 멱등(PUT) — 같은 요청을 여러 번 보내도 결과가 같다.

**Request**

```json
{
  "attendances": [
    { "memberId": 1, "status": "PRESENT" },
    { "memberId": 2, "status": "LATE" },
    { "memberId": 3, "status": "ABSENT" },
    { "memberId": 4, "status": "PRESENT" }
  ]
}
```

| 필드                     | 검증                                        |
| ------------------------ | ------------------------------------------- |
| `attendances`            | `@NotEmpty`, `@Valid`                       |
| `attendances[].memberId` | `@NotNull`                                  |
| `attendances[].status`   | `@NotNull`, `PRESENT` \| `LATE` \| `ABSENT` |

> **`fineAmount` 는 요청에 없다.** 클라이언트가 벌금 금액을 보낼 수 있으면 조작 가능해진다. 금액은 서버가 현재 벌금 단가로 계산한다.

**서버 처리 (ARCHITECTURE.md §5.4)**

1. 회차 존재 확인 → 없으면 404
2. `study_room` 1회 로드 → 단가 확보
3. 배열 내 `memberId` 중복 검사 → 400
4. `memberId` 들이 실재하는지, 그리고 **활성 멤버이거나 이 회차에 이미 기록이 있는 멤버**인지 **1회 조회로** 검증 → 아니면 400 (#12 참고 — 회차 상세가 포함시키는 비활성 멤버는 여기서도 항상 통과해야 한다. 그 외 비활성 멤버는 여전히 거부)
5. 기존 출석 기록 1회 조회 → `Map<memberId, record>` (검증 4단계에서도 재사용, 추가 쿼리 없음)
6. 각 항목: `fineAmount = FinePolicy.calculate(status, room)` → upsert
7. 커밋 (부분 저장 없음)

**200** — #12 와 동일 스키마 (저장 후 상태)

**400**

- `존재하지 않거나 비활성인 멤버가 포함되어 있습니다`
- `중복된 멤버가 포함되어 있습니다`

**403** — MEMBER (유저 스토리 M-4: 멤버는 자기 출석을 고칠 수 없다)

---

## 15. `GET /api/me/attendances` 🔑

토큰의 `sub` 만 사용. **id 파라미터를 받지 않으므로 남의 데이터를 지칭할 방법 자체가 없다.**

**200**

```json
{
  "member": { "id": 2, "name": "김스터디", "role": "MEMBER" },
  "accumulatedFine": 8000,
  "presentCount": 3,
  "lateCount": 1,
  "absentCount": 1,
  "records": [
    {
      "sessionId": 2,
      "sessionDate": "2026-08-05",
      "sessionTitle": "3주차 — 알고리즘",
      "status": "LATE",
      "fineAmount": 3000
    },
    {
      "sessionId": 1,
      "sessionDate": "2026-07-29",
      "sessionTitle": "2주차 — 스프링",
      "status": "PRESENT",
      "fineAmount": 0
    }
  ]
}
```

`sessionDate` 내림차순. 회차 정보는 `join fetch` 로 함께 가져온다(DATA-MODEL.md §4.3).

---

## 16. 권한 매트릭스 (요약)

| 엔드포인트                           | 비로그인 | MEMBER  | ORGANIZER |
| ------------------------------------ | :------: | :-----: | :-------: |
| `GET /health`                        |   200    |   200   |    200    |
| `POST /api/auth/login`               |   200    |   200   |    200    |
| `GET /api/auth/me`                   | **401**  |   200   |    200    |
| `GET /api/study-room`                | **401**  |   200   |    200    |
| `PATCH /api/study-room`              | **401**  | **403** |    200    |
| `GET /api/members`                   | **401**  | **403** |    200    |
| `POST /api/members`                  | **401**  | **403** |    201    |
| `PATCH /api/members/{id}`            | **401**  | **403** |    200    |
| `GET /api/members/{id}/attendances`  | **401**  | **403** |    200    |
| `GET /api/sessions`                  | **401**  |   200   |    200    |
| `POST /api/sessions`                 | **401**  | **403** |    201    |
| `GET /api/sessions/{id}`             | **401**  | **403** |    200    |
| `DELETE /api/sessions/{id}`          | **401**  | **403** |    204    |
| `PUT /api/sessions/{id}/attendances` | **401**  | **403** |    200    |
| `GET /api/me/attendances`            | **401**  |   200   |    200    |

이 표가 **권한 가드 단위 테스트의 명세**다 (PLAN.md 청크 2).

---

## 17. springdoc 설정

- `OpenApiConfig` 에 `bearerAuth` (`type: http`, `scheme: bearer`, `bearerFormat: JWT`) 보안 스키마 등록 → Swagger UI 에서 토큰 붙여 바로 호출 가능
- 컨트롤러에 `@Tag`, 핸들러에 `@Operation(summary=…)` — 설명은 **한국어**
- `/swagger-ui/**`, `/v3/api-docs/**`, `/health` 는 SecurityFilterChain 에서 `permitAll`
