-- =====================================================================
-- 명세 §9.3 v1.1.29 — schedule 테이블에 경유 노드별 알림 (per-leg reminder) 컬럼 추가.
-- 팀 피드백 #3 ("경유 노드별 N분 전 알림") 의 데이터 모델.
--
-- 배경:
--   - 기존 모델은 한 일정 = 1회 알림 (recommended_departure_time 의 N분 전, reminder_at 단일 컬럼).
--   - 환승이 있는 경로에서 사용자는 출발 외에도 환승 boarding 시점마다 heads-up 이 필요하다는
--     사용자 피드백.
--   - reminder_at 은 그대로 두고 (스케줄러 폴링 쿼리 `WHERE reminder_at <= now` 변경 X),
--     "현재 leg 의 fireAt" 으로 denormalize. leg advance 시 다음 leg fireAt 으로 갱신.
--
-- 컬럼:
--   - reminder_legs_json: ReminderLeg 배열을 JSON 으로 저장. 예시:
--       [
--         {"legIndex":0,"fireAt":"2026-04-21T08:25:00+09:00","mode":"WALK","fromName":"홈","toName":null,"offsetMinutes":5},
--         {"legIndex":1,"fireAt":"2026-04-21T08:35:00+09:00","mode":"BUS","fromName":"○○정류장","toName":"△△정류장","lineName":"700","offsetMinutes":5},
--         {"legIndex":2,"fireAt":"2026-04-21T08:55:00+09:00","mode":"SUBWAY","fromName":"강남역","toName":"교대역","lineName":"2호선","offsetMinutes":5}
--       ]
--     NULL 인 경우 (마이그레이션 이전 행 / ODsay 호출 실패 / segments 0건):
--     기존 단일 reminder_at 흐름 그대로 동작. 회귀 안전망.
--   - reminder_leg_index: 현재 발송 대기 중인 leg index (0-based). reminder_legs_json 의
--     해당 index 행이 곧 발송될 leg. NULL 인 경우 위 안전망과 동일.
--
-- 인덱스:
--   - 신규 추가 없음. 스케줄러 폴링은 reminder_at + deleted_at 만 사용 (ix_sch_reminder 그대로).
--
-- 영향 범위:
--   - Schedule 엔티티 신규 필드 + getter / advance 메서드.
--   - OdsayRouteService.applyToSchedule: ReminderLegPlanner 호출 후 결과 동기 저장.
--   - PushReminderTransactional.advanceOrTerminate: leg index 우선 advance, 마지막 leg 도달
--     시 occurrence advance + leg planner 로 재계산.
--   - PushReminderDispatcher.buildPayloadJson: 현재 leg 의 mode/from/to/lineName payload 노출.
-- =====================================================================

ALTER TABLE schedule
    ADD COLUMN reminder_legs_json JSON NULL
        COMMENT '경유 노드별 알림 leg 배열 (v1.1.29) — NULL 시 단일 reminder_at fallback',
    ADD COLUMN reminder_leg_index INT  NULL
        COMMENT '현재 발송 대기 leg index (0-based) — NULL 시 단일 reminder_at fallback';
