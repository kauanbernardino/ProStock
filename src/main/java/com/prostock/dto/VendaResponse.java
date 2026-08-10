package com.prostock.dto;

import com.prostock.model.Venda;
import com.prostock.model.enums.FormaPagamento;
import com.prostock.model.enums.StatusVenda;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VendaResponse(
        Long id,
        LocalDateTime dataHora,
        String usuarioNome,
        List<ItemVendaResponse> itens,
        BigDecimal valorTotal,
        FormaPagamento formaPagamento,
        StatusVenda status,
        LocalDateTime canceladaEm,
        String canceladoPorNome,
        String pixCopiaECola
) {
    public static VendaResponse from(Venda venda, String pixCopiaECola) {
        List<ItemVendaResponse> itens = venda.getItens().stream()
                .map(ItemVendaResponse::from)
                .toList();

        return new VendaResponse(
                venda.getId(),
                venda.getDataHora(),
                venda.getUsuario().getNome(),
                itens,
                venda.getValorTotal(),
                venda.getFormaPagamento(),
                venda.getStatus(),
                venda.getCanceladaEm(),
                venda.getCanceladoPor() != null ? venda.getCanceladoPor().getNome() : null,
                pixCopiaECola
        );
    }
}
