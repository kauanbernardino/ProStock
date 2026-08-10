package com.prostock.dto;

import com.prostock.model.Movimentacao;
import com.prostock.model.enums.TipoMovimentacao;

import java.time.LocalDateTime;

public record MovimentacaoResponse(
        Long id,
        Long produtoId,
        String produtoNome,
        String produtoSku,
        Long usuarioId,
        String usuarioNome,
        String motivoDescricao,
        TipoMovimentacao tipo,
        Integer quantidade,
        LocalDateTime dataHora,
        String observacao
) {
    public static MovimentacaoResponse from(Movimentacao movimentacao) {
        return new MovimentacaoResponse(
                movimentacao.getId(),
                movimentacao.getProduto().getId(),
                movimentacao.getProduto().getNome(),
                movimentacao.getProduto().getSku(),
                movimentacao.getUsuario().getId(),
                movimentacao.getUsuario().getNome(),
                movimentacao.getMotivo().getDescricao(),
                movimentacao.getTipo(),
                movimentacao.getQuantidade(),
                movimentacao.getDataHora(),
                movimentacao.getObservacao()
        );
    }
}
