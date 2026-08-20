package com.prostock.dto;

import java.math.BigDecimal;

public record RelatorioItemResponse(
        Long produtoId,
        String sku,
        String nome,
        String categoriaNome,
        int quantidadeAtual,
        long entradas,
        long saidas,
        BigDecimal giroPercentual,
        BigDecimal valorEmEstoque
) {
}
