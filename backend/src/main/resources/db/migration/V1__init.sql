-- =====================================================================
-- DB 스키마 v1.1-MVP — Spring 단일 백엔드 (MySQL 8.x / utf8mb4)
-- 작성: 황찬우, 2026-04-23
-- =====================================================================
--
-- 🗑 삭제된 테이블 (10개)
--   - member_calendar_link        외부 캘린더 OAuth → 자체 캘린더로 전환
--   - member_preferences          선호도 → MVP에선 사용처 없음 (랭킹 알고리즘 부재)
--   - favorite_route              즐겨찾기 → P1, MVP 범위 밖
--   - route_set                   경로 후보 묶음 → ODsay 응답을 schedule.route_summary_json에 저장
--   - route_candidate             경로 후보 → 단일 경로 표시 (후보 리랭킹 없음)
--   - route_segment               경로 구간 → JSON 필드로 흡수
--   - subway_congestion_lookup    지하철 혼잡도 → MVP에서 혼잡도 계산 안 함
--   - bus_congestion_lookup       버스 혼잡도 → 동일 사유
--   - ranking_weight              랭킹 가중치 학습 → 랭킹 알고리즘 부재로 불필요
--   - feedback                    피드백 → P1, MVP 범위 밖
--
-- 🔧 단순화된 테이블 (1개)
--   - member: calendar_linked 컬럼 삭제
--
-- ♻️ v1.0-MVP에서 다시 복구된 테이블 (3개)
--   - push_subscription           Web Push 구독 (알림 기능 유지 결정)
--   - push_log                    푸시 발송 이력
--   - geocode_cache               지오코딩 캐시 (ODsay 좌표 입력 가정)
--
-- ✨ 남은 테이블 (6개)
--   1. member             회원
--   2. refresh_token      JWT 인증 (S1 결정에 따라 변경 가능)
--   3. schedule           일정/루틴 + ODsay 응답 캐시
--   4. push_subscription  Web Push 구독
--   5. push_log           푸시 발송 이력
--   6. geocode_cache      지오코딩 결과 캐시
--
-- =====================================================================
-- MVP 사용자 흐름
--
--   1. 회원가입/로그인              → member, refresh_token
--   2. 푸시 알림 구독                → push_subscription
--   3. 일정 등록                    → schedule (출/도착지, 출발시각, 도착희망시각)
--      └ 출/도착지 텍스트 → 좌표 변환 → geocode_cache (캐시 미스 시 카카오/네이버 호출)
--   4. 서버가 ODsay 호출            → schedule.route_summary_json에 응답 저장
--   5. 권장 출발시각 안내            → schedule.recommended_departure_time 계산 후 표시
--   6. 스케줄러가 reminder_at 시점에 푸시 발송 → push_log에 기록
--   7. 일정 조회/수정/삭제           → schedule
--
-- =====================================================================


-- =====================================================================
-- 1. 회원 / 인증
-- =====================================================================

CREATE TABLE member (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  member_uid    CHAR(26)     NOT NULL                COMMENT 'ULID, 외부 노출용',
  login_id      VARCHAR(50)  NOT NULL                COMMENT '로그인 아이디',
  password_hash VARCHAR(255) NOT NULL                COMMENT 'bcrypt 등',
  nickname      VARCHAR(50)  NOT NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at    DATETIME(3)  NULL                    COMMENT '탈퇴 시각 (소프트 삭제)',
  PRIMARY KEY (id),
  UNIQUE KEY uq_member_uid   (member_uid),
  UNIQUE KEY uq_member_login (login_id)
) ENGINE=InnoDB COMMENT='회원';


-- Refresh Token (JWT 사용 가정.)
CREATE TABLE refresh_token (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  member_id  BIGINT      NOT NULL,
  token_hash CHAR(64)    NOT NULL                  COMMENT 'SHA-256 해시',
  expires_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_refresh_hash   (token_hash),
  KEY        ix_refresh_member (member_id, revoked_at),
  CONSTRAINT fk_refresh_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='리프레시 토큰';


-- =====================================================================
-- 2. 일정 / 루틴 + ODsay 응답 캐시
-- =====================================================================

CREATE TABLE schedule (
  id                          BIGINT        NOT NULL AUTO_INCREMENT,
  schedule_uid                CHAR(26)      NOT NULL,
  member_id                   BIGINT        NOT NULL,
  title                       VARCHAR(100)  NOT NULL,

  -- 출발지
  origin_name                 VARCHAR(100)  NOT NULL,
  origin_lat                  DECIMAL(10,7) NOT NULL,
  origin_lng                  DECIMAL(10,7) NOT NULL,
  origin_address              VARCHAR(255)  NULL,
  origin_place_id             VARCHAR(100)  NULL,
  origin_provider             ENUM('NAVER','KAKAO','ODSAY','MANUAL') NULL,

  -- 도착지
  destination_name            VARCHAR(100)  NOT NULL,
  destination_lat             DECIMAL(10,7) NOT NULL,
  destination_lng             DECIMAL(10,7) NOT NULL,
  destination_address         VARCHAR(255)  NULL,
  destination_place_id        VARCHAR(100)  NULL,
  destination_provider        ENUM('NAVER','KAKAO','ODSAY','MANUAL') NULL,

  -- 시간 (사용자 입력)
  user_departure_time         DATETIME(3)   NOT NULL  COMMENT '사용자가 입력한 출발 시각',
  arrival_time                DATETIME(3)   NOT NULL  COMMENT '도착 희망 시각',

  -- ODsay 호출 결과 (서버가 채움)
  estimated_duration_minutes  INT           NULL      COMMENT 'ODsay 응답 소요시간 (분)',
  recommended_departure_time  DATETIME(3)   NULL      COMMENT '서버가 권장하는 출발시각',
  departure_advice            ENUM('EARLIER','ON_TIME','LATER') NULL COMMENT '출발시각 조정 안내',
  route_summary_json          JSON          NULL      COMMENT 'ODsay 응답 통째 저장',
  route_calculated_at         DATETIME(3)   NULL      COMMENT 'ODsay 호출 시각',

  -- 알림 (푸시 복구로 재추가)
  reminder_offset_minutes     INT           NOT NULL DEFAULT 5  COMMENT '권장 출발시각 몇 분 전 알림',
  reminder_at                 DATETIME(3)   NULL                COMMENT 'recommended_departure_time - offset',

  -- 루틴 (NULL이면 단발성)
  routine_type                ENUM('ONCE','DAILY','WEEKLY','CUSTOM') NULL,
  routine_days_of_week        VARCHAR(20)   NULL      COMMENT 'MON,TUE,...',
  routine_interval_days       INT           NULL,

  created_at                  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at                  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at                  DATETIME(3)   NULL,

  PRIMARY KEY (id),
  UNIQUE KEY uq_schedule_uid    (schedule_uid),
  KEY        ix_sch_member_arrival (member_id, arrival_time),
  KEY        ix_sch_reminder    (reminder_at, deleted_at) COMMENT '스케줄러용',
  CONSTRAINT fk_sch_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='일정/루틴 + ODsay 응답 캐시';


-- =====================================================================
-- 3. 알림 / 푸시
-- =====================================================================

CREATE TABLE push_subscription (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  subscription_uid CHAR(26)     NOT NULL,
  member_id        BIGINT       NOT NULL,
  endpoint         VARCHAR(500) NOT NULL                COMMENT '브라우저 푸시 서버 URL',
  p256dh_key       VARCHAR(255) NOT NULL                COMMENT '암호화 공개키 (P-256 ECDH)',
  auth_key         VARCHAR(255) NOT NULL                COMMENT '인증 비밀',
  user_agent       VARCHAR(255) NULL,
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  revoked_at       DATETIME(3)  NULL                    COMMENT '구독 해제 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uq_push_uid      (subscription_uid),
  UNIQUE KEY uq_push_endpoint (endpoint),
  KEY        ix_push_member   (member_id, revoked_at),
  CONSTRAINT fk_push_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Web Push 구독 (사용자 1명 = 기기당 1행)';


-- 푸시 발송 이력 (재시도/디버깅용)
CREATE TABLE push_log (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  subscription_id BIGINT      NOT NULL,
  schedule_id     BIGINT      NULL                    COMMENT '어느 일정 알림인지',
  push_type       ENUM('REMINDER') NOT NULL DEFAULT 'REMINDER' COMMENT 'MVP에선 출발시각 알림만',
  payload_json    JSON        NULL                    COMMENT '실제 전송한 메시지 본문',
  status          ENUM('SENT','FAILED','EXPIRED') NOT NULL,
  http_status     INT         NULL                    COMMENT '푸시 서버 응답 코드',
  sent_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY ix_pushlog_sub (subscription_id, sent_at DESC),
  CONSTRAINT fk_pushlog_sub      FOREIGN KEY (subscription_id) REFERENCES push_subscription(id) ON DELETE CASCADE,
  CONSTRAINT fk_pushlog_schedule FOREIGN KEY (schedule_id)     REFERENCES schedule(id)          ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='푸시 발송 이력';


-- =====================================================================
-- 4. 외부 API 캐시
-- =====================================================================

-- 지오코딩 캐시 (사용자 텍스트 → 좌표 변환 결과)
-- ODsay에 좌표를 직접 전달하는 흐름 가정. 텍스트 직접 전달이면 사용 빈도 낮음.
CREATE TABLE geocode_cache (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  query_hash CHAR(64)      NOT NULL              COMMENT 'SHA-256(query)',
  query_text VARCHAR(255)  NOT NULL,
  matched    BOOLEAN       NOT NULL,
  name       VARCHAR(100)  NULL,
  address    VARCHAR(255)  NULL,
  lat        DECIMAL(10,7) NULL,
  lng        DECIMAL(10,7) NULL,
  place_id   VARCHAR(100)  NULL,
  provider   ENUM('NAVER','KAKAO_LOCAL') NOT NULL,
  cached_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_geo_hash   (query_hash, provider),
  KEY        ix_geo_cached (cached_at)
) ENGINE=InnoDB COMMENT='지오코딩 결과 캐시 (TTL 30일 권장)';


-- =====================================================================
-- ERD 요약 (텍스트)
--
-- member ──┬── refresh_token       (1:N)
--          ├── schedule            (1:N) ── push_log (1:N, schedule_id로 연결)
--          └── push_subscription   (1:N) ── push_log (1:N, subscription_id로 연결)
--
-- 독립 테이블:
--   - geocode_cache (회원과 무관, 전역 공유 캐시)
-- =====================================================================


-- =====================================================================
-- 향후 추가 예정 (시간 여유 시 5/22 데모 전 복구)
-- =====================================================================
--   P1: feedback                  사용자 평가 기록
--   P1: favorite_route            즐겨찾기 - 추가 예정?
--   P1: member_preferences        선호도 (랭킹 알고리즘 도입 시)
--   P2: subway_congestion_lookup  혼잡도 통계
--   P2: bus_congestion_lookup     혼잡도 통계
--   P2: ranking_weight            랭킹 가중치 학습
--   P2: route_set / route_candidate / route_segment   후보 리랭킹 도입 시 정규화
--   P2: odsay_cache               ODsay 호출 비용 최적화
-- =====================================================================
