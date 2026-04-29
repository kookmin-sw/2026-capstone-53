package com.todayway.backend.member.dto;

import com.todayway.backend.common.util.MemberIdFormatter;
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
                MemberIdFormatter.format(member.getMemberUid()),
                member.getLoginId(),
                member.getNickname(),
                member.getCreatedAt()
        );
    }
}
