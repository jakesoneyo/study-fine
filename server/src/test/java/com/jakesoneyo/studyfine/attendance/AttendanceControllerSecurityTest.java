package com.jakesoneyo.studyfine.attendance;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jakesoneyo.studyfine.attendance.dto.AttendanceHistoryResponse;
import com.jakesoneyo.studyfine.security.SecurityConfig;
import com.jakesoneyo.studyfine.security.SecurityTestSupportConfig;
import com.jakesoneyo.studyfine.session.dto.StudySessionDetailResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API.md §16: 출석 체크(#14)는 M-4(멤버는 자기 출석을 못 고침) 검증 — 토큰 없음→401, MEMBER→403,
 * ORGANIZER→200. 내 출석 내역(#15)은 MEMBER도 200 — id 파라미터가 없는 본인 전용 경로다.
 */
@WebMvcTest(AttendanceController.class)
@Import({SecurityConfig.class, SecurityTestSupportConfig.class})
class AttendanceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttendanceService attendanceService;

    @Test
    void checkIn_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/api/sessions/1/attendances")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"attendances":[{"memberId":1,"status":"PRESENT"}]}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void checkIn_withMemberRole_returns403() throws Exception {
        mockMvc.perform(put("/api/sessions/1/attendances")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"attendances":[{"memberId":1,"status":"PRESENT"}]}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void checkIn_withOrganizerRole_returns200() throws Exception {
        given(attendanceService.checkIn(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
            .willReturn(new StudySessionDetailResponse(1L, java.time.LocalDate.now(), "회차", 0, List.of()));

        mockMvc.perform(put("/api/sessions/1/attendances")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"attendances":[{"memberId":1,"status":"PRESENT"}]}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void myAttendances_withMemberRole_returns200() throws Exception {
        given(attendanceService.history(2L)).willReturn(
            new AttendanceHistoryResponse(
                new AttendanceHistoryResponse.MemberBrief(2L, "김스터디", "MEMBER"), 0, 0, 0, 0, List.of()
            )
        );

        mockMvc.perform(get("/api/me/attendances")
                .with(jwt().jwt(builder -> builder.subject("2")).authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
            .andExpect(status().isOk());
    }
}
