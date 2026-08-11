package com.prostock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prostock.config.SecurityConfig;
import com.prostock.dto.ProdutoRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.Produto;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.service.ProdutoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o controller "de fora", como se fosse o navegador batendo na API:
 * sobe so a camada web (sem banco de verdade), aplicando as regras reais
 * de autenticacao/autorizacao/CSRF do SecurityConfig.
 */
@WebMvcTest(ProdutoController.class)
@Import(SecurityConfig.class)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProdutoService produtoService;

    // SecurityConfig precisa de um CustomUserDetailsService para existir;
    // @WithMockUser nao passa por ele, entao um mock vazio basta.
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private Produto produtoExemplo() {
        Categoria categoria = Categoria.builder().id(1L).nome("Freios").build();
        Fornecedor fornecedor = Fornecedor.builder()
                .id(1L).razaoSocial("Autopeças Rio Grande").cnpj("12345678000199").build();
        return Produto.builder()
                .id(10L).nome("Pastilha de Freio").sku("PF-001")
                .preco(new BigDecimal("89.90")).quantidadeAtual(20)
                .estoqueMinimo(5).estoqueMaximo(50)
                .categoria(categoria).fornecedor(fornecedor)
                .ativo(true)
                .build();
    }

    private ProdutoRequest requestValido() {
        return new ProdutoRequest(
                "Pastilha de Freio", "PF-001", "descrição",
                new BigDecimal("89.90"), 20, 5, 50, "Corredor 1", 1L, 1L);
    }

    @Test
    void listar_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/produtos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void listar_comUsuarioAutenticado_deveRetornarProdutos() throws Exception {
        when(produtoService.listarTodos()).thenReturn(List.of(produtoExemplo()));

        mockMvc.perform(get("/api/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("PF-001"))
                .andExpect(jsonPath("$[0].nome").value("Pastilha de Freio"))
                .andExpect(jsonPath("$[0].categoriaNome").value("Freios"));
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void criar_comUsuarioFuncionario_deveRetornar403() throws Exception {
        // Cadastrar produto e restrito a ADMINISTRADOR — funcionario nao pode.
        mockMvc.perform(post("/api/produtos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void criar_comUsuarioAdministrador_deveRetornar201() throws Exception {
        when(produtoService.criar(any(ProdutoRequest.class))).thenReturn(produtoExemplo());

        mockMvc.perform(post("/api/produtos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void criar_semTokenCsrf_deveRetornar403() throws Exception {
        mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void buscar_comIdInexistente_deveRetornar404() throws Exception {
        when(produtoService.buscarPorId(999L))
                .thenThrow(new RecursoNaoEncontradoException("Produto não encontrado: 999"));

        mockMvc.perform(get("/api/produtos/999"))
                .andExpect(status().isNotFound());
    }
}
