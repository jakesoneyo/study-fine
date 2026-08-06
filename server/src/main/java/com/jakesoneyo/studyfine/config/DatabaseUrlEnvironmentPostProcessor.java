package com.jakesoneyo.studyfine.config;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Neon이 주는 DATABASE_URL은 "postgresql://user:pass@host/db?…" 형태(URI 스타일)라
 * pgjdbc가 요구하는 "jdbc:postgresql://host/db?...&user=..&password=.." 형태로 변환해야 한다.
 * pgjdbc는 URI의 user:pass@host 부분을 파싱하지 않는다.
 *
 * ConfigDataEnvironmentPostProcessor(spring.config.import로 .env를 읽는 처리기)는
 * HIGHEST_PRECEDENCE로 이 클래스보다 먼저 실행되므로, 여기서는 이미 해석된 DATABASE_URL을
 * 읽어 spring.datasource.* 프로퍼티로 변환해 넣기만 하면 된다.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String rawUrl = environment.getProperty("DATABASE_URL");
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }

        URI uri = URI.create(rawUrl);
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost()
            + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
            + uri.getPath()
            + (uri.getQuery() != null ? "?" + uri.getQuery() : "");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.url", jdbcUrl);
        if (uri.getUserInfo() != null) {
            String[] userInfo = uri.getUserInfo().split(":", 2);
            props.put("spring.datasource.username", userInfo[0]);
            if (userInfo.length > 1) {
                props.put("spring.datasource.password", userInfo[1]);
            }
        }

        environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
    }
}
