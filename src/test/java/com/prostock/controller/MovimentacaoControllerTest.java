package com.prostock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prostock.config.SecurityConfig;
import com.prostock.dto.MovimentacaoRequest;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.MotivoMovimentacao;
import com.prostock.model.Movimentacao;
import com.prostock.model.Produto;
import com.prostock.model.Usuario;
import com.prostock.model.enums.Cargo;
import com.prostock.model.enums.TipoMovimentacao;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.security.UsuarioPrincipal;
import com.prostock.service.MovimentacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovimentacaoController.class)
@Import(SecurityConfig.class)
class MovimentacaoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private MovimentacaoService movimentacaoService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private RequestPostProcessor autenticado() {
        Usuario usuario = Usuario.builder()
                .id(1L).nome("Maria").email("maria@prostock.com")
                .senhaHash("hash").cargo(Cargo.FUNCIONARIO).ativo(true).build();
        return user(new UsuarioPrincipal(usuario));
    }

    private Movimentacao movimentacaoExemplo() {
        Categoria categoria = Categoria.builder().id(1L).nome("Freios").build();
        Fornecedor fornecedor = Fornecedor.builder().id(1L).razaoSocial("Autopeças Rio Grande").cnpj("12345678000199").build();
        Produto produto = Produto.builder()
                .id(5L).nome("Pastilha de Freio").sku("PF-001")
                .preco(new BigDecimal("89.90")).quantidadeAtual(18)
                .estoqueMinimo(5).estoqueMaximo(50)
                .categoria(categoria).fornecedor(fornecedor).ativo(true).build();
        Usuario usuario = Usuario.builder().id(1L).nome("Maria").email("maria@prostock.com").senhaHash("h").cargo(Cargo.FUNCIONARIO).build();
        MotivoMovimentacao motivo = MotivoMovimentacao.builder().id(1L).descricao("COMPRA").build();

        return Movimentacao.builder()
                .id(50L).produto(produto).usuario(usuario).motivo(motivo)
                .tipo(TipoMovimentacao.ENTRADA).quantidade(10)
                .dataHora(LocalDateTime.now()).build();
    }

    @Test
    void registrar_semCsrf_deveRetornar403() throws Exception {
        MovimentacaoRequest request = new MovimentacaoRequest(5L, 1L, TipoMovimentacao.ENTRADA, 10, null);

        mockMvc.perform(post("/api/movimentacoes")
                        .with(autenticado())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrar_comDadosValidos_deveRetornar201() throws Exception {
        MovimentacaoRequest request = new MovimentacaoRequest(5L, 1L, TipoMovimentacao.ENTRADA, 10, "Reposição");
        when(movimentacaoService.registrarManual(any(MovimentacaoRequest.class), any(Usuario.class)))
                .thenReturn(movimentacaoExemplo());

        mockMvc.perform(post("/api/movimentacoes")
                        .with(csrf())
                        .with(autenticado())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.produtoSku").value("PF-001"))
                .andExpect(jsonPath("$.tipo").value("ENTRADA"));
    }

    @Test
    void registrar_comEstoqueInsuficienteNaSaida_deveRetornar400() throws Exception {
        MovimentacaoRequest request = new MovimentacaoRequest(5L, 1L, TipoMovimentacao.SAIDA, 999, null);
        when(movimentacaoService.registrarManual(any(MovimentacaoRequest.class), any(Usuario.class)))
                .thenThrow(new RegraDeNegocioException("Estoque insuficiente para 'Pastilha de Freio': disponível 18, solicitado 999"));

        mockMvc.perform(post("/api/movimentacoes")
                        .with(csrf())
                        .with(autenticado())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarPorPeriodo_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/movimentacoes")
                        .queryParam("inicio", "2026-01-01T00:00:00")
                        .queryParam("fim", "2026-01-31T23:59:59"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarPorPeriodo_comUsuarioAutenticado_deveRetornarLista() throws Exception {
        when(movimentacaoService.listarPorPeriodo(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(movimentacaoExemplo()));

        mockMvc.perform(get("/api/movimentacoes")
                        .with(autenticado())
                        .queryParam("inicio", "2026-01-01T00:00:00")
                        .queryParam("fim", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(50));
    }
}
