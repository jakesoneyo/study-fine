package com.jakesoneyo.studyfine.common;

import org.springframework.http.HttpStatus;

/** 이메일 중복, 회차 날짜 중복, 마지막 운영자 비활성화 시도 등 상태 충돌(409). */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
