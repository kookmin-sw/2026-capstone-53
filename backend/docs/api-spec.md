# 오늘어디 (TodayWay) Backend API 명세

> **버전**: v1.1.20-MVP
> **최종 수정**: 2026-05-08 (이상진 — §6.1 transferCount 정의 확정 (이용 노선 수, 환승 횟수 = transferCount - 1) + §12 체크리스트 완료 표시. Step 6 PR #11 follow-up 1번 자체 판단 처리.)
> **기준**: DB 스키마 v1.1-MVP (DB-SQL.txt, 2026-04-23)
> **데모 일정**: 2026-05-22

---

## 0. 개요

본 문서는 사용자의 출/도착지·도착시각·루틴 정보를 입력받아 ODsay 대중교통 길찾기 API를 호출하고, 권장 출발시각·푸시 알림을 제공하는 서비스의 백엔드 API 명세이다.

### 0.1 변경 이력

| 버전 | 일자 | 변경 사항 |
|---|---|---|
| v1.0 | 2026-04-21 | 초기 명세 (다중 경로 후보 + select 가정) |
| v1.1-MVP | 2026-04-27 | DB 스키마 v1.1-MVP 동기화. 단순화 결정 반영. |
| v1.1.1 | 2026-04-27 | 명세 명확화: `routeStatus` 도출 로직, `PATCH` 시 ODsay 재호출 분기 조건 추가 |
| v1.1.2 | 2026-04-27 | 알람 발송 시점에 ODsay 재호출 + 폴백 전략 추가 (실시간성 확보) |
| v1.1.3 | 2026-04-27 | `Route.pathType` 필드 제거 (외부 API 인코딩 leak 제거, forward-compat). `segments[].mode`로 파생 가능 |
| v1.1.4 | 2026-04-28 | 외부 API 매핑 보강 (이상진): ODsay→Route 매핑표 (§6.1), Kakao 응답 변환·provider 변환·query_hash 정규화 (§8.1), `EXTERNAL_AUTH_MISCONFIGURED` ErrorCode 추가 (§1.6) |
| **v1.1.5** | **2026-04-29** | **§2.3 logout 인터페이스 RFC 7009 정합 — Authorization 헤더 인증 → body의 refreshToken (소유 증명), 멤버 모든 활성 토큰 폐기 → 전달된 1개만 폐기 (단일 디바이스). logout-all은 P1 별도 엔드포인트로 분리. §1.8 logout 인증 ✓ → ✗** |
| **v1.1.6** | **2026-04-30** | **§1.6 `INTERNAL_SERVER_ERROR` 행 추가(fallback 명시), §1.7 JWT sub claim raw ULID 명시, §3.2 password 정규식 §2.1 정합 + 둘 다 null/생략 → 400 + password 변경 시 token 폐기 비고, §3.3 DELETE 멱등성 비고. (Step 4 PR #5 이상진 리뷰 보강 5건 + Q1-B/Q8-1 흡수)** |
| **v1.1.7** | **2026-04-30** | **§1.7 Resolver 동작 정정 — `Authentication.getName()`으로 raw `member_uid` 반환만(DB 호출 X), Service가 `findByMemberUid` 1회 조회. §3.3 탈퇴 회원 응답: 404 `MEMBER_NOT_FOUND` → **401 `UNAUTHORIZED`** (Service 부재 시 응답). β PR Resolver 마이그레이션 (이상진 PR #5 I-1 + claude.ai P1).** |
| **v1.1.8** | **2026-04-30** | **§5.4 PATCH 검증 정정 — `arrivalTime`의 NOW() 검사는 `arrivalTime`이 요청에 포함된 경우에만 적용. 지난 일정의 `title` 등 메모 편집 허용. (Step 5 PR #10 claude.ai 리뷰 P1 흡수)** |
| **v1.1.9** | **2026-04-30** | **§6.1 WALK 구간 path 보충 알고리즘 명시 (Step 6 이상진) — ODsay WALK subPath에 좌표 키가 없어 `origin`/`destination`/이전 transit 끝점으로 합성. 매핑표의 `path` 행에 WALK 분기 추가.** |
| **v1.1.10** | **2026-05-04** | **§6.1 transit path 출처 승격 (이상진) — `passStopList` 정류장 직선 → ODsay `loadLane` 도로 곡선 (`lane[i].section[].graphPos[]`) 정식 사용. `route_summary_json`을 `{"path":..., "lane":...}` wrapped 형식으로 저장 — 캐시 hit 시 재호출 없이 곡선 복원. loadLane 실패는 graceful — `passStopList` 직선 fallback. ODsay 호출 cache miss 1회당 1회 → 2회 (직렬, mapObj 의존).** |
| **v1.1.11** | **2026-05-04** | **§6.1 응답 예시 WALK `from`/`to` 제거 — `RouteSegment` record invariant + `@JsonInclude(NON_NULL)` 정합 cleanup. 코드 동작 변경 X (record가 WALK에서 reject + Jackson이 null drop).** |
| **v1.1.12** | **2026-05-07** | **§12.3 분담표 갱신 — `push` 도메인 황찬우→이상진 위임 (issue #9 본문 + 황찬우 직접 위임 발화 확정). `route` 완료 표시. Step 7 PR(`feat/backend-step7-push`)에 §7.1·§7.2·§9.1·§9.2 글루·#9 cascade 동반.** |
| **v1.1.13** | **2026-05-07** | **§9.2 보강 — `userDepartureTime` delta shift 명시 (silent corruption 방지: routine advance 후 `departureAdvice` 정합). §12.5/§12.6 완료 표시. PR #24 셀프리뷰 후속 (이상진).** |
| **v1.1.14** | **2026-05-07** | **§9.1 payload `data.subscriptionId` 추가 (멀티 디바이스 식별 — SW 가 어느 device 의 push 인지 분기 가능). 동작 흐름에 scan↔dispatch race 가드 명시 — PATCH 로 `reminder_at` 변경 시 dispatcher skip (중복 발송 방지). PR #24 외부 리뷰 (황찬우) Q1·Q2 흡수.** |
| **v1.1.15** | **2026-05-07** | **§7.1 `endpoint` 길이 500 → 2048 + `CHARACTER SET ascii COLLATE ascii_bin` (V2 migration). FCM/Apple/Mozilla/Microsoft WNS 모든 push provider endpoint 안전 마진. utf8mb4 환경 InnoDB UNIQUE INDEX max key length (3072 byte) 회피 위해 ASCII charset (URL RFC 3986 정합). PR #24 외부 리뷰 (황찬우) Q3 흡수.** |
| **v1.1.16** | **2026-05-07** | **§9.1 dispatcher 트랜잭션 분리 — 기존 단일 `@Transactional` 안에서 ODsay 재호출(최악 11초) + push provider IO 까지 묶여 30초 폴링 사이클 race 위험. 새 패턴: read tx (race 가드 + activeSubs fetch) → 트랜잭션 밖 ODsay → 트랜잭션 밖 push 발송 IO → write tx (schedule reload + race 재검증 + ODsay 결과 적용 + PushLog INSERT + 410 revoke + advance). PR #24 외부 리뷰 (황찬우) B1 흡수.** |
| **v1.1.17** | **2026-05-07** | **§8.1 `query_hash` canonical 강화 — `trim` 만 적용하던 v1.1.4 룰을 `lowercase(NFC(squash(trim(query))))` 로 확장. squash (`\s+`→` `) / NFC / `Locale.ROOT` lowercase 추가로 cache hit ratio 향상 + 외부 API quota 보호. 기존 캐시 row 는 TTL 30일 후 자연 만료 (별도 마이그레이션 불필요). PR #27 외부 리뷰 (황찬우) G1 흡수.** |
| **v1.1.18** | **2026-05-07** | **§8.1 `GeocodeCacheCleanupScheduler` 추가 — 매일 04:00 KST `@Scheduled` 로 `cached_at < NOW() - INTERVAL 30 DAY` row 삭제. read filter TTL 과 같은 cutoff. 운영 1년+ 누적 시 row/UNIQUE 인덱스 비대화 차단. `geocode.cleanup.{enabled,cron}` 환경변수 외부화. PR #27 외부 리뷰 (황찬우) G3 흡수.** |
| **v1.1.19** | **2026-05-08** | **§4.1 lat/lng XOR 검증 명시 — 둘 다 함께 또는 둘 다 누락만 허용. 한쪽만 채워 보낸 케이스는 400 VALIDATION_ERROR (silent default fallback 차단). NaN/±Infinity 도 400 명시. PR #27 review M2 코드 fix 의 명세 mirroring + 자체 review H1/L1/L3 (javadoc 운영 가정/XFF spoof 안내) 동반.** |
| **v1.1.20** | **2026-05-08** | **§6.1 `transferCount` 정의 확정 — "이용 대중교통 노선 수 (= 탑승 횟수)". 응답 예시 (지하철 1노선 + 도보 = `transferCount: 1`) 와 정합. **환승 횟수 = `transferCount - 1`** 비고 추가 (0 노선 케이스는 `Math.max(0, n-1)` 권고). v1.1.4 의 "미확정" 표기 제거. 코드 동작 변경 X (현 합산 패턴 그대로 OK). §12 체크리스트 완료 표시 (push/map/geocode/ODsay 4행). Step 6 PR #11 follow-up 1번 자체 판단 처리 (이상진).** |

### 0.2 v1.0 → v1.1-MVP 주요 변경

- **경로 도메인 단순화**: 다중 후보(`candidates[]`) → 단일 경로(`route`).
- **경로 fallback 통합**: `GET /schedules/{id}/routes` + `POST /schedules/{id}/routes/immediate` → `GET /schedules/{id}/route` 단일 엔드포인트.
- **경로 raw 저장**: 단일 경로만 응답하되, 백엔드는 ODsay raw JSON을 `route_summary_json`에 통째로 저장 (P1 다중 경로 부활 시 backfill 불필요).
- **회원 응답 단순화**: `preferences`, `calendarLinked` 필드 제거.
- **캘린더 연동 폐기**: `/members/me/calendar-link`, `/schedules/import-calendar` (P1 보류).
- **선호도/즐겨찾기/피드백 폐기**: 관련 API 전체 (P1 보류).
- **일정 등록 시 동기 ODsay 호출**: `POST /schedules`가 ODsay를 동기 호출. 외부 API 실패 시 graceful degradation.
- **`select` API 폐기**: 단일 경로라 무의미 (P1 부활 예정).

---

## 1. 공통 규약

### 1.1 Base URL

```
{프로토콜}://{호스트}/api/v1
```

개발 환경: `http://localhost:8080/api/v1`

### 1.2 인증

JWT Bearer Token. 인증이 필요한 엔드포인트는 다음 헤더 필수.

```
Authorization: Bearer {accessToken}
```

토큰 만료 시 `401 TOKEN_EXPIRED` 반환. refresh 엔드포인트는 v1.2에서 정의 예정 (현재는 재로그인).

### 1.3 응답 포맷

**성공 응답**

```json
{
  "data": { /* payload */ }
}
```

**에러 응답**

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "사람이 읽을 수 있는 메시지",
    "details": null
  }
}
```

`204 No Content`는 빈 바디.

### 1.4 시간/타임존

모든 시간 필드는 ISO 8601 with KST offset.

```
"2026-04-21T09:00:00+09:00"
```

서버는 `Asia/Seoul` 기준으로 직렬화 (`spring.jackson.time-zone`). 클라이언트도 동일 가정.

### 1.5 페이지네이션 (cursor 기반)

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `limit` | integer | 1회 조회 최대 개수 (기본 20, 최대 100) |
| `cursor` | string | 다음 페이지 커서 (이전 응답의 `nextCursor`) |

응답 형태:

```json
{
  "data": {
    "items": [...],
    "nextCursor": "eyJpZCI6MTIzfQ==",
    "hasMore": true
  }
}
```

`nextCursor`가 `null`이면 마지막 페이지.

### 1.6 통합 ErrorCode

| HTTP | code | 의미 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 요청 바디/파라미터 검증 실패 |
| 401 | `INVALID_CREDENTIALS` | 로그인 ID/비밀번호 불일치 |
| 401 | `TOKEN_EXPIRED` | 액세스 토큰 만료 |
| 401 | `UNAUTHORIZED` | 인증 헤더 누락 또는 유효하지 않은 토큰 |
| 403 | `FORBIDDEN_RESOURCE` | 본인 소유 자원이 아님 |
| 404 | `MEMBER_NOT_FOUND` | 회원 없음 |
| 404 | `SCHEDULE_NOT_FOUND` | 일정 없음 |
| 404 | `ROUTE_NOT_CALCULATED` | 경로가 아직 계산되지 않음 |
| 404 | `GEOCODE_NO_MATCH` | 지오코딩 결과 없음 |
| 404 | `SUBSCRIPTION_NOT_FOUND` | 푸시 구독 없음 |
| 409 | `LOGIN_ID_DUPLICATED` | 로그인 ID 중복 |
| 502 | `EXTERNAL_ROUTE_API_FAILED` | ODsay 호출 실패 (5xx / 네트워크 장애 / 일반 4xx — 401/403 제외) |
| **503** | **`EXTERNAL_AUTH_MISCONFIGURED`** | **외부 API 키 미설정 또는 인증 실패. 운영자 조치 필요 (일반 외부장애와 구분) — v1.1.4 추가** |
| 503 | `MAP_PROVIDER_UNAVAILABLE` | 지도 SDK 설정 조회 불가 |
| 504 | `EXTERNAL_TIMEOUT` | 외부 API 타임아웃 |
| **500** | **`INTERNAL_SERVER_ERROR`** | **처리되지 않은 예외에 대한 fallback. `GlobalExceptionHandler` catch-all (예상하지 못한 예외 — 운영 모니터링 대상) — v1.1.6 추가** |

### 1.7 식별자 규약

외부 노출 ID는 모두 ULID 기반 prefix 형식. 내부 BIGINT id는 응답에 노출하지 않는다.

| Prefix | 대상 | DB 컬럼 |
|---|---|---|
| `mem_` | 회원 | `member.member_uid` |
| `sch_` | 일정 | `schedule.schedule_uid` |
| `sub_` | 푸시 구독 | `push_subscription.subscription_uid` |

#### 비고 — JWT sub claim (v1.1.6 / v1.1.7 정정)

JWT의 `sub` claim에는 `member.member_uid` 값(prefix 없는 raw ULID 26자, Crockford Base32)이 박힌다. 외부 응답 ID `mem_<uid>`의 `mem_` prefix는 응답 직렬화 단계에서 부착되며, JWT subject 자체에는 포함되지 않는다. 서버 측 `CurrentMemberArgumentResolver`는 `Authentication.getName()`으로 raw `member_uid`를 반환만 한다 (DB 호출 X). Service가 진입 시 `findByMemberUid`로 1회 조회 — 부재 시 401 `UNAUTHORIZED`.

### 1.8 엔드포인트 한눈에 보기

| # | 메서드 | 경로 | 인증 | 도메인 |
|---|---|---|---|---|
| 1.1 | POST | `/auth/signup` | ✗ | auth |
| 1.2 | POST | `/auth/login` | ✗ | auth |
| 1.3 | POST | `/auth/logout` | ✗ | auth |
| 2.1 | GET | `/members/me` | ✓ | member |
| 2.2 | PATCH | `/members/me` | ✓ | member |
| 2.3 | DELETE | `/members/me` | ✓ | member |
| 3.1 | GET | `/main` | △ | display |
| 3.2 | GET | `/map/config` | ✗ | settings |
| 4.1 | POST | `/schedules` | ✓ | schedule |
| 4.2 | GET | `/schedules` | ✓ | schedule |
| 4.3 | GET | `/schedules/{scheduleId}` | ✓ | schedule |
| 4.4 | PATCH | `/schedules/{scheduleId}` | ✓ | schedule |
| 4.5 | DELETE | `/schedules/{scheduleId}` | ✓ | schedule |
| 5.1 | GET | `/schedules/{scheduleId}/route` | ✓ | route |
| 6.1 | POST | `/push/subscribe` | ✓ | push |
| 6.2 | DELETE | `/push/subscribe/{subscriptionId}` | ✓ | push |
| 7.1 | POST | `/geocode` | ✓ | geocode |

> △: 게스트 허용. 인증 시 추가 정보 반환.

---

## 2. 인증 (auth)

### 2.1 회원가입

`POST /auth/signup` — 인증 불필요

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `loginId` | string | Y | 영문+숫자, 4~20자 |
| `password` | string | Y | 8자 이상, 영문+숫자+특수문자 포함 |
| `nickname` | string | Y | 2~20자 |

```json
{
  "loginId": "chanwoo90",
  "password": "P@ssw0rd!",
  "nickname": "찬우"
}
```

#### Response — `201 Created`

```json
{
  "data": {
    "memberId": "mem_01HAA0123456789ABCDEFGHJK",
    "loginId": "chanwoo90",
    "nickname": "찬우",
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc..."
  }
}
```

#### 에러
- `400 VALIDATION_ERROR` — 형식 위반
- `409 LOGIN_ID_DUPLICATED` — 로그인 ID 중복

#### DB 매핑
- `member` INSERT (`member_uid` ULID 자동 생성, `password_hash` bcrypt)
- `refresh_token` INSERT (`token_hash` SHA-256, `expires_at` 발급 시점 + N일)

---

### 2.2 로그인

`POST /auth/login` — 인증 불필요

#### Request Body

| 필드 | 타입 | 필수 |
|---|---|---|
| `loginId` | string | Y |
| `password` | string | Y |

```json
{ "loginId": "chanwoo90", "password": "P@ssw0rd!" }
```

#### Response — `200 OK`

```json
{
  "data": {
    "memberId": "mem_01HAA...",
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc..."
  }
}
```

#### 에러
- `401 INVALID_CREDENTIALS`

#### DB 매핑
- `member` 단건 조회 + bcrypt 비교
- `refresh_token` 새 row 생성

---

### 2.3 로그아웃

`POST /auth/logout` — 인증 불필요 (refresh 토큰 자체가 소유 증명, RFC 7009 정신)

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `refreshToken` | string | Y | 폐기할 refresh 토큰 |

```json
{ "refreshToken": "eyJhbGc..." }
```

#### Response — `204 No Content`

#### 에러
- `400 VALIDATION_ERROR` — `refreshToken` 누락/공백

#### 비고
- 전달된 refreshToken 1개만 `revoked_at` 갱신 (단일 디바이스 로그아웃)
- 미존재/이미 폐기된 토큰도 멱등 처리 — silent `204` (RFC 7009 정신)
- accessToken은 만료까지 유효 (서버 측 블랙리스트는 v1.2)
- 다중 디바이스 일괄 로그아웃은 P1 별도 엔드포인트 (`POST /auth/logout-all`)

#### DB 매핑
- `refresh_token.revoked_at = NOW()` (`token_hash`로 조회된 row 1건만)

---

## 3. 회원 (member)

### 3.1 본인 정보 조회

`GET /members/me` — 인증 필요

요청 바디/쿼리 없음.

#### Response — `200 OK`

```json
{
  "data": {
    "memberId": "mem_01HAA...",
    "loginId": "chanwoo90",
    "nickname": "찬우",
    "createdAt": "2026-04-20T10:00:00+09:00"
  }
}
```

#### 비고 (v1.0 → v1.1)
`preferences`, `calendarLinked` 필드 제거. P1 부활 예정.

#### DB 매핑
- `member` 단건 조회 (`deleted_at IS NULL`)

---

### 3.2 회원정보 수정

`PATCH /members/me` — 인증 필요

#### Request Body (변경할 필드만)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `nickname` | string | N | 2~20자 |
| `password` | string | N | §2.1과 동일 — 영문+숫자+특수문자 모두 포함 8~72자. 정규식 `^(?=.*[A-Za-z])(?=.*\d)(?=.*[\W_]).{8,72}$` |

`loginId`는 수정 불가. **`nickname`/`password` 둘 다 null/생략 시 → 400 `VALIDATION_ERROR`** (v1.1.6 — silent 보안 사고 방지, 적어도 한 필드 명시 필수).

```json
{ "nickname": "새별명" }
```

#### Response — `200 OK`

3.1과 동일 형태.

#### 에러
- `400 VALIDATION_ERROR` — 필드 형식 위반 또는 두 필드 모두 null/생략

#### DB 매핑
- `member.nickname` 또는 `member.password_hash` UPDATE
- `updated_at` 자동 갱신

#### 비고 — token 폐기 (v1.1.6 추가)

`password` 필드 명시 시 해당 회원의 모든 활성 refresh token이 폐기된다 → 다른 디바이스 자동 로그아웃(보안 자기방어 사이드이펙트). 프론트는 password 변경 후 재로그인 UX 제공 권장.

---

### 3.3 회원 탈퇴

`DELETE /members/me` — 인증 필요

요청 바디 없음.

#### Response — `204 No Content`

#### DB 매핑
- 소프트 삭제: `member.deleted_at = NOW()`
- 관련 `schedule`, `push_subscription`은 FK ON DELETE CASCADE이지만 소프트 삭제 시점엔 별도 로직으로 deleted_at/revoked_at 갱신

#### 비고 — 멱등성 (v1.1.6 / v1.1.7 정정)

본 API는 인증 토큰의 회원이 이미 탈퇴 처리된 경우(`deleted_at IS NOT NULL`) Service의 `findByMemberUid`에서 회원 조회 실패 → **401 `UNAUTHORIZED`** 응답. RFC 9110의 일반 DELETE 멱등성과 달리, 본 API는 인증 정책 우선 설계의 결과로 두 번째 DELETE 요청은 401로 응답된다 (JWT는 유효하지만 가리키는 회원이 무효).

---

## 4. 메인/지도 (display, settings)

### 4.1 메인 화면 데이터 조회

`GET /main` — **게스트 허용** (인증 시 일정 정보 추가)

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `lat` | number | N | 사용자 현재 위도 (`-90.0 ~ 90.0`, finite) |
| `lng` | number | N | 사용자 현재 경도 (`-180.0 ~ 180.0`, finite) |

> **둘 다 함께 또는 둘 다 누락** 만 허용 (XOR 검증, v1.1.19). 한쪽만 채워 보낸 케이스는 `400 VALIDATION_ERROR` — silent default fallback (서울시청) 으로 떨어지면 "내 위치 기반 지도" 의도와 어긋나 UX 혼란.
> NaN / ±Infinity 도 `400 VALIDATION_ERROR` (controller 의 `Double.isFinite` 가드).

```
GET /main?lat=37.66&lng=127.01
```

#### Response — `200 OK`

```json
{
  "data": {
    "nearestSchedule": {
      "scheduleId": "sch_abc123",
      "title": "국민대 등교",
      "arrivalTime": "2026-04-21T09:00:00+09:00",
      "origin": { "name": "우이동", "lat": 37.66, "lng": 127.01 },
      "destination": { "name": "국민대학교", "lat": 37.61, "lng": 126.99 },
      "hasCalculatedRoute": true,
      "recommendedDepartureTime": "2026-04-21T08:25:00+09:00",
      "reminderAt": "2026-04-21T08:20:00+09:00"
    },
    "mapCenter": { "lat": 37.5665, "lng": 126.9780 }
  }
}
```

#### 비고
- `nearestSchedule`은 **시간상 가장 가까운 미래 일정** (단수). 미인증 또는 일정 없을 시 `null`.
- `mapCenter` 결정 우선순위: ① 쿼리 lat/lng → ② nearestSchedule.origin → ③ 기본값 (서울시청).
- `hasCalculatedRoute`는 `route_summary_json IS NOT NULL` 여부.

#### DB 매핑
- `nearestSchedule`:
```sql
SELECT * FROM schedule
WHERE member_id = ?
  AND deleted_at IS NULL
  AND arrival_time > NOW()
ORDER BY arrival_time ASC LIMIT 1
```

---

### 4.2 지도 SDK 설정 조회

`GET /map/config` — 인증 불필요

요청 바디/쿼리 없음.

#### Response — `200 OK`

```json
{
  "data": {
    "provider": "NAVER",
    "defaultZoom": 15,
    "defaultCenter": { "lat": 37.5665, "lng": 126.9780 },
    "tileStyle": "basic"
  }
}
```

> 클라이언트 API 키는 응답에 포함하지 않는다 (프론트 빌드 시 환경변수로 주입).

#### 에러
- `503 MAP_PROVIDER_UNAVAILABLE`

#### DB 매핑
- DB 미사용. 정적 설정 (application.yml).

---

## 5. 일정 (schedule)

### 5.1 일정 등록

`POST /schedules` — 인증 필요

> ⚠️ **중요**: 본 엔드포인트는 내부에서 ODsay API를 **동기 호출**한다. 응답 시간 평균 2~5초.
> ODsay 호출 실패 시에도 일정은 정상 등록되며, 경로 관련 필드는 `null`로 응답된다 (graceful degradation).
> 401/403(API 키 미설정/만료)도 등록 흐름에선 graceful 흡수 — 일정 등록 우선. 단 운영자 alert가 필요하므로
> `log.error` 레벨로 격상해 모니터링 신호는 보존 (조회 흐름 §6.1은 503 격상으로 분리).

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `title` | string | Y | 일정 제목 (1~100자) |
| `origin` | `Place` | Y | 출발지 |
| `destination` | `Place` | Y | 도착지 |
| `userDepartureTime` | string (ISO) | Y | 사용자가 입력한 출발 시각 |
| `arrivalTime` | string (ISO) | Y | 도착 희망 시각 |
| `reminderOffsetMinutes` | integer | N | 알림 시각 = 권장 출발시각 - N분 (기본 5) |
| `routineRule` | `RoutineRule` | N | 루틴 설정 (없으면 단발성) |

`Place`, `RoutineRule` 스키마는 §11. 데이터 타입 부록 참조.

```json
{
  "title": "국민대 등교",
  "origin": {
    "name": "우이동",
    "lat": 37.66,
    "lng": 127.01,
    "provider": "KAKAO"
  },
  "destination": {
    "name": "국민대학교",
    "lat": 37.61,
    "lng": 126.99,
    "provider": "KAKAO"
  },
  "userDepartureTime": "2026-04-21T08:30:00+09:00",
  "arrivalTime": "2026-04-21T09:00:00+09:00",
  "reminderOffsetMinutes": 5,
  "routineRule": {
    "type": "WEEKLY",
    "daysOfWeek": ["MON", "TUE", "WED", "THU", "FRI"]
  }
}
```

#### Response — `201 Created` (ODsay 호출 성공)

```json
{
  "data": {
    "scheduleId": "sch_01HBB...",
    "title": "국민대 등교",
    "origin": { "name": "우이동", "lat": 37.66, "lng": 127.01 },
    "destination": { "name": "국민대학교", "lat": 37.61, "lng": 126.99 },
    "userDepartureTime": "2026-04-21T08:30:00+09:00",
    "arrivalTime": "2026-04-21T09:00:00+09:00",
    "estimatedDurationMinutes": 35,
    "recommendedDepartureTime": "2026-04-21T08:25:00+09:00",
    "departureAdvice": "LATER",
    "reminderOffsetMinutes": 5,
    "reminderAt": "2026-04-21T08:20:00+09:00",
    "routineRule": {
      "type": "WEEKLY",
      "daysOfWeek": ["MON", "TUE", "WED", "THU", "FRI"]
    },
    "routeStatus": "CALCULATED",
    "routeCalculatedAt": "2026-04-20T15:00:00+09:00",
    "createdAt": "2026-04-20T15:00:00+09:00"
  }
}
```

#### Response — `201 Created` (ODsay 호출 실패 — graceful degradation)

```json
{
  "data": {
    "scheduleId": "sch_01HBB...",
    "title": "국민대 등교",
    "origin": { "name": "우이동", "lat": 37.66, "lng": 127.01 },
    "destination": { "name": "국민대학교", "lat": 37.61, "lng": 126.99 },
    "userDepartureTime": "2026-04-21T08:30:00+09:00",
    "arrivalTime": "2026-04-21T09:00:00+09:00",
    "estimatedDurationMinutes": null,
    "recommendedDepartureTime": null,
    "departureAdvice": null,
    "reminderOffsetMinutes": 5,
    "reminderAt": null,
    "routineRule": { "type": "WEEKLY", "daysOfWeek": ["MON", "..."] },
    "routeStatus": "PENDING_RETRY",
    "routeCalculatedAt": null,
    "createdAt": "2026-04-20T15:00:00+09:00"
  }
}
```

> `routeStatus`가 `PENDING_RETRY`면 클라이언트는 잠시 후 `GET /schedules/{id}/route?forceRefresh=true`로 재시도할 수 있다.

#### `departureAdvice` 값

| 값 | 의미 | 판정 기준 (권장: 표준 ±3분 윈도우) |
|---|---|---|
| `EARLIER` | 일찍 출발해야 함 | `recommendedDepartureTime < userDepartureTime - 3min` |
| `ON_TIME` | 적정 | 두 시각의 차이가 ±3분 이내 |
| `LATER` | 늦게 출발해도 됨 | `recommendedDepartureTime > userDepartureTime + 3min` |

> ON_TIME 윈도우(±3분)는 백엔드 상수로 둔다. 추후 사용자 선호도 도입 시 가변화 가능.

#### `routeStatus` 값

| 값 | 의미 |
|---|---|
| `CALCULATED` | ODsay 호출 성공, 경로 정보 정상 |
| `PENDING_RETRY` | ODsay 호출 실패, 재시도 필요 |

#### 에러
- `400 VALIDATION_ERROR` — 형식 위반, `arrivalTime <= NOW()`, `userDepartureTime > arrivalTime` 등

> ODsay 호출 실패 자체는 에러로 응답하지 않는다 (graceful degradation으로 `PENDING_RETRY` 반환).

#### DB 매핑
- `schedule` INSERT, `schedule_uid`는 ULID 생성
- ODsay 호출 → 응답을 `route_summary_json` 컬럼에 **raw JSON 통째 저장**
- `estimated_duration_minutes`: ODsay 응답의 `result.path[0].info.totalTime`
- `recommended_departure_time`: `arrival_time - estimated_duration_minutes`
- `departure_advice`: 위 표 기준 ENUM 값
- `route_calculated_at`: ODsay 호출 시각
- `reminder_at`: `recommended_departure_time - reminder_offset_minutes`
- `routeStatus`: **DB 컬럼이 아닌 도출값** — 응답 시점에 `route_summary_json IS NOT NULL ? "CALCULATED" : "PENDING_RETRY"`로 계산. 엔티티에 컬럼/필드 추가 불필요.
- 루틴 일정의 경우 `arrival_time`은 *다음 occurrence* 시각으로 저장

---

### 5.2 일정 목록 조회

`GET /schedules` — 인증 필요

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `from` | string (ISO) | N | 조회 시작 시각 (포함) |
| `to` | string (ISO) | N | 조회 종료 시각 (포함) |
| `limit` | integer | N | 기본 20, 최대 100 |
| `cursor` | string | N | 페이지 커서 |

```
GET /schedules?from=2026-04-21T00:00:00%2B09:00&limit=10
```

#### Response — `200 OK`

```json
{
  "data": {
    "items": [
      {
        "scheduleId": "sch_abc123",
        "title": "국민대 등교",
        "arrivalTime": "2026-04-21T09:00:00+09:00",
        "recommendedDepartureTime": "2026-04-21T08:25:00+09:00",
        "origin": { "name": "우이동", "lat": 37.66, "lng": 127.01 },
        "destination": { "name": "국민대학교", "lat": 37.61, "lng": 126.99 },
        "routeStatus": "CALCULATED"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

> 목록 응답은 페이로드 절감을 위해 `route_summary_json` 등 무거운 필드를 포함하지 않는다. 상세는 5.3에서 조회.

#### DB 매핑
```sql
SELECT * FROM schedule
WHERE member_id = ?
  AND deleted_at IS NULL
  [AND arrival_time BETWEEN ? AND ?]
ORDER BY arrival_time ASC
LIMIT ?
```

---

### 5.3 일정 상세 조회

`GET /schedules/{scheduleId}` — 인증 필요

#### Path
- `scheduleId`

요청 바디 없음.

#### Response — `200 OK`

5.1 응답과 동일 형태 (Schedule 객체).

#### 에러
- `404 SCHEDULE_NOT_FOUND`
- `403 FORBIDDEN_RESOURCE` — 본인 소유 아님

#### DB 매핑
- `schedule` 단건 조회 (`schedule_uid = ? AND deleted_at IS NULL`)
- `member_id` 매칭 검증

---

### 5.4 일정 수정

`PATCH /schedules/{scheduleId}` — 인증 필요

#### Path
- `scheduleId`

#### Request Body (변경할 필드만)

| 필드 | 타입 | 필수 |
|---|---|---|
| `title` | string | N |
| `origin` | `Place` | N |
| `destination` | `Place` | N |
| `userDepartureTime` | string (ISO) | N |
| `arrivalTime` | string (ISO) | N |
| `reminderOffsetMinutes` | integer | N |
| `routineRule` | `RoutineRule` | N |

```json
{
  "arrivalTime": "2026-04-21T10:00:00+09:00",
  "reminderOffsetMinutes": 10
}
```

#### 비고
- 출/도착지 또는 `arrivalTime`이 변경되면 ODsay를 **재호출**하여 경로 관련 필드를 재계산한다 (5.1과 동일 graceful degradation 적용).
- `userDepartureTime`만 변경된 경우는 ODsay 재호출 **불필요**. 동일 출/도착·동일 도착시각이면 경로/소요시간이 같으므로 `departureAdvice`만 재계산하면 된다.
- 변경 사항이 시간/경로 관련이 아닌 경우 (예: `title`, `reminderOffsetMinutes`만 변경) ODsay 재호출 불필요. 단, `reminderOffsetMinutes` 변경 시 `reminder_at`은 재계산.
- **`arrivalTime` 검증 (v1.1.8)**: `arrivalTime <= NOW()` 검사는 요청에 `arrivalTime`이 포함된 경우에만 적용된다. 지난 일정의 `title` 등 메모 편집은 허용. 사용자가 명시적으로 `arrivalTime`을 NOW() 이전으로 변경하는 시도는 거절.

#### Response — `200 OK`

5.1 응답과 동일 형태.

#### 에러
- `400 VALIDATION_ERROR`
- `404 SCHEDULE_NOT_FOUND`
- `403 FORBIDDEN_RESOURCE`

#### DB 매핑
- `schedule` UPDATE
- ODsay 재호출 시 `route_summary_json`, `estimated_duration_minutes`, `recommended_departure_time`, `departure_advice`, `route_calculated_at`, `reminder_at` 갱신

---

### 5.5 일정 삭제

`DELETE /schedules/{scheduleId}` — 인증 필요

#### Path
- `scheduleId`

요청 바디 없음.

#### Response — `204 No Content`

#### 에러
- `404 SCHEDULE_NOT_FOUND`
- `403 FORBIDDEN_RESOURCE`

#### DB 매핑
- 소프트 삭제: `schedule.deleted_at = NOW()`
- 푸시 알림 발송 차단 (스케줄러가 `deleted_at IS NULL` 조건으로 필터)

---

## 6. 경로 (route)

### 6.1 일정 경로 조회 (단일)

`GET /schedules/{scheduleId}/route` — 인증 필요

> ⚠️ 본 엔드포인트는 v1.0의 `GET /schedules/{id}/routes`(다중 후보)와 `POST /schedules/{id}/routes/immediate`(즉시 계산 fallback)를 통합한 단일 엔드포인트이다.

#### 동작 흐름

1. `schedule.route_summary_json`이 있고 TTL(권장 10분) 내면 캐시 hit → DB 데이터로 응답
2. 캐시 없거나 만료, 또는 `forceRefresh=true`면 ODsay 동기 호출 → DB 저장 후 응답 (캐시 miss)

#### Path
- `scheduleId`

#### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `forceRefresh` | boolean | N | true면 캐시 무시하고 ODsay 재호출 (기본 false) |

#### Response — `200 OK`

```json
{
  "data": {
    "scheduleId": "sch_abc123",
    "route": {
      "totalDurationMinutes": 35,
      "totalDistanceMeters": 8500,
      "totalWalkMeters": 700,
      "transferCount": 1,
      "payment": 1450,
      "segments": [
        {
          "mode": "WALK",
          "durationMinutes": 5,
          "distanceMeters": 350,
          "path": [[127.012, 37.661], [127.013, 37.662]]
        },
        {
          "mode": "SUBWAY",
          "durationMinutes": 25,
          "distanceMeters": 7500,
          "from": "우이동역",
          "to": "성신여대입구역",
          "lineName": "우이신설선",
          "lineId": "109",
          "stationStart": "우이동역",
          "stationEnd": "성신여대입구역",
          "stationCount": 7,
          "path": [[127.013, 37.662], [127.015, 37.662]]
        },
        {
          "mode": "WALK",
          "durationMinutes": 5,
          "distanceMeters": 350,
          "path": [[127.015, 37.662], [127.001, 37.610]]
        }
      ]
    },
    "calculatedAt": "2026-04-21T08:25:00+09:00"
  }
}
```

#### 비고 (forward-compat)
- `route_summary_json`에는 ODsay 응답이 다중 경로(`result.path[]` 배열)로 저장된다.
- v1.1에서는 백엔드가 `result.path[0]`만 추출해 단일 경로로 응답한다.
- P1에서 다중 경로 응답으로 확장 시, DB 컬럼 `selected_path_index TINYINT` 추가만으로 마이그레이션 가능 (기존 데이터 backfill 불필요).

#### `Segment.mode` 값

| 값 | 설명 |
|---|---|
| `WALK` | 도보 |
| `BUS` | 버스 |
| `SUBWAY` | 지하철 |

> 경로의 모드 조합(지하철만/버스만/혼합)은 `segments[].mode`로 파생한다. 별도 필드를 두지 않는다 — 외부 API 의존성 격리.

#### 🆕 v1.1.4 — ODsay 응답 → `Route` record 매핑

백엔드가 `route_summary_json`(raw)에서 응답 객체를 추출할 때 사용하는 **결정적 매핑표**. 자의적 매핑 금지.

| `Route` 필드 | ODsay 응답 경로 | 비고 |
|---|---|---|
| `totalDurationMinutes` | `result.path[0].info.totalTime` | 분 |
| `totalDistanceMeters` | `result.path[0].info.totalDistance` | m |
| `totalWalkMeters` | `result.path[0].info.totalWalk` | m |
| `transferCount` | `result.path[0].info.subwayTransitCount + busTransitCount` | **이용 대중교통 노선 수 (= 탑승 횟수)**. 응답 예시 `"transferCount": 1` (지하철 1노선 + 도보) 와 정합. **환승 횟수 = `transferCount - 1`** (단, 0 노선 = 도보만 케이스에선 음수 의미 X — UI 표시 시 `Math.max(0, transferCount-1)` 권고). v1.1.20 정의 확정. |
| `payment` | `result.path[0].info.payment` | 원 |

`segments[]` 매핑 (`result.path[0].subPath[]` 순회):

| `RouteSegment` 필드 | ODsay 경로 | 비고 |
|---|---|---|
| `mode` | `subPath[].trafficType` | 1→`SUBWAY`, 2→`BUS`, 3→`WALK` |
| `durationMinutes` | `subPath[].sectionTime` | 분 |
| `distanceMeters` | `subPath[].distance` | m |
| `from` | `subPath[].startName` | 출발 정류장/지점 |
| `to` | `subPath[].endName` | 도착 정류장/지점 |
| `lineName` | (SUBWAY) `subPath.lane[0].name`<br>(BUS) `subPath.lane[0].busNo`<br>(WALK) null | mode 분기 |
| `lineId` | (SUBWAY) `subPath.lane[0].subwayCode`<br>(BUS) `subPath.lane[0].busID`<br>(WALK) null | |
| `stationStart` | `subPath.startName` (SUBWAY 한정) | BUS는 from과 중복이라 null |
| `stationEnd` | `subPath.endName` (SUBWAY 한정) | |
| `stationCount` | `subPath.stationCount` | |
| `path` | (transit) `loadLane.result.lane[i].section[].graphPos[]` 평탄화 (도로 곡선)<br>(WALK) 좌표 키 없음 → 합성 (아래 알고리즘) | `[lng, lat]` 배열. loadLane 실패/누락 시 `passStopList` 직선으로 graceful fallback (v1.1.10) |

🆕 **v1.1.9 — WALK 구간 path 보충 알고리즘** (ODsay WALK subPath는 `startX/Y`/`endX/Y` 키가 없음):

- **첫 WALK** (subPath[0]이 WALK일 때): `origin` → 다음 transit subPath의 `startX/Y`
- **중간 WALK**: 이전 transit subPath의 `endX/Y` → 다음 transit subPath의 `startX/Y`
- **마지막 WALK** (subPath 마지막이 WALK일 때): 이전 transit subPath의 `endX/Y` → `destination`

여기서 `origin/destination`은 `schedule.origin_lng/lat`, `schedule.destination_lng/lat`. 매핑은 lookahead 1패스로 transit 시작점들을 미리 수집한 뒤 결정적으로 적용 (자의적 보간 금지).

🆕 **v1.1.10 — transit `path` 출처 = ODsay `loadLane` 도로 곡선**:

```
GET https://api.odsay.com/v1/api/loadLane?mapObject=0:0@{info.mapObj}&apiKey={key}
```

- `mapObject` 형식: **`"0:0@" + result.path[0].info.mapObj`** ⚠️ ODsay 공식 문서엔 prefix 명시 안 됨. 검증된 패턴
- 응답: `result.lane[i].section[j].graphPos[k].{x, y}` — `lane[i]`가 i번째 transit subPath와 1:1 매칭, `section[]`은 평탄화하여 한 transit segment의 path로 합침
- 호출 흐름: `searchPubTransPathT` → 응답에서 `info.mapObj` 추출 → `loadLane` 호출 (직렬, mapObj 의존)
- **graceful fallback** — 다음 케이스에서 `passStopList.stations[].x/y` 직선으로 fallback:
  - `loadLane` 5xx/timeout/응답 형식 위반
  - `searchPubTransPathT` 응답에 `info.mapObj` 누락 또는 빈 문자열
  - `result.lane`이 array 아님 또는 길이가 transit subPath 개수와 불일치 (부분 매핑은 *swap 위험*으로 금지 — 잘못된 노선 곡선이 silent하게 그려지는 시각 버그)
  - `graphPos[].{x, y}` 좌표 sanity 위반: NaN/Infinity, 서비스 영역 bbox 밖 (경도 `[124.0, 132.0]` / 위도 `[33.0, 39.0]` — 마라도 33.07°N / 백령도 124.7°E 포함)
  - lane의 graphPos 점이 2개 미만 (polyline 무의미)

  `searchPubTransPathT`는 정상이라 응답 자체는 가능 (cache 갱신도 진행).
  거리 기반 swap 의심 검사(예: lane 시작점-transit 시작점 거리 비교)는 도입 검토했으나 평행 노선/동일 정류장/shift swap에 본질적으로 비효과적이라 채택 안 함 — ODsay 1:1 매칭 가이드 신뢰가 전제. 실 운영에서 이상 곡선 발견 시 lane name 비교 등 별도 검증 로직 추가 검토.
- **auth 격상 (운영자 alert) — 조회 흐름(`getRoute`) 한정**: `loadLane`/`searchPubTransPathT` 401/403은 graceful 흡수하지 않고 propagate → `503 EXTERNAL_AUTH_MISCONFIGURED`로 격상 (API 키 미설정/만료는 운영 조치 필요).
  등록/수정 흐름(`refreshRouteSync` — §5.1)은 `routeStatus = PENDING_RETRY` graceful 정책 우선. 401/403도 false 반환하되 `log.error`로 운영자 모니터링 신호는 보존.

**`route_summary_json` 저장 형식 (wrapped)**:
```json
{ "path": <searchPubTransPathT raw>, "lane": <loadLane raw or null> }
```
캐시 hit 시 두 raw를 함께 unwrap → mapper에 전달 → 곡선 그대로 복원 (재호출 불필요).

**Wrapped JSON 타입 invariant**:
- `path` 키: 항상 JSON object (`{...}`). 누락/null/array/primitive면 캐시 손상 판정 → fresh 호출로 자동 복구
- `lane` 키: JSON object (`{...}`) 또는 `null`만 valid. 텍스트 `"null"` / 빈 문자열 / 숫자 / array는 거부 → 캐시 손상 판정. graceful로 흡수하지 않고 명시 거부해야 silent corruption 방지

**v1.1.10 운영 노트 — 마이그레이션**:
v1.1.10 배포 직후엔 v1.1.9 이전 비-wrapped 캐시(`route_summary_json`이 `searchPubTransPathT` raw 그대로 박힘)가 자동으로 invalid 판정됨 → cache miss로 ODsay 재호출 → cache 갱신. 자동 마이그레이션이라 별도 데이터 작업 불필요. 단, 배포 직후 잠깐(TTL 윈도우만큼) ODsay 호출 spike 가능 — 운영 모니터링 권장.

#### 에러
- `404 SCHEDULE_NOT_FOUND`
- `403 FORBIDDEN_RESOURCE`
- `502 EXTERNAL_ROUTE_API_FAILED` — ODsay 호출 실패 + 캐시도 없음
- `503 EXTERNAL_AUTH_MISCONFIGURED` — ODsay API 키 미설정/만료 (v1.1.4). `searchPubTransPathT` / `loadLane` 둘 다 401/403 시 동일 격상 (v1.1.10)
- `504 EXTERNAL_TIMEOUT` — ODsay 타임아웃 + 캐시도 없음

> 캐시가 있으면 ODsay 실패 또는 fresh 응답 매핑 실패 시에도 캐시로 응답 (캐시 stale 허용). `calculatedAt`으로 신선도 판단. v1.1.10부터 매핑 실패도 동일 정책.

#### DB 매핑
- 캐시 hit: `schedule.route_summary_json` 그대로 사용. 첫 path만 변환해 응답.
- 캐시 miss: ODsay 호출 → `route_summary_json`, `route_calculated_at`, `estimated_duration_minutes`, `recommended_departure_time`, `departure_advice`, `reminder_at` UPDATE.

---

## 7. 푸시 (push)

### 7.1 Web Push 구독 등록

`POST /push/subscribe` — 인증 필요

Web Push 표준 PushSubscription 객체를 등록.

#### Request Body

```json
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "keys": {
    "p256dh": "BNc...",
    "auth": "tBHI..."
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `endpoint` | string | Y | 브라우저 푸시 서버 URL. **max 2048 char** (v1.1.15 — FCM ~200 / Apple Web Push ~280 / Mozilla autopush ~400 / Microsoft WNS ~2048 모두 안전 마진). RFC 3986 ASCII. |
| `keys.p256dh` | string | Y | P-256 ECDH 공개키. max 255 char. |
| `keys.auth` | string | Y | 인증 비밀. max 255 char. |

#### Response — `201 Created`

```json
{
  "data": {
    "subscriptionId": "sub_01HEE..."
  }
}
```

#### 비고
- 동일 `endpoint`로 재구독 시 기존 row의 `revoked_at`을 `NULL`로 갱신 (재활성화)
- `endpoint`는 unique key
- `endpoint` 컬럼은 `VARCHAR(2048) CHARACTER SET ascii` (v1.1.15) — InnoDB UNIQUE INDEX max key length (utf8mb4 환경 3072 byte) 제약 + URL 표준 ASCII 정합. 비ASCII endpoint 는 spec 위반.

#### 에러
- `400 VALIDATION_ERROR`

#### DB 매핑
- `push_subscription` UPSERT (endpoint 기준)
- `subscription_uid` ULID 생성

---

### 7.2 Web Push 구독 해제

`DELETE /push/subscribe/{subscriptionId}` — 인증 필요

#### Path
- `subscriptionId`

요청 바디 없음.

#### Response — `204 No Content`

#### 에러
- `404 SUBSCRIPTION_NOT_FOUND`
- `403 FORBIDDEN_RESOURCE`

#### DB 매핑
- 소프트 해제: `push_subscription.revoked_at = NOW()`

---

## 8. 지오코딩 (geocode)

### 8.1 주소/장소 지오코딩

`POST /geocode` — 인증 필요

백엔드 경유로 외부 API 키 보호 + 캐시 처리. 일정 등록 흐름에서 클라이언트가 좌표를 얻기 위해 호출한다.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `query` | string | Y | 주소 또는 장소명 |

```json
{ "query": "국민대학교" }
```

#### Response — `200 OK`

```json
{
  "data": {
    "matched": true,
    "name": "국민대학교",
    "address": "서울 성북구 정릉로 77",
    "lat": 37.6103,
    "lng": 126.9969,
    "placeId": "1234567",
    "provider": "KAKAO_LOCAL"
  }
}
```

#### 🆕 v1.1.4 — Kakao Local 응답 → 명세 응답 변환 규칙

카카오 키워드 검색 (`https://dapi.kakao.com/v2/local/search/keyword.json`) 응답을 명세 응답으로 변환하는 결정적 규칙:

| 명세 응답 필드 | Kakao 응답 경로 | 변환 |
|---|---|---|
| `matched` | `documents.length > 0` | bool |
| `name` | `documents[0].place_name` | string 그대로 |
| `address` | `documents[0].road_address_name`<br>(빈값이면 `documents[0].address_name` fallback) | 도로명 우선, 지번 fallback |
| `lat` | `documents[0].y` | **`Double.parseDouble`** ⚠️ Kakao는 string 반환 |
| `lng` | `documents[0].x` | **`Double.parseDouble`** ⚠️ 위와 동일 |
| `placeId` | `documents[0].id` | string |
| `provider` | (고정값) | `"KAKAO_LOCAL"` |

**예외 처리**:
- `documents`가 빈 배열 → `404 GEOCODE_NO_MATCH`
- Kakao 401/403 → `503 EXTERNAL_AUTH_MISCONFIGURED`
- Kakao 5xx → `502 EXTERNAL_ROUTE_API_FAILED`
- Kakao 타임아웃 → `504 EXTERNAL_TIMEOUT`

#### 🆕 v1.1.4 — Provider 값 변환 규칙

DB ENUM 두 개가 다르게 정의돼 있어 **저장 위치별로 변환** 필수:

| 저장 위치 | DB ENUM 값 | 매핑 |
|---|---|---|
| `geocode_cache.provider` | `'NAVER', 'KAKAO_LOCAL'` | `"KAKAO_LOCAL"` 그대로 저장 |
| `schedule.origin_provider`<br>`schedule.destination_provider`<br>`Place.provider` (§11.1) | `'NAVER', 'KAKAO', 'ODSAY', 'MANUAL'` | `"KAKAO_LOCAL"` → **`"KAKAO"`** 변환 |

**변환 함수** (구현 참고):
```java
public static String toPlaceProvider(String geocodeProvider) {
    return "KAKAO_LOCAL".equals(geocodeProvider) ? "KAKAO" : geocodeProvider;
}
```

**이유**: `Place.provider`는 도메인 추상화 ENUM (`NAVER/KAKAO/ODSAY/MANUAL`). `KAKAO_LOCAL`은 Kakao 내부 API 세분 구분이라 도메인 모델에 노출 불필요. 반면 `geocode_cache`는 캐시 키 구분용이라 세분 구분 필요.

#### 🆕 v1.1.17 — `query_hash` 정규화 룰

캐시 미스 방지를 위해 hash 계산 전 canonical 정규화:

```
canonical  = lowercase(NFC(squash(trim(query))))
query_hash = SHA-256(canonical)
```

| 단계 | 규칙 | 효과 |
|---|---|---|
| trim | 양 끝 whitespace 제거 | `"국민대학교"` / `"국민대학교 "` 동일 |
| squash | `\s+` → 단일 SPACE | `"강남 역"` / `"강남  역"` 동일 |
| NFC | 한글 자모 분리·합치기 동등화 | NFD 형태 입력이 들어와도 NFC 캐시와 hit |
| lowercase | `Locale.ROOT` 영문 대소문자 동등화 (Turkish I 회피) | `"Seoul Station"` / `"seoul station"` 동일 |

- Kakao 호출에는 **trim 만 적용한 사용자 원본** 을 보낸다 (검색 서버 자체 normalize 보유). canonical 은 cache hash 에만 사용.
- v1.1.4 에서는 `trim` 만 정의되었음. v1.1.17 에서 squash/NFC/lowercase 추가 — **기존 캐시 row 의 query_hash 가 새 canonical 과 다른 경우 cache miss 후 새 hash 로 INSERT (TTL 30일 후 자연 만료, 별도 마이그레이션 불필요)**.

#### 에러
- `404 GEOCODE_NO_MATCH`
- `502 EXTERNAL_ROUTE_API_FAILED` — Kakao 5xx
- `503 EXTERNAL_AUTH_MISCONFIGURED` — Kakao API 키 미설정/만료 (v1.1.4)
- `504 EXTERNAL_TIMEOUT` — Kakao 타임아웃

#### DB 매핑
- 캐시 조회 (v1.1.17 canonical 적용):
```sql
-- application 단에서 canonical = lowercase(NFC(squash(trim(?)))) 후 hash 계산
SELECT * FROM geocode_cache
WHERE query_hash = ?     -- application 측에서 canonical hash 전달
  AND provider = ?
  AND cached_at > NOW() - INTERVAL 30 DAY
```
- 캐시 miss 시 KAKAO Local 또는 NAVER 호출 → `geocode_cache` INSERT
- `provider` 컬럼은 `'KAKAO_LOCAL'` 저장
- schedule에 좌표 저장 시엔 `'KAKAO'`로 변환 (위 매핑 규칙 적용)

#### 🆕 v1.1.18 — TTL eviction (`GeocodeCacheCleanupScheduler`)

- 매일 **04:00 KST** (`@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")`) 에 `cached_at < NOW() - INTERVAL 30 DAY` row 삭제.
- read filter (TTL 30일) 와 같은 cutoff — 만료된 row 만 제거하고 hit 가능 row 는 보존.
- 운영 1년+ 누적 시 row 비대화 + UNIQUE INDEX `(query_hash, provider)` 비대화 차단 + read filter 의 `cached_at >` 조건 인덱스 효율 보존.
- `geocode.cleanup.enabled` (default `true`), `geocode.cleanup.cron` 환경변수 외부화.

```sql
DELETE FROM geocode_cache
WHERE cached_at < NOW() - INTERVAL 30 DAY
```

---

## 9. 백엔드 내부 동작 (외부 API 아님)

> 본 섹션은 외부 API가 아니지만 명세화가 필요한 백엔드 동작이다.

### 9.1 리마인더 알림 스케줄러

#### 트리거
`@Scheduled(fixedDelay = 30000)` (30초 주기)

#### 쿼리
```sql
SELECT s.*, m.id as member_id
FROM schedule s
JOIN member m ON s.member_id = m.id
WHERE s.reminder_at <= NOW()
  AND s.reminder_at > NOW() - INTERVAL 5 MINUTE  -- 누락 방지 윈도우
  AND s.deleted_at IS NULL
  AND m.deleted_at IS NULL;
```

#### 동작 흐름 (v1.1.16 — 트랜잭션 분리 패턴)

dispatcher 의 한 schedule 처리 사이클은 **read 트랜잭션 → 트랜잭션 밖 IO → write 트랜잭션** 세 단계로 나뉜다. ODsay(최악 11초) + push provider IO 가 한 트랜잭션에 묶여 30초 폴링 사이클을 못 따라잡는 race 차단이 목적.

1. **scan** (PushScheduler, 트랜잭션 X) — 위 쿼리로 due 목록 조회. `(schedule_id, reminder_at)` tuple 을 dispatcher 에 그대로 전달.
2. **read tx** (`PushReminderTransactional.loadContext`) — `Schedule` 재 fetch + race 가드. 현재 `reminder_at` 이 scan 시점 값과 일치 안 하면 (사용자 PATCH 로 `arrival_time`/출발지/도착지 변경) 본 사이클 skip — 새 `reminder_at` 이 같은 윈도우 안이면 다음 폴링 사이클에 다시 잡혀 발송. soft-deleted / `reminder_at = NULL` 도 동일 분기. 통과 시 회원의 활성 구독 목록을 같이 fetch 해 detached 상태로 반환.
3. **트랜잭션 밖 — ODsay 재호출** (최대 2회 시도, 1초 간격)
   - 성공 시: 결과를 in-memory snapshot 으로 캡처 (`route_summary_json`, `estimated_duration_minutes`, `recommended_departure_time`, `route_calculated_at`).
   - 실패 시 (2회 모두 실패): **폴백** — payload 의 `data.fallback=true`. DB 컬럼은 단계 5에서도 갱신 X (마지막 성공 스냅샷 보존).
4. **트랜잭션 밖 — sub 별 페이로드 빌드 + 발송** — 회원의 `push_subscription` 중 `revoked_at IS NULL` 인 모든 구독에 대해 **sub 단위 빌드** (`data.subscriptionId` 가 sub 마다 다름, v1.1.14). 한 sub 의 410 EXPIRED 가 다른 sub 의 발송에 전파되지 않는다 (`for` 루프 sub 별 독립 처리).
5. **write tx** (`PushReminderTransactional.persistAndAdvance`) — schedule reload + race 재검증 + 갱신 적용:
   - race 재검증 통과 시: ODsay snapshot 으로 `update_route_info` 호출 (DB 컬럼 갱신) + 발송 결과 `push_log` INSERT (`status`, `http_status`, `payload_json`, `schedule_id`, `subscription_id`) + 410 EXPIRED 시 해당 구독 `revoked_at` 채움 + advance/clear (단계 6/7).
   - race 재검증 실패 시 (ODsay 호출 ~ persist 사이 PATCH/DELETE): 모든 mutate skip. 이미 발송된 push 의 `push_log` 는 누락되지만 advance 가 옛 schedule 기준으로 `reminder_at` 을 덮어쓰는 silent corruption 차단이 우선 (관측성 trade-off).
6. 루틴 일정 (`routine_type ≠ ONCE/NULL`): 다음 occurrence 로 `arrival_time`, `recommended_departure_time`, `reminder_at` 갱신. ODsay 재호출 X (9.2 참조).
7. 단발성 일정 (`routine_type = 'ONCE'` 또는 NULL): `reminder_at = NULL` (재발송 방지).

> **설계 원칙**: 사용자가 받는 알람은 항상 *최신 ODsay 정보*로 발송하는 것이 원칙. 외부 API 장애 시에도 알람을 누락시키지 않기 위해 폴백 전략을 둔다 ("구버전 정보라도 보내는 게 안 보내는 것보다 사용자에게 유용").

#### 푸시 페이로드 예시

**ODsay 재호출 성공 시 (갱신된 값 반영)**
```json
{
  "title": "국민대 등교",
  "body": "5분 뒤 출발하세요 (8:25, 예상 소요시간 35분)",
  "data": {
    "scheduleId": "sch_abc123",
    "subscriptionId": "sub_def456",
    "type": "REMINDER",
    "url": "/schedules/sch_abc123",
    "recommendedDepartureTime": "2026-04-21T08:25:00+09:00",
    "estimatedDurationMinutes": 35,
    "fallback": false
  }
}
```

**ODsay 재호출 실패 — 폴백 시 (기존 스냅샷 값)**
```json
{
  "title": "국민대 등교",
  "body": "5분 뒤 출발하세요 (8:25, 예상 소요시간 35분)",
  "data": {
    "scheduleId": "sch_abc123",
    "subscriptionId": "sub_def456",
    "type": "REMINDER",
    "url": "/schedules/sch_abc123",
    "recommendedDepartureTime": "2026-04-21T08:25:00+09:00",
    "estimatedDurationMinutes": 35,
    "fallback": true,
    "fallbackReason": "EXTERNAL_ROUTE_API_FAILED"
  }
}
```

> `data.subscriptionId` (v1.1.14 추가) — 한 회원이 다중 구독을 가질 수 있고 (PC + 모바일 등) 서비스 워커가 어느 device 의 푸시인지 분기해야 할 때 사용. payload 는 sub 단위로 빌드되어 sub 마다 다른 `subscriptionId` 가 들어간다.

#### 동시성/내구성 고려
- 단일 인스턴스 가정 (MVP)
- 다중 인스턴스 시 `ShedLock` 또는 `@SchedulerLock` 도입 (P2)
- 발송 실패 시 재시도 정책: 동일 `reminder_at`에 대해 1회만 발송 (중복 방지). 실패 시 `push_log.status = 'FAILED'` 기록 후 다음 occurrence부터 재시도.

---

### 9.2 루틴 일정의 다음 occurrence 계산

알림 발송 직후 (또는 트랜잭션 내에서) 다음 발생 시각으로 갱신.

| `routine_type` | 다음 occurrence 계산 |
|---|---|
| `ONCE` 또는 `NULL` | `reminder_at = NULL` (재발송 안 함) |
| `DAILY` | `arrival_time + 1 day` |
| `WEEKLY` | `arrival_time` 이후, `routine_days_of_week` 중 가장 가까운 요일 |
| `CUSTOM` | `arrival_time + routine_interval_days` |

갱신 후:
- `user_departure_time` 도 동일 delta(`new_arrival - old_arrival`)만큼 shift — 사용자가 입력한 출발 의도 시각이 다음 occurrence 기준으로 보존되어 `departure_advice` 가 정상 산출됨. (v1.1.13 보강)
- `recommended_departure_time = arrival_time - estimated_duration_minutes`
  > **주의**: *다음 occurrence 계산 시점*에는 ODsay를 호출하지 않는다. 며칠 뒤 일정의 소요시간 예측은 부정확하므로 무의미한 호출. 마지막 호출의 `estimated_duration_minutes`를 그대로 사용해 `reminder_at`만 미리 박아둔다.
  > **실제 알람 발송 시점**에는 9.1에 따라 ODsay를 재호출하여 실시간 정보로 갱신한 뒤 발송한다. 사용자가 받는 알람은 항상 최신 정보 기반.
- `reminder_at = recommended_departure_time - reminder_offset_minutes`

---

## 10. P1 / P2 보류 항목

### P1 — 데모 후 우선 부활 후보

| 항목 | 부활 비용 |
|---|---|
| 다중 경로 후보 응답 | DB에 `selected_path_index TINYINT NOT NULL DEFAULT 0` 컬럼 추가. `route_summary_json`은 이미 raw 저장이라 backfill 불필요 |
| `POST /schedules/{id}/route/select` | 위 부활과 함께 |
| `GET /routes/{routeId}` | routeId 발급 정책 정의 후 (가상 ID 또는 별도 테이블) |
| `POST /feedbacks` (체감 불쾌도, 정확성 평가) | `feedback` 테이블 부활 |
| `GET /feedbacks` | 동일 |
| 회원 즐겨찾기 | `favorite_route` 테이블 부활 |
| `GET /members/me`에 `preferences`, `calendarLinked` 부활 | 관련 테이블 부활과 동시 |

### P2 — 장기 차별화

| 항목 | 비고 |
|---|---|
| 시간대별 평균 소요시간 학습 | 사전 호출 배치 + lookup 테이블 |
| 지하철/버스 혼잡도 예측 | `subway_congestion_lookup`, `bus_congestion_lookup` |
| 후보 리랭킹 (사용자 선호 반영) | `member_preferences`, `ranking_weight` |
| 외부 캘린더 연동 | `member_calendar_link`, `/schedules/import-calendar` |
| ODsay 응답 글로벌 캐시 | `odsay_cache` (출/도착 좌표 + time bucket 키) |
| 분산 스케줄러 | ShedLock |

---

## 11. 데이터 타입 부록

### 11.1 `Place`

```typescript
{
  name: string;          // 장소명
  lat: number;           // 위도
  lng: number;           // 경도
  address?: string;      // 도로명 주소
  placeId?: string;      // 외부 장소 ID
  provider?: "NAVER" | "KAKAO" | "ODSAY" | "MANUAL";
}
```

### 11.2 `RoutineRule`

```typescript
{
  type: "ONCE" | "DAILY" | "WEEKLY" | "CUSTOM";
  daysOfWeek?: ("MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN")[];
  // WEEKLY일 때 사용
  intervalDays?: number;
  // CUSTOM일 때 사용 (N일 간격)
}
```

### 11.3 `Schedule` (응답 공통)

```typescript
{
  scheduleId: string;                    // sch_{ULID}
  title: string;
  origin: Place;
  destination: Place;
  userDepartureTime: string;             // ISO 8601 KST
  arrivalTime: string;
  estimatedDurationMinutes: number | null;
  recommendedDepartureTime: string | null;
  departureAdvice: "EARLIER" | "ON_TIME" | "LATER" | null;
  reminderOffsetMinutes: number;
  reminderAt: string | null;
  routineRule: RoutineRule | null;
  routeStatus: "CALCULATED" | "PENDING_RETRY";
  routeCalculatedAt: string | null;
  createdAt: string;
}
```

### 11.4 `Route` (단일 경로)

```typescript
{
  totalDurationMinutes: number;
  totalDistanceMeters: number;
  totalWalkMeters: number;
  transferCount: number;
  payment: number;
  segments: RouteSegment[];
}
```

### 11.5 `RouteSegment`

```typescript
{
  mode: "WALK" | "BUS" | "SUBWAY";
  durationMinutes: number;
  distanceMeters: number;
  from?: string;                  // 출발 정류장/지점
  to?: string;                    // 도착 정류장/지점
  lineName?: string;              // 버스/지하철일 때
  lineId?: string;
  stationStart?: string;          // 지하철일 때
  stationEnd?: string;
  stationCount?: number;          // 정류장 수
  path: [number, number][];       // [lng, lat] 배열
}
```

### 11.6 `ApiResponse<T>` (공통 응답 래퍼)

```typescript
// 성공
{ data: T }

// 실패
{
  error: {
    code: string;
    message: string;
    details: object | null;
  }
}
```

---

## 12. 백엔드 구현 체크리스트

명세 기반 작업 누락 방지용.

### 12.1 인프라
- [x] Flyway 추가 + `V1__init.sql` (DB-SQL.txt 그대로) — Step 1 완료 (황찬우)
- [x] Spring Security + JJWT 의존성 추가 — Step 1 완료
- [x] `application.yml`: `spring.jackson.time-zone: Asia/Seoul` — Step 1 완료
- [x] `application.yml`: `odsay.base-url`, `odsay.api-key` 환경변수 추가 — Step 1 완료
- [x] CI: `./gradlew build` 활성화 — Step 1 완료

### 12.2 공통 (common 패키지)
- [ ] `ApiResponse<T>` 응답 래퍼 (`{"data": ...}`)
- [ ] `ErrorResponse` + `ErrorCode` enum (1.6의 모든 코드)
- [ ] `GlobalExceptionHandler` (도메인 예외 → ErrorCode 변환)
- [ ] `JwtAuthenticationFilter`, `JwtProvider`
- [ ] `SecurityConfig`: permitAll = `/auth/signup`, `/auth/login`, `/main`, `/map/config`, `/actuator/health`
- [ ] `BaseEntity` (createdAt, updatedAt)
- [ ] ULID 생성 유틸

### 12.3 도메인 분담 (v1.1.12 확정)
- [x] `auth`, `member`, `schedule`: 황찬우 — Step 3/4/5 완료 (PR #4/#5/#6/#7/#10)
- [x] `route` (§6 + §9.1 ODsay 재호출 글루): 이상진 — Step 6 완료 (PR #11/#13/#14)
- [x] `push` (§7 + §9.1 + §9.2 글루): 이상진 — Step 7 완료 (PR #24, 황찬우 위임 + issue #9 cascade)
- [x] `map` (§4 `/main`, `/map/config`): 이상진 — Step 8 완료 (PR #27)
- [x] `geocode` (§8): 이상진 — Step 8 완료 (PR #27)

### 12.4 ODsay 연동
- [x] `OdsayClient.searchPubTransPathT(SX, SY, EX, EY)` — Step 3 외부 API 클라이언트 골격 (이상진, PR #3)
- [x] 응답을 raw JSON 그대로 `route_summary_json`에 저장 — Step 6 완료 (이상진)
- [x] 응답 `result.path[0]`만 추출해 DTO 변환 (§6.1 매핑표 따름) — Step 6 완료 (이상진)
- [x] graceful degradation: try-catch로 감싸 schedule 등록 자체는 성공 — Step 6 완료 (이상진)

### 12.5 스케줄러
- [x] `@Scheduled(fixedDelay = 30000)` PushScheduler — Step 7 완료 (이상진)
- [x] 루틴 일정 다음 occurrence 계산 (`RoutineCalculator`) — Step 5 schedule 도메인 (황찬우)
- [x] 누락 방지: 5분 윈도우 내 미발송 알림 스캔 — Step 7 완료 (이상진)
- [x] `push_log` 기록 — Step 7 완료 (이상진)

### 12.6 Web Push
- [x] `nl.martijndwars:web-push` 의존성 — Step 1 완료
- [x] VAPID 키페어 application.yml에 환경변수로 주입 — Step 1 (선반영) + Step 7 PushSender 결선
- [x] 발송 시 endpoint 만료(410 Gone) 처리 → 자동 `revoked_at` 갱신 — Step 7 완료 (이상진)

---

**문서 끝.**
