package com.jakesoneyo.studyfine.session;

import com.jakesoneyo.studyfine.attendance.AttendanceRecord;
import com.jakesoneyo.studyfine.attendance.AttendanceRepository;
import com.jakesoneyo.studyfine.attendance.dto.AttendanceView;
import com.jakesoneyo.studyfine.common.ConflictException;
import com.jakesoneyo.studyfine.common.NotFoundException;
import com.jakesoneyo.studyfine.member.Member;
import com.jakesoneyo.studyfine.member.MemberRepository;
import com.jakesoneyo.studyfine.session.dto.StudySessionCreateRequest;
import com.jakesoneyo.studyfine.session.dto.StudySessionDetailResponse;
import com.jakesoneyo.studyfine.session.dto.StudySessionSummaryResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudySessionService {

    private final StudySessionRepository studySessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final MemberRepository memberRepository;

    public StudySessionService(
        StudySessionRepository studySessionRepository,
        AttendanceRepository attendanceRepository,
        MemberRepository memberRepository
    ) {
        this.studySessionRepository = studySessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.memberRepository = memberRepository;
    }

    /** 회차별 벌금 합계·상태 카운트를 단일 GROUP BY로 산출한다(회차마다 합계 쿼리를 날리면 N+1). */
    @Transactional(readOnly = true)
    public List<StudySessionSummaryResponse> list() {
        return studySessionRepository.findAllWithSummary().stream()
            .map(StudySessionSummaryResponse::from)
            .toList();
    }

    @Transactional
    public StudySessionSummaryResponse create(StudySessionCreateRequest request) {
        if (studySessionRepository.existsBySessionDate(request.sessionDate())) {
            throw new ConflictException("해당 날짜의 회차가 이미 있습니다");
        }
        StudySession session = new StudySession(request.sessionDate(), request.title());
        studySessionRepository.save(session);
        return StudySessionSummaryResponse.ofNewSession(session);
    }

    /**
     * 출석 체크 화면의 로딩 데이터. 활성 멤버 전원 + 기록이 있는 비활성 멤버(과거 기록 보존)를 합쳐서
     * 보여준다. 아직 체크 안 한 멤버는 status: null.
     */
    @Transactional(readOnly = true)
    public StudySessionDetailResponse detail(Long sessionId) {
        StudySession session = studySessionRepository.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("회차를 찾을 수 없습니다"));

        List<AttendanceRecord> records = attendanceRepository.findByStudySessionId(sessionId);
        Map<Long, AttendanceRecord> recordsByMember = new LinkedHashMap<>();
        for (AttendanceRecord record : records) {
            recordsByMember.put(record.getMember().getId(), record);
        }

        List<Member> activeMembers = memberRepository.findByActiveTrueOrderByIdAsc();

        List<AttendanceView> attendances = new ArrayList<>();
        for (Member member : activeMembers) {
            AttendanceRecord record = recordsByMember.remove(member.getId());
            attendances.add(toView(member, record));
        }
        // 남은 항목 = 기록은 있지만 이미 비활성화된 멤버. 과거 기록을 화면에서 지우지 않기 위해 포함한다.
        for (AttendanceRecord record : recordsByMember.values()) {
            attendances.add(toView(record.getMember(), record));
        }

        long totalFine = attendances.stream().mapToLong(AttendanceView::fineAmount).sum();
        return new StudySessionDetailResponse(session.getId(), session.getSessionDate(), session.getTitle(), totalFine, attendances);
    }

    @Transactional
    public void delete(Long sessionId) {
        if (!studySessionRepository.existsById(sessionId)) {
            throw new NotFoundException("회차를 찾을 수 없습니다");
        }
        // 출석 기록은 FK ON DELETE CASCADE로 함께 삭제된다(DATA-MODEL.md §2.4).
        studySessionRepository.deleteById(sessionId);
    }

    private AttendanceView toView(Member member, AttendanceRecord record) {
        if (record == null) {
            return new AttendanceView(member.getId(), member.getName(), null, 0);
        }
        return new AttendanceView(member.getId(), member.getName(), record.getStatus().name(), record.getFineAmount());
    }
}
