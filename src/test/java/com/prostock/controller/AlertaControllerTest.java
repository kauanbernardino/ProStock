package com.prostock.controller;

import com.prostock.config.SecurityConfig;
import com.prostock.model.Alerta;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.Produto;
import com.prostock.model.enums.TipoAlerta;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.service.AlertaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertaController.class)
@Import(SecurityConfig.class)
class AlertaControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AlertaService alertaService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private Alerta alertaExemplo() {
        Categoria categoria = Categoria.builder().id(1L).nome("Freios").build();
        Fornecedor fornecedor = Fornecedor.builder().id(1L).razaoSocial("Autopeças Rio Grande").cnpj("12345678000199").build();
        Produto produto = Produto.builder()
                .id(5L).nome("Pastilha de Freio").sku("PF-001")
                .preco(new BigDecimal("89.90")).quantidadeAtual(0)
                .estoqueMinimo(5).estoqueMaximo(50)
                .categoria(categoria).fornecedor(fornecedor).ativo(true).build();
        return Alerta.builder().id(1L).produto(produto).tipo(TipoAlerta.RUPTURA)
                .mensagem("Produto em ruptura").lido(false).build();
    }

    @Test
    void listarNaoLidos_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/alertas/nao-lidos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void listarNaoLidos_comUsuarioAutenticado_deveRetornarAlertas() throws Exception {
        when(alertaService.listarNaoLidos()).thenReturn(List.of(alertaExemplo()));

        mockMvc.perform(get("/api/alertas/nao-lidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("RUPTURA"));
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void marcarComoLido_comUsuarioFuncionario_deveRetornar204() throws Exception {
        // Marcar alerta como lido: os dois perfis.
        mockMvc.perform(patch("/api/alertas/1/lido").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void verificarObsolescencia_comUsuarioFuncionario_deveRetornar403() throws Exception {
        // Verificar obsolescência: só administrador.
        mockMvc.perform(post("/api/alertas/verificar-obsolescencia").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void verificarObsolescencia_comUsuarioAdministrador_deveRetornar200() throws Exception {
        when(alertaService.verificarEstoqueObsoleto()).thenReturn(List.of());

        mockMvc.perform(post("/api/alertas/verificar-obsolescencia").with(csrf()))
                .andExpect(status().isOk());
    }
}
