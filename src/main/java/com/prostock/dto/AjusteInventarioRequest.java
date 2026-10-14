package com.prostock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** UC07: quantidade encontrada na contagem física. */
public record AjusteInventarioRequest(
        @NotNull @PositiveOrZero Integer quantidade,
        @Size(max = 150) String observacao
) {
}
