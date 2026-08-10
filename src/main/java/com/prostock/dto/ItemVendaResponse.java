package com.prostock.dto;

import com.prostock.model.ItemVenda;

import java.math.BigDecimal;

public record ItemVendaResponse(
        Long produtoId,
        String produtoNome,
        String produtoSku,
        Integer quantidade,
        BigDecimal precoUnitario,
        BigDecimal subtotal
) {
    public static ItemVendaResponse from(ItemVenda item) {
        return new ItemVendaResponse(
                item.getProduto().getId(),
                item.getProduto().getNome(),
                item.getProduto().getSku(),
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()))
        );
    }
}
