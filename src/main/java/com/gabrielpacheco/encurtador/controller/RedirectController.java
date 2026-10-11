package com.gabrielpacheco.encurtador.controller;

import com.gabrielpacheco.encurtador.dto.RedirectTarget;
import com.gabrielpacheco.encurtador.exception.ApiException;
import com.gabrielpacheco.encurtador.service.ClickRecorder;
import com.gabrielpacheco.encurtador.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

@RestController
@Tag(name = "Redirecionamento")
public class RedirectController {

    private final ShortUrlService shortUrlService;
    private final ClickRecorder clickRecorder;
    private final Clock clock;

    public RedirectController(ShortUrlService shortUrlService, ClickRecorder clickRecorder, Clock clock) {
        this.shortUrlService = shortUrlService;
        this.clickRecorder = clickRecorder;
        this.clock = clock;
    }

    /**
     * Usa 302 e no-store de propósito: com 301, o navegador guardaria o redirecionamento
     * e os próximos cliques não chegariam ao servidor, o que deixaria as estatísticas incompletas.
     */
    @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
    @Operation(summary = "Redireciona para a URL original e registra o clique")
    public ResponseEntity<Void> redirect(@PathVariable String code,
                                         @RequestHeader(value = HttpHeaders.REFERER, required = false) String referrer,
                                         @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        RedirectTarget target = shortUrlService.resolve(code);
        if (target.isExpired(Instant.now(clock))) {
            throw ApiException.expired(code);
        }

        clickRecorder.record(target.id(), referrer, userAgent);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target.originalUrl()))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
