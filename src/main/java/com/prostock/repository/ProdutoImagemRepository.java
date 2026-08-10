package com.prostock.repository;

import com.prostock.model.ProdutoImagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProdutoImagemRepository extends JpaRepository<ProdutoImagem, Long> {

    List<ProdutoImagem> findByProdutoIdOrderByOrdemAsc(Long produtoId);

    long countByProdutoId(Long produtoId);
}
