// Protege páginas internas: exige sessão válida e ajusta a UI conforme o cargo do usuário.

async function protegerPagina() {
    let usuario;
    try {
        usuario = await api.get("/api/auth/me");
    } catch (erro) {
        window.location.href = "/login.html";
        return null;
    }

    preencherUsuarioNaTopbar(usuario);

    if (usuario.cargo === "ADMINISTRADOR") {
        // "display: revert" restaura o valor padrão do elemento (block, inline-flex,
        // table-cell...) e vence o "display: none" da classe .somente-admin no CSS,
        // o que "" sozinho não faria (só limparia um eventual estilo inline).
        document.querySelectorAll(".somente-admin").forEach((el) => (el.style.display = "revert"));
    }

    const botaoSair = document.getElementById("btn-sair");
    if (botaoSair) {
        botaoSair.addEventListener("click", async () => {
            await api.post("/api/auth/logout");
            window.location.href = "/login.html";
        });
    }

    marcarLinkAtivo();
    configurarMenuMobile();

    return usuario;
}

// Em telas estreitas a sidebar vira uma gaveta escondida (ver @media em
// style.css); o botão hambúrguer no topo é o único jeito de abri-la.
function configurarMenuMobile() {
    const botao = document.getElementById("btn-menu");
    const sidebar = document.querySelector(".sidebar");
    const overlay = document.getElementById("overlay-menu");
    if (!botao || !sidebar || !overlay) return;

    const abrirMenu = () => {
        sidebar.classList.add("aberta");
        overlay.classList.add("aberta");
    };
    const fecharMenu = () => {
        sidebar.classList.remove("aberta");
        overlay.classList.remove("aberta");
    };

    botao.addEventListener("click", abrirMenu);
    overlay.addEventListener("click", fecharMenu);
    sidebar.querySelectorAll("nav a").forEach((link) => link.addEventListener("click", fecharMenu));
}

function preencherUsuarioNaTopbar(usuario) {
    const nomeEl = document.getElementById("usuario-nome");
    const cargoEl = document.getElementById("usuario-cargo");
    const avatarEl = document.getElementById("usuario-avatar");

    if (nomeEl) nomeEl.textContent = usuario.nome;
    if (cargoEl) cargoEl.textContent = usuario.cargo === "ADMINISTRADOR" ? "Administrador" : "Funcionário";
    if (avatarEl) avatarEl.textContent = usuario.nome.trim().charAt(0).toUpperCase();
}

function marcarLinkAtivo() {
    const paginaAtual = window.location.pathname.split("/").pop() || "dashboard.html";
    document.querySelectorAll(".sidebar nav a").forEach((link) => {
        if (link.getAttribute("href") === paginaAtual) {
            link.classList.add("ativo");
        }
    });
}

function formatarMoeda(valor) {
    return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(valor || 0);
}

function formatarData(dataIso) {
    if (!dataIso) return "-";
    return new Date(dataIso).toLocaleString("pt-BR");
}

function badgeStatusEstoque(status) {
    const mapa = {
        NORMAL: ["Normal", "badge-normal"],
        BAIXO: ["Estoque baixo", "badge-baixo"],
        RUPTURA: ["Ruptura", "badge-ruptura"],
        EXCESSO: ["Excesso", "badge-excesso"],
    };
    const [texto, classe] = mapa[status] || [status, "badge-normal"];
    return `<span class="badge ${classe}">${texto}</span>`;
}

function debounce(fn, atraso) {
    let temporizador;
    return (...args) => {
        clearTimeout(temporizador);
        temporizador = setTimeout(() => fn(...args), atraso);
    };
}

const ROTULOS_FORMA_PAGAMENTO = {
    DINHEIRO: "Dinheiro",
    PIX: "Pix",
    CARTAO: "Cartão",
};

function escaparHtml(texto) {
    const div = document.createElement("div");
    div.textContent = texto === null || texto === undefined ? "" : String(texto);
    return div.innerHTML;
}

function exibirMensagem(elementoId, texto, tipo = "erro") {
    const el = document.getElementById(elementoId);
    if (!el) return;
    el.textContent = texto;
    el.className = `mensagem mensagem-${tipo}`;
    el.style.display = texto ? "block" : "none";
}
