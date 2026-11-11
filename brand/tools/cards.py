"""Export the social cards (canvas boards in final/canvas/Card*.dc.html) as PNGs.

Usage (from brand/):
  python3 tools/cards.py <geist-fonts-dir> final/social

<geist-fonts-dir> is dist/fonts from the `geist` npm package (OFL; not committed). The cards render
with those local files instead of Google Fonts, so the output doesn't depend on the network.
Needs a `chromium` on PATH (or CHROMIUM=/path/to/chromium).
"""
import os
import re
import subprocess
import sys
import tempfile

CARDS = {
    "CardOgLight": ("zio-logging-og-light.png", 1200, 630),
    "CardOgDark": ("zio-logging-og-dark.png", 1200, 630),
    "CardGhLight": ("zio-logging-github-light.png", 1280, 640),
    "CardGhDark": ("zio-logging-github-dark.png", 1280, 640),
}


def font_faces(fonts):
    faces = [("Geist", "geist-sans/Geist-Regular.woff2", 400), ("Geist", "geist-sans/Geist-SemiBold.woff2", 600),
             ("Geist Mono", "geist-mono/GeistMono-Regular.woff2", 400),
             ("Geist Mono", "geist-mono/GeistMono-Medium.woff2", 500)]
    out = []
    for family, rel, weight in faces:
        path = os.path.join(fonts, rel)
        if not os.path.exists(path):
            sys.exit(f"missing font file: {path}")
        out.append(f"@font-face{{font-family:'{family}';src:url('file://{path}');font-weight:{weight}}}")
    return "".join(out)


def card_body(src):
    """The card's markup: everything inside <x-dc> after the <helmet> block."""
    m = re.search(r"</helmet>(.*?)</x-dc>", src, re.S)
    if not m:
        sys.exit("card file has no <x-dc> body")
    return m.group(1).strip()


def main(fonts, out_dir):
    fonts = os.path.abspath(fonts)
    chromium = os.environ.get("CHROMIUM", "chromium")
    os.makedirs(out_dir, exist_ok=True)
    here = os.path.dirname(os.path.abspath(__file__))
    canvas = os.path.join(here, "..", "final", "canvas")
    with tempfile.TemporaryDirectory() as tmp:
        for card, (png, w, h) in CARDS.items():
            with open(os.path.join(canvas, f"{card}.dc.html")) as f:
                body = card_body(f.read())
            page = os.path.join(tmp, f"{card}.html")
            with open(page, "w") as f:
                f.write(f"<!doctype html><meta charset=utf-8><style>{font_faces(fonts)}"
                        f"html,body{{margin:0;overflow:hidden}}</style>{body}")
            out = os.path.abspath(os.path.join(out_dir, png))
            subprocess.run([chromium, "--headless", "--disable-gpu", "--hide-scrollbars",
                            "--force-device-scale-factor=1", "--allow-file-access-from-files",
                            "--virtual-time-budget=5000", f"--window-size={w},{h}",
                            f"--screenshot={out}", f"file://{page}"],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print("wrote", out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
