package com.prostock.dto;

import java.time.LocalDate;

/**
 * Um dia do gráfico "Movimentações da semana".
 *
 * totalEntradas / totalSaidas = soma das quantidades (peças); movimentacoesDeEntrada /
 * movimentacoesDeSaida = quantos lançamentos houve (uma venda de 3 itens gera 3
 * movimentações de saída).
 */
public record GraficoSemanalResponse(
        LocalDate dia,
        long totalEntradas,
        long totalSaidas,
        long movimentacoesDeEntrada,
        long movimentacoesDeSaida
) {
}
