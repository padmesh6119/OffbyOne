package com.offbyone.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.util.Arrays;

@Configuration
public class RenderConfiguration {
    public RenderConfiguration(Environment env) {
        if (!"true".equals(env.getProperty("RENDER"))) return;
        if (Arrays.stream(env.getActiveProfiles()).anyMatch(p -> p.equals("preview") || p.equals("local")))
            throw new IllegalStateException("Do not activate preview or local profiles on Render. Use the managed PostgreSQL configuration.");
        if (!env.getProperty("spring.datasource.url", "").startsWith("jdbc:postgresql:"))
            throw new IllegalStateException("Render requires a persistent PostgreSQL datasource.");
    }
}
