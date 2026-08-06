package com.jakesoneyo.studyfine.security;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.jwt.* 설정. HS256은 최소 32바이트 키를 요구하므로 기동 시점에 미리 검증해 늦은 실패를 막는다. */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private static final int MIN_SECRET_BYTES = 32;

    private final String secret;
    private final long expirationHours;

    public JwtProperties(String secret, long expirationHours) {
        this.secret = secret;
        this.expirationHours = expirationHours;
    }

    @PostConstruct
    void validateSecretLength() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET은 HS256 최소 요구치인 32바이트 이상이어야 합니다. .env의 JWT_SECRET을 확인하세요."
            );
        }
    }

    public String getSecret() {
        return secret;
    }

    public long getExpirationHours() {
        return expirationHours;
    }
}
