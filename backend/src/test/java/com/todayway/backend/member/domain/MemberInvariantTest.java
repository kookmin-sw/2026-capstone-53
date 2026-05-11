package com.todayway.backend.member.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Member 도메인 단위 테스트.
 *
 * <p>v1.1.22 (이슈 #31) — soft delete → hard delete 전환으로 deleted state 가드 (softDelete + 후속
 * update 차단) 가 사라졌다. 회원 row 자체가 DB 에서 사라지므로 service 진입 시
 * {@code findByMemberUid} 가 empty → orElseThrow(UNAUTHORIZED) — entity 레벨 invariant 가드
 * 도달 불가능 (dead code). 따라서 본 테스트도 active-state 동작 확인만 보존.
 */
class MemberInvariantTest {

    @Test
    void updateNickname_updates() {
        Member m = Member.create("loginid01", "hash", "초기");
        m.updateNickname("변경후");
        assertThat(m.getNickname()).isEqualTo("변경후");
    }

    @Test
    void updatePasswordHash_updates() {
        Member m = Member.create("loginid03", "hash", "초기");
        m.updatePasswordHash("newHash");
        assertThat(m.getPasswordHash()).isEqualTo("newHash");
    }
}
