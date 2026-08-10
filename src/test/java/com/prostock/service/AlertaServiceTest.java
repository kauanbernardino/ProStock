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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
