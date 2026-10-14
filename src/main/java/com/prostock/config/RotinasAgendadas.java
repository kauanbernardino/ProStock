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


@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class RotinasAgendadas {

    private final AlertaService alertaService;

    /** Todo dia às 7h (app.rotinas.cron-obsolescencia); também roda ao iniciar, caso o computador estivesse desligado no horário. */
    @Scheduled(cron = "${app.rotinas.cron-obsolescencia:0 0 7 * * *}", zone = "America/Sao_Paulo")
    public void verificacaoDiariaDeEstoqueObsoleto() {
        executarVerificacaoDeObsolescencia("agendada");
    }


    @EventListener(ApplicationReadyEvent.class)
    public void verificacaoAoIniciar() {
        executarVerificacaoDeObsolescencia("na inicialização");
    }

    private void executarVerificacaoDeObsolescencia(String origem) {
        try {
            List<Produto> obsoletos = alertaService.verificarEstoqueObsoleto();
            log.info("Verificação de estoque obsoleto ({}): {} produto(s) sem saída no período", origem, obsoletos.size());
        } catch (RuntimeException e) {
            // Uma falha na verificação não deve derrubar a aplicação.
            log.error("Falha na verificação de estoque obsoleto ({})", origem, e);
        }
    }
}
