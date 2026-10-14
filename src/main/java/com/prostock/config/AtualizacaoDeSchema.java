package com.prostock.config;

import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/** Aplica db/atualizacoes.sql a cada inicialização, antes de o Hibernate validar as entidades (ddl-auto: validate). */
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
