package com.todayway.backend.common.pagination;

import java.util.List;

public record CursorResponse<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorResponse<T> of(List<T> items, String nextCursor) {
        return new CursorResponse<>(items, nextCursor, nextCursor != null);
    }
}
