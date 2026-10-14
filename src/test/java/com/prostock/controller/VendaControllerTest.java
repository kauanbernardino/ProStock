package com.prostock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prostock.config.SecurityConfig;
import com.prostock.dto.ItemVendaRequest;
import com.prostock.dto.VendaRequest;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.ItemVenda;
import com.prostock.model.Produto;
import com.prostock.model.Usuario;
import com.prostock.model.Venda;
import com.prostock.model.enums.Cargo;
import com.prostock.model.enums.FormaPagamento;
import com.prostock.model.enums.StatusVenda;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.security.UsuarioPrincipal;
import com.prostock.service.PixService;
import com.prostock.service.VendaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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

@WebMvcTest(VendaController.class)
@Import(SecurityConfig.class)
class VendaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VendaService vendaService;

    @MockBean
    private PixService pixService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    // Autentica com um UsuarioPrincipal, exigido pelo @AuthenticationPrincipal do controller.
    private RequestPostProcessor autenticadoComo(Usuario usuario) {
        return user(new UsuarioPrincipal(usuario));
    }

    private Usuario vendedor() {
        return Usuario.builder()
                .id(1L).nome("Maria").email("maria@prostock.com")
                .senhaHash("hash").cargo(Cargo.FUNCIONARIO).ativo(true).build();
    }

    private Venda vendaExemplo(FormaPagamento formaPagamento) {
        Categoria categoria = Categoria.builder().id(1L).nome("Freios").build();
        Fornecedor fornecedor = Fornecedor.builder().id(1L).razaoSocial("Autopeças Rio Grande").cnpj("12345678000199").build();
        Produto produto = Produto.builder()
                .id(5L).nome("Pastilha de Freio").sku("PF-001")
                .preco(new BigDecimal("89.90")).quantidadeAtual(18)
                .estoqueMinimo(5).estoqueMaximo(50)
                .categoria(categoria).fornecedor(fornecedor).ativo(true).build();
        ItemVenda item = ItemVenda.builder()
                .id(1L).produto(produto).quantidade(2).precoUnitario(new BigDecimal("89.90")).build();

        return Venda.builder()
                .id(100L).usuario(vendedor())
                .valorTotal(new BigDecimal("179.80"))
                .formaPagamento(formaPagamento)
                .status(StatusVenda.CONCLUIDA)
                .dataHora(LocalDateTime.now())
                .itens(List.of(item))
                .build();
    }

    private VendaRequest requestValido(FormaPagamento formaPagamento) {
        return new VendaRequest(List.of(new ItemVendaRequest(5L, 2)), formaPagamento);
    }

    @Test
    void listar_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/vendas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void listar_comUsuarioAutenticado_deveRetornarResumoDeVendas() throws Exception {
        when(vendaService.listarTodas()).thenReturn(List.of(vendaExemplo(FormaPagamento.DINHEIRO)));

        mockMvc.perform(get("/api/vendas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].quantidadeItens").value(1));
    }

    @Test
    void finalizar_semCsrf_deveRetornar403() throws Exception {
        mockMvc.perform(post("/api/vendas")
                        .with(autenticadoComo(vendedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido(FormaPagamento.DINHEIRO))))
                .andExpect(status().isForbidden());
    }

    @Test
    void finalizar_semItens_deveRetornar400() throws Exception {
        VendaRequest requestVazio = new VendaRequest(List.of(), FormaPagamento.DINHEIRO);

        mockMvc.perform(post("/api/vendas")
                        .with(csrf())
                        .with(autenticadoComo(vendedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestVazio)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vendaService);
    }

    @Test
    void finalizar_comPix_deveRetornar201ComCodigoPix() throws Exception {
        when(vendaService.finalizar(any(VendaRequest.class), any(Usuario.class)))
                .thenReturn(vendaExemplo(FormaPagamento.PIX));
        when(pixService.gerarCopiaECola(any(BigDecimal.class), anyString()))
                .thenReturn("00020101...CODIGO-PIX-FALSO...6304ABCD");

        mockMvc.perform(post("/api/vendas")
                        .with(csrf())
                        .with(autenticadoComo(vendedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido(FormaPagamento.PIX))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.pixCopiaECola").value("00020101...CODIGO-PIX-FALSO...6304ABCD"))
                .andExpect(jsonPath("$.itens[0].produtoSku").value("PF-001"));

        verify(pixService).gerarCopiaECola(new BigDecimal("179.80"), "VDA100");
    }

    @Test
    void finalizar_comDinheiro_naoDeveGerarPix() throws Exception {
        when(vendaService.finalizar(any(VendaRequest.class), any(Usuario.class)))
                .thenReturn(vendaExemplo(FormaPagamento.DINHEIRO));

        mockMvc.perform(post("/api/vendas")
                        .with(csrf())
                        .with(autenticadoComo(vendedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido(FormaPagamento.DINHEIRO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pixCopiaECola").doesNotExist());

        verifyNoInteractions(pixService);
    }

    @Test
    void finalizar_comEstoqueInsuficiente_deveRetornar400() throws Exception {
        when(vendaService.finalizar(any(VendaRequest.class), any(Usuario.class)))
                .thenThrow(new RegraDeNegocioException("Estoque insuficiente para 'Pastilha de Freio': disponível 1, solicitado 2"));

        mockMvc.perform(post("/api/vendas")
                        .with(csrf())
                        .with(autenticadoComo(vendedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido(FormaPagamento.DINHEIRO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("Estoque insuficiente")));
    }

    @Test
    void cancelar_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(patch("/api/vendas/100/cancelar").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelar_deVendaJaCancelada_deveRetornar400() throws Exception {
        when(vendaService.cancelar(org.mockito.ArgumentMatchers.eq(100L), any(Usuario.class)))
                .thenThrow(new RegraDeNegocioException("Esta venda já está cancelada"));

        mockMvc.perform(patch("/api/vendas/100/cancelar")
                        .with(csrf())
                        .with(autenticadoComo(vendedor())))
                .andExpect(status().isBadRequest());

        verify(vendaService, never()).buscarPorId(any());
    }

    @Test
    void finalizar_comConflitoDeConcorrencia_deveRetornar409SemGravarNada() throws Exception {
        // Simula duas vendas simultâneas da última unidade.
        when(vendaService.finalizar(any(VendaRequest.class), any(Usuario.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Produto.class, 5L));

        mockMvc.perform(post("/api/vendas")
                        .with(csrf())
                        .with(autenticadoComo(vendedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido(FormaPagamento.DINHEIRO))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Conflito de atualização"));
    }
}
