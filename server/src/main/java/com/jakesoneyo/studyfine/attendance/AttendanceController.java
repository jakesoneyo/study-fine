package com.jakesoneyo.studyfine.attendance;

import com.jakesoneyo.studyfine.attendance.dto.AttendanceCheckInRequest;
import com.jakesoneyo.studyfine.attendance.dto.AttendanceHistoryResponse;
import com.jakesoneyo.studyfine.security.CurrentMemberId;
import com.jakesoneyo.studyfine.session.dto.StudySessionDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 출석 체크(bulk upsert) + 출석 내역 조회. 본인 조회(#15)는 id 파라미터를 아예 받지 않는 전용
 * 경로로 분리했다 — id를 받는 경로(#9)는 통째로 ORGANIZER 전용이라 "principal과 id 비교"를
 * 빠뜨려 생기는 수평 권한 상승이 구조적으로 불가능하다(ARCHITECTURE.md §4).
 */
@RestController
@Tag(name = "Attendance", description = "출석 체크 · 출석 내역")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PutMapping("/api/sessions/{id}/attendances")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "출석 체크(bulk upsert)", description = "멤버 전원 상태를 한 번에 저장. 벌금은 서버가 계산해 확정한다")
    public StudySessionDetailResponse checkIn(@PathVariable Long id, @Valid @RequestBody AttendanceCheckInRequest request) {
        return attendanceService.checkIn(id, request);
    }

    @GetMapping("/api/members/{id}/attendances")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "특정 멤버 출석 내역", description = "본인 id를 넣어도 403 — 본인 조회는 /api/me/attendances 전용")
    public AttendanceHistoryResponse memberHistory(@PathVariable Long id) {
        return attendanceService.history(id);
    }

    @GetMapping("/api/me/attendances")
    @Operation(summary = "내 출석 내역 + 누적 벌금", description = "토큰의 sub만 사용. 남의 데이터를 지칭할 방법이 없다")
    public AttendanceHistoryResponse myHistory(@CurrentMemberId Long memberId) {
        return attendanceService.history(memberId);
    }
}
