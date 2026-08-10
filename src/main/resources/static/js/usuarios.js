let usuariosCarregados = [];
let usuarioAtual = null;

(async function () {
    usuarioAtual = await protegerPagina();
    if (!usuarioAtual) return;

    if (usuarioAtual.cargo !== "ADMINISTRADOR") {
        document.getElementById("acesso-negado").style.display = "block";
        return;
    }
    document.getElementById("area-admin").style.display = "block";

    await carregarUsuarios();

    document.getElementById("btn-novo-usuario").addEventListener("click", () => abrirModal());
    document.getElementById("btn-cancelar-usuario").addEventListener("click", fecharModal);
    document.getElementById("form-usuario").addEventListener("submit", salvarUsuario);
})();

async function carregarUsuarios() {
    try {
        usuariosCarregados = await api.get("/api/usuarios");
        renderizar(usuariosCarregados);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function renderizar(usuarios) {
    const corpo = document.getElementById("tabela-usuarios");
    corpo.innerHTML = "";

    for (const u of usuarios) {
        const linha = document.createElement("tr");
        linha.innerHTML = `
            <td>${escaparHtml(u.nome)}</td>
            <td>${escaparHtml(u.email)}</td>
            <td>${u.cargo === "ADMINISTRADOR" ? "Administrador" : "Funcionário"}</td>
            <td><span class="badge ${u.ativo ? "badge-normal" : "badge-ruptura"}">${u.ativo ? "Ativo" : "Inativo"}</span></td>
            <td>
                <button class="btn btn-secundario btn-pequeno" data-editar="${u.id}">Editar</button>
                <button class="btn btn-pequeno ${u.ativo ? "btn-perigo" : ""}" data-alternar="${u.id}" data-ativo="${u.ativo}">
                    ${u.ativo ? "Inativar" : "Reativar"}
                </button>
            </td>
        `;
        corpo.appendChild(linha);
    }

    corpo.querySelectorAll("[data-editar]").forEach((btn) =>
        btn.addEventListener("click", () => abrirModal(Number(btn.dataset.editar)))
    );
    corpo.querySelectorAll("[data-alternar]").forEach((btn) =>
        btn.addEventListener("click", () => alternarStatus(Number(btn.dataset.alternar), btn.dataset.ativo === "true"))
    );
}

function abrirModal(id) {
    const form = document.getElementById("form-usuario");
    form.reset();
    exibirMensagem("mensagem-modal", "");
    document.getElementById("usuario-id").value = "";
    document.getElementById("modal-titulo").textContent = "Novo usuário";
    document.getElementById("senha-opcional").style.display = "none";
    document.getElementById("usuario-senha").required = true;

    if (id) {
        const usuario = usuariosCarregados.find((u) => u.id === id);
        document.getElementById("modal-titulo").textContent = "Editar usuário";
        document.getElementById("usuario-id").value = usuario.id;
        document.getElementById("usuario-nome-campo").value = usuario.nome;
        document.getElementById("usuario-email").value = usuario.email;
        document.getElementById("usuario-cargo-campo").value = usuario.cargo;
        document.getElementById("senha-opcional").style.display = "inline";
        document.getElementById("usuario-senha").required = false;
    }

    document.getElementById("modal-usuario").classList.add("aberto");
}

function fecharModal() {
    document.getElementById("modal-usuario").classList.remove("aberto");
}

async function salvarUsuario(evento) {
    evento.preventDefault();
    exibirMensagem("mensagem-modal", "");

    const id = document.getElementById("usuario-id").value;
    const senha = document.getElementById("usuario-senha").value;
    const corpo = {
        nome: document.getElementById("usuario-nome-campo").value,
        email: document.getElementById("usuario-email").value,
        senha: senha || null,
        cargo: document.getElementById("usuario-cargo-campo").value,
    };

    try {
        if (id) {
            await api.put(`/api/usuarios/${id}`, corpo);
        } else {
            await api.post("/api/usuarios", corpo);
        }
        fecharModal();
        await carregarUsuarios();
    } catch (erro) {
        exibirMensagem("mensagem-modal", erro.message);
    }
}

async function alternarStatus(id, ativo) {
    const acao = ativo ? "inativar" : "reativar";
    try {
        await api.patch(`/api/usuarios/${id}/${acao}`);
        await carregarUsuarios();
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}
