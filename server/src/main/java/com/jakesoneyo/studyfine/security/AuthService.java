package com.jakesoneyo.studyfine.security;

import com.jakesoneyo.studyfine.common.InvalidCredentialsException;
import com.jakesoneyo.studyfine.member.Member;
import com.jakesoneyo.studyfine.member.MemberRepository;
import com.jakesoneyo.studyfine.security.dto.AuthenticatedMember;
import com.jakesoneyo.studyfine.security.dto.LoginRequest;
import com.jakesoneyo.studyfine.security.dto.LoginResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 로그인 자격 검증 + 토큰 발급. */
@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /**
     * 계정 없음 / 비밀번호 불일치 / 비활성 세 경우 모두 동일한 {@link InvalidCredentialsException}을
     * 던진다(계정 존재 여부 노출 방지). admin 계정도 예외 없이 이 경로를 그대로 통과해야 한다 —
     * 우회 분기를 두지 않는다(CLAUDE.md 데모 계정 규정).
     */
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!member.isActive()) {
            throw new InvalidCredentialsException();
        }

        TokenService.IssuedToken issuedToken = tokenService.issue(member.getId(), member.getRole(), member.getName());
        return new LoginResponse(issuedToken.accessToken(), issuedToken.expiresInSeconds(), AuthenticatedMember.from(member));
    }
}
