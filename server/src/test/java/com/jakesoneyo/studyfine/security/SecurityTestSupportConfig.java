package com.jakesoneyo.studyfine.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * {@code @WebMvcTest} 권한 가드 테스트 전용 보안 빈. 실제 {@code JwtConfig}는 JWT_SECRET(.env)이
 * 있어야 하지만, 슬라이스 테스트는 {@code jwt()} 요청 후처리기로 인증을 직접 주입하므로 실제
 * 디코더가 호출될 일이 없다 — 시크릿 없이도 CI에서 그대로 돌아가게 하기 위해 최소 빈만 따로 둔다.
 */
@TestConfiguration
public class SecurityTestSupportConfig {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("role");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException("테스트는 jwt() 포스트프로세서로 인증을 주입하므로 디코더가 호출되지 않아야 한다");
        };
    }
}
