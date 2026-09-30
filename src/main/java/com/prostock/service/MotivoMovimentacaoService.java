package com.prostock.service;

import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.MotivoMovimentacao;
import com.prostock.repository.MotivoMovimentacaoRepository;
import com.prostock.repository.MovimentacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class MotivoMovimentacaoService {

    /**
     * Motivos que o próprio código usa pelo nome (VendaService e
     * ProdutoService). Se algum deles fosse removido, vendas, cancelamentos e
     * ajustes de inventário passariam a falhar.
     */
    static final Set<String> MOTIVOS_DO_SISTEMA = Set.of("VENDA", "ESTORNO", "AJUSTE_INVENTARIO");

    private final MotivoMovimentacaoRepository motivoMovimentacaoRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    @Transactional(readOnly = true)
    public List<MotivoMovimentacao> listarTodos() {
        return motivoMovimentacaoRepository.findAll();
    }

    public MotivoMovimentacao criar(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new RegraDeNegocioException("A descrição do motivo é obrigatória");
        }
        descricao = descricao.trim();
        String descricaoFinal = descricao;
        motivoMovimentacaoRepository.findByDescricaoIgnoreCase(descricao)
                .ifPresent(m -> {
                    throw new RegraDeNegocioException("Já existe um motivo com a descrição '" + descricaoFinal + "'");
                });
        return motivoMovimentacaoRepository.save(MotivoMovimentacao.builder().descricao(descricaoFinal).build());
    }

    public void remover(Long id) {
        MotivoMovimentacao motivo = motivoMovimentacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo não encontrado: " + id));

        if (MOTIVOS_DO_SISTEMA.contains(motivo.getDescricao().toUpperCase())) {
            throw new RegraDeNegocioException("O motivo '" + motivo.getDescricao()
                    + "' é usado automaticamente pelo sistema e não pode ser removido");
        }
        if (movimentacaoRepository.existsByMotivoId(id)) {
            throw new RegraDeNegocioException("O motivo '" + motivo.getDescricao()
                    + "' já foi usado em movimentações e não pode ser removido (o histórico depende dele)");
        }
        motivoMovimentacaoRepository.delete(motivo);
    }
}
