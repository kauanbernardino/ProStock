package com.prostock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prostock.config.SecurityConfig;
import com.prostock.dto.CategoriaRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.model.Categoria;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.service.CategoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoriaController.class)
@Import(SecurityConfig.class)
class CategoriaControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private CategoriaService categoriaService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void listar_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void listar_comUsuarioAutenticado_deveRetornarCategorias() throws Exception {
        when(categoriaService.listarTodas()).thenReturn(List.of(
                Categoria.builder().id(1L).nome("Freios").descricao("Peças de freio").build()));

        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Freios"));
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void criar_comUsuarioFuncionario_deveRetornar403() throws Exception {
        // Cadastro de categoria é restrito a ADMINISTRADOR.
        mockMvc.perform(post("/api/categorias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoriaRequest("Freios", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void criar_comUsuarioAdministrador_deveRetornar201() throws Exception {
        when(categoriaService.criar(any(CategoriaRequest.class)))
                .thenReturn(Categoria.builder().id(1L).nome("Freios").build());

        mockMvc.perform(post("/api/categorias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoriaRequest("Freios", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void criar_semNome_deveRetornar400() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoriaRequest("", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void buscar_comIdInexistente_deveRetornar404() throws Exception {
        when(categoriaService.buscarPorId(999L))
                .thenThrow(new RecursoNaoEncontradoException("Categoria não encontrada: 999"));

        mockMvc.perform(get("/api/categorias/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void remover_comUsuarioAdministrador_deveRetornar204() throws Exception {
        mockMvc.perform(delete("/api/categorias/1").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
