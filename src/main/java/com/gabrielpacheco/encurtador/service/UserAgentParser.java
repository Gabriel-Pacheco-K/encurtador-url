package com.gabrielpacheco.encurtador.service;

import java.util.Locale;

/**
 * Classificação simples de navegador e dispositivo a partir do User-Agent.
 * A ordem das verificações importa: o Edge e o Opera também se identificam como "Chrome",
 * e o Chrome também se identifica como "Safari".
 */
public final class UserAgentParser {

    private UserAgentParser() {
    }

    public static String browser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Desconhecido";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")) return "Bot";
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("opr/") || ua.contains("opera")) return "Opera";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("chrome/") || ua.contains("crios/")) return "Chrome";
        if (ua.contains("safari/")) return "Safari";
        if (ua.contains("curl/")) return "curl";
        return "Outro";
    }

    public static String device(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Desconhecido";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("ipad") || ua.contains("tablet")) return "Tablet";
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return "Celular";
        return "Desktop";
    }
}
