package com.prostock.service;

import com.prostock.model.Alerta;
import com.prostock.model.Produto;
import com.prostock.model.enums.TipoAlerta;
import com.prostock.repository.AlertaRepository;
import com.prostock.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AlertaService {

    private static final int MESES_PARA_OBSOLESCENCIA = 6;

    private final AlertaRepository alertaRepository;
    private final ProdutoRepository produtoRepository;

    @Transactional(readOnly = true)
    public List<Alerta> listarNaoLidos() {
        return alertaRepository.findByLidoFalseOrderByDataCriacaoDesc();
    }

    @Transactional(readOnly = true)
    public List<Alerta> listarPorProduto(Long produtoId) {
        return alertaRepository.findByProdutoIdOrderByDataCriacaoDesc(produtoId);
    }

    public void marcarComoLido(Long alertaId) {
        Alerta alerta = alertaRepository.findById(alertaId)
                .orElseThrow(() -> new com.prostock.exception.RecursoNaoEncontradoException("Alerta não encontrado: " + alertaId));
        alerta.setLido(true);
        alertaRepository.save(alerta);
    }

    /**
     * UC11: avalia o nível de estoque de um produto após uma movimentação e
     * dispara (ou resolve) alertas de ruptura / estoque baixo.
     */
    public void avaliarNivelDeEstoque(Produto produto) {
        criarSeNecessario(produto, TipoAlerta.RUPTURA, produto.getQuantidadeAtual() <= 0,
                "Produto '" + produto.getNome() + "' (SKU " + produto.getSku() + ") está em ruptura de estoque");

        criarSeNecessario(produto, TipoAlerta.ESTOQUE_BAIXO,
                produto.getQuantidadeAtual() > 0 && produto.getQuantidadeAtual() <= produto.getEstoqueMinimo(),
                "Produto '" + produto.getNome() + "' (SKU " + produto.getSku() + ") atingiu o estoque mínimo");

        if (produto.getQuantidadeAtual() > produto.getEstoqueMinimo()) {
            resolverAlertasAbertos(produto, TipoAlerta.ESTOQUE_BAIXO);
        }
        if (produto.getQuantidadeAtual() > 0) {
            resolverAlertasAbertos(produto, TipoAlerta.RUPTURA);
        }
    }

    private void criarSeNecessario(Produto produto, TipoAlerta tipo, boolean condicao, String mensagem) {
        if (!condicao) {
            return;
        }
        boolean jaExiste = alertaRepository.existsByProdutoIdAndTipoAndLidoFalse(produto.getId(), tipo);
        if (jaExiste) {
            return;
        }
        Alerta alerta = Alerta.builder()
                .produto(produto)
                .tipo(tipo)
                .mensagem(mensagem)
                .lido(false)
                .build();
        alertaRepository.save(alerta);
    }

    private void resolverAlertasAbertos(Produto produto, TipoAlerta tipo) {
        alertaRepository.findFirstByProdutoIdAndTipoAndLidoFalse(produto.getId(), tipo)
                .ifPresent(alerta -> {
                    alerta.setLido(true);
                    alertaRepository.save(alerta);
                });
    }

    /**
     * UC "Alertar Estoque Obsoleto": produtos sem nenhuma saída registrada
     * nos últimos meses definidos em MESES_PARA_OBSOLESCENCIA.
     */
    public List<Produto> verificarEstoqueObsoleto() {
        LocalDateTime limite = LocalDateTime.now().minusMonths(MESES_PARA_OBSOLESCENCIA);
        List<Produto> obsoletos = produtoRepository.findProdutosSemSaidaDesde(limite);

        for (Produto produto : obsoletos) {
            criarSeNecessario(produto, TipoAlerta.ESTOQUE_OBSOLETO, true,
                    "Produto '" + produto.getNome() + "' (SKU " + produto.getSku()
                            + ") está sem movimentação de saída há mais de " + MESES_PARA_OBSOLESCENCIA + " meses");
        }
        return obsoletos;
    }
}
