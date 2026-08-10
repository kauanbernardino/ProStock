package com.prostock.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Gera o código Pix "copia e cola" (BR Code / EMV QR Code estático) seguindo
 * o padrão do Banco Central. Isso apenas monta uma string-alvo de pagamento
 * para o cliente colar no próprio aplicativo do banco — o ProStock nunca
 * processa, recebe ou confirma o pagamento em si.
 */
@Service
public class PixService {

    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}");

    @Value("${app.pix.chave}")
    private String chave;

    @Value("${app.pix.nome-recebedor}")
    private String nomeRecebedor;

    @Value("${app.pix.cidade}")
    private String cidade;

    public String gerarCopiaECola(BigDecimal valor, String txid) {
        String nome = normalizarEEncurtar(nomeRecebedor, 25);
        String cidadeNormalizada = normalizarEEncurtar(cidade, 15);
        String identificador = (txid == null || txid.isBlank()) ? "***" : normalizarEEncurtar(txid, 25);

        StringBuilder payload = new StringBuilder();
        payload.append(tlv("00", "01"));
        payload.append(tlv("01", "12"));
        payload.append(tlv("26", tlv("00", "BR.GOV.BCB.PIX") + tlv("01", chave)));
        payload.append(tlv("52", "0000"));
        payload.append(tlv("53", "986"));
        payload.append(tlv("54", valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
        payload.append(tlv("58", "BR"));
        payload.append(tlv("59", nome));
        payload.append(tlv("60", cidadeNormalizada));
        payload.append(tlv("62", tlv("05", identificador)));
        payload.append("6304");

        return payload + crc16(payload.toString());
    }

    private String tlv(String id, String valor) {
        return id + String.format("%02d", valor.length()) + valor;
    }

    private String normalizarEEncurtar(String texto, int tamanhoMaximo) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD);
        semAcento = DIACRITICOS.matcher(semAcento).replaceAll("");
        semAcento = semAcento.replaceAll("[^A-Za-z0-9 ]", "").trim();
        return semAcento.length() > tamanhoMaximo ? semAcento.substring(0, tamanhoMaximo) : semAcento;
    }

    /**
     * CRC16/CCITT-FALSE (poly 0x1021, init 0xFFFF) — checksum exigido pelo
     * padrão EMV do Pix ao final do payload.
     */
    String crc16(String payload) {
        int crc = 0xFFFF;
        for (byte b : payload.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
