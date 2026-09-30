package com.prostock.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * Cria e popula o banco de demonstração antes de o Spring subir.
 *
 * Passos: (1) cria o banco prostock_demo se não existir; (2) se ainda não tem
 * tabelas, aplica database/schema.sql e src/main/resources/demo/dados-demo-N.sql
 * numa única transação; (3) copia as fotos de demonstração para a pasta de
 * upload usada pelo perfil "demo".
 *
 * Os dados são gerados por ferramentas/demo/gerar_dados_demo.py.
 */
class PreparadorBancoDemo {

    private static final Logger log = LoggerFactory.getLogger(PreparadorBancoDemo.class);

    static final String NOME_BANCO = "prostock_demo";
    private static final Path SCHEMA = Path.of("database", "schema.sql");
    /**
     * Há uma versão dos dados para cada dia da semana (dados-demo-1.sql = segunda
     * ... dados-demo-7.sql = domingo). Carregando a do dia de hoje, as datas são
     * deslocadas por semanas inteiras e os dias da semana ficam coerentes (sem
     * venda no domingo, loja aberta até ontem).
     */
    private static String arquivoDeDadosDeHoje() {
        return "demo/dados-demo-" + LocalDate.now().getDayOfWeek().getValue() + ".sql";
    }
    private static final String FOTOS = "classpath:demo/fotos/*.png";
    private static final Path PASTA_FOTOS = Path.of("uploads-demo", "produtos");

    private final String servidor = variavel("DEMO_DB_SERVIDOR", "localhost:5432");
    private final String usuario = variavel("SPRING_DATASOURCE_USERNAME", variavel("DB_USERNAME", "postgres"));
    private final String senha = variavel("SPRING_DATASOURCE_PASSWORD", variavel("DB_PASSWORD", "postgres"));

    void preparar(boolean recriar) throws SQLException, IOException {
        try (Connection admin = conectar("postgres"); Statement st = admin.createStatement()) {
            if (recriar) {
                log.warn("Recriando o banco de demonstração '{}' do zero", NOME_BANCO);
                st.execute("DROP DATABASE IF EXISTS " + NOME_BANCO + " WITH (FORCE)");
            }
            if (!existeBanco(st)) {
                st.execute("CREATE DATABASE " + NOME_BANCO);
                log.info("Banco '{}' criado", NOME_BANCO);
            }
        }

        try (Connection banco = conectar(NOME_BANCO)) {
            if (temTabelas(banco)) {
                log.info("Banco de demonstração já populado; mantendo os dados (use --recriar-demo para gerar de novo)");
            } else {
                carregar(banco);
            }
        }

        copiarFotos(recriar);
    }

    private void carregar(Connection banco) throws SQLException, IOException {
        if (!Files.exists(SCHEMA)) {
            throw new IllegalStateException("Não encontrei " + SCHEMA.toAbsolutePath()
                    + ". Rode a aplicação de demonstração a partir da pasta raiz do projeto.");
        }
        log.info("Criando tabelas e carregando ~7 meses de dados simulados...");
        banco.setAutoCommit(false);
        try {
            ScriptUtils.executeSqlScript(banco, new EncodedResource(new FileSystemResource(SCHEMA), StandardCharsets.UTF_8));
            ScriptUtils.executeSqlScript(banco, new EncodedResource(new ClassPathResource(arquivoDeDadosDeHoje()), StandardCharsets.UTF_8));
            banco.commit();
        } catch (RuntimeException | SQLException e) {
            banco.rollback();
            throw e;
        }
        log.info("Banco de demonstração pronto");
    }

    private void copiarFotos(boolean sobrescrever) throws IOException {
        Files.createDirectories(PASTA_FOTOS);
        int copiadas = 0;
        for (Resource foto : new PathMatchingResourcePatternResolver().getResources(FOTOS)) {
            Path destino = PASTA_FOTOS.resolve(foto.getFilename());
            if (sobrescrever || !Files.exists(destino)) {
                try (InputStream entrada = foto.getInputStream()) {
                    Files.copy(entrada, destino, StandardCopyOption.REPLACE_EXISTING);
                    copiadas++;
                }
            }
        }
        if (copiadas > 0) {
            log.info("{} fotos de demonstração copiadas para {}", copiadas, PASTA_FOTOS.toAbsolutePath());
        }
    }

    private boolean existeBanco(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + NOME_BANCO + "'")) {
            return rs.next();
        }
    }

    private boolean temTabelas(Connection banco) throws SQLException {
        try (Statement st = banco.createStatement();
             ResultSet rs = st.executeQuery("SELECT to_regclass('prostock.produtos') IS NOT NULL")) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private Connection conectar(String nomeBanco) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + servidor + "/" + nomeBanco, usuario, senha);
    }

    private static String variavel(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
