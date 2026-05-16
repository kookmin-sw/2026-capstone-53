# Frontend ↔ Backend Cross-Check Audit

작성일: 2026-05-16
대상 브랜치: backend `feat/backend` (v1.1.37-MVP) ↔ frontend `origin/feat/frontend` (read-only worktree)
범위: **이번 PR 범위 외** + **본인 담당(route/push/map/geocode) 외** 모든 도메인 전체 (auth, members, schedules, route, push, main, map, geocode, common)

> **목적**: 프론트엔드 코드와 백엔드 코드를 한 줄씩 대조하여 백엔드 측에서 고쳐야 할 모든 항목을 추출. 프론트 측 결함은 (제외) 표기로 명시만 하고 본 문서의 fix 목록에는 포함하지 않음.
>
> **전제**: 프론트는 v1.1.x 백엔드 피드백 (v1.1.34~v1.1.37) 을 아직 반영하지 않은 상태. 명세 (`api-spec.md`) 가 정합 기준 (single source of truth).
>
> **분류**: P1 (Blocker, 데모 실동작 차단 가능) / P2 (운영/안정성/관측성) / Nit (개선)

---

## 0. 요약

| 도메인 | 백엔드 fix 필요 | 이미 처리됨 (v1.1.x) | 프론트 fix 영역 (제외) | 결론 |
|--------|----------------|---------------------|----------------------|------|
| Auth (로그인/회원가입/JWT) | **0건** | — | — | ✅ 정합 |
| Members | **0건** | — | 1건 (typedef) | ✅ 정합 |
| Schedules | **1건 (P2)** | 2건 | — | ⚠ scheduleId strict validate 비대칭 |
| Route | **0건** | 11건 (v1.1.36) | — | ✅ 정합 |
| Push | **0건** | — | 2건 (mock 데이터) | ✅ 정합 |
| Main / Map | **0건** | — | — | ✅ 정합 (3-way diff 검증 완료) |
| Geocode | **0건** | 5건 (v1.1.37) | — | ✅ 정합 |
| Common (envelope/auth/CORS/시간) | **0건** | — | — | ✅ 정합 |

**총 백엔드 fix 후보**: P1 0건, P2 1건 (Schedules `scheduleId` strict validate — 황찬우 영역), Nit 0건. **데모 (2026-05-22) 블로커 없음.**

---

## 1. Auth (로그인 / 회원가입 / JWT) — 황찬우 영역

### 검증 영역
- POST `/auth/login` 요청/응답 envelope
- POST `/auth/signup` 요청 검증 (email, password, nickname)
- JWT bearer 흐름 (issuance → header injection → filter → 401 매핑)
- 프론트 `fetchClient.js` ↔ 백엔드 `JwtAuthenticationFilter` + `SecurityConfig`

### 결과
**백엔드 fix 필요: 0건.** 모든 영역 정합.
- 응답 envelope `{ ok, data | error }` 일치
- 401 매핑 (`AuthenticationException` → 표준 envelope) 정합
- 토큰 헤더 `Authorization: Bearer <jwt>` 키 케이스 일치
- 프론트 `errors.js` 코드 매핑이 백엔드 `ErrorCode` 와 1:1

---

## 2. Members — 황찬우 영역

### 검증 영역
- GET / POST / PATCH / DELETE `/members/*`
- 멤버 동기화 흐름, 권한 (본인만)

### 결과
**백엔드 fix 필요: 0건.** 모든 필드/검증/응답 정합.

### 프론트 fix 영역 (제외)
- `PUSH_SUBSCRIBE_CONFLICT` typedef 가 프론트 `src/api/errors.js` 정의 누락. → 프론트 PR 대상.

---

## 3. Schedules — 황찬우 영역

### 검증 영역
- POST `/schedules` (createSchedule)
- GET `/schedules` (listSchedules)
- DELETE `/schedules/{id}`
- 프론트 `Calendar.js` / `ScheduleCard.js` / `mocks/handlers/schedules.js` ↔ 백엔드 `ScheduleController` + DTO

### 백엔드 fix 후보

#### [P2] scheduleId strict validate 정책 비대칭
- **현상**: `RouteController` 는 path `scheduleId` 에 ULID 형식 `@Pattern` (또는 strict validator) 적용되어 invalid 시 400 으로 폴백. `ScheduleController` 의 DELETE/GET-by-id 는 동일 검증이 부재 — invalid 입력이 그대로 service 로 흘러가 404 매핑까지 도달.
- **영향**: 운영/관측 일관성. 데모 동작에는 영향 없음 (실 클라이언트는 유효 ULID 만 송신).
- **권고 fix**:
  - `ScheduleController` path variable 에 `@Pattern(regexp = "^[0-9A-HJ-KM-NP-TV-Z]{26}$")` 추가 (Crockford Base32, ULID 26 자)
  - 또는 공용 `@UlidPath` 메타 어노테이션 신설 후 두 컨트롤러에 일괄 적용

#### [이미 처리됨] GeocodeResponse provider 변환
- v1.1.30 에서 `KAKAO` 도메인 ENUM 값으로 응답 단계 변환 완료. 프론트가 `kakao` (lower) 사용 시 case-insensitive 비교 또는 toLowerCase 변환은 프론트 PR 대상.

#### [이미 처리됨] /geocode/search size cap valid-only 카운팅
- v1.1.37 P1#11 처리 완료. invalid skip 은 size 미차감.

#### [P2 → 보류] PlaceDto 좌표 타입
- 백엔드: `BigDecimal lat, lng` (정밀도 보존), 프론트: JS `number` (IEEE 754 double)
- 명세 §8.1/§8.2 모두 `number` 로 직렬화 정의 — Jackson `BigDecimalSerializer` 가 number primitive 로 송신하므로 wire-level 정합. fix 불필요. 다만 백엔드 내부 산술 (route 계산 좌표 비교) 시 BigDecimal/double mixed 변환 시점에서 precision drift 가능성은 후속 리뷰 항목 (현재 영향 없음).

---

## 4. Route — 이상진 영역 (본인)

### 검증 영역
- GET `/schedules/{id}/route?forceRefresh={true|false}`
- 캐시 hit / miss / forceRefresh 경로
- `RouteResponse.calculatedAt` non-null 보장
- ODsay 호출 실패 502 매핑, transferCount 의미, in-memory 캐시
- 프론트 `RouteCard.js` / `mocks/handlers/route.js` ↔ 백엔드 `RouteController` + `OdsayRouteService` + `RouteResponse`

### 결과
**백엔드 fix 필요: 0건.** v1.1.36 에서 11건 일괄 처리됨.
- `RouteResponse.of(...)` compact constructor 가 `calculatedAt` non-null 강제 (v1.1.36)
- forceRefresh 디폴트 `false` 와 boolean parse 정합
- 502 응답 envelope (`EXTERNAL_ROUTE_API_FAILED`) 매핑 통과
- 프론트 mock 의 path field `{ path: {}, lane: null }` shape 정합

### 보류 (Step 6 PR #11 follow-up 메모)
- `transferCount` 의미 (도보 환승 포함 여부), in-memory Route 캐시 동시성, `Route.equals` 비교 함정 — 트리거 만족 시 다음 PR 반영 (이번 PR 범위 외).

---

## 5. Push — 이상진 영역 (본인 → 황찬우 위임)

### 검증 영역
- POST `/push/subscribe` (endpoint + p256dh + auth)
- DELETE `/push/subscriptions/{id}`
- VAPID public key endpoint
- RFC 8030 push gateway 호출
- 프론트 `mocks/handlers/push.js` / `NotificationPage.jsx` ↔ 백엔드 `PushController` + `WebPushService`

### 결과
**백엔드 fix 필요: 0건.**
- `PUSH_SUBSCRIBE_CONFLICT` 응답 코드 백엔드 매핑 정상 (UNIQUE INDEX 위반 → 409 envelope)
- endpoint 응답 마스킹/보안 정상 (서비스 도메인만 노출, query 미노출)

### 프론트 fix 영역 (제외)
- 프론트 `errors.js` 에 `PUSH_SUBSCRIBE_CONFLICT` typedef 누락 → 프론트 PR 대상
- 프론트 mock ULID 형식 (Crockford Base32) 일부 lower-case 혼입 → 프론트 PR 대상

### 참고
- 본 도메인은 이슈 #9 / 사용자 메시지로 황찬우 인계 확정 (Step 7 PR 시 §12.3 stale 갱신 동반).

---

## 6. Main — 이상진 영역 (본인)

### 검증 영역
- GET `/main` (홈 카드/요약 응답) ↔ 백엔드 `MapController.main` + `MainResponse`/`NearestScheduleDto`/`MainPlaceDto`
- 프론트 `types/api.js` 의 `NearestSchedule` typedef + `GetMainResponse` typedef
- 프론트 `HomeV2Route.jsx` (실 production 페이지)

### 결과
**백엔드 fix 필요: 0건.** 3-way diff (2026-05-16) 검증 완료.

#### 3-way diff 결과

| 필드 | 명세 §4.1 | 백엔드 `NearestScheduleDto` | 프론트 typedef (`types/api.js:207-216`) | 정합 |
|------|----------|---------------------------|----------------------------------------|------|
| scheduleId | ✅ | ✅ | ✅ | ✅ |
| title | ✅ | ✅ | ✅ | ✅ |
| arrivalTime | ✅ | ✅ | ✅ | ✅ |
| origin (name/lat/lng) | ✅ | ✅ | ✅ | ✅ |
| destination (name/lat/lng) | ✅ | ✅ | ✅ | ✅ |
| hasCalculatedRoute | ✅ | ✅ | ✅ | ✅ |
| recommendedDepartureTime | ✅ | ✅ | ✅ | ✅ |
| reminderAt | ✅ | ✅ | ✅ | ✅ |
| mapCenter (lat/lng) | ✅ | ✅ (`MainResponse`) | ✅ (`GetMainResponse`) | ✅ |

#### 사용처 검증
- 프론트 production 코드에서 `/main` 직접 호출 없음 — `HomeV2Route.jsx` 는 `api.schedules.list()` + `api.route.get()` 으로 동등 데이터 구성
- `mockData.js` / `archive/` 의 `nearestSchedule.reminderOffsetMinutes` / `routineRule.daysOfWeek` 참조는 **stale mock 전용** (production 미참조), `types/api.js` 의 `NearestSchedule` 정식 typedef 와 일치하지 않음 — 프론트 측 정리 대상이지 백엔드 명세/DTO 변경 사유 아님
- `api.main.get()` ([api/index.js:124](/tmp/todayway-frontend/src/api/index.js#L124)) 는 정의만 있고 production 호출처 없음 (향후 게스트 홈 화면 도입 시 활성)

#### /main query fallback
- 현 정책 (게스트 허용, 인증 시 일정 정보 추가, `lat`/`lng` XOR 검증 v1.1.19) 명세 §4.1 와 정합. 프론트 영향 없음.

---

## 7. Map / Kakao Map — 이상진 영역 (본인)

### 검증 영역
- 지도 SDK 호출 / LatLng vs GeoJSON [lng,lat] 순서
- 마커 placement 좌표 송신 형식
- 프론트 `KakaoMap.js` / `MapPage.jsx` ↔ 백엔드 좌표 응답 (Geocode + Route path)

### 결과
**백엔드 fix 필요: 0건.**
- 백엔드 응답이 `{ lat, lng }` 키 분리 (배열 순서 의존 X) → 프론트 SDK 호환 OK
- v1.1.37 에서 NaN/Infinity 가드 추가로 marker placement 폭주 차단

---

## 8. Geocode — 이상진 영역 (본인)

### 검증 영역
- GET `/geocode` (단일, schedule 저장 흐름)
- GET `/geocode/search` (후보 다건)
- TTL eviction (`GeocodeCacheCleanupScheduler`, 30일)
- Kakao Local 호출 실패 매핑
- 프론트 검색 UI ↔ 백엔드 `GeocodeController` + `GeocodeService`

### 결과
**백엔드 fix 필요: 0건.** v1.1.37 에서 5건 일괄 처리됨.
- P1#2: query masking (`queryLength=…`)
- P1#6: NaN/Infinity isFinite 가드
- P1#7: KakaoLocalClient `RuntimeException` → 502 매핑
- P1#10: `CACHE_TTL_DAYS` 단일 출처 (`GeocodeService.CACHE_TTL_DAYS` 참조)
- P1#11: size cap valid 후보 카운팅 (invalid skip 미차감)

---

## 9. Common Infrastructure — 황찬우 영역

### 검증 영역
- 응답 envelope `{ ok, data | error }` shape
- `ErrorCode` enum ↔ 프론트 `errors.js` 코드 매핑
- JWT 인증 필터 + `SecurityConfig` 화이트리스트
- CORS 헤더 (Origin, Authorization preflight)
- 시간대 (`spring.jackson.time-zone: Asia/Seoul` ISO 8601 +09:00)
- Pagination 표준 (`page`, `size`, `totalElements`)
- 글로벌 예외 핸들러 (BusinessException, ValidationException, AuthenticationException)

### 결과
**백엔드 fix 필요: 0건.** 모든 영역 정합.

---

## 10. 결론

### 백엔드 측 actionable 항목 (정렬: 우선순위)

| # | 도메인 | 항목 | 우선순위 | 담당 | 비고 |
|---|--------|------|---------|------|------|
| 1 | Schedules | scheduleId path strict validate (ULID 26자 `@Pattern`) | P2 | 황찬우 | RouteController 와 대칭화 |

### 데모 (2026-05-22) 블로커
**없음.** P1 항목 0건. P2 1건은 황찬우 영역 운영/관측 일관성 이슈로, 실 사용자 흐름 (로그인 → 일정 등록 → 경로 조회 → 푸시) 은 모두 정합.

### 프론트 측 후속 작업 (본 문서 범위 외, 참고용)
- `errors.js` 에 `PUSH_SUBSCRIBE_CONFLICT` typedef 추가
- mock 데이터 ULID 형식 Crockford Base32 (대문자 + I/L/O/U 제외) 일관화
- v1.1.x 백엔드 피드백 반영 (envelope 변경분 없음, code 매핑 추가만)

---

## 11. 검증 범위 추적표

| 컨트롤러 | 엔드포인트 | 프론트 호출 위치 | 정합 |
|---------|-----------|----------------|------|
| AuthController | POST /auth/login, /auth/signup | LoginPage.jsx, SignupPage.jsx | ✅ |
| MemberController | /members/* | Settings.js | ✅ |
| ScheduleController | /schedules, /schedules/{id} | Calendar.js | ⚠ (P2 §3) |
| RouteController | /schedules/{id}/route | RouteCard.js | ✅ |
| PushController | /push/subscribe, /push/subscriptions/{id}, /push/vapid | NotificationPage.jsx | ✅ |
| MapController.main | /main | (typedef 정의만, 직접 호출 없음) | ✅ |
| GeocodeController | /geocode, /geocode/search | (검색 UI) | ✅ |
| Kakao Map SDK | — | KakaoMap.js, MapPage.jsx | ✅ |

---

**감사 결론**: 본인 담당 (route/push/map/geocode) v1.1.34~v1.1.37 작업으로 owned 도메인은 모두 정합. 비담당 도메인 (auth/members/schedules/main) 도 큰 결함 없음. 백엔드 fix 후보는 P2 2건 (Schedules path validate 대칭화, Main DTO 3-way diff) 뿐이며, 두 건 모두 별도 PR 로 분리 가능한 운영성 개선 항목.
