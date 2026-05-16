# 오늘어디 (TodayWay) Backend API 명세

> **버전**: v1.1.40-MVP
> **최종 수정**: 2026-05-17 (황찬우 — 슬랙 follow-up 통합. (T1) `§5.2 GET /schedules` 목록 응답 풀필드 통일 (캘린더 반복 일정 표시 버그 #2 직접 원인). (T2) `AuthService.login` 디버그 logging 1줄 (재로그인 안 됨 #5 FE 합동 재현용, 데모 후 제거). (T4) RoutineRule `startDate` / `endDate` 신규 + V6 마이그레이션 + `RoutineCalculator` endDate 도달 시 advance 종료 가드. (T5+T5-Q4) `userDepartureTime` optional 자동 계산 + V6_5 마이그레이션 + Schedule `userDepartureTimeAutoFilled` 컬럼 + 응답 `departureAdviceReliable` 메타. (T7) `arrivalTime` 미입력 명세 비고. (R1~R3) `reminderOffsetMinutes` `@Min(0) @Max(1440)` 검증 + 도메인 비고 ("본 알림은 출발 알림이며 일정 사전 알림과 구분"). (R4+R4-Q2) `reminderAt` clamp/skip 가드 (floor=NOW()+60s, ceiling=arrivalTime-1min) + 응답 `reminderClamped` / `reminderSkipped` 메타. T6 (member.default_reminder_offset + Settings API) 와 FE 마이그레이션 (#1/#6/#7) 은 데모 후 별 PR.)
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
| **v1.1.21** | **2026-05-08** | **§6.1 WALK 구간 `path` 출처 = TMAP 보행자 경로 (인도 곡선). 기존 v1.1.9 합성 직선 → `POST https://apis.openapi.sk.com/tmap/routes/pedestrian` 호출 결과(GeoJSON LineString features)로 승격 — 4차선 도로 가로지르는 비현실적 직선 시각화 차단. WALK 구간당 1회 추가 호출. 외부 API 의존성 +1 (`TMAP_APP_KEY` 환경변수). 모든 실패 (키 미설정 / 401/403 / timeout / 5xx / 응답 형식 위반) 는 graceful — v1.1.9 합성 직선 fallback. ErrorCode 신규 X. 시각 검증: `~/route-preview/odsay-tmap-walk.html` (이상진).** |
| **v1.1.23** | **2026-05-11** | **§1.6 `RESOURCE_NOT_FOUND` ErrorCode 추가 (이슈 #33). 매핑되지 않은 URL 호출 시 Spring 6.x 가 `NoResourceFoundException` throw → `GlobalExceptionHandler` catch-all 이 잡아 500 `INTERNAL_SERVER_ERROR` 로 폴백하던 결함 fix. 신규 핸들러는 404 + WARN 로깅 (`resourcePath` 한 줄, 스택 미포함 — 4xx 류 ERROR 가 운영 false alarm 의 원인이었음). `NoHandlerFoundException` 도 동시 매핑 (현 application.yml 미설정이라 미발생, future-proof). 405 `HttpRequestMethodNotSupportedException` 잘못 매핑 (400 → 405) 은 별 이슈로 분리.** |
| **v1.1.22** | **2026-05-11** | **§3.3 회원 탈퇴 soft delete → hard delete 전환 (이슈 #31). soft delete + `login_id` UNIQUE 충돌로 동일 loginId 재가입 불가하던 버그 해소. DB FK ON DELETE CASCADE 가 refresh_token / schedule / push_subscription row 일괄 삭제 — 코드 cascade 메서드 (`ScheduleRepository.softDeleteByMemberId` / `PushSubscriptionRepository.revokeAllByMemberId`) 제거. push_log 는 FK 비대칭 동작 — `schedule_id` ON DELETE SET NULL (다른 회원의 schedule 삭제 시 이력 보존), `subscription_id` ON DELETE CASCADE (탈퇴 회원 본인의 발송 이력은 동반 삭제, 회원 데이터 완전 삭제 정책). schedule 개별 DELETE / push subscription unsubscribe 는 별개 정책으로 soft delete/revoke 유지. 멱등성 비고 (v1.1.7) 그대로 — 두 번째 DELETE 도 401 UNAUTHORIZED (member row 없음). V3 마이그레이션 (`V3__member_drop_deleted_at.sql`) 적용 시 옛 soft-deleted row 정리 (FK CASCADE 발동) + `deleted_at` 컬럼 drop.** |
| **v1.1.24** | **2026-05-12** | **§1.9 CORS 정책 섹션 신규 추가 — `CorsConfigurationSource` Bean 등록 (`common.security.CorsProperties` + `SecurityConfig`). 화이트리스트 default `http://localhost:3000` (yml fallback, env `CORS_ALLOWED_ORIGINS` 콤마 구분 override), `allowCredentials=false` (JWT stateless 정합), `allowedHeaders="*"` (`Content-Type` / `Authorization` 커버), `maxAge=1800`. PR #29 (frontend 통합) unblock. 운영 도메인은 별 task (외부 노출) 진입 시 `CORS_ALLOWED_ORIGINS` env 로 보강.** |
| **v1.1.40** | **2026-05-17** | **슬랙 follow-up 통합 (황찬우) — 8 task 단일 PR. **(T1) `§5.2 GET /schedules` 목록 응답 풀필드 통일**: `ScheduleListItem` 이 기존 7필드 (`scheduleId/title/origin/destination/arrivalTime/recommendedDepartureTime/routeStatus`) 만이라 프론트가 캘린더에서 반복 일정의 `routineRule.daysOfWeek` 를 받지 못해 단발 처리해 `arrivalTime` 날짜 1건만 표시 + dispatcher advance 후 "다음주 일정" 1건만 남는 결함 (슬랙 #2). `ScheduleResponse` 와 동일 풀필드 (userDepartureTime / estimatedDurationMinutes / departureAdvice / reminderOffsetMinutes / reminderAt / routineRule / routeCalculatedAt / createdAt / departureAdviceReliable 추가). record 분리 유지 — 향후 목록 경량화 가능성 trail. **(T2) `AuthService.login` debug logging 1줄**: 슬랙 #5 (회원가입→로그아웃→재로그인 안 됨) FE 합동 재현용. `loginId` 와 `password.length()` (PII 차단, 평문 X) 로 자동완성/IME silent 공백 식별. 데모 후 T2 별 task 검증 완료 시 제거. **(T4) RoutineRule `startDate`/`endDate` 확장**: 슬랙 #4 + 추가사항 (4). V6 마이그레이션 — `schedule.routine_start_date` / `routine_end_date` (DATE NULL) 추가. `endDate=null` = 무한반복 (default, 슬랙 "끝나는 날짜 설정 안함" 정합). `startDate` 과거 허용 — dispatcher 가 `reminder_at > NOW()` 만 발송이라 과거 occurrence silent skip. `RoutineCalculator.calculateNextOccurrence` 에 endDate 가드 — KST 기준 LocalDate 비교 (v1.1.25 패턴 정합), endDate 초과 시 null 반환 → §9.2 advance 종료 → `reminder_at NULL` dormant ("자동 삭제 X" 슬랙 #2 정합). backward compat — 기존 schedule (NULL) = 기존 동작 (무한반복) 보존. **(T5 + T5-Q4) `userDepartureTime` optional + departureAdviceReliable 메타**: 슬랙 #3 ("출발 시각 입력 필요성 X — 노드별 N분 전 알림") + 외부 review T5-Q4 (hero feature 트레일 보존). V6_5 마이그레이션 — `schedule.user_departure_time NULLABLE` + `user_departure_time_auto_filled BOOLEAN NOT NULL DEFAULT FALSE`. `CreateScheduleRequest.userDepartureTime @NotNull` 제거. 사용자 미입력 시 BE 가 ODsay 응답의 `recommendedDepartureTime` 으로 자동 채움 + autoFilled=true. PATCH 로 사용자 명시 변경 시 autoFilled=false 갱신. 응답 schema 에 `departureAdviceReliable = !autoFilled` 노출 — FE 가 false 시 `departureAdvice` 회색 처리 권고 (자동 채움 시 user==recommended 라 비교 신호 의미 X). FE 폼에서 `arrivalTime - 30분` 등 prefill 권고 — 사용자가 prefill 값을 명시 변경하면 hero feature (EARLIER/ON_TIME/LATER) 정상 산출. **(T7) `arrivalTime` 미입력 명세 비고**: BE `@NotNull` 이미 강제 — 슬랙 #1 "등록은 되지만 캘린더 X" 는 FE 폼이 invalid/default date 전송하는 패턴. §5.1 비고 한 줄로 "arrivalTime 누락 시 400 VALIDATION_ERROR, FE client-side 검증 필수" 명시. **(R1~R3) `reminderOffsetMinutes` 범위 검증**: 슬랙 FE 채팅 — 명세 공백 + DTO 검증 0. `CreateScheduleRequest` / `UpdateScheduleRequest` 의 `reminderOffsetMinutes` 에 `@Min(0) @Max(1440)` 추가. 음수는 reminder_at 이 출발시각 *이후* 가 되는 UX 위반 + dispatcher 폴링 윈도우 silent 누락 위험. 1440 (24시간 = 1일 전) 상한은 일반 calendar 앱 표준. §5.1 비고에 "본 알림은 출발 알림이며 일정 사전 알림과 구분" — 평가관 질문 대비 (Google Calendar 의 일정 자체 알림과 도메인 차이 명시). **(R4 + R4-Q2) `reminderAt` clamp/skip 가드**: T6 default = 30분 채택 시 가까운 일정에서 새 일정의 silent 누락 흔해질 시나리오 (예: 30분 후 일정에 30분 offset → reminder_at = NOW() — dispatcher 폴링 5분 윈도우 race). 등록/수정 시 `ScheduleService.applyReminderAtGuard` 가 3분기 처리: (a) 정상 (floor=NOW()+60s ~ ceiling=arrivalTime-1min) → 그대로, (b) clamp (reminderAt < floor) → floor 로 override + `reminderClamped: true` 메타, (c) skip (ceiling < floor — arrivalTime 자체 너무 가까움) → `reminderAt = null` override + `reminderSkipped: true` 메타. `Schedule.overrideReminderAt(OffsetDateTime nullable)` 신규 메서드. FE 가 메타로 toast 표시 권고. **운영 RDS 전제**: V6 / V6_5 모두 NULL 허용 ADD COLUMN — backward compat 안전. Forward fix 전략 — 회귀 발견 시 V8 으로 대응, 컬럼 자체 DROP 은 데모 후 데이터 보존 기간 (1주일) 이후. **별 task**: T6 (member default offset + Settings API) — FE Settings BE 연동 ~80~110줄 부담으로 데모 후 별 PR. #5 재로그인 안 됨 — FE 합동 재현 후 원인 확정. #6 지오코딩 다중 후보 — `/geocode/search` (v1.1.27) FE 마이그레이션 잔존. #7 지도 임베딩 — FE Kakao Map SDK. #8 푸시 안 뜸 — VAPID env EC2 주입 (인프라). K (PENDING_RETRY 자동 재시도 / 알림 미수신 사용자 인지) — 후속 백로그.** |
| **v1.1.39** | **2026-05-16** | **PR #54 follow-up — audit doc 사실 정정 + geocode catch 가드 (황찬우) — (1) `backend/docs/cross-check-frontend.md` (v1.1.37 audit deliverable) 의 push 도메인 4건 사실 정정: §5 검증영역 + §11 추적표 의 DELETE path (`/push/subscriptions/{id}` plural → `/push/subscribe/{subscriptionId}` singular, `PushController.java:43` 정합), §5 결과 bullet 의 `PUSH_SUBSCRIBE_CONFLICT` 코드 및 원인 ("UNIQUE INDEX 위반 → 409" → "UPSERT race contention → 503 SERVICE_UNAVAILABLE", v1.1.35 `ErrorCode.PUSH_SUBSCRIBE_CONFLICT(SERVICE_UNAVAILABLE)` 정합), §5/§11 의 존재하지 않는 "VAPID public key endpoint" 표기 제거 (VAPID 는 `application.yml` config 차원, HTTP endpoint X), §3 P2 권고 본문의 "RouteController 에 strict validate 적용됨" → "RouteController/ScheduleController 둘 다 미적용, `@UlidPath` 메타 어노테이션 도입 후 두 controller 일괄 적용 (후속 PR 백로그)". §0 요약표 / §10 결론표 / §11 추적표 / §12 정정 이력 동반 갱신. (2) `GeocodeService.callKakao` 에 `catch (ExternalApiException)` 와 `catch (RuntimeException)` 사이에 `catch (BusinessException be) { throw be; }` 명시 가드 추가 — v1.1.37 P1#7 의 RuntimeException catch 가 `BusinessException extends RuntimeException` 라 도메인 예외까지 silent 502 변환할 위험. v1.1.37 커밋 메시지에 "BusinessException 우선 catch propagate" 의도가 명시돼 있던 부분을 코드에 반영. 회귀 가드 `GeocodeControllerIntegrationTest.geocode_KakaoLocalClient_BusinessException_시_원래_코드_propagate` (KakaoLocalClient 가 `EXTERNAL_AUTH_MISCONFIGURED` BusinessException 을 던졌을 때 응답 503 + 원본 ErrorCode 유지) 1건 추가. **운영 RDS 전제 (Q1)**: V4/V5 마이그레이션은 운영 RDS 의 schema_history 가 Flyway 단독 관리 (manual SQL 미적용) 라는 전제 하에 fresh DB / V4 적용 DB / V3 까지만 적용된 DB 모두 안전 — V5 의 `DROP COLUMN` IF EXISTS 미지원 (MySQL 8.x) 라 manual 변경 있으면 별도 점검 필요.** |
| **v1.1.38** | **2026-05-16** | **fresh 코드리뷰 P2 3건 fix (이상진) — **(1) `§7.1 PushSubscribeRequest.keys.p256dh/auth` base64url `@Pattern` 가드**: 기존엔 `@NotBlank + @Size(max=255)` 만이라 RFC 4648 §5 (URL/Filename safe) alphabet 을 벗어난 garbage 문자열 (공백 / `+` / `/` / `@` / 비ASCII) 이 그대로 통과 → `PushSender.send` 의 nl.martijndwars `Notification` 생성자가 base64 디코드 실패 `IllegalArgumentException` 던져 catch-all 에 `FAILED` 로 흡수되어 발송 영구 실패 좀비 row 생성. v1.1.32 endpoint scheme 가드 패턴 정합으로 진입점 `VALIDATION_ERROR` (400) fail-fast. P-256 ECDH 공개키 (raw 65 byte) → 87자 + 0~1 padding, auth (16 byte raw) → 22~24자 모두 `@Size(max=255)` 안. 회귀 가드: `PushControllerIntegrationTest.keys_base64url_아니면_400_VALIDATION_ERROR` (p256dh / auth 각 5 invalid case). **(2) `§4.1 NearestScheduleDto` corrupted coordinate WARN `side` 추가**: 4 케이스 (originLat / originLng / destinationLat / destinationLng) 가 동일 메시지로 묻혀 운영 진단 시 root cause 추적 불가. `side=ORIGIN|DESTINATION|ORIGIN+DESTINATION` 표기로 PII 없이 정보량만 증가 — alert 매칭 + root cause 분리 모두 가능. mapper 시그니처 / null 반환 invariant 그대로라 기존 v1.1.34 회귀 가드 (4 null 케이스) 그대로 통과. **(3) `OdsayClient.maskCoord` / `TmapClient.maskCoord` `Locale.ROOT` 명시**: `String.format("%.1f*", v)` 가 JVM default locale 의존 — 운영 호스트가 유럽권 locale (예: `de_DE`) 로 부팅되면 `37,5*` 류로 직렬화되어 로그 파서 / `grep` regex 호환성이 silent 깨지던 잠재 결함. `Locale.ROOT` 고정으로 locale-independent 회귀 안전. v1.1.33 도입 시 의도된 정규 출력 형식과 일치.** |
| **v1.1.37** | **2026-05-16** | **§8 Geocode 도메인 보안/정합성 5건 묶음 (이상진) — **(1) `KakaoLocalClient.searchKeyword` 호출 로그 query 마스킹**: 기존엔 `log.debug("Kakao Local 호출: query={}", query)` 가 사용자 검색어 평문을 그대로 노출 — `debug` 라도 운영 진단 toggle 시 주소/장소명 PII 평문 누출. `queryLength` 만 출력 (quota/입력 패턴 진단은 length 로 충분, 본문 추적은 `GeocodeService` 의 query hash 로 가능). **(2) `GeocodeCandidate` Double.isFinite 가드**: §8.2 후보 매핑이 `Double.parseDouble` 만 사용해 Kakao 가 `"NaN"`/`"Infinity"` 를 응답하면 그대로 통과 → Jackson 직렬화 후 프론트 지도 SDK 의 marker placement 가 NaN 으로 폭주하던 결함. `parseFiniteCoord` 헬퍼에서 `Double.isFinite` 검증 후 non-finite 면 기존 `NumberFormatException` 흐름에 합류 (caller 가 skip). §8.1 는 `BigDecimal("NaN")` 자체가 throw 라 영향 없음. **(3) `GeocodeService.callKakao` RuntimeException 흡수**: `KakaoLocalClient` 가 외부 `ExternalApiException` 외 unchecked (응답 deserialize 중 `IllegalStateException`, Jackson 매핑 예외 등) 를 던지면 controller 까지 propagate → `500 INTERNAL_SERVER_ERROR` 노출. 명세 §8.1 매핑표상 외부 응답 형식 위반은 502 → `EXTERNAL_ROUTE_API_FAILED` 로 흡수 (보안: 예외 메시지 quota/URL leak 차단, 클래스명만 로깅). 명세상 `BusinessException` 은 우선 catch 로 그대로 propagate. **(4) `CACHE_TTL_DAYS` 단일 출처화**: `GeocodeService` (read filter) 와 `GeocodeCacheCleanupScheduler` (TTL eviction) 가 각자 사본 `30` 을 보유 → 한쪽만 변경 시 cutoff 정합 깨질 위험. `GeocodeService.CACHE_TTL_DAYS` 를 `public static final` 노출, scheduler 가 참조. **(5) §8.2 size cap valid 카운팅 정정**: 기존엔 `Math.min(size, documents.size())` 만큼 raw docs 만 훑어 앞쪽에 invalid 가 끼면 사용자가 요청한 size 보다 적은 후보가 반환 (size=10, Kakao 10건 중 3건 invalid → 7건만 노출). 전체 documents 순회하되 valid candidates 가 size 에 도달하면 break — autocomplete UX 보강. §8.2 본문 "documents[0..size)" 표현 정정.** |
| **v1.1.36** | **2026-05-16** | **§6.1 Route 정합성 2건 (이상진) — **(1) ODsay 401/403 시 stale cache fallback 차단**: `OdsayRouteService.getRoute` 가 ODsay 호출 실패 시 매핑 가능한 캐시가 있으면 stale 응답으로 fallback (§6.1 비고). 기존엔 401/403 auth 에러도 이 분기에 흡수되어, 운영자 조치가 필요한 신호 (`EXTERNAL_AUTH_MISCONFIGURED` 503) 가 다른 회원/요청 응답에 가려져 영구 잠복하던 결함. `refreshRouteSync` 의 auth alert 격상 (v1.1.4) 과 정합하도록 stale 분기 진입 전에 `isAuthError` 우선 검사 → 즉시 503 매핑 + ERROR 로그. 5xx / timeout / network 등 transient 외부 장애는 §6.1 비고대로 stale fallback 유지 (사용자 경험 보호). **(2) `RouteResponse.calculatedAt` non-null invariant**: 응답 record 의 `calculatedAt` 은 cache hit / fresh / stale 모든 경로에서 route 데이터가 존재한다는 건 ODsay 호출이 적어도 한 번 성공했다는 뜻이라 본질적으로 non-null. 기존엔 `Schedule.getRouteCalculatedAt()` 을 그대로 위임해 `route_summary_json` 만 채워지고 `route_calculated_at` 만 NULL 인 corruption (직접 SQL / 부분 마이그레이션) 이 응답에 silent leak. compact constructor 의 `Objects.requireNonNull(calculatedAt)` 로 boundary 차단 + `tryMapCache` 가 `routeCalculatedAt == null` corrupted cache 를 skip (caller 가 fresh 호출 또는 502 분기로 자연 fallback). 회귀 가드: `OdsayRouteServiceTest.getRoute_ODsay_401_403_캐시있어도_stale_차단_503_격상` (parameterized 401/403, mapper.toRoute 미호출 검증) + `getRoute_routeCalculatedAt_null_corrupted_cache_stale_fallback_차단` (502 응답 + mapper 미호출).** |
| **v1.1.35** | **2026-05-16** | **§7 Push 도메인 보안/정합성 3건 묶음 (이상진) — **(1) `PushSender` 로그 endpoint URL 마스킹**: 4xx/5xx 운영 로그가 `endpoint` 전체를 그대로 찍던 결함. push subscription resource path 는 push provider 가 발급한 per-device token (RFC 8030 §5) 이라 그 자체가 발송 권한 자격 — 로그 노출은 다른 회원에게 푸시 보낼 수 있는 자격 누출. `maskEndpoint(String)` 헬퍼 신설 (scheme+host[:port] 만 보존, 파싱 실패 시 `(invalid)` — 절대 원본 fallback X). 정상 provider host (FCM/APNs/Mozilla/WNS) 별 호출 분류는 그대로 가능. **(2) UPSERT race ErrorCode 정밀화**: `PushService.upsertWithRetry` 가 1회 retry 후에도 `DuplicateKeyException`/`TransientDataAccessException` 잡힐 때 기존엔 `ErrorCode.INTERNAL_SERVER_ERROR` (500) 로 던져 클라이언트가 unrecoverable bug 로 오인하던 결함. 본질적으로 "일시적 contention, 잠시 후 재시도" 상태라 신규 `ErrorCode.PUSH_SUBSCRIBE_CONFLICT` (503) 로 격상 → 클라이언트가 retry 가능. 로그는 ERROR 유지 (빈번 시 동시성 재검토 신호). §1.6 ErrorCode 표 + §7.1 에러 표에 신규 코드 명시. **(3) `DELETE /push/subscribe/{subscriptionId}` strict ULID 검증**: 기존엔 `IdPrefixes.strip` 로 silent strip 후 DB lookup 까지 흘려보내 형식 자체가 위반된 입력도 `404 SUBSCRIPTION_NOT_FOUND` 로 응답되어 클라이언트 진단 모호 + 무의미한 DB quota 소비. `IdPrefixes.stripAndValidateUlid` 신설 (prefix 필수 + 본문 Crockford Base32 `[0-9A-HJKMNP-TV-Z]{26}` 정합 검증), 위반 시 `400 VALIDATION_ERROR`. 회귀 가드: `PushSenderTest` (마스킹 6 case, null/blank/invalid 원본 누출 차단 어설션 포함), `PushControllerIntegrationTest.잘못된_형식_subscriptionId_해제시_400_VALIDATION_ERROR` (prefix 누락 / 잘못된 prefix / 길이 위반 / 금지문자 I·O / 소문자 7 case).** |
| **v1.1.34** | **2026-05-16** | **§4.1 `nearestSchedule` corrupted coordinate graceful 직렬화 (이상진) — `NearestScheduleDto.from(Schedule)` 가 `originLat/Lng/destinationLat/Lng` 중 하나라도 null 인 경우 기존엔 `IllegalStateException` 을 throw 해 `GlobalExceptionHandler.handleUnknown` 이 500 `INTERNAL_SERVER_ERROR` 로 떨어뜨렸음. JPA `@Column(nullable=false)` + `PlaceDto` Bean Validation (v1.1.33) 가 일반 경로의 boundary 가드라 정상 흐름에선 발생할 수 없지만, 직접 SQL / 마이그레이션 사고 / 외부 도구 INSERT 등으로 corrupted row 가 1건이라도 흘러들어오면 그 회원의 `/main` 이 영구 500 으로 죽어 첫 화면 진입 자체 불가하던 critical 결함. mapper 가 null 반환 + WARN 로그 (`scheduleUid`) 격하 → caller (`MainService.resolveNearest`) 의 `Optional.map(NearestScheduleDto::from)` 가 `Optional.empty()` 로 흡수 → 응답 `nearestSchedule=null` graceful 직렬화 (명세 §4.1 의 "일정 없음" 직렬화 경로와 정합). 운영 진단 가능성은 WARN 의 `scheduleUid` 로 보존 — 모니터링이 WARN 패턴 매칭으로 corrupted row alert 가능. 회귀 가드: `NearestScheduleDtoTest` (정상 변환 + 4 null 좌표 케이스 각각 null 반환).** |
| **v1.1.33** | **2026-05-16** | **§11.1 `Place` lat/lng 범위 가드 + ODsay/TMAP 좌표 로그 PII 마스킹 (이상진) — (1) `PlaceDto.lat` 에 `@DecimalMin(-90)`/`@DecimalMax(90)`, `PlaceDto.lng` 에 `@DecimalMin(-180)`/`@DecimalMax(180)` 추가. 기존엔 `@NotNull BigDecimal` 만 두어 잘못된 좌표가 ODsay `searchPubTransPathT` 외부 호출 단계까지 흘러가 5xx/빈 경로 응답이 우리쪽 500 으로 누출되던 결함. v1.1.19 `GeocodeRequest` 진입점 검증 패턴 정합 → `400 VALIDATION_ERROR` fail-fast. (2) `OdsayClient.searchPubTransPathT` / `TmapClient.routesPedestrian` 의 `log.debug` 좌표 출력에 `%.1f*` 마스킹 적용 (~10km 도시급 정확도). 운영 DEBUG 가 진단 목적으로 일시 켜진 환경에서 사용자 정확 위치 PII 누출 차단. 디버깅 가치는 보존 (호출 라우팅 / 권역 식별 가능). 회귀 가드: `ScheduleControllerIntegrationTest.create_whenCoordOutOfRange_returns400_VALIDATION_ERROR` (origin lat±91 / destination lng±181 4 cases, `RouteService` 미호출 검증).** |
| **v1.1.32** | **2026-05-16** | **§7.1 `endpoint` scheme 가드 (이상진) — `PushSubscribeRequest.endpoint` 에 `@Pattern("^https://\\S+$")` 추가. 배경: Web Push 표준 RFC 8030 은 push service endpoint 가 HTTPS 임을 요구하는데 기존 `@NotBlank + @Size(max=2048)` 만으로는 클라이언트가 실수/악의로 보낸 `http://` / `file://` / `data:` / `javascript:` / 공백 포함 URL 이 통과해 push provider 호출 단계에서 NPE/`MalformedURLException` 류로 500 `INTERNAL_SERVER_ERROR` 누출. fail-fast 로 `400 VALIDATION_ERROR` 변환. 정규식은 scheme 강제 + 공백 차단만 (host/path 형식은 의도적으로 느슨 — IDN/IPv6/미래 provider 호스트명 silent 차단 회피). §7.1 본문에 응답 schema field 표 (`data.subscriptionId` `sub_` + ULID26 = 30char) + 에러 표에 `400 VALIDATION_ERROR` (scheme) / `403 FORBIDDEN_RESOURCE` 명시 동반. 회귀 가드: `PushControllerIntegrationTest.endpoint_https_아니면_400_VALIDATION_ERROR` (5 invalid scheme cases).** |
| **v1.1.31** | **2026-05-16** | **v1.1.29 §9.3 "경유 노드별 알림" 롤백 (이상진) — 팀 피드백 #3 재해석 후 v1.1.29 도입한 multi-leg reminder 모델/페이로드를 전부 제거. 실제 요구는 "여러 일정이 있으면 각 일정마다 출발 전 알림" + "N일/특정 요일 반복 일정도 매 occurrence 마다 알림" 이었고, 이는 v1.1.16 분리 패턴 + `RoutineCalculator.advanceToNextOccurrence` (§9.2) 가 이미 만족. multi-leg 는 한 일정 안에서 환승마다 알람을 추가하는 모델이라 요구와 무관 — 잉여로 판단 후 제거. 변경: `ReminderLeg` / `ReminderLegPlanner` 삭제, `Schedule` 의 `reminder_legs_json` / `reminder_leg_index` 필드+메서드 (`applyReminderLegs` / `advanceToNextLeg` / `clearReminderLegs`) 삭제, `PushReminderTransactional.advanceOrTerminate` 를 ONCE 종결/occurrence advance 단일 분기로 환원, `PushReminderDispatcher.buildPayloadJson` 에서 `legIndex/legMode/legFrom/legTo/legLineName` 키 제거 + `buildBody` 의 leg 합류 제거, `DispatchContext.currentLeg` 제거, `OdsayRouteService.applyToSchedule` 에서 leg 적용 호출 제거. DB: V4 (`reminder_legs_json` / `reminder_leg_index` 컬럼) 는 forward-only 원칙상 파일은 유지하되 V5 (`V5__drop_schedule_reminder_legs.sql`) 가 즉시 drop — dev DB 가 V4 를 적용한 환경도 schema_history 손상 없이 정합. §9.3 본문은 통째로 제거.** |
| **v1.1.30** | **2026-05-15** | **§8.1/§8.2 응답 `provider` 정규화 (이상진) — 기존엔 캐시 row 의 세분 식별자 `KAKAO_LOCAL` 을 응답에 그대로 노출하던 결함. §8.1 v1.1.4 변환표상 `Place.provider` 도메인 ENUM 은 `KAKAO` 인데 §8.1/§8.2 응답에서만 `KAKAO_LOCAL` 이 노출되어 프론트 `PlaceProvider` typedef (`NAVER/KAKAO/ODSAY/MANUAL`) 와 불일치 → 클라이언트가 후보 선택 후 schedule 저장 단계에서 별도 변환을 해야 하는 부담. 변환 책임을 응답 단계로 끌어올려 (`GeocodeResponse.from` / `GeocodeService.searchCandidates`) frontend MSW 목 (`provider: 'KAKAO'`) 과도 자연 정합. DB 캐시 컬럼 / `GeocodeCacheProvider` ENUM 은 그대로 — 내부 식별자라 API 표면에 노출 안 함. 부수 fix: javadoc dead link `GeocodeSearchService` → `GeocodeService#searchCandidates` (`GeocodeCandidate`, `GeocodeSearchResponse`), 버전 drift "v1.1.28 이전" → "v1.1.29 이전" (`DispatchContext`, `PushReminderDispatcher`), §9.3 호환성 cutoff "v1.1.27 이전" → "v1.1.29 이전" (legs 컬럼 도입 시점이 v1.1.29 인데 본문이 v1.1.27 을 언급하던 drift), `GeocodeService.searchCandidates` x/y null skip 시 `log.debug` 보강. 명세 §8.1 v1.1.4 변환표 갱신 — 변환 책임 위치 명시. 회귀 가드: `GeocodeControllerIntegrationTest` 어설션 `KAKAO_LOCAL` → `KAKAO`.** |
| **v1.1.29** | **2026-05-15** | **§9.3 신규 + §11.3 `Schedule` 컬럼 2개 추가 — 경유 노드별 알림 (팀 피드백 #3 "경유 노드별 N분 전 알림"). 기존 모델 (한 일정 = 1회 알림, 권장 출발시각 N분 전) 을 환승 포함 다중 알림으로 확장. ODsay route segments → `ReminderLegPlanner` 가 각 segment 시작 시각의 N분 전을 `ReminderLeg` 배열로 계산해 `schedule.reminder_legs_json` 에 저장 (V4 migration). `schedule.reminder_leg_index` 가 현재 발송 대기 leg 추적, `reminder_at` 은 denormalize 된 "현재 leg fireAt" — 스케줄러 폴링 쿼리 변경 X (`reminder_at <= now` 그대로). dispatcher advance: leg index 우선 advance (다음 leg fireAt 으로 `reminder_at` 갱신), 마지막 leg 도달 시 기존 occurrence 종결/advance 흐름. payload 에 현재 leg 의 `legIndex/legMode/legFrom/legTo/legLineName` 키 추가 — 환승 boarding 알림은 본문에 "○○에서 △△ 승차" 합류. v1.1.27 이전 schedule / ODsay 실패 fallback (legs JSON null) 은 기존 단발 알림 흐름과 동치 (회귀 안전망). (이상진).** |
| **v1.1.27** | **2026-05-15** | **§8.2 신규 — `POST /geocode/search` 다중 후보 검색 엔드포인트. 일정 등록 입력 자동완성에서 §8.1 `POST /geocode` 가 첫 결과 1건만 노출하던 결함 (팀 피드백 #6 — "지오코딩 한 노드만 뜸") 보강. 응답 `candidates[]` 배열 (1~10건, `size` 파라미터 default 10 max 10 — 클라이언트가 생략하면 항상 상한까지 후보 노출). §8.1 단일-resolution 시맨틱 영향 X (좌표 확정 흐름 그대로). 캐시 미사용 — autocomplete 키스트로크 query 는 hit ratio 가 낮아 §8.1 의 `geocode_cache` 와 분리 (TTL/quota 그대로 §8.1 만 보호). Kakao Local 응답 매핑은 §8.1 v1.1.4 변환표 재사용 — `documents[].place_name/x/y/...` 를 `candidates[i]` 로 1:1 매핑. (이상진).** |
| **v1.1.26** | **2026-05-13** | **§5.2 cursor 페이지네이션 keyset 합성키 명시 (이슈 #15 fix). `ScheduleRepository.findPage` keyset 술어가 `s.id > :cursorId` 단독이라 정렬 `(arrival_time ASC, id ASC)` 과 어긋날 때 (arrival 이른 일정이 더 큰 id 보유) 다음 페이지에서 영구 누락. 술어를 `(arrivalTime > :cArr OR (arrivalTime = :cArr AND id > :cId))` 합성키로 교체. cursor 인코딩도 `id:N` (v1) → `v2:<ISO_UTC>|<id>` 로 변경, v1 cursor 는 `VALIDATION_ERROR` (400) 로 거부 — 명세 §1.5 opaque 정책상 클라이언트는 1페이지부터 재요청. 회귀 가드 `ScheduleControllerIntegrationTest.list_keysetHandlesAsymmetricArrivalAndId_doesNotDropSchedules` + `list_whenInvalidCursorFormat_returns400`.** |
| **v1.1.25** | **2026-05-12** | **§9.2 WEEKLY routine 요일 비교 `Asia/Seoul` (KST) 기준 명시 (이슈 #36 fix). `RoutineCalculator.nextWeeklyOccurrence` 가 `cand.getDayOfWeek()` 를 사용하던 결함 — `OffsetDateTime` 영속화 후 displayed offset 이 UTC 로 reconstruct 되어 KST 가 아닌 UTC 기준 요일 평가 → KST 새벽 시간대 일정의 advance 가 `daysOfWeek` 에 없는 요일 (예: MON/WED/FRI 일정이 THU 로) 반환. `cand.atZoneSameInstant(KST).getDayOfWeek()` 로 정정. 회귀 가드 추가 (`PushReminderDispatcherIntegrationTest.S5B` 명시 KST 새벽 시각). 데모 시연 (정오) 직접 영향 ⚪ 낮음 — KST/UTC 동일 요일 시간대. DAILY/CUSTOM (`plusDays(N)` instant 기준) / `reminderAt` 계산 (instant 기준) 영향 0.** |

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
| **404** | **`RESOURCE_NOT_FOUND`** | **매핑되지 않은 URL 경로 호출. `GlobalExceptionHandler` 가 `NoResourceFoundException` / `NoHandlerFoundException` 을 404 로 변환 — v1.1.23 추가** |
| 409 | `LOGIN_ID_DUPLICATED` | 로그인 ID 중복 |
| **503** | **`PUSH_SUBSCRIBE_CONFLICT`** | **푸시 구독 UPSERT race 가 1회 retry 후에도 해소 안 됨 (일시적 contention) — v1.1.35 추가** |
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
| 7.2 | POST | `/geocode/search` | ✓ | geocode |

> △: 게스트 허용. 인증 시 추가 정보 반환.

### 1.9 CORS 정책

브라우저 cross-origin 호출 (예: 프론트 `http://localhost:3000` → 백엔드 `http://localhost:8080/api/v1`) 을 위한 화이트리스트 정책.

- **허용 origin**: `application.yml` 의 `cors.allowed-origins` 키 (환경변수 `CORS_ALLOWED_ORIGINS` 콤마 구분 override). 로컬 dev default `http://localhost:3000`. 운영 도메인은 별 task (외부 노출) 진입 시 추가.
- **허용 method**: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`
- **허용 header**: `*` (`Content-Type`, `Authorization` 자동 부착 헤더 커버)
- **노출 header**: 없음 (응답 본문 `{data}` / `{error}` 만 사용)
- **credentials**: ❌ — JWT 가 `Authorization` 헤더 stateless 인증, cookie 미사용
- **preflight 캐시**: 1800초 (30분)

차단 origin 의 preflight (`OPTIONS` + `Origin` + `Access-Control-Request-Method`) 는 Spring Security 가 `403 Invalid CORS request` 응답. 비-허용 origin 의 본 요청도 `Access-Control-Allow-Origin` 헤더 부재로 브라우저 차단.

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

#### DB 매핑 (v1.1.22)

- **Hard delete**: `DELETE FROM member WHERE id = ?` — row 자체 삭제 (`memberRepository.delete()`).
- **FK ON DELETE CASCADE** 가 다음 row 일괄 삭제:
  - `refresh_token` → 삭제 (활성 토큰 무효화 보장)
  - `schedule` → 삭제 → `push_log.schedule_id SET NULL` (다른 회원에 영향 없도록 SET NULL)
  - `push_subscription` → 삭제 → `push_log` CASCADE 삭제 (탈퇴 회원 본인의 발송 이력 동반 삭제)

> **회원 데이터 완전 삭제 정책**: 회원 탈퇴 cascade 한해 `push_subscription` row + `push_log` 까지 함께 삭제됨. 다른 흐름 (사용자 unsubscribe §7.2 / 410 EXPIRED 자동 revoke) 은 `revoked_at` soft revoke 유지 — `push_log` 이력 보존.

#### 비고 — 멱등성 (v1.1.6 / v1.1.7 / v1.1.22)

본 API 는 인증 토큰의 회원이 이미 탈퇴 처리된 경우 (member row 자체가 없음) Service 의 `findByMemberUid` 에서 회원 조회 실패 → **401 `UNAUTHORIZED`** 응답. RFC 9110 의 일반 DELETE 멱등성과 달리, 본 API 는 인증 정책 우선 설계의 결과로 두 번째 DELETE 요청은 401 로 응답된다 (JWT 는 유효하지만 가리키는 회원이 무효).

#### 비고 — 동일 loginId 재가입 (v1.1.22)

회원 탈퇴 후 동일 `loginId` 로 재가입 가능 (`201 Created`). 이전 soft delete 모델에서는 `login_id` UNIQUE 제약 + `@SQLRestriction("deleted_at IS NULL")` 비대칭으로 `existsByLoginId=false` → `save()` 시 `DataIntegrityViolationException` → 409 `LOGIN_ID_DUPLICATED` 던지던 버그가 hard delete 전환으로 해소됨.

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
- **v1.1.34 — corrupted coordinate graceful skip**: DB row 의 `origin_lat/lng` 또는 `destination_lat/lng` 중 하나라도 `NULL` 이면 (직접 SQL / 마이그레이션 사고 등으로 JPA `nullable=false` 가드 우회 시) `nearestSchedule` 을 `null` 로 직렬화하고 WARN 로그 (`scheduleUid` 포함) 만 남긴다. `/main` 이 500 으로 죽지 않게 하기 위한 안전망 — 정상 입력 경로에선 `PlaceDto` Bean Validation (v1.1.33) 이 boundary 가드라 발생하지 않는다.

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
| `userDepartureTime` | string (ISO) | **N (v1.1.40)** | 사용자가 입력한 출발 시각. **v1.1.40 — optional**. 미입력 시 BE 가 ODsay 응답의 `recommendedDepartureTime` 으로 자동 채움. 응답 `departureAdviceReliable` 메타로 자동 채움 여부 노출. FE 폼에서 `arrivalTime - 30분` 등 prefill 권고 — 사용자가 prefill 값을 명시 변경하면 `departureAdvice` 비교 신호 (EARLIER/ON_TIME/LATER) 정상 산출. |
| `arrivalTime` | string (ISO) | Y | 도착 희망 시각. **v1.1.40 — 누락 시 `400 VALIDATION_ERROR` (BE `@NotNull` 강제). FE client-side 검증 필수** (슬랙 #1 — FE 가 invalid/default date 전송하는 패턴 차단). |
| `reminderOffsetMinutes` | integer | N | 알림 시각 = 권장 출발시각 - N분 (기본 5, T6 도입 후 회원별 default). **v1.1.40 — `@Min(0) @Max(1440)` 범위 검증**. 0 = 출발 정각 알림 (긴급 알림 의도), 1440 = 1일 전 알림 (일반 calendar 앱 표준). 음수는 reminder_at 이 출발시각 이후가 되어 UX 위반. 본 알림은 **출발 알림** (departure reminder) 이며 일정 사전 알림 (event reminder) 과 도메인 구분 — Google Calendar 의 일정 자체 알림과 다름. |
| `routineRule` | `RoutineRule` | N | 루틴 설정 (없으면 단발성). **v1.1.40 — `startDate` / `endDate` 추가** (§11.2). |

`Place`, `RoutineRule` 스키마는 §11. 데이터 타입 부록 참조.

#### 응답 메타 필드 (v1.1.40)

| 필드 | 타입 | 설명 |
|---|---|---|
| `departureAdviceReliable` | boolean | **T5-Q4** — `departureAdvice` 비교 신호 신뢰성. `false` = BE 가 `userDepartureTime` 자동 채움 (user==recommended 라 ON_TIME 만 산출, 비교 의미 X). FE 는 false 시 advice 회색 처리 권고. `true` = 사용자 명시 입력. |
| `reminderClamped` | boolean | **R4** — `reminderAt < NOW()+60s` 인 경우 `NOW()+60s` 로 clamp 했음을 표시. dispatcher 5분 폴링 윈도우 silent 누락 방지. FE toast 표시 권고. 일반 조회에선 false. |
| `reminderSkipped` | boolean | **R4-Q2** — clamp floor 가 ceiling (`arrivalTime - 1min`) 보다 미래라 의미 있는 알림 불가능. `reminderAt = null` skip + 응답 메타로 사용자 안내. FE toast "일정 시각이 임박해 알림 미설정" 권고. |

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
        "origin": { "name": "우이동", "lat": 37.66, "lng": 127.01 },
        "destination": { "name": "국민대학교", "lat": 37.61, "lng": 126.99 },
        "userDepartureTime": "2026-04-21T08:30:00+09:00",
        "arrivalTime": "2026-04-21T09:00:00+09:00",
        "estimatedDurationMinutes": 35,
        "recommendedDepartureTime": "2026-04-21T08:25:00+09:00",
        "departureAdvice": "LATER",
        "reminderOffsetMinutes": 5,
        "reminderAt": "2026-04-21T08:20:00+09:00",
        "routineRule": { "type": "WEEKLY", "daysOfWeek": ["MON","TUE","WED","THU","FRI"], "startDate": "2026-04-21", "endDate": null },
        "routeStatus": "CALCULATED",
        "routeCalculatedAt": "2026-04-20T22:14:00+09:00",
        "createdAt": "2026-04-20T22:13:55+09:00",
        "departureAdviceReliable": true
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

> **v1.1.40 — 목록 응답을 단일 응답 (§5.1/§5.3) 과 동일 필드로 통일**. v1.1.39 이전엔 페이로드 절감을 위해 `scheduleId/title/arrivalTime/recommendedDepartureTime/origin/destination/routeStatus` 7필드만 포함되어, 프론트가 캘린더에서 반복 일정의 `routineRule.daysOfWeek` 를 받지 못해 단발 처리해 `arrivalTime` 날짜 1건만 표시하던 결함 (슬랙 #2). 풀필드 통일로 프론트가 추가 분기 없이 그대로 처리. 향후 목록 경량화 필요 시 별 PR 로 재분리 가능 (현 시점 코드 trail 보존).

#### DB 매핑
```sql
SELECT * FROM schedule
WHERE member_id = ?
  AND deleted_at IS NULL
  [AND arrival_time BETWEEN ? AND ?]
  -- cursor 가 있으면 합성키 keyset (이슈 #15 fix, v1.1.26)
  [AND (arrival_time > ? OR (arrival_time = ? AND id > ?))]
ORDER BY arrival_time ASC, id ASC
LIMIT ?
```

> cursor 는 opaque token — 서버 내부 표현은 `Base64URL("v2:" + <arrival_time UTC ISO> + "|" + <id>)`. v1 (`id:N`) cursor 는 `VALIDATION_ERROR` (400) 로 거부. 정렬 tie-break (`id ASC`) 와 동일한 합성키 술어로 누락 방지.

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
| `path` | (transit) `loadLane.result.lane[i].section[].graphPos[]` 평탄화 (도로 곡선)<br>(WALK) **TMAP `routes/pedestrian` 호출 (인도 곡선, v1.1.21)** → 실패/미설정 시 v1.1.9 합성 직선 fallback | `[lng, lat]` 배열. loadLane 실패/누락 시 `passStopList` 직선으로 graceful fallback (v1.1.10) |

🆕 **v1.1.21 — WALK 구간 `path` 출처 = TMAP 보행자 경로 (인도 곡선)**:

```
POST https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1&format=json
Header: appKey: {TMAP_APP_KEY}
Body  : { "startX", "startY", "endX", "endY", "reqCoordType":"WGS84GEO", "resCoordType":"WGS84GEO", "startName", "endName" }
```

- 응답: GeoJSON `FeatureCollection` — `features[].geometry.type=="LineString"` 의 `coordinates[]` (이미 `[lng, lat]` 순서) 를 모든 LineString feature 에 대해 평탄화하여 한 WALK segment 의 path 로 합침.
- 호출 빈도: WALK subPath 1개당 1회. 한 cache miss 사이클의 외부 API 호출 = ODsay × 2 (`searchPubTransPathT` + `loadLane`) + TMAP × N. N 은 환승 횟수에 비례: **환승 0회 → N=2** (출발 WALK + 도착 WALK) / **환승 1회 → N=3** (+ 환승 walk 1개) / **환승 2회 → N=4**.
- 응답 지연 worst case (`tmap.timeout-seconds: 5` + 직렬 호출): 환승 0회 ≈ 20초 / 환승 1회 ≈ 25초 / 환승 2회 ≈ 30초. graceful fallback 으로 사용자에겐 직선 그려짐 정도지만 `GET /schedules/{id}/route` 동기 SLA 영향. P1 백로그: 첫 TMAP timeout 시 나머지 WALK 즉시 fallback (fail-fast) 또는 WALK 호출 비동기 fan-out (Java 21 Virtual Threads + `CompletableFuture.allOf`).
- WALK 의 시작/끝 좌표는 v1.1.9 합성 알고리즘과 동일 (origin / 다음 transit `startX/Y` / 이전 transit `endX/Y` / destination). TMAP 응답 양 끝이 정확히 그 좌표와 일치하지 않을 수 있어 **양 끝에 시작/끝 좌표를 강제 prepend/append** — 정류장 좌표와 시각상 정확히 만나도록 보정.
- **graceful fallback** — 다음 케이스에서 v1.1.9 합성 직선으로 fallback:
  - `TMAP_APP_KEY` 미설정 (호출 자체 skip — 401 비용 + 노이즈 회피)
  - TMAP 401/403/timeout/5xx
  - 응답 본문 빈 / GeoJSON 형식 위반 / `features[]` 배열 없음 / LineString feature 0개
- TMAP 보행자 경로안내 신청: SK Open API 콘솔 (`https://openapi.sk.com`) → 상품 마켓 → "보행자 경로안내 V1" 사용 신청 → 발급된 AppKey 를 `TMAP_APP_KEY` 환경변수로 주입.

🆕 **v1.1.9 — WALK 구간 좌표 결정 알고리즘** (ODsay WALK subPath 는 `startX/Y`/`endX/Y` 키가 없음 — TMAP 호출 시작/끝 좌표 결정에 그대로 사용):

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
  **v1.1.36 — 401/403 은 stale cache fallback 대상 X**: ODsay 호출이 401/403 으로 실패한 경우 캐시가 살아있어도 stale 응답으로 응답하지 않는다. 운영자 alert 가 다른 회원/요청의 stale 응답에 가려져 영구 잠복하는 결함을 차단하기 위함. 5xx / timeout / network 등 transient 외부 장애는 §6.1 비고 그대로 stale fallback 유지 (사용자 경험 보호).
- **v1.1.36 — `RouteResponse.calculatedAt` non-null invariant**: 응답이 만들어지는 모든 경로(fresh / cache hit / stale)에서 route 데이터가 존재한다는 건 ODsay 호출이 적어도 한 번 성공했다는 뜻이므로 `calculatedAt` 은 본질적으로 non-null. compact constructor 가 boundary 검증. `route_summary_json` 만 채워지고 `route_calculated_at` 만 NULL 인 corruption row (직접 SQL / 부분 마이그레이션) 는 `tryMapCache` 단계에서 skip — caller 는 fresh 호출 또는 502 `EXTERNAL_ROUTE_API_FAILED` 분기로 자연 fallback.

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
| `endpoint` | string | Y | 브라우저 푸시 서버 URL. **max 2048 char** (v1.1.15 — FCM ~200 / Apple Web Push ~280 / Mozilla autopush ~400 / Microsoft WNS ~2048 모두 안전 마진). RFC 3986 ASCII. **v1.1.32 — scheme 가드: `^https://\S+$` 정규식 강제** (`http://`, `file://`, `data:`, `javascript:` 등 비-HTTPS 또는 공백 포함 입력은 `400 VALIDATION_ERROR`). Web Push 표준 RFC 8030 정합 + push provider 호출 단계 unchecked 예외 방지. host/path 형식은 의도적으로 느슨 — IDN / IPv6 / 미래 provider 호스트명을 silent 차단하지 않기 위함. |
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

| 필드 | 타입 | 설명 |
|---|---|---|
| `data.subscriptionId` | string | 외부 노출 ID. §1.7 규약상 `sub_` prefix + ULID 26자 (총 30자). 이후 §7.2 unsubscribe path 파라미터로 사용. |

#### 비고
- 동일 `endpoint`로 재구독 시 기존 row의 `revoked_at`을 `NULL`로 갱신 (재활성화) — 이때도 `subscriptionId` 는 기존 ULID 그대로 (회전 X)
- `endpoint`는 unique key
- `endpoint` 컬럼은 `VARCHAR(2048) CHARACTER SET ascii` (v1.1.15) — InnoDB UNIQUE INDEX max key length (utf8mb4 환경 3072 byte) 제약 + URL 표준 ASCII 정합. 비ASCII endpoint 는 spec 위반.

#### 에러
- `400 VALIDATION_ERROR` — 필드 누락 / `endpoint` 길이 초과 / `endpoint` scheme 위반 (v1.1.32)
- `403 FORBIDDEN_RESOURCE` — 다른 회원이 이미 동일 `endpoint` 로 구독한 경우 (§7.1 v1.1.13)
- `503 PUSH_SUBSCRIBE_CONFLICT` — UPSERT race 가 1회 retry 후에도 해소 안 됨 (v1.1.35). 일시적 contention, 클라이언트는 잠시 후 재시도 권장

#### DB 매핑
- `push_subscription` UPSERT (endpoint 기준)
- `subscription_uid` ULID 생성

---

### 7.2 Web Push 구독 해제

`DELETE /push/subscribe/{subscriptionId}` — 인증 필요

#### Path
- `subscriptionId` — **v1.1.35** `sub_` prefix + Crockford Base32 26자 ULID 본문 strict 검증. 위반 시 `400 VALIDATION_ERROR` (DB lookup 까지 흘려보내지 않음)

요청 바디 없음.

#### Response — `204 No Content`

#### 에러
- `400 VALIDATION_ERROR` — `subscriptionId` 형식 위반 (prefix 누락 / 본문 길이 ≠ 26 / 금지문자 `I/L/O/U` / 소문자) (v1.1.35)
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
    "provider": "KAKAO"
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
| `provider` | (고정값) | **v1.1.30** `"KAKAO"` (응답 단계에서 도메인 ENUM 값으로 변환 완료 — `Place.provider` 와 동일 ENUM 공간 `NAVER/KAKAO/ODSAY/MANUAL`. 캐시 row 의 세분 식별자 `KAKAO_LOCAL` 은 backend 내부에서만 사용, API 표면 노출 X) |

**예외 처리**:
- `documents`가 빈 배열 → `404 GEOCODE_NO_MATCH`
- Kakao 401/403 → `503 EXTERNAL_AUTH_MISCONFIGURED`
- Kakao 5xx → `502 EXTERNAL_ROUTE_API_FAILED`
- Kakao 타임아웃 → `504 EXTERNAL_TIMEOUT`

#### 🆕 v1.1.4 — Provider 값 변환 규칙 (v1.1.30 변환 책임 명시)

DB ENUM 두 개가 다르게 정의돼 있어 **저장/응답 위치별로 변환** 필수:

| 위치 | ENUM 값 공간 | 매핑 |
|---|---|---|
| `geocode_cache.provider` (DB 내부) | `'NAVER', 'KAKAO_LOCAL'` | `"KAKAO_LOCAL"` 그대로 저장 |
| **§8.1 / §8.2 응답 `provider`** | `'NAVER', 'KAKAO', 'ODSAY', 'MANUAL'` | **v1.1.30 — 응답 단계에서 `"KAKAO"` 로 변환 완료** (`GeocodeResponse.from` / `GeocodeService.searchCandidates`) |
| `schedule.origin_provider`<br>`schedule.destination_provider`<br>`Place.provider` (§11.1) | `'NAVER', 'KAKAO', 'ODSAY', 'MANUAL'` | `"KAKAO"` 그대로 저장 (응답 단계 변환 완료라 schedule 저장 시 추가 변환 불필요) |

**변환 함수** (구현 참고):
```java
public static String toPlaceProvider(String geocodeProvider) {
    return "KAKAO_LOCAL".equals(geocodeProvider) ? "KAKAO" : geocodeProvider;
}
```

**이유**: `Place.provider`는 도메인 추상화 ENUM (`NAVER/KAKAO/ODSAY/MANUAL`). `KAKAO_LOCAL`은 Kakao 내부 API 세분 구분이라 도메인 모델 / API 표면 어디에도 노출 불필요. 반면 `geocode_cache`는 캐시 키 구분용이라 세분 구분 필요. **v1.1.30 변경 전엔 §8.1/§8.2 응답에서만 `KAKAO_LOCAL` 이 새어 나가 프론트 `PlaceProvider` typedef 와 불일치하던 결함이 있었음 — 응답 단계에서 변환을 끌어올려 frontend 가 추가 변환 없이 후보를 그대로 schedule payload 로 흘려보낼 수 있게 정합.**

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
- `provider` 컬럼은 `'KAKAO_LOCAL'` 저장 (DB 내부 식별자)
- §8.1/§8.2 응답 단계에서 `'KAKAO'` 로 변환해 노출 (v1.1.30) — 프론트가 추가 변환 없이 schedule 저장 payload 로 흘려보낼 수 있음

#### 🆕 v1.1.18 — TTL eviction (`GeocodeCacheCleanupScheduler`)

- 매일 **04:00 KST** (`@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")`) 에 `cached_at < NOW() - INTERVAL 30 DAY` row 삭제.
- read filter (TTL 30일) 와 같은 cutoff — 만료된 row 만 제거하고 hit 가능 row 는 보존.
- 운영 1년+ 누적 시 row 비대화 + UNIQUE INDEX `(query_hash, provider)` 비대화 차단 + read filter 의 `cached_at >` 조건 인덱스 효율 보존.
- `geocode.cleanup.enabled` (default `true`), `geocode.cleanup.cron` 환경변수 외부화.

```sql
DELETE FROM geocode_cache
WHERE cached_at < NOW() - INTERVAL 30 DAY
```

### 8.2 주소/장소 다중 후보 검색 (v1.1.27)

`POST /geocode/search` — 인증 필요

일정 등록 화면의 입력 자동완성용 — 키워드 하나로 1~10건의 후보 (Kakao Local `documents[]` 상위 N) 를 반환한다.
§8.1 `POST /geocode` 가 첫 결과만 노출해 "지오코딩 한 노드만 뜸" (팀 피드백 #6) 결함이 있어 분리 추가. §8.1 단일-resolution 시맨틱은 그대로 유지 — 사용자가 후보 중 하나를 골라 좌표 확정한 뒤 schedule 저장 흐름은 변동 없음.

#### Request Body

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `query` | string | Y | 주소 또는 장소명 (§8.1 와 동일 — `trim` 만 적용 후 Kakao 호출) |
| `size` | int | N | 1~10. default 10 (생략 시 상한까지 노출). 11 이상 / 0 이하 / non-numeric → `400 VALIDATION_ERROR` |

```json
{ "query": "강남역", "size": 10 }
```

#### Response — `200 OK`

```json
{
  "data": {
    "candidates": [
      {
        "name": "강남역 2호선",
        "address": "서울 강남구 역삼동 858",
        "lat": 37.4979,
        "lng": 127.0276,
        "placeId": "21160606",
        "provider": "KAKAO"
      },
      {
        "name": "강남역 신분당선",
        "address": "서울 강남구 강남대로 396",
        "lat": 37.4980,
        "lng": 127.0274,
        "placeId": "1234567",
        "provider": "KAKAO"
      }
    ]
  }
}
```

- `candidates[]` 는 Kakao 응답 `documents` 를 §8.1 v1.1.4 변환표 그대로 매핑한 결과. `provider` 는 v1.1.30 응답 단계 변환 적용 → `"KAKAO"`.
- **v1.1.37 — `size` cap 의미는 "valid 후보 N건"**: 응답 `candidates[]` 의 길이가 `size` (혹은 documents 가 부족하면 그 수) 가 되도록 documents 를 순회하며 valid 만 누적. invalid (좌표 누락 / non-numeric / `NaN`/`Infinity`) row 는 skip 하되 size 카운트에 포함되지 않음. (v1.1.27 ~ v1.1.36: documents[0..size) 만 훑어 앞쪽 invalid 시 결과가 size 보다 적게 반환되던 결함.)
- 빈 검색 결과 → `404 GEOCODE_NO_MATCH` (§8.1 와 동일 ErrorCode 재사용 — 신규 ErrorCode 추가 X).
- `lat`/`lng` 가 누락된 document 는 skip — 한 row 라도 valid 면 200, 전부 invalid 면 `502 EXTERNAL_ROUTE_API_FAILED`.

#### 캐시 정책

- `geocode_cache` 미사용 — autocomplete 키스트로크 query (예: `"강"`, `"강남"`, `"강남역"`) 는 hit ratio 가 낮아 캐시 ROI 가 부정적. §8.1 의 TTL/quota 보호는 그대로.
- 향후 동일 query 의 다중 후보 캐시가 필요해지면 v1.1.x 에서 별도 컬럼 (`candidates_json`) 도입 검토.

#### 에러
- `400 VALIDATION_ERROR` — `query` 누락 / 빈 문자열 / `size` 범위 위반
- `404 GEOCODE_NO_MATCH` — Kakao `documents` 빈 배열
- `502 EXTERNAL_ROUTE_API_FAILED` — Kakao 5xx / 응답 형식 위반
- `503 EXTERNAL_AUTH_MISCONFIGURED` — Kakao API 키 미설정/만료
- `504 EXTERNAL_TIMEOUT` — Kakao 타임아웃

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
| `WEEKLY` | `arrival_time` 이후, `routine_days_of_week` 중 가장 가까운 요일 (`Asia/Seoul` 기준 — v1.1.25) |
| `CUSTOM` | `arrival_time + routine_interval_days` |

갱신 후:
- **WEEKLY 요일 비교 기준** (v1.1.25, 이슈 #36): `daysOfWeek` 와 다음 occurrence 후보의 요일 비교는 `Asia/Seoul` (KST, +09:00) 기준. `OffsetDateTime` 영속화 후 displayed offset 이 UTC 로 reconstruct 되어도 KST zone 으로 변환 후 평가 (`cand.atZoneSameInstant(KST).getDayOfWeek()`).
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
  name: string;          // 장소명 (1~100자)
  lat: number;           // 위도, -90 ≤ lat ≤ 90 (v1.1.33)
  lng: number;           // 경도, -180 ≤ lng ≤ 180 (v1.1.33)
  address?: string;      // 도로명 주소
  placeId?: string;      // 외부 장소 ID
  provider?: "NAVER" | "KAKAO" | "ODSAY" | "MANUAL";
}
```

**검증 (v1.1.33)** — `lat`/`lng` 가 WGS84 범위 밖이면 `400 VALIDATION_ERROR` 로 fail-fast.
배경: 기존엔 `@NotNull` 만 두어 클라이언트가 잘못된 좌표를 보내면 ODsay `searchPubTransPathT` 외부 호출 단계까지 흘러가 5xx 또는 빈 경로 응답이 우리쪽 500 으로 누출되던 결함. v1.1.19 `GeocodeRequest` NaN/Infinity·XOR 검증과 동일한 진입점 검증 패턴.

### 11.2 `RoutineRule`

```typescript
{
  type: "ONCE" | "DAILY" | "WEEKLY" | "CUSTOM";
  daysOfWeek?: ("MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN")[];
  // WEEKLY일 때 사용
  intervalDays?: number;
  // CUSTOM일 때 사용 (N일 간격)
  startDate?: string;   // ISO date (v1.1.40) — 반복 시작 날짜. null = 등록 시점부터. 과거 허용.
  endDate?: string;     // ISO date (v1.1.40) — 반복 종료 날짜. null = 무한반복 (default).
}
```

**검증 (v1.1.40)**:
- `startDate` 과거 허용 — 회상/소급 일정. dispatcher 가 `reminder_at > NOW()` 만 발송이라 과거 occurrence silent skip.
- `endDate=null` = 무한반복 (슬랙 "끝나는 날짜 설정 안함 = 무한반복" 정합). 명시적 boolean flag 없음 — `endDate` 단일 컬럼.
- `RoutineCalculator.calculateNextOccurrence` 가 next occurrence 의 KST 기준 LocalDate 가 `endDate` 초과 시 null 반환 → §9.2 advance 종료 → `reminder_at NULL` dormant. 일정 row 자동 삭제 X (슬랙 #2 "자동 삭제 X" 정합).
- backward compat — 기존 schedule (start/end NULL) = 기존 동작 (무한반복) 보존.

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
  // v1.1.40 응답 메타 (§5.1 응답 메타 필드 표 참조)
  departureAdviceReliable: boolean;   // T5-Q4 — userDepartureTime 자동 채움 시 false
  reminderClamped?: boolean;          // R4 — 등록/수정 시점 only, 일반 조회에선 미포함 (또는 false)
  reminderSkipped?: boolean;          // R4-Q2 — 등록/수정 시점 only
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
