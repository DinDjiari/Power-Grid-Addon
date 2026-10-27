# Textur-Aufträge (Gemini über GitHub Actions)

Eine `*.json` hier hinein pushen ⇒ der Workflow „Gemini-Texturen“ erzeugt die Texturen, committet sie auf denselben
Branch und legt Vorschau/Rohbilder unter `out/<auftrag>/` ab. Format: siehe `tools/gemini-texture-mcp/batch.py`.
Voraussetzung: Repository-Secret `GEMINI_API_KEY` (Settings → Secrets and variables → Actions).
