package com.prostock.dto;

import java.math.BigDecimal;

public record SugestaoCompraResponse(
        Long produtoId,
        String nome,
        String sku,
        Integer quantidadeAtual,
        Integer estoqueMinimo,
        Integer estoqueMaximo,
        BigDecimal mediaMensalDeSaida,
        int quantidadeSugerida,
        String fornecedorNome
) {
}
