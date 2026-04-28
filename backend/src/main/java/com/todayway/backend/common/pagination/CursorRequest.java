package com.todayway.backend.common.pagination;

public record CursorRequest(int limit, String cursor) {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    public CursorRequest {
        if (limit <= 0 || limit > MAX_LIMIT) {
            limit = DEFAULT_LIMIT;
        }
    }
}
