package com.jakesoneyo.studyfine.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jakesoneyo.studyfine.common.InvalidCredentialsException;
import com.jakesoneyo.studyfine.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * API.md §16: 로그인 자격 불일치 → 401, 요청 검증 실패 → 400. 자격 검증 자체는 AuthServiceTest가
 * 아니라 여기서 컨트롤러↔예외 변환 경로만 확인한다(AuthService는 목).
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityTestSupportConfig.class})
class AuthControllerLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    void login_withWrongCredentials_returns401() throws Exception {
        given(authService.login(any())).willThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginBody("admin", "wrong-password"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withBlankPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginBody("admin", ""))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    private record LoginBody(String email, String password) {
    }
}
