package com.jakesoneyo.studyfine.common;

import org.springframework.http.HttpStatus;

/** Bean Validation으로 못 잡는 의미적 검증 실패(예: 출석 체크의 비활성/미존재 멤버, 중복 멤버) — 400. */
public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
