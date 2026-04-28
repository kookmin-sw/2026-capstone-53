package com.todayway.backend.common.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQtYmFzZTY0LXBhZGRlZC0zMmJ5dGVzLWxvbmc9PQ==";
    private static final String ISSUER = "todayway-test";

    @Test
    void issueAccessToken_으로_발급한_토큰을_parseSubject로_복호화하면_동일한_memberUid를_얻는다() {
        JwtProperties props = new JwtProperties(TEST_SECRET, 30, 14, ISSUER);
        JwtProvider provider = new JwtProvider(props);
        String memberUid = "01HXYA3K9G7ZRCB1234567890V";

        String token = provider.issueAccessToken(memberUid);
        String parsed = provider.parseSubject(token);

        assertThat(parsed).isEqualTo(memberUid);
    }

    @Test
    void issueRefreshToken_으로_발급한_토큰도_동일하게_복호화된다() {
        JwtProperties props = new JwtProperties(TEST_SECRET, 30, 14, ISSUER);
        JwtProvider provider = new JwtProvider(props);
        String memberUid = "01HXYA3K9G7ZRCB1234567890V";

        String token = provider.issueRefreshToken(memberUid);
        String parsed = provider.parseSubject(token);

        assertThat(parsed).isEqualTo(memberUid);
    }

    @Test
    void 만료된_토큰을_parse하면_ExpiredJwtException이_발생한다() throws InterruptedException {
        // 만료시각 즉시(0분) 설정 → 발급 직후 만료
        JwtProperties props = new JwtProperties(TEST_SECRET, 0, 0, ISSUER);
        JwtProvider provider = new JwtProvider(props);
        String token = provider.issueAccessToken("01HXYA3K9G7ZRCB1234567890V");

        Thread.sleep(50);

        assertThatThrownBy(() -> provider.parseSubject(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
