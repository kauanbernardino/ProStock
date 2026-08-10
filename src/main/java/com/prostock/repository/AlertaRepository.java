package com.prostock.repository;

import com.prostock.model.Alerta;
import com.prostock.model.enums.TipoAlerta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    List<Alerta> findByLidoFalseOrderByDataCriacaoDesc();

    List<Alerta> findByProdutoIdOrderByDataCriacaoDesc(Long produtoId);

    Optional<Alerta> findFirstByProdutoIdAndTipoAndLidoFalse(Long produtoId, TipoAlerta tipo);

    boolean existsByProdutoIdAndTipoAndLidoFalse(Long produtoId, TipoAlerta tipo);
}
