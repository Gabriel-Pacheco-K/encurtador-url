package com.gabrielpacheco.encurtador.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class UrlDtos {

    private UrlDtos() {
    }

    public record ShortenRequest(
            @NotBlank @Size(max = 2048) String url,
            @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "deve ter de 3 a 32 caracteres: letras, números, '-' ou '_'")
            String alias,
            @Future Instant expiresAt) {
    }

    /** O manageToken só é exibido na criação: é ele que permite apagar o link depois. */
    public record ShortenResponse(
            String code,
            String shortUrl,
            String originalUrl,
            Instant createdAt,
            Instant expiresAt,
            String manageToken) {
    }

    public record UrlStats(
            String code,
            String shortUrl,
            String originalUrl,
            Instant createdAt,
            Instant expiresAt,
            boolean expired,
            long totalClicks,
            List<DailyClicks> clicksByDay,
            List<CountByKey> topReferrers,
            List<CountByKey> browsers,
            List<CountByKey> devices) {
    }
}
