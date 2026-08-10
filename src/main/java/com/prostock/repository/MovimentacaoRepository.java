package com.prostock.repository;

import com.prostock.model.Movimentacao;
import com.prostock.model.enums.TipoMovimentacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    List<Movimentacao> findByProdutoIdOrderByDataHoraDesc(Long produtoId);

    List<Movimentacao> findByDataHoraBetweenOrderByDataHoraDesc(LocalDateTime inicio, LocalDateTime fim);

    List<Movimentacao> findByUsuarioIdOrderByDataHoraDesc(Long usuarioId);

    @Query("""
        SELECT COALESCE(SUM(m.quantidade), 0) FROM Movimentacao m
        WHERE m.produto.id = :produtoId
        AND m.tipo = com.prostock.model.enums.TipoMovimentacao.SAIDA
        AND m.dataHora >= :desde
        """)
    Long somarSaidasDoProdutoDesde(@Param("produtoId") Long produtoId, @Param("desde") LocalDateTime desde);

    @Query("""
        SELECT m FROM Movimentacao m
        WHERE m.tipo = :tipo
        AND m.dataHora >= :desde
        ORDER BY m.dataHora DESC
        """)
    List<Movimentacao> findByTipoDesde(@Param("tipo") TipoMovimentacao tipo, @Param("desde") LocalDateTime desde);
}
