const TAMANHO_PAGINA = 10;

let categorias = [];
let fornecedores = [];
let usuarioAtual = null;
let produtoEmEdicao = null;
let paginaAtual = 0;
let totalPaginas = 1;
let mostrandoInativos = false;

(async function () {
    usuarioAtual = await protegerPagina();
    if (!usuarioAtual) return;

    await Promise.all([carregarCategorias(), carregarFornecedores()]);
    await carregarProdutos();

    document.getElementById("campo-busca").addEventListener("input", debounce(() => {
        paginaAtual = 0;
        carregarProdutos();
    }, 300));
    document.getElementById("btn-novo-produto").addEventListener("click", () => abrirModal());
    document.getElementById("btn-cancelar-produto").addEventListener("click", fecharModal);
    document.getElementById("form-produto").addEventListener("submit", salvarProduto);
    document.getElementById("input-nova-imagem").addEventListener("change", enviarNovaImagem);
    document.getElementById("btn-pagina-anterior").addEventListener("click", () => mudarPagina(-1));
    document.getElementById("btn-pagina-proxima").addEventListener("click", () => mudarPagina(1));
    configurarJanela("modal-detalhes");
    configurarJanela("modal-foto");

    const chkInativos = document.getElementById("chk-mostrar-inativos");
    if (chkInativos) {
        chkInativos.addEventListener("change", () => {
            mostrandoInativos = chkInativos.checked;
            paginaAtual = 0;
            carregarProdutos();
        });
    }
})();

async function carregarCategorias() {
    categorias = await api.get("/api/categorias");
    const select = document.getElementById("produto-categoria");
    select.innerHTML = categorias.map((c) => `<option value="${c.id}">${escaparHtml(c.nome)}</option>`).join("");
}

async function carregarFornecedores() {
    fornecedores = await api.get("/api/fornecedores");
    const select = document.getElementById("produto-fornecedor");
    select.innerHTML = fornecedores.map((f) => `<option value="${f.id}">${escaparHtml(f.razaoSocial)}</option>`).join("");
}

async function carregarProdutos() {
    try {
        if (mostrandoInativos) {
            const produtos = await api.get("/api/produtos/inativos");
            renderizarProdutos(produtos);
            document.getElementById("paginacao").style.display = "none";
            return;
        }

        document.getElementById("paginacao").style.display = "flex";
        const nome = document.getElementById("campo-busca").value.trim();
        const parametros = new URLSearchParams({ pagina: paginaAtual, tamanho: TAMANHO_PAGINA });
        if (nome) parametros.set("nome", nome);

        const resultado = await api.get(`/api/produtos/pagina?${parametros.toString()}`);
        totalPaginas = Math.max(resultado.totalPaginas, 1);
        renderizarProdutos(resultado.conteudo);
        renderizarPaginacao(resultado);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function renderizarPaginacao(resultado) {
    const info = document.getElementById("paginacao-info");
    if (resultado.totalElementos === 0) {
        info.textContent = "";
    } else {
        const inicio = resultado.paginaAtual * resultado.tamanhoPagina + 1;
        const fim = Math.min(inicio + resultado.conteudo.length - 1, resultado.totalElementos);
        info.textContent = `${inicio}–${fim} de ${resultado.totalElementos} produtos`;
    }

    document.getElementById("btn-pagina-anterior").disabled = paginaAtual <= 0;
    document.getElementById("btn-pagina-proxima").disabled = paginaAtual >= totalPaginas - 1;
}

function mudarPagina(delta) {
    const proxima = paginaAtual + delta;
    if (proxima < 0 || proxima >= totalPaginas) return;
    paginaAtual = proxima;
    carregarProdutos();
}

function renderizarProdutos(produtos) {
    const corpo = document.getElementById("tabela-produtos");
    const vazio = document.getElementById("vazio-produtos");
    corpo.innerHTML = "";

    if (!produtos.length) {
        vazio.style.display = "block";
        return;
    }
    vazio.style.display = "none";

    const ehAdmin = usuarioAtual.cargo === "ADMINISTRADOR";

    for (const p of produtos) {
        const foto = p.imagens && p.imagens.length
            ? `<img src="${escaparHtml(p.imagens[0].url)}" alt="${escaparHtml(p.nome)}" class="foto-tabela">`
            : `<div class="foto-tabela-vazia"></div>`;
        const linha = document.createElement("tr");
        linha.title = "Clique para ver detalhes e fotos";
        tornarLinhaClicavel(linha, () => abrirDetalhes(p));
        linha.innerHTML = `
            <td>${foto}</td>
            <td>${escaparHtml(p.sku)}</td>
            <td>${escaparHtml(p.nome)}</td>
            <td>${escaparHtml(p.categoriaNome)}</td>
            <td>${escaparHtml(p.fornecedorNome)}</td>
            <td>${formatarMoeda(p.preco)}</td>
            <td>${p.quantidadeAtual}</td>
            <td>${mostrandoInativos ? '<span class="badge badge-ruptura">Inativo</span>' : badgeStatusEstoque(p.statusEstoque)}</td>
            <td style="${ehAdmin ? "" : "display:none;"}">
                ${mostrandoInativos
                    ? `<button class="btn btn-secundario btn-pequeno" data-reativar="${p.id}">Reativar</button>`
                    : `<button class="btn btn-secundario btn-pequeno" data-editar="${p.id}">Editar</button>
                       <button class="btn btn-perigo btn-pequeno" data-remover="${p.id}">Remover</button>`}
            </td>
        `;
        corpo.appendChild(linha);
    }

    corpo.querySelectorAll("[data-editar]").forEach((btn) =>
        btn.addEventListener("click", () => abrirModal(Number(btn.dataset.editar), produtos))
    );
    corpo.querySelectorAll("[data-remover]").forEach((btn) =>
        btn.addEventListener("click", () => removerProduto(Number(btn.dataset.remover)))
    );
    corpo.querySelectorAll("[data-reativar]").forEach((btn) =>
        btn.addEventListener("click", () => reativarProduto(Number(btn.dataset.reativar)))
    );
}

function abrirModal(id, produtosCarregados) {
    const form = document.getElementById("form-produto");
    form.reset();
    exibirMensagem("mensagem-modal", "");
    document.getElementById("produto-id").value = "";
    document.getElementById("modal-titulo").textContent = "Novo produto";
    produtoEmEdicao = null;

    if (id) {
        const produto = produtosCarregados.find((p) => p.id === id);
        produtoEmEdicao = produto;
        document.getElementById("modal-titulo").textContent = "Editar produto";
        document.getElementById("produto-id").value = produto.id;
        document.getElementById("produto-nome").value = produto.nome;
        document.getElementById("produto-sku").value = produto.sku;
        document.getElementById("produto-descricao").value = produto.descricao || "";
        document.getElementById("produto-preco").value = produto.preco;
        // UC07: a quantidade editada vira ajuste de inventário no backend.
        document.getElementById("produto-quantidade").value = produto.quantidadeAtual;
        document.getElementById("produto-quantidade").disabled = false;
        document.getElementById("dica-ajuste-inventario").style.display = "block";
        document.getElementById("produto-estoque-minimo").value = produto.estoqueMinimo;
        document.getElementById("produto-estoque-maximo").value = produto.estoqueMaximo;
        document.getElementById("produto-categoria").value = produto.categoriaId;
        document.getElementById("produto-fornecedor").value = produto.fornecedorId;
        document.getElementById("produto-localizacao").value = produto.localizacaoFisica || "";

        document.getElementById("secao-extras").style.display = "block";
        renderizarGaleria(produto.imagens || []);
    } else {
        document.getElementById("produto-quantidade").disabled = false;
        document.getElementById("dica-ajuste-inventario").style.display = "none";
        document.getElementById("secao-extras").style.display = "none";
    }

    document.getElementById("modal-produto").classList.add("aberto");
}

function fecharModal() {
    document.getElementById("modal-produto").classList.remove("aberto");
}

async function salvarProduto(evento) {
    evento.preventDefault();
    exibirMensagem("mensagem-modal", "");

    const id = document.getElementById("produto-id").value;
    const corpo = {
        nome: document.getElementById("produto-nome").value,
        sku: document.getElementById("produto-sku").value,
        descricao: document.getElementById("produto-descricao").value,
        preco: Number(document.getElementById("produto-preco").value),
        quantidadeAtual: Number(document.getElementById("produto-quantidade").value),
        estoqueMinimo: Number(document.getElementById("produto-estoque-minimo").value),
        estoqueMaximo: Number(document.getElementById("produto-estoque-maximo").value),
        localizacaoFisica: document.getElementById("produto-localizacao").value,
        categoriaId: Number(document.getElementById("produto-categoria").value),
        fornecedorId: Number(document.getElementById("produto-fornecedor").value),
    };

    try {
        if (id) {
            await api.put(`/api/produtos/${id}`, corpo);

            // UC07: quantidade diferente do saldo registra ajuste de inventário.
            const quantidadeContada = corpo.quantidadeAtual;
            if (produtoEmEdicao && quantidadeContada !== produtoEmEdicao.quantidadeAtual) {
                try {
                    await api.patch(`/api/produtos/${id}/ajuste-inventario`, {
                        quantidade: quantidadeContada,
                        observacao: "Contagem física pela tela de Produtos",
                    });
                } catch (erroAjuste) {
                    await carregarProdutos();
                    exibirMensagem("mensagem-modal",
                        `Os dados do produto foram salvos, mas o ajuste de quantidade falhou: ${erroAjuste.message}`);
                    return;
                }
            }
            fecharModal();
        } else {
            const criado = await api.post("/api/produtos", corpo);
            await carregarProdutos();
            // Reabre em modo edição para adicionar fotos.
            abrirModal(criado.id, [criado]);
            return;
        }
        await carregarProdutos();
    } catch (erro) {
        exibirMensagem("mensagem-modal", erro.message);
    }
}

async function removerProduto(id) {
    if (!confirm("Remover este produto do estoque ativo?")) return;
    try {
        await api.delete(`/api/produtos/${id}`);
        await carregarProdutos();
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

async function reativarProduto(id) {
    try {
        await api.patch(`/api/produtos/${id}/reativar`);
        await carregarProdutos();
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

// ---------- Detalhes do produto ----------

function abrirDetalhes(produto) {
    document.getElementById("detalhes-titulo").textContent = produto.nome;
    document.getElementById("detalhes-subtitulo").textContent = `SKU ${produto.sku}`;

    renderizarFotosDetalhes(produto.imagens || [], produto.nome);

    const status = mostrandoInativos
        ? '<span class="badge badge-ruptura">Inativo</span>'
        : badgeStatusEstoque(produto.statusEstoque);
    const campos = [
        ["Status", status],
        ["Em estoque", `${produto.quantidadeAtual} un.`],
        ["Mínimo / máximo", `${produto.estoqueMinimo} / ${produto.estoqueMaximo}`],
        ["Preço", formatarMoeda(produto.preco)],
        ["Categoria", escaparHtml(produto.categoriaNome)],
        ["Fornecedor", escaparHtml(produto.fornecedorNome)],
        ["Localização", escaparHtml(produto.localizacaoFisica || "-")],
        ["Descrição / aplicação", escaparHtml(produto.descricao || "-")],
    ];
    document.getElementById("detalhes-info").innerHTML = campos
        .map(([rotulo, valor]) => `<dt>${rotulo}</dt><dd>${valor}</dd>`)
        .join("");

    const btnEditar = document.getElementById("btn-detalhes-editar");
    const podeEditar = usuarioAtual.cargo === "ADMINISTRADOR" && !mostrandoInativos;
    btnEditar.style.display = podeEditar ? "" : "none";
    btnEditar.onclick = () => {
        fecharJanela("modal-detalhes");
        abrirModal(produto.id, [produto]);
    };

    abrirJanela("modal-detalhes");
}

function renderizarFotosDetalhes(imagens, nome) {
    const area = document.getElementById("detalhes-fotos");
    if (!imagens.length) {
        area.innerHTML = '<div class="foto-vazia">Sem fotos cadastradas</div>';
        return;
    }

    area.innerHTML = `
        <img class="foto-principal" id="detalhes-foto-principal" src="${escaparHtml(imagens[0].url)}"
             alt="${escaparHtml(nome)}" title="Clique para ampliar">
        ${imagens.length > 1 ? `<div class="miniaturas">${imagens
            .map((img, i) => `
                <button type="button" data-indice="${i}" class="${i === 0 ? "ativa" : ""}" aria-label="Foto ${i + 1}">
                    <img src="${escaparHtml(img.url)}" alt="">
                </button>`)
            .join("")}</div>` : ""}
    `;

    const principal = document.getElementById("detalhes-foto-principal");
    principal.addEventListener("click", () => ampliarFoto(principal.src, nome));
    area.querySelectorAll("[data-indice]").forEach((btn) =>
        btn.addEventListener("click", () => {
            principal.src = imagens[Number(btn.dataset.indice)].url;
            area.querySelectorAll("[data-indice]").forEach((b) => b.classList.toggle("ativa", b === btn));
        })
    );
}

function ampliarFoto(url, nome) {
    const foto = document.getElementById("foto-ampliada");
    foto.src = url;
    foto.alt = nome;
    abrirJanela("modal-foto");
}

// ---------- Fotos do produto ----------

function renderizarGaleria(imagens) {
    const galeria = document.getElementById("galeria-imagens");
    if (!imagens.length) {
        galeria.innerHTML = '<span style="color:var(--cor-texto-suave); font-size:13px;">Nenhuma foto ainda.</span>';
        return;
    }
    galeria.innerHTML = imagens
        .map(
            (img) => `
        <div style="position:relative;">
            <img src="${escaparHtml(img.url)}" data-ampliar title="Clique para ampliar" style="width:80px;height:80px;object-fit:cover;border-radius:8px;border:1px solid var(--cor-borda);cursor:zoom-in;">
            <button type="button" data-remover-imagem="${img.id}"
                style="position:absolute; top:-6px; right:-6px; width:20px; height:20px; border-radius:50%; border:none; background:var(--cor-perigo); color:#fff; font-size:12px; cursor:pointer; line-height:1;">
                &times;
            </button>
        </div>
    `
        )
        .join("");

    galeria.querySelectorAll("[data-ampliar]").forEach((img) =>
        img.addEventListener("click", () => ampliarFoto(img.src, produtoEmEdicao ? produtoEmEdicao.nome : ""))
    );
    galeria.querySelectorAll("[data-remover-imagem]").forEach((btn) =>
        btn.addEventListener("click", () => removerImagemProduto(Number(btn.dataset.removerImagem)))
    );
}

async function enviarNovaImagem(evento) {
    const arquivo = evento.target.files[0];
    if (!arquivo || !produtoEmEdicao) return;

    exibirMensagem("mensagem-imagem", "");
    const formData = new FormData();
    formData.append("arquivo", arquivo);

    try {
        const resposta = await fetch(`/api/produtos/${produtoEmEdicao.id}/imagens`, {
            method: "POST",
            credentials: "include",
            body: formData,
        });
        const dados = await resposta.json();
        if (!resposta.ok) {
            throw new Error(dados.mensagem || "Falha ao enviar a imagem");
        }
        produtoEmEdicao = dados;
        renderizarGaleria(dados.imagens);
        evento.target.value = "";
        await carregarProdutos();
    } catch (erro) {
        exibirMensagem("mensagem-imagem", erro.message);
    }
}

async function removerImagemProduto(imagemId) {
    try {
        const dados = await api.delete(`/api/produtos/${produtoEmEdicao.id}/imagens/${imagemId}`);
        produtoEmEdicao = dados;
        renderizarGaleria(dados.imagens);
        await carregarProdutos();
    } catch (erro) {
        exibirMensagem("mensagem-imagem", erro.message);
    }
}
