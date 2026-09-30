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
        // O Spring Security guarda cargo e e-mail do usuário dentro da sessão.
        // Se algum dado de acesso mudar, as sessões abertas precisam cair: senão
        // um funcionário rebaixado continuaria com permissão de administrador
        // até fazer logout, e uma senha trocada não derrubaria quem já entrou.
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

    /**
     * Inativa a conta e encerra na hora todas as sessões abertas dela.
     *
     * Só marcar ativo=false bloquearia o PRÓXIMO login (isEnabled() em
     * UsuarioPrincipal), mas uma sessão já aberta continuaria válida até
     * expirar, porque o Spring Security guarda o usuário autenticado dentro
     * da sessão. Como as sessões ficam na tabela SPRING_SESSION (Spring
     * Session JDBC), indexadas pelo e-mail do usuário, dá para apagá-las
     * direto: a próxima requisição dele já volta 401 e o front manda para
     * a tela de login.
     */
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
