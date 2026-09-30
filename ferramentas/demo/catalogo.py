"""
Catálogo fictício usado pelo gerador de dados de demonstração do ProStock.

Nomes de fornecedores e pessoas são inventados. As aplicações (veículo/ano)
imitam o jeito como uma loja de autopeças descreve as peças: o mesmo nome de
peça aparece com SKUs diferentes porque serve em carros/anos diferentes.

Campos de cada produto:
  sku, nome, descricao, preco, minimo, maximo, local, categoria, fornecedor,
  demanda  -> unidades vendidas por mês, em média (0 = não vende)
  desenho  -> tipo de ilustração gerada para a foto
  papel    -> comportamento especial na simulação (ver gerar_dados_demo.py)
  criado   -> dias depois do início da simulação em que o produto foi cadastrado
  unidade  -> quantidade típica por item de venda (ex.: óleo sai de 4 em 4 litros)
"""

CATEGORIAS = [
    ("Freios", "Pastilhas, discos, lonas, cilindros e fluidos de freio"),
    ("Suspensão", "Amortecedores, bandejas, pivôs, buchas e terminais"),
    ("Motor", "Juntas, correias, bombas e componentes internos do motor"),
    ("Filtros e Lubrificantes", "Filtros de óleo, ar, combustível e cabine, além de óleos e aditivos"),
    ("Ignição", "Velas, cabos de vela e bobinas"),
    ("Elétrica e Iluminação", "Lâmpadas, baterias, relés e componentes elétricos"),
    ("Arrefecimento", "Radiadores, bombas d'água, válvulas termostáticas e mangueiras"),
    ("Embreagem e Transmissão", "Kits de embreagem, cabos, rolamentos e coifas"),
]

# (razão social, contato, ativo)
FORNECEDORES = [
    ("Distribuidora Paulista de Autopeças Ltda", "Carlos Menezes - (18) 3321-0000", True),
    ("Rede Sul Distribuidora Automotiva Ltda", "Fernanda Lopes - (14) 3402-0000", True),
    ("Oeste Paulista Rolamentos e Transmissão Ltda", "Rogério Alves - (18) 3908-0000", True),
    ("Central Elétrica Automotiva Ltda", "Juliana Prado - (11) 4003-0000", True),
    ("Vale do Paranapanema Filtros e Lubrificantes Ltda", "Paulo Ribeiro - (18) 3366-0000", True),
    ("Horizonte Peças Automotivas Ltda", "Sem contato ativo (fornecedor descontinuado)", False),
]

# Usuários da loja: (nome, email, senha, cargo, ativo, dia de cadastro)
USUARIOS = [
    ("Kauan Bernardino (Administrador)", "admin@prostock.com", "admin123", "ADMINISTRADOR", True, 0),
    ("Ana Souza", "ana.souza@prostock.com", "func123", "FUNCIONARIO", True, 0),
    ("João Pereira", "joao.pereira@prostock.com", "func123", "FUNCIONARIO", True, 0),
    ("Marcos Lima", "marcos.lima@prostock.com", "func123", "FUNCIONARIO", False, 0),
]

C_FREIOS, C_SUSP, C_MOTOR, C_FILTROS, C_IGN, C_ELET, C_ARREF, C_EMBR = range(1, 9)
F_PAULISTA, F_SUL, F_OESTE, F_ELETRICA, F_FILTROS, F_HORIZONTE = range(1, 7)


def p(sku, nome, descricao, preco, minimo, maximo, local, categoria, fornecedor, demanda,
      desenho, papel="normal", criado=0, unidade=1, fotos=2):
    return dict(sku=sku, nome=nome, descricao=descricao, preco=preco, minimo=minimo,
                maximo=maximo, local=local, categoria=categoria, fornecedor=fornecedor,
                demanda=demanda, desenho=desenho, papel=papel, criado=criado,
                unidade=unidade, fotos=fotos)


PRODUTOS = [
    # ---------------- Freios ----------------
    p("FR-PST-1021", "Pastilha de freio dianteira",
      "Jogo com 4 pastilhas. Aplicação: VW Gol G5/G6/G7 1.0 e 1.6 (2008-2022), Voyage, Fox.",
      89.90, 6, 30, "Corredor A - Prateleira 1", C_FREIOS, F_PAULISTA, 14, "pastilha"),
    p("FR-PST-1034", "Pastilha de freio dianteira",
      "Jogo com 4 pastilhas. Aplicação: Fiat Uno/Palio/Siena 1.0 e 1.4 Fire (2004-2016).",
      79.90, 6, 30, "Corredor A - Prateleira 1", C_FREIOS, F_PAULISTA, 12, "pastilha", papel="ruptura"),
    p("FR-PST-1047", "Pastilha de freio dianteira",
      "Jogo com 4 pastilhas cerâmicas. Aplicação: Chevrolet Onix/Prisma 1.0 e 1.4 (2013-2019).",
      109.90, 5, 25, "Corredor A - Prateleira 1", C_FREIOS, F_SUL, 10, "pastilha"),
    p("FR-DSC-2210", "Disco de freio ventilado (par)",
      "Par de discos dianteiros ventilados 239 mm. Aplicação: VW Gol G5/G6, Voyage, Saveiro (2008-2016).",
      289.90, 2, 10, "Corredor A - Prateleira 2", C_FREIOS, F_PAULISTA, 3, "disco"),
    p("FR-DSC-2233", "Disco de freio sólido (par)",
      "Par de discos dianteiros sólidos 240 mm. Aplicação: Fiat Uno Mille e Palio Fire (2004-2013).",
      219.90, 2, 10, "Corredor A - Prateleira 2", C_FREIOS, F_PAULISTA, 2, "disco"),
    p("FR-FLD-0501", "Fluido de freio DOT 4 500 ml",
      "Fluido sintético DOT 4, frasco de 500 ml. Uso geral em sistemas de freio hidráulico.",
      32.90, 8, 40, "Corredor A - Prateleira 3", C_FREIOS, F_FILTROS, 9, "frasco"),
    p("FR-LON-3110", "Lona de freio traseira",
      "Jogo de lonas para tambor 180 mm. Aplicação: VW Fusca e Brasília (1970-1986).",
      64.90, 2, 12, "Corredor A - Prateleira 4", C_FREIOS, F_HORIZONTE, 0, "pastilha",
      papel="obsoleto"),

    # ---------------- Suspensão ----------------
    p("SU-AMT-4102", "Amortecedor dianteiro",
      "Amortecedor a gás, lado esquerdo ou direito. Aplicação: VW Gol G5/G6 (2008-2016).",
      249.90, 2, 10, "Corredor B - Prateleira 1", C_SUSP, F_SUL, 3, "amortecedor"),
    p("SU-AMT-4115", "Amortecedor traseiro",
      "Amortecedor a gás. Aplicação: Fiat Palio/Siena Fire (2004-2016).",
      189.90, 2, 10, "Corredor B - Prateleira 1", C_SUSP, F_SUL, 2, "amortecedor"),
    p("SU-BAN-4230", "Bandeja de suspensão dianteira",
      "Bandeja com pivô e buchas, lado direito. Aplicação: Chevrolet Celta/Prisma (2003-2014).",
      179.90, 1, 6, "Corredor B - Prateleira 2", C_SUSP, F_SUL, 1.5, "bandeja"),
    p("SU-PIV-4301", "Pivô de suspensão",
      "Pivô inferior. Aplicação: VW Gol G2/G3/G4 (1995-2013).",
      59.90, 3, 15, "Corredor B - Prateleira 2", C_SUSP, F_PAULISTA, 4, "pivo"),
    p("SU-TER-4410", "Terminal de direção",
      "Terminal de direção lado esquerdo. Aplicação: Fiat Uno/Palio/Strada (1996-2016).",
      49.90, 3, 15, "Corredor B - Prateleira 3", C_SUSP, F_PAULISTA, 4, "pivo"),
    p("SU-KIT-4520", "Kit batente e coifa do amortecedor",
      "Kit dianteiro com batente e coifa (par). Aplicação: Renault Sandero/Logan (2008-2019).",
      69.90, 2, 12, "Corredor B - Prateleira 3", C_SUSP, F_SUL, 0.2, "coifa", papel="obsoleto"),

    # ---------------- Motor ----------------
    p("MO-COR-5101", "Correia dentada",
      "Correia dentada 137 dentes. Aplicação: VW Gol/Fox/Voyage 1.0 e 1.6 8V (2008-2016).",
      84.90, 3, 15, "Corredor C - Prateleira 1", C_MOTOR, F_OESTE, 5, "correia"),
    p("MO-KIT-5120", "Kit correia dentada com tensor",
      "Correia + tensor. Aplicação: Fiat Palio/Uno/Siena 1.0 e 1.4 Fire (2004-2016).",
      219.90, 2, 8, "Corredor C - Prateleira 1", C_MOTOR, F_OESTE, 2.5, "correia"),
    p("MO-COR-5133", "Correia do alternador (poly-V)",
      "Correia 6PK1035. Aplicação: Chevrolet Onix/Prisma 1.0 e 1.4 (2013-2019).",
      54.90, 3, 12, "Corredor C - Prateleira 1", C_MOTOR, F_OESTE, 4, "correia"),
    p("MO-JUN-5210", "Junta do cabeçote",
      "Junta do cabeçote metálica. Aplicação: VW motor AP 1.6/1.8/2.0 (1985-2013).",
      129.90, 2, 8, "Corredor C - Prateleira 2", C_MOTOR, F_PAULISTA, 1.5, "junta"),
    p("MO-JUN-5222", "Junta da tampa de válvulas",
      "Junta de borracha. Aplicação: Fiat motor Fire 1.0/1.4 8V (2001-2016).",
      39.90, 3, 15, "Corredor C - Prateleira 2", C_MOTOR, F_PAULISTA, 3, "junta"),
    p("MO-BOM-5305", "Bomba de óleo",
      "Bomba de óleo completa. Aplicação: Chevrolet motor 1.0/1.4 8V Família I (1994-2012).",
      289.90, 1, 5, "Corredor C - Prateleira 3", C_MOTOR, F_OESTE, 0, "bomba", papel="obsoleto"),

    # ---------------- Filtros e Lubrificantes ----------------
    p("FL-OLE-6001", "Óleo de motor 5W30 sintético (1 L)",
      "Óleo sintético API SP, frasco de 1 litro. Indicado para motores flex modernos.",
      44.90, 20, 120, "Corredor D - Prateleira 1", C_FILTROS, F_FILTROS, 60, "frasco", unidade=4),
    p("FL-OLE-6014", "Óleo de motor 15W40 mineral (1 L)",
      "Óleo mineral API SL, frasco de 1 litro. Indicado para motores mais antigos.",
      29.90, 16, 100, "Corredor D - Prateleira 1", C_FILTROS, F_FILTROS, 44, "frasco", unidade=4),
    p("FL-FOL-6101", "Filtro de óleo",
      "Filtro de óleo blindado. Aplicação: VW Gol/Fox/Voyage/Saveiro 1.0 e 1.6 (2008-2022).",
      29.90, 8, 40, "Corredor D - Prateleira 2", C_FILTROS, F_FILTROS, 16, "filtro"),
    p("FL-FOL-6114", "Filtro de óleo",
      "Filtro de óleo blindado. Aplicação: Fiat Uno/Palio/Siena/Strada Fire (2001-2016).",
      27.90, 8, 40, "Corredor D - Prateleira 2", C_FILTROS, F_FILTROS, 14, "filtro"),
    p("FL-FAR-6201", "Filtro de ar do motor",
      "Elemento filtrante de papel. Aplicação: Chevrolet Onix/Prisma/Cobalt (2012-2019).",
      42.90, 5, 25, "Corredor D - Prateleira 3", C_FILTROS, F_FILTROS, 8, "filtro_ar", papel="baixo"),
    p("FL-FCB-6305", "Filtro de ar-condicionado (cabine)",
      "Filtro de cabine com carvão ativado. Aplicação: Hyundai HB20 (2012-2019).",
      49.90, 4, 20, "Corredor D - Prateleira 3", C_FILTROS, F_FILTROS, 5, "filtro_ar"),
    p("FL-FCO-6402", "Filtro de combustível",
      "Filtro de combustível em linha, motores flex. Aplicação: Fiat, VW e Chevrolet (1996-2016).",
      24.90, 5, 25, "Corredor D - Prateleira 3", C_FILTROS, F_FILTROS, 6, "filtro"),
    p("FL-ADT-6510", "Aditivo para radiador concentrado 1 L",
      "Aditivo de arrefecimento orgânico, rosa, frasco de 1 litro. Diluir conforme o manual.",
      26.90, 6, 50, "Corredor D - Prateleira 4", C_FILTROS, F_FILTROS, 7, "frasco", papel="excesso"),

    # ---------------- Ignição ----------------
    p("IG-VEL-7001", "Vela de ignição (jogo com 4)",
      "Jogo com 4 velas de níquel. Aplicação: VW Gol/Fox/Voyage 1.0 e 1.6 8V flex (2008-2016).",
      99.90, 4, 20, "Corredor E - Prateleira 1", C_IGN, F_ELETRICA, 7, "vela"),
    p("IG-VEL-7015", "Vela de ignição (jogo com 4)",
      "Jogo com 4 velas de níquel. Aplicação: Fiat Uno/Palio Fire 1.0 e 1.4 flex (2004-2016).",
      89.90, 4, 20, "Corredor E - Prateleira 1", C_IGN, F_ELETRICA, 6, "vela", papel="ruptura"),
    p("IG-CAB-7102", "Cabo de vela",
      "Jogo de cabos de vela. Aplicação: VW Gol bola 1.0 16V (1997-1999).",
      129.90, 2, 8, "Corredor E - Prateleira 2", C_IGN, F_ELETRICA, 1.5, "cabo_vela"),
    p("IG-CAB-7103", "Cabo de vela",
      "Jogo de cabos de vela. Aplicação: VW Gol bola 1.6 e 1.8 AP (1995-1996).",
      119.90, 2, 8, "Corredor E - Prateleira 2", C_IGN, F_ELETRICA, 1.2, "cabo_vela", papel="baixo"),
    p("IG-BOB-7201", "Bobina de ignição",
      "Bobina de ignição plástica. Aplicação: Fiat motor Fire 1.0/1.4 flex (2004-2016).",
      189.90, 1, 6, "Corredor E - Prateleira 3", C_IGN, F_ELETRICA, 1.2, "bobina"),

    # ---------------- Elétrica e Iluminação ----------------
    p("EL-LAM-8001", "Lâmpada farol H4 12V 60/55W",
      "Lâmpada halógena H4 para farol, unidade. Uso geral.",
      24.90, 10, 50, "Corredor F - Prateleira 1", C_ELET, F_ELETRICA, 18, "lampada"),
    p("EL-LAM-8015", "Lâmpada farol H7 12V 55W",
      "Lâmpada halógena H7 para farol, unidade. Uso geral.",
      29.90, 8, 40, "Corredor F - Prateleira 1", C_ELET, F_ELETRICA, 10, "lampada", papel="baixo"),
    p("EL-LAM-8030", "Lâmpada pingo 12V 5W (caixa com 10)",
      "Lâmpada de lanterna e placa, esmagada T10, caixa com 10 unidades.",
      19.90, 6, 30, "Corredor F - Prateleira 1", C_ELET, F_ELETRICA, 6, "lampada"),
    p("EL-BAT-8101", "Bateria 60 Ah",
      "Bateria selada 60 Ah, 12V, polo positivo à direita. Veículos populares 1.0 a 1.6.",
      489.90, 2, 8, "Depósito - Estante 1", C_ELET, F_ELETRICA, 3, "bateria"),
    p("EL-REL-8210", "Relé auxiliar 12V 4 terminais",
      "Relé universal 40A para farol, buzina e ventoinha.",
      22.90, 4, 20, "Corredor F - Prateleira 2", C_ELET, F_ELETRICA, 3, "rele"),
    p("EL-LED-8305", "Lâmpada LED H4 6000K (par)",
      "Par de lâmpadas LED H4 com dissipador. Lançamento no catálogo da loja.",
      159.90, 2, 10, "Corredor F - Prateleira 1", C_ELET, F_ELETRICA, 3, "lampada", criado=175),

    # ---------------- Arrefecimento ----------------
    p("AR-BAG-9001", "Bomba d'água",
      "Bomba d'água com junta. Aplicação: VW Gol/Voyage/Fox 1.0 e 1.6 8V (2008-2016).",
      149.90, 2, 8, "Corredor G - Prateleira 1", C_ARREF, F_OESTE, 2.5, "bomba"),
    p("AR-VTE-9102", "Válvula termostática",
      "Válvula termostática com carcaça. Aplicação: Fiat motor Fire 1.0/1.4 (2004-2016).",
      99.90, 2, 8, "Corredor G - Prateleira 1", C_ARREF, F_OESTE, 1.5, "valvula"),
    p("AR-RAD-9210", "Radiador",
      "Radiador de alumínio com ar-condicionado. Aplicação: Chevrolet Onix 1.0/1.4 (2013-2019).",
      549.90, 1, 4, "Depósito - Estante 2", C_ARREF, F_OESTE, 0.6, "radiador"),
    p("AR-MAN-9305", "Mangueira superior do radiador",
      "Mangueira de borracha. Aplicação: VW Gol G5/G6 1.0 (2008-2016). Novo item de linha.",
      69.90, 2, 10, "Corredor G - Prateleira 2", C_ARREF, F_OESTE, 2, "mangueira", criado=190),

    # ---------------- Embreagem e Transmissão ----------------
    p("EM-KIT-9501", "Kit de embreagem",
      "Platô, disco e rolamento. Aplicação: VW Gol/Voyage/Fox 1.0 e 1.6 8V (2008-2016).",
      489.90, 1, 5, "Depósito - Estante 3", C_EMBR, F_OESTE, 1.5, "embreagem"),
    p("EM-KIT-9514", "Kit de embreagem",
      "Platô, disco e rolamento. Aplicação: Fiat Uno/Palio/Strada Fire 1.0 e 1.4 (2004-2016).",
      449.90, 1, 5, "Depósito - Estante 3", C_EMBR, F_OESTE, 1.2, "embreagem"),
    p("EM-CAB-9602", "Cabo de embreagem",
      "Cabo de embreagem com regulagem automática. Aplicação: Fiat Uno Mille (1994-2013).",
      59.90, 2, 10, "Corredor H - Prateleira 1", C_EMBR, F_OESTE, 2, "cabo"),
    p("EM-COF-9710", "Coifa da homocinética",
      "Coifa lado roda com abraçadeiras. Aplicação: VW Gol/Parati/Saveiro G2 a G4 (1995-2013).",
      34.90, 3, 15, "Corredor H - Prateleira 1", C_EMBR, F_OESTE, 3, "coifa"),
    p("EM-ROL-9801", "Rolamento de roda dianteira",
      "Rolamento com ABS. Aplicação: Chevrolet Celta/Prisma (2006-2014). Fornecedor descontinuado.",
      119.90, 2, 8, "Corredor H - Prateleira 2", C_EMBR, F_HORIZONTE, 1, "rolamento", papel="inativo"),
]
