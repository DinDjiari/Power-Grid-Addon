"""
Gemini-Textur-Connector (MCP-Server) für Minecraft-Texturen.

Lässt Claude (Claude Code oder claude.ai) Texturen mit Google Gemini („Nano Banana“) erzeugen
und sofort auf Minecraft-Auflösung (16×16, 32×32, …) herunterrechnen, Farben reduzieren,
Transparenz freistellen und als PNG speichern. Claude bekommt jedes Ergebnis vergrößert als Bild zurück
und kann es ansehen und nachbessern lassen.

Start:
  stdio (Claude Code):   python server.py
  HTTP  (claude.ai):     python server.py --http --port 8765        (URL: http://host:8765/<TOKEN>/mcp)

Umgebung:
  GEMINI_API_KEY        Pflicht – Schlüssel aus https://aistudio.google.com/apikey
  GEMINI_IMAGE_MODEL    optional, Standard gemini-nano-banana-2.1
  TEXTURE_ROOT          optional – nur unterhalb dieses Ordners wird gelesen/geschrieben (Standard: aktueller Ordner)
  MCP_PATH_TOKEN        nur HTTP – geheimer Pfadteil, damit nicht jeder den Server nutzen kann

Lizenz-Hinweis: Fremde Texturen (z. B. Create, Power Grid) nur als Stil-Referenz verwenden, nicht nachbauen lassen
und die Ergebnisse vor dem Einchecken ansehen.
"""
from __future__ import annotations

import argparse
import base64
import io
import os
import sys
from pathlib import Path

from PIL import Image as PILImage
from mcp.server.fastmcp import FastMCP, Image

DEFAULT_MODEL = os.environ.get("GEMINI_IMAGE_MODEL", "gemini-nano-banana-2.1")
ROOT = Path(os.environ.get("TEXTURE_ROOT", os.getcwd())).resolve()
KEY_COLOR = (255, 0, 255)  # Magenta-Hintergrund für Freistellung

STYLE = (
    "Minecraft block texture, pixel art, viewed straight on, flat orthographic, no perspective, no shadows cast "
    "outside the texture, no text, no watermark, no border frame unless asked, crisp hard pixels, limited palette, "
    "readable at {w}x{h} pixels, in the style of the Create mod for Minecraft (industrial, andesite/brass/copper). "
)

mcp = FastMCP("gemini-textures")


# ---------------------------------------------------------------- helpers

def _path(p: str) -> Path:
    path = (ROOT / p).resolve() if not os.path.isabs(p) else Path(p).resolve()
    if ROOT != path and ROOT not in path.parents:
        raise ValueError(f"Pfad außerhalb von TEXTURE_ROOT ({ROOT}): {path}")
    return path


def _client():
    key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
    if not key:
        raise RuntimeError("GEMINI_API_KEY fehlt (https://aistudio.google.com/apikey)")
    from google import genai
    return genai.Client(api_key=key)


def _png_b64(img: PILImage.Image) -> str:
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return base64.b64encode(buf.getvalue()).decode()


def _gemini_image(prompt: str, refs: list[PILImage.Image], model: str) -> PILImage.Image:
    """Ein Bild von Gemini holen. Erst Interactions-API, sonst klassisches generate_content."""
    client = _client()
    errors = []
    if hasattr(client, "interactions"):
        try:
            inp = [{"type": "text", "text": prompt}] + [
                {"type": "image", "data": _png_b64(r), "mime_type": "image/png"} for r in refs]
            it = client.interactions.create(model=model, input=inp if refs else prompt,
                                            response_format={"type": "image", "mime_type": "image/jpeg",
                                                             "aspect_ratio": "1:1", "image_size": "1K"})
            data = it.output_image.data
            return PILImage.open(io.BytesIO(base64.b64decode(data))).convert("RGBA")
        except Exception as e:  # noqa: BLE001 – Fallback versuchen
            errors.append(f"interactions: {e}")
    try:
        from google.genai import types
        contents = [prompt] + [types.Part.from_bytes(data=base64.b64decode(_png_b64(r)), mime_type="image/png")
                               for r in refs]
        resp = client.models.generate_content(
            model=model, contents=contents,
            config=types.GenerateContentConfig(response_modalities=["IMAGE"],
                                               image_config=types.ImageConfig(aspect_ratio="1:1")))
        for part in resp.candidates[0].content.parts:
            if getattr(part, "inline_data", None) and part.inline_data.data:
                return PILImage.open(io.BytesIO(part.inline_data.data)).convert("RGBA")
        errors.append("generate_content: keine Bilddaten in der Antwort")
    except Exception as e:  # noqa: BLE001
        errors.append(f"generate_content: {e}")
    raise RuntimeError("Gemini lieferte kein Bild – " + " | ".join(errors))


def _pixelize(img: PILImage.Image, w: int, h: int, colors: int, transparent: bool) -> PILImage.Image:
    img = img.convert("RGBA")
    if transparent:
        # Magenta-Hintergrund (auch leicht abweichend) freistellen, bevor gemittelt wird.
        px = img.load()
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = px[x, y]
                if r > 180 and b > 180 and g < 90:
                    px[x, y] = (0, 0, 0, 0)
    # Fläche je Zielpixel mitteln (BOX), danach harte Kanten.
    small = img.resize((w, h), PILImage.BOX)
    if colors and colors > 0:
        alpha = small.getchannel("A")
        rgb = small.convert("RGB").quantize(colors=colors, method=PILImage.Quantize.MEDIANCUT,
                                            dither=PILImage.Dither.NONE).convert("RGB")
        small = rgb.convert("RGBA")
        small.putalpha(alpha)
    a = small.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
    small.putalpha(a)
    return small


def _preview(img: PILImage.Image, scale: int | None = None) -> Image:
    scale = scale or max(1, 256 // max(img.width, img.height))
    big = img.resize((img.width * scale, img.height * scale), PILImage.NEAREST)
    bg = PILImage.new("RGBA", big.size, (60, 60, 60, 255))
    for y in range(0, big.height, scale):  # Schachbrett für Transparenz
        for x in range(0, big.width, scale):
            if ((x // scale) + (y // scale)) % 2:
                bg.paste((90, 90, 90, 255), (x, y, x + scale, y + scale))
    bg.alpha_composite(big)
    buf = io.BytesIO()
    bg.convert("RGB").save(buf, "PNG")
    return Image(data=buf.getvalue(), format="png")


def _load(p: str) -> PILImage.Image:
    img = PILImage.open(_path(p)).convert("RGBA")
    if max(img.size) < 256:  # kleine Texturen für Gemini groß und scharf machen
        s = max(1, 512 // max(img.size))
        img = img.resize((img.width * s, img.height * s), PILImage.NEAREST)
    return img


def _raw_path(out_path: str) -> str:
    """Große Rohbilder nicht neben die Texturen (sonst landen sie im Jar), sondern nach .gemini_raw/."""
    return str(Path(".gemini_raw") / Path(out_path).with_suffix(".raw.png").name)


def _save(img: PILImage.Image, out_path: str) -> Path:
    path = _path(out_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "PNG")
    return path


# ---------------------------------------------------------------- tools

@mcp.tool()
def generate_texture(prompt: str, out_path: str, width: int = 16, height: int = 16, colors: int = 0,
                     transparent: bool = False, tileable: bool = False, reference_paths: list[str] | None = None,
                     model: str | None = None) -> list:
    """Neue Minecraft-Textur mit Gemini erzeugen und auf width×height Pixel herunterrechnen.

    prompt: Beschreibung (Englisch liefert meist bessere Ergebnisse).
    out_path: Ziel-PNG (relativ zu TEXTURE_ROOT). Das große Rohbild landet in .gemini_raw/.
    colors: Farbanzahl begrenzen (0 = frei, typisch 8–24).
    transparent: Hintergrund wird freigestellt (für Gitter, Items, Cutout-Texturen).
    tileable: nahtlos kachelbar (für Blockseiten).
    reference_paths: Bilder als Stil-/Formvorlage (z. B. eigene vorhandene Texturen).
    Rückgabe: Text + vergrößerte Vorschau.
    """
    full = STYLE.format(w=width, h=height) + prompt
    if tileable:
        full += " The texture must tile seamlessly on all four edges."
    if transparent:
        full += (" Put the object on a perfectly flat pure magenta (#FF00FF) background, "
                 "no magenta anywhere on the object itself.")
    full += f" Design it on a {width}x{height} pixel grid, each grid cell one solid colour."
    refs = [_load(p) for p in (reference_paths or [])]
    raw = _gemini_image(full, refs, model or DEFAULT_MODEL)
    raw_path = _save(raw, _raw_path(out_path))
    tex = _pixelize(raw, width, height, colors, transparent)
    path = _save(tex, out_path)
    return [f"Gespeichert: {path} ({width}×{height}), Rohbild: {raw_path}", _preview(tex)]


@mcp.tool()
def edit_texture(input_path: str, instruction: str, out_path: str, width: int | None = None,
                 height: int | None = None, colors: int = 0, transparent: bool = False,
                 model: str | None = None) -> list:
    """Vorhandene Textur mit Gemini überarbeiten (z. B. „mehr Rost“, „Messing statt Andesit“, „Kanten betonen“).

    Größe bleibt gleich, wenn width/height fehlen. Rückgabe: Text + Vorschau.
    """
    src = PILImage.open(_path(input_path))
    w, h = width or src.width, height or src.height
    full = (STYLE.format(w=w, h=h) + "Edit the given texture: " + instruction +
            " Keep the layout and pixel grid of the original unless told otherwise.")
    if transparent:
        full += " Keep transparent areas as a perfectly flat pure magenta (#FF00FF) background."
    raw = _gemini_image(full, [_load(input_path)], model or DEFAULT_MODEL)
    _save(raw, _raw_path(out_path))
    tex = _pixelize(raw, w, h, colors, transparent)
    path = _save(tex, out_path)
    return [f"Gespeichert: {path} ({w}×{h})", _preview(tex)]


@mcp.tool()
def pixelize(input_path: str, out_path: str, width: int = 16, height: int = 16, colors: int = 0,
             transparent: bool = False) -> list:
    """Beliebiges Bild (z. B. ein *.raw.png) lokal ohne Gemini auf Minecraft-Auflösung bringen."""
    tex = _pixelize(PILImage.open(_path(input_path)), width, height, colors, transparent)
    path = _save(tex, out_path)
    return [f"Gespeichert: {path} ({width}×{height})", _preview(tex)]


@mcp.tool()
def show_textures(paths: list[str]) -> list:
    """Texturen vergrößert nebeneinander ansehen (Vergleich, Kontrolle vor dem Commit)."""
    imgs = [PILImage.open(_path(p)).convert("RGBA") for p in paths]
    scale = max(1, 128 // max(max(i.size) for i in imgs))
    sheet = PILImage.new("RGBA", (sum(i.width * scale + 8 for i in imgs), max(i.height for i in imgs) * scale),
                         (40, 40, 40, 255))
    x = 0
    for i in imgs:
        sheet.alpha_composite(i.resize((i.width * scale, i.height * scale), PILImage.NEAREST), (x, 0))
        x += i.width * scale + 8
    buf = io.BytesIO()
    sheet.convert("RGB").save(buf, "PNG")
    return [", ".join(paths), Image(data=buf.getvalue(), format="png")]


# ---------------------------------------------------------------- main

def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--http", action="store_true", help="Streamable HTTP statt stdio")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8765)
    a = ap.parse_args()
    if not a.http:
        mcp.run()
        return
    token = os.environ.get("MCP_PATH_TOKEN")
    if not token:
        sys.exit("MCP_PATH_TOKEN setzen (langer Zufallswert), sonst kann jeder deinen Gemini-Schlüssel benutzen.")
    mcp.settings.host, mcp.settings.port = a.host, a.port
    mcp.settings.streamable_http_path = f"/{token}/mcp"
    mcp.run(transport="streamable-http")


if __name__ == "__main__":
    main()
