package com.prostock.controller;

import com.prostock.dto.AlertaResponse;
import com.prostock.service.AlertaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alertas")
@RequiredArgsConstructor
public class AlertaController {

    private final AlertaService alertaService;

    @GetMapping("/nao-lidos")
    public List<AlertaResponse> listarNaoLidos() {
        return alertaService.listarNaoLidos().stream().map(AlertaResponse::from).toList();
    }

    @GetMapping("/produto/{produtoId}")
    public List<AlertaResponse> listarPorProduto(@PathVariable Long produtoId) {
        return alertaService.listarPorProduto(produtoId).stream().map(AlertaResponse::from).toList();
    }

    @PatchMapping("/{id}/lido")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarComoLido(@PathVariable Long id) {
        alertaService.marcarComoLido(id);
    }

    @PostMapping("/verificar-obsolescencia")
    public List<String> verificarEstoqueObsoleto() {
        return alertaService.verificarEstoqueObsoleto().stream().map(p -> p.getSku() + " - " + p.getNome()).toList();
    }
}
