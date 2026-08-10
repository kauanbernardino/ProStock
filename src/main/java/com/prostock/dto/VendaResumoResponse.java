package com.prostock.dto;

import com.prostock.model.Venda;
import com.prostock.model.enums.FormaPagamento;
import com.prostock.model.enums.StatusVenda;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VendaResumoResponse(
        Long id,
        LocalDateTime dataHora,
        String usuarioNome,
        int quantidadeItens,
        BigDecimal valorTotal,
        FormaPagamento formaPagamento,
        StatusVenda status
) {
    public static VendaResumoResponse from(Venda venda) {
        return new VendaResumoResponse(
                venda.getId(),
                venda.getDataHora(),
                venda.getUsuario().getNome(),
                venda.getItens().size(),
                venda.getValorTotal(),
                venda.getFormaPagamento(),
                venda.getStatus()
        );
    }
}
