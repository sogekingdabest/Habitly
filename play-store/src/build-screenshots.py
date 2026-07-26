"""Genera las capturas de la ficha de Play a partir de las capturas crudas del dispositivo.

Play exige relación 16:9 o 9:16 exacta, y ninguna captura de un móvil o una tablet reales
la cumple (el S25+ es 1080x2340, la tablet simulada 1600x2560). En vez de recortar la app
—que se comería la barra de navegación o la cabecera— cada captura se monta sobre un lienzo
9:16 con el mismo fondo de marca que feature-graphic-1024x500.html y un titular encima. Así
la proporción sale exacta por construcción y de paso la ficha gana el texto de venta.

Las crudas se toman con `adb exec-out screencap`; las de tablet salen del propio móvil con
`wm size 1600x2560` + `wm density 320` (la app no está adaptada a pantallas grandes, así que
en vertical es donde mejor se ve). Se recortan la barra de estado y la de navegación del
sistema: llevan hora, batería e iconos de notificación que no pintan nada en una ficha.

El render lo hace Edge headless, igual que el gráfico destacado, para poder usar Baloo 2 y
Nunito (las fuentes de la app) sin tener los .ttf en el repo.

Uso:  python play-store/src/build-screenshots.py
"""

import base64
import io
import subprocess
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RAW = Path(__file__).parent / "screenshots-raw"
OUT = ROOT / "play-store" / "screenshots"

EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")

# Píxeles a recortar de las capturas crudas: barra de estado arriba, barra de navegación
# del sistema abajo. La barra inferior DE LA APP se conserva entera.
CROP = {
    (1080, 2340): (94, 105),   # Galaxy S25+
    (1600, 2560): (120, 100),  # tablet simulada
}

# Orden de la ficha: lo primero que se ve en el listado son las dos primeras.
SHOTS = [
    ("inicio", "Toda la casa, en orden", "Lo de hoy, de un vistazo"),
    ("compra", "La compra, siempre al día", "Todos veis la misma lista al instante"),
    ("ia", "Un asistente que no sale del móvil", "El modelo corre en tu teléfono, incluso sin conexión"),
    ("rutinas", "Reparto justo de las tareas", "Quién ha hecho qué esta semana"),
    ("casa", "Invita a los tuyos", "Un código y ya estáis dentro"),
    ("plantillas", "Listo en 30 segundos", "Empieza con las rutinas típicas de una casa"),
]

# Cada formato de Play: carpeta, lienzo y de qué juego de crudas tira.
FORMATS = [
    ("phone", 1080, 1920, "phone"),
    ("tablet-7", 1080, 1920, "tablet"),
    ("tablet-10", 1440, 2560, "tablet"),
]

TEMPLATE = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Baloo+2:wght@600;700&family=Nunito:wght@400;600&display=swap" rel="stylesheet">
<style>
  html,body{{margin:0;padding:0;width:{w}px;height:{h}px;overflow:hidden}}
  /* Fondo salvia de marca (el del icono). La app es clara, así que sobre el mesh claro
     del gráfico destacado la captura no se despegaba del fondo: aquí sí contrasta. */
  .canvas{{
    position:relative;width:{w}px;height:{h}px;background:#5F8F82;
    background-image:
      radial-gradient({w}px {w}px at 15% 8%, #74A294 0%, rgba(116,162,148,0) 60%),
      radial-gradient({w}px {w}px at 85% 72%, #4E7D70 0%, rgba(78,125,112,0) 58%),
      radial-gradient({w0}px {w0}px at 82% 2%, #8FB0A0 0%, rgba(143,176,160,0) 55%);
    overflow:hidden;
  }}
  .copy{{padding:{pad_top}px {pad_x}px 0;text-align:center}}
  h1{{
    margin:0;font-family:"Baloo 2","Segoe UI Variable Display","Trebuchet MS",sans-serif;
    font-weight:700;font-size:{h1}px;line-height:1.05;color:#F9FBF6;letter-spacing:-.5px;
  }}
  p{{
    margin:{gap}px auto 0;font-family:"Nunito","Segoe UI",sans-serif;font-weight:600;
    font-size:{p}px;line-height:1.3;color:#D9E8E1;max-width:{copy_w}px;
  }}
  /* La captura entra entera: cortarla por abajo se comía la barra de navegación de la
     app justo por la mitad y parecía un error de montaje, no una sangría buscada. */
  .shot{{
    position:absolute;left:50%;transform:translateX(-50%);top:{shot_top}px;
    width:{shot_w}px;border-radius:{radius}px;overflow:hidden;
    box-shadow:0 {sh1}px {sh2}px rgba(31,54,44,.38);
  }}
  .shot img{{display:block;width:100%}}
</style>
</head>
<body>
<div class="canvas">
  <div class="copy">
    <h1>{title}</h1>
    <p>{subtitle}</p>
  </div>
  <div class="shot"><img src="data:image/png;base64,{b64}"></div>
</div>
</body>
</html>
"""


def cropped_b64(path: Path) -> tuple[str, float]:
    """Devuelve la captura sin barras del sistema en base64, y su relación alto/ancho."""
    img = Image.open(path)
    top, bottom = CROP[img.size]
    img = img.crop((0, top, img.width, img.height - bottom))
    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    return base64.b64encode(buf.getvalue()).decode("ascii"), img.height / img.width


def render(html: str, w: int, h: int, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "page.html"
        page.write_text(html, encoding="utf-8")
        subprocess.run(
            [
                str(EDGE),
                "--headless=new",
                "--disable-gpu",
                "--hide-scrollbars",
                f"--user-data-dir={tmp}\\profile",
                # Sin presupuesto de tiempo virtual la captura sale con la fuente de
                # respaldo: Edge dispara el screenshot antes de que llegue Google Fonts.
                "--virtual-time-budget=6000",
                f"--window-size={w},{h}",
                f"--screenshot={dest}",
                page.as_uri(),
            ],
            check=True,
            capture_output=True,
        )


def main() -> None:
    for folder, w, h, source in FORMATS:
        print(f"\n{folder}  ({w}x{h})")
        for i, (key, title, subtitle) in enumerate(SHOTS, start=1):
            raw = RAW / f"{source}-{key}.png"
            if not raw.exists():  # las tablets no llevan la pantalla de plantillas
                continue
            b64, ratio = cropped_b64(raw)

            k = w / 1080.0  # todas las medidas se derivan del ancho del lienzo
            # La captura ocupa todo el hueco que dejan el titular y el margen inferior,
            # limitada por el ancho para que no toque los bordes. Las de tablet son mucho
            # menos altas, así que sin el tope de ancho saldrían pegadas a los lados.
            header = round(h * 0.165)
            avail = h - header - round(h * 0.045)
            shot_w = min(round(w * 0.84), round(avail / ratio))
            shot_top = header + round((avail - shot_w * ratio) / 2)

            html = TEMPLATE.format(
                w=w, h=h, w0=round(w * 0.7),
                pad_top=round(72 * k), pad_x=round(70 * k), copy_w=round(880 * k),
                h1=round(66 * k), p=round(34 * k), gap=round(16 * k),
                shot_top=shot_top, shot_w=shot_w,
                radius=round(34 * k), sh1=round(22 * k), sh2=round(60 * k),
                title=title, subtitle=subtitle, b64=b64,
            )
            dest = OUT / folder / f"{i:02d}-{key}.png"
            render(html, w, h, dest)
            print(f"  {dest.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
