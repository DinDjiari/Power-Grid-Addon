"""
Stapelbetrieb für GitHub Actions: arbeitet Auftragsdateien texture-requests/*.json ab.

Auftrag:
  {"jobs": [
     {"tool": "generate_texture", "args": {"prompt": "...", "out_path": "src/main/resources/assets/pghv/textures/block/x.png",
                                          "width": 16, "height": 16, "colors": 12, "transparent": false}},
     {"tool": "edit_texture", "args": {...}}, {"tool": "pixelize", "args": {...}}
  ]}
Ergebnis: texture-requests/results/<auftrag>/  (Vorschau je Job, Rohbilder, result.json); Auftrag -> texture-requests/done/.
"""
import json
import os
import shutil
import sys
import traceback
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import server  # noqa: E402

REQ = Path("texture-requests")


def run(req: Path) -> None:
    out = REQ / "results" / req.stem
    out.mkdir(parents=True, exist_ok=True)
    results = []
    jobs = json.loads(req.read_text(encoding="utf-8")).get("jobs", [])
    for i, job in enumerate(jobs):
        name = f"{i:02d}_" + Path(job.get("args", {}).get("out_path", f"job{i}")).stem
        try:
            fn = {"generate_texture": server.generate_texture, "edit_texture": server.edit_texture,
                  "pixelize": server.pixelize, "show_textures": server.show_textures}[job["tool"]]
            res = fn(**job["args"])
            text = [r for r in res if isinstance(r, str)]
            for r in res:
                if not isinstance(r, str):
                    (out / f"{name}_preview.png").write_bytes(r.data)
            results.append({"job": i, "ok": True, "text": text})
        except Exception as e:  # noqa: BLE001
            results.append({"job": i, "ok": False, "error": str(e), "trace": traceback.format_exc()[-1500:]})
        print(json.dumps(results[-1], ensure_ascii=False)[:500])
    raw = Path(".gemini_raw")
    if raw.exists():
        (out / "raw").mkdir(exist_ok=True)
        for f in raw.iterdir():
            shutil.move(str(f), out / "raw" / f.name)
    (out / "result.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    (REQ / "done").mkdir(exist_ok=True)
    shutil.move(str(req), REQ / "done" / req.name)


if __name__ == "__main__":
    os.environ.setdefault("TEXTURE_ROOT", os.getcwd())
    for r in sorted(REQ.glob("*.json")):
        print("Auftrag", r)
        run(r)
