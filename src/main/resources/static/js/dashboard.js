let sugestoesCarregadas = [];

(async function () {
    await protegerPagina();
    await carregarDashboard();

    document.getElementById("btn-copiar-sugestoes").addEventListener("click", copiarSugestoes);
    document.getElementById("btn-imprimir-sugestoes").addEventListener("click", imprimirSugestoes);
})();

async function carregarDashboard() {
    try {
        const [resumo, sugestoes, grafico] = await Promise.all([
            api.get("/api/dashboard"),
            api.get("/api/dashboard/sugestoes-compra"),
            api.get("/api/dashboard/grafico-semanal"),
        ]);

        sugestoesCarregadas = sugestoes;
        preencherKpis(resumo);
        preencherSugestoes(sugestoes);
        desenharGraficoSemanal(grafico);
    } catch (erro) {
        exibirMensagem("mensagem", erro.message);
    }
}

async function copiarSugestoes() {
    if (!sugestoesCarregadas.length) {
        exibirMensagem("mensagem", "Não há itens na sugestão de compra para copiar.", "sucesso");
        return;
    }

    const linhas = [
        `Sugestão de compra — ${new Date().toLocaleDateString("pt-BR")}`,
        "",
        ...sugestoesCarregadas.map(
            (s) => `• ${s.nome} (SKU ${s.sku}) — pedir ${s.quantidadeSugerida} un. (vende ~${s.mediaMensalDeSaida}/mês) — fornecedor: ${s.fornecedorNome}`
        ),
    ];

    try {
        await navigator.clipboard.writeText(linhas.join("\n"));
        const botao = document.getElementById("btn-copiar-sugestoes");
        const textoOriginal = botao.textContent;
        botao.textContent = "Copiado!";
        setTimeout(() => (botao.textContent = textoOriginal), 1500);
    } catch (erro) {
        exibirMensagem("mensagem", "Não foi possível copiar. Tente novamente.");
    }
}

function imprimirSugestoes() {
    if (!sugestoesCarregadas.length) {
        exibirMensagem("mensagem", "Não há itens na sugestão de compra para imprimir.", "sucesso");
        return;
    }

    const linhasTabela = sugestoesCarregadas
        .map(
            (s) => `
        <tr>
            <td>${escaparHtml(s.sku)}</td>
            <td>${escaparHtml(s.nome)}</td>
            <td>${s.quantidadeAtual}</td>
            <td>${s.estoqueMinimo}</td>
            <td>${s.mediaMensalDeSaida}</td>
            <td><strong>${s.quantidadeSugerida}</strong></td>
            <td>${escaparHtml(s.fornecedorNome)}</td>
        </tr>
    `
        )
        .join("");

    const janela = window.open("", "_blank");
    janela.document.write(`
        <!doctype html>
        <html lang="pt-BR">
        <head>
            <meta charset="UTF-8">
            <title>Sugestão de compra — ${new Date().toLocaleDateString("pt-BR")}</title>
            <style>
                body { font-family: Arial, sans-serif; padding: 24px; color: #0f172a; }
                h1 { font-size: 18px; }
                table { width: 100%; border-collapse: collapse; margin-top: 16px; }
                th, td { text-align: left; padding: 8px; border-bottom: 1px solid #e2e8f0; font-size: 13px; }
                th { text-transform: uppercase; font-size: 11px; color: #64748b; }
            </style>
        </head>
        <body>
            <h1>ProStock — Sugestão de compra</h1>
            <p>Gerado em ${new Date().toLocaleString("pt-BR")}</p>
            <table>
                <thead>
                <tr><th>SKU</th><th>Produto</th><th>Atual</th><th>Mínimo</th><th>Venda média/mês</th><th>Pedir</th><th>Fornecedor</th></tr>
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

function preencherKpis(resumo) {
    document.getElementById("kpi-valor-total").textContent = formatarMoeda(resumo.valorTotalEmEstoque);
    document.getElementById("kpi-quantidade-total").textContent = resumo.quantidadeTotalEmEstoque;
    document.getElementById("kpi-total-produtos").textContent = resumo.totalProdutos;
    document.getElementById("kpi-abaixo-minimo").textContent = resumo.produtosAbaixoDoMinimo;
    document.getElementById("kpi-ruptura").textContent = resumo.produtosEmRuptura;
    document.getElementById("kpi-alertas").textContent = resumo.alertasNaoLidos;
}

function preencherSugestoes(sugestoes) {
    const corpo = document.getElementById("tabela-sugestoes");
    const vazio = document.getElementById("vazio-sugestoes");
    corpo.innerHTML = "";

    if (!sugestoes.length) {
        vazio.style.display = "block";
        return;
    }
    vazio.style.display = "none";

    for (const s of sugestoes) {
        const linha = document.createElement("tr");
        linha.innerHTML = `
            <td>${escaparHtml(s.sku)}</td>
            <td>${escaparHtml(s.nome)}</td>
            <td>${s.quantidadeAtual}</td>
            <td>${s.estoqueMinimo}</td>
            <td>${s.mediaMensalDeSaida} un.</td>
            <td><strong>${s.quantidadeSugerida}</strong> un.</td>
            <td>${escaparHtml(s.fornecedorNome)}</td>
        `;
        corpo.appendChild(linha);
    }
}

function desenharGraficoSemanal(dados) {
    const svg = document.getElementById("grafico-semanal");
    const largura = 700;
    const altura = 220;
    const margemInferior = 30;
    const alturaUtil = altura - margemInferior;

    const maiorValor = Math.max(1, ...dados.flatMap((d) => [d.totalEntradas, d.totalSaidas]));
    const larguraGrupo = largura / dados.length;
    const larguraBarra = larguraGrupo / 4;

    let svgConteudo = "";

    dados.forEach((d, indice) => {
        const centroGrupo = indice * larguraGrupo + larguraGrupo / 2;
        const alturaEntrada = (d.totalEntradas / maiorValor) * alturaUtil;
        const alturaSaida = (d.totalSaidas / maiorValor) * alturaUtil;

        const xEntrada = centroGrupo - larguraBarra - 2;
        const xSaida = centroGrupo + 2;

        svgConteudo += `<rect x="${xEntrada}" y="${alturaUtil - alturaEntrada}" width="${larguraBarra}" height="${alturaEntrada}" rx="3" fill="var(--cor-sucesso, #16a34a)"></rect>`;
        svgConteudo += `<rect x="${xSaida}" y="${alturaUtil - alturaSaida}" width="${larguraBarra}" height="${alturaSaida}" rx="3" fill="var(--cor-perigo, #dc2626)"></rect>`;

        const rotuloDia = new Date(d.dia + "T00:00:00").toLocaleDateString("pt-BR", { weekday: "short" });
        svgConteudo += `<text x="${centroGrupo}" y="${altura - 8}" text-anchor="middle" font-size="11" fill="#64748b">${rotuloDia}</text>`;
    });

    svgConteudo += `<line x1="0" y1="${alturaUtil}" x2="${largura}" y2="${alturaUtil}" stroke="#e2e8f0" stroke-width="1"></line>`;

    svg.innerHTML = svgConteudo;
}
