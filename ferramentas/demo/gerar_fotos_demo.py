"""
Gera as "fotos" dos produtos de demonstração: ilustrações próprias (nenhuma
imagem de terceiros), uma da peça e outra da embalagem com etiqueta.

Saída: src/main/resources/demo/fotos/<sku>-1.png e <sku>-2.png

Uso (na raiz do projeto):
  python ferramentas/demo/gerar_fotos_demo.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from catalogo import CATEGORIAS, PRODUTOS

RAIZ = Path(__file__).resolve().parents[2]
SAIDA = RAIZ / "src/main/resources/demo/fotos"
W, H = 640, 480
FONTE = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONTE_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

ACO = (132, 140, 150)
ACO_ESCURO = (78, 86, 96)
ACO_CLARO = (196, 202, 210)
BORRACHA = (40, 42, 46)
COR_CATEGORIA = {1: (200, 60, 50), 2: (40, 110, 190), 3: (90, 90, 100), 4: (220, 150, 30),
                 5: (120, 70, 170), 6: (30, 150, 120), 7: (40, 150, 200), 8: (160, 100, 50)}


def fonte(tam, negrito=False):
    return ImageFont.truetype(FONTE_B if negrito else FONTE, tam)


def fundo():
    img = Image.new("RGB", (W, H), (236, 238, 241))
    d = ImageDraw.Draw(img)
    for y in range(H):  # leve degradê de "estúdio"
        c = int(246 - 22 * (y / H))
        d.line([(0, y), (W, y)], fill=(c, c + 1, c + 3))
    return img


def sombra(img, caixa):
    s = Image.new("L", (W, H), 0)
    ImageDraw.Draw(s).ellipse(caixa, fill=110)
    s = s.filter(ImageFilter.GaussianBlur(14))
    preto = Image.new("RGB", (W, H), (120, 124, 130))
    img.paste(preto, (0, 0), s)


def centro_cx():
    return W // 2, 215


# ---------------------------------------------------------------- desenhos
def pastilha(d, cor):
    for dx in (-80, 80):
        cx, cy = W // 2 + dx, 215
        d.rounded_rectangle([cx - 70, cy - 45, cx + 70, cy + 45], 26, fill=ACO_ESCURO)
        d.rounded_rectangle([cx - 62, cy - 30, cx + 62, cy + 40], 22, fill=(58, 52, 48))
        d.ellipse([cx - 55, cy - 44, cx - 43, cy - 32], fill=ACO_CLARO)
        d.ellipse([cx + 43, cy - 44, cx + 55, cy - 32], fill=ACO_CLARO)
        d.line([cx, cy - 30, cx, cy + 40], fill=(90, 82, 76), width=3)


def disco(d, cor):
    cx, cy = W // 2, 215
    d.ellipse([cx - 140, cy - 140, cx + 140, cy + 140], fill=ACO)
    d.ellipse([cx - 128, cy - 128, cx + 128, cy + 128], outline=ACO_CLARO, width=3)
    d.ellipse([cx - 62, cy - 62, cx + 62, cy + 62], fill=ACO_ESCURO)
    d.ellipse([cx - 26, cy - 26, cx + 26, cy + 26], fill=(236, 238, 241))
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        x, y = cx + 44 * math.cos(a), cy + 44 * math.sin(a)
        d.ellipse([x - 7, y - 7, x + 7, y + 7], fill=(236, 238, 241))
    for i in range(24):
        a = i * 2 * math.pi / 24
        x, y = cx + 100 * math.cos(a), cy + 100 * math.sin(a)
        d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=ACO_ESCURO)


def frasco(d, cor):
    cx, cy = W // 2, 215
    d.rounded_rectangle([cx - 70, cy - 95, cx + 70, cy + 130], 24, fill=cor)
    d.rounded_rectangle([cx - 28, cy - 140, cx + 28, cy - 90], 8, fill=BORRACHA)
    d.rounded_rectangle([cx - 55, cy - 40, cx + 55, cy + 80], 10, fill=(250, 250, 250))
    d.rectangle([cx - 55, cy - 40, cx + 55, cy - 18], fill=cor)
    for i, y in enumerate(range(cy - 5, cy + 70, 16)):
        d.line([cx - 40, y, cx + (40 if i % 2 else 15), y], fill=(170, 170, 175), width=4)


def amortecedor(d, cor):
    cx = W // 2
    d.rounded_rectangle([cx - 22, 60, cx + 22, 200], 8, fill=ACO_CLARO)
    d.rounded_rectangle([cx - 40, 190, cx + 40, 370], 12, fill=cor)
    d.ellipse([cx - 22, 40, cx + 22, 80], outline=ACO_ESCURO, width=8)
    d.ellipse([cx - 22, 355, cx + 22, 395], outline=ACO_ESCURO, width=8)
    d.rectangle([cx - 40, 240, cx + 40, 250], fill=(255, 255, 255))


def bandeja(d, cor):
    d.polygon([(170, 300), (470, 150), (500, 190), (240, 340)], fill=ACO_ESCURO)
    d.polygon([(170, 300), (250, 170), (290, 185), (230, 330)], fill=ACO)
    d.ellipse([440, 130, 520, 210], fill=BORRACHA)
    d.ellipse([462, 152, 498, 188], fill=ACO_CLARO)
    d.ellipse([150, 280, 210, 340], fill=BORRACHA)
    d.ellipse([230, 150, 290, 210], fill=BORRACHA)


def pivo(d, cor):
    cx, cy = W // 2, 215
    d.ellipse([cx - 90, cy - 20, cx + 30, cy + 100], fill=ACO_ESCURO)
    d.rectangle([cx - 10, cy - 20, cx + 120, cy + 20], fill=ACO)
    d.ellipse([cx - 60, cy - 110, cx + 0, cy - 50], fill=BORRACHA)
    d.polygon([(cx - 40, cy - 60), (cx - 20, cy - 60), (cx - 12, cy + 10), (cx - 48, cy + 10)], fill=ACO_CLARO)


def coifa(d, cor):
    cx = W // 2
    for i in range(7):
        r = 45 + i * 12
        y = 90 + i * 38
        d.ellipse([cx - r, y - 16, cx + r, y + 16], fill=BORRACHA, outline=(70, 72, 78), width=3)
    d.ellipse([cx - 40, 72, cx + 40, 104], outline=ACO_CLARO, width=5)


def correia(d, cor):
    cx, cy = W // 2, 215
    d.rounded_rectangle([cx - 200, cy - 110, cx + 200, cy + 110], 110, outline=BORRACHA, width=26)
    for i in range(40):
        x = cx - 170 + i * 8.5
        d.line([x, cy - 124, x, cy - 100], fill=(70, 72, 78), width=2)
    d.text((cx - 70, cy - 12), "137 DENTES", font=fonte(20, True), fill=(120, 120, 125))


def junta(d, cor):
    cx, cy = W // 2, 215
    d.rounded_rectangle([cx - 210, cy - 90, cx + 210, cy + 90], 18, fill=ACO_CLARO, outline=ACO, width=4)
    for i in range(4):
        x = cx - 150 + i * 100
        d.ellipse([x - 38, cy - 38, x + 38, cy + 38], fill=(236, 238, 241), outline=ACO_ESCURO, width=5)
    for x in (cx - 195, cx + 195):
        for y in (cy - 70, cy + 70):
            d.ellipse([x - 8, y - 8, x + 8, y + 8], fill=(236, 238, 241))


def bomba(d, cor):
    cx, cy = W // 2, 215
    d.ellipse([cx - 120, cy - 100, cx + 80, cy + 100], fill=ACO)
    d.rounded_rectangle([cx + 40, cy - 30, cx + 170, cy + 30], 12, fill=ACO_ESCURO)
    d.ellipse([cx - 60, cy - 40, cx + 20, cy + 40], fill=ACO_ESCURO)
    for i in range(6):
        a = i * math.pi / 3
        x, y = cx - 20 + 85 * math.cos(a), cy + 85 * math.sin(a)
        d.ellipse([x - 9, y - 9, x + 9, y + 9], fill=ACO_CLARO)


def filtro(d, cor):
    cx = W // 2
    d.rounded_rectangle([cx - 85, 90, cx + 85, 350], 30, fill=cor)
    d.ellipse([cx - 85, 70, cx + 85, 120], fill=ACO_CLARO)
    d.ellipse([cx - 30, 82, cx + 30, 108], fill=ACO_ESCURO)
    for y in range(150, 330, 30):
        d.line([cx - 85, y, cx + 85, y], fill=tuple(max(0, c - 40) for c in cor), width=4)
    d.rectangle([cx - 60, 190, cx + 60, 260], fill=(250, 250, 250))


def filtro_ar(d, cor):
    cx, cy = W // 2, 215
    d.rounded_rectangle([cx - 190, cy - 110, cx + 190, cy + 110], 14, fill=(60, 60, 64))
    d.rectangle([cx - 170, cy - 90, cx + 170, cy + 90], fill=(245, 236, 210))
    for x in range(cx - 165, cx + 170, 14):
        d.line([x, cy - 90, x + 7, cy + 90], fill=(215, 200, 160), width=4)


def vela(d, cor):
    for dx in (-150, -50, 50, 150):
        cx = W // 2 + dx
        d.rectangle([cx - 6, 70, cx + 6, 100], fill=ACO)
        d.rounded_rectangle([cx - 20, 100, cx + 20, 230], 16, fill=(245, 245, 245))
        d.line([cx - 20, 130, cx + 20, 130], fill=(210, 210, 210), width=3)
        d.polygon([(cx - 28, 230), (cx + 28, 230), (cx + 28, 280), (cx - 28, 280)], fill=ACO)
        d.rectangle([cx - 14, 280, cx + 14, 340], fill=ACO_ESCURO)
        d.line([cx, 340, cx, 358], fill=ACO_ESCURO, width=4)
        d.line([cx, 358, cx + 12, 358], fill=ACO_ESCURO, width=4)


def cabo_vela(d, cor):
    for i in range(4):
        y0 = 110 + i * 60
        d.line([(120, y0), (320, y0 + 30), (520, y0 - 10)], fill=cor, width=14, joint="curve")
        d.rounded_rectangle([90, y0 - 18, 140, y0 + 18], 8, fill=BORRACHA)
        d.rounded_rectangle([505, y0 - 30, 555, y0 + 10], 8, fill=BORRACHA)


def bobina(d, cor):
    cx, cy = W // 2, 215
    d.rounded_rectangle([cx - 150, cy - 70, cx + 150, cy + 30], 16, fill=BORRACHA)
    for i in range(4):
        x = cx - 110 + i * 73
        d.rectangle([x - 16, cy + 30, x + 16, cy + 130], fill=(55, 58, 64))
        d.ellipse([x - 16, cy + 120, x + 16, cy + 140], fill=(55, 58, 64))
    d.rounded_rectangle([cx - 40, cy - 110, cx + 40, cy - 70], 8, fill=cor)


def lampada(d, cor):
    cx, cy = W // 2, 190
    d.ellipse([cx - 80, cy - 110, cx + 80, cy + 60], fill=(250, 252, 255), outline=(200, 205, 215), width=4)
    d.line([cx - 25, cy - 10, cx - 10, cy - 50, cx + 10, cy - 10, cx + 25, cy - 50], fill=(240, 170, 60), width=4)
    d.rectangle([cx - 45, cy + 55, cx + 45, cy + 110], fill=ACO)
    for y in range(cy + 62, cy + 110, 12):
        d.line([cx - 45, y, cx + 45, y], fill=ACO_ESCURO, width=3)
    d.rectangle([cx - 30, cy + 110, cx + 30, cy + 160], fill=BORRACHA)
    d.rectangle([cx - 20, cy + 160, cx - 8, cy + 190], fill=ACO_CLARO)
    d.rectangle([cx + 8, cy + 160, cx + 20, cy + 190], fill=ACO_CLARO)


def bateria(d, cor):
    cx, cy = W // 2, 225
    d.rounded_rectangle([cx - 200, cy - 110, cx + 200, cy + 120], 14, fill=(35, 38, 44))
    d.rectangle([cx - 200, cy - 110, cx + 200, cy - 70], fill=(55, 58, 66))
    d.rectangle([cx - 160, cy - 145, cx - 110, cy - 110], fill=(200, 60, 50))
    d.rectangle([cx + 110, cy - 145, cx + 160, cy - 110], fill=(70, 70, 75))
    d.rectangle([cx - 150, cy - 30, cx + 150, cy + 60], fill=cor)
    d.text((cx - 70, cy - 8), "60 Ah", font=fonte(40, True), fill=(255, 255, 255))


def rele(d, cor):
    cx, cy = W // 2, 200
    d.rounded_rectangle([cx - 90, cy - 90, cx + 90, cy + 90], 12, fill=BORRACHA)
    for dx, dy in ((-40, 0), (40, 0), (0, -40), (0, 40)):
        d.rectangle([cx + dx - 10, cy + 90, cx + dx + 10, cy + 150 + dy // 4], fill=ACO_CLARO)
    d.text((cx - 38, cy - 16), "12V", font=fonte(34, True), fill=(230, 230, 230))


def valvula(d, cor):
    cx, cy = W // 2, 215
    d.ellipse([cx - 110, cy - 30, cx + 110, cy + 30], fill=ACO_CLARO)
    d.rectangle([cx - 40, cy - 130, cx + 40, cy - 20], fill=(210, 170, 70))
    for y in range(cy - 120, cy - 25, 14):
        d.line([cx - 40, y, cx + 40, y], fill=(170, 130, 40), width=4)
    d.rectangle([cx - 14, cy + 20, cx + 14, cy + 120], fill=ACO)


def radiador(d, cor):
    d.rectangle([110, 90, 530, 340], fill=(60, 62, 68))
    d.rectangle([130, 110, 510, 320], fill=ACO_CLARO)
    for x in range(135, 510, 9):
        d.line([x, 110, x, 320], fill=ACO, width=3)
    d.rounded_rectangle([90, 70, 150, 360], 10, fill=BORRACHA)
    d.rounded_rectangle([490, 70, 550, 360], 10, fill=BORRACHA)


def mangueira(d, cor):
    d.line([(150, 120), (260, 150), (330, 250), (480, 300)], fill=BORRACHA, width=46, joint="curve")
    for x, y in ((150, 120), (480, 300)):
        d.ellipse([x - 32, y - 32, x + 32, y + 32], outline=ACO_CLARO, width=8)


def embreagem(d, cor):
    cx, cy = W // 2 - 60, 215
    d.ellipse([cx - 140, cy - 140, cx + 140, cy + 140], fill=ACO)
    d.ellipse([cx - 60, cy - 60, cx + 60, cy + 60], fill=ACO_ESCURO)
    for i in range(12):
        a = i * math.pi / 6
        d.line([cx + 65 * math.cos(a), cy + 65 * math.sin(a), cx + 125 * math.cos(a), cy + 125 * math.sin(a)],
               fill=ACO_CLARO, width=10)
    d.ellipse([cx + 150, cy + 20, cx + 250, cy + 120], fill=(45, 48, 54))
    d.ellipse([cx + 180, cy + 50, cx + 220, cy + 90], fill=ACO_CLARO)


def cabo(d, cor):
    d.line([(90, 330), (220, 150), (420, 250), (560, 110)], fill=BORRACHA, width=18, joint="curve")
    d.rounded_rectangle([60, 310, 130, 360], 10, fill=ACO)
    d.rounded_rectangle([530, 80, 590, 140], 10, fill=ACO)


def rolamento(d, cor):
    cx, cy = W // 2, 215
    d.ellipse([cx - 140, cy - 140, cx + 140, cy + 140], fill=ACO)
    d.ellipse([cx - 110, cy - 110, cx + 110, cy + 110], fill=ACO_ESCURO)
    for i in range(12):
        a = i * math.pi / 6
        x, y = cx + 88 * math.cos(a), cy + 88 * math.sin(a)
        d.ellipse([x - 16, y - 16, x + 16, y + 16], fill=ACO_CLARO)
    d.ellipse([cx - 68, cy - 68, cx + 68, cy + 68], fill=ACO)
    d.ellipse([cx - 48, cy - 48, cx + 48, cy + 48], fill=(236, 238, 241))


DESENHOS = dict(pastilha=pastilha, disco=disco, frasco=frasco, amortecedor=amortecedor, bandeja=bandeja,
                pivo=pivo, coifa=coifa, correia=correia, junta=junta, bomba=bomba, filtro=filtro,
                filtro_ar=filtro_ar, vela=vela, cabo_vela=cabo_vela, bobina=bobina, lampada=lampada,
                bateria=bateria, rele=rele, valvula=valvula, radiador=radiador, mangueira=mangueira,
                embreagem=embreagem, cabo=cabo, rolamento=rolamento)


def quebrar(texto, fonte_, largura, d):
    linhas, atual = [], ""
    for palavra in texto.split():
        teste = (atual + " " + palavra).strip()
        if d.textlength(teste, font=fonte_) <= largura:
            atual = teste
        else:
            linhas.append(atual)
            atual = palavra
    linhas.append(atual)
    return linhas


def foto_peca(p):
    img = fundo()
    sombra(img, (150, 360, 490, 420))
    d = ImageDraw.Draw(img)
    DESENHOS[p["desenho"]](d, COR_CATEGORIA[p["categoria"]])
    d.rectangle([0, H - 62, W, H], fill=(255, 255, 255))
    d.rectangle([0, H - 62, 8, H], fill=COR_CATEGORIA[p["categoria"]])
    d.text((24, H - 54), p["nome"], font=fonte(20, True), fill=(30, 32, 36))
    d.text((24, H - 28), f"SKU {p['sku']}  ·  {CATEGORIAS[p['categoria'] - 1][0]}", font=fonte(15),
           fill=(95, 100, 110))
    return img


def foto_embalagem(p):
    img = fundo()
    sombra(img, (120, 370, 520, 425))
    d = ImageDraw.Draw(img)
    cor = COR_CATEGORIA[p["categoria"]]
    # caixa em perspectiva
    d.polygon([(130, 130), (450, 130), (510, 90), (190, 90)], fill=(214, 190, 150))
    d.polygon([(450, 130), (510, 90), (510, 350), (450, 390)], fill=(186, 160, 118))
    d.rectangle([130, 130, 450, 390], fill=(226, 204, 166))
    # etiqueta
    d.rectangle([150, 160, 430, 360], fill=(255, 255, 255), outline=(200, 200, 200))
    d.rectangle([150, 160, 430, 194], fill=cor)
    d.text((162, 166), "PROSTOCK AUTOPEÇAS", font=fonte(17, True), fill=(255, 255, 255))
    y = 204
    for linha in quebrar(p["nome"], fonte(17, True), 256, d)[:2]:
        d.text((162, y), linha, font=fonte(17, True), fill=(30, 32, 36))
        y += 22
    for linha in quebrar(p["descricao"], fonte(12), 256, d)[:4]:
        d.text((162, y + 2), linha, font=fonte(12), fill=(80, 84, 92))
        y += 16
    # código de barras ilustrativo a partir do SKU
    x = 162
    for i, ch in enumerate((p["sku"] * 3)[:34]):
        if x > 280:
            break
        largura = 1 + (ord(ch) + i) % 3
        d.rectangle([x, 318, x + largura, 348], fill=(20, 20, 20))
        x += largura + 2 + (ord(ch) % 2)
    d.text((296, 326), p["sku"], font=fonte(16, True), fill=(30, 32, 36))
    d.text((24, H - 36), f"{p['nome']} — embalagem  ·  {p['local']}", font=fonte(15), fill=(95, 100, 110))
    return img


def main():
    SAIDA.mkdir(parents=True, exist_ok=True)
    for f in SAIDA.glob("*.png"):
        f.unlink()
    total = 0
    for p in PRODUTOS:
        geradores = [foto_peca, foto_embalagem][:p["fotos"]]
        for i, gerar in enumerate(geradores, start=1):
            gerar(p).save(SAIDA / f"{p['sku'].lower()}-{i}.png", optimize=True)
            total += 1
    print(f"{total} imagens em {SAIDA}")


if __name__ == "__main__":
    main()
