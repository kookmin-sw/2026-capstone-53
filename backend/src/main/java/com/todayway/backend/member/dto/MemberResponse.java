package com.todayway.backend.member.dto;

import com.todayway.backend.common.web.IdPrefixes;
import com.todayway.backend.member.domain.Member;

import java.time.OffsetDateTime;

public record MemberResponse(
        String memberId,
        String loginId,
        String nickname,
        OffsetDateTime createdAt
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                IdPrefixes.MEMBER + member.getMemberUid(),
                member.getLoginId(),
                member.getNickname(),
                member.getCreatedAt()
        );
    }
}
