package com.prostock.service;

import com.prostock.dto.GraficoSemanalResponse;
import com.prostock.dto.PrevisaoDemandaResponse;
import com.prostock.dto.SugestaoCompraResponse;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.Movimentacao;
import com.prostock.model.Produto;
import com.prostock.model.enums.TipoMovimentacao;
import com.prostock.repository.AlertaRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private AlertaRepository alertaRepository;
    @Mock
    private MovimentacaoRepository movimentacaoRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(produtoRepository, alertaRepository, movimentacaoRepository);
    }

    private Produto produto(int quantidade, int minimo, int maximo, String preco) {
        Categoria categoria = Categoria.builder().id(1L).nome("Freios").build();
        Fornecedor fornecedor = Fornecedor.builder().id(1L).razaoSocial("Autopeças Rio Grande").build();
        return Produto.builder()
                .id(1L).nome("Pastilha").sku("PF-001")
                .preco(new BigDecimal(preco))
                .quantidadeAtual(quantidade).estoqueMinimo(minimo).estoqueMaximo(maximo)
                .categoria(categoria).fornecedor(fornecedor)
                .build();
    }

    @Test
    void previsaoDemanda_deveCalcularMediaMensalComBaseNosUltimosTresMeses() {
        Produto produto = produto(10, 5, 50, "10.00");
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
        when(movimentacaoRepository.somarSaidasDoProdutoDesde(eq(1L), any(LocalDateTime.class))).thenReturn(90L);

        PrevisaoDemandaResponse resposta = dashboardService.previsaoDemanda(1L);

        assertThat(resposta.totalSaidasUltimos3Meses()).isEqualTo(90L);
        assertThat(resposta.mediaMensalDeSaida()).isEqualByComparingTo("30.00");
        assertThat(resposta.previsaoProximoMes()).isEqualByComparingTo("30.00");
    }

    @Test
    void previsaoDemanda_semSaidasNoPeriodo_deveRetornarMediaZero() {
        Produto produto = produto(10, 5, 50, "10.00");
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
        when(movimentacaoRepository.somarSaidasDoProdutoDesde(eq(1L), any(LocalDateTime.class))).thenReturn(0L);

        PrevisaoDemandaResponse resposta = dashboardService.previsaoDemanda(1L);

        assertThat(resposta.mediaMensalDeSaida()).isEqualByComparingTo("0.00");
    }

    @Test
    void sugestoesDeCompra_produtoDeGiroRapido_deveSugerirCoberturaDeDoisMesesDeVenda() {
        // Média de 10/mês x 2 meses = 20, menos 3 em estoque = 17.
        Produto produto = produto(3, 5, 50, "10.00");
        when(produtoRepository.findProdutosAbaixoDoEstoqueMinimo()).thenReturn(List.of(produto));
        when(movimentacaoRepository.somarSaidasDoProdutoDesde(eq(1L), any(LocalDateTime.class))).thenReturn(30L);

        List<SugestaoCompraResponse> sugestoes = dashboardService.sugestoesDeCompra();

        assertThat(sugestoes).hasSize(1);
        assertThat(sugestoes.get(0).mediaMensalDeSaida()).isEqualByComparingTo("10.00");
        assertThat(sugestoes.get(0).quantidadeSugerida()).isEqualTo(17);
        assertThat(sugestoes.get(0).fornecedorNome()).isEqualTo("Autopeças Rio Grande");
    }

    @Test
    void sugestoesDeCompra_produtoQueQuaseNaoVende_naoDeveSugerirEncherAteOMaximo() {
        // Sem saídas: repõe só até o mínimo (2).
        Produto produto = produto(3, 5, 50, "10.00");
        when(produtoRepository.findProdutosAbaixoDoEstoqueMinimo()).thenReturn(List.of(produto));
        when(movimentacaoRepository.somarSaidasDoProdutoDesde(eq(1L), any(LocalDateTime.class))).thenReturn(0L);

        List<SugestaoCompraResponse> sugestoes = dashboardService.sugestoesDeCompra();

        assertThat(sugestoes.get(0).mediaMensalDeSaida()).isEqualByComparingTo("0.00");
        assertThat(sugestoes.get(0).quantidadeSugerida()).isEqualTo(2);
    }

    @Test
    void sugestoesDeCompra_coberturaNuncaUltrapassaOEstoqueMaximo() {
        // Demanda de 100/mês pediria 200; o teto é o espaço até o máximo (47).
        Produto produto = produto(3, 5, 50, "10.00");
        when(produtoRepository.findProdutosAbaixoDoEstoqueMinimo()).thenReturn(List.of(produto));
        when(movimentacaoRepository.somarSaidasDoProdutoDesde(eq(1L), any(LocalDateTime.class))).thenReturn(300L);

        List<SugestaoCompraResponse> sugestoes = dashboardService.sugestoesDeCompra();

        assertThat(sugestoes.get(0).quantidadeSugerida()).isEqualTo(47);
    }

    @Test
    void graficoSemanal_deveSomarPecasEContarMovimentacoesPorDia() {
        Movimentacao venda1 = Movimentacao.builder().tipo(TipoMovimentacao.SAIDA).quantidade(4).build();
        Movimentacao venda2 = Movimentacao.builder().tipo(TipoMovimentacao.SAIDA).quantidade(1).build();
        Movimentacao compra = Movimentacao.builder().tipo(TipoMovimentacao.ENTRADA).quantidade(30).build();
        when(movimentacaoRepository.findByDataHoraBetweenOrderByDataHoraDesc(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(movimentacaoRepository.findByDataHoraBetweenOrderByDataHoraDesc(
                eq(LocalDate.now().atStartOfDay()), any(LocalDateTime.class)))
                .thenReturn(List.of(venda1, venda2, compra));

        List<GraficoSemanalResponse> semana = dashboardService.graficoSemanal();

        assertThat(semana).hasSize(7);
        assertThat(semana.get(0).dia()).isEqualTo(LocalDate.now().minusDays(6));
        GraficoSemanalResponse hoje = semana.get(6);
        assertThat(hoje.dia()).isEqualTo(LocalDate.now());
        assertThat(hoje.totalSaidas()).isEqualTo(5);
        assertThat(hoje.movimentacoesDeSaida()).isEqualTo(2);
        assertThat(hoje.totalEntradas()).isEqualTo(30);
        assertThat(hoje.movimentacoesDeEntrada()).isEqualTo(1);
        assertThat(semana.get(0).totalSaidas()).isZero();
    }
}
