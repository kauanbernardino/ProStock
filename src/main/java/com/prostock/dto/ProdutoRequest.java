package com.prostock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProdutoRequest(
        @NotBlank String nome,
        @NotBlank String sku,
        String descricao,
        @NotNull @PositiveOrZero BigDecimal preco,
        @NotNull @PositiveOrZero Integer quantidadeAtual,
        @NotNull @PositiveOrZero Integer estoqueMinimo,
        @NotNull @PositiveOrZero Integer estoqueMaximo,
        String localizacaoFisica,
        @NotNull Long categoriaId,
        @NotNull Long fornecedorId
) {
}
