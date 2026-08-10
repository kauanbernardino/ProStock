package com.prostock.service;

import com.prostock.dto.FornecedorRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Fornecedor;
import com.prostock.repository.FornecedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FornecedorService {

    private final FornecedorRepository fornecedorRepository;

    @Transactional(readOnly = true)
    public List<Fornecedor> listarTodos() {
        return fornecedorRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Fornecedor buscarPorId(Long id) {
        return fornecedorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado: " + id));
    }

    public Fornecedor criar(FornecedorRequest request) {
        if (fornecedorRepository.existsByCnpj(request.cnpj())) {
            throw new RegraDeNegocioException("Já existe um fornecedor com o CNPJ '" + request.cnpj() + "'");
        }
        Fornecedor fornecedor = Fornecedor.builder()
                .razaoSocial(request.razaoSocial())
                .cnpj(request.cnpj())
                .contato(request.contato())
                .ativo(true)
                .build();
        return fornecedorRepository.save(fornecedor);
    }

    public Fornecedor atualizar(Long id, FornecedorRequest request) {
        Fornecedor fornecedor = buscarPorId(id);
        fornecedorRepository.findByCnpj(request.cnpj())
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new RegraDeNegocioException("Já existe um fornecedor com o CNPJ '" + request.cnpj() + "'");
                });
        fornecedor.setRazaoSocial(request.razaoSocial());
        fornecedor.setCnpj(request.cnpj());
        fornecedor.setContato(request.contato());
        return fornecedorRepository.save(fornecedor);
    }

    public void inativar(Long id) {
        Fornecedor fornecedor = buscarPorId(id);
        fornecedor.setAtivo(false);
        fornecedorRepository.save(fornecedor);
    }
}
