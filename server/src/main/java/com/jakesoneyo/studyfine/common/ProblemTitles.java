package com.jakesoneyo.studyfine.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * HTTP status → 고정 title 문구(ARCHITECTURE.md §8). 컨트롤러 예외 처리(ApiExceptionHandler)와
 * 시큐리티 필터 단계 예외 처리(SecurityConfig의 EntryPoint/AccessDeniedHandler)가 같은 문구를
 * 쓰도록 한 곳에 모아둔다 — 두 곳에서 각자 문구를 관리하면 반드시 어긋난다.
 */
public final class ProblemTitles {

    private static final Map<HttpStatus, String> TITLES = Map.of(
        HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다",
        HttpStatus.UNAUTHORIZED, "인증이 필요합니다",
        HttpStatus.FORBIDDEN, "권한이 없습니다",
        HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다",
        HttpStatus.CONFLICT, "이미 존재합니다",
        HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류"
    );

    private ProblemTitles() {
    }

    public static String of(HttpStatus status) {
        return TITLES.getOrDefault(status, status.getReasonPhrase());
    }
}
