package com.jakesoneyo.studyfine.studyroom;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jakesoneyo.studyfine.security.SecurityConfig;
import com.jakesoneyo.studyfine.security.SecurityTestSupportConfig;
import com.jakesoneyo.studyfine.studyroom.dto.StudyRoomResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API.md §16 권한 매트릭스: PATCH /api/study-room은 토큰 없음→401, MEMBER→403, ORGANIZER→200.
 * SecurityConfig가 @WebMvcTest에 자동으로 안 올라오므로 명시적으로 @Import한다.
 */
@WebMvcTest(StudyRoomController.class)
@Import({SecurityConfig.class, SecurityTestSupportConfig.class})
class StudyRoomControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyRoomService studyRoomService;

    @Test
    void patch_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/study-room").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void patch_withMemberRole_returns403() throws Exception {
        mockMvc.perform(patch("/api/study-room")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void patch_withOrganizerRole_returns200() throws Exception {
        given(studyRoomService.update(org.mockito.ArgumentMatchers.any()))
            .willReturn(new StudyRoomResponse(1L, "스터디룸", 3000, 5000));

        mockMvc.perform(patch("/api/study-room")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }
}
