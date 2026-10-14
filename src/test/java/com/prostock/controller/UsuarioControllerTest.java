package com.prostock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prostock.config.SecurityConfig;
import com.prostock.dto.UsuarioRequest;
import com.prostock.model.Usuario;
import com.prostock.model.enums.Cargo;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.service.UsuarioService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
@Import(SecurityConfig.class)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private UsuarioService usuarioService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private UsuarioRequest requestValido() {
        return new UsuarioRequest("Maria Souza", "maria@prostock.com", "senha123", Cargo.FUNCIONARIO);
    }

    @Test
    void listar_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "FUNCIONARIO")
    void listar_comUsuarioFuncionario_deveRetornar403() throws Exception {
        // Usuários: até a leitura é só do administrador.
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void listar_comUsuarioAdministrador_deveRetornar200() throws Exception {
        when(usuarioService.listarTodos()).thenReturn(java.util.List.of(
                Usuario.builder().id(1L).nome("Maria Souza").email("maria@prostock.com")
                        .senhaHash("hash").cargo(Cargo.FUNCIONARIO).ativo(true).build()));

        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("maria@prostock.com"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void criar_comSenhaCurta_deveRetornar400() throws Exception {
        UsuarioRequest invalido = new UsuarioRequest("Maria Souza", "maria@prostock.com", "123", Cargo.FUNCIONARIO);

        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void criar_comDadosValidos_deveRetornar201() throws Exception {
        when(usuarioService.criar(any(UsuarioRequest.class))).thenReturn(
                Usuario.builder().id(1L).nome("Maria Souza").email("maria@prostock.com")
                        .senhaHash("hash").cargo(Cargo.FUNCIONARIO).ativo(true).build());

        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Maria Souza"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void inativar_comUsuarioAdministrador_deveRetornar204() throws Exception {
        mockMvc.perform(patch("/api/usuarios/1/inativar").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
