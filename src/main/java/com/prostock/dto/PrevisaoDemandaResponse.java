package com.prostock.dto;

import java.math.BigDecimal;

public record PrevisaoDemandaResponse(
        Long produtoId,
        String nome,
        String sku,
        long totalSaidasUltimos3Meses,
        BigDecimal mediaMensalDeSaida,
        BigDecimal previsaoProximoMes
) {
}
