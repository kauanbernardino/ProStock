package com.prostock.dto;

import com.prostock.model.Usuario;
import com.prostock.model.enums.Cargo;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        Cargo cargo,
        boolean ativo,
        LocalDateTime criadoEm
) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getCargo(),
                usuario.isAtivo(),
                usuario.getCriadoEm()
        );
    }
}
