package com.gabrielpacheco.encurtador.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Clock;
import java.time.Duration;

import static com.gabrielpacheco.encurtador.service.ShortUrlService.REDIRECT_CACHE;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Encurtador de URL",
        version = "1.0.0",
        description = "Encurta links e acompanha os cliques: por dia, origem, navegador e dispositivo.",
        contact = @Contact(name = "Gabriel Pacheco Kiesel", url = "https://gabriel-pacheco-k.github.io")))
public class AppConfig {

    /** Relógio injetável: permite testar expiração sem depender da hora real. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Entradas de cache expiram sozinhas, para o Redis não crescer sem limite. */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer(@Value("${app.cache.ttl}") Duration ttl) {
        return builder -> builder.withCacheConfiguration(REDIRECT_CACHE,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl).disableCachingNullValues());
    }
}
