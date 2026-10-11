package com.gabrielpacheco.encurtador.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    @ParameterizedTest(name = "{1} / {2}")
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36 | Chrome | Desktop",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/129.0 Safari/537.36 Edg/129.0 | Edge | Desktop",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 Version/17.0 Safari/605.1.15 | Safari | Desktop",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1 | Safari | Celular",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/129.0 Mobile Safari/537.36 | Chrome | Celular",
            "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Safari/604.1 | Safari | Tablet",
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0 | Firefox | Desktop",
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html) | Bot | Desktop",
            "curl/8.5.0 | curl | Desktop"
    })
    void classifiesBrowserAndDevice(String userAgent, String browser, String device) {
        assertThat(UserAgentParser.browser(userAgent)).isEqualTo(browser);
        assertThat(UserAgentParser.device(userAgent)).isEqualTo(device);
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "'   '"})
    void blankUserAgentIsUnknown(String userAgent) {
        assertThat(UserAgentParser.browser(userAgent)).isEqualTo("Desconhecido");
        assertThat(UserAgentParser.device(null)).isEqualTo("Desconhecido");
    }
}
