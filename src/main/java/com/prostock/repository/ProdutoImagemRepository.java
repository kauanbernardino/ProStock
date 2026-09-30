package com.prostock.repository;

import com.prostock.model.ProdutoImagem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoImagemRepository extends JpaRepository<ProdutoImagem, Long> {

    long countByProdutoId(Long produtoId);
}
