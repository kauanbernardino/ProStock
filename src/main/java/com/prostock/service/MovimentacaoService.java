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

@Service
@RequiredArgsConstructor
@Transactional
public class MovimentacaoService {

    private final MovimentacaoRepository movimentacaoRepository;
    private final ProdutoRepository produtoRepository;
    private final MotivoMovimentacaoRepository motivoMovimentacaoRepository;
    private final AlertaService alertaService;

    /**
     * UC02/UC04/UC05: registra entrada, saída, devolução ou avaria como uma
     * movimentação, atualizando a quantidade em estoque do produto e
     * disparando os alertas cabíveis (UC11).
     */
    public Movimentacao registrar(MovimentacaoRequest request, Usuario usuario) {
        Produto produto = produtoRepository.findById(request.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado: " + request.produtoId()));
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
