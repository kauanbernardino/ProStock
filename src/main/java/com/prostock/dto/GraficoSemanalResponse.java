package com.prostock.dto;

import java.time.LocalDate;

public record GraficoSemanalResponse(
        LocalDate dia,
        long totalEntradas,
        long totalSaidas
) {
}
