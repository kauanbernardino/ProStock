package com.prostock.demo;

import com.prostock.ProStockApplication;
import org.springframework.boot.SpringApplication;

import java.util.Arrays;

/**
 * Sobe o ProStock apontando para o banco de DEMONSTRAÇÃO (prostock_demo), com
 * ~7 meses de operação simulada de uma loja de autopeças.
 *
 * Na primeira execução o banco é criado e populado automaticamente. Para
 * gerar tudo de novo (com as datas atualizadas para "hoje"), rode com o
 * argumento --recriar-demo — útil na véspera da apresentação, para o gráfico
 * da semana e a previsão de demanda refletirem os últimos dias.
 *
 * O banco de uso real (prostock) não é tocado.
 */
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
