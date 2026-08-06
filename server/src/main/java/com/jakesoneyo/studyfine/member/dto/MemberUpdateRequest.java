package com.jakesoneyo.studyfine.member.dto;

import com.jakesoneyo.studyfine.member.MemberRole;
import jakarta.validation.constraints.Size;

/** 부분 수정(PATCH). 이메일·비밀번호 변경은 범위 밖(SPEC.md 비범위). */
public record MemberUpdateRequest(
    @Size(min = 1, max = 50) String name,
    MemberRole role,
    Boolean active
) {
}
