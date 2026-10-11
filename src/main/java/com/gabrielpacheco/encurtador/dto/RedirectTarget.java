package com.gabrielpacheco.encurtador.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * O mínimo necessário para redirecionar. É o que fica no cache (Redis),
 * para que o redirecionamento não precise consultar o PostgreSQL.
 */
public record RedirectTarget(Long id, String originalUrl, Instant expiresAt) implements Serializable {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
