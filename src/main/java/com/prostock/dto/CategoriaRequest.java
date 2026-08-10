package com.prostock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
        @NotBlank String nome,
        @Size(max = 255) String descricao
) {
}
