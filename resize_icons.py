
import os
from PIL import Image

src_img = r"d:\University\Projects\EEI4369-Android\SaluMate - Copy\app\src\main\res\drawable\salumate_icon.png"
base_dir = r"d:\University\Projects\EEI4369-Android\SaluMate - Copy\app\src\main\res"

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

try:
    img = Image.open(src_img).convert("RGBA")
    for folder, size in sizes.items():
        out_folder = os.path.join(base_dir, folder)
        if not os.path.exists(out_folder):
            os.makedirs(out_folder)
            
        # Using High-Quality anti-aliased Lanczos downscaling natively supported inside Pillow
        resized = img.resize((size, size), getattr(Image, "Resampling", Image).LANCZOS)
        
        resized.save(os.path.join(out_folder, "ic_launcher.png"))
        resized.save(os.path.join(out_folder, "ic_launcher_round.png"))
        
    print("Native icon overrides have been successful!")
except Exception as e:
    print(f"Error: {e}")

