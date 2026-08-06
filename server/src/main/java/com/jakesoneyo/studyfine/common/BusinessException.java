package com.jakesoneyo.studyfine.common;

import org.springframework.http.HttpStatus;

/** 도메인 규칙 위반을 나타내는 예외의 공통 기저 클래스. ApiExceptionHandler가 status로 응답을 만든다. */
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;

    protected BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
