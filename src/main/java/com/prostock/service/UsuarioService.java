package com.prostock.service;

import com.prostock.dto.UsuarioRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Usuario;
import com.prostock.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

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
        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        usuario.setCargo(request.cargo());
        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        }
        return usuarioRepository.save(usuario);
    }

    public void inativar(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }

    public void reativar(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setAtivo(true);
        usuarioRepository.save(usuario);
    }
}
