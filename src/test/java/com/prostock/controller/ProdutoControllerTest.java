package com.prostock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prostock.config.SecurityConfig;
import com.prostock.dto.ProdutoRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.Produto;
import com.prostock.model.Usuario;
import com.prostock.model.enums.Cargo;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.security.UsuarioPrincipal;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testes da camada web com as regras reais do SecurityConfig, sem banco. */
@WebMvcTest(ProdutoController.class)
@Import(SecurityConfig.class)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProdutoService produtoService;

    // Necessário para o contexto de segurança subir.
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
        // Cadastro de produto: só administrador.
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

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void listarInativos_comUsuarioAdministrador_deveRetornarProdutosInativos() throws Exception {
        Produto inativo = produtoExemplo();
        inativo.setAtivo(false);
        when(produtoService.listarInativos()).thenReturn(List.of(inativo));

        mockMvc.perform(get("/api/produtos/inativos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("PF-001"))
                .andExpect(jsonPath("$[0].ativo").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void reativar_comUsuarioAdministrador_deveRetornar200() throws Exception {
        Produto reativado = produtoExemplo();
        when(produtoService.reativar(10L)).thenReturn(reativado);

        mockMvc.perform(patch("/api/produtos/10/reativar").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));

        verify(produtoService).reativar(10L);
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void reativar_comUsuarioFuncionario_deveRetornar403() throws Exception {
        mockMvc.perform(patch("/api/produtos/10/reativar").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // UC07: ajuste de inventário

    private Usuario usuarioComCargo(Cargo cargo) {
        return Usuario.builder().id(1L).nome("Teste").email("teste@prostock.com")
                .senhaHash("hash").cargo(cargo).ativo(true).build();
    }

    @Test
    void ajustarInventario_comAdministrador_deveRegistrarAjusteERetornarProduto() throws Exception {
        Usuario admin = usuarioComCargo(Cargo.ADMINISTRADOR);
        Produto ajustado = produtoExemplo();
        ajustado.setQuantidadeAtual(17);
        when(produtoService.ajustarInventario(eq(10L), eq(17), anyString(), any(Usuario.class))).thenReturn(ajustado);

        mockMvc.perform(patch("/api/produtos/10/ajuste-inventario")
                        .with(csrf())
                        .with(user(new UsuarioPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantidade", 17, "observacao", "contagem"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeAtual").value(17));

        verify(produtoService).ajustarInventario(eq(10L), eq(17), eq("contagem"), any(Usuario.class));
    }

    @Test
    void ajustarInventario_comFuncionario_deveRetornar403() throws Exception {
        mockMvc.perform(patch("/api/produtos/10/ajuste-inventario")
                        .with(csrf())
                        .with(user(new UsuarioPrincipal(usuarioComCargo(Cargo.FUNCIONARIO))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantidade", 17))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(produtoService);
    }

    @Test
    void ajustarInventario_comQuantidadeNegativa_deveRetornar400() throws Exception {
        mockMvc.perform(patch("/api/produtos/10/ajuste-inventario")
                        .with(csrf())
                        .with(user(new UsuarioPrincipal(usuarioComCargo(Cargo.ADMINISTRADOR))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantidade", -3))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(produtoService);
    }
}
