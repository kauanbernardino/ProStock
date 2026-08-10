let carrinho = [];
let usuarioAtual = null;
let vendaAtual = null;

(async function () {
    usuarioAtual = await protegerPagina();
    if (!usuarioAtual) return;

    const campoBusca = document.getElementById("campo-busca-produto");
    campoBusca.addEventListener("input", debounce(buscarProdutos, 250));
    document.addEventListener("click", (evento) => {
        const resultados = document.getElementById("resultados-busca");
        if (!resultados.contains(evento.target) && evento.target !== campoBusca) {
            resultados.classList.remove("aberto");
        }
    });

    document.querySelectorAll('input[name="forma-pagamento"]').forEach((radio) =>
        radio.addEventListener("change", atualizarSelecaoDeFormaDePagamento)
    );

    document.getElementById("btn-finalizar-venda").addEventListener("click", finalizarVenda);
    document.getElementById("btn-limpar-carrinho").addEventListener("click", limparCarrinho);
    document.getElementById("btn-nova-venda").addEventListener("click", novaVenda);
    document.getElementById("btn-imprimir-recibo").addEventListener("click", () => window.print());
    document.getElementById("btn-copiar-pix").addEventListener("click", copiarPix);
    document.getElementById("btn-cancelar-venda").addEventListener("click", cancelarVendaAtual);

    renderizarCarrinho();
})();

function atualizarSelecaoDeFormaDePagamento() {
    document.querySelectorAll(".opcao-pagamento").forEach((label) => {
        const radio = label.querySelector('input[type="radio"]');
        label.classList.toggle("selecionada", radio.checked);
    });
}

async function buscarProdutos() {
    const termo = document.getElementById("campo-busca-produto").value.trim();
    const resultados = document.getElementById("resultados-busca");

    if (!termo) {
        resultados.classList.remove("aberto");
        return;
    }

    try {
        const produtos = await api.get(`/api/produtos?nome=${encodeURIComponent(termo)}`);
        const disponiveis = produtos.filter((p) => p.quantidadeAtual > 0);

        if (!disponiveis.length) {
            resultados.innerHTML = '<div class="resultado-item">Nenhum produto disponível encontrado.</div>';
        } else {
            resultados.innerHTML = disponiveis
                .map(
                    (p) => `
                <div class="resultado-item" data-id="${p.id}">
                    <div class="info">
                        <span>${escaparHtml(p.nome)}</span>
                        <span class="sku">SKU ${escaparHtml(p.sku)} — ${p.quantidadeAtual} un. disponível</span>
                    </div>
                    <strong>${formatarMoeda(p.preco)}</strong>
                </div>
            `
                )
                .join("");

            resultados.querySelectorAll("[data-id]").forEach((el) =>
                el.addEventListener("click", () => {
                    const produto = disponiveis.find((p) => p.id === Number(el.dataset.id));
                    adicionarAoCarrinho(produto);
                    document.getElementById("campo-busca-produto").value = "";
                    resultados.classList.remove("aberto");
                })
            );
        }

        resultados.classList.add("aberto");
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function adicionarAoCarrinho(produto) {
    const existente = carrinho.find((item) => item.produtoId === produto.id);
    if (existente) {
        if (existente.quantidade < produto.quantidadeAtual) {
            existente.quantidade += 1;
        }
    } else {
        carrinho.push({
            produtoId: produto.id,
            nome: produto.nome,
            sku: produto.sku,
            preco: produto.preco,
            quantidade: 1,
            estoqueDisponivel: produto.quantidadeAtual,
        });
    }
    renderizarCarrinho();
}

function alterarQuantidade(produtoId, delta) {
    const item = carrinho.find((i) => i.produtoId === produtoId);
    if (!item) return;
    const novaQuantidade = item.quantidade + delta;
    if (novaQuantidade < 1 || novaQuantidade > item.estoqueDisponivel) return;
    item.quantidade = novaQuantidade;
    renderizarCarrinho();
}

function removerDoCarrinho(produtoId) {
    carrinho = carrinho.filter((i) => i.produtoId !== produtoId);
    renderizarCarrinho();
}

function limparCarrinho() {
    if (!carrinho.length) return;
    if (!confirm("Limpar todos os itens do carrinho?")) return;
    carrinho = [];
    renderizarCarrinho();
}

function renderizarCarrinho() {
    const corpo = document.getElementById("tabela-carrinho");
    const vazio = document.getElementById("carrinho-vazio");
    const btnFinalizar = document.getElementById("btn-finalizar-venda");
    const btnLimpar = document.getElementById("btn-limpar-carrinho");

    corpo.innerHTML = "";

    if (!carrinho.length) {
        vazio.style.display = "block";
        btnFinalizar.disabled = true;
        btnLimpar.disabled = true;
    } else {
        vazio.style.display = "none";
        btnFinalizar.disabled = false;
        btnLimpar.disabled = false;

        for (const item of carrinho) {
            const subtotal = item.preco * item.quantidade;
            const linha = document.createElement("tr");
            linha.innerHTML = `
                <td>${escaparHtml(item.nome)}<br><span style="color:var(--cor-texto-suave); font-size:12px;">SKU ${escaparHtml(item.sku)}</span></td>
                <td>${formatarMoeda(item.preco)}</td>
                <td>
                    <div class="qtd-controle">
                        <button type="button" data-menos="${item.produtoId}">−</button>
                        <input type="text" value="${item.quantidade}" readonly>
                        <button type="button" data-mais="${item.produtoId}">+</button>
                    </div>
                </td>
                <td>${formatarMoeda(subtotal)}</td>
                <td><button type="button" class="btn btn-perigo btn-pequeno" data-remover="${item.produtoId}">Remover</button></td>
            `;
            corpo.appendChild(linha);
        }

        corpo.querySelectorAll("[data-mais]").forEach((btn) =>
            btn.addEventListener("click", () => alterarQuantidade(Number(btn.dataset.mais), 1))
        );
        corpo.querySelectorAll("[data-menos]").forEach((btn) =>
            btn.addEventListener("click", () => alterarQuantidade(Number(btn.dataset.menos), -1))
        );
        corpo.querySelectorAll("[data-remover]").forEach((btn) =>
            btn.addEventListener("click", () => removerDoCarrinho(Number(btn.dataset.remover)))
        );
    }

    const total = carrinho.reduce((soma, item) => soma + item.preco * item.quantidade, 0);
    document.getElementById("total-venda").textContent = formatarMoeda(total);
}

const ROTULOS_FORMA_PAGAMENTO = {
    DINHEIRO: "Dinheiro",
    PIX: "Pix",
    CARTAO: "Cartão",
};

async function finalizarVenda() {
    exibirMensagem("mensagem", "");
    const formaPagamento = document.querySelector('input[name="forma-pagamento"]:checked').value;
    const corpo = {
        itens: carrinho.map((item) => ({ produtoId: item.produtoId, quantidade: item.quantidade })),
        formaPagamento,
    };

    try {
        const venda = await api.post("/api/vendas", corpo);
        exibirRecibo(venda);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function exibirRecibo(venda) {
    vendaAtual = venda;

    document.getElementById("recibo-id").textContent = venda.id;
    document.getElementById("recibo-data").textContent = formatarData(venda.dataHora);
    document.getElementById("recibo-vendedor").textContent = venda.usuarioNome;
    document.getElementById("recibo-total").textContent = formatarMoeda(venda.valorTotal);
    document.getElementById("recibo-forma-pagamento").textContent = ROTULOS_FORMA_PAGAMENTO[venda.formaPagamento];

    const secaoPix = document.getElementById("secao-pix");
    if (venda.formaPagamento === "PIX") {
        secaoPix.style.display = "block";
        document.getElementById("recibo-pix").textContent = venda.pixCopiaECola;
    } else {
        secaoPix.style.display = "none";
    }

    document.getElementById("recibo-itens").innerHTML = venda.itens
        .map(
            (item) => `
        <tr>
            <td>${escaparHtml(item.produtoNome)} <span style="color:var(--cor-texto-suave); font-size:12px;">(SKU ${escaparHtml(item.produtoSku)})</span></td>
            <td>${item.quantidade}</td>
            <td>${formatarMoeda(item.precoUnitario)}</td>
            <td>${formatarMoeda(item.subtotal)}</td>
        </tr>
    `
        )
        .join("");

    const cancelada = venda.status === "CANCELADA";
    document.getElementById("recibo-cancelada-aviso").style.display = cancelada ? "block" : "none";
    document.getElementById("btn-cancelar-venda").style.display = cancelada ? "none" : "";

    document.getElementById("tela-carrinho").style.display = "none";
    document.getElementById("recibo").style.display = "block";
}

async function cancelarVendaAtual() {
    if (!vendaAtual) return;
    if (!confirm(`Cancelar a venda #${vendaAtual.id}? O estoque dos itens será devolvido automaticamente.`)) return;

    try {
        const venda = await api.patch(`/api/vendas/${vendaAtual.id}/cancelar`);
        exibirRecibo(venda);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function novaVenda() {
    carrinho = [];
    vendaAtual = null;
    renderizarCarrinho();
    document.querySelector('input[name="forma-pagamento"][value="DINHEIRO"]').checked = true;
    atualizarSelecaoDeFormaDePagamento();
    document.getElementById("recibo").style.display = "none";
    document.getElementById("tela-carrinho").style.display = "block";
}

async function copiarPix() {
    const texto = document.getElementById("recibo-pix").textContent;
    try {
        await navigator.clipboard.writeText(texto);
        const botao = document.getElementById("btn-copiar-pix");
        const textoOriginal = botao.textContent;
        botao.textContent = "Copiado!";
        setTimeout(() => (botao.textContent = textoOriginal), 1500);
    } catch (erro) {
        exibirMensagem("mensagem", "Não foi possível copiar automaticamente. Selecione o código manualmente.");
    }
}

function debounce(fn, atraso) {
    let temporizador;
    return (...args) => {
        clearTimeout(temporizador);
        temporizador = setTimeout(() => fn(...args), atraso);
    };
}
