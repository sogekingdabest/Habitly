"""Genera todos los assets del icono de Habitly a partir de mark-source.png.

El original es un render (bandeja blanca, sombra suave, destello decorativo). Aquí se
extrae solo el dibujo a una máscara de transparencia y se recolorea.

La medida no es el brillo sino el croma verde, g - (r+b)/2: la sombra de la bandeja y
el degradado del fondo son neutros, así que su croma es ~0 y desaparecen solos. Sobre
esa medida el render es de dos tonos planos con antialiasing, o sea que cada píxel es
una mezcla lineal `px = a*MARCA + (1-a)*BANDEJA`, y despejar `a` da el alfa exacto
conservando los bordes suavizados en vez de dentarlos como haría un umbral.

(Primer intento fallido, por si vuelve a hacer falta: proyectar el color RGB sobre la
recta bandeja->marca. Falla porque las esquinas de cualquier recorte rectangular caen
fuera de la bandeja redondeada y la sombra gris se cuela como si fuera dibujo.)

Uso:  python play-store/src/build-mark.py
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(__file__).parent / "mark-source.png"

CREAM = (0xF9, 0xFB, 0xF6)  # dibujo sobre el salvia de marca
SAGE = (0x5F, 0x8F, 0x82)   # fondo de marca

# Alfa a partir del cual un píxel cuenta como dibujo para calcular el recorte. 0.25 es
# estable (a 0.50 la caja solo cambia 2px) y deja fuera el destello decorativo.
INK = 0.25
MARGIN = 6  # px de aire alrededor de la caja, para no comerse el borde suavizado

# Alto de la marca dentro del lienzo de 108dp del adaptive icon. La zona segura son
# 66dp; 48 deja aire como el render original sin que se vea perdida a 48px.
MARK_DP = 48.0
CANVAS_DP = 108.0

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def extract_alpha(img: Image.Image) -> Image.Image:
    """Devuelve la marca como imagen L (alfa) recortada a su caja."""
    rgb = np.asarray(img.convert("RGB"), dtype=np.float64)
    chroma = rgb[:, :, 1] - (rgb[:, :, 0] + rgb[:, :, 2]) / 2

    # El dibujo ocupa ~4% del lienzo, así que el grueso del histograma es bandeja y
    # la cola alta es marca. Los percentiles evitan fijar colores a mano.
    plate = np.median(chroma[chroma < np.percentile(chroma, 60)])
    mark = np.median(chroma[chroma > np.percentile(chroma, 97)])

    a = (chroma - plate) / (mark - plate)
    # Remapeo 0.20..0.92 -> 0..1. El verde del render no es plano del todo, así que
    # sin esto el relleno queda moteado (18% del dibujo entre 0.6 y 1.0) y un halo
    # tenue de ~37k píxeles lo deja lavado. La banda que queda sigue siendo ancha,
    # así que el borde suavizado se conserva.
    a = np.clip((a - 0.20) / 0.72, 0.0, 1.0)

    ys, xs = np.nonzero(a > INK)
    y0, y1 = max(0, ys.min() - MARGIN), min(a.shape[0], ys.max() + 1 + MARGIN)
    x0, x1 = max(0, xs.min() - MARGIN), min(a.shape[1], xs.max() + 1 + MARGIN)

    alpha = Image.fromarray((a[y0:y1, x0:x1] * 255).round().astype(np.uint8), mode="L")

    # El render tiene vetas dentro del trazo que a 512px se ven. En vez de difuminar
    # (una mediana no llega: las vetas son más anchas que su radio), se aplana el
    # interior a opaco y se conserva el alfa suave solo en la franja del borde, que
    # es donde vive el antialiasing. MinFilter = erosión; el trazo más fino son ~55px
    # a esta resolución, así que quitarle 4px por lado no se come ningún detalle.
    interior = alpha.point(lambda v: 255 if v > 128 else 0).filter(ImageFilter.MinFilter(9))
    return ImageChops.lighter(alpha, interior)


def tinted(alpha: Image.Image, size: tuple[int, int], color) -> Image.Image:
    """La máscara escalada a `size` y teñida de `color`, con fondo transparente."""
    m = alpha.resize(size, Image.LANCZOS)
    out = Image.new("RGBA", size, color + (0,))
    out.putalpha(m)
    return out


def write(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)
    print(f"  {path.relative_to(ROOT)}  {img.size[0]}x{img.size[1]}")


def main() -> None:
    alpha = extract_alpha(Image.open(SRC))
    aspect = alpha.size[0] / alpha.size[1]
    print(f"marca extraída: {alpha.size[0]}x{alpha.size[1]} (relación {aspect:.3f})")

    print("\nforeground del launcher (lienzo 108dp, marca de 48dp centrada):")
    for name, k in DENSITIES.items():
        canvas_px = round(CANVAS_DP * k)
        mh = round(MARK_DP * k)
        mw = round(mh * aspect)
        canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        canvas.alpha_composite(tinted(alpha, (mw, mh), CREAM),
                               ((canvas_px - mw) // 2, (canvas_px - mh) // 2))
        write(canvas, ROOT / "app/src/main/res" / f"mipmap-{name}" / "ic_launcher_foreground.png")

    print("\nmarca suelta y tintable (login):")
    for name, k in DENSITIES.items():
        mh = round(96 * k)
        write(tinted(alpha, (round(mh * aspect), mh), (255, 255, 255)),
              ROOT / "app/src/main/res" / f"drawable-{name}" / "ic_habitly_mark.png")

    print("\nassets de Play:")
    # El icono de Play reproduce el recorte visible del launcher: 72 de los 108dp.
    k512 = 512 / 72.0
    mh = round(MARK_DP * k512)
    icon = Image.new("RGBA", (512, 512), SAGE + (255,))
    icon.alpha_composite(tinted(alpha, (round(mh * aspect), mh), CREAM),
                         ((512 - round(mh * aspect)) // 2, (512 - mh) // 2))
    write(icon.convert("RGB"), ROOT / "play-store/icon-512.png")
    # La usa feature-graphic-1024x500.html vía <img>.
    write(tinted(alpha, (round(600 * aspect), 600), CREAM),
          Path(__file__).parent / "mark-cream.png")


if __name__ == "__main__":
    main()
