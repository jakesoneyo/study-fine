package com.jakesoneyo.studyfine.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 토큰의 sub(memberId) 클레임을 컨트롤러 파라미터로 바로 주입한다.
 * "/api/me/**" 계열 엔드포인트가 id 파라미터를 아예 받지 않게 하는 장치 — 클라이언트가
 * 남의 id를 지칭할 방법 자체를 없애 수평 권한 상승을 구조적으로 막는다(ARCHITECTURE.md §4).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(claims['sub'])")
public @interface CurrentMemberId {
}
