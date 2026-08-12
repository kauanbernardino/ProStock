package com.prostock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prostock.config.SecurityConfig;
import com.prostock.model.MotivoMovimentacao;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.service.MotivoMovimentacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MotivoMovimentacaoController.class)
@Import(SecurityConfig.class)
class MotivoMovimentacaoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private MotivoMovimentacaoService motivoMovimentacaoService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void listar_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/motivos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void listar_comUsuarioAutenticado_deveRetornarMotivos() throws Exception {
        when(motivoMovimentacaoService.listarTodos())
                .thenReturn(List.of(MotivoMovimentacao.builder().id(1L).descricao("COMPRA").build()));

        mockMvc.perform(get("/api/motivos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descricao").value("COMPRA"));
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void criar_comUsuarioFuncionario_deveRetornar403() throws Exception {
        mockMvc.perform(post("/api/motivos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("descricao", "TROCA_GARANTIA"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void criar_comUsuarioAdministrador_deveRetornar201() throws Exception {
        when(motivoMovimentacaoService.criar(anyString()))
                .thenReturn(MotivoMovimentacao.builder().id(7L).descricao("TROCA_GARANTIA").build());

        mockMvc.perform(post("/api/motivos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("descricao", "TROCA_GARANTIA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void remover_comUsuarioAdministrador_deveRetornar204() throws Exception {
        mockMvc.perform(delete("/api/motivos/1").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
