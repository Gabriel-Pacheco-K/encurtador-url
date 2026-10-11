package com.gabrielpacheco.encurtador.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita quantos links cada IP pode criar por janela de tempo, para dificultar
 * o uso do encurtador como ferramenta de spam. Usa janela fixa em memória, que
 * é suficiente para uma única instância. Com várias instâncias, o contador
 * precisaria ficar no Redis.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.max-requests}") int maxRequests,
                           @Value("${app.rate-limit.window}") Duration window,
                           Clock clock) {
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/urls".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        String ip = request.getRemoteAddr();

        Window window = windows.compute(ip, (key, current) ->
                current == null || now - current.start >= windowMillis ? new Window(now, 1) : current.increment());

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - window.count)));

        if (window.count > maxRequests) {
            long retryAfterSeconds = Math.max(1, (window.start + windowMillis - now) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"title":"Muitas requisições","status":429,"detail":"Limite de criação de links atingido. Tente novamente em %d s."}"""
                    .formatted(retryAfterSeconds));
            return;
        }

        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> now - w.start >= windowMillis);
        }

        chain.doFilter(request, response);
    }

    private record Window(long start, int count) {
        Window increment() {
            return new Window(start, count + 1);
        }
    }
}
