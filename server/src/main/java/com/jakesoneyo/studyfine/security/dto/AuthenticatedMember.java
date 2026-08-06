package com.jakesoneyo.studyfine.security.dto;

import com.jakesoneyo.studyfine.member.Member;

/** 로그인 응답의 member 필드 및 GET /api/auth/me 응답에 공통으로 쓰는 최소 표현. */
public record AuthenticatedMember(Long id, String name, String email, String role) {

    public static AuthenticatedMember from(Member member) {
        return new AuthenticatedMember(member.getId(), member.getName(), member.getEmail(), member.getRole().name());
    }
}
