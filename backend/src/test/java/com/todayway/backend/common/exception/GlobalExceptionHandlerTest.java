package com.todayway.backend.common.exception;

import com.todayway.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.DummyController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.DummyController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    // WebMvcConfig가 슬라이스에 등록되며 CurrentMemberArgumentResolver → MemberRepository 의존을 요구.
    // 이 테스트는 web 계층만 검증하므로 mock으로 대체.
    @MockBean
    MemberRepository memberRepository;

    @Test
    void BusinessException은_ErrorCode의_status와_code_message로_변환된다() throws Exception {
        mockMvc.perform(get("/dummy/business"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("회원을 찾을 수 없습니다"))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    void AccessDeniedException은_403_FORBIDDEN_RESOURCE로_변환된다() throws Exception {
        mockMvc.perform(get("/dummy/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    void 알수없는_예외는_500_INTERNAL_SERVER_ERROR로_변환된다() throws Exception {
        mockMvc.perform(get("/dummy/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
    }

    @RestController
    static class DummyController {
        @GetMapping("/dummy/business")
        public String business() {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        @GetMapping("/dummy/forbidden")
        public String forbidden() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/dummy/boom")
        public String boom() {
            throw new IllegalStateException("unexpected");
        }
    }
}
