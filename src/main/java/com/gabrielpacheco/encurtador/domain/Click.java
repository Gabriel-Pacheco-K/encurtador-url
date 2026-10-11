package com.gabrielpacheco.encurtador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Entity
@Table(name = "clicks")
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_url_id", nullable = false)
    private Long shortUrlId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    /** Dia do clique (UTC), guardado à parte para agrupar por dia sem funções de data específicas do banco. */
    @Column(name = "clicked_on", nullable = false)
    private LocalDate clickedOn;

    @Column(length = 255)
    private String referrer;

    @Column(nullable = false, length = 30)
    private String browser;

    @Column(nullable = false, length = 30)
    private String device;

    protected Click() {
    }

    public Click(Long shortUrlId, Instant clickedAt, String referrer, String browser, String device) {
        this.shortUrlId = shortUrlId;
        this.clickedAt = clickedAt;
        this.clickedOn = LocalDate.ofInstant(clickedAt, ZoneOffset.UTC);
        this.referrer = referrer;
        this.browser = browser;
        this.device = device;
    }

    public Long getShortUrlId() {
        return shortUrlId;
    }

    public LocalDate getClickedOn() {
        return clickedOn;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getBrowser() {
        return browser;
    }

    public String getDevice() {
        return device;
    }
}
