package com.jakesoneyo.studyfine.attendance.dto;

import java.time.LocalDate;
import java.util.List;

/** 특정 멤버 출석 내역(#9) / 내 출석 내역(#15) 공통 응답 — 대상만 다르고 스키마는 동일하다. */
public record AttendanceHistoryResponse(
    MemberBrief member,
    long accumulatedFine,
    long presentCount,
    long lateCount,
    long absentCount,
    List<AttendanceRecordItem> records
) {

    public record MemberBrief(Long id, String name, String role) {
    }

    public record AttendanceRecordItem(
        Long sessionId, LocalDate sessionDate, String sessionTitle, String status, int fineAmount
    ) {
    }
}
