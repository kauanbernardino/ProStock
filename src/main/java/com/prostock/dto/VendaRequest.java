package com.prostock.dto;

import com.prostock.model.enums.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record VendaRequest(
        @NotEmpty(message = "A venda precisa ter ao menos um item")
        @Valid
        List<ItemVendaRequest> itens,

        @NotNull(message = "Informe a forma de pagamento")
        FormaPagamento formaPagamento
) {
}
