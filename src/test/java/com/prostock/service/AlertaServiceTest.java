package com.prostock.service;

import com.prostock.model.Alerta;
import com.prostock.model.Produto;
import com.prostock.model.enums.TipoAlerta;
import com.prostock.repository.AlertaRepository;
import com.prostock.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    @Mock
    private AlertaRepository alertaRepository;
    @Mock
    private ProdutoRepository produtoRepository;

    private AlertaService alertaService;

    @BeforeEach
    void setUp() {
        alertaService = new AlertaService(alertaRepository, produtoRepository);
    }

    private Produto produtoCom(int quantidade, int minimo) {
        return Produto.builder()
                .id(1L)
                .nome("Pastilha de Freio")
                .sku("PF-001")
                .quantidadeAtual(quantidade)
                .estoqueMinimo(minimo)
                .estoqueMaximo(50)
                .build();
    }

    @Test
    void estoqueAbaixoDoMinimo_deveCriarAlertaDeEstoqueBaixoQuandoNaoExisteUmNaoLido() {
        Produto produto = produtoCom(3, 5);
        when(alertaRepository.existsByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.ESTOQUE_BAIXO)).thenReturn(false);

        alertaService.avaliarNivelDeEstoque(produto);

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoAlerta.ESTOQUE_BAIXO);
        assertThat(captor.getValue().getProduto()).isEqualTo(produto);
    }

    @Test
    void estoqueAbaixoDoMinimo_naoDeveDuplicarAlertaSeJaExisteUmNaoLido() {
        Produto produto = produtoCom(3, 5);
        when(alertaRepository.existsByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.ESTOQUE_BAIXO)).thenReturn(true);

        alertaService.avaliarNivelDeEstoque(produto);

        verify(alertaRepository, never()).save(any());
    }

    @Test
    void estoqueZerado_deveCriarAlertaDeRuptura() {
        Produto produto = produtoCom(0, 5);
        when(alertaRepository.existsByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.RUPTURA)).thenReturn(false);

        alertaService.avaliarNivelDeEstoque(produto);

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoAlerta.RUPTURA);
    }

    @Test
    void estoqueAcimaDoMinimo_deveResolverAlertasAbertosDeBaixoERuptura() {
        Produto produto = produtoCom(20, 5);
        Alerta alertaBaixoAberto = Alerta.builder().id(1L).produto(produto).tipo(TipoAlerta.ESTOQUE_BAIXO).lido(false).build();
        Alerta alertaRupturaAberto = Alerta.builder().id(2L).produto(produto).tipo(TipoAlerta.RUPTURA).lido(false).build();

        when(alertaRepository.findFirstByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.ESTOQUE_BAIXO))
                .thenReturn(Optional.of(alertaBaixoAberto));
        when(alertaRepository.findFirstByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.RUPTURA))
                .thenReturn(Optional.of(alertaRupturaAberto));
        when(alertaRepository.findFirstByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.ESTOQUE_EXCESSO))
                .thenReturn(Optional.empty());

        alertaService.avaliarNivelDeEstoque(produto);

        assertThat(alertaBaixoAberto.isLido()).isTrue();
        assertThat(alertaRupturaAberto.isLido()).isTrue();
        verify(alertaRepository).save(alertaBaixoAberto);
        verify(alertaRepository).save(alertaRupturaAberto);
        verify(alertaRepository, never()).save(argThat(a -> a != alertaBaixoAberto && a != alertaRupturaAberto));
    }

    @Test
    void marcarComoLido_deveAtualizarFlagLido() {
        Alerta alerta = Alerta.builder().id(1L).lido(false).build();
        when(alertaRepository.findById(1L)).thenReturn(Optional.of(alerta));

        alertaService.marcarComoLido(1L);

        assertThat(alerta.isLido()).isTrue();
        verify(alertaRepository).save(alerta);
    }

    // Alerta de excesso

    private Produto produtoComMaximo(int quantidade, int maximo) {
        return Produto.builder()
                .id(1L).nome("Pastilha de Freio").sku("PF-001")
                .quantidadeAtual(quantidade).estoqueMinimo(5).estoqueMaximo(maximo)
                .build();
    }

    @Test
    void estoqueAcimaDoMaximo_deveCriarAlertaDeExcesso() {
        Produto produto = produtoComMaximo(60, 50);
        when(alertaRepository.existsByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.ESTOQUE_EXCESSO)).thenReturn(false);

        alertaService.avaliarNivelDeEstoque(produto);

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoAlerta.ESTOQUE_EXCESSO);
        assertThat(captor.getValue().getMensagem()).contains("ultrapassou o estoque máximo");
    }

    @Test
    void estoqueExatamenteNoMaximo_naoDeveCriarAlertaDeExcesso() {
        Produto produto = produtoComMaximo(50, 50);

        alertaService.avaliarNivelDeEstoque(produto);

        verify(alertaRepository, never()).existsByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.ESTOQUE_EXCESSO);
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void estoqueVoltaAoLimite_deveResolverAlertaDeExcessoAberto() {
        Produto produto = produtoComMaximo(40, 50);
        Alerta excessoAberto = Alerta.builder().id(3L).produto(produto).tipo(TipoAlerta.ESTOQUE_EXCESSO).lido(false).build();
        when(alertaRepository.findFirstByProdutoIdAndTipoAndLidoFalse(eq(1L), any(TipoAlerta.class)))
                .thenReturn(Optional.empty());
        when(alertaRepository.findFirstByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.ESTOQUE_EXCESSO))
                .thenReturn(Optional.of(excessoAberto));

        alertaService.avaliarNivelDeEstoque(produto);

        assertThat(excessoAberto.isLido()).isTrue();
        verify(alertaRepository).save(excessoAberto);
    }

    // Estoque obsoleto

    @Test
    void verificarEstoqueObsoleto_produtoSemAlertaRecente_deveCriarAlerta() {
        Produto parado = produtoCom(8, 5);
        when(produtoRepository.findProdutosSemSaidaDesde(any(LocalDateTime.class))).thenReturn(List.of(parado));
        when(alertaRepository.existsByProdutoIdAndTipoAndDataCriacaoAfter(
                eq(1L), eq(TipoAlerta.ESTOQUE_OBSOLETO), any(LocalDateTime.class))).thenReturn(false);
        when(alertaRepository.existsByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.ESTOQUE_OBSOLETO)).thenReturn(false);

        List<Produto> obsoletos = alertaService.verificarEstoqueObsoleto();

        assertThat(obsoletos).containsExactly(parado);
        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoAlerta.ESTOQUE_OBSOLETO);
    }

    @Test
    void verificarEstoqueObsoleto_produtoJaAlertadoNosUltimos30Dias_naoDeveRepetirAlerta() {
        // Alerta lido ontem não é recriado pela verificação de hoje.
        Produto parado = produtoCom(8, 5);
        when(produtoRepository.findProdutosSemSaidaDesde(any(LocalDateTime.class))).thenReturn(List.of(parado));
        when(alertaRepository.existsByProdutoIdAndTipoAndDataCriacaoAfter(
                eq(1L), eq(TipoAlerta.ESTOQUE_OBSOLETO), any(LocalDateTime.class))).thenReturn(true);

        List<Produto> obsoletos = alertaService.verificarEstoqueObsoleto();

        assertThat(obsoletos).containsExactly(parado);
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void produtoZerado_deveResolverEstoqueBaixoAbertoEFicarSoComRuptura() {
        // Ao zerar, fica só o alerta de ruptura.
        Produto produto = produtoCom(0, 5);
        Alerta baixoAberto = Alerta.builder().id(7L).produto(produto).tipo(TipoAlerta.ESTOQUE_BAIXO).lido(false).build();
        when(alertaRepository.existsByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.RUPTURA)).thenReturn(false);
        when(alertaRepository.findFirstByProdutoIdAndTipoAndLidoFalse(eq(1L), any(TipoAlerta.class)))
                .thenReturn(Optional.empty());
        when(alertaRepository.findFirstByProdutoIdAndTipoAndLidoFalse(1L, TipoAlerta.ESTOQUE_BAIXO))
                .thenReturn(Optional.of(baixoAberto));

        alertaService.avaliarNivelDeEstoque(produto);

        assertThat(baixoAberto.isLido()).isTrue();
        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Alerta::getTipo)
                .containsExactlyInAnyOrder(TipoAlerta.RUPTURA, TipoAlerta.ESTOQUE_BAIXO);
    }

    @Test
    void encerrarAlertasAbertos_deveMarcarTodosOsAlertasAbertosDoProdutoComoLidos() {
        Produto produto = produtoCom(0, 5);
        Alerta ruptura = Alerta.builder().produto(produto).tipo(TipoAlerta.RUPTURA).lido(false).build();
        Alerta obsoleto = Alerta.builder().produto(produto).tipo(TipoAlerta.ESTOQUE_OBSOLETO).lido(false).build();
        when(alertaRepository.findByProdutoIdAndLidoFalse(1L)).thenReturn(List.of(ruptura, obsoleto));

        alertaService.encerrarAlertasAbertos(produto);

        assertThat(ruptura.isLido()).isTrue();
        assertThat(obsoleto.isLido()).isTrue();
        verify(alertaRepository).saveAll(List.of(ruptura, obsoleto));
    }
}
