package com.prostock.repository;

import com.prostock.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    List<Venda> findAllByOrderByDataHoraDesc();
}
