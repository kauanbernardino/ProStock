package com.prostock.service;

import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.MotivoMovimentacao;
import com.prostock.repository.MotivoMovimentacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MotivoMovimentacaoService {

    private final MotivoMovimentacaoRepository motivoMovimentacaoRepository;

    @Transactional(readOnly = true)
    public List<MotivoMovimentacao> listarTodos() {
        return motivoMovimentacaoRepository.findAll();
    }

    public MotivoMovimentacao criar(String descricao) {
        motivoMovimentacaoRepository.findByDescricaoIgnoreCase(descricao)
                .ifPresent(m -> {
                    throw new RegraDeNegocioException("Já existe um motivo com a descrição '" + descricao + "'");
                });
        return motivoMovimentacaoRepository.save(MotivoMovimentacao.builder().descricao(descricao).build());
    }

    public void remover(Long id) {
        MotivoMovimentacao motivo = motivoMovimentacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo não encontrado: " + id));
        motivoMovimentacaoRepository.delete(motivo);
    }
}
