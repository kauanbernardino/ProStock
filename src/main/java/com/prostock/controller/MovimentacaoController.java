package com.prostock.controller;

import com.prostock.dto.MovimentacaoRequest;
import com.prostock.dto.MovimentacaoResponse;
import com.prostock.security.UsuarioPrincipal;
import com.prostock.service.MovimentacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/movimentacoes")
@RequiredArgsConstructor
public class MovimentacaoController {

    private final MovimentacaoService movimentacaoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovimentacaoResponse registrar(@Valid @RequestBody MovimentacaoRequest request,
                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        return MovimentacaoResponse.from(movimentacaoService.registrar(request, principal.getUsuario()));
    }

    @GetMapping("/produto/{produtoId}")
    public List<MovimentacaoResponse> historicoDoProduto(@PathVariable Long produtoId) {
        return movimentacaoService.historicoDoProduto(produtoId).stream().map(MovimentacaoResponse::from).toList();
    }

    @GetMapping
    public List<MovimentacaoResponse> listarPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        return movimentacaoService.listarPorPeriodo(inicio, fim).stream().map(MovimentacaoResponse::from).toList();
    }
}
