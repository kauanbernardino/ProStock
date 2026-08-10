package com.prostock.controller;

import com.prostock.dto.DashboardResponse;
import com.prostock.dto.GraficoSemanalResponse;
import com.prostock.dto.PrevisaoDemandaResponse;
import com.prostock.dto.SugestaoCompraResponse;
import com.prostock.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardResponse dashboard() {
        return dashboardService.obterDashboard();
    }

    @GetMapping("/sugestoes-compra")
    public List<SugestaoCompraResponse> sugestoesDeCompra() {
        return dashboardService.sugestoesDeCompra();
    }

    @GetMapping("/previsao-demanda")
    public List<PrevisaoDemandaResponse> previsaoDemandaGeral() {
        return dashboardService.previsaoDemandaGeral();
    }

    @GetMapping("/previsao-demanda/{produtoId}")
    public PrevisaoDemandaResponse previsaoDemanda(@PathVariable Long produtoId) {
        return dashboardService.previsaoDemanda(produtoId);
    }

    @GetMapping("/grafico-semanal")
    public List<GraficoSemanalResponse> graficoSemanal() {
        return dashboardService.graficoSemanal();
    }
}
