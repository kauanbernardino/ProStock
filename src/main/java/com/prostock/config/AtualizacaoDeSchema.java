package com.prostock.config;

import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * Aplica as alterações de schema feitas depois que o banco já estava em uso
 * (arquivo src/main/resources/db/atualizacoes.sql), para que um banco criado
 * com uma versão antiga do database/schema.sql continue compatível sem
 * ninguém precisar rodar SQL na mão.
 *
 * Por ser um DataSourceScriptDatabaseInitializer, o Spring Boot garante que
 * ele roda ANTES do Hibernate validar as entidades (ddl-auto: validate) —
 * necessário quando a alteração cria uma coluna que uma entidade já espera,
 * como produtos.versao.
 *
 * Se o projeto crescer, o caminho natural é trocar isto por uma ferramenta de
 * migração versionada (Flyway).
 */
@Configuration
public class AtualizacaoDeSchema {

    @Bean
    public DataSourceScriptDatabaseInitializer atualizacoesDeSchema(DataSource dataSource) {
        DatabaseInitializationSettings configuracao = new DatabaseInitializationSettings();
        configuracao.setSchemaLocations(List.of("classpath:db/atualizacoes.sql"));
        configuracao.setMode(DatabaseInitializationMode.ALWAYS);
        configuracao.setContinueOnError(false);
        return new DataSourceScriptDatabaseInitializer(dataSource, configuracao);
    }
}
