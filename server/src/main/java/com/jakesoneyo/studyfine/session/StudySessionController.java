package com.jakesoneyo.studyfine.session;

import com.jakesoneyo.studyfine.session.dto.StudySessionCreateRequest;
import com.jakesoneyo.studyfine.session.dto.StudySessionDetailResponse;
import com.jakesoneyo.studyfine.session.dto.StudySessionSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 회차(스터디 모임 1회) CRUD. 목록 조회는 전원, 생성·상세·삭제는 운영자 전용. */
@RestController
@RequestMapping("/api/sessions")
@Tag(name = "StudySession", description = "회차 관리")
public class StudySessionController {

    private final StudySessionService studySessionService;

    public StudySessionController(StudySessionService studySessionService) {
        this.studySessionService = studySessionService;
    }

    @GetMapping
    @Operation(summary = "회차 목록", description = "sessionDate 내림차순, 회차별 벌금 합계 포함")
    public List<StudySessionSummaryResponse> list() {
        return studySessionService.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "회차 생성", description = "같은 날짜 회차가 이미 있으면 409")
    public StudySessionSummaryResponse create(@Valid @RequestBody StudySessionCreateRequest request) {
        return studySessionService.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "회차 상세(전체 출석 현황)", description = "출석 체크 화면의 로딩 데이터")
    public StudySessionDetailResponse detail(@PathVariable Long id) {
        return studySessionService.detail(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "회차 삭제", description = "출석 기록도 FK CASCADE로 함께 삭제된다")
    public void delete(@PathVariable Long id) {
        studySessionService.delete(id);
    }
}
