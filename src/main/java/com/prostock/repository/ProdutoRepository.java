package com.prostock.repository;

import com.prostock.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    Optional<Produto> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    List<Produto> findByNomeContainingIgnoreCaseAndAtivoTrue(String nome);

    List<Produto> findByCategoriaIdAndAtivoTrue(Long categoriaId);

    List<Produto> findByFornecedorIdAndAtivoTrue(Long fornecedorId);

    List<Produto> findByAtivoTrue();

    Page<Produto> findByAtivoTrue(Pageable pageable);

    Page<Produto> findByNomeContainingIgnoreCaseAndAtivoTrue(String nome, Pageable pageable);

    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND p.quantidadeAtual <= p.estoqueMinimo")
    List<Produto> findProdutosAbaixoDoEstoqueMinimo();

    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND p.quantidadeAtual = 0")
    List<Produto> findProdutosEmRuptura();

    @Query("SELECT p FROM Produto p WHERE p.ativo = true AND p.quantidadeAtual >= p.estoqueMaximo")
    List<Produto> findProdutosAcimaDoEstoqueMaximo();

    @Query("SELECT COALESCE(SUM(p.preco * p.quantidadeAtual), 0) FROM Produto p WHERE p.ativo = true")
    BigDecimal calcularValorTotalEmEstoque();

    @Query("SELECT COALESCE(SUM(p.quantidadeAtual), 0) FROM Produto p WHERE p.ativo = true")
    Long calcularQuantidadeTotalEmEstoque();

    @Query("""
        SELECT DISTINCT p FROM Produto p
        WHERE p.ativo = true
        AND p.id NOT IN (
            SELECT m.produto.id FROM Movimentacao m
            WHERE m.tipo = com.prostock.model.enums.TipoMovimentacao.SAIDA
            AND m.dataHora >= :dataLimite
        )
        """)
    List<Produto> findProdutosSemSaidaDesde(@Param("dataLimite") java.time.LocalDateTime dataLimite);
}
