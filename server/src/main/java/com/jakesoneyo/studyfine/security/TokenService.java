package com.jakesoneyo.studyfine.security;

import com.jakesoneyo.studyfine.member.MemberRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** 로그인 성공 시 액세스 토큰을 발급한다. sub=memberId, role/name은 프론트가 재조회 없이 쓸 수 있게 담는다. */
@Service
public class TokenService {

    private static final String ISSUER = "study-fine";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public IssuedToken issue(Long memberId, MemberRole role, String name) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.getExpirationHours(), ChronoUnit.HOURS);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(String.valueOf(memberId))
            .claim("role", role.name())
            .claim("name", name)
            .build();

        // JwtEncoderParameters.from(claims)만 쓰면 헤더 기본값이 RS256으로 잡혀 HS256 JWK와 맞지 않아
        // "Failed to select a JWK signing key"로 실패한다. HS256을 헤더에 명시해야 한다.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        long expiresInSeconds = jwtProperties.getExpirationHours() * 3600;
        return new IssuedToken(tokenValue, expiresInSeconds);
    }

    public record IssuedToken(String accessToken, long expiresInSeconds) {
    }
}
