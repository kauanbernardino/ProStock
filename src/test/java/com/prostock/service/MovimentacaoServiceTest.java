package com.prostock.service;

import com.prostock.dto.MovimentacaoRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.MotivoMovimentacao;
import com.prostock.model.Movimentacao;
import com.prostock.model.Produto;
import com.prostock.model.Usuario;
import com.prostock.model.enums.TipoMovimentacao;
import com.prostock.repository.MotivoMovimentacaoRepository;
import com.prostock.repository.MovimentacaoRepository;
import com.prostock.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovimentacaoServiceTest {

    @Mock
    private MovimentacaoRepository movimentacaoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private MotivoMovimentacaoRepository motivoMovimentacaoRepository;
    @Mock
    private AlertaService alertaService;

    private MovimentacaoService movimentacaoService;

    private Produto produto;
    private Usuario usuario;
    private MotivoMovimentacao motivo;

    @BeforeEach
    void setUp() {
        movimentacaoService = new MovimentacaoService(
                movimentacaoRepository, produtoRepository, motivoMovimentacaoRepository, alertaService);

        produto = Produto.builder()
                .id(1L)
                .nome("Pastilha de Freio")
                .sku("PF-001")
                .preco(new BigDecimal("89.90"))
                .quantidadeAtual(10)
                .estoqueMinimo(5)
                .estoqueMaximo(50)
                .build();

        usuario = Usuario.builder().id(1L).nome("Vendedor").build();
        motivo = MotivoMovimentacao.builder().id(1L).descricao("VENDA").build();

        lenient().when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
        lenient().when(motivoMovimentacaoRepository.findById(1L)).thenReturn(Optional.of(motivo));
        lenient().when(movimentacaoRepository.save(any(Movimentacao.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void saidaComEstoqueSuficiente_deveReduzirQuantidadeEDispararAvaliacaoDeAlerta() {
        MovimentacaoRequest request = new MovimentacaoRequest(1L, 1L, TipoMovimentacao.SAIDA, 4, "venda balcão");

        movimentacaoService.registrar(request, usuario);

        assertThat(produto.getQuantidadeAtual()).isEqualTo(6);
        verify(produtoRepository).save(produto);
        verify(alertaService).avaliarNivelDeEstoque(produto);
    }

    @Test
    void saidaComEstoqueInsuficiente_deveLancarRegraDeNegocioENaoAlterarEstoque() {
        MovimentacaoRequest request = new MovimentacaoRequest(1L, 1L, TipoMovimentacao.SAIDA, 999, null);

        assertThatThrownBy(() -> movimentacaoService.registrar(request, usuario))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Estoque insuficiente");

        assertThat(produto.getQuantidadeAtual()).isEqualTo(10);
        verify(produtoRepository, never()).save(any());
        verify(movimentacaoRepository, never()).save(any());
    }

    @Test
    void entrada_deveAumentarQuantidade() {
        MovimentacaoRequest request = new MovimentacaoRequest(1L, 1L, TipoMovimentacao.ENTRADA, 15, "compra");

        Movimentacao salva = movimentacaoService.registrar(request, usuario);

        assertThat(produto.getQuantidadeAtual()).isEqualTo(25);
        assertThat(salva.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(salva.getQuantidade()).isEqualTo(15);
    }

    @Test
    void produtoInexistente_deveLancarRecursoNaoEncontrado() {
        when(produtoRepository.findById(99L)).thenReturn(Optional.empty());
        MovimentacaoRequest request = new MovimentacaoRequest(99L, 1L, TipoMovimentacao.ENTRADA, 1, null);

        assertThatThrownBy(() -> movimentacaoService.registrar(request, usuario))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
