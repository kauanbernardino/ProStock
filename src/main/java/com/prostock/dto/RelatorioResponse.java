package com.prostock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RelatorioResponse(
        LocalDate periodoInicio,
        LocalDate periodoFim,
        BigDecimal valorTotalEmEstoque,
        BigDecimal valorMovimentado,
        long totalEntradas,
        long totalSaidas,
        BigDecimal giroMedio,
        List<RelatorioItemResponse> itens
) {
}
