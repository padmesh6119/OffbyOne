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
        String databaseUrl = env.getProperty("spring.datasource.url", "");
        if (!databaseUrl.startsWith("jdbc:postgresql:"))
            throw new IllegalStateException("Render requires a persistent PostgreSQL datasource.");
        String databaseUser = env.getProperty("spring.datasource.username", "");
        if (databaseUrl.contains(".pooler.supabase.com") && !databaseUser.contains("."))
            throw new IllegalStateException("Supabase shared pooler requires username postgres.<project-ref>. Remove SPRING_DATASOURCE_USERNAME to use the configured default, or set it to the username shown under Supabase Connect > Session pooler.");
    }
}
