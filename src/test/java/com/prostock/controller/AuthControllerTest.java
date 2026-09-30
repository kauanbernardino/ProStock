package com.prostock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prostock.config.SecurityConfig;
import com.prostock.dto.LoginRequest;
import com.prostock.model.Usuario;
import com.prostock.model.enums.Cargo;
import com.prostock.security.CustomUserDetailsService;
import com.prostock.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController é o único ponto de entrada não autenticado do sistema —
 * merece atenção redobrada: é onde a sessão nasce.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    // Substitui a autenticação real (banco + BCrypt) por um dublê controlável.
    @MockBean
    private AuthenticationManager authenticationManager;

    // SecurityConfig ainda monta um DaoAuthenticationProvider que depende
    // deste bean, mesmo sem ser usado diretamente nestes testes.
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private Usuario usuarioValido() {
        return Usuario.builder()
                .id(1L).nome("Maria Souza").email("maria@prostock.com")
                .senhaHash("hash-bcrypt").cargo(Cargo.ADMINISTRADOR).ativo(true).build();
    }

    @Test
    void login_semTokenCsrf_deveRetornar403() throws Exception {
        LoginRequest request = new LoginRequest("maria@prostock.com", "senha123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_comCredenciaisValidas_deveRetornar200ComUsuario() throws Exception {
        UsuarioPrincipal principal = new UsuarioPrincipal(usuarioValido());
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        LoginRequest request = new LoginRequest("maria@prostock.com", "senha123");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("maria@prostock.com"))
                .andExpect(jsonPath("$.cargo").value("ADMINISTRADOR"));
    }

    @Test
    void login_comCredenciaisInvalidas_deveRetornar401() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Credenciais inválidas"));

        LoginRequest request = new LoginRequest("maria@prostock.com", "senha-errada");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha incorretos"));
    }

    @Test
    void me_semAutenticacao_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_comUsuarioAutenticado_deveRetornarUsuarioLogado() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(user(new UsuarioPrincipal(usuarioValido()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria Souza"));
    }

    @Test
    void login_comUsuarioInativo_deveRetornar401ComMensagemClara() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("User is disabled"));

        LoginRequest request = new LoginRequest("maria@prostock.com", "senha123");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Usuário inativo"));
    }
}
