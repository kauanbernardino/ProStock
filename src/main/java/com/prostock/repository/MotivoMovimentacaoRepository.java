package com.prostock.repository;

import com.prostock.model.MotivoMovimentacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MotivoMovimentacaoRepository extends JpaRepository<MotivoMovimentacao, Long> {

    Optional<MotivoMovimentacao> findByDescricaoIgnoreCase(String descricao);
}
