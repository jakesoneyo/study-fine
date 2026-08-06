package com.jakesoneyo.studyfine.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.jakesoneyo.studyfine.attendance.dto.AttendanceCheckInRequest;
import com.jakesoneyo.studyfine.common.BadRequestException;
import com.jakesoneyo.studyfine.member.Member;
import com.jakesoneyo.studyfine.member.MemberRepository;
import com.jakesoneyo.studyfine.member.MemberRole;
import com.jakesoneyo.studyfine.session.StudySession;
import com.jakesoneyo.studyfine.session.StudySessionRepository;
import com.jakesoneyo.studyfine.session.StudySessionService;
import com.jakesoneyo.studyfine.session.dto.StudySessionDetailResponse;
import com.jakesoneyo.studyfine.studyroom.StudyRoom;
import com.jakesoneyo.studyfine.studyroom.StudyRoomRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW.md 🔴-2: API.md #12(회차 상세는 기존 기록 있는 비활성 멤버도 포함)와 #14(bulk upsert 검증)가
 * 서로 다른 말을 하면 안 된다 — 비활성 멤버라도 그 회차에 이미 기록이 있으면 재저장이 통과해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private StudySessionRepository studySessionRepository;
    @Mock
    private StudyRoomRepository studyRoomRepository;
    @Mock
    private StudySessionService studySessionService;

    private AttendanceService attendanceService;

    private StudySession session;
    private Member inactiveMemberWithRecord;
    private StudyRoom room;

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceService(
            attendanceRepository, memberRepository, studySessionRepository, studyRoomRepository, studySessionService
        );

        session = new StudySession(LocalDate.of(2026, 1, 1), "1회차");
        ReflectionTestUtils.setField(session, "id", 1L);

        inactiveMemberWithRecord = new Member("member@example.com", "hash", "비활성멤버", MemberRole.MEMBER);
        ReflectionTestUtils.setField(inactiveMemberWithRecord, "id", 10L);
        inactiveMemberWithRecord.deactivate();

        room = BeanUtils.instantiateClass(StudyRoom.class);
        ReflectionTestUtils.setField(room, "id", 1L);
        room.updateRates("스터디룸", 3000, 5000);
    }

    @Test
    void checkIn_allowsInactiveMember_whenSessionAlreadyHasRecordForThem() {
        // 이미 이 회차에 출석 기록이 있는 비활성 멤버 — SPEC O-7: 그 기록의 상태만 다시 바꿔서 저장 가능해야 한다.
        AttendanceRecord existingRecord = new AttendanceRecord(session, inactiveMemberWithRecord, AttendanceStatus.PRESENT, 0);

        given(studySessionRepository.findById(1L)).willReturn(java.util.Optional.of(session));
        given(memberRepository.findAllById(List.of(10L))).willReturn(List.of(inactiveMemberWithRecord));
        given(attendanceRepository.findByStudySessionId(1L)).willReturn(List.of(existingRecord));
        given(studyRoomRepository.findById(1L)).willReturn(java.util.Optional.of(room));
        given(studySessionService.detail(1L))
            .willReturn(new StudySessionDetailResponse(1L, session.getSessionDate(), session.getTitle(), 5000, List.of()));

        AttendanceCheckInRequest request = new AttendanceCheckInRequest(
            List.of(new AttendanceCheckInRequest.Item(10L, AttendanceStatus.ABSENT))
        );

        attendanceService.checkIn(1L, request);

        // 새 레코드를 만들지 않고 기존 레코드의 상태·벌금만 갱신됐는지 확인한다.
        assertThat(existingRecord.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(existingRecord.getFineAmount()).isEqualTo(5000);
        verify(studySessionService).detail(1L);
    }

    @Test
    void checkIn_rejectsInactiveMember_whenNoExistingRecordForThisSession() {
        // 이 회차에 기록이 없는 비활성 멤버를 새로 체크하려는 시도는 여전히 막혀야 한다.
        given(studySessionRepository.findById(1L)).willReturn(java.util.Optional.of(session));
        given(memberRepository.findAllById(List.of(10L))).willReturn(List.of(inactiveMemberWithRecord));
        given(attendanceRepository.findByStudySessionId(1L)).willReturn(List.of());

        AttendanceCheckInRequest request = new AttendanceCheckInRequest(
            List.of(new AttendanceCheckInRequest.Item(10L, AttendanceStatus.PRESENT))
        );

        assertThatThrownBy(() -> attendanceService.checkIn(1L, request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("존재하지 않거나 비활성인 멤버");
    }
}
