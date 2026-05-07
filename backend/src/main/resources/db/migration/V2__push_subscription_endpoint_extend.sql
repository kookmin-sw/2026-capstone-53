-- =====================================================================
-- 명세 §7.1 v1.1.15 — push_subscription.endpoint 길이 500 → 2048 확장.
--
-- 배경:
--   - 일부 push provider 의 endpoint 가 500 자 초과 가능 (Microsoft WNS 등 다양한 query 파라미터
--     포함). VARCHAR(500) 시 사용자가 구독을 못 하는 silent 400 발생.
--   - URL 표준 implementation 한계인 2048 채택 — FCM(~200) / Apple(~280) / Mozilla(~400) /
--     WNS(~2048) 모두 포함.
--
-- charset = ascii:
--   - URL 은 RFC 3986 ASCII 표준 — 비ASCII 문자가 endpoint 에 들어올 일 없음.
--   - utf8mb4 채로 VARCHAR(2048) 선언 시 8192 byte → InnoDB UNIQUE INDEX max key length
--     3072 byte (DYNAMIC row format) 초과로 인덱스 생성 실패. ascii 1 char = 1 byte 라
--     2048 byte ≤ 3072 byte 안전.
--   - 기존 endpoint 행은 모두 https URL → ASCII charset 변환 시 데이터 손실 X.
--
-- 영향 범위:
--   - PushSubscribeRequest @Size(max=2048) (entity Annotation 동기화 — JPA validate 통과 필요).
--   - PushSubscription 엔티티 @Column(length=2048) 동기화.
--   - UNIQUE KEY uq_push_endpoint 는 ALTER MODIFY 가 자동 재생성 (key length 도 ascii 2048 byte로 갱신).
-- =====================================================================

ALTER TABLE push_subscription
    MODIFY endpoint VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '브라우저 푸시 서버 URL (RFC 3986 ASCII / max 2048 — v1.1.15)';
