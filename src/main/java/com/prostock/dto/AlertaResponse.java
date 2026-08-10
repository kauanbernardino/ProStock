package com.prostock.dto;

import com.prostock.model.Alerta;
import com.prostock.model.enums.TipoAlerta;

import java.time.LocalDateTime;

public record AlertaResponse(
        Long id,
        Long produtoId,
        String produtoNome,
        String produtoSku,
        TipoAlerta tipo,
        String mensagem,
        LocalDateTime dataCriacao,
        boolean lido
) {
    public static AlertaResponse from(Alerta alerta) {
        return new AlertaResponse(
                alerta.getId(),
                alerta.getProduto().getId(),
                alerta.getProduto().getNome(),
                alerta.getProduto().getSku(),
                alerta.getTipo(),
                alerta.getMensagem(),
                alerta.getDataCriacao(),
                alerta.isLido()
        );
    }
}
