package com.todayway.backend.schedule.repository;

import com.todayway.backend.schedule.domain.Schedule;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /**
     * derived query — @SQLRestriction("deleted_at IS NULL") 자동 적용.
     */
    Optional<Schedule> findByScheduleUid(String scheduleUid);

    /**
     * 명세 §5.2 cursor 페이지네이션. ⚠️ JPQL은 @SQLRestriction 자동 적용 X — `deletedAt IS NULL` 명시 필수.
     * 정렬: arrival_time ASC, id ASC tie-break.
     *
     * <p>Keyset 술어는 정렬과 동일한 합성 키 {@code (arrivalTime, id)} 기준이어야 함 — 이슈 #15:
     * id 단일 술어 시 arrival ASC 순서와 id 단조 증가가 어긋나면 일정 영구 누락.
     * cursor 양쪽이 모두 null 인 경우만 1페이지로 처리 — service 가 동반 보장.
     */
    @Query("""
            SELECT s FROM Schedule s
            WHERE s.memberId = :memberId
              AND s.deletedAt IS NULL
              AND (:from IS NULL OR s.arrivalTime >= :from)
              AND (:to IS NULL OR s.arrivalTime <= :to)
              AND (:cursorArrival IS NULL
                   OR s.arrivalTime > :cursorArrival
                   OR (s.arrivalTime = :cursorArrival AND s.id > :cursorId))
            ORDER BY s.arrivalTime ASC, s.id ASC
            """)
    List<Schedule> findPage(@Param("memberId") Long memberId,
                            @Param("from") OffsetDateTime from,
                            @Param("to") OffsetDateTime to,
                            @Param("cursorArrival") OffsetDateTime cursorArrival,
                            @Param("cursorId") Long cursorId,
                            Pageable pageable);

    /**
     * 명세 §4.1 메인 화면의 nearestSchedule 조회 — 미래 가장 가까운 일정.
     * derived query — @SQLRestriction 자동 적용.
     */
    Optional<Schedule> findFirstByMemberIdAndArrivalTimeAfterOrderByArrivalTimeAsc(
            Long memberId, OffsetDateTime now);

    /**
     * 명세 §9.1 — PushScheduler 알림 시각 도래 일정 조회. {@code reminder_at > NOW()-5min AND <= NOW()}
     * 누락 방지 윈도우 + soft-delete 필터.
     *
     * <p>회원 soft-delete 는 {@code MemberService.softDelete} cascade 가 schedule 도 일괄
     * {@code deleted_at} 채우므로 위 {@code s.deletedAt IS NULL} 조건에서 자동 배제 — Member 서브쿼리
     * 중복 제거. cascade 회귀는 {@code MemberSoftDeleteCascadeTest} 가 가드.
     */
    @Query("""
            SELECT s FROM Schedule s
            WHERE s.reminderAt > :windowStart
              AND s.reminderAt <= :now
              AND s.deletedAt IS NULL
            """)
    List<Schedule> findDueReminders(@Param("now") OffsetDateTime now,
                                    @Param("windowStart") OffsetDateTime windowStart);
}
