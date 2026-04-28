package com.todayway.backend.common.ulid;

import com.github.f4b6a3.ulid.Ulid;

public final class UlidGenerator {

    private UlidGenerator() {}

    public static String generate() {
        return Ulid.fast().toString();
    }
}
