package com.jakesoneyo.studyfine.studyroom;

import com.jakesoneyo.studyfine.studyroom.dto.StudyRoomResponse;
import com.jakesoneyo.studyfine.studyroom.dto.StudyRoomUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 스터디룸 설정(이름·벌금 단가) 조회/수정. */
@RestController
@RequestMapping("/api/study-room")
@Tag(name = "StudyRoom", description = "스터디룸 설정 · 벌금 단가")
public class StudyRoomController {

    private final StudyRoomService studyRoomService;

    public StudyRoomController(StudyRoomService studyRoomService) {
        this.studyRoomService = studyRoomService;
    }

    @GetMapping
    @Operation(summary = "스터디룸 + 벌금 단가 조회", description = "멤버도 자기 벌금의 근거를 알아야 하므로 전원 조회 가능")
    public StudyRoomResponse get() {
        return studyRoomService.get();
    }

    @PatchMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "벌금 단가 수정", description = "새 단가는 이후 출석 체크부터 적용되며 과거 기록은 변하지 않는다")
    public StudyRoomResponse update(@Valid @RequestBody StudyRoomUpdateRequest request) {
        return studyRoomService.update(request);
    }
}
