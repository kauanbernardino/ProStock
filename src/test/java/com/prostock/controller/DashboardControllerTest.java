package com.prostock.controller;

import com.prostock.config.SecurityConfig;
import com.prostock.dto.DashboardResponse;
import com.prostock.dto.SugestaoCompraResponse;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.service.DashboardService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private DashboardService dashboardService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void dashboard_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void dashboard_comUsuarioAutenticado_deveRetornarResumo() throws Exception {
        when(dashboardService.obterDashboard()).thenReturn(new DashboardResponse(
                new BigDecimal("15000.00"), 320L, 42L, 5L, 2L, 1L, 3L));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProdutos").value(42))
                .andExpect(jsonPath("$.produtosEmRuptura").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void sugestoesDeCompra_comUsuarioAutenticado_deveRetornarLista() throws Exception {
        when(dashboardService.sugestoesDeCompra()).thenReturn(List.of(
                new SugestaoCompraResponse(5L, "Pastilha de Freio", "PF-001", 2, 5, 50,
                        new BigDecimal("12.5"), 20, "Autopeças Rio Grande")));

        mockMvc.perform(get("/api/dashboard/sugestoes-compra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("PF-001"))
                .andExpect(jsonPath("$[0].quantidadeSugerida").value(20));
    }
}
