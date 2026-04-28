package com.todayway.backend.common.jwt;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQtYmFzZTY0LXBhZGRlZC0zMmJ5dGVzLWxvbmc9PQ==";
    private static final String ISSUER = "todayway-test";
    private static final String MEMBER_UID = "01HXYA3K9G7ZRCB1234567890V";

    private JwtProvider jwtProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtProvider = new JwtProvider(new JwtProperties(TEST_SECRET, 30, 14, ISSUER));
        filter = new JwtAuthenticationFilter(jwtProvider);
    }

    @Test
    void 헤더에_Authorization이_없으면_SecurityContext가_비어있고_체인이_그대로_진행된다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/main");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void 유효한_토큰이면_SecurityContext에_memberUid가_저장되고_체인이_진행된다() throws Exception {
        String token = jwtProvider.issueAccessToken(MEMBER_UID);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/schedules");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(MEMBER_UID);
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void 만료된_토큰이면_체인을_차단하고_401_TOKEN_EXPIRED_JSON을_응답한다() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(new JwtProperties(TEST_SECRET, 0, 0, ISSUER));
        String expiredToken = expiredProvider.issueAccessToken(MEMBER_UID);
        Thread.sleep(50);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/schedules");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("TOKEN_EXPIRED");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void 위조된_토큰이면_SecurityContext만_비우고_체인은_진행한다_401은_EntryPoint가_담당() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/schedules");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer this.is.not.a.valid.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(request, response);
    }
}
