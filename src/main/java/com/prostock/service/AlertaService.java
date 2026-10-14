package com.prostock.service;

import com.prostock.exception.RecursoNaoEncontradoException;
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
    /** Intervalo mínimo entre dois alertas de obsolescência do mesmo produto. */
    private static final int DIAS_ENTRE_ALERTAS_DE_OBSOLESCENCIA = 30;

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
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alerta não encontrado: " + alertaId));
        alerta.setLido(true);
        alertaRepository.save(alerta);
    }

    /** UC11: cria ou resolve os alertas de ruptura, estoque baixo e excesso. */
    public void avaliarNivelDeEstoque(Produto produto) {
        int quantidade = produto.getQuantidadeAtual();

        criarSeNecessario(produto, TipoAlerta.RUPTURA, quantidade <= 0,
                "Produto '" + produto.getNome() + "' (SKU " + produto.getSku() + ") está em ruptura de estoque");

        criarSeNecessario(produto, TipoAlerta.ESTOQUE_BAIXO,
                quantidade > 0 && quantidade <= produto.getEstoqueMinimo(),
                "Produto '" + produto.getNome() + "' (SKU " + produto.getSku() + ") atingiu o estoque mínimo");

        criarSeNecessario(produto, TipoAlerta.ESTOQUE_EXCESSO, quantidade > produto.getEstoqueMaximo(),
                "Produto '" + produto.getNome() + "' (SKU " + produto.getSku() + ") ultrapassou o estoque máximo ("
                        + quantidade + " de no máximo " + produto.getEstoqueMaximo() + ")");

        // Ao zerar, o estoque baixo dá lugar à ruptura.
        if (quantidade > produto.getEstoqueMinimo() || quantidade <= 0) {
            resolverAlertasAbertos(produto, TipoAlerta.ESTOQUE_BAIXO);
        }
        if (quantidade > 0) {
            resolverAlertasAbertos(produto, TipoAlerta.RUPTURA);
        }
        if (quantidade <= produto.getEstoqueMaximo()) {
            resolverAlertasAbertos(produto, TipoAlerta.ESTOQUE_EXCESSO);
        }
    }

    /** Ao inativar o produto, encerra os alertas abertos dele. */
    public void encerrarAlertasAbertos(Produto produto) {
        List<Alerta> abertos = alertaRepository.findByProdutoIdAndLidoFalse(produto.getId());
        abertos.forEach(alerta -> alerta.setLido(true));
        alertaRepository.saveAll(abertos);
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

    /** Produtos ativos, cadastrados há mais de MESES_PARA_OBSOLESCENCIA meses, sem saída no período. */
    public List<Produto> verificarEstoqueObsoleto() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime limite = agora.minusMonths(MESES_PARA_OBSOLESCENCIA);
        LocalDateTime janelaSemRepetir = agora.minusDays(DIAS_ENTRE_ALERTAS_DE_OBSOLESCENCIA);
        List<Produto> obsoletos = produtoRepository.findProdutosSemSaidaDesde(limite);

        for (Produto produto : obsoletos) {
            boolean alertadoRecentemente = alertaRepository.existsByProdutoIdAndTipoAndDataCriacaoAfter(
                    produto.getId(), TipoAlerta.ESTOQUE_OBSOLETO, janelaSemRepetir);
            if (alertadoRecentemente) {
                continue;
            }
            criarSeNecessario(produto, TipoAlerta.ESTOQUE_OBSOLETO, true,
                    "Produto '" + produto.getNome() + "' (SKU " + produto.getSku()
                            + ") está sem movimentação de saída há mais de " + MESES_PARA_OBSOLESCENCIA + " meses");
        }
        return obsoletos;
    }
}
