package com.prostock.controller;

import com.prostock.config.SecurityConfig;
import com.prostock.dto.RelatorioItemResponse;
import com.prostock.dto.RelatorioResponse;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.service.RelatorioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RelatorioController.class)
@Import(SecurityConfig.class)
class RelatorioControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private RelatorioService relatorioService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void gerar_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/relatorios").param("de", "2026-07-01").param("ate", "2026-07-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void gerar_comFuncionario_deveRetornar403() throws Exception {
        mockMvc.perform(get("/api/relatorios").param("de", "2026-07-01").param("ate", "2026-07-31"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void gerar_comAdministrador_deveRetornarRelatorio() throws Exception {
        RelatorioItemResponse item = new RelatorioItemResponse(
                1L, "PF-001", "Pastilha de Freio", "Freios", 20, 15, 10,
                new BigDecimal("50"), new BigDecimal("200.00"));
        RelatorioResponse resposta = new RelatorioResponse(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                new BigDecimal("200.00"), new BigDecimal("100.00"), 15L, 10L,
                new BigDecimal("50"), List.of(item));

        when(relatorioService.gerar(eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 31)), any()))
                .thenReturn(resposta);

        mockMvc.perform(get("/api/relatorios").param("de", "2026-07-01").param("ate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].sku").value("PF-001"))
                .andExpect(jsonPath("$.giroMedio").value(50))
                .andExpect(jsonPath("$.valorTotalEmEstoque").value(200.00));
    }
}
