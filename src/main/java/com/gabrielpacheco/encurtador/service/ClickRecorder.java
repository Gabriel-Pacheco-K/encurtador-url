package com.gabrielpacheco.encurtador.service;

import com.gabrielpacheco.encurtador.domain.Click;
import com.gabrielpacheco.encurtador.repository.ClickRepository;
import com.gabrielpacheco.encurtador.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

/**
 * Registra cliques de forma assíncrona: quem acessa o link é redirecionado
 * na hora, sem esperar a gravação da estatística no banco.
 */
@Service
public class ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

    private final ClickRepository clickRepository;
    private final ShortUrlRepository shortUrlRepository;
    private final Clock clock;

    public ClickRecorder(ClickRepository clickRepository, ShortUrlRepository shortUrlRepository, Clock clock) {
        this.clickRepository = clickRepository;
        this.shortUrlRepository = shortUrlRepository;
        this.clock = clock;
    }

    @Async
    @Transactional
    public void record(Long shortUrlId, String referrer, String userAgent) {
        try {
            clickRepository.save(new Click(
                    shortUrlId,
                    Instant.now(clock),
                    referrerHost(referrer),
                    UserAgentParser.browser(userAgent),
                    UserAgentParser.device(userAgent)));
            shortUrlRepository.incrementClicks(shortUrlId);
        } catch (RuntimeException ex) {
            // Uma falha ao gravar a estatística nunca deve afetar o redirecionamento.
            log.warn("Falha ao registrar clique do link {}", shortUrlId, ex);
        }
    }

    /** Guarda só o domínio de origem, sem caminho nem parâmetros, que podem conter dados pessoais. */
    static String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(referrer.trim()).getHost();
            if (host == null) {
                return null;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
