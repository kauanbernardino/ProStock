package com.prostock.config;

import com.prostock.model.Usuario;
import com.prostock.model.enums.Cargo;
import com.prostock.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Cria o administrador padrão quando não há nenhum usuário cadastrado. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        Usuario admin = Usuario.builder()
                .nome("Administrador")
                .email("admin@prostock.com")
                .senhaHash(passwordEncoder.encode("admin123"))
                .cargo(Cargo.ADMINISTRADOR)
                .ativo(true)
                .build();
        usuarioRepository.save(admin);

        log.warn("Nenhum usuário encontrado — usuário administrador padrão criado " +
                "(admin@prostock.com / admin123).");
    }
}
