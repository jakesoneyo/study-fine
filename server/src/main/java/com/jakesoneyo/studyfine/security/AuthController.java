package com.jakesoneyo.studyfine.security;

import com.jakesoneyo.studyfine.common.NotFoundException;
import com.jakesoneyo.studyfine.member.Member;
import com.jakesoneyo.studyfine.member.MemberRepository;
import com.jakesoneyo.studyfine.security.dto.AuthenticatedMember;
import com.jakesoneyo.studyfine.security.dto.LoginRequest;
import com.jakesoneyo.studyfine.security.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 + 현재 로그인 멤버 조회(새로고침 시 세션 복구용). */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "로그인/세션")
public class AuthController {

    private final AuthService authService;
    private final MemberRepository memberRepository;

    public AuthController(AuthService authService, MemberRepository memberRepository) {
        this.authService = authService;
        this.memberRepository = memberRepository;
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일 + 비밀번호로 JWT를 발급한다. 데모 계정: admin/admin")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "현재 로그인 멤버 조회")
    public AuthenticatedMember me(@CurrentMemberId Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다"));
        return AuthenticatedMember.from(member);
    }
}
