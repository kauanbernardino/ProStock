package com.prostock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FornecedorRequest(
        @NotBlank String razaoSocial,
        @NotBlank @Pattern(regexp = "\\d{14}", message = "CNPJ deve conter 14 dígitos numéricos") String cnpj,
        String contato
) {
}
