-- =====================================================================
-- 이슈 #31 fix — 회원 탈퇴 soft delete → hard delete 전환.
--
-- 배경:
--   - soft delete 모델 (member.deleted_at) + login_id UNIQUE 제약 충돌로
--     회원 탈퇴 후 동일 loginId 재가입 시 409 LOGIN_ID_DUPLICATED 발생.
--   - existsByLoginId 가 @SQLRestriction("deleted_at IS NULL") 로 false 반환 후
--     save() 시 DB UNIQUE 위반 → DataIntegrityViolationException → 409 매핑.
--
-- 정책 결정 (명세 v1.1.22):
--   - 회원 탈퇴 = 그 회원의 모든 데이터 완전 삭제 (사용자 데이터 권한 모델).
--   - FK ON DELETE CASCADE 가 refresh_token / schedule / push_subscription 일괄 삭제.
--   - push_log 는 두 FK 경로의 비대칭 동작:
--       schedule_id    ON DELETE SET NULL  (다른 회원의 schedule 삭제 시 이력 보존)
--       subscription_id ON DELETE CASCADE  (회원 탈퇴 시 그 회원의 발송 이력도 삭제)
--   - 두 번째 DELETE /members/me 응답은 401 UNAUTHORIZED 유지 (member row 없음 → findByMemberUid empty).
--
-- 마이그레이션 순서:
--   1) 옛 soft-deleted row 정리 — DROP COLUMN 후엔 살아있는 회원처럼 부활하는 silent
--      corruption 차단. DELETE 가 FK CASCADE 를 발동시켜 cascade 도 함께 정리됨.
--   2) deleted_at 컬럼 제거.
--
-- 영향:
--   - Member 엔티티에서 @SQLRestriction / deletedAt / softDelete() 제거.
--   - MemberService.delete() 가 memberRepository.delete(m) 한 줄로 단순화.
--   - ScheduleRepository.softDeleteByMemberId / PushSubscriptionRepository.revokeAllByMemberId 제거.
--   - schedule 개별 DELETE / push subscription unsubscribe 정책은 그대로 유지 (별개 정책).
-- =====================================================================

DELETE FROM member WHERE deleted_at IS NOT NULL;

ALTER TABLE member DROP COLUMN deleted_at;
