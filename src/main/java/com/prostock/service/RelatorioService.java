package com.prostock.service;

import com.prostock.dto.RelatorioItemResponse;
import com.prostock.dto.RelatorioResponse;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Produto;
import com.prostock.model.enums.TipoMovimentacao;
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

/**
 * UC03: relatório gerencial de valor em estoque e giro de peças — o
 * administrador escolhe um período (e opcionalmente uma categoria) e o
 * sistema processa os dados do banco e devolve o resumo financeiro.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelatorioService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    public RelatorioResponse gerar(LocalDate inicio, LocalDate fim, Long categoriaId) {
        if (fim.isBefore(inicio)) {
            throw new RegraDeNegocioException("A data final não pode ser anterior à data inicial");
        }

        LocalDateTime inicioDoPeriodo = inicio.atStartOfDay();
        LocalDateTime fimDoPeriodo = fim.atTime(LocalTime.MAX);

        List<Produto> produtos = categoriaId == null
                ? produtoRepository.findByAtivoTrue()
                : produtoRepository.findByCategoriaIdAndAtivoTrue(categoriaId);

        List<RelatorioItemResponse> itens = new ArrayList<>();
        long totalEntradas = 0;
        long totalSaidas = 0;
        long quantidadeAtualTotal = 0;
        BigDecimal valorTotalEmEstoque = BigDecimal.ZERO;
        BigDecimal valorMovimentado = BigDecimal.ZERO;

        for (Produto produto : produtos) {
            long entradas = movimentacaoRepository.somarPorProdutoTipoEPeriodo(
                    produto.getId(), TipoMovimentacao.ENTRADA, inicioDoPeriodo, fimDoPeriodo);
            long saidas = movimentacaoRepository.somarPorProdutoTipoEPeriodo(
                    produto.getId(), TipoMovimentacao.SAIDA, inicioDoPeriodo, fimDoPeriodo);

            BigDecimal valorEmEstoque = produto.getPreco().multiply(BigDecimal.valueOf(produto.getQuantidadeAtual()));
            BigDecimal giro = calcularGiro(saidas, produto.getQuantidadeAtual());

            itens.add(new RelatorioItemResponse(
                    produto.getId(), produto.getSku(), produto.getNome(), produto.getCategoria().getNome(),
                    produto.getQuantidadeAtual(), entradas, saidas, giro, valorEmEstoque));

            totalEntradas += entradas;
            totalSaidas += saidas;
            quantidadeAtualTotal += produto.getQuantidadeAtual();
            valorTotalEmEstoque = valorTotalEmEstoque.add(valorEmEstoque);
            valorMovimentado = valorMovimentado.add(produto.getPreco().multiply(BigDecimal.valueOf(saidas)));
        }

        BigDecimal giroMedio = calcularGiro(totalSaidas, quantidadeAtualTotal);

        return new RelatorioResponse(inicio, fim, valorTotalEmEstoque, valorMovimentado,
                totalEntradas, totalSaidas, giroMedio == null ? BigDecimal.ZERO : giroMedio, itens);
    }

    /**
     * Giro = saídas no período ÷ estoque atual. Sem estoque atual, a divisão
     * não faz sentido (indefinida) — devolve null em vez de forçar um valor
     * artificial, e o front-end mostra "—" nesse caso.
     */
    private BigDecimal calcularGiro(long saidas, long quantidadeAtual) {
        if (quantidadeAtual <= 0) {
            return null;
        }
        return BigDecimal.valueOf(saidas)
                .divide(BigDecimal.valueOf(quantidadeAtual), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP);
    }
}
