package com.prostock.repository;

import com.prostock.model.Produto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Consultas dinâmicas de produto (Specification). */
public final class ProdutoEspecificacoes {

    private static final char ESCAPE = '\\';

    private ProdutoEspecificacoes() {
    }

    /** Produtos ativos em que cada palavra buscada aparece no nome, no SKU ou na descrição (aplicação). % e _ são tratados como texto. */
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
