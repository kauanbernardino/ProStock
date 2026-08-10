package com.prostock.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fornecedores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "razao_social", nullable = false, length = 150)
    private String razaoSocial;

    @NotBlank
    @Pattern(regexp = "\\d{14}", message = "CNPJ deve conter 14 dígitos numéricos")
    @Column(nullable = false, length = 14, unique = true)
    private String cnpj;

    @Column(length = 120)
    private String contato;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Builder.Default
    @OneToMany(mappedBy = "fornecedor")
    private List<Produto> produtos = new ArrayList<>();
}
