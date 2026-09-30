package com.prostock.repository;

import com.prostock.model.Produto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Consultas dinâmicas de produto (Spring Data JPA Specification).
 */
public final class ProdutoEspecificacoes {

    private static final char ESCAPE = '\\';

    private ProdutoEspecificacoes() {
    }

    /**
     * Busca de balcão: produtos ativos em que CADA palavra digitada aparece no
     * nome, no SKU ou na descrição (onde fica a aplicação: veículo, motor, ano).
     *
     * Ex.: "gol 1997" encontra o cabo de vela cuja descrição diz
     * "VW Gol bola 1.0 16V (1997-1999)", mesmo sem "gol" nem "1997" no nome.
     * Não diferencia maiúsculas de minúsculas, e %/_ digitados são tratados
     * como texto comum (não como curinga do LIKE).
     */
    public static Specification<Produto> ativosComTermos(String busca) {
        return (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            condicoes.add(cb.isTrue(root.<Boolean>get("ativo")));

            for (String termo : busca.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                if (termo.isBlank()) {
                    continue;
                }
                String padrao = "%" + escaparCuringas(termo) + "%";
                condicoes.add(cb.or(
                        cb.like(cb.lower(root.<String>get("nome")), padrao, ESCAPE),
                        cb.like(cb.lower(root.<String>get("sku")), padrao, ESCAPE),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("descricao"), "")), padrao, ESCAPE)
                ));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }

    private static String escaparCuringas(String termo) {
        return termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
