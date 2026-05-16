-- =====================================================================
-- 명세 §5.1 v1.1.40 — schedule.user_departure_time NULLABLE 전환 + auto_filled 메타 컬럼.
-- 슬랙 #3: "출발 시각 입력 필요성 X — 자동 계산 + FE prefill" + T5-Q4 (외부 review).
--
-- 배경:
--   v1.1.39 이전 user_departure_time DATETIME(3) NOT NULL — 사용자가 폼에서 매번 입력
--   강요. 슬랙 #3 요구로 optional 화 + BE 가 미입력 시 recommendedDepartureTime 으로
--   자동 채움. T5-Q4 (외부 review) — 자동 추정/명시 입력 구분 메타 (departureAdviceReliable)
--   를 응답에 노출하기 위한 entity 컬럼.
--
-- 컬럼:
--   - user_departure_time: NOT NULL → NULL 허용. 등록 시 사용자 미입력 시 BE 가 ODsay 응답의
--     recommended_departure_time 으로 자동 채움 (ScheduleService.create). 자동 채움 / 명시
--     입력 모두 동일 컬럼 사용.
--   - user_departure_time_auto_filled: BOOLEAN NOT NULL DEFAULT FALSE. true = BE 자동 채움,
--     false = 사용자 명시 입력. 응답 schema 의 departureAdviceReliable 은 본 컬럼의 부정
--     (auto=true → reliable=false). dispatcher 동작 무관 — 응답 노출 전용.
--
-- backward compat:
--   기존 schedule (auto_filled NULL 없음 — 모두 사용자 명시 입력으로 간주) — DEFAULT FALSE 로
--   기존 row 모두 reliable=true 로 노출. 회귀 안전망.
--
-- ALTER 영향:
--   user_departure_time NULL 허용 전환은 MySQL 8.0+ 에서 lock-free metadata-only.
--   ADD COLUMN auto_filled BOOLEAN DEFAULT FALSE 도 동일. 운영 RDS lock 영향 0.
-- =====================================================================

ALTER TABLE schedule
    MODIFY COLUMN user_departure_time DATETIME(3) NULL
        COMMENT '사용자 의도 출발시각 (v1.1.40 NULL 허용 — BE 자동 계산 fallback)',
    ADD COLUMN user_departure_time_auto_filled BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'true=BE 자동 채움, false=사용자 명시 입력 (v1.1.40 T5-Q4 departureAdviceReliable 메타)';
