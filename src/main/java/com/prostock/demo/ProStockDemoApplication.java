package com.prostock.demo;

import com.prostock.ProStockApplication;
import org.springframework.boot.SpringApplication;

import java.util.Arrays;

/** Sobe o ProStock com o banco de demonstração (prostock_demo). O argumento --recriar-demo gera os dados de novo, com as datas atualizadas. */
public final class ProStockDemoApplication {

    private ProStockDemoApplication() {
    }

    public static void main(String[] args) throws Exception {
        boolean recriar = Arrays.asList(args).contains("--recriar-demo");
        new PreparadorBancoDemo().preparar(recriar);

        SpringApplication aplicacao = new SpringApplication(ProStockApplication.class);
        aplicacao.setAdditionalProfiles("demo");
        aplicacao.run(args);
    }
}
