package com.gabrielpacheco.encurtador.service;

import com.gabrielpacheco.encurtador.domain.ShortUrl;
import com.gabrielpacheco.encurtador.dto.DailyClicks;
import com.gabrielpacheco.encurtador.dto.RedirectTarget;
import com.gabrielpacheco.encurtador.dto.UrlDtos.ShortenRequest;
import com.gabrielpacheco.encurtador.dto.UrlDtos.ShortenResponse;
import com.gabrielpacheco.encurtador.dto.UrlDtos.UrlStats;
import com.gabrielpacheco.encurtador.exception.ApiException;
import com.gabrielpacheco.encurtador.repository.ClickRepository;
import com.gabrielpacheco.encurtador.repository.ShortUrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShortUrlService {

    public static final String REDIRECT_CACHE = "redirects";
    static final int STATS_DAYS = 30;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ClickRepository clickRepository;
    private final CodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final Clock clock;
    private final String baseUrl;

    public ShortUrlService(ShortUrlRepository shortUrlRepository,
                           ClickRepository clickRepository,
                           CodeGenerator codeGenerator,
                           UrlValidator urlValidator,
                           Clock clock,
                           @Value("${app.base-url}") String baseUrl) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickRepository = clickRepository;
        this.codeGenerator = codeGenerator;
        this.urlValidator = urlValidator;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        String url = urlValidator.normalize(request.url());
        String code = request.alias() != null ? reserveAlias(request.alias()) : uniqueRandomCode();

        ShortUrl saved = shortUrlRepository.save(new ShortUrl(code, url, codeGenerator.newToken(), request.expiresAt()));
        return new ShortenResponse(saved.getCode(), shortUrl(saved.getCode()), saved.getOriginalUrl(),
                saved.getCreatedAt(), saved.getExpiresAt(), saved.getManageToken());
    }

    /**
     * Caminho mais acessado da aplicação. O resultado fica no cache, então
     * redirecionamentos repetidos não consultam o banco.
     */
    @Cacheable(REDIRECT_CACHE)
    @Transactional(readOnly = true)
    public RedirectTarget resolve(String code) {
        ShortUrl url = shortUrlRepository.findByCode(code).orElseThrow(() -> ApiException.notFound(code));
        return new RedirectTarget(url.getId(), url.getOriginalUrl(), url.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public UrlStats stats(String code) {
        ShortUrl url = shortUrlRepository.findByCode(code).orElseThrow(() -> ApiException.notFound(code));
        Long id = url.getId();

        return new UrlStats(
                url.getCode(),
                shortUrl(url.getCode()),
                url.getOriginalUrl(),
                url.getCreatedAt(),
                url.getExpiresAt(),
                url.isExpired(Instant.now(clock)),
                url.getClickCount(),
                lastDays(id),
                clickRepository.topReferrers(id, PageRequest.of(0, 5)),
                clickRepository.countByBrowser(id),
                clickRepository.countByDevice(id));
    }

    @CacheEvict(value = REDIRECT_CACHE, key = "#code")
    @Transactional
    public void delete(String code, String manageToken) {
        ShortUrl url = shortUrlRepository.findByCode(code).orElseThrow(() -> ApiException.notFound(code));
        if (manageToken == null || !constantTimeEquals(url.getManageToken(), manageToken)) {
            throw ApiException.forbidden();
        }
        clickRepository.deleteByShortUrlId(url.getId());
        shortUrlRepository.delete(url);
    }

    /** Série dos últimos 30 dias, preenchendo com zero os dias sem cliques para o gráfico ficar contínuo. */
    private List<DailyClicks> lastDays(Long id) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate since = today.minusDays(STATS_DAYS - 1);

        Map<LocalDate, Long> counts = clickRepository.countByDay(id, since).stream()
                .collect(Collectors.toMap(DailyClicks::date, DailyClicks::clicks));

        return since.datesUntil(today.plusDays(1))
                .map(day -> new DailyClicks(day, counts.getOrDefault(day, 0L)))
                .toList();
    }

    private String reserveAlias(String alias) {
        urlValidator.checkAlias(alias);
        if (shortUrlRepository.existsByCode(alias)) {
            throw ApiException.aliasTaken(alias);
        }
        return alias;
    }

    private String uniqueRandomCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.newCode();
            if (!shortUrlRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código único após " + MAX_CODE_ATTEMPTS + " tentativas.");
    }

    private String shortUrl(String code) {
        return baseUrl + "/" + code;
    }

    /** Comparação em tempo constante, para não revelar o token por diferença de tempo de resposta. */
    private static boolean constantTimeEquals(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
