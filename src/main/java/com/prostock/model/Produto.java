package com.prostock.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "produtos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String nome;

    @NotBlank
    @Column(nullable = false, length = 50, unique = true)
    private String sku;

    @Column(length = 500)
    private String descricao;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @NotNull
    @PositiveOrZero
    @Column(name = "quantidade_atual", nullable = false)
    @Builder.Default
    private Integer quantidadeAtual = 0;

    @NotNull
    @PositiveOrZero
    @Column(name = "estoque_minimo", nullable = false)
    @Builder.Default
    private Integer estoqueMinimo = 0;

    @NotNull
    @Column(name = "estoque_maximo", nullable = false)
    private Integer estoqueMaximo;

    @Column(name = "localizacao_fisica", length = 100)
    private String localizacaoFisica;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    /**
     * Controle de concorrência otimista. O Hibernate grava
     * "UPDATE ... WHERE id = ? AND versao = ?" e incrementa a versão a cada
     * alteração. Se dois vendedores venderem a última unidade ao mesmo tempo,
     * os dois leem a mesma versão, mas só o primeiro UPDATE encontra a linha:
     * o segundo afeta 0 linhas, a transação inteira dele é desfeita e a API
     * responde 409 (ver GlobalExceptionHandler). Sem isso, o segundo
     * sobrescreveria o primeiro e o sistema registraria duas vendas de uma peça.
     */
    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Builder.Default
    @OneToMany(mappedBy = "produto", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC")
    private List<ProdutoImagem> imagens = new ArrayList<>();

    @PrePersist
    protected void aoCriar() {
        LocalDateTime agora = LocalDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    @PreUpdate
    protected void aoAtualizar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
