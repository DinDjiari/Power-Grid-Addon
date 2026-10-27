# Gemini-Textur-Connector (MCP)

Kleiner MCP-Server, mit dem Claude Texturen über Google Gemini („Nano Banana“) erzeugt und gleich
auf Minecraft-Auflösung bringt. Werkzeuge:

| Werkzeug | Zweck |
|---|---|
| `generate_texture` | neue Textur aus Text (+ optional Referenzbilder) → 16×16/32×32 PNG, Farben begrenzen, Hintergrund freistellen, kachelbar |
| `edit_texture` | vorhandene Textur überarbeiten lassen („Messing statt Andesit“, „mehr Abnutzung“ …) |
| `pixelize` | beliebiges Bild lokal (ohne Gemini) auf MC-Auflösung bringen |
| `show_textures` | mehrere Texturen vergrößert nebeneinander zeigen |

Claude bekommt jedes Ergebnis vergrößert als Bild zurück, kann es beurteilen und nachbessern lassen.
Rohbilder landen in `.gemini_raw/` (git-ignoriert, kommen nicht ins Jar).

**Gemini-API-Schlüssel:** https://aistudio.google.com/apikey (Bildmodelle sind kostenpflichtig, wenige Cent pro Bild).
Modell per `GEMINI_IMAGE_MODEL` umstellbar, Standard `gemini-nano-banana-2.1`
(Alternativen: `gemini-3.1-flash-image`, `gemini-3-pro-image`, `gemini-2.5-flash-image`).

## Variante A – Claude Code am Windows-PC (empfohlen)

Lokal (PowerShell, im Repo-Ordner):
```powershell
cd tools\gemini-texture-mcp
py -3 -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
cd ..\..
claude mcp add gemini-textures --scope user `
  -e GEMINI_API_KEY=DEIN_SCHLUESSEL `
  -e TEXTURE_ROOT="$PWD" `
  -- "$PWD\tools\gemini-texture-mcp\.venv\Scripts\python.exe" "$PWD\tools\gemini-texture-mcp\server.py"
claude mcp list
```
Danach in Claude Code z. B.: „Erzeuge mit gemini-textures eine 16×16-Messing-Gittertextur, transparent,
nach src/main/resources/assets/pghv/textures/block/brass_fan_grill.png und zeig sie mir.“

## Variante B – als Connector in claude.ai (über deinen Server)

claude.ai braucht eine öffentlich erreichbare HTTPS-Adresse. Der geheime Pfadteil (`MCP_PATH_TOKEN`) schützt deinen
Gemini-Schlüssel – lang und zufällig wählen.

Lokal:
```bash
scp -r tools/gemini-texture-mcp user@DEIN-SERVER:/opt/
```

Am Server:
```bash
cd /opt/gemini-texture-mcp
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
export GEMINI_API_KEY=DEIN_SCHLUESSEL MCP_PATH_TOKEN=$(openssl rand -hex 24) TEXTURE_ROOT=/opt/gemini-texture-mcp/out
mkdir -p "$TEXTURE_ROOT" && echo "Token: $MCP_PATH_TOKEN"
.venv/bin/python server.py --http --host 127.0.0.1 --port 8765
# Reverse-Proxy (z. B. Caddy):  texturen.deine-domain.at { reverse_proxy 127.0.0.1:8765 }
```
In claude.ai: Einstellungen → Connectors → Eigenen Connector hinzufügen →
`https://texturen.deine-domain.at/<TOKEN>/mcp`.
Hinweis: In Variante B liegen die PNGs auf dem Server (`TEXTURE_ROOT`) – Claude sieht die Vorschau und kann sie
von dort ins Repo übernehmen lassen; für direktes Arbeiten im Repo ist Variante A bequemer.

## Lizenz
Fremde Texturen (Create, Power Grid) nur als Stil-Referenz verwenden, nicht 1:1 nachbauen lassen.
Ergebnisse vor dem Einchecken ansehen.
