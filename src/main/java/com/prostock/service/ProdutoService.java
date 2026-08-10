package com.prostock.service;

import com.prostock.dto.ProdutoRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.Produto;
import com.prostock.model.ProdutoImagem;
import com.prostock.repository.CategoriaRepository;
import com.prostock.repository.FornecedorRepository;
import com.prostock.repository.ProdutoImagemRepository;
import com.prostock.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProdutoService {

    private static final int MAX_IMAGENS_POR_PRODUTO = 6;

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final FornecedorRepository fornecedorRepository;
    private final ProdutoImagemRepository produtoImagemRepository;
    private final ImagemStorageService imagemStorageService;
    private final AlertaService alertaService;

    @Transactional(readOnly = true)
    public List<Produto> listarTodos() {
        return produtoRepository.findByAtivoTrue();
    }

    @Transactional(readOnly = true)
    public Page<Produto> listarPaginado(String nome, Pageable pageable) {
        return (nome == null || nome.isBlank())
                ? produtoRepository.findByAtivoTrue(pageable)
                : produtoRepository.findByNomeContainingIgnoreCaseAndAtivoTrue(nome, pageable);
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<Produto> buscarPorNome(String nome) {
        return produtoRepository.findByNomeContainingIgnoreCaseAndAtivoTrue(nome);
    }

    @Transactional(readOnly = true)
    public List<Produto> listarAbaixoDoEstoqueMinimo() {
        return produtoRepository.findProdutosAbaixoDoEstoqueMinimo();
    }

    @Transactional(readOnly = true)
    public List<Produto> listarEmRuptura() {
        return produtoRepository.findProdutosEmRuptura();
    }

    public Produto criar(ProdutoRequest request) {
        if (produtoRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new RegraDeNegocioException("Já existe um produto com o SKU '" + request.sku() + "'");
        }
        if (request.estoqueMaximo() < request.estoqueMinimo()) {
            throw new RegraDeNegocioException("O estoque máximo não pode ser menor que o estoque mínimo");
        }

        Categoria categoria = buscarCategoria(request.categoriaId());
        Fornecedor fornecedor = buscarFornecedor(request.fornecedorId());

        Produto produto = Produto.builder()
                .nome(request.nome())
                .sku(request.sku())
                .descricao(request.descricao())
                .preco(request.preco())
                .quantidadeAtual(request.quantidadeAtual())
                .estoqueMinimo(request.estoqueMinimo())
                .estoqueMaximo(request.estoqueMaximo())
                .localizacaoFisica(request.localizacaoFisica())
                .categoria(categoria)
                .fornecedor(fornecedor)
                .ativo(true)
                .build();

        Produto salvo = produtoRepository.save(produto);
        alertaService.avaliarNivelDeEstoque(salvo);
        return salvo;
    }

    public Produto atualizar(Long id, ProdutoRequest request) {
        Produto produto = buscarPorId(id);

        produtoRepository.findBySkuIgnoreCase(request.sku())
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new RegraDeNegocioException("Já existe um produto com o SKU '" + request.sku() + "'");
                });
        if (request.estoqueMaximo() < request.estoqueMinimo()) {
            throw new RegraDeNegocioException("O estoque máximo não pode ser menor que o estoque mínimo");
        }

        Categoria categoria = buscarCategoria(request.categoriaId());
        Fornecedor fornecedor = buscarFornecedor(request.fornecedorId());

        produto.setNome(request.nome());
        produto.setSku(request.sku());
        produto.setDescricao(request.descricao());
        produto.setPreco(request.preco());
        produto.setEstoqueMinimo(request.estoqueMinimo());
        produto.setEstoqueMaximo(request.estoqueMaximo());
        produto.setLocalizacaoFisica(request.localizacaoFisica());
        produto.setCategoria(categoria);
        produto.setFornecedor(fornecedor);
        // A quantidade em estoque só é alterada via movimentações (UC02/UC04/UC05/UC07),
        // nunca diretamente pela edição do cadastro do produto.

        Produto salvo = produtoRepository.save(produto);
        alertaService.avaliarNivelDeEstoque(salvo);
        return salvo;
    }

    /**
     * UC07: ajuste direto de inventário (contagem física), com justificativa
     * já registrada como movimentação pelo chamador (MovimentacaoService).
     */
    public Produto ajustarQuantidade(Long id, int novaQuantidade) {
        if (novaQuantidade < 0) {
            throw new RegraDeNegocioException("A quantidade em estoque não pode ser negativa");
        }
        Produto produto = buscarPorId(id);
        produto.setQuantidadeAtual(novaQuantidade);
        Produto salvo = produtoRepository.save(produto);
        alertaService.avaliarNivelDeEstoque(salvo);
        return salvo;
    }

    public void inativar(Long id) {
        Produto produto = buscarPorId(id);
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    /**
     * Fotos do produto: usadas pelo vendedor para conferir visualmente a peça
     * quando o cliente a traz em mãos sem saber o veículo/ano exato.
     */
    public Produto adicionarImagem(Long produtoId, MultipartFile arquivo) {
        Produto produto = buscarPorId(produtoId);

        if (produtoImagemRepository.countByProdutoId(produtoId) >= MAX_IMAGENS_POR_PRODUTO) {
            throw new RegraDeNegocioException(
                    "Limite de " + MAX_IMAGENS_POR_PRODUTO + " imagens por produto atingido");
        }

        String caminho = imagemStorageService.salvar(arquivo);
        int proximaOrdem = produto.getImagens().size();

        ProdutoImagem imagem = ProdutoImagem.builder()
                .produto(produto)
                .caminho(caminho)
                .ordem(proximaOrdem)
                .build();
        produto.getImagens().add(imagem);

        return produtoRepository.save(produto);
    }

    public Produto removerImagem(Long produtoId, Long imagemId) {
        Produto produto = buscarPorId(produtoId);

        ProdutoImagem imagem = produto.getImagens().stream()
                .filter(img -> img.getId().equals(imagemId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imagem não encontrada: " + imagemId));

        produto.getImagens().remove(imagem);
        Produto salvo = produtoRepository.save(produto);
        imagemStorageService.remover(imagem.getCaminho());

        return salvo;
    }

    private Categoria buscarCategoria(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada: " + id));
    }

    private Fornecedor buscarFornecedor(Long id) {
        return fornecedorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor não encontrado: " + id));
    }
}
