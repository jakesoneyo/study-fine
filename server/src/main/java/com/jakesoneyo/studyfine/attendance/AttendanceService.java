package com.jakesoneyo.studyfine.attendance;

import com.jakesoneyo.studyfine.attendance.dto.AttendanceCheckInRequest;
import com.jakesoneyo.studyfine.attendance.dto.AttendanceHistoryResponse;
import com.jakesoneyo.studyfine.attendance.dto.AttendanceHistoryResponse.AttendanceRecordItem;
import com.jakesoneyo.studyfine.attendance.dto.AttendanceHistoryResponse.MemberBrief;
import com.jakesoneyo.studyfine.common.BadRequestException;
import com.jakesoneyo.studyfine.common.NotFoundException;
import com.jakesoneyo.studyfine.member.Member;
import com.jakesoneyo.studyfine.member.MemberFineSummaryProjection;
import com.jakesoneyo.studyfine.member.MemberRepository;
import com.jakesoneyo.studyfine.session.StudySession;
import com.jakesoneyo.studyfine.session.StudySessionRepository;
import com.jakesoneyo.studyfine.session.StudySessionService;
import com.jakesoneyo.studyfine.session.dto.StudySessionDetailResponse;
import com.jakesoneyo.studyfine.studyroom.FinePolicy;
import com.jakesoneyo.studyfine.studyroom.StudyRoom;
import com.jakesoneyo.studyfine.studyroom.StudyRoomRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 출석 체크(bulk upsert) + 출석 내역 조회. */
@Service
public class AttendanceService {

    private static final Long STUDY_ROOM_ID = 1L;

    private final AttendanceRepository attendanceRepository;
    private final MemberRepository memberRepository;
    private final StudySessionRepository studySessionRepository;
    private final StudyRoomRepository studyRoomRepository;
    private final StudySessionService studySessionService;

    public AttendanceService(
        AttendanceRepository attendanceRepository,
        MemberRepository memberRepository,
        StudySessionRepository studySessionRepository,
        StudyRoomRepository studyRoomRepository,
        StudySessionService studySessionService
    ) {
        this.attendanceRepository = attendanceRepository;
        this.memberRepository = memberRepository;
        this.studySessionRepository = studySessionRepository;
        this.studyRoomRepository = studyRoomRepository;
        this.studySessionService = studySessionService;
    }

    /**
     * 한 회차의 멤버 전원 출석을 단일 트랜잭션으로 upsert한다(ARCHITECTURE.md §5.4).
     * 멤버 존재/활성 검증과 기존 기록 조회를 각각 1회로 끝내고, 멤버마다 리포지토리를 다시
     * 호출하지 않는다 — 그 순간 N+1이 된다.
     */
    @Transactional
    public StudySessionDetailResponse checkIn(Long sessionId, AttendanceCheckInRequest request) {
        StudySession session = studySessionRepository.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("회차를 찾을 수 없습니다"));

        List<Long> requestedIds = request.attendances().stream()
            .map(AttendanceCheckInRequest.Item::memberId)
            .toList();
        if (new HashSet<>(requestedIds).size() != requestedIds.size()) {
            throw new BadRequestException("중복된 멤버가 포함되어 있습니다");
        }

        // 1회 조회로 요청 멤버 전원의 실재 여부를 확인한다(멤버마다 findById 금지).
        Map<Long, Member> membersById = memberRepository.findAllById(requestedIds).stream()
            .collect(Collectors.toMap(Member::getId, m -> m));

        // 기존 기록을 1회 조회해 Map으로 만든다 — 항목마다 findBySessionAndMember를 부르면 N+1.
        // 검증 단계에서도 재사용: 비활성 멤버라도 이 회차에 이미 기록이 있으면 재저장을 허용한다
        // (API.md #12에서 회차 상세가 기존 기록 있는 비활성 멤버를 포함시키므로, 저장도 그와 일관돼야 함).
        Map<Long, AttendanceRecord> existingByMember = attendanceRepository.findByStudySessionId(sessionId).stream()
            .collect(Collectors.toMap(r -> r.getMember().getId(), r -> r));

        boolean allExistAndAllowed = requestedIds.stream()
            .allMatch(id -> membersById.containsKey(id)
                && (membersById.get(id).isActive() || existingByMember.containsKey(id)));
        if (!allExistAndAllowed) {
            throw new BadRequestException("존재하지 않거나 비활성인 멤버가 포함되어 있습니다");
        }

        StudyRoom room = studyRoomRepository.findById(STUDY_ROOM_ID)
            .orElseThrow(() -> new NotFoundException("스터디룸 설정을 찾을 수 없습니다"));

        List<AttendanceRecord> newRecords = new ArrayList<>();
        for (AttendanceCheckInRequest.Item item : request.attendances()) {
            int fineAmount = FinePolicy.calculate(item.status(), room);
            AttendanceRecord existing = existingByMember.get(item.memberId());
            if (existing != null) {
                existing.updateStatus(item.status(), fineAmount);
            } else {
                newRecords.add(new AttendanceRecord(session, membersById.get(item.memberId()), item.status(), fineAmount));
            }
        }
        attendanceRepository.saveAll(newRecords);

        return studySessionService.detail(sessionId);
    }

    /** 특정 멤버(#9) 또는 본인(#15)의 출석 내역 + 누적 벌금. 스키마는 동일, 대상만 다르다. */
    @Transactional(readOnly = true)
    public AttendanceHistoryResponse history(Long memberId) {
        // LEFT JOIN 집계라 결과가 없으면 그 memberId 자체가 존재하지 않는다는 뜻 — 404 판별에 재사용.
        MemberFineSummaryProjection summary = memberRepository.findFineSummaryByMemberId(memberId)
            .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다"));

        List<AttendanceRecordItem> records = attendanceRepository.findByMemberIdOrderBySessionDateDesc(memberId).stream()
            .map(r -> new AttendanceRecordItem(
                r.getStudySession().getId(), r.getStudySession().getSessionDate(), r.getStudySession().getTitle(),
                r.getStatus().name(), r.getFineAmount()
            ))
            .toList();

        return new AttendanceHistoryResponse(
            new MemberBrief(summary.getId(), summary.getName(), summary.getRole()),
            summary.getAccumulatedFine(), summary.getPresentCount(), summary.getLateCount(), summary.getAbsentCount(),
            records
        );
    }
}
