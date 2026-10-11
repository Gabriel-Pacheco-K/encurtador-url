package com.gabrielpacheco.encurtador.service;

import com.gabrielpacheco.encurtador.domain.ShortUrl;
import com.gabrielpacheco.encurtador.dto.DailyClicks;
import com.gabrielpacheco.encurtador.dto.UrlDtos.ShortenRequest;
import com.gabrielpacheco.encurtador.exception.ApiException;
import com.gabrielpacheco.encurtador.repository.ClickRepository;
import com.gabrielpacheco.encurtador.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private ClickRepository clickRepository;

    @Mock
    private CodeGenerator codeGenerator;

    private ShortUrlService service;

    @BeforeEach
    void setUp() {
        service = new ShortUrlService(shortUrlRepository, clickRepository, codeGenerator,
                new UrlValidator("https://encurta.dev"), Clock.fixed(NOW, ZoneOffset.UTC), "https://encurta.dev/");
    }

    @Test
    void retriesWhenGeneratedCodeAlreadyExists() {
        when(codeGenerator.newCode()).thenReturn("aaaaaaa", "bbbbbbb");
        when(codeGenerator.newToken()).thenReturn("token");
        when(shortUrlRepository.existsByCode("aaaaaaa")).thenReturn(true);
        when(shortUrlRepository.existsByCode("bbbbbbb")).thenReturn(false);
        when(shortUrlRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.shorten(new ShortenRequest("https://github.com", null, null));

        assertThat(response.code()).isEqualTo("bbbbbbb");
        assertThat(response.shortUrl()).isEqualTo("https://encurta.dev/bbbbbbb");
    }

    @Test
    void aliasAlreadyInUseIsRejected() {
        when(shortUrlRepository.existsByCode("meu-link")).thenReturn(true);

        assertThatThrownBy(() -> service.shorten(new ShortenRequest("https://github.com", "meu-link", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("já está em uso");
        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void deleteRequiresTheRightToken() {
        when(shortUrlRepository.findByCode("abc1234"))
                .thenReturn(Optional.of(new ShortUrl("abc1234", "https://github.com", "token-certo", null)));

        assertThatThrownBy(() -> service.delete("abc1234", "token-errado")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.delete("abc1234", null)).isInstanceOf(ApiException.class);
        verify(shortUrlRepository, never()).delete(any());
    }

    @Test
    void statsFillDaysWithoutClicksWithZero() {
        when(shortUrlRepository.findByCode("abc1234"))
                .thenReturn(Optional.of(new ShortUrl("abc1234", "https://github.com", "t", NOW.minusSeconds(60))));
        when(clickRepository.countByDay(any(), any())).thenReturn(List.of(
                new DailyClicks(LocalDate.of(2026, 10, 11), 7),
                new DailyClicks(LocalDate.of(2026, 10, 9), 3)));

        var stats = service.stats("abc1234");

        assertThat(stats.clicksByDay()).hasSize(ShortUrlService.STATS_DAYS);
        assertThat(stats.clicksByDay().getFirst().date()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(stats.clicksByDay().getLast()).isEqualTo(new DailyClicks(LocalDate.of(2026, 10, 11), 7));
        assertThat(stats.clicksByDay().get(27)).isEqualTo(new DailyClicks(LocalDate.of(2026, 10, 9), 3));
        assertThat(stats.clicksByDay().get(28).clicks()).isZero();
        assertThat(stats.expired()).isTrue();
    }

    @Test
    void unknownCodeIsNotFound() {
        when(shortUrlRepository.findByCode(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("naoexiste")).isInstanceOf(ApiException.class);
    }
}
