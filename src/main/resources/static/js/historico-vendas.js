let usuarioAtual = null;

(async function () {
    usuarioAtual = await protegerPagina();
    if (!usuarioAtual) return;

    configurarJanela("modal-venda");
    await carregarVendas();
})();

async function carregarVendas() {
    try {
        const vendas = await api.get("/api/vendas");
        renderizarVendas(vendas);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function renderizarVendas(vendas) {
    const corpo = document.getElementById("tabela-vendas");
    const vazio = document.getElementById("vazio-vendas");
    corpo.innerHTML = "";

    if (!vendas.length) {
        vazio.style.display = "block";
        return;
    }
    vazio.style.display = "none";

    for (const v of vendas) {
        const cancelada = v.status === "CANCELADA";
        const linha = document.createElement("tr");
        linha.title = "Clique para ver os itens desta venda";
        tornarLinhaClicavel(linha, () => abrirDetalhesVenda(v.id));
        linha.innerHTML = `
            <td>${formatarData(v.dataHora)}</td>
            <td>${escaparHtml(v.usuarioNome)}</td>
            <td>${v.quantidadeItens}</td>
            <td>${ROTULOS_FORMA_PAGAMENTO[v.formaPagamento]}</td>
            <td>${formatarMoeda(v.valorTotal)}</td>
            <td><span class="badge ${cancelada ? "badge-ruptura" : "badge-normal"}">${cancelada ? "Cancelada" : "Concluída"}</span></td>
            <td>
                <button type="button" class="btn btn-secundario btn-pequeno" data-detalhes="${v.id}">Ver itens</button>
                <button type="button" class="btn btn-secundario btn-pequeno" data-reimprimir="${v.id}">Reimprimir</button>
                ${cancelada ? "" : `<button type="button" class="btn btn-perigo btn-pequeno" data-cancelar="${v.id}">Cancelar</button>`}
            </td>
        `;
        corpo.appendChild(linha);
    }

    corpo.querySelectorAll("[data-detalhes]").forEach((btn) =>
        btn.addEventListener("click", () => abrirDetalhesVenda(Number(btn.dataset.detalhes)))
    );
    corpo.querySelectorAll("[data-reimprimir]").forEach((btn) =>
        btn.addEventListener("click", () => reimprimir(Number(btn.dataset.reimprimir)))
    );
    corpo.querySelectorAll("[data-cancelar]").forEach((btn) =>
        btn.addEventListener("click", () => cancelarVenda(Number(btn.dataset.cancelar)))
    );
}

async function abrirDetalhesVenda(id) {
    try {
        const venda = await api.get(`/api/vendas/${id}`);
        renderizarDetalhesVenda(venda);
        abrirJanela("modal-venda");
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function renderizarDetalhesVenda(venda) {
    const cancelada = venda.status === "CANCELADA";
    exibirMensagem("mensagem-venda", "");

    document.getElementById("venda-titulo").innerHTML =
        `Venda #${venda.id} <span class="badge ${cancelada ? "badge-ruptura" : "badge-normal"}">${cancelada ? "Cancelada" : "Concluída"}</span>`;
    document.getElementById("venda-subtitulo").textContent =
        `${formatarData(venda.dataHora)} · Vendedor: ${venda.usuarioNome} · Pagamento: ${ROTULOS_FORMA_PAGAMENTO[venda.formaPagamento]}`;

    exibirMensagem("venda-cancelamento", cancelada
        ? `Cancelada em ${formatarData(venda.canceladaEm)} por ${venda.canceladoPorNome}. O estoque destes itens já foi devolvido.`
        : "");

    document.getElementById("venda-itens").innerHTML = venda.itens
        .map((item) => `
            <tr>
                <td>${escaparHtml(item.produtoNome)}</td>
                <td>${escaparHtml(item.produtoSku)}</td>
                <td>${item.quantidade}</td>
                <td>${formatarMoeda(item.precoUnitario)}</td>
                <td>${formatarMoeda(item.subtotal)}</td>
            </tr>`)
        .join("");
    document.getElementById("venda-total").textContent = `Total: ${formatarMoeda(venda.valorTotal)}`;

    const temPix = venda.formaPagamento === "PIX" && venda.pixCopiaECola && !cancelada;
    document.getElementById("venda-pix").style.display = temPix ? "block" : "none";
    document.getElementById("venda-pix-codigo").textContent = temPix ? venda.pixCopiaECola : "";

    document.getElementById("btn-venda-reimprimir").onclick = () => abrirJanelaDeImpressao(venda);
    const btnCancelar = document.getElementById("btn-venda-cancelar");
    btnCancelar.style.display = cancelada ? "none" : "";
    btnCancelar.onclick = async () => {
        const cancelou = await cancelarVenda(venda.id, "mensagem-venda");
        if (cancelou) await abrirDetalhesVenda(venda.id);
    };
}

async function cancelarVenda(id, idMensagem = "mensagem") {
    if (!confirm(`Cancelar a venda #${id}? O estoque dos itens será devolvido automaticamente.`)) return false;
    try {
        await api.patch(`/api/vendas/${id}/cancelar`);
        await carregarVendas();
        return true;
    } catch (erro) {
        exibirMensagem(idMensagem, erro.message);
        return false;
    }
}

async function reimprimir(id) {
    try {
        const venda = await api.get(`/api/vendas/${id}`);
        abrirJanelaDeImpressao(venda);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function abrirJanelaDeImpressao(venda) {
    const linhasItens = venda.itens
        .map(
            (item) => `
        <tr>
            <td>${escaparHtml(item.produtoNome)} (SKU ${escaparHtml(item.produtoSku)})</td>
            <td>${item.quantidade}</td>
            <td>${formatarMoeda(item.precoUnitario)}</td>
            <td>${formatarMoeda(item.subtotal)}</td>
        </tr>
    `
        )
        .join("");

    const blocoPix = venda.formaPagamento === "PIX"
        ? `
        <h3>Pagamento via Pix</h3>
        <div class="pix">${escaparHtml(venda.pixCopiaECola)}</div>
    `
        : "";

    const blocoCancelamento = venda.status === "CANCELADA"
        ? `<p class="cancelada">VENDA CANCELADA em ${formatarData(venda.canceladaEm)} por ${escaparHtml(venda.canceladoPorNome)}</p>`
        : "";

    const janela = window.open("", "_blank");
    janela.document.write(`
        <!doctype html>
        <html lang="pt-BR">
        <head>
            <meta charset="UTF-8">
            <title>Recibo — Venda #${venda.id}</title>
            <style>
                body { font-family: Arial, sans-serif; padding: 24px; color: #0f172a; }
                h1 { font-size: 18px; margin-bottom: 4px; }
                h3 { font-size: 14px; margin-top: 24px; }
                .aviso { background: #fef3c7; color: #d97706; padding: 10px 14px; border-radius: 8px; font-size: 13px; font-weight: 600; margin: 12px 0; }
                .cancelada { background: #fee2e2; color: #dc2626; padding: 10px 14px; border-radius: 8px; font-size: 13px; font-weight: 700; }
                table { width: 100%; border-collapse: collapse; margin-top: 12px; }
                th, td { text-align: left; padding: 8px; border-bottom: 1px solid #e2e8f0; font-size: 13px; }
                th { text-transform: uppercase; font-size: 11px; color: #64748b; }
                .total { text-align: right; font-size: 18px; font-weight: 700; margin-top: 12px; }
                .pix { background: #f8fafc; border: 1px dashed #e2e8f0; border-radius: 8px; padding: 12px; font-family: monospace; font-size: 12px; word-break: break-all; }
            </style>
        </head>
        <body>
            <h1>ProStock — Recibo de venda #${venda.id}</h1>
            <div class="aviso">ESTE DOCUMENTO NÃO É UMA NOTA FISCAL — apenas um comprovante interno da venda.</div>
            ${blocoCancelamento}
            <p>Data: ${formatarData(venda.dataHora)} | Vendedor: ${escaparHtml(venda.usuarioNome)} | Pagamento: ${ROTULOS_FORMA_PAGAMENTO[venda.formaPagamento]}</p>
            <table>
                <thead>
                <tr><th>Produto</th><th>Qtd.</th><th>Preço unit.</th><th>Subtotal</th></tr>
                </thead>
                <tbody>${linhasItens}</tbody>
            </table>
            <div class="total">Total: ${formatarMoeda(venda.valorTotal)}</div>
            ${blocoPix}
        </body>
        </html>
    `);
    janela.document.close();
    janela.focus();
    janela.print();
}
