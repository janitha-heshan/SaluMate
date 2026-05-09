
import sys
try:
    from PIL import Image
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow"])
    from PIL import Image

img = Image.open(r"d:\University\Projects\EEI4369-Android\SaluMate - Copy\Salumate_icon.png").convert("RGBA")
colors = sorted(img.getcolors(img.size[0] * img.size[1]), key=lambda t: t[0], reverse=True)
solid = [c for c in colors if c[1][3] > 200]
print("DOMINANT COLORS:")
for idx, (cnt, col) in enumerate(solid[:5]):
    print(f"Color {idx}: #{col[0]:02x}{col[1]:02x}{col[2]:02x} ({cnt} pixels)")

