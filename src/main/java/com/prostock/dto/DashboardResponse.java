package com.prostock.dto;

import java.math.BigDecimal;

public record DashboardResponse(
        BigDecimal valorTotalEmEstoque,
        Long quantidadeTotalEmEstoque,
        long totalProdutos,
        long produtosAbaixoDoMinimo,
        long produtosEmRuptura,
        long produtosAcimaDoMaximo,
        long alertasNaoLidos
) {
}
