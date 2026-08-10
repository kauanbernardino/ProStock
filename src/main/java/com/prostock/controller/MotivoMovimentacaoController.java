package com.prostock.controller;

import com.prostock.model.MotivoMovimentacao;
import com.prostock.service.MotivoMovimentacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/motivos")
@RequiredArgsConstructor
public class MotivoMovimentacaoController {

    private final MotivoMovimentacaoService motivoMovimentacaoService;

    @GetMapping
    public List<MotivoMovimentacao> listar() {
        return motivoMovimentacaoService.listarTodos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MotivoMovimentacao criar(@RequestBody Map<String, String> body) {
        return motivoMovimentacaoService.criar(body.get("descricao"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id) {
        motivoMovimentacaoService.remover(id);
    }
}
