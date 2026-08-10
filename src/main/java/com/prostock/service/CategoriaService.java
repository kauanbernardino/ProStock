package com.prostock.service;

import com.prostock.dto.CategoriaRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Categoria;
import com.prostock.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<Categoria> listarTodas() {
        return categoriaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Categoria buscarPorId(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada: " + id));
    }

    public Categoria criar(CategoriaRequest request) {
        if (categoriaRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new RegraDeNegocioException("Já existe uma categoria com o nome '" + request.nome() + "'");
        }
        Categoria categoria = Categoria.builder()
                .nome(request.nome())
                .descricao(request.descricao())
                .build();
        return categoriaRepository.save(categoria);
    }

    public Categoria atualizar(Long id, CategoriaRequest request) {
        Categoria categoria = buscarPorId(id);
        categoriaRepository.findByNomeIgnoreCase(request.nome())
                .filter(outra -> !outra.getId().equals(id))
                .ifPresent(outra -> {
                    throw new RegraDeNegocioException("Já existe uma categoria com o nome '" + request.nome() + "'");
                });
        categoria.setNome(request.nome());
        categoria.setDescricao(request.descricao());
        return categoriaRepository.save(categoria);
    }

    public void remover(Long id) {
        Categoria categoria = buscarPorId(id);
        if (!categoria.getProdutos().isEmpty()) {
            throw new RegraDeNegocioException("Não é possível remover uma categoria vinculada a produtos");
        }
        categoriaRepository.delete(categoria);
    }
}
