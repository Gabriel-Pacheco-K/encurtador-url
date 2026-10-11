package com.gabrielpacheco.encurtador.controller;

import com.gabrielpacheco.encurtador.web.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    /** Relógio que o teste pode avançar manualmente. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-11T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final RateLimitFilter filter = new RateLimitFilter(3, Duration.ofMinutes(1), clock);

    @Test
    void blocksAfterLimitAndResetsAfterWindow() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(create("10.0.0.1").getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = create("10.0.0.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");

        assertThat(create("10.0.0.2").getStatus()).as("outro IP não é afetado").isEqualTo(200);

        clock.advance(Duration.ofMinutes(1));
        assertThat(create("10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void redirectsAreNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/abc1234");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse create(String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/urls");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
