package com.prostock.dto;

import com.prostock.model.Produto;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoResponse(
        Long id,
        String nome,
        String sku,
        String descricao,
        BigDecimal preco,
        Integer quantidadeAtual,
        Integer estoqueMinimo,
        Integer estoqueMaximo,
        String localizacaoFisica,
        Long categoriaId,
        String categoriaNome,
        Long fornecedorId,
        String fornecedorNome,
        boolean ativo,
        String statusEstoque,
        List<ProdutoImagemResponse> imagens
) {
    public static ProdutoResponse from(Produto produto) {
        String status;
        if (produto.getQuantidadeAtual() <= 0) {
            status = "RUPTURA";
        } else if (produto.getQuantidadeAtual() <= produto.getEstoqueMinimo()) {
            status = "BAIXO";
        } else if (produto.getQuantidadeAtual() >= produto.getEstoqueMaximo()) {
            status = "EXCESSO";
        } else {
            status = "NORMAL";
        }

        List<ProdutoImagemResponse> imagens = produto.getImagens().stream()
                .map(ProdutoImagemResponse::from)
                .toList();

        return new ProdutoResponse(
                produto.getId(),
                produto.getNome(),
                produto.getSku(),
                produto.getDescricao(),
                produto.getPreco(),
                produto.getQuantidadeAtual(),
                produto.getEstoqueMinimo(),
                produto.getEstoqueMaximo(),
                produto.getLocalizacaoFisica(),
                produto.getCategoria().getId(),
                produto.getCategoria().getNome(),
                produto.getFornecedor().getId(),
                produto.getFornecedor().getRazaoSocial(),
                produto.isAtivo(),
                status,
                imagens
        );
    }
}
