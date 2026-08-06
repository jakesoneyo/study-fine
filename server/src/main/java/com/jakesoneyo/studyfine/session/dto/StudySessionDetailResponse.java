package com.jakesoneyo.studyfine.session.dto;

import com.jakesoneyo.studyfine.attendance.dto.AttendanceView;
import java.time.LocalDate;
import java.util.List;

/** 출석 체크 화면의 로딩 데이터(#12)이자 저장 후 응답(#14)과 동일한 스키마. */
public record StudySessionDetailResponse(
    Long id,
    LocalDate sessionDate,
    String title,
    long totalFine,
    List<AttendanceView> attendances
) {
}
