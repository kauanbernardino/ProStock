package com.prostock.service;

import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.MotivoMovimentacao;
import com.prostock.repository.MotivoMovimentacaoRepository;
import com.prostock.repository.MovimentacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MotivoMovimentacaoServiceTest {

    @Mock
    private MotivoMovimentacaoRepository motivoMovimentacaoRepository;
    @Mock
    private MovimentacaoRepository movimentacaoRepository;

    private MotivoMovimentacaoService service;

    @BeforeEach
    void setUp() {
        service = new MotivoMovimentacaoService(motivoMovimentacaoRepository, movimentacaoRepository);
    }

    private MotivoMovimentacao motivo(long id, String descricao) {
        return MotivoMovimentacao.builder().id(id).descricao(descricao).build();
    }

    @Test
    void remover_motivoUsadoPeloSistema_deveSerBloqueado() {
        when(motivoMovimentacaoRepository.findById(2L)).thenReturn(Optional.of(motivo(2L, "VENDA")));

        assertThatThrownBy(() -> service.remover(2L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("usado automaticamente pelo sistema");

        verify(motivoMovimentacaoRepository, never()).delete(any());
    }

    @Test
    void remover_motivoJaUsadoEmMovimentacoes_deveSerBloqueado() {
        when(motivoMovimentacaoRepository.findById(7L)).thenReturn(Optional.of(motivo(7L, "BRINDE")));
        when(movimentacaoRepository.existsByMotivoId(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.remover(7L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("já foi usado em movimentações");

        verify(motivoMovimentacaoRepository, never()).delete(any());
    }

    @Test
    void remover_motivoLivre_deveRemover() {
        MotivoMovimentacao brinde = motivo(7L, "BRINDE");
        when(motivoMovimentacaoRepository.findById(7L)).thenReturn(Optional.of(brinde));
        when(movimentacaoRepository.existsByMotivoId(7L)).thenReturn(false);

        service.remover(7L);

        verify(motivoMovimentacaoRepository).delete(brinde);
    }

    @Test
    void criar_comDescricaoEmBranco_deveSerBloqueado() {
        assertThatThrownBy(() -> service.criar("   "))
                .isInstanceOf(RegraDeNegocioException.class);

        verifyNoInteractions(motivoMovimentacaoRepository);
    }
}
