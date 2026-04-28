package com.todayway.backend.common.ulid;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class UlidGeneratorTest {

    private static final Pattern CROCKFORD_BASE32 = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$");

    @Test
    void 생성된_ULID는_길이가_26이고_Crockford_Base32_문자만_포함한다() {
        for (int i = 0; i < 1000; i++) {
            String ulid = UlidGenerator.generate();
            assertThat(ulid).hasSize(26);
            assertThat(CROCKFORD_BASE32.matcher(ulid).matches())
                    .as("ULID는 Crockford Base32 문자만 포함해야 한다: %s", ulid)
                    .isTrue();
        }
    }

    @Test
    void 만회_생성해도_중복이_없다() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(UlidGenerator.generate());
        }
        assertThat(seen).hasSize(10_000);
    }
}
