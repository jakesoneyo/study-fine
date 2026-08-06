package com.jakesoneyo.studyfine.security.dto;

public record LoginResponse(String accessToken, long expiresIn, AuthenticatedMember member) {
}
