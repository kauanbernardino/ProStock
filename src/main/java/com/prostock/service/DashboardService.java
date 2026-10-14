package com.prostock.service;

import com.prostock.dto.DashboardResponse;
import com.prostock.dto.GraficoSemanalResponse;
import com.prostock.dto.PrevisaoDemandaResponse;
import com.prostock.dto.SugestaoCompraResponse;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.model.Produto;
import com.prostock.model.enums.TipoMovimentacao;
import com.prostock.repository.AlertaRepository;
import com.prostock.repository.MovimentacaoRepository;
import com.prostock.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int MESES_PREVISAO_DEMANDA = 3;
    private static final int MESES_COBERTURA_SUGESTAO = 2;

    private final ProdutoRepository produtoRepository;
    private final AlertaRepository alertaRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    /** UC13: indicadores do painel geral. */
    public DashboardResponse obterDashboard() {
        BigDecimal valorTotal = produtoRepository.calcularValorTotalEmEstoque();
        Long quantidadeTotal = produtoRepository.calcularQuantidadeTotalEmEstoque();
        long totalProdutos = produtoRepository.findByAtivoTrue().size();
        long abaixoDoMinimo = produtoRepository.findProdutosAbaixoDoEstoqueMinimo().size();
        long emRuptura = produtoRepository.findProdutosEmRuptura().size();
        long acimaDoMaximo = produtoRepository.findProdutosAcimaDoEstoqueMaximo().size();
        long alertasNaoLidos = alertaRepository.findByLidoFalseOrderByDataCriacaoDesc().size();

        return new DashboardResponse(
                valorTotal, quantidadeTotal, totalProdutos, abaixoDoMinimo, emRuptura, acimaDoMaximo, alertasNaoLidos);
    }

    /** UC09: sugestão de compra. Cobre MESES_COBERTURA_SUGESTAO meses da demanda média, no mínimo até o estoque mínimo e no máximo até o estoque máximo. */
    public List<SugestaoCompraResponse> sugestoesDeCompra() {
        return produtoRepository.findProdutosAbaixoDoEstoqueMinimo().stream()
                .map(p -> {
                    BigDecimal mediaMensal = calcularMediaMensalDeSaida(p.getId());

                    int coberturaDesejada = mediaMensal
                            .multiply(BigDecimal.valueOf(MESES_COBERTURA_SUGESTAO))
                            .setScale(0, RoundingMode.HALF_UP)
                            .intValue();

                    int reposicaoParaMinimo = Math.max(0, p.getEstoqueMinimo() - p.getQuantidadeAtual());
                    int reposicaoParaCobertura = Math.max(0, coberturaDesejada - p.getQuantidadeAtual());
                    int espacoDisponivel = Math.max(0, p.getEstoqueMaximo() - p.getQuantidadeAtual());

                    int quantidadeSugerida = Math.min(espacoDisponivel, Math.max(reposicaoParaMinimo, reposicaoParaCobertura));

                    return new SugestaoCompraResponse(
                            p.getId(),
                            p.getNome(),
                            p.getSku(),
                            p.getQuantidadeAtual(),
                            p.getEstoqueMinimo(),
                            p.getEstoqueMaximo(),
                            mediaMensal,
                            quantidadeSugerida,
                            p.getFornecedor().getRazaoSocial()
                    );
                })
                .toList();
    }

    /** UC12: média mensal de saídas dos últimos 3 meses. */
    public PrevisaoDemandaResponse previsaoDemanda(Long produtoId) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado: " + produtoId));

        LocalDateTime desde = LocalDateTime.now().minusMonths(MESES_PREVISAO_DEMANDA);
        Long totalSaidas = movimentacaoRepository.somarSaidasDoProdutoDesde(produtoId, desde);
        BigDecimal mediaMensal = BigDecimal.valueOf(totalSaidas)
                .divide(BigDecimal.valueOf(MESES_PREVISAO_DEMANDA), 2, RoundingMode.HALF_UP);

        return new PrevisaoDemandaResponse(
                produto.getId(), produto.getNome(), produto.getSku(), totalSaidas, mediaMensal, mediaMensal);
    }

    private BigDecimal calcularMediaMensalDeSaida(Long produtoId) {
        LocalDateTime desde = LocalDateTime.now().minusMonths(MESES_PREVISAO_DEMANDA);
        Long totalSaidas = movimentacaoRepository.somarSaidasDoProdutoDesde(produtoId, desde);

        return BigDecimal.valueOf(totalSaidas)
                .divide(BigDecimal.valueOf(MESES_PREVISAO_DEMANDA), 2, RoundingMode.HALF_UP);
    }

    public List<PrevisaoDemandaResponse> previsaoDemandaGeral() {
        return produtoRepository.findByAtivoTrue().stream()
                .map(p -> previsaoDemanda(p.getId()))
                .toList();
    }

    /** Entradas e saídas de cada um dos últimos 7 dias. */
    public List<GraficoSemanalResponse> graficoSemanal() {
        List<GraficoSemanalResponse> resultado = new ArrayList<>();
        LocalDate hoje = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate dia = hoje.minusDays(i);
            LocalDateTime inicio = dia.atStartOfDay();
            LocalDateTime fim = dia.atTime(LocalTime.MAX);

            var movimentacoesDoDia = movimentacaoRepository.findByDataHoraBetweenOrderByDataHoraDesc(inicio, fim);
            var entradas = movimentacoesDoDia.stream().filter(m -> m.getTipo() == TipoMovimentacao.ENTRADA).toList();
            var saidas = movimentacoesDoDia.stream().filter(m -> m.getTipo() == TipoMovimentacao.SAIDA).toList();

            resultado.add(new GraficoSemanalResponse(
                    dia,
                    entradas.stream().mapToLong(m -> m.getQuantidade()).sum(),
                    saidas.stream().mapToLong(m -> m.getQuantidade()).sum(),
                    entradas.size(),
                    saidas.size()));
        }

        return resultado;
    }
}
