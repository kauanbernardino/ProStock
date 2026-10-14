package com.prostock.service;

import com.prostock.dto.UsuarioRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Usuario;
import com.prostock.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Transactional(readOnly = true)
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + id));
    }

    public Usuario criar(UsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new RegraDeNegocioException("Já existe um usuário com o e-mail '" + request.email() + "'");
        }
        if (request.senha() == null || request.senha().isBlank()) {
            throw new RegraDeNegocioException("A senha é obrigatória para criar um novo usuário");
        }
        Usuario usuario = Usuario.builder()
                .nome(request.nome())
                .email(request.email())
                .senhaHash(passwordEncoder.encode(request.senha()))
                .cargo(request.cargo())
                .ativo(true)
                .build();
        return usuarioRepository.save(usuario);
    }

    public Usuario atualizar(Long id, UsuarioRequest request) {
        Usuario usuario = buscarPorId(id);
        usuarioRepository.findByEmail(request.email())
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new RegraDeNegocioException("Já existe um usuário com o e-mail '" + request.email() + "'");
                });
        // Cargo e e-mail ficam guardados na sessão: mudar dados de acesso encerra as sessões abertas.
        String emailAnterior = usuario.getEmail();
        boolean trocouSenha = request.senha() != null && !request.senha().isBlank();
        boolean mudouAcesso = usuario.getCargo() != request.cargo()
                || !emailAnterior.equalsIgnoreCase(request.email())
                || trocouSenha;

        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        usuario.setCargo(request.cargo());
        if (trocouSenha) {
            usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        }
        Usuario salvo = usuarioRepository.save(usuario);

        if (mudouAcesso) {
            encerrarSessoesDo(emailAnterior);
        }
        return salvo;
    }

    /** Inativa o usuário e apaga as sessões abertas dele (SPRING_SESSION). */
    public void inativar(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
        encerrarSessoesDo(usuario.getEmail());
    }

    private void encerrarSessoesDo(String email) {
        sessionRepository.findByPrincipalName(email)
                .keySet()
                .forEach(sessionRepository::deleteById);
    }

    public void reativar(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setAtivo(true);
        usuarioRepository.save(usuario);
    }
}
