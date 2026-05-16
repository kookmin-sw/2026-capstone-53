-- =====================================================================
-- 명세 §11.2 v1.1.40 — schedule 테이블에 routine 시작/종료 날짜 컬럼 추가.
-- 슬랙 #4 + 추가사항 (4): 반복 시작/끝 날짜 지정 가능 + 무한반복 (NULL = 무한).
--
-- 배경:
--   v1.1.39 이전 RoutineRule 은 type/daysOfWeek/intervalDays 만 — 반복의 경계가 없어
--   사용자가 "특정 기간만 반복" 또는 "반복 종료" 의도를 표현 불가. 슬랙 #4 의 요구
--   ("반복이 시작되는 날짜/끝나는 날짜 지정") 반영.
--
-- 컬럼:
--   - routine_start_date: 반복 시작 날짜 (DATE NULL). NULL = 등록 시점부터 시작 (backward compat).
--                         과거 날짜 허용 — dispatcher 가 reminder_at > NOW() 만 발송이라
--                         과거 occurrence 는 silent skip (T4-Q2).
--   - routine_end_date:   반복 종료 날짜 (DATE NULL). NULL = 무한반복 (default, 슬랙 정합).
--                         RoutineCalculator.calculateNextOccurrence 에서 endDate 도달 시 null
--                         반환 → §9.2 advance 종료 → reminder_at NULL dormant.
--
-- backward compat (T4-Q4):
--   기존 schedule 의 두 컬럼 = NULL → "지정 안 함 = 무한반복" 으로 통일. backfill 불필요,
--   기존 routine 일정의 동작 불변. 회귀 안전망.
--
-- 인덱스:
--   신규 추가 없음. dispatcher 폴링 쿼리는 reminder_at + deleted_at 만 사용 (기존
--   ix_sch_reminder 그대로). routine_end_date 도달 가드는 RoutineCalculator 가 application
--   layer 에서 처리 (DB row level filter 불필요).
-- =====================================================================

ALTER TABLE schedule
    ADD COLUMN routine_start_date DATE NULL
        COMMENT '반복 시작 날짜 (v1.1.40) — NULL 시 등록 시점부터',
    ADD COLUMN routine_end_date   DATE NULL
        COMMENT '반복 종료 날짜 (v1.1.40) — NULL 시 무한반복';
