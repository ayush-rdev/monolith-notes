import subprocess
import os
from PIL import Image, ImageOps

base_dir = "/home/rdev/Monolith"
art_dir = f"{base_dir}/art"
res_dir = f"{base_dir}/app/src/main/res"
master_svg = f"{base_dir}/art/concepts/monolith_masterpiece.svg"

# 1. Update art master assets
subprocess.run(["cp", master_svg, f"{art_dir}/monolith-logo-mark.svg"], check=True)
subprocess.run(["rsvg-convert", "-w", "1024", "-h", "1024", master_svg, "-o", f"{art_dir}/monolith-app-icon-1024.png"], check=True)
subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", master_svg, "-o", f"{art_dir}/monolith-app-icon-512.png"], check=True)

# 2. In-app About sheet logo (512x512)
subprocess.run(["cp", f"{art_dir}/monolith-app-icon-512.png", f"{res_dir}/drawable/monolith_logo.png"], check=True)

# 3. Create foreground glyph for Adaptive Icon (432x432 canvas, safe zone center ~250px)
# First render the emblem without squircle background for the adaptive foreground
symbol_svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="432" height="432">
  <defs>
    <!-- Left Wing (Brushed Titanium) -->
    <linearGradient id="leftWingGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" />
      <stop offset="35%" stop-color="#DCDCE6" />
      <stop offset="70%" stop-color="#9A9AAA" />
      <stop offset="100%" stop-color="#606070" />
    </linearGradient>

    <!-- Left Downward Bevel Facet -->
    <linearGradient id="leftDownFacet" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#8E8E9E" />
      <stop offset="60%" stop-color="#4A4A5A" />
      <stop offset="100%" stop-color="#242430" />
    </linearGradient>

    <!-- Center Diagonal Light Blade -->
    <linearGradient id="centerBladeGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#E8E8F2" />
      <stop offset="50%" stop-color="#B0B0C4" />
      <stop offset="100%" stop-color="#6E6E82" />
    </linearGradient>

    <!-- Right Pillar -->
    <linearGradient id="rightPillarGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#B8B8C8" />
      <stop offset="50%" stop-color="#727284" />
      <stop offset="100%" stop-color="#323240" />
    </linearGradient>

    <!-- Warm Amber Gold Core Crevice Glow -->
    <linearGradient id="goldCrevice" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FFE066" />
      <stop offset="50%" stop-color="#F5A623" />
      <stop offset="100%" stop-color="#D97706" />
    </linearGradient>

    <filter id="monolithGlow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="12" stdDeviation="16" flood-color="#000000" flood-opacity="0.85" />
      <feDropShadow dx="0" dy="1" stdDeviation="4" flood-color="#F5A623" flood-opacity="0.25" />
    </filter>
  </defs>

  <g transform="translate(152, 114)" filter="url(#monolithGlow)">
    <!-- LEFT WING OF 'M' -->
    <path d="M 0 68 L 78 0 L 84 0 L 84 148 L 40 220 L 40 284 L 0 248 Z" fill="url(#leftWingGrad)" />
    <path d="M 0 68 L 40 102 L 40 284 L 0 248 Z" fill="url(#leftDownFacet)" opacity="0.85" />
    <line x1="84" y1="18" x2="84" y2="152" stroke="url(#goldCrevice)" stroke-width="3" stroke-linecap="round" />
    <line x1="84" y1="152" x2="40" y2="224" stroke="url(#goldCrevice)" stroke-width="3" stroke-linecap="round" />

    <!-- CENTER DIAGONAL ASCENT OF 'M' -->
    <path d="M 94 158 L 132 80 L 132 284 L 88 284 L 88 234 Z" fill="url(#centerBladeGrad)" />
    <line x1="94" y1="160" x2="132" y2="84" stroke="url(#goldCrevice)" stroke-width="2.5" stroke-linecap="round" />

    <!-- RIGHT PILLAR OF 'M' -->
    <path d="M 142 34 L 196 66 L 196 248 L 142 284 Z" fill="url(#rightPillarGrad)" />
    <polygon points="142,34 196,66 186,74 142,48" fill="#FFFFFF" opacity="0.6" />
  </g>
</svg>"""

with open(f"{art_dir}/symbol_foreground.svg", "w") as f:
    f.write(symbol_svg)

subprocess.run(["rsvg-convert", "-w", "432", "-h", "432", f"{art_dir}/symbol_foreground.svg", "-o", f"{res_dir}/drawable/ic_launcher_foreground.png"], check=True)

# 4. Generate Monochrome version for Android 13+ Themed Icons
# Pure white silhouette
monochrome_svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="432" height="432">
  <g transform="translate(152, 114)">
    <path d="M 0 68 L 78 0 L 84 0 L 84 148 L 40 220 L 40 284 L 0 248 Z" fill="#FFFFFF" />
    <path d="M 94 158 L 132 80 L 132 284 L 88 284 L 88 234 Z" fill="#FFFFFF" />
    <path d="M 142 34 L 196 66 L 196 248 L 142 284 Z" fill="#FFFFFF" />
  </g>
</svg>"""
with open(f"{art_dir}/symbol_monochrome.svg", "w") as f:
    f.write(monochrome_svg)

subprocess.run(["rsvg-convert", "-w", "432", "-h", "432", f"{art_dir}/symbol_monochrome.svg", "-o", f"{res_dir}/drawable/ic_launcher_monochrome.png"], check=True)

# 5. Generate legacy launcher icons across densities
densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

master_img = Image.open(f"{art_dir}/monolith-app-icon-1024.png")

for folder, size in densities.items():
    target_dir = f"{res_dir}/{folder}"
    os.makedirs(target_dir, exist_ok=True)
    
    # Square / Squircle
    resized_square = master_img.resize((size, size), Image.Resampling.LANCZOS)
    resized_square.save(f"{target_dir}/ic_launcher.png")
    
    # Circle mask for round icon
    mask = Image.new('L', (size, size), 0)
    from PIL import ImageDraw
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    
    round_img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    round_img.paste(resized_square, (0, 0), mask=mask)
    round_img.save(f"{target_dir}/ic_launcher_round.png")

print("All icons successfully generated across all densities!")
