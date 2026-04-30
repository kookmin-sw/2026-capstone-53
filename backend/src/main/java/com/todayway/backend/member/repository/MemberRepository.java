package com.todayway.backend.member.repository;

import com.todayway.backend.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String loginId);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByMemberUid(String memberUid);
}
