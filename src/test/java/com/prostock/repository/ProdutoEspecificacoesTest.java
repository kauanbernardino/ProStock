package com.prostock.repository;

import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.Produto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a consulta de verdade (JPA + banco em memória H2), não um mock: é aqui
 * que se garante que "gol 1997" acha a peça pela aplicação escrita na descrição.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class ProdutoEspecificacoesTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private ProdutoRepository produtoRepository;

    private Categoria ignicao;
    private Fornecedor fornecedor;

    @BeforeEach
    void setUp() {
        ignicao = em.persist(Categoria.builder().nome("Ignição").build());
        fornecedor = em.persist(Fornecedor.builder().razaoSocial("Central Elétrica Ltda")
                .cnpj("11222333000181").ativo(true).build());

        salvar("Cabo de vela", "IG-CAB-7102", "Jogo de cabos. Aplicação: VW Gol bola 1.0 16V (1997-1999).", true);
        salvar("Cabo de vela", "IG-CAB-7103", "Jogo de cabos. Aplicação: VW Gol bola 1.6 e 1.8 AP (1995-1996).", true);
        salvar("Vela de ignição (jogo com 4)", "IG-VEL-7015", "Aplicação: Fiat Uno/Palio Fire (2004-2016).", true);
        salvar("Bobina de ignição", "IG-BOB-7201", null, true);
        salvar("Cabo de vela antigo", "IG-CAB-0001", "Aplicação: VW Gol (1997).", false);
        em.flush();
    }

    private void salvar(String nome, String sku, String descricao, boolean ativo) {
        em.persist(Produto.builder()
                .nome(nome).sku(sku).descricao(descricao)
                .preco(BigDecimal.TEN).quantidadeAtual(5).estoqueMinimo(1).estoqueMaximo(10)
                .categoria(ignicao).fornecedor(fornecedor).ativo(ativo)
                .build());
    }

    private List<String> skus(String busca) {
        return produtoRepository.findAll(ProdutoEspecificacoes.ativosComTermos(busca), Sort.by("sku"))
                .stream().map(Produto::getSku).toList();
    }

    @Test
    void buscaPeloVeiculoEAno_deveAcharSoAPecaDaquelaAplicacao() {
        assertThat(skus("gol 1997")).containsExactly("IG-CAB-7102");
        assertThat(skus("GOL 1995")).containsExactly("IG-CAB-7103");
    }

    @Test
    void buscaPeloSku_deveAcharOProduto() {
        assertThat(skus("ig-vel-7015")).containsExactly("IG-VEL-7015");
    }

    @Test
    void buscaPeloNome_continuaFuncionando() {
        assertThat(skus("cabo de vela")).containsExactly("IG-CAB-7102", "IG-CAB-7103");
    }

    @Test
    void produtoSemDescricao_eAchadoPeloNome() {
        assertThat(skus("bobina")).containsExactly("IG-BOB-7201");
    }

    @Test
    void produtoInativo_naoApareceNaBusca() {
        assertThat(skus("antigo")).isEmpty();
    }

    @Test
    void curingasDigitados_saoTratadosComoTexto() {
        assertThat(skus("%")).isEmpty();
        assertThat(skus("_")).isEmpty();
    }
}
