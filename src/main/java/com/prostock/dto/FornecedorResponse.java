package com.prostock.dto;

import com.prostock.model.Fornecedor;

public record FornecedorResponse(
        Long id,
        String razaoSocial,
        String cnpj,
        String contato,
        boolean ativo
) {
    public static FornecedorResponse from(Fornecedor fornecedor) {
        return new FornecedorResponse(
                fornecedor.getId(),
                fornecedor.getRazaoSocial(),
                fornecedor.getCnpj(),
                fornecedor.getContato(),
                fornecedor.isAtivo()
        );
    }
}
