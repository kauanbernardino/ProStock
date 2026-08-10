package com.prostock.service;

import com.prostock.dto.PrevisaoDemandaResponse;
import com.prostock.dto.SugestaoCompraResponse;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.Produto;
import com.prostock.repository.AlertaRepository;
import com.prostock.repository.MovimentacaoRepository;
import com.prostock.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
        // Vende em média 10 un./mês (30 nos últimos 3 meses) — cobertura de 2 meses = 20,
        // menos o que já tem em estoque (3) = 17.
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
        // Sem saídas nos últimos 3 meses — a sugestão deve se limitar a repor o mínimo (2),
        // e não mais encher até o máximo (47) como a versão antiga fazia.
        Produto produto = produto(3, 5, 50, "10.00");
        when(produtoRepository.findProdutosAbaixoDoEstoqueMinimo()).thenReturn(List.of(produto));
        when(movimentacaoRepository.somarSaidasDoProdutoDesde(eq(1L), any(LocalDateTime.class))).thenReturn(0L);

        List<SugestaoCompraResponse> sugestoes = dashboardService.sugestoesDeCompra();

        assertThat(sugestoes.get(0).mediaMensalDeSaida()).isEqualByComparingTo("0.00");
        assertThat(sugestoes.get(0).quantidadeSugerida()).isEqualTo(2);
    }

    @Test
    void sugestoesDeCompra_coberturaNuncaUltrapassaOEstoqueMaximo() {
        // Demanda altíssima (300 nos últimos 3 meses = 100/mês) pediria 200 de cobertura,
        // mas o espaço até o máximo (50 - 3 = 47) deve ser o teto.
        Produto produto = produto(3, 5, 50, "10.00");
        when(produtoRepository.findProdutosAbaixoDoEstoqueMinimo()).thenReturn(List.of(produto));
        when(movimentacaoRepository.somarSaidasDoProdutoDesde(eq(1L), any(LocalDateTime.class))).thenReturn(300L);

        List<SugestaoCompraResponse> sugestoes = dashboardService.sugestoesDeCompra();

        assertThat(sugestoes.get(0).quantidadeSugerida()).isEqualTo(47);
    }
}
