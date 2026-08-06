package com.jakesoneyo.studyfine.studyroom.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 부분 수정(PATCH) — null 필드는 미변경. */
public record StudyRoomUpdateRequest(
    @Size(min = 1, max = 60) String name,
    @Min(0) @Max(1_000_000) Integer lateFineAmount,
    @Min(0) @Max(1_000_000) Integer absentFineAmount
) {
}
