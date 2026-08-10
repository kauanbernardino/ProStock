package com.prostock.service;

import com.prostock.dto.ProdutoRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.Produto;
import com.prostock.repository.CategoriaRepository;
import com.prostock.repository.FornecedorRepository;
import com.prostock.repository.ProdutoImagemRepository;
import com.prostock.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private FornecedorRepository fornecedorRepository;
    @Mock
    private ProdutoImagemRepository produtoImagemRepository;
    @Mock
    private ImagemStorageService imagemStorageService;
    @Mock
    private AlertaService alertaService;

    private ProdutoService produtoService;

    private Categoria categoria;
    private Fornecedor fornecedor;

    @BeforeEach
    void setUp() {
        produtoService = new ProdutoService(
                produtoRepository, categoriaRepository, fornecedorRepository,
                produtoImagemRepository, imagemStorageService, alertaService);

        categoria = Categoria.builder().id(1L).nome("Freios").build();
        fornecedor = Fornecedor.builder().id(1L).razaoSocial("Autopeças Rio Grande").cnpj("12345678000199").build();
    }

    private ProdutoRequest requestValido() {
        return new ProdutoRequest(
                "Pastilha de Freio", "PF-001", "descrição",
                new BigDecimal("89.90"), 20, 5, 50,
                "Corredor 1", 1L, 1L);
    }

    @Test
    void criar_comSkuJaExistente_deveLancarRegraDeNegocio() {
        when(produtoRepository.existsBySkuIgnoreCase("PF-001")).thenReturn(true);

        assertThatThrownBy(() -> produtoService.criar(requestValido()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("SKU");

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void criar_comEstoqueMaximoMenorQueMinimo_deveLancarRegraDeNegocio() {
        ProdutoRequest request = new ProdutoRequest(
                "Pastilha", "PF-002", null, BigDecimal.TEN, 5, 10, 3, null, 1L, 1L);
        when(produtoRepository.existsBySkuIgnoreCase("PF-002")).thenReturn(false);

        assertThatThrownBy(() -> produtoService.criar(request))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("estoque máximo");
    }

    @Test
    void criar_comCategoriaInexistente_deveLancarRecursoNaoEncontrado() {
        when(produtoRepository.existsBySkuIgnoreCase("PF-001")).thenReturn(false);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.criar(requestValido()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void criar_comDadosValidos_deveSalvarEAvaliarNivelDeEstoque() {
        when(produtoRepository.existsBySkuIgnoreCase("PF-001")).thenReturn(false);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(fornecedorRepository.findById(1L)).thenReturn(Optional.of(fornecedor));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(inv -> {
            Produto p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        Produto salvo = produtoService.criar(requestValido());

        assertThat(salvo.getId()).isEqualTo(10L);
        assertThat(salvo.getSku()).isEqualTo("PF-001");
        assertThat(salvo.getQuantidadeAtual()).isEqualTo(20);
        verify(alertaService).avaliarNivelDeEstoque(salvo);
    }

    @Test
    void atualizar_naoDeveAlterarQuantidadeAtualDoEstoque() {
        Produto existente = Produto.builder()
                .id(1L).nome("Pastilha").sku("PF-001")
                .preco(BigDecimal.TEN).quantidadeAtual(42)
                .estoqueMinimo(5).estoqueMaximo(50)
                .categoria(categoria).fornecedor(fornecedor)
                .build();

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(produtoRepository.findBySkuIgnoreCase("PF-001")).thenReturn(Optional.of(existente));
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(fornecedorRepository.findById(1L)).thenReturn(Optional.of(fornecedor));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(inv -> inv.getArgument(0));

        // O request tenta enviar quantidadeAtual=999, mas isso não deve ser aplicado aqui:
        // a quantidade em estoque só muda via movimentações.
        ProdutoRequest request = new ProdutoRequest(
                "Pastilha Atualizada", "PF-001", null, BigDecimal.TEN, 999, 5, 50, null, 1L, 1L);

        Produto atualizado = produtoService.atualizar(1L, request);

        assertThat(atualizado.getQuantidadeAtual()).isEqualTo(42);
        assertThat(atualizado.getNome()).isEqualTo("Pastilha Atualizada");
    }
}
