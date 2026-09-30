package com.prostock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * UC07: quantidade real encontrada na contagem física do produto, com uma
 * observação opcional (ex.: "contagem de fim de mês").
 */
public record AjusteInventarioRequest(
        @NotNull @PositiveOrZero Integer quantidade,
        @Size(max = 150) String observacao
) {
}
