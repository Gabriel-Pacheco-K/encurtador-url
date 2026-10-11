package com.gabrielpacheco.encurtador.dto;

import java.time.LocalDate;

public record DailyClicks(LocalDate date, long clicks) {
}
