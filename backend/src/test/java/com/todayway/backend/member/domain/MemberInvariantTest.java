package com.todayway.backend.member.domain;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Member 도메인 invariant 가드 단위 테스트.
 * α-c2 (PR #6) deleted 가드 회귀 보호선 — 정상 흐름에서 도달 불가능한 코드라
 * 통합 테스트로는 검증 불가 (claude.ai PR #6 리뷰 P1, claude.ai PR #7 리뷰 P3 클래스 명명 정정).
 */
class MemberInvariantTest {

    @Test
    void updateNickname_whenActive_updates() {
        Member m = Member.create("loginid01", "hash", "초기");
        m.updateNickname("변경후");
        assertThat(m.getNickname()).isEqualTo("변경후");
    }

    @Test
    void updateNickname_whenDeleted_throwsMemberNotFound() {
        Member m = Member.create("loginid02", "hash", "초기");
        m.softDelete();
        assertThatThrownBy(() -> m.updateNickname("변경후"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void updatePasswordHash_whenActive_updates() {
        Member m = Member.create("loginid03", "hash", "초기");
        m.updatePasswordHash("newHash");
        assertThat(m.getPasswordHash()).isEqualTo("newHash");
    }

    @Test
    void updatePasswordHash_whenDeleted_throwsMemberNotFound() {
        Member m = Member.create("loginid04", "hash", "초기");
        m.softDelete();
        assertThatThrownBy(() -> m.updatePasswordHash("newHash"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
