package com.prostock.service;

import com.prostock.dto.MovimentacaoRequest;
import com.prostock.dto.ProdutoRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.MotivoMovimentacao;
import com.prostock.model.Produto;
import com.prostock.model.Usuario;
import com.prostock.model.enums.Cargo;
import com.prostock.model.enums.TipoMovimentacao;
import com.prostock.repository.CategoriaRepository;
import com.prostock.repository.FornecedorRepository;
import com.prostock.repository.MotivoMovimentacaoRepository;
import com.prostock.repository.ProdutoImagemRepository;
import com.prostock.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private MovimentacaoService movimentacaoService;
    @Mock
    private MotivoMovimentacaoRepository motivoMovimentacaoRepository;

    private ProdutoService produtoService;

    private Categoria categoria;
    private Fornecedor fornecedor;

    @BeforeEach
    void setUp() {
        produtoService = new ProdutoService(
                produtoRepository, categoriaRepository, fornecedorRepository,
                produtoImagemRepository, imagemStorageService, alertaService,
                movimentacaoService, motivoMovimentacaoRepository);

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

        // A quantidade do request é ignorada: o estoque só muda por movimentação.
        ProdutoRequest request = new ProdutoRequest(
                "Pastilha Atualizada", "PF-001", null, BigDecimal.TEN, 999, 5, 50, null, 1L, 1L);

        Produto atualizado = produtoService.atualizar(1L, request);

        assertThat(atualizado.getQuantidadeAtual()).isEqualTo(42);
        assertThat(atualizado.getNome()).isEqualTo("Pastilha Atualizada");
    }

    // UC07: ajuste de inventário

    private Produto produtoComEstoque(int quantidade) {
        return Produto.builder()
                .id(1L).nome("Pastilha").sku("PF-001")
                .preco(BigDecimal.TEN).quantidadeAtual(quantidade)
                .estoqueMinimo(5).estoqueMaximo(50)
                .categoria(categoria).fornecedor(fornecedor)
                .build();
    }

    private Usuario administrador() {
        return Usuario.builder().id(1L).nome("Admin").email("admin@prostock.com")
                .senhaHash("hash").cargo(Cargo.ADMINISTRADOR).ativo(true).build();
    }

    private MotivoMovimentacao motivoAjuste() {
        return MotivoMovimentacao.builder().id(5L).descricao("AJUSTE_INVENTARIO").build();
    }

    @Test
    void ajustarInventario_contagemMenorQueOSistema_deveRegistrarSaidaDaDiferencaComMotivoDeAjuste() {
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoComEstoque(42)));
        when(motivoMovimentacaoRepository.findByDescricaoIgnoreCase("AJUSTE_INVENTARIO"))
                .thenReturn(Optional.of(motivoAjuste()));
        Usuario admin = administrador();

        produtoService.ajustarInventario(1L, 40, "contagem de fim de mês", admin);

        ArgumentCaptor<MovimentacaoRequest> captor = ArgumentCaptor.forClass(MovimentacaoRequest.class);
        verify(movimentacaoService).registrar(captor.capture(), eq(admin));
        MovimentacaoRequest registrada = captor.getValue();
        assertThat(registrada.tipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(registrada.quantidade()).isEqualTo(2);
        assertThat(registrada.motivoId()).isEqualTo(5L);
        assertThat(registrada.observacao()).contains("42 → 40").contains("contagem de fim de mês");
    }

    @Test
    void ajustarInventario_contagemMaiorQueOSistema_deveRegistrarEntradaDaDiferenca() {
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoComEstoque(10)));
        when(motivoMovimentacaoRepository.findByDescricaoIgnoreCase("AJUSTE_INVENTARIO"))
                .thenReturn(Optional.of(motivoAjuste()));

        produtoService.ajustarInventario(1L, 13, null, administrador());

        ArgumentCaptor<MovimentacaoRequest> captor = ArgumentCaptor.forClass(MovimentacaoRequest.class);
        verify(movimentacaoService).registrar(captor.capture(), any(Usuario.class));
        assertThat(captor.getValue().tipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(captor.getValue().quantidade()).isEqualTo(3);
    }

    @Test
    void ajustarInventario_contagemIgualAoSistema_naoDeveGerarMovimentacao() {
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produtoComEstoque(42)));

        Produto resultado = produtoService.ajustarInventario(1L, 42, null, administrador());

        assertThat(resultado.getQuantidadeAtual()).isEqualTo(42);
        verifyNoInteractions(movimentacaoService);
    }

    @Test
    void ajustarInventario_comQuantidadeNegativa_deveLancarRegraDeNegocio() {
        assertThatThrownBy(() -> produtoService.ajustarInventario(1L, -1, null, administrador()))
                .isInstanceOf(RegraDeNegocioException.class);

        verifyNoInteractions(movimentacaoService);
    }

    // Busca por nome, SKU ou aplicação

    @Test
    @SuppressWarnings("unchecked")
    void listarPaginado_comTexto_deveUsarABuscaPorNomeSkuOuAplicacao() {
        PageRequest pagina = PageRequest.of(0, 10);
        when(produtoRepository.findAll(any(Specification.class), eq(pagina))).thenReturn(Page.empty());

        produtoService.listarPaginado("gol 1997", pagina);

        verify(produtoRepository).findAll(any(Specification.class), eq(pagina));
        verify(produtoRepository, never()).findByAtivoTrue(any(PageRequest.class));
    }

    @Test
    void listarPaginado_semTexto_deveListarTodosOsAtivos() {
        PageRequest pagina = PageRequest.of(0, 10);
        when(produtoRepository.findByAtivoTrue(pagina)).thenReturn(new PageImpl<>(List.of()));

        produtoService.listarPaginado("  ", pagina);

        verify(produtoRepository).findByAtivoTrue(pagina);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscar_deveOrdenarPorNome() {
        when(produtoRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        produtoService.buscar("cabo");

        verify(produtoRepository).findAll(any(Specification.class), eq(Sort.by("nome")));
    }

    @Test
    void inativar_deveEncerrarOsAlertasAbertosDoProduto() {
        Produto produto = Produto.builder().id(1L).nome("Pastilha").sku("PF-001").ativo(true).build();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

        produtoService.inativar(1L);

        assertThat(produto.isAtivo()).isFalse();
        verify(alertaService).encerrarAlertasAbertos(produto);
    }

    @Test
    void reativar_deveReavaliarOsAlertasDeEstoque() {
        Produto produto = Produto.builder().id(1L).nome("Pastilha").sku("PF-001").ativo(false).build();
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
        when(produtoRepository.save(produto)).thenReturn(produto);

        produtoService.reativar(1L);

        assertThat(produto.isAtivo()).isTrue();
        verify(alertaService).avaliarNivelDeEstoque(produto);
    }
}
