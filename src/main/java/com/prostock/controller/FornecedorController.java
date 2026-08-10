package com.prostock.controller;

import com.prostock.dto.FornecedorRequest;
import com.prostock.dto.FornecedorResponse;
import com.prostock.service.FornecedorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fornecedores")
@RequiredArgsConstructor
public class FornecedorController {

    private final FornecedorService fornecedorService;

    @GetMapping
    public List<FornecedorResponse> listar() {
        return fornecedorService.listarTodos().stream().map(FornecedorResponse::from).toList();
    }

    @GetMapping("/{id}")
    public FornecedorResponse buscar(@PathVariable Long id) {
        return FornecedorResponse.from(fornecedorService.buscarPorId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FornecedorResponse criar(@Valid @RequestBody FornecedorRequest request) {
        return FornecedorResponse.from(fornecedorService.criar(request));
    }

    @PutMapping("/{id}")
    public FornecedorResponse atualizar(@PathVariable Long id, @Valid @RequestBody FornecedorRequest request) {
        return FornecedorResponse.from(fornecedorService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativar(@PathVariable Long id) {
        fornecedorService.inativar(id);
    }
}
