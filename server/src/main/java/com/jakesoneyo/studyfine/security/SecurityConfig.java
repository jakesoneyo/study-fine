package com.jakesoneyo.studyfine.security;

import com.jakesoneyo.studyfine.common.ProblemTitles;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * stateless JWT API 보안 설정. Spring Security 7 함정 대응(ARCHITECTURE.md ADR-2):
 * CSRF는 폼 기반뿐 아니라 API 요청에도 기본 ON이라 명시적으로 꺼야 하고(안 끄면 전체 쓰기 요청 403),
 * authorizeRequests()는 제거되어 authorizeHttpRequests()만 쓴다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;

    public SecurityConfig(
        JwtAuthenticationConverter jwtAuthenticationConverter,
        ObjectMapper objectMapper,
        @Value("${app.cors.allowed-origins}") List<String> allowedOrigins
    ) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(problemDetailEntryPoint())
            )
            .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(problemDetailAccessDeniedHandler()));

        return http.build();
    }

    /**
     * 필터 체인 단계(토큰 없음/무효/만료)에서 막히는 401은 DispatcherServlet에 닿지 않아
     * ApiExceptionHandler가 못 잡는다. 여기서 같은 ProblemDetail 포맷으로 직접 응답을 쓴다.
     */
    private AuthenticationEntryPoint problemDetailEntryPoint() {
        return (request, response, authException) -> writeProblemDetail(
            response, HttpStatus.UNAUTHORIZED, "인증 정보가 유효하지 않습니다", request.getRequestURI()
        );
    }

    private AccessDeniedHandler problemDetailAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeProblemDetail(
            response, HttpStatus.FORBIDDEN, "이 요청을 수행할 권한이 없습니다", request.getRequestURI()
        );
    }

    private void writeProblemDetail(
        jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String detail, String instance
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(ProblemTitles.of(status));
        problem.setInstance(URI.create(instance));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    /** 프론트 오리진을 명시적 목록으로만 허용한다. 와일드카드(*) 금지(CLAUDE.md/ARCHITECTURE.md §9). */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
