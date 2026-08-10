package com.prostock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemVendaRequest(
        @NotNull Long produtoId,
        @NotNull @Positive Integer quantidade
) {
}
