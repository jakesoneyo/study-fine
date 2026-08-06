package com.jakesoneyo.studyfine.attendance.dto;

import com.jakesoneyo.studyfine.attendance.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 한 회차의 멤버 전원 출석 상태를 한 번에 담는다. fineAmount는 요청에 없다 — 클라이언트가 금액을
 * 보낼 수 있으면 조작 가능해지므로, 금액은 항상 서버가 현재 벌금 단가로 계산한다.
 */
public record AttendanceCheckInRequest(@NotEmpty List<@Valid Item> attendances) {

    public record Item(@NotNull Long memberId, @NotNull AttendanceStatus status) {
    }
}
