package com.gabrielpacheco.encurtador.exception;

import org.springframework.http.HttpStatus;

/**
 * Erro de negócio que já sabe qual status HTTP representa.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    private ApiException(HttpStatus status, String title, String message) {
        super(message);
        this.status = status;
        this.title = title;
    }

    public static ApiException notFound(String code) {
        return new ApiException(HttpStatus.NOT_FOUND, "Link não encontrado", "Nenhum link com o código '" + code + "'.");
    }

    public static ApiException expired(String code) {
        return new ApiException(HttpStatus.GONE, "Link expirado", "O link '" + code + "' expirou.");
    }

    public static ApiException aliasTaken(String alias) {
        return new ApiException(HttpStatus.CONFLICT, "Apelido indisponível", "O apelido '" + alias + "' já está em uso.");
    }

    public static ApiException invalidUrl(String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, "URL inválida", reason);
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "Acesso negado", "Token de gerenciamento inválido.");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
