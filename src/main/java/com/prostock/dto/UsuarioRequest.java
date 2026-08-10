package com.prostock.dto;

import com.prostock.model.enums.Cargo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @Size(min = 6, message = "A senha deve ter ao menos 6 caracteres") String senha,
        @NotNull Cargo cargo
) {
}
