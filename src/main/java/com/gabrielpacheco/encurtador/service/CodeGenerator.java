package com.gabrielpacheco.encurtador.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Gera códigos aleatórios em Base62. Com 7 caracteres são 62^7 ≈ 3,5 trilhões
 * de combinações, e os códigos não seguem uma sequência que permita adivinhar outros links.
 */
@Component
public class CodeGenerator {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static final int CODE_LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    public String newCode() {
        return randomString(CODE_LENGTH);
    }

    public String newToken() {
        return randomString(32);
    }

    private String randomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
