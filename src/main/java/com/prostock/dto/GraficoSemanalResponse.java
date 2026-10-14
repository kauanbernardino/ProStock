package com.prostock.dto;

import java.time.LocalDate;

/** Um dia do gráfico semanal: total de peças e número de movimentações de entrada e de saída. */
public record GraficoSemanalResponse(
        LocalDate dia,
        long totalEntradas,
        long totalSaidas,
        long movimentacoesDeEntrada,
        long movimentacoesDeSaida
) {
}
