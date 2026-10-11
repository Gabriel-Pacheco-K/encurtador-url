package com.gabrielpacheco.encurtador.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.encurtador.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa a aplicação de ponta a ponta: HTTP, validação, cache, registro assíncrono de cliques e migrações Flyway.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EncurtadorIntegrationTest {

    private static final String CHROME_ANDROID =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/129.0 Mobile Safari/537.36";
    private static final String FIREFOX_DESKTOP =
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @SpyBean
    private ShortUrlRepository shortUrlRepository;

    @Test
    void shortenRedirectAndCountClicks() throws Exception {
        JsonNode created = shorten(Map.of("url", "https://github.com/Gabriel-Pacheco-K"));
        String code = created.get("code").asText();

        mvc.perform(get("/" + code).header("User-Agent", CHROME_ANDROID).header("Referer", "https://www.linkedin.com/feed/123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://github.com/Gabriel-Pacheco-K"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/" + code).header("User-Agent", CHROME_ANDROID).header("Referer", "https://linkedin.com/in/alguem"));
        mvc.perform(get("/" + code).header("User-Agent", FIREFOX_DESKTOP));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get("/api/urls/" + code + "/stats"))
                        .andExpect(jsonPath("$.totalClicks").value(3)));

        mvc.perform(get("/api/urls/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clicksByDay", hasSize(30)))
                .andExpect(jsonPath("$.clicksByDay[29].clicks").value(3))
                .andExpect(jsonPath("$.topReferrers[0].key").value("linkedin.com"))
                .andExpect(jsonPath("$.topReferrers[0].count").value(2))
                .andExpect(jsonPath("$.topReferrers[1].key").value("Direto"))
                .andExpect(jsonPath("$.browsers[0].key").value("Chrome"))
                .andExpect(jsonPath("$.devices[0].key").value("Celular"));
    }

    @Test
    void repeatedRedirectsAreServedFromCache() throws Exception {
        String code = shorten(Map.of("url", "https://spring.io")).get("code").asText();

        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/" + code)).andExpect(status().isFound());
        }

        verify(shortUrlRepository, times(1)).findByCode(code);
    }

    @Test
    void customAliasWorksAndCannotBeReused() throws Exception {
        JsonNode created = shorten(Map.of("url", "https://gabriel-pacheco-k.github.io", "alias", "meu-portfolio"));

        org.assertj.core.api.Assertions.assertThat(created.get("shortUrl").asText()).isEqualTo("http://encurta.test/meu-portfolio");
        mvc.perform(get("/meu-portfolio")).andExpect(status().isFound());

        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", "https://outro.com", "alias", "meu-portfolio"))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", "javascript:alert(1)"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("URL inválida"));

        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", "https://ok.com", "alias", "a b"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.alias").exists());

        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", "https://ok.com", "expiresAt", "2020-01-01T00:00:00Z"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expiresAt").exists());
    }

    @Test
    void expiredLinkReturns410() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("url", "https://exemplo.com");
        request.put("expiresAt", Instant.now().plusMillis(800).toString());
        String code = shorten(request).get("code").asText();

        mvc.perform(get("/" + code)).andExpect(status().isFound());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get("/" + code)).andExpect(status().isGone()));
        mvc.perform(get("/api/urls/" + code + "/stats")).andExpect(jsonPath("$.expired").value(true));
    }

    @Test
    void deleteNeedsManageTokenAndEvictsCache() throws Exception {
        JsonNode created = shorten(Map.of("url", "https://exemplo.com"));
        String code = created.get("code").asText();
        String token = created.get("manageToken").asText();

        mvc.perform(get("/" + code)).andExpect(status().isFound());

        mvc.perform(delete("/api/urls/" + code)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/urls/" + code).header("X-Manage-Token", "errado")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/urls/" + code).header("X-Manage-Token", token)).andExpect(status().isNoContent());

        mvc.perform(get("/" + code)).andExpect(status().isNotFound());
    }

    @Test
    void unknownCodeReturns404AndHomePageIsServed() throws Exception {
        mvc.perform(get("/naoexiste")).andExpect(status().isNotFound());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    void rateLimitHeadersArePresent() throws Exception {
        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", "https://exemplo.com"))))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(jsonPath("$.shortUrl", startsWith("http://encurta.test/")));
    }

    private JsonNode shorten(Map<String, ?> request) throws Exception {
        String response = mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
