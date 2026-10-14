package com.prostock.service;

import com.prostock.dto.ItemVendaRequest;
import com.prostock.dto.VendaRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.ItemVenda;
import com.prostock.model.Movimentacao;
import com.prostock.model.MotivoMovimentacao;
import com.prostock.model.Produto;
import com.prostock.model.Usuario;
import com.prostock.model.Venda;
import com.prostock.model.enums.StatusVenda;
import com.prostock.model.enums.TipoMovimentacao;
import com.prostock.repository.MotivoMovimentacaoRepository;
import com.prostock.repository.MovimentacaoRepository;
import com.prostock.repository.ProdutoRepository;
import com.prostock.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class VendaService {

    private static final String MOTIVO_VENDA = "VENDA";
    private static final String MOTIVO_ESTORNO = "ESTORNO";

    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final MotivoMovimentacaoRepository motivoMovimentacaoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final AlertaService alertaService;

    @Transactional(readOnly = true)
    public Venda buscarPorId(Long id) {
        return vendaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Venda não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<Venda> listarTodas() {
        return vendaRepository.findAllByOrderByDataHoraDesc();
    }

    /** Finaliza a venda: confere o estoque de todos os itens, baixa o estoque, grava as movimentações e reavalia os alertas. */
    public Venda finalizar(VendaRequest request, Usuario usuario) {
        // Junta itens repetidos do carrinho.
        Map<Long, Integer> quantidadesPorProduto = new LinkedHashMap<>();
        for (ItemVendaRequest item : request.itens()) {
            quantidadesPorProduto.merge(item.produtoId(), item.quantidade(), Integer::sum);
        }

        Map<Long, Produto> produtos = new LinkedHashMap<>();
        for (Long produtoId : quantidadesPorProduto.keySet()) {
            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado: " + produtoId));
            if (!produto.isAtivo()) {
                throw new RegraDeNegocioException("Produto '" + produto.getNome() + "' não está mais ativo");
            }
            produtos.put(produtoId, produto);
        }

        // Confere todo o estoque antes de alterar qualquer item.
        for (Map.Entry<Long, Integer> entrada : quantidadesPorProduto.entrySet()) {
            Produto produto = produtos.get(entrada.getKey());
            int quantidadeSolicitada = entrada.getValue();
            if (quantidadeSolicitada > produto.getQuantidadeAtual()) {
                throw new RegraDeNegocioException(
                        "Estoque insuficiente para '" + produto.getNome() + "': disponível "
                                + produto.getQuantidadeAtual() + ", solicitado " + quantidadeSolicitada);
            }
        }

        MotivoMovimentacao motivoVenda = motivoMovimentacaoRepository.findByDescricaoIgnoreCase(MOTIVO_VENDA)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo '" + MOTIVO_VENDA + "' não cadastrado"));

        BigDecimal valorTotal = BigDecimal.ZERO;
        Venda venda = Venda.builder()
                .usuario(usuario)
                .valorTotal(BigDecimal.ZERO)
                .formaPagamento(request.formaPagamento())
                .build();

        for (Map.Entry<Long, Integer> entrada : quantidadesPorProduto.entrySet()) {
            Produto produto = produtos.get(entrada.getKey());
            int quantidade = entrada.getValue();

            ItemVenda item = ItemVenda.builder()
                    .venda(venda)
                    .produto(produto)
                    .quantidade(quantidade)
                    .precoUnitario(produto.getPreco())
                    .build();
            venda.getItens().add(item);

            valorTotal = valorTotal.add(produto.getPreco().multiply(BigDecimal.valueOf(quantidade)));
        }
        venda.setValorTotal(valorTotal);

        Venda vendaSalva = vendaRepository.save(venda);

        for (Map.Entry<Long, Integer> entrada : quantidadesPorProduto.entrySet()) {
            Produto produto = produtos.get(entrada.getKey());
            int quantidade = entrada.getValue();

            produto.setQuantidadeAtual(produto.getQuantidadeAtual() - quantidade);
            produtoRepository.save(produto);

            Movimentacao movimentacao = Movimentacao.builder()
                    .produto(produto)
                    .usuario(usuario)
                    .motivo(motivoVenda)
                    .tipo(TipoMovimentacao.SAIDA)
                    .quantidade(quantidade)
                    .dataHora(LocalDateTime.now())
                    .observacao("Venda #" + vendaSalva.getId())
                    .build();
            movimentacaoRepository.save(movimentacao);

            alertaService.avaliarNivelDeEstoque(produto);
        }

        return vendaSalva;
    }

    /** Cancela a venda: devolve o estoque com movimentações de estorno e marca a venda como CANCELADA. */
    public Venda cancelar(Long vendaId, Usuario usuario) {
        Venda venda = buscarPorId(vendaId);

        if (venda.getStatus() == StatusVenda.CANCELADA) {
            throw new RegraDeNegocioException("Esta venda já está cancelada");
        }

        MotivoMovimentacao motivoEstorno = motivoMovimentacaoRepository.findByDescricaoIgnoreCase(MOTIVO_ESTORNO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo '" + MOTIVO_ESTORNO + "' não cadastrado"));

        for (ItemVenda item : venda.getItens()) {
            Produto produto = item.getProduto();
            produto.setQuantidadeAtual(produto.getQuantidadeAtual() + item.getQuantidade());
            produtoRepository.save(produto);

            Movimentacao movimentacao = Movimentacao.builder()
                    .produto(produto)
                    .usuario(usuario)
                    .motivo(motivoEstorno)
                    .tipo(TipoMovimentacao.ENTRADA)
                    .quantidade(item.getQuantidade())
                    .dataHora(LocalDateTime.now())
                    .observacao("Estorno da venda #" + venda.getId())
                    .build();
            movimentacaoRepository.save(movimentacao);

            alertaService.avaliarNivelDeEstoque(produto);
        }

        venda.setStatus(StatusVenda.CANCELADA);
        venda.setCanceladaEm(LocalDateTime.now());
        venda.setCanceladoPor(usuario);

        return vendaRepository.save(venda);
    }
}
