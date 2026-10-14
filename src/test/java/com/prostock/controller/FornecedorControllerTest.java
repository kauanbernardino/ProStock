package com.prostock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prostock.config.SecurityConfig;
import com.prostock.dto.FornecedorRequest;
import com.prostock.model.Fornecedor;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.service.FornecedorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FornecedorController.class)
@Import(SecurityConfig.class)
class FornecedorControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private FornecedorService fornecedorService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private FornecedorRequest requestValido() {
        return new FornecedorRequest("Autopeças Rio Grande", "12345678000199", "(11) 4002-8922");
    }

    @Test
    void listar_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/fornecedores"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void criar_comUsuarioFuncionario_deveRetornar403() throws Exception {
        mockMvc.perform(post("/api/fornecedores")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void criar_comCnpjInvalido_deveRetornar400() throws Exception {
        // CNPJ inválido (letras e menos de 14 dígitos).
        FornecedorRequest invalido = new FornecedorRequest("Autopeças Rio Grande", "123ABC", null);

        mockMvc.perform(post("/api/fornecedores")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void criar_comDadosValidos_deveRetornar201() throws Exception {
        when(fornecedorService.criar(any(FornecedorRequest.class))).thenReturn(
                Fornecedor.builder().id(1L).razaoSocial("Autopeças Rio Grande")
                        .cnpj("12345678000199").ativo(true).build());

        mockMvc.perform(post("/api/fornecedores")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.razaoSocial").value("Autopeças Rio Grande"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void inativar_comUsuarioAdministrador_deveRetornar204() throws Exception {
        mockMvc.perform(delete("/api/fornecedores/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void reativar_comUsuarioAdministrador_deveRetornar204() throws Exception {
        mockMvc.perform(patch("/api/fornecedores/1/reativar").with(csrf()))
                .andExpect(status().isNoContent());

        verify(fornecedorService).reativar(1L);
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void reativar_comUsuarioFuncionario_deveRetornar403() throws Exception {
        mockMvc.perform(patch("/api/fornecedores/1/reativar").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
