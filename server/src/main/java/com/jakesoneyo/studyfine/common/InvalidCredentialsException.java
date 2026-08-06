package com.jakesoneyo.studyfine.common;

import org.springframework.http.HttpStatus;

/**
 * 로그인 실패(401). 계정 없음/비밀번호 불일치/비활성 세 경우를 이 예외 하나로 통일한다 —
 * 원인을 구분해서 응답하면 계정 존재 여부가 새어나가 사용자 열거(enumeration) 공격에 쓰인다.
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다");
    }
}
