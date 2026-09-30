package com.prostock.service;

import com.prostock.dto.UsuarioRequest;
import com.prostock.model.Usuario;
import com.prostock.model.enums.Cargo;
import com.prostock.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private FindByIndexNameSessionRepository<Session> sessionRepository;

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder, sessionRepository);
    }

    private Usuario funcionarioAtivo() {
        return Usuario.builder()
                .id(2L).nome("Maria").email("maria@prostock.com")
                .senhaHash("hash").cargo(Cargo.FUNCIONARIO).ativo(true).build();
    }

    @Test
    void inativar_deveMarcarComoInativoEEncerrarTodasAsSessoesAbertasDoUsuario() {
        Usuario maria = funcionarioAtivo();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));
        Map<String, Session> sessoesAbertas = new HashMap<>();
        sessoesAbertas.put("sessao-balcao", mock(Session.class));
        sessoesAbertas.put("sessao-celular", mock(Session.class));
        when(sessionRepository.findByPrincipalName("maria@prostock.com")).thenReturn(sessoesAbertas);

        usuarioService.inativar(2L);

        assertThat(maria.isAtivo()).isFalse();
        verify(usuarioRepository).save(maria);
        verify(sessionRepository).deleteById("sessao-balcao");
        verify(sessionRepository).deleteById("sessao-celular");
    }

    @Test
    void inativar_usuarioSemSessaoAberta_naoDeveFalhar() {
        Usuario maria = funcionarioAtivo();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));
        when(sessionRepository.findByPrincipalName("maria@prostock.com")).thenReturn(Map.of());

        usuarioService.inativar(2L);

        assertThat(maria.isAtivo()).isFalse();
        verify(sessionRepository, never()).deleteById(anyString());
    }

    @Test
    void reativar_naoDeveMexerEmSessoes() {
        Usuario maria = funcionarioAtivo();
        maria.setAtivo(false);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));

        usuarioService.reativar(2L);

        assertThat(maria.isAtivo()).isTrue();
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void atualizar_comMudancaDeCargo_deveEncerrarSessoesAbertas() {
        Usuario maria = funcionarioAtivo();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));
        when(usuarioRepository.findByEmail("maria@prostock.com")).thenReturn(Optional.of(maria));
        when(sessionRepository.findByPrincipalName("maria@prostock.com"))
                .thenReturn(Map.of("sessao-balcao", mock(Session.class)));

        usuarioService.atualizar(2L, new UsuarioRequest("Maria", "maria@prostock.com", null, Cargo.ADMINISTRADOR));

        assertThat(maria.getCargo()).isEqualTo(Cargo.ADMINISTRADOR);
        verify(sessionRepository).deleteById("sessao-balcao");
    }

    @Test
    void atualizar_trocandoEmail_deveEncerrarSessoesDoEmailAntigo() {
        Usuario maria = funcionarioAtivo();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));
        when(usuarioRepository.findByEmail("maria.souza@prostock.com")).thenReturn(Optional.empty());
        when(sessionRepository.findByPrincipalName("maria@prostock.com"))
                .thenReturn(Map.of("sessao-antiga", mock(Session.class)));

        usuarioService.atualizar(2L, new UsuarioRequest("Maria", "maria.souza@prostock.com", null, Cargo.FUNCIONARIO));

        verify(sessionRepository).deleteById("sessao-antiga");
    }

    @Test
    void atualizar_soONome_naoDeveDerrubarOUsuario() {
        Usuario maria = funcionarioAtivo();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(maria));
        when(usuarioRepository.findByEmail("maria@prostock.com")).thenReturn(Optional.of(maria));

        usuarioService.atualizar(2L, new UsuarioRequest("Maria Souza", "maria@prostock.com", "", Cargo.FUNCIONARIO));

        assertThat(maria.getNome()).isEqualTo("Maria Souza");
        verifyNoInteractions(sessionRepository);
    }
}
