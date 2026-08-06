package com.jakesoneyo.studyfine.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전 API 예외를 RFC 9457 ProblemDetail로 통일한다(ARCHITECTURE.md §8). 커스텀 에러 DTO를
 * 따로 만들지 않는다 — ProblemDetail이 Swagger·프론트 양쪽에서 이미 일관된 표준 포맷이다.
 *
 * 주의: 이 클래스는 DispatcherServlet 이후(컨트롤러/서비스 계층)에서 던져진 예외만 잡는다.
 * JWT 자체가 없거나 무효해서 필터 체인에서 막히는 401/403은 여기 닿지 않으므로
 * SecurityConfig의 AuthenticationEntryPoint/AccessDeniedHandler가 같은 포맷으로 별도 처리한다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 본문 검증에 실패했습니다");
        problem.setTitle(ProblemTitles.of(HttpStatus.BAD_REQUEST));
        problem.setInstance(URI.create(request.getRequestURI()));
        List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> new FieldErrorItem(fieldError.getField(), fieldError.getDefaultMessage()))
            .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ProblemTitles.of(ex.getStatus()));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "이 요청을 수행할 권한이 없습니다");
        problem.setTitle(ProblemTitles.of(HttpStatus.FORBIDDEN));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "인증 정보가 유효하지 않습니다");
        problem.setTitle(ProblemTitles.of(HttpStatus.UNAUTHORIZED));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /** 예상 못 한 예외는 절대 스택트레이스/메시지를 클라이언트에 노출하지 않는다. 서버 로그 + traceId만. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("처리되지 않은 예외 [traceId={}]", traceId, ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요"
        );
        problem.setTitle(ProblemTitles.of(HttpStatus.INTERNAL_SERVER_ERROR));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", traceId);
        return problem;
    }

    private record FieldErrorItem(String field, String message) {
    }
}
