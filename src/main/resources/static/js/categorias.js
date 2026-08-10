let usuarioAtual = null;

(async function () {
    usuarioAtual = await protegerPagina();
    if (!usuarioAtual) return;

    await carregarCategorias();
    document.getElementById("form-categoria").addEventListener("submit", criarCategoria);
})();

async function carregarCategorias() {
    try {
        const categorias = await api.get("/api/categorias");
        renderizar(categorias);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function renderizar(categorias) {
    const corpo = document.getElementById("tabela-categorias");
    const vazio = document.getElementById("vazio-categorias");
    corpo.innerHTML = "";

    if (!categorias.length) {
        vazio.style.display = "block";
        return;
    }
    vazio.style.display = "none";

    const ehAdmin = usuarioAtual.cargo === "ADMINISTRADOR";

    for (const c of categorias) {
        const linha = document.createElement("tr");
        linha.innerHTML = `
            <td>${c.nome}</td>
            <td>${c.descricao || "-"}</td>
            <td style="${ehAdmin ? "" : "display:none;"}">
                <button class="btn btn-perigo btn-pequeno" data-remover="${c.id}">Remover</button>
            </td>
        `;
        corpo.appendChild(linha);
    }

    corpo.querySelectorAll("[data-remover]").forEach((btn) =>
        btn.addEventListener("click", () => remover(Number(btn.dataset.remover)))
    );
}

async function criarCategoria(evento) {
    evento.preventDefault();
    exibirMensagem("mensagem", "");
    try {
        await api.post("/api/categorias", {
            nome: document.getElementById("categoria-nome").value,
            descricao: document.getElementById("categoria-descricao").value,
        });
        document.getElementById("form-categoria").reset();
        await carregarCategorias();
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

async function remover(id) {
    if (!confirm("Remover esta categoria?")) return;
    try {
        await api.delete(`/api/categorias/${id}`);
        await carregarCategorias();
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}
