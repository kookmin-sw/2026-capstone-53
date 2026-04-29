package com.todayway.backend.common.util;

import com.todayway.backend.common.exception.BusinessException;
import com.todayway.backend.common.exception.ErrorCode;

public final class MemberIdFormatter {

    private static final String PREFIX = "mem_";

    private MemberIdFormatter() {}

    public static String format(String memberUid) {
        return PREFIX + memberUid;
    }

    public static String parse(String prefixedId) {
        if (prefixedId == null || !prefixedId.startsWith(PREFIX)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "잘못된 회원 ID 형식");
        }
        return prefixedId.substring(PREFIX.length());
    }
}
