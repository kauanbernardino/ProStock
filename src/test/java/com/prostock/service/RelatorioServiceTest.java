package com.prostock.service;

import com.prostock.dto.RelatorioResponse;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.Produto;
import com.prostock.model.enums.TipoMovimentacao;
import com.prostock.repository.MovimentacaoRepository;
import com.prostock.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private MovimentacaoRepository movimentacaoRepository;

    private RelatorioService relatorioService;

    @BeforeEach
    void setUp() {
        relatorioService = new RelatorioService(produtoRepository, movimentacaoRepository);
    }

    private Produto produto(long id, int quantidade, String preco) {
        Categoria categoria = Categoria.builder().id(1L).nome("Freios").build();
        Fornecedor fornecedor = Fornecedor.builder().id(1L).razaoSocial("Autopeças Rio Grande").build();
        return Produto.builder()
                .id(id).nome("Pastilha de Freio").sku("PF-00" + id)
                .preco(new BigDecimal(preco)).quantidadeAtual(quantidade)
                .estoqueMinimo(5).estoqueMaximo(50)
                .categoria(categoria).fornecedor(fornecedor)
                .build();
    }

    @Test
    void gerar_deveCalcularValorEmEstoqueEGiroPorProduto() {
        Produto produto = produto(1L, 20, "10.00");
        when(produtoRepository.findByAtivoTrue()).thenReturn(List.of(produto));
        when(movimentacaoRepository.somarPorProdutoTipoEPeriodo(
                eq(1L), eq(TipoMovimentacao.ENTRADA), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(15L);
        when(movimentacaoRepository.somarPorProdutoTipoEPeriodo(
                eq(1L), eq(TipoMovimentacao.SAIDA), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(10L);

        RelatorioResponse relatorio = relatorioService.gerar(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null);

        assertThat(relatorio.itens()).hasSize(1);
        assertThat(relatorio.itens().get(0).entradas()).isEqualTo(15L);
        assertThat(relatorio.itens().get(0).saidas()).isEqualTo(10L);
        // giro = saídas (10) / estoque atual (20) * 100 = 50%
        assertThat(relatorio.itens().get(0).giroPercentual()).isEqualByComparingTo("50");
        assertThat(relatorio.itens().get(0).valorEmEstoque()).isEqualByComparingTo("200.00");
        assertThat(relatorio.valorTotalEmEstoque()).isEqualByComparingTo("200.00");
        assertThat(relatorio.valorMovimentado()).isEqualByComparingTo("100.00");
        assertThat(relatorio.giroMedio()).isEqualByComparingTo("50");
    }

    @Test
    void gerar_produtoSemEstoqueAtual_deveDevolverGiroNulo() {
        Produto produto = produto(1L, 0, "10.00");
        when(produtoRepository.findByAtivoTrue()).thenReturn(List.of(produto));
        when(movimentacaoRepository.somarPorProdutoTipoEPeriodo(any(), any(), any(), any())).thenReturn(0L);

        RelatorioResponse relatorio = relatorioService.gerar(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null);

        assertThat(relatorio.itens().get(0).giroPercentual()).isNull();
    }

    @Test
    void gerar_filtraPorCategoriaQuandoInformada() {
        when(produtoRepository.findByCategoriaIdAndAtivoTrue(9L)).thenReturn(List.of());

        RelatorioResponse relatorio = relatorioService.gerar(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 9L);

        assertThat(relatorio.itens()).isEmpty();
    }

    @Test
    void gerar_dataFinalAntesDaInicial_deveLancarRegraDeNegocio() {
        assertThatThrownBy(() -> relatorioService.gerar(
                LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1), null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("data final");
    }
}
