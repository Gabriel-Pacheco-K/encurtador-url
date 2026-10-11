package com.gabrielpacheco.encurtador.service;

import com.gabrielpacheco.encurtador.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator("https://encurta.dev");

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/Gabriel-Pacheco-K",
            "http://exemplo.com.br/caminho?busca=java&pagina=2#topo",
            "  https://exemplo.com  "
    })
    void acceptsHttpAndHttpsUrls(String url) {
        assertThat(validator.normalize(url)).isEqualTo(url.trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "ftp://arquivos.com/arquivo.zip",
            "exemplo.com",
            "https://semponto",
            "https://exemplo com.br"
    })
    void rejectsInvalidOrUnsafeUrls(String url) {
        assertThatThrownBy(() -> validator.normalize(url)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsLinksToItself() {
        assertThatThrownBy(() -> validator.normalize("https://encurta.dev/aZ3kP9q"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("próprio encurtador");
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "API", "actuator", "swagger-ui"})
    void rejectsReservedAliases(String alias) {
        assertThatThrownBy(() -> validator.checkAlias(alias)).isInstanceOf(ApiException.class);
    }
}
