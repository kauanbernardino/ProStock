package com.prostock.dto;

import com.prostock.model.ProdutoImagem;

public record ProdutoImagemResponse(
        Long id,
        String url,
        Integer ordem
) {
    public static ProdutoImagemResponse from(ProdutoImagem imagem) {
        return new ProdutoImagemResponse(imagem.getId(), imagem.getCaminho(), imagem.getOrdem());
    }
}
