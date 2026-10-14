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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class MovimentacaoService {

    private final MovimentacaoRepository movimentacaoRepository;
    private final ProdutoRepository produtoRepository;
    private final MotivoMovimentacaoRepository motivoMovimentacaoRepository;
    private final AlertaService alertaService;

    /** Motivos lançados apenas pelo sistema (cancelamento de venda e UC07). */
    static final Set<String> MOTIVOS_EXCLUSIVOS_DO_SISTEMA = Set.of("ESTORNO", "AJUSTE_INVENTARIO");
    private static final String MOTIVO_VENDA = "VENDA";

    /** Movimentação da tela de Movimentações: bloqueia os motivos do sistema e aceita VENDA só como saída. */
    public Movimentacao registrarManual(MovimentacaoRequest request, Usuario usuario) {
        MotivoMovimentacao motivo = motivoMovimentacaoRepository.findById(request.motivoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo não encontrado: " + request.motivoId()));
        String descricao = motivo.getDescricao().toUpperCase();

        if (MOTIVOS_EXCLUSIVOS_DO_SISTEMA.contains(descricao)) {
            throw new RegraDeNegocioException("O motivo '" + motivo.getDescricao()
                    + "' é lançado automaticamente pelo sistema e não pode ser usado em uma movimentação manual");
        }
        if (MOTIVO_VENDA.equals(descricao) && request.tipo() != TipoMovimentacao.SAIDA) {
            throw new RegraDeNegocioException("Uma movimentação com o motivo VENDA precisa ser do tipo saída");
        }
        return registrar(request, usuario);
    }

    /** UC02/UC04/UC05: registra a movimentação, atualiza o estoque e reavalia os alertas. */
    public Movimentacao registrar(MovimentacaoRequest request, Usuario usuario) {
        Produto produto = produtoRepository.findById(request.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado: " + request.produtoId()));
        if (!produto.isAtivo()) {
            throw new RegraDeNegocioException("Produto '" + produto.getNome() + "' está inativo; reative-o antes de movimentar o estoque");
        }
        MotivoMovimentacao motivo = motivoMovimentacaoRepository.findById(request.motivoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo não encontrado: " + request.motivoId()));

        int quantidadeAtual = produto.getQuantidadeAtual();
        int novaQuantidade;

        if (request.tipo() == TipoMovimentacao.SAIDA) {
            if (request.quantidade() > quantidadeAtual) {
                throw new RegraDeNegocioException(
                        "Estoque insuficiente para '" + produto.getNome() + "': disponível "
                                + quantidadeAtual + ", solicitado " + request.quantidade());
            }
            novaQuantidade = quantidadeAtual - request.quantidade();
        } else {
            novaQuantidade = quantidadeAtual + request.quantidade();
        }

        produto.setQuantidadeAtual(novaQuantidade);
        produtoRepository.save(produto);

        Movimentacao movimentacao = Movimentacao.builder()
                .produto(produto)
                .usuario(usuario)
                .motivo(motivo)
                .tipo(request.tipo())
                .quantidade(request.quantidade())
                .dataHora(LocalDateTime.now())
                .observacao(request.observacao())
                .build();
        Movimentacao salva = movimentacaoRepository.save(movimentacao);

        alertaService.avaliarNivelDeEstoque(produto);

        return salva;
    }

    @Transactional(readOnly = true)
    public List<Movimentacao> historicoDoProduto(Long produtoId) {
        return movimentacaoRepository.findByProdutoIdOrderByDataHoraDesc(produtoId);
    }

    @Transactional(readOnly = true)
    public List<Movimentacao> listarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return movimentacaoRepository.findByDataHoraBetweenOrderByDataHoraDesc(inicio, fim);
    }
}
