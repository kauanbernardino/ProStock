let usuarioAtual = null;
let fornecedoresCarregados = [];

(async function () {
    usuarioAtual = await protegerPagina();
    if (!usuarioAtual) return;

    await carregarFornecedores();

    document.getElementById("btn-novo-fornecedor").addEventListener("click", () => abrirModal());
    document.getElementById("btn-cancelar-fornecedor").addEventListener("click", fecharModal);
    document.getElementById("form-fornecedor").addEventListener("submit", salvarFornecedor);
})();

async function carregarFornecedores() {
    try {
        fornecedoresCarregados = await api.get("/api/fornecedores");
        renderizar(fornecedoresCarregados);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function renderizar(fornecedores) {
    const corpo = document.getElementById("tabela-fornecedores");
    const vazio = document.getElementById("vazio-fornecedores");
    corpo.innerHTML = "";

    if (!fornecedores.length) {
        vazio.style.display = "block";
        return;
    }
    vazio.style.display = "none";

    const ehAdmin = usuarioAtual.cargo === "ADMINISTRADOR";

    for (const f of fornecedores) {
        const linha = document.createElement("tr");
        linha.innerHTML = `
            <td>${f.razaoSocial}</td>
            <td>${formatarCnpj(f.cnpj)}</td>
            <td>${f.contato || "-"}</td>
            <td><span class="badge ${f.ativo ? "badge-normal" : "badge-ruptura"}">${f.ativo ? "Ativo" : "Inativo"}</span></td>
            <td style="${ehAdmin ? "" : "display:none;"}">
                <button class="btn btn-secundario btn-pequeno" data-editar="${f.id}">Editar</button>
                <button class="btn btn-perigo btn-pequeno" data-inativar="${f.id}">Inativar</button>
            </td>
        `;
        corpo.appendChild(linha);
    }

    corpo.querySelectorAll("[data-editar]").forEach((btn) =>
        btn.addEventListener("click", () => abrirModal(Number(btn.dataset.editar)))
    );
    corpo.querySelectorAll("[data-inativar]").forEach((btn) =>
        btn.addEventListener("click", () => inativar(Number(btn.dataset.inativar)))
    );
}

function abrirModal(id) {
    const form = document.getElementById("form-fornecedor");
    form.reset();
    exibirMensagem("mensagem-modal", "");
    document.getElementById("fornecedor-id").value = "";
    document.getElementById("modal-titulo").textContent = "Novo fornecedor";

    if (id) {
        const fornecedor = fornecedoresCarregados.find((f) => f.id === id);
        document.getElementById("modal-titulo").textContent = "Editar fornecedor";
        document.getElementById("fornecedor-id").value = fornecedor.id;
        document.getElementById("fornecedor-razao-social").value = fornecedor.razaoSocial;
        document.getElementById("fornecedor-cnpj").value = formatarCnpj(fornecedor.cnpj);
        document.getElementById("fornecedor-contato").value = fornecedor.contato || "";
    }

    document.getElementById("modal-fornecedor").classList.add("aberto");
}

function fecharModal() {
    document.getElementById("modal-fornecedor").classList.remove("aberto");
}

async function salvarFornecedor(evento) {
    evento.preventDefault();
    exibirMensagem("mensagem-modal", "");

    const id = document.getElementById("fornecedor-id").value;
    const cnpjLimpo = document.getElementById("fornecedor-cnpj").value.replace(/\D/g, "");

    if (cnpjLimpo.length !== 14) {
        exibirMensagem("mensagem-modal", "CNPJ deve ter 14 números (com ou sem pontuação).");
        return;
    }

    const corpo = {
        razaoSocial: document.getElementById("fornecedor-razao-social").value,
        cnpj: cnpjLimpo,
        contato: document.getElementById("fornecedor-contato").value,
    };

    try {
        if (id) {
            await api.put(`/api/fornecedores/${id}`, corpo);
        } else {
            await api.post("/api/fornecedores", corpo);
        }
        fecharModal();
        await carregarFornecedores();
    } catch (erro) {
        exibirMensagem("mensagem-modal", erro.message);
    }
}

async function inativar(id) {
    if (!confirm("Inativar este fornecedor?")) return;
    try {
        await api.delete(`/api/fornecedores/${id}`);
        await carregarFornecedores();
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function formatarCnpj(cnpj) {
    if (!cnpj || cnpj.length !== 14) return cnpj;
    return cnpj.replace(/(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, "$1.$2.$3/$4-$5");
}
