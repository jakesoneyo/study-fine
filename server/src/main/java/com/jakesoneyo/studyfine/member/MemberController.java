package com.jakesoneyo.studyfine.member;

import com.jakesoneyo.studyfine.member.dto.MemberCreateRequest;
import com.jakesoneyo.studyfine.member.dto.MemberSummaryResponse;
import com.jakesoneyo.studyfine.member.dto.MemberUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 멤버 명단 관리 — 전부 ORGANIZER 전용(SPEC.md 유저 스토리 M-3: 멤버 목록은 남에게 보이면 안 됨). */
@RestController
@RequestMapping("/api/members")
@PreAuthorize("hasRole('ORGANIZER')")
@Tag(name = "Member", description = "멤버 관리 (운영자 전용)")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @Operation(summary = "멤버 목록 + 누적 벌금 조회")
    public List<MemberSummaryResponse> list(
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return memberService.list(includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "멤버 생성", description = "공개 회원가입이 없으므로 운영자가 명단에 추가한다")
    public MemberSummaryResponse create(@Valid @RequestBody MemberCreateRequest request) {
        return memberService.create(request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "멤버 수정/비활성화", description = "active=false는 물리 삭제가 아니라 명단에서만 제외(soft delete)")
    public MemberSummaryResponse update(@PathVariable Long id, @Valid @RequestBody MemberUpdateRequest request) {
        return memberService.update(id, request);
    }
}
