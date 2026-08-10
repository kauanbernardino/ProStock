package com.prostock.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "motivos_movimentacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MotivoMovimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 100, unique = true)
    private String descricao;
}
