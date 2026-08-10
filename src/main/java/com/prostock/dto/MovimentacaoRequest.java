package com.prostock.dto;

import com.prostock.model.enums.TipoMovimentacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MovimentacaoRequest(
        @NotNull Long produtoId,
        @NotNull Long motivoId,
        @NotNull TipoMovimentacao tipo,
        @NotNull @Positive Integer quantidade,
        @Size(max = 255) String observacao
) {
}
