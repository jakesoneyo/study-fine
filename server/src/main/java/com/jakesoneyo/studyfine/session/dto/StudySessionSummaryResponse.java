package com.jakesoneyo.studyfine.session.dto;

import com.jakesoneyo.studyfine.session.StudySession;
import com.jakesoneyo.studyfine.session.StudySessionSummaryProjection;
import java.time.LocalDate;

/** 회차 목록(#10)·생성(#11) 공통 응답 형태. */
public record StudySessionSummaryResponse(
    Long id,
    LocalDate sessionDate,
    String title,
    boolean checkedIn,
    long totalFine,
    long presentCount,
    long lateCount,
    long absentCount
) {

    public static StudySessionSummaryResponse from(StudySessionSummaryProjection projection) {
        return new StudySessionSummaryResponse(
            projection.getId(),
            projection.getSessionDate(),
            projection.getTitle(),
            projection.getCheckedIn(),
            projection.getTotalFine(),
            projection.getPresentCount(),
            projection.getLateCount(),
            projection.getAbsentCount()
        );
    }

    /** 방금 생성한 회차는 출석 기록이 있을 수 없으므로 집계 쿼리 없이 0으로 채운다. */
    public static StudySessionSummaryResponse ofNewSession(StudySession session) {
        return new StudySessionSummaryResponse(
            session.getId(), session.getSessionDate(), session.getTitle(), false, 0, 0, 0, 0
        );
    }
}
