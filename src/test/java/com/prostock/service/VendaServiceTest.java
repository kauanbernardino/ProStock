package com.prostock.service;

import com.prostock.dto.ItemVendaRequest;
import com.prostock.dto.VendaRequest;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.ItemVenda;
import com.prostock.model.MotivoMovimentacao;
import com.prostock.model.Produto;
import com.prostock.model.Usuario;
import com.prostock.model.Venda;
import com.prostock.model.enums.FormaPagamento;
import com.prostock.model.enums.StatusVenda;
import com.prostock.model.enums.TipoMovimentacao;
import com.prostock.repository.MotivoMovimentacaoRepository;
import com.prostock.repository.MovimentacaoRepository;
import com.prostock.repository.ProdutoRepository;
import com.prostock.repository.VendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendaServiceTest {

    @Mock
    private VendaRepository vendaRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private MotivoMovimentacaoRepository motivoMovimentacaoRepository;
    @Mock
    private MovimentacaoRepository movimentacaoRepository;
    @Mock
    private AlertaService alertaService;

    private VendaService vendaService;

    private Usuario usuario;
    private Produto produto;

    @BeforeEach
    void setUp() {
        vendaService = new VendaService(
                vendaRepository, produtoRepository, motivoMovimentacaoRepository, movimentacaoRepository, alertaService);

        usuario = Usuario.builder().id(1L).nome("Vendedor").build();
        produto = Produto.builder()
                .id(1L).nome("Pastilha").sku("PF-001")
                .preco(new BigDecimal("10.00"))
                .quantidadeAtual(20).estoqueMinimo(5).estoqueMaximo(50)
                .build();

        lenient().when(vendaRepository.save(any(Venda.class))).thenAnswer(inv -> {
            Venda v = inv.getArgument(0);
            if (v.getId() == null) v.setId(1L);
            return v;
        });
    }

    @Test
    void finalizar_deveDecrementarEstoqueEGravarFormaDePagamento() {
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
        when(motivoMovimentacaoRepository.findByDescricaoIgnoreCase("VENDA"))
                .thenReturn(Optional.of(MotivoMovimentacao.builder().id(1L).descricao("VENDA").build()));

        VendaRequest request = new VendaRequest(List.of(new ItemVendaRequest(1L, 5)), FormaPagamento.DINHEIRO);

        Venda venda = vendaService.finalizar(request, usuario);

        assertThat(produto.getQuantidadeAtual()).isEqualTo(15);
        assertThat(venda.getFormaPagamento()).isEqualTo(FormaPagamento.DINHEIRO);
        assertThat(venda.getStatus()).isEqualTo(StatusVenda.CONCLUIDA);
        assertThat(venda.getValorTotal()).isEqualByComparingTo("50.00");
        verify(movimentacaoRepository).save(argThat(m -> m.getTipo() == TipoMovimentacao.SAIDA && m.getQuantidade() == 5));
    }

    @Test
    void cancelar_deveDevolverEstoqueEGerarMovimentacaoDeEstorno() {
        ItemVenda item = ItemVenda.builder().produto(produto).quantidade(5).precoUnitario(new BigDecimal("10.00")).build();
        Venda venda = Venda.builder()
                .id(1L).usuario(usuario).valorTotal(new BigDecimal("50.00"))
                .formaPagamento(FormaPagamento.DINHEIRO).status(StatusVenda.CONCLUIDA)
                .itens(List.of(item))
                .build();
        produto.setQuantidadeAtual(15);

        when(vendaRepository.findById(1L)).thenReturn(Optional.of(venda));
        when(motivoMovimentacaoRepository.findByDescricaoIgnoreCase("ESTORNO"))
                .thenReturn(Optional.of(MotivoMovimentacao.builder().id(2L).descricao("ESTORNO").build()));

        Venda cancelada = vendaService.cancelar(1L, usuario);

        assertThat(produto.getQuantidadeAtual()).isEqualTo(20);
        assertThat(cancelada.getStatus()).isEqualTo(StatusVenda.CANCELADA);
        assertThat(cancelada.getCanceladoPor()).isEqualTo(usuario);
        assertThat(cancelada.getCanceladaEm()).isNotNull();

        ArgumentCaptor<com.prostock.model.Movimentacao> captor = ArgumentCaptor.forClass(com.prostock.model.Movimentacao.class);
        verify(movimentacaoRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(captor.getValue().getQuantidade()).isEqualTo(5);
        assertThat(captor.getValue().getMotivo().getDescricao()).isEqualTo("ESTORNO");

        verify(alertaService).avaliarNivelDeEstoque(produto);
    }

    @Test
    void cancelar_vendaJaCancelada_deveLancarRegraDeNegocio() {
        Venda venda = Venda.builder()
                .id(1L).usuario(usuario).valorTotal(BigDecimal.TEN)
                .formaPagamento(FormaPagamento.PIX).status(StatusVenda.CANCELADA)
                .itens(List.of())
                .build();
        when(vendaRepository.findById(1L)).thenReturn(Optional.of(venda));

        assertThatThrownBy(() -> vendaService.cancelar(1L, usuario))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("já está cancelada");

        verify(produtoRepository, never()).save(any());
    }
}
