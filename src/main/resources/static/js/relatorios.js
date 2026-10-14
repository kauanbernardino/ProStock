let itensCarregados = [];
let periodoCarregado = { de: "", ate: "" };

(async function () {
    const usuario = await protegerPagina();
    if (!usuario) return;

    await carregarCategorias();
    definirPeriodoPadrao();
    await gerarRelatorio();

    document.getElementById("form-relatorio").addEventListener("submit", gerarRelatorio);
    document.getElementById("btn-copiar-relatorio").addEventListener("click", copiarRelatorio);
    document.getElementById("btn-imprimir-relatorio").addEventListener("click", imprimirRelatorio);
})();

function definirPeriodoPadrao() {
    // Período padrão: do dia 1º do mês até hoje.
    const hoje = new Date();
    const primeiroDiaMes = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
    document.getElementById("filtro-de").value = paraDataInput(primeiroDiaMes);
    document.getElementById("filtro-ate").value = paraDataInput(hoje);
}

function paraDataInput(data) {
    const ano = data.getFullYear();
    const mes = String(data.getMonth() + 1).padStart(2, "0");
    const dia = String(data.getDate()).padStart(2, "0");
    return `${ano}-${mes}-${dia}`;
}

async function carregarCategorias() {
    try {
        const categorias = await api.get("/api/categorias");
        const select = document.getElementById("filtro-categoria");
        for (const c of categorias) {
            const opcao = document.createElement("option");
            opcao.value = c.id;
            opcao.textContent = c.nome;
            select.appendChild(opcao);
        }
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

async function gerarRelatorio(evento) {
    if (evento) evento.preventDefault();
    exibirMensagem("mensagem", "");

    const de = document.getElementById("filtro-de").value;
    const ate = document.getElementById("filtro-ate").value;
    const categoriaId = document.getElementById("filtro-categoria").value;

    if (!de || !ate) {
        exibirMensagem("mensagem", "Informe o período do relatório.");
        return;
    }
    if (ate < de) {
        exibirMensagem("mensagem", "A data final não pode ser anterior à data inicial.");
        return;
    }

    try {
        let caminho = `/api/relatorios?de=${de}&ate=${ate}`;
        if (categoriaId) caminho += `&categoriaId=${categoriaId}`;

        const relatorio = await api.get(caminho);
        itensCarregados = relatorio.itens;
        periodoCarregado = { de, ate };
        preencherResumo(relatorio);
        preencherTabela(relatorio.itens);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

function preencherResumo(relatorio) {
    document.getElementById("kpi-valor-estoque").textContent = formatarMoeda(relatorio.valorTotalEmEstoque);
    document.getElementById("kpi-valor-movimentado").textContent = formatarMoeda(relatorio.valorMovimentado);
    document.getElementById("kpi-entradas").textContent = `${relatorio.totalEntradas} un.`;
    document.getElementById("kpi-saidas").textContent = `${relatorio.totalSaidas} un.`;
    document.getElementById("kpi-giro-medio").textContent = `${relatorio.giroMedio}%`;
}

function classeGiro(giro) {
    if (giro === null || giro === undefined) return "giro-baixo";
    if (giro >= 80) return "giro-alto";
    if (giro >= 40) return "giro-medio";
    return "giro-baixo";
}

function preencherTabela(itens) {
    const corpo = document.getElementById("tabela-relatorio");
    const vazio = document.getElementById("vazio-relatorio");
    corpo.innerHTML = "";

    if (!itens.length) {
        vazio.style.display = "block";
        return;
    }
    vazio.style.display = "none";

    for (const item of itens) {
        const giroTexto = item.giroPercentual === null ? "—" : `${item.giroPercentual}%`;
        const larguraBarra = item.giroPercentual === null ? 0 : Math.min(100, item.giroPercentual);

        const linha = document.createElement("tr");
        linha.innerHTML = `
            <td><span class="sku-tabela">${escaparHtml(item.sku)}</span><br>${escaparHtml(item.nome)}</td>
            <td>${escaparHtml(item.categoriaNome)}</td>
            <td>${item.quantidadeAtual}</td>
            <td>${item.entradas}</td>
            <td>${item.saidas}</td>
            <td>
                <div class="giro-celula ${classeGiro(item.giroPercentual)}">
                    <div class="giro-barra"><span style="width:${larguraBarra}%"></span></div>
                    <span class="giro-pct">${giroTexto}</span>
                </div>
            </td>
            <td>${formatarMoeda(item.valorEmEstoque)}</td>
        `;
        corpo.appendChild(linha);
    }
}

async function copiarRelatorio() {
    if (!itensCarregados.length) {
        exibirMensagem("mensagem", "Não há itens no relatório para copiar.", "sucesso");
        return;
    }

    const linhas = [
        `Relatório gerencial ProStock — ${periodoCarregado.de} a ${periodoCarregado.ate}`,
        "",
        ...itensCarregados.map((i) => {
            const giro = i.giroPercentual === null ? "sem estoque" : `giro ${i.giroPercentual}%`;
            return `• ${i.nome} (SKU ${i.sku}) — estoque ${i.quantidadeAtual}, entradas ${i.entradas}, saídas ${i.saidas}, ${giro}, valor em estoque ${formatarMoeda(i.valorEmEstoque)}`;
        }),
    ];

    try {
        await navigator.clipboard.writeText(linhas.join("\n"));
        const botao = document.getElementById("btn-copiar-relatorio");
        const textoOriginal = botao.textContent;
        botao.textContent = "Copiado!";
        setTimeout(() => (botao.textContent = textoOriginal), 1500);
    } catch (erro) {
        exibirMensagem("mensagem", "Não foi possível copiar. Tente novamente.");
    }
}

function imprimirRelatorio() {
    if (!itensCarregados.length) {
        exibirMensagem("mensagem", "Não há itens no relatório para imprimir.", "sucesso");
        return;
    }

    const linhasTabela = itensCarregados
        .map((i) => `
        <tr>
            <td>${escaparHtml(i.sku)}</td>
            <td>${escaparHtml(i.nome)}</td>
            <td>${escaparHtml(i.categoriaNome)}</td>
            <td>${i.quantidadeAtual}</td>
            <td>${i.entradas}</td>
            <td>${i.saidas}</td>
            <td>${i.giroPercentual === null ? "—" : i.giroPercentual + "%"}</td>
            <td>${formatarMoeda(i.valorEmEstoque)}</td>
        </tr>
    `)
        .join("");

    const janela = window.open("", "_blank");
    janela.document.write(`
        <!doctype html>
        <html lang="pt-BR">
        <head>
            <meta charset="UTF-8">
            <title>Relatório gerencial — ${periodoCarregado.de} a ${periodoCarregado.ate}</title>
            <style>
                body { font-family: Arial, sans-serif; padding: 24px; color: #0f172a; }
                h1 { font-size: 18px; }
                table { width: 100%; border-collapse: collapse; margin-top: 16px; }
                th, td { text-align: left; padding: 8px; border-bottom: 1px solid #e2e8f0; font-size: 13px; }
                th { text-transform: uppercase; font-size: 11px; color: #64748b; }
            </style>
        </head>
        <body>
            <h1>ProStock — Relatório gerencial (valor em estoque e giro de peças)</h1>
            <p>Período: ${periodoCarregado.de} a ${periodoCarregado.ate} — gerado em ${new Date().toLocaleString("pt-BR")}</p>
            <table>
                <thead>
                <tr><th>SKU</th><th>Produto</th><th>Categoria</th><th>Estoque</th><th>Entradas</th><th>Saídas</th><th>Giro</th><th>Valor em estoque</th></tr>
                </thead>
                <tbody>${linhasTabela}</tbody>
            </table>
        </body>
        </html>
    `);
    janela.document.close();
    janela.focus();
    janela.print();
}
