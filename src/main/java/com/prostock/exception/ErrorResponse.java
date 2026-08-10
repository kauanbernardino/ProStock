package com.prostock.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        List<String> detalhes
) {
    public static ErrorResponse of(int status, String erro, String mensagem) {
        return new ErrorResponse(LocalDateTime.now(), status, erro, mensagem, List.of());
    }

    public static ErrorResponse of(int status, String erro, String mensagem, List<String> detalhes) {
        return new ErrorResponse(LocalDateTime.now(), status, erro, mensagem, detalhes);
    }
}
