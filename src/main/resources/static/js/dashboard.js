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
    document.getElementById("kpi-acima-maximo").textContent = resumo.produtosAcimaDoMaximo;
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

// Gráfico "Movimentações da semana": colunas de entradas e saídas por dia.
let dadosGraficoSemanal = [];
let observadorGrafico = null;
let primeiraRenderizacao = true;

const SVG_NS = "http://www.w3.org/2000/svg";
const fmtNumero = new Intl.NumberFormat("pt-BR");

function desenharGraficoSemanal(dados) {
    dadosGraficoSemanal = dados;
    preencherResumoSemanal(dados);
    preencherTabelaSemanal(dados);
    renderizarColunas();

    if (!observadorGrafico && "ResizeObserver" in window) {
        let largura = 0;
        observadorGrafico = new ResizeObserver((entradas) => {
            const nova = Math.round(entradas[0].contentRect.width);
            if (nova !== largura) {
                largura = nova;
                renderizarColunas();
            }
        });
        observadorGrafico.observe(document.getElementById("grafico-area"));
    }
}

function dataDoDia(d) {
    return new Date(d.dia + "T00:00:00");
}

function rotuloCurto(d) {
    const data = dataDoDia(d);
    const semana = data.toLocaleDateString("pt-BR", { weekday: "short" }).replace(".", "");
    return `${semana} ${data.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" })}`;
}

function rotuloSemana(d) {
    return dataDoDia(d).toLocaleDateString("pt-BR", { weekday: "short" }).replace(".", "");
}

function rotuloLongo(d) {
    const texto = dataDoDia(d).toLocaleDateString("pt-BR", { weekday: "long", day: "2-digit", month: "long" });
    return texto.charAt(0).toUpperCase() + texto.slice(1);
}

function ehHoje(d) {
    return dataDoDia(d).toDateString() === new Date().toDateString();
}

function pluralMov(n) {
    return `${fmtNumero.format(n)} ${n === 1 ? "movimentação" : "movimentações"}`;
}

function preencherResumoSemanal(dados) {
    const soma = (campo) => dados.reduce((total, d) => total + d[campo], 0);
    document.getElementById("resumo-entradas").textContent = `${fmtNumero.format(soma("totalEntradas"))} peças`;
    document.getElementById("resumo-entradas-mov").textContent = pluralMov(soma("movimentacoesDeEntrada"));
    document.getElementById("resumo-saidas").textContent = `${fmtNumero.format(soma("totalSaidas"))} peças`;
    document.getElementById("resumo-saidas-mov").textContent = pluralMov(soma("movimentacoesDeSaida"));

    const pico = dados.reduce((melhor, d) => {
        const total = d.movimentacoesDeEntrada + d.movimentacoesDeSaida;
        return !melhor || total > melhor.total ? { d, total } : melhor;
    }, null);
    if (pico && pico.total > 0) {
        document.getElementById("resumo-pico").textContent = rotuloCurto(pico.d);
        document.getElementById("resumo-pico-detalhe").textContent = pluralMov(pico.total);
    } else {
        document.getElementById("resumo-pico").textContent = "—";
        document.getElementById("resumo-pico-detalhe").textContent = "Nenhuma movimentação na semana";
    }
}

function preencherTabelaSemanal(dados) {
    const corpo = document.getElementById("grafico-tabela-corpo");
    corpo.replaceChildren();
    dados.forEach((d) => {
        const linha = document.createElement("tr");
        [rotuloLongo(d), d.totalEntradas, d.movimentacoesDeEntrada, d.totalSaidas, d.movimentacoesDeSaida]
            .forEach((valor) => {
                const celula = document.createElement("td");
                celula.textContent = typeof valor === "number" ? fmtNumero.format(valor) : valor;
                linha.appendChild(celula);
            });
        corpo.appendChild(linha);
    });
}

/** Escala com teto e passo redondos. */
function escalaEixo(maximo) {
    if (maximo <= 0) return { teto: 4, passo: 1 };
    const bruto = maximo / 4;
    const potencia = Math.pow(10, Math.floor(Math.log10(bruto)));
    const passo = [1, 2, 2.5, 5, 10].map((m) => m * potencia).find((p) => p >= bruto);
    return { teto: Math.ceil(maximo / passo) * passo, passo };
}

function el(nome, atributos = {}, texto) {
    const no = document.createElementNS(SVG_NS, nome);
    Object.entries(atributos).forEach(([k, v]) => no.setAttribute(k, v));
    if (texto !== undefined) no.textContent = texto;
    return no;
}

/** Coluna com o topo arredondado (4px) e a base reta, apoiada na linha zero. */
function caminhoColuna(x, y, largura, altura) {
    const r = Math.min(4, largura / 2, altura);
    return `M${x},${y + altura} V${y + r} Q${x},${y} ${x + r},${y} H${x + largura - r} ` +
        `Q${x + largura},${y} ${x + largura},${y + r} V${y + altura} Z`;
}

function renderizarColunas() {
    const dados = dadosGraficoSemanal;
    const svg = document.getElementById("grafico-semanal");
    if (!dados.length || !svg) return;

    const largura = Math.max(320, svg.getBoundingClientRect().width || 700);
    const altura = 260;
    const margem = { topo: 22, direita: 8, base: 34, esquerda: 40 };
    const larguraUtil = largura - margem.esquerda - margem.direita;
    const alturaUtil = altura - margem.topo - margem.base;
    const baseY = margem.topo + alturaUtil;

    const maior = Math.max(0, ...dados.flatMap((d) => [d.totalEntradas, d.totalSaidas]));
    const { teto, passo } = escalaEixo(maior);
    const y = (v) => baseY - (v / teto) * alturaUtil;

    svg.setAttribute("viewBox", `0 0 ${largura} ${altura}`);
    svg.replaceChildren();

    // Grade horizontal e valores do eixo
    for (let v = 0; v <= teto + 1e-9; v += passo) {
        svg.appendChild(el("line", {
            x1: margem.esquerda, x2: largura - margem.direita, y1: y(v), y2: y(v),
            stroke: v === 0 ? "#cbd5e1" : "var(--grade-grafico)", "stroke-width": 1,
        }));
        svg.appendChild(el("text", {
            x: margem.esquerda - 8, y: y(v) + 4, "text-anchor": "end", class: "eixo-texto",
        }, fmtNumero.format(v)));
    }

    const larguraFaixa = larguraUtil / dados.length;
    const larguraColuna = Math.min(24, (larguraFaixa - 16) / 2);
    const intervalo = 2;
    const tooltip = document.getElementById("grafico-tooltip");
    const animar = primeiraRenderizacao;
    const compacto = larguraFaixa < 64;           // em telas estreitas, só o dia da semana

    dados.forEach((d, i) => {
        const x0 = margem.esquerda + i * larguraFaixa;
        const centro = x0 + larguraFaixa / 2;
        const grupo = el("g", { class: "grupo-dia" });

        const series = [
            { valor: d.totalEntradas, cor: "var(--serie-entrada)", x: centro - intervalo / 2 - larguraColuna },
            { valor: d.totalSaidas, cor: "var(--serie-saida)", x: centro + intervalo / 2 },
        ];

        series.forEach((s, k) => {
            if (s.valor <= 0) return;
            const topo = y(s.valor);
            const coluna = el("path", {
                d: caminhoColuna(s.x, topo, larguraColuna, baseY - topo),
                fill: s.cor, class: "coluna",
            });
            const atraso = animar ? `${i * 60 + k * 30}ms` : "0ms";
            coluna.style.animationDelay = atraso;
            if (!animar) coluna.style.animation = "none";
            grupo.appendChild(coluna);

            const valor = el("text", {
                x: s.x + larguraColuna / 2, y: topo - 6, "text-anchor": "middle", class: "valor-coluna",
            }, fmtNumero.format(s.valor));
            valor.style.animationDelay = animar ? `${i * 60 + k * 30 + 350}ms` : "0ms";
            if (!animar) valor.style.animation = "none";
            grupo.appendChild(valor);
        });

        if (d.totalEntradas === 0 && d.totalSaidas === 0) {
            grupo.appendChild(el("text", {
                x: centro, y: baseY - 8, "text-anchor": "middle", class: "sem-movimento",
            }, compacto ? "—" : "sem movimento"));
        }

        grupo.appendChild(el("text", {
            x: centro, y: baseY + 20, "text-anchor": "middle", class: "rotulo-dia" + (ehHoje(d) ? " hoje" : ""),
        }, ehHoje(d) ? "hoje" : (compacto ? rotuloSemana(d) : rotuloCurto(d))));

        // Área do dia inteiro, alvo do mouse e do teclado
        const faixa = el("rect", {
            x: x0 + 2, y: margem.topo - 10, width: larguraFaixa - 4, height: alturaUtil + 10, rx: 6,
            class: "faixa-dia", tabindex: 0, "aria-label":
                `${rotuloLongo(d)}: entraram ${d.totalEntradas} peças em ${d.movimentacoesDeEntrada} movimentações; ` +
                `saíram ${d.totalSaidas} peças em ${d.movimentacoesDeSaida} movimentações`,
        });
        const mostrar = () => {
            grupo.classList.add("ativo");
            mostrarTooltip(tooltip, d, centro, larguraFaixa, largura, y(Math.max(d.totalEntradas, d.totalSaidas)));
        };
        const esconder = () => {
            grupo.classList.remove("ativo");
            tooltip.classList.remove("visivel");
        };
        faixa.addEventListener("pointerenter", mostrar);
        faixa.addEventListener("focus", mostrar);
        faixa.addEventListener("pointerleave", esconder);
        faixa.addEventListener("blur", esconder);
        grupo.appendChild(faixa); // por cima das colunas, para o mouse não perder o dia

        svg.appendChild(grupo);
    });

    primeiraRenderizacao = false;
}

function mostrarTooltip(tooltip, d, centro, larguraFaixa, larguraTotal, topoColunas) {
    tooltip.replaceChildren();
    const titulo = document.createElement("div");
    titulo.className = "tooltip-titulo";
    titulo.textContent = rotuloLongo(d);
    tooltip.appendChild(titulo);

    [
        ["Entradas", d.totalEntradas, d.movimentacoesDeEntrada, "var(--serie-entrada)"],
        ["Saídas", d.totalSaidas, d.movimentacoesDeSaida, "var(--serie-saida)"],
    ].forEach(([nome, pecas, movs, cor]) => {
        const linha = document.createElement("div");
        linha.className = "tooltip-linha";
        const chave = document.createElement("span");
        chave.className = "tooltip-chave";
        chave.style.background = cor;
        const valor = document.createElement("strong");
        valor.textContent = `${fmtNumero.format(pecas)} ${pecas === 1 ? "peça" : "peças"}`;
        const detalhe = document.createElement("span");
        detalhe.textContent = `${nome.toLowerCase()} · ${pluralMov(movs)}`;
        linha.append(chave, valor, detalhe);
        tooltip.appendChild(linha);
    });

    tooltip.classList.add("visivel");
    const larguraTooltip = tooltip.offsetWidth;
    let esquerda = centro + larguraFaixa / 2 + 4;
    if (esquerda + larguraTooltip > larguraTotal) {
        esquerda = centro - larguraFaixa / 2 - larguraTooltip - 4;
    }
    tooltip.style.left = `${Math.max(0, esquerda)}px`;
    tooltip.style.top = `${Math.max(0, topoColunas - 10)}px`;
}
