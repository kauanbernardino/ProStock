package com.prostock.controller;

import com.prostock.dto.AjusteInventarioRequest;
import com.prostock.dto.PaginaResponse;
import com.prostock.dto.ProdutoRequest;
import com.prostock.dto.ProdutoResponse;
import com.prostock.security.UsuarioPrincipal;
import com.prostock.service.ProdutoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    /**
     * Sem parâmetro: todos os produtos ativos. Com "nome": busca pelo texto no
     * nome, no SKU ou na aplicação (o parâmetro manteve o nome por compatibilidade).
     */
    @GetMapping
    public List<ProdutoResponse> listar(@RequestParam(required = false) String nome) {
        var produtos = (nome == null || nome.isBlank())
                ? produtoService.listarTodos()
                : produtoService.buscar(nome);
        return produtos.stream().map(ProdutoResponse::from).toList();
    }

    /**
     * Versão paginada da listagem, usada pela tela de cadastro de produtos
     * para não carregar o catálogo inteiro de uma vez.
     */
    @GetMapping("/pagina")
    public PaginaResponse<ProdutoResponse> listarPaginado(
            @RequestParam(required = false) String nome,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by("nome").ascending());
        return PaginaResponse.from(produtoService.listarPaginado(nome, pageable), ProdutoResponse::from);
    }

    @GetMapping("/{id}")
    public ProdutoResponse buscar(@PathVariable Long id) {
        return ProdutoResponse.from(produtoService.buscarPorId(id));
    }

    @GetMapping("/abaixo-do-minimo")
    public List<ProdutoResponse> listarAbaixoDoMinimo() {
        return produtoService.listarAbaixoDoEstoqueMinimo().stream().map(ProdutoResponse::from).toList();
    }

    @GetMapping("/ruptura")
    public List<ProdutoResponse> listarEmRuptura() {
        return produtoService.listarEmRuptura().stream().map(ProdutoResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoResponse criar(@Valid @RequestBody ProdutoRequest request) {
        return ProdutoResponse.from(produtoService.criar(request));
    }

    @PutMapping("/{id}")
    public ProdutoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ProdutoRequest request) {
        return ProdutoResponse.from(produtoService.atualizar(id, request));
    }

    /**
     * UC07: ajuste de inventário (contagem física). Restrito ao Administrador
     * pela regra de PATCH /api/produtos/** do SecurityConfig.
     */
    @PatchMapping("/{id}/ajuste-inventario")
    public ProdutoResponse ajustarInventario(@PathVariable Long id,
                                             @Valid @RequestBody AjusteInventarioRequest request,
                                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ProdutoResponse.from(produtoService.ajustarInventario(
                id, request.quantidade(), request.observacao(), principal.getUsuario()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativar(@PathVariable Long id) {
        produtoService.inativar(id);
    }

    @GetMapping("/inativos")
    public List<ProdutoResponse> listarInativos() {
        return produtoService.listarInativos().stream().map(ProdutoResponse::from).toList();
    }

    @PatchMapping("/{id}/reativar")
    public ProdutoResponse reativar(@PathVariable Long id) {
        return ProdutoResponse.from(produtoService.reativar(id));
    }

    @PostMapping(value = "/{id}/imagens", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoResponse adicionarImagem(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo) {
        return ProdutoResponse.from(produtoService.adicionarImagem(id, arquivo));
    }

    @DeleteMapping("/{id}/imagens/{imagemId}")
    public ProdutoResponse removerImagem(@PathVariable Long id, @PathVariable Long imagemId) {
        return ProdutoResponse.from(produtoService.removerImagem(id, imagemId));
    }
}
