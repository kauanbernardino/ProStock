(async function () {
    const usuario = await protegerPagina();
    if (!usuario) return;

    await carregarAlertas();

    document.getElementById("btn-verificar-obsolescencia").addEventListener("click", verificarObsolescencia);
})();

async function carregarAlertas() {
    try {
        const alertas = await api.get("/api/alertas/nao-lidos");
        renderizar(alertas);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

const rotulosTipo = {
    RUPTURA: ["Ruptura de estoque", "badge-ruptura"],
    ESTOQUE_BAIXO: ["Estoque baixo", "badge-baixo"],
    ESTOQUE_OBSOLETO: ["Estoque obsoleto", "badge-excesso"],
};

function renderizar(alertas) {
    const corpo = document.getElementById("tabela-alertas");
    const vazio = document.getElementById("vazio-alertas");
    corpo.innerHTML = "";

    if (!alertas.length) {
        vazio.style.display = "block";
        return;
    }
    vazio.style.display = "none";

    for (const a of alertas) {
        const [rotulo, classe] = rotulosTipo[a.tipo] || [a.tipo, "badge-normal"];
        const linha = document.createElement("tr");
        linha.innerHTML = `
            <td><span class="badge ${classe}">${escaparHtml(rotulo)}</span></td>
            <td>${escaparHtml(a.produtoSku)} — ${escaparHtml(a.produtoNome)}</td>
            <td>${escaparHtml(a.mensagem)}</td>
            <td>${formatarData(a.dataCriacao)}</td>
            <td><button class="btn btn-secundario btn-pequeno" data-lido="${a.id}">Marcar como lido</button></td>
        `;
        corpo.appendChild(linha);
    }

    corpo.querySelectorAll("[data-lido]").forEach((btn) =>
        btn.addEventListener("click", () => marcarComoLido(Number(btn.dataset.lido)))
    );
}

async function marcarComoLido(id) {
    try {
        await api.patch(`/api/alertas/${id}/lido`);
        await carregarAlertas();
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

async function verificarObsolescencia() {
    try {
        const produtos = await api.post("/api/alertas/verificar-obsolescencia");
        await carregarAlertas();
        if (produtos.length) {
            exibirMensagem("mensagem", `${produtos.length} produto(s) sinalizado(s) como obsoleto(s).`, "sucesso");
        } else {
            exibirMensagem("mensagem", "Nenhum produto obsoleto encontrado.", "sucesso");
        }
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}
