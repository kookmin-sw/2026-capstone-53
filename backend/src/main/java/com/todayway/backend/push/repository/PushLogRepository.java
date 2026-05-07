package com.todayway.backend.push.repository;

import com.todayway.backend.push.domain.PushLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 명세 §9.1 — 푸시 발송 이력. append-only. 현재는 INSERT + 통합 테스트용 단건 조회만.
 * 재시도/관측성 도메인 쿼리는 도입 시 추가.
 */
public interface PushLogRepository extends JpaRepository<PushLog, Long> {

    /**
     * 통합 테스트에서 자기 케이스의 발송 이력만 격리해 검증하기 위한 derived query.
     * 운영 코드에서는 사용하지 않는다.
     */
    List<PushLog> findBySubscriptionId(Long subscriptionId);
}
