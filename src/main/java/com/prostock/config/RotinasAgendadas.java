package com.prostock.config;

import com.prostock.model.Produto;
import com.prostock.service.AlertaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rotinas automáticas do ator "Sistema" (diagrama de casos de uso).
 *
 * Ruptura, estoque baixo e excesso já são avaliados a cada movimentação ou
 * venda (AlertaService.avaliarNivelDeEstoque). O estoque obsoleto depende da
 * passagem do tempo — um produto fica obsoleto justamente porque NADA
 * aconteceu com ele —, então precisa de uma verificação periódica.
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class RotinasAgendadas {

    private final AlertaService alertaService;

    /**
     * Todo dia às 7h (configurável em app.rotinas.cron-obsolescencia).
     */
    @Scheduled(cron = "${app.rotinas.cron-obsolescencia:0 0 7 * * *}", zone = "America/Sao_Paulo")
    public void verificacaoDiariaDeEstoqueObsoleto() {
        executarVerificacaoDeObsolescencia("agendada");
    }

    /**
     * Também roda ao iniciar a aplicação: o sistema roda num computador da
     * loja, que pode estar desligado no horário agendado.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verificacaoAoIniciar() {
        executarVerificacaoDeObsolescencia("na inicialização");
    }

    private void executarVerificacaoDeObsolescencia(String origem) {
        try {
            List<Produto> obsoletos = alertaService.verificarEstoqueObsoleto();
            log.info("Verificação de estoque obsoleto ({}): {} produto(s) sem saída no período", origem, obsoletos.size());
        } catch (RuntimeException e) {
            // Uma falha aqui não pode derrubar a aplicação nem a rotina dos próximos dias.
            log.error("Falha na verificação de estoque obsoleto ({})", origem, e);
        }
    }
}
