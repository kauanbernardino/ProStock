(async function () {
    const usuario = await protegerPagina();
    if (!usuario) return;

    await Promise.all([carregarProdutos(), carregarMotivos()]);
    await carregarHistorico();

    document.getElementById("form-movimentacao").addEventListener("submit", registrarMovimentacao);
})();

async function carregarProdutos() {
    const produtos = await api.get("/api/produtos");
    const select = document.getElementById("mov-produto");
    select.innerHTML = produtos
        .map((p) => `<option value="${p.id}">${escaparHtml(p.sku)} — ${escaparHtml(p.nome)} (${p.quantidadeAtual} un.)</option>`)
        .join("");
}

async function carregarMotivos() {
    const motivos = await api.get("/api/motivos");
    const select = document.getElementById("mov-motivo");
    select.innerHTML = motivos.map((m) => `<option value="${m.id}">${escaparHtml(m.descricao)}</option>`).join("");
}

async function carregarHistorico() {
    try {
        const fim = new Date();
        const inicio = new Date();
        inicio.setDate(inicio.getDate() - 30);

        const movimentacoes = await api.get(
            `/api/movimentacoes?inicio=${inicio.toISOString()}&fim=${fim.toISOString()}`
        );
        renderizarHistorico(movimentacoes);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function renderizarHistorico(movimentacoes) {
    const corpo = document.getElementById("tabela-movimentacoes");
    const vazio = document.getElementById("vazio-movimentacoes");
    corpo.innerHTML = "";

    if (!movimentacoes.length) {
        vazio.style.display = "block";
        return;
    }
    vazio.style.display = "none";

    for (const m of movimentacoes) {
        const linha = document.createElement("tr");
        const classeTipo = m.tipo === "ENTRADA" ? "badge-entrada" : "badge-saida";
        const rotuloTipo = m.tipo === "ENTRADA" ? "Entrada" : "Saída";
        linha.innerHTML = `
            <td>${formatarData(m.dataHora)}</td>
            <td>${escaparHtml(m.produtoSku)} — ${escaparHtml(m.produtoNome)}</td>
            <td><span class="badge ${classeTipo}">${rotuloTipo}</span></td>
            <td>${m.quantidade}</td>
            <td>${escaparHtml(m.motivoDescricao)}</td>
            <td>${escaparHtml(m.usuarioNome)}</td>
            <td>${escaparHtml(m.observacao || "-")}</td>
        `;
        corpo.appendChild(linha);
    }
}

async function registrarMovimentacao(evento) {
    evento.preventDefault();
    exibirMensagem("mensagem-form", "");

    const corpo = {
        produtoId: Number(document.getElementById("mov-produto").value),
        motivoId: Number(document.getElementById("mov-motivo").value),
        tipo: document.getElementById("mov-tipo").value,
        quantidade: Number(document.getElementById("mov-quantidade").value),
        observacao: document.getElementById("mov-observacao").value,
    };

    try {
        await api.post("/api/movimentacoes", corpo);
        document.getElementById("form-movimentacao").reset();
        await Promise.all([carregarProdutos(), carregarHistorico()]);
    } catch (erro) {
        exibirMensagem("mensagem-form", erro.message);
    }
}
