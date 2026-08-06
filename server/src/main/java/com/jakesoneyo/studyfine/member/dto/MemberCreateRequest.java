package com.jakesoneyo.studyfine.member.dto;

import com.jakesoneyo.studyfine.member.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 운영자가 멤버를 등록한다. 공개 회원가입이 없으므로 이메일은 항상 표준 형식(예외 없음). */
public record MemberCreateRequest(
    @NotBlank @Size(min = 1, max = 50) String name,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotNull MemberRole role
) {
}
