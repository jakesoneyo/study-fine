package com.jakesoneyo.studyfine.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI에서 Bearer 토큰을 붙여 바로 API를 호출할 수 있게 보안 스키마를 등록한다. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI studyFineOpenApi() {
        return new OpenAPI()
            .info(new Info().title("study-fine API").description("스터디모임 출석 관리 + 벌금 자동 계산").version("v1"))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
            .components(new Components().addSecuritySchemes(
                BEARER_SCHEME_NAME,
                new SecurityScheme()
                    .name(BEARER_SCHEME_NAME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ));
    }
}
