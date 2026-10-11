package com.gabrielpacheco.encurtador.service;

import com.gabrielpacheco.encurtador.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlValidator {

    /** Apelidos que colidiriam com rotas da própria aplicação. */
    private static final Set<String> RESERVED_ALIASES = Set.of("api", "actuator", "swagger-ui", "v3", "error", "index");

    private final String ownHost;

    public UrlValidator(@Value("${app.base-url}") String baseUrl) {
        this.ownHost = URI.create(baseUrl).getHost();
    }

    /**
     * Aceita apenas http/https com host válido, e recusa links para o próprio
     * encurtador, o que evitaria cadeias de redirecionamento infinitas.
     */
    public String normalize(String rawUrl) {
        String url = rawUrl.trim();
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException ex) {
            throw ApiException.invalidUrl("Não foi possível interpretar a URL.");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw ApiException.invalidUrl("Apenas URLs http:// ou https:// são aceitas.");
        }
        if (uri.getHost() == null || !uri.getHost().contains(".") && !uri.getHost().equals("localhost")) {
            throw ApiException.invalidUrl("A URL precisa ter um domínio válido.");
        }
        if (uri.getHost().equalsIgnoreCase(ownHost)) {
            throw ApiException.invalidUrl("Não é possível encurtar um link do próprio encurtador.");
        }
        return url;
    }

    public void checkAlias(String alias) {
        if (RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
            throw ApiException.aliasTaken(alias);
        }
    }
}
