package com.jakesoneyo.studyfine.common;

import org.springframework.http.HttpStatus;

/** 대상 리소스가 없을 때(404). */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
