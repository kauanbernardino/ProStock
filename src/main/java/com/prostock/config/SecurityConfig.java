package com.prostock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Autenticação montada pelo Spring a partir do CustomUserDetailsService e do PasswordEncoder (BCrypt). */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()

                        // Usuários: só administrador
                        .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")

                        // Consultas: os dois perfis
                        .requestMatchers(HttpMethod.GET,
                                "/api/produtos/**", "/api/categorias/**", "/api/fornecedores/**",
                                "/api/motivos/**", "/api/movimentacoes/**", "/api/alertas/**", "/api/dashboard/**",
                                "/api/vendas/**"
                        ).hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")

                        // Movimentações, vendas e alertas: os dois perfis
                        .requestMatchers(HttpMethod.POST, "/api/movimentacoes", "/api/vendas")
                                .hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")
                        .requestMatchers(HttpMethod.PATCH, "/api/alertas/**", "/api/vendas/**")
                                .hasAnyRole("ADMINISTRADOR", "FUNCIONARIO")

                        // Relatórios: só administrador
                        .requestMatchers(HttpMethod.GET, "/api/relatorios/**").hasRole("ADMINISTRADOR")

                        // Cadastros: só administrador
                        .requestMatchers(HttpMethod.POST, "/api/produtos/**", "/api/categorias/**",
                                "/api/fornecedores/**", "/api/motivos/**", "/api/alertas/verificar-obsolescencia"
                        ).hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PUT, "/api/produtos/**", "/api/categorias/**", "/api/fornecedores/**")
                                .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/produtos/**", "/api/fornecedores/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/produtos/**", "/api/categorias/**",
                                "/api/fornecedores/**", "/api/motivos/**"
                        ).hasRole("ADMINISTRADOR")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(401, "Não autenticado"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(403, "Acesso negado"))
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }
}
