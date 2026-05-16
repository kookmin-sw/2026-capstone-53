-- =====================================================================
-- v1.1.31 — V4 (`reminder_legs_json`, `reminder_leg_index`) 컬럼 제거.
--
-- 배경:
--   V4 (v1.1.29) 에서 도입한 "경유 노드별 N분 전 알림" (#3) 은 팀 피드백 의도와
--   불일치로 롤백 결정. 실제 요구는 "한 일정 = 1회 출발 알림" + "반복 일정은
--   매 occurrence 마다 알림" 이고, 이는 이미 v1.1.16 분리 패턴 + RoutineCalculator
--   advanceToNextOccurrence 로 만족됨. multi-leg 데이터 모델은 잉여로 판단해 제거.
--
-- Flyway forward-only 원칙에 따라 V4 파일 자체는 유지하되 본 V5 가 컬럼을 drop —
-- dev DB 가 이미 V4 를 적용한 환경에서도 schema_history 손상 없이 정합 보장.
--
-- 영향 범위:
--   - Schedule 엔티티 매핑에서 reminder_legs_json / reminder_leg_index 필드 없음 (롤백됨).
--   - PushReminderTransactional / Dispatcher / OdsayRouteService 의 leg 분기 모두 제거됨.
-- =====================================================================

ALTER TABLE schedule
    DROP COLUMN reminder_legs_json,
    DROP COLUMN reminder_leg_index;
