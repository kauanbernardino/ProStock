package com.prostock.service;

import com.prostock.dto.MovimentacaoRequest;
import com.prostock.dto.ProdutoRequest;
import com.prostock.exception.RecursoNaoEncontradoException;
import com.prostock.exception.RegraDeNegocioException;
import com.prostock.model.Categoria;
import com.prostock.model.Fornecedor;
import com.prostock.model.MotivoMovimentacao;
import com.prostock.model.Produto;
import com.prostock.model.ProdutoImagem;
import com.prostock.model.Usuario;
import com.prostock.model.enums.TipoMovimentacao;
import com.prostock.repository.CategoriaRepository;
import com.prostock.repository.FornecedorRepository;
import com.prostock.repository.MotivoMovimentacaoRepository;
import com.prostock.repository.ProdutoImagemRepository;
import com.prostock.repository.ProdutoEspecificacoes;
import com.prostock.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProdutoService {

    private static final int MAX_IMAGENS_POR_PRODUTO = 6;
    private static final String MOTIVO_AJUSTE_INVENTARIO = "AJUSTE_INVENTARIO";
    private static final int TAMANHO_MAXIMO_OBSERVACAO = 255;

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final FornecedorRepository fornecedorRepository;
    private final ProdutoImagemRepository produtoImagemRepository;
    private final ImagemStorageService imagemStorageService;
    private final AlertaService alertaService;
    private final MovimentacaoService movimentacaoService;
    private final MotivoMovimentacaoRepository motivoMovimentacaoRepository;

    @Transactional(readOnly = true)
    public List<Produto> listarTodos() {
        return produtoRepository.findByAtivoTrue();
    }

    /**
     * Listagem paginada da tela de Produtos. Com texto de busca, procura no
     * nome, no SKU e na aplicação (ver ProdutoEspecificacoes.ativosComTermos).
     */
    @Transactional(readOnly = true)
    public Page<Produto> listarPaginado(String busca, Pageable pageable) {
        return (busca == null || busca.isBlank())
                ? produtoRepository.findByAtivoTrue(pageable)
                : produtoRepository.findAll(ProdutoEspecificacoes.ativosComTermos(busca), pageable);
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado: " + id));
    }

    /**
     * Busca da tela de Vendas: nome, SKU ou aplicação do veículo, em ordem
     * alfabética de nome.
     */
    @Transactional(readOnly = true)
    public List<Produto> buscar(String busca) {
        return produtoRepository.findAll(ProdutoEspecificacoes.ativosComTermos(busca), Sort.by("nome"));
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
     * UC07: ajuste de inventário a partir da contagem física. O administrador
     * informa a quantidade real contada e o sistema registra a DIFERENÇA como
     * uma movimentação (ENTRADA ou SAÍDA) com o motivo AJUSTE_INVENTARIO.
     *
     * A baixa/soma em si é delegada ao MovimentacaoService, que é o único
     * ponto do sistema que altera a quantidade em estoque: assim o ajuste
     * fica no histórico (quem, quando, de quanto para quanto) e passa pela
     * mesma reavaliação de alertas de qualquer outra movimentação.
     */
    public Produto ajustarInventario(Long id, int quantidadeContada, String observacao, Usuario usuario) {
        if (quantidadeContada < 0) {
            throw new RegraDeNegocioException("A quantidade em estoque não pode ser negativa");
        }
        Produto produto = buscarPorId(id);

        int quantidadeAnterior = produto.getQuantidadeAtual();
        int diferenca = quantidadeContada - quantidadeAnterior;
        if (diferenca == 0) {
            // Contagem confere com o sistema: nada a ajustar, nenhuma movimentação vazia.
            return produto;
        }

        MotivoMovimentacao motivo = motivoMovimentacaoRepository.findByDescricaoIgnoreCase(MOTIVO_AJUSTE_INVENTARIO)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Motivo '" + MOTIVO_AJUSTE_INVENTARIO + "' não cadastrado"));

        TipoMovimentacao tipo = diferenca > 0 ? TipoMovimentacao.ENTRADA : TipoMovimentacao.SAIDA;
        String registro = montarObservacaoDoAjuste(quantidadeAnterior, quantidadeContada, observacao);

        movimentacaoService.registrar(
                new MovimentacaoRequest(produto.getId(), motivo.getId(), tipo, Math.abs(diferenca), registro),
                usuario);

        return buscarPorId(id);
    }

    private String montarObservacaoDoAjuste(int anterior, int contada, String observacao) {
        String texto = "Ajuste de inventário: " + anterior + " → " + contada;
        if (observacao != null && !observacao.isBlank()) {
            texto += " — " + observacao.trim();
        }
        return texto.length() > TAMANHO_MAXIMO_OBSERVACAO ? texto.substring(0, TAMANHO_MAXIMO_OBSERVACAO) : texto;
    }

    public void inativar(Long id) {
        Produto produto = buscarPorId(id);
        produto.setAtivo(false);
        produtoRepository.save(produto);
        alertaService.encerrarAlertasAbertos(produto);
    }

    @Transactional(readOnly = true)
    public List<Produto> listarInativos() {
        return produtoRepository.findByAtivoFalse();
    }

    /**
     * Reverte a inativação. Necessário porque o SKU é único no banco mesmo
     * para produtos inativos — sem isso, um produto "removido" trava o SKU
     * pra sempre e não há como recadastrar a mesma peça.
     */
    public Produto reativar(Long id) {
        Produto produto = buscarPorId(id);
        produto.setAtivo(true);
        Produto salvo = produtoRepository.save(produto);
        // Volta a ser acompanhado: se reativou zerado ou abaixo do mínimo, o alerta reaparece.
        alertaService.avaliarNivelDeEstoque(salvo);
        return salvo;
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
