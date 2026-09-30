"""
Gera o banco de DEMONSTRAÇÃO do ProStock: ~7 meses de operação simulada de uma
loja de autopeças (cadastros, compras, vendas, cancelamentos, devoluções,
avarias, requisições internas, inventários mensais e alertas).

Saída:
  src/main/resources/demo/dados-demo-1.sql ... dados-demo-7.sql
  (uma versão para cada dia da semana; ProStockDemoApplication carrega a do
  dia em que o banco é criado, ver abaixo)

Uso (na raiz do projeto):
  python ferramentas/demo/gerar_dados_demo.py

A simulação aplica exatamente as mesmas regras do sistema:
  * estoque nunca fica negativo (venda sem saldo não acontece);
  * toda mudança de estoque é uma movimentação — ao final, para cada produto,
    soma(entradas) - soma(saídas) == quantidade_atual;
  * alertas de ruptura, estoque baixo e excesso nascem e são resolvidos como
    no AlertaService (os de estoque obsoleto são criados pela própria aplicação
    ao iniciar, pela rotina automática);
  * preço do item de venda é o preço do produto no momento da venda.

As datas são geradas em relação a DATA_REFERENCIA e, ao carregar o SQL, são
deslocadas para que "hoje" seja o dia da carga (ver fim do arquivo SQL). Assim a
demonstração sempre tem movimento na última semana.

Por que 7 arquivos: o deslocamento precisa ser um número inteiro de semanas,
senão os dias da semana "escorregam" (a loja apareceria vendendo no domingo e
parada na segunda). Cada arquivo simula até a véspera de um dia da semana
diferente; carregando o arquivo do dia de hoje, o deslocamento é sempre múltiplo
de 7.
"""
import math
import random
from datetime import date, datetime, time, timedelta
from pathlib import Path

import bcrypt

from catalogo import CATEGORIAS, FORNECEDORES, PRODUTOS, USUARIOS

SEMENTE = 20261028
REFERENCIA_BASE = date(2026, 9, 28)          # segunda-feira; as 7 variantes vão até o domingo seguinte
DATA_REFERENCIA = REFERENCIA_BASE            # "hoje" da simulação (redefinido por variante)
INICIO = date(2026, 2, 23)                   # segunda-feira ~7 meses antes (redefinido por variante)
FIM = DATA_REFERENCIA - timedelta(days=1)    # simula até ontem (redefinido por variante)


def configurar_variante(referencia):
    global DATA_REFERENCIA, INICIO, FIM, SAIDA_SQL
    DATA_REFERENCIA = referencia
    inicio = referencia - timedelta(days=217)
    INICIO = inicio - timedelta(days=inicio.weekday())   # volta para a segunda-feira
    FIM = referencia - timedelta(days=1)
    SAIDA_SQL = RAIZ / f"src/main/resources/demo/dados-demo-{referencia.isoweekday()}.sql"

ADMIN, ANA, JOAO, MARCOS = 1, 2, 3, 4
DIA_SAIDA_MARCOS = 140                       # Marcos saiu da loja (usuário inativado)
DIA_DESCONTINUA_HORIZONTE = 130              # fornecedor Horizonte deixa de entregar
DIA_REAJUSTE = 140                           # reajuste de preço de alguns itens

M_COMPRA, M_VENDA, M_DEVOLUCAO, M_AVARIA, M_AJUSTE, M_ESTORNO, M_REQUISICAO = range(1, 8)

RAIZ = Path(__file__).resolve().parents[2]
SAIDA_SQL = RAIZ / "src/main/resources/demo/dados-demo.sql"

rnd = random.Random(SEMENTE)


# ---------------------------------------------------------------------------
# utilidades
# ---------------------------------------------------------------------------
def dia_util(d):
    return d.weekday() < 6  # segunda a sábado


def horario(d, inicio_h, fim_h):
    minutos = rnd.randint(inicio_h * 60, fim_h * 60 - 1)
    return datetime.combine(d, time(minutos // 60, minutos % 60, rnd.randint(0, 59)))


def expediente(d):
    return (8, 12) if d.weekday() == 5 else (8, 18)


def poisson(lam):
    if lam <= 0:
        return 0
    limite, k, prod = math.exp(-lam), 0, 1.0
    while True:
        prod *= rnd.random()
        if prod <= limite:
            return k
        k += 1


def sql_str(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int,)):
        return str(v)
    if isinstance(v, float):
        return f"{v:.2f}"
    if isinstance(v, datetime):
        return "'" + v.strftime("%Y-%m-%d %H:%M:%S") + "'"
    s = str(v).replace("'", "''")
    assert ";" not in s and "--" not in s, s
    return "'" + s + "'"


def cnpj_ficticio(base8):
    """CNPJ com dígitos verificadores válidos a partir de uma raiz fictícia."""
    numeros = [int(c) for c in f"{base8:08d}0001"]

    def dv(nums, pesos):
        soma = sum(n * p for n, p in zip(nums, pesos))
        r = soma % 11
        return 0 if r < 2 else 11 - r

    d1 = dv(numeros, [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
    d2 = dv(numeros + [d1], [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
    return "".join(map(str, numeros + [d1, d2]))


# ---------------------------------------------------------------------------
# estado da simulação
# ---------------------------------------------------------------------------
class Sim:
    def __init__(self):
        self.produtos = []          # dicts do catálogo + estado
        self.movs = []              # (id, produto, usuario, motivo, tipo, qtd, data, obs)
        self.vendas = []            # dict
        self.itens = []             # (id, venda, produto, qtd, preco)
        self.alertas = []           # dict(id, produto, tipo, msg, data, lido)
        self.alerta_aberto = {}     # (produto, tipo) -> alerta
        self.pedidos = {}           # produto -> (data_chegada, qtd)
        self.devolucoes = []        # (data, produto, qtd, venda_id, usuario)
        self.nf = 48210
        self.promo_feita = False

    # ---- alertas (mesma lógica do AlertaService.avaliarNivelDeEstoque)
    def avaliar(self, p, quando):
        q = p["qtd"]
        rotulo = f"Produto '{p['nome']}' (SKU {p['sku']})"
        self._criar(p, "RUPTURA", q <= 0, f"{rotulo} está em ruptura de estoque", quando)
        self._criar(p, "ESTOQUE_BAIXO", 0 < q <= p["minimo"], f"{rotulo} atingiu o estoque mínimo", quando)
        self._criar(p, "ESTOQUE_EXCESSO", q > p["maximo"],
                    f"{rotulo} ultrapassou o estoque máximo ({q} de no máximo {p['maximo']})", quando)
        if q > p["minimo"] or q <= 0:
            self._resolver(p, "ESTOQUE_BAIXO")
        if q > 0:
            self._resolver(p, "RUPTURA")
        if q <= p["maximo"]:
            self._resolver(p, "ESTOQUE_EXCESSO")

    def _criar(self, p, tipo, condicao, msg, quando):
        if not condicao or (p["id"], tipo) in self.alerta_aberto:
            return
        a = dict(id=len(self.alertas) + 1, produto=p["id"], tipo=tipo, msg=msg, data=quando, lido=False)
        self.alertas.append(a)
        self.alerta_aberto[(p["id"], tipo)] = a

    def _resolver(self, p, tipo):
        a = self.alerta_aberto.pop((p["id"], tipo), None)
        if a:
            a["lido"] = True

    # ---- movimentações
    def mover(self, p, usuario, motivo, tipo, qtd, quando, obs):
        assert qtd > 0
        if tipo == "SAIDA":
            assert p["qtd"] >= qtd, (p["sku"], p["qtd"], qtd)
            p["qtd"] -= qtd
            p["ultima_saida"] = quando
        else:
            p["qtd"] += qtd
        self.movs.append((len(self.movs) + 1, p["id"], usuario, motivo, tipo, qtd, quando, obs))
        self.avaliar(p, quando)

    def ajuste(self, p, usuario, nova, quando, complemento):
        anterior = p["qtd"]
        dif = nova - anterior
        if dif == 0:
            return
        obs = f"Ajuste de inventário: {anterior} → {nova} — {complemento}"
        self.mover(p, usuario, M_AJUSTE, "ENTRADA" if dif > 0 else "SAIDA", abs(dif), quando, obs)

    def proxima_nf(self):
        self.nf += rnd.randint(3, 40)
        return self.nf


def dia_numero(d):
    return (d - INICIO).days


def taxa_diaria(p, d):
    """Demanda esperada de 'eventos de venda' do produto no dia."""
    n = dia_numero(d)
    if not p["ativo_sim"] or n < p["criado"]:
        return 0.0
    base = p["demanda"]
    if p["papel"] == "obsoleto":
        base = 1.5 if (n < 25 and p["demanda"] > 0) else 0.0
    if p["papel"] == "inativo" and n >= DIA_DESCONTINUA_HORIZONTE:
        return 0.0
    tendencia = 1 + 0.02 * (n / 30)                       # loja crescendo ~2% ao mês
    sazonal = 1 + 0.12 * math.sin(2 * math.pi * n / 60)   # meses mais fortes e mais fracos
    sabado = 0.55 if d.weekday() == 5 else 1.0
    # itens que terminam a simulação em falta/baixa: procura aumentou no último mês
    if p["papel"] in ("ruptura", "baixo") and n >= dia_numero(FIM) - 30:
        base *= 2.2
    eventos_mes = base / max(1, p["unidade"])
    return eventos_mes / 26 * tendencia * sazonal * sabado


def reposicao_permitida(p, n, ultimo_dia):
    if p["papel"] == "ruptura":
        return n < ultimo_dia - 35
    if p["papel"] == "baixo":
        return n < ultimo_dia - 20
    if p["papel"] == "inativo":
        return n < DIA_DESCONTINUA_HORIZONTE
    if p["papel"] == "obsoleto":
        return False
    return True


def simular(semente):
    rnd.seed(semente)
    sim = Sim()
    for i, dados in enumerate(PRODUTOS, start=1):
        p = dict(dados)
        p.update(id=i, qtd=0, ativo_sim=True, ultima_saida=None,
                 criado_em=None, atualizado_em=None, preco_atual=dados["preco"])
        sim.produtos.append(p)

    ultimo_dia = dia_numero(FIM)
    reajustados = {"FL-OLE-6001", "EL-BAT-8101", "FR-PST-1021"}

    d = INICIO
    while d <= FIM:
        n = dia_numero(d)
        if not dia_util(d):
            d += timedelta(days=1)
            continue
        h_ini, h_fim = expediente(d)
        eventos = []  # (quando, prioridade, função)

        # --- cadastro de produtos e contagem inicial
        for p in sim.produtos:
            if n >= p["criado"]:
                if p["criado_em"] is None:
                    quando = datetime.combine(d, time(8, 5 + p["id"] % 50))
                    p["criado_em"] = p["atualizado_em"] = quando
                    inicial = max(p["minimo"] + 1, round(p["maximo"] * rnd.uniform(0.55, 0.8)))
                    if p["papel"] == "obsoleto":
                        inicial = rnd.randint(8, 10) if p["demanda"] > 0 else rnd.randint(4, 7)
                    if n == 0:
                        eventos.append((quando + timedelta(minutes=30), 0, ("inicial", p, inicial)))
                    else:
                        eventos.append((quando + timedelta(minutes=20), 0, ("compra_inicial", p, inicial)))

        # --- entregas de pedidos de compra
        for pid, (chegada, qtd, obs) in list(sim.pedidos.items()):
            if chegada <= d:
                p = sim.produtos[pid - 1]
                eventos.append((horario(d, 8, 10), 1, ("entrega", p, qtd, obs)))
                del sim.pedidos[pid]

        # --- compra promocional que deixa o aditivo acima do máximo
        # (primeiro dia útil a partir de 15 dias antes do fim)
        if n >= ultimo_dia - 15 and not sim.promo_feita:
            sim.promo_feita = True
            for p in sim.produtos:
                if p["papel"] == "excesso":
                    eventos.append((horario(d, 9, 11), 1, ("promo", p)))

        # --- reajuste de preços
        if n == DIA_REAJUSTE:
            for p in sim.produtos:
                if p["sku"] in reajustados:
                    eventos.append((datetime.combine(d, time(8, 1)), 0, ("reajuste", p)))

        # --- vendas do dia
        linhas = []
        for p in sim.produtos:
            for _ in range(poisson(taxa_diaria(p, d))):
                qtd = p["unidade"] * (2 if rnd.random() < 0.12 else 1)
                linhas.append((p, qtd))
        rnd.shuffle(linhas)
        while linhas:
            tamanho = rnd.choices([1, 2, 3], weights=[62, 28, 10])[0]
            carrinho, linhas = linhas[:tamanho], linhas[tamanho:]
            eventos.append((horario(d, h_ini, h_fim), 2, ("venda", carrinho)))

        # --- devoluções agendadas
        for dev in [x for x in sim.devolucoes if x[0] == d]:
            eventos.append((horario(d, h_ini, h_fim), 2, ("devolucao",) + dev[1:]))

        # --- avarias e requisições internas
        if rnd.random() < 0.05:
            eventos.append((horario(d, h_ini, h_fim), 3, ("avaria",)))
        if rnd.random() < 0.04:
            eventos.append((horario(d, h_ini, h_fim), 3, ("requisicao",)))

        # --- inventário mensal (último dia útil do mês)
        amanha = d + timedelta(days=1)
        proximo_util = amanha if dia_util(amanha) else amanha + timedelta(days=1)
        if proximo_util.month != d.month:
            eventos.append((datetime.combine(d, time(h_fim - 1, 30)), 4, ("inventario",)))

        eventos.sort(key=lambda e: (e[0], e[1]))
        for quando, _, ev in eventos:
            processar(sim, ev, quando, d, n, reajustados)

        # --- fim do dia: pedidos de reposição
        for p in sim.produtos:
            if (p["ativo_sim"] and p["criado_em"] and p["qtd"] <= p["minimo"]
                    and p["id"] not in sim.pedidos and reposicao_permitida(p, n, ultimo_dia)):
                prazo = rnd.randint(2, 5)
                chegada = d + timedelta(days=prazo + (1 if prazo >= (6 - d.weekday()) % 7 else 0))
                qtd = max(1, p["maximo"] - p["qtd"] - rnd.randint(0, max(0, p["maximo"] // 6)))
                fornecedor = FORNECEDORES[p["fornecedor"] - 1][0].split(" Ltda")[0]
                sim.pedidos[p["id"]] = (chegada, qtd, f"NF {sim.proxima_nf()} - {fornecedor}")

        d += timedelta(days=1)

    return sim


def vendedor(n):
    opcoes = [(ANA, 40), (JOAO, 40), (ADMIN, 6)]
    if n < DIA_SAIDA_MARCOS:
        opcoes.append((MARCOS, 18))
    return rnd.choices([u for u, _ in opcoes], weights=[w for _, w in opcoes])[0]


def processar(sim, ev, quando, d, n, reajustados):
    tipo = ev[0]
    if tipo == "inicial":
        _, p, qtd = ev
        sim.ajuste(p, ADMIN, qtd, quando, "contagem inicial na implantação do sistema")
    elif tipo == "compra_inicial":
        _, p, qtd = ev
        fornecedor = FORNECEDORES[p["fornecedor"] - 1][0].split(" Ltda")[0]
        sim.mover(p, ADMIN, M_COMPRA, "ENTRADA", qtd, quando,
                  f"NF {sim.proxima_nf()} - {fornecedor} (primeiro lote do item)")
    elif tipo == "entrega":
        _, p, qtd, obs = ev
        sim.mover(p, ADMIN, M_COMPRA, "ENTRADA", qtd, quando, obs)
    elif tipo == "promo":
        _, p = ev
        qtd = p["maximo"] + 22 - p["qtd"]
        fornecedor = FORNECEDORES[p["fornecedor"] - 1][0].split(" Ltda")[0]
        sim.mover(p, ADMIN, M_COMPRA, "ENTRADA", qtd, quando,
                  f"NF {sim.proxima_nf()} - {fornecedor} (lote promocional fechado)")
    elif tipo == "reajuste":
        _, p = ev
        novo = math.floor(p["preco_atual"] * 1.08) + 0.90
        p["preco_atual"] = round(novo, 2)
        p["atualizado_em"] = quando
    elif tipo == "venda":
        registrar_venda(sim, ev[1], quando, d, n)
    elif tipo == "devolucao":
        _, p, qtd, venda_id, usuario = ev
        sim.mover(p, usuario, M_DEVOLUCAO, "ENTRADA", qtd, quando,
                  f"Cliente devolveu - peça não serviu no veículo (venda #{venda_id})")
    elif tipo == "avaria":
        candidatos = [p for p in sim.produtos if p["qtd"] > 0 and p["ativo_sim"]
                      and p["papel"] != "obsoleto" and p["criado_em"]]
        if candidatos:
            p = rnd.choice(candidatos)
            motivo = rnd.choice(["Embalagem danificada no recebimento", "Peça caiu e trincou no balcão",
                                 "Defeito de fábrica identificado no teste", "Umidade no depósito danificou a caixa"])
            sim.mover(p, rnd.choice([ANA, JOAO, ADMIN]), M_AVARIA, "SAIDA", 1, quando, motivo)
    elif tipo == "requisicao":
        candidatos = [p for p in sim.produtos if p["categoria"] == 4 and p["qtd"] > 4 and p["criado_em"]]
        if candidatos:
            p = rnd.choice(candidatos)
            qtd = p["unidade"] if p["unidade"] > 1 else rnd.randint(1, 2)
            if p["qtd"] - qtd > 0:
                sim.mover(p, rnd.choice([ANA, JOAO]), M_REQUISICAO, "SAIDA", qtd, quando,
                          f"Uso na oficina parceira - OS nº {rnd.randint(1200, 1999)}")
    elif tipo == "inventario":
        candidatos = [p for p in sim.produtos if p["ativo_sim"] and p["qtd"] > 2
                      and p["papel"] not in ("obsoleto",) and p["criado_em"]]
        for p in rnd.sample(candidatos, min(4, len(candidatos))):
            nova = p["qtd"] + rnd.choice([-2, -1, -1, 1])
            sim.ajuste(p, ADMIN, max(0, nova), quando, "contagem mensal")
        # item do fornecedor descontinuado é inativado após o último inventário com ele
        for p in sim.produtos:
            if p["papel"] == "inativo" and p["ativo_sim"] and n >= DIA_DESCONTINUA_HORIZONTE:
                p["ativo_sim"] = False
                p["atualizado_em"] = quando


def registrar_venda(sim, carrinho, quando, d, n):
    # junta itens repetidos, como faz o VendaService
    agregado = {}
    for p, qtd in carrinho:
        agregado[p["id"]] = agregado.get(p["id"], 0) + qtd
    itens = []
    for pid, qtd in agregado.items():
        p = sim.produtos[pid - 1]
        if p["ativo_sim"] and p["qtd"] >= qtd:
            itens.append((p, qtd))
        elif p["ativo_sim"] and p["qtd"] > 0 and p["unidade"] == 1:
            itens.append((p, p["qtd"]))  # cliente leva o que tem
    if not itens:
        return
    usuario = vendedor(n)
    forma = rnd.choices(["PIX", "CARTAO", "DINHEIRO"], weights=[46, 34, 20])[0]
    venda_id = len(sim.vendas) + 1
    total = 0.0
    for p, qtd in itens:
        preco = p["preco_atual"]
        sim.itens.append((len(sim.itens) + 1, venda_id, p["id"], qtd, preco))
        total += preco * qtd
    venda = dict(id=venda_id, usuario=usuario, total=round(total, 2), forma=forma,
                 status="CONCLUIDA", data=quando, cancelada_em=None, cancelado_por=None)
    sim.vendas.append(venda)
    for p, qtd in itens:
        sim.mover(p, usuario, M_VENDA, "SAIDA", qtd, quando, f"Venda #{venda_id}")

    # cancelamento no mesmo dia (cliente desistiu / erro de lançamento)
    if rnd.random() < 0.022:
        _, fim_h = expediente(d)
        cancel = min(quando + timedelta(minutes=rnd.randint(5, 80)),
                     datetime.combine(d, time(fim_h - 1, 59)))
        venda["status"] = "CANCELADA"
        venda["cancelada_em"] = cancel
        venda["cancelado_por"] = ADMIN
        for p, qtd in itens:
            sim.mover(p, ADMIN, M_ESTORNO, "ENTRADA", qtd, cancel, f"Estorno da venda #{venda_id}")
        return

    # devolução alguns dias depois (peça não serviu)
    for p, qtd in itens:
        if rnd.random() < 0.012:
            dias = rnd.randint(1, 6)
            data_dev = d + timedelta(days=dias)
            while not dia_util(data_dev):
                data_dev += timedelta(days=1)
            if data_dev <= FIM:
                sim.devolucoes.append((data_dev, p, min(qtd, p["unidade"]), venda_id,
                                       rnd.choice([ANA, JOAO])))


# ---------------------------------------------------------------------------
# escrita do SQL
# ---------------------------------------------------------------------------
def inserir(linhas_sql, tabela, colunas, registros, lote=250):
    for i in range(0, len(registros), lote):
        valores = ",\n".join("(" + ", ".join(sql_str(v) for v in r) + ")" for r in registros[i:i + lote])
        linhas_sql.append(f"INSERT INTO {tabela} ({', '.join(colunas)}) VALUES\n{valores};\n")


def escrever_sql(sim):
    inicio_dt = datetime.combine(INICIO, time(7, 30))
    out = [
        "-- =====================================================================\n"
        "-- ProStock - dados de DEMONSTRAÇÃO (gerado por ferramentas/demo/gerar_dados_demo.py)\n"
        f"-- Simulação de {INICIO:%d/%m/%Y} a {FIM:%d/%m/%Y}. Não editar à mão.\n"
        "-- =====================================================================\n",
        "SET search_path TO prostock;\n",
        "INSERT INTO motivos_movimentacao (id, descricao) VALUES (7, 'REQUISICAO_INTERNA');\n",
    ]

    usuarios = []
    for i, (nome, email, senha, cargo, ativo, dia) in enumerate(USUARIOS, start=1):
        hash_ = bcrypt.hashpw(senha.encode(), bcrypt.gensalt(rounds=10, prefix=b"2a")).decode()
        usuarios.append((i, nome, email, hash_, cargo, ativo, inicio_dt + timedelta(days=dia, minutes=i)))
    inserir(out, "usuarios", ["id", "nome", "email", "senha_hash", "cargo", "ativo", "criado_em"], usuarios)

    inserir(out, "categorias", ["id", "nome", "descricao"],
            [(i, n, dsc) for i, (n, dsc) in enumerate(CATEGORIAS, start=1)])

    raizes = [31415926, 27182818, 16180339, 14142135, 17320508, 22360679]
    inserir(out, "fornecedores", ["id", "razao_social", "cnpj", "contato", "ativo"],
            [(i, rs, cnpj_ficticio(raizes[i - 1]), contato, ativo)
             for i, (rs, contato, ativo) in enumerate(FORNECEDORES, start=1)])

    inserir(out, "produtos",
            ["id", "nome", "sku", "descricao", "preco", "quantidade_atual", "estoque_minimo", "estoque_maximo",
             "localizacao_fisica", "categoria_id", "fornecedor_id", "ativo", "criado_em", "atualizado_em", "versao"],
            [(p["id"], p["nome"], p["sku"], p["descricao"], float(p["preco_atual"]), p["qtd"], p["minimo"],
              p["maximo"], p["local"], p["categoria"], p["fornecedor"], p["ativo_sim"], p["criado_em"],
              max(p["atualizado_em"], p["criado_em"]), 0)
             for p in sim.produtos])

    imagens, img_id = [], 1
    for p in sim.produtos:
        for ordem in range(p["fotos"]):
            imagens.append((img_id, p["id"], f"/uploads/produtos/{p['sku'].lower()}-{ordem + 1}.png", ordem,
                            p["criado_em"] + timedelta(minutes=2 + ordem)))
            img_id += 1
    inserir(out, "produto_imagens", ["id", "produto_id", "caminho", "ordem", "criado_em"], imagens)

    inserir(out, "vendas", ["id", "usuario_id", "valor_total", "forma_pagamento", "status", "data_hora",
                            "cancelada_em", "cancelado_por_usuario_id"],
            [(v["id"], v["usuario"], float(v["total"]), v["forma"], v["status"], v["data"],
              v["cancelada_em"], v["cancelado_por"]) for v in sim.vendas])
    inserir(out, "itens_venda", ["id", "venda_id", "produto_id", "quantidade", "preco_unitario"],
            [(i, v, p, q, float(pr)) for i, v, p, q, pr in sim.itens])
    inserir(out, "movimentacoes", ["id", "produto_id", "usuario_id", "motivo_id", "tipo", "quantidade",
                                   "data_hora", "observacao"], sim.movs)
    inserir(out, "alertas", ["id", "produto_id", "tipo", "mensagem", "data_criacao", "lido"],
            [(a["id"], a["produto"], a["tipo"], a["msg"], a["data"], a["lido"]) for a in sim.alertas])

    # sequências (os ids foram gravados explicitamente)
    for tabela in ["usuarios", "categorias", "fornecedores", "produtos", "produto_imagens",
                   "motivos_movimentacao", "movimentacoes", "vendas", "itens_venda", "alertas"]:
        out.append(f"SELECT setval(pg_get_serial_sequence('{tabela}', 'id'), (SELECT MAX(id) FROM {tabela}));\n")

    # desloca todas as datas para que DATA_REFERENCIA vire o dia da carga
    ref = DATA_REFERENCIA.isoformat()
    desloc = f"(CURRENT_DATE - DATE '{ref}') * INTERVAL '1 day'"
    for tabela, colunas in [("usuarios", ["criado_em"]), ("produtos", ["criado_em", "atualizado_em"]),
                            ("produto_imagens", ["criado_em"]), ("movimentacoes", ["data_hora"]),
                            ("vendas", ["data_hora", "cancelada_em"]), ("alertas", ["data_criacao"])]:
        sets = ", ".join(f"{c} = {c} + {desloc}" for c in colunas)
        out.append(f"UPDATE {tabela} SET {sets};\n")

    SAIDA_SQL.parent.mkdir(parents=True, exist_ok=True)
    SAIDA_SQL.write_text("\n".join(out), encoding="utf-8")


def resumo(sim):
    print(f"Produtos: {len(sim.produtos)}  Vendas: {len(sim.vendas)} "
          f"(canceladas: {sum(v['status'] == 'CANCELADA' for v in sim.vendas)})  "
          f"Itens: {len(sim.itens)}  Movimentações: {len(sim.movs)}  Alertas: {len(sim.alertas)} "
          f"(abertos: {sum(not a['lido'] for a in sim.alertas)})")
    fat = sum(v["total"] for v in sim.vendas if v["status"] == "CONCLUIDA")
    print(f"Faturamento simulado: R$ {fat:,.2f}")
    for p in sim.produtos:
        if p["papel"] != "normal":
            print(f"  {p['papel']:9s} {p['sku']:12s} qtd={p['qtd']:3d} min={p['minimo']} max={p['maximo']} "
                  f"ativo={p['ativo_sim']} ultima_saida={p['ultima_saida']}")
    print("Situação final:",
          "ruptura", sum(p["qtd"] == 0 and p["ativo_sim"] for p in sim.produtos),
          "| baixo", sum(0 < p["qtd"] <= p["minimo"] and p["ativo_sim"] for p in sim.produtos),
          "| excesso", sum(p["qtd"] > p["maximo"] and p["ativo_sim"] for p in sim.produtos))


def cenario_ok(sim):
    """Garante que a demonstração tenha todos os estados que o sistema sabe mostrar."""
    papel = lambda nome: [p for p in sim.produtos if p["papel"] == nome]
    return (all(p["qtd"] == 0 for p in papel("ruptura"))
            and sum(0 < p["qtd"] <= p["minimo"] for p in papel("baixo")) >= 2
            and all(p["qtd"] > p["maximo"] for p in papel("excesso"))
            and all(p["qtd"] > 0 for p in papel("obsoleto")))


if __name__ == "__main__":
    for deslocamento in range(7):
        configurar_variante(REFERENCIA_BASE + timedelta(days=deslocamento))
        semente = SEMENTE
        s = simular(semente)
        while not cenario_ok(s):
            semente += 1
            s = simular(semente)
        escrever_sql(s)
        print(f"\n=== {SAIDA_SQL.name}: simulação de {INICIO:%d/%m} a {FIM:%d/%m} (semente {semente})")
        resumo(s)
