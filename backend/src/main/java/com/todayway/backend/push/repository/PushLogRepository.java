package com.todayway.backend.push.repository;

import com.todayway.backend.push.domain.PushLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 명세 §9.1 — 푸시 발송 이력. append-only.
 * 별도 도메인 쿼리는 P1 (재시도 정책/관측성). MVP는 INSERT 만.
 */
public interface PushLogRepository extends JpaRepository<PushLog, Long> {
}
