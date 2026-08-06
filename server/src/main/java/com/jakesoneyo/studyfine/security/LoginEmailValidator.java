package com.jakesoneyo.studyfine.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/** "admin" 리터럴 예외 + 표준 이메일 정규식. null/blank는 {@code @NotBlank}가 별도로 처리한다. */
public class LoginEmailValidator implements ConstraintValidator<LoginEmail, String> {

    private static final String DEMO_ACCOUNT_EMAIL = "admin";
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return DEMO_ACCOUNT_EMAIL.equals(value) || EMAIL_PATTERN.matcher(value).matches();
    }
}
