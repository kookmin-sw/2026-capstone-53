package com.todayway.backend.common.exception;

import com.todayway.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @MockitoBean
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

    // ─────────── self-review M1 — handleBadRequest root cause unwrap 분기 ───────────

    @Test
    void HttpMessageNotReadable_record_compact_ctor_IAE는_400_VALIDATION_ERROR_매핑된다() throws Exception {
        // record DummyCoord 의 compact ctor 가 NaN 입력에 IAE throw → Jackson wrap →
        // HttpMessageNotReadableException 으로 도착. handleBadRequest 의 root cause unwrap 분기가
        // IAE 를 발견해 WARN 로깅. 응답은 일반 사용자 입력 오류와 동일하게 400 VALIDATION_ERROR.
        mockMvc.perform(post("/dummy/deserialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\": \"NaN\", \"lng\": 127.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void HttpMessageNotReadable_malformed_JSON_은_400_VALIDATION_ERROR_매핑된다() throws Exception {
        // malformed JSON — root cause 는 JsonParseException (IAE 아님). 사용자 입력 오류 path 라
        // unwrap 분기는 fall through. 두 path 모두 동일 400 응답으로 떨어지는지 회귀 가드.
        mockMvc.perform(post("/dummy/deserialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
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

        @PostMapping("/dummy/deserialize")
        public String deserialize(@RequestBody DummyCoord body) {
            return "ok";
        }
    }

    /** record compact ctor 가 IAE throw — deserialization 도중 Jackson 이 wrap 하는 케이스 재현. */
    public record DummyCoord(double lat, double lng) {
        public DummyCoord {
            if (!Double.isFinite(lat)) {
                throw new IllegalArgumentException("lat must be finite");
            }
            if (!Double.isFinite(lng)) {
                throw new IllegalArgumentException("lng must be finite");
            }
        }
    }
}
