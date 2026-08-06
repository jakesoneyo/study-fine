package com.jakesoneyo.studyfine.member;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jakesoneyo.studyfine.security.SecurityConfig;
import com.jakesoneyo.studyfine.security.SecurityTestSupportConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API.md §16: GET /api/members는 전체 멤버·벌금이 드러나므로(M-3) 토큰 없음→401, MEMBER→403, ORGANIZER→200. */
@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, SecurityTestSupportConfig.class})
class MemberControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/members"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withMemberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/members").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void list_withOrganizerRole_returns200() throws Exception {
        given(memberService.list(false)).willReturn(List.of());

        mockMvc.perform(get("/api/members").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
            .andExpect(status().isOk());
    }
}
