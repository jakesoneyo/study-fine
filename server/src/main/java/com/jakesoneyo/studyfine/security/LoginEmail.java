package com.jakesoneyo.studyfine.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인 요청 전용 이메일 제약. 데모 계정 리터럴 "admin" 딱 하나만 이메일 형식 검증을 우회시킨다.
 * 표준 {@code @Email}을 그냥 빼면 모든 비이메일 문자열이 통과하므로, 예외를 리터럴 1건으로
 * 좁히기 위해 커스텀 제약이 필요하다(CLAUDE.md 데모 계정 규정). 로그인 DTO에만 붙이고,
 * 멤버 생성 DTO는 항상 표준 {@code @Email}을 쓴다 — 예외 없음.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LoginEmailValidator.class)
public @interface LoginEmail {

    String message() default "올바른 이메일 형식이 아닙니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
