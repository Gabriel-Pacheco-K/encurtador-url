package com.gabrielpacheco.encurtador.controller;

import com.gabrielpacheco.encurtador.dto.UrlDtos.ShortenRequest;
import com.gabrielpacheco.encurtador.dto.UrlDtos.ShortenResponse;
import com.gabrielpacheco.encurtador.dto.UrlDtos.UrlStats;
import com.gabrielpacheco.encurtador.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urls")
@Tag(name = "Links")
public class UrlController {

    public static final String MANAGE_TOKEN_HEADER = "X-Manage-Token";

    private final ShortUrlService shortUrlService;

    public UrlController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Encurta uma URL, com apelido e data de expiração opcionais")
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request) {
        return shortUrlService.shorten(request);
    }

    @GetMapping("/{code}/stats")
    @Operation(summary = "Estatísticas do link: cliques por dia, origens, navegadores e dispositivos")
    public UrlStats stats(@PathVariable String code) {
        return shortUrlService.stats(code);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove o link. Exige o token de gerenciamento recebido na criação")
    public void delete(@PathVariable String code,
                       @RequestHeader(value = MANAGE_TOKEN_HEADER, required = false) String manageToken) {
        shortUrlService.delete(code, manageToken);
    }
}
