package com.prostock.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PixServiceTest {

    private final PixService pixService = new PixService();

    @Test
    void crc16_deveBaterComVetorDeTestePadraoCcittFalse() {
        // Vetor de referência do CRC-16/CCITT-FALSE: CRC16("123456789") = 0x29B1.
        assertThat(pixService.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    void gerarCopiaECola_deveConterOsCamposObrigatoriosEChecksumConsistente() {
        ReflectionTestUtils.setField(pixService, "chave", "12345678000199");
        ReflectionTestUtils.setField(pixService, "nomeRecebedor", "ProStock Autopecas");
        ReflectionTestUtils.setField(pixService, "cidade", "SAO PAULO");

        String copiaECola = pixService.gerarCopiaECola(new BigDecimal("24.90"), "VDA1");

        assertThat(copiaECola).startsWith("000201");
        assertThat(copiaECola).contains("BR.GOV.BCB.PIX");
        assertThat(copiaECola).contains("12345678000199");
        assertThat(copiaECola).contains("5802BR");
        assertThat(copiaECola).contains("540524.90");

        String semChecksum = copiaECola.substring(0, copiaECola.length() - 4);
        String checksumInformado = copiaECola.substring(copiaECola.length() - 4);
        assertThat(pixService.crc16(semChecksum)).isEqualTo(checksumInformado);
    }
}
