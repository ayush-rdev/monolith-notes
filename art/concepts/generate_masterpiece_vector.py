import subprocess

concepts_dir = "/home/rdev/Monolith/art/concepts"

# Masterpiece Vector: The Hexagonal Architectural Monolith "M"
svg_master = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <!-- 1. Pure AMOLED Deep Obsidian Squircle Background -->
    <radialGradient id="bgObsidian" cx="50%" cy="38%" r="72%">
      <stop offset="0%" stop-color="#15151C" />
      <stop offset="55%" stop-color="#08080B" />
      <stop offset="100%" stop-color="#020203" />
    </radialGradient>

    <!-- 2. Subtle Outer Metallic Bezel -->
    <linearGradient id="squircleRim" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#3E3E50" stop-opacity="0.9" />
      <stop offset="40%" stop-color="#20202A" stop-opacity="0.6" />
      <stop offset="100%" stop-color="#0A0A10" stop-opacity="0.4" />
    </linearGradient>

    <!-- 3. Left Monolith Wing (Brushed Titanium Specular) -->
    <linearGradient id="leftWingGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" />
      <stop offset="35%" stop-color="#DCDCE6" />
      <stop offset="70%" stop-color="#9A9AAA" />
      <stop offset="100%" stop-color="#606070" />
    </linearGradient>

    <!-- 4. Left Downward Bevel Facet (Darker Metal) -->
    <linearGradient id="leftDownFacet" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#8E8E9E" />
      <stop offset="60%" stop-color="#4A4A5A" />
      <stop offset="100%" stop-color="#242430" />
    </linearGradient>

    <!-- 5. Center Diagonal Light Blade -->
    <linearGradient id="centerBladeGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#E8E8F2" />
      <stop offset="50%" stop-color="#B0B0C4" />
      <stop offset="100%" stop-color="#6E6E82" />
    </linearGradient>

    <!-- 6. Right Pillar (Deep Shadow Titanium) -->
    <linearGradient id="rightPillarGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#B8B8C8" />
      <stop offset="50%" stop-color="#727284" />
      <stop offset="100%" stop-color="#323240" />
    </linearGradient>

    <!-- 7. Warm Amber Gold Core Crevice Glow -->
    <linearGradient id="goldCrevice" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FFE066" />
      <stop offset="50%" stop-color="#F5A623" />
      <stop offset="100%" stop-color="#D97706" />
    </linearGradient>

    <!-- Drop Shadow filter for floating depth -->
    <filter id="monolithGlow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="16" stdDeviation="20" flood-color="#000000" flood-opacity="0.8" />
      <feDropShadow dx="0" dy="2" stdDeviation="6" flood-color="#F5A623" flood-opacity="0.15" />
    </filter>
  </defs>

  <!-- Squircle Base -->
  <rect x="24" y="24" width="464" height="464" rx="108" fill="url(#bgObsidian)" stroke="url(#squircleRim)" stroke-width="2.5" />

  <!-- Monolith Hex-M Architectural Emblem -->
  <g transform="translate(152, 114)" filter="url(#monolithGlow)">
    
    <!-- LEFT WING OF 'M': Monolithic Hex Pillar & Inner Diagonal Slope -->
    <path d="M 0 68 L 78 0 L 84 0 L 84 148 L 40 220 L 40 284 L 0 248 Z" 
          fill="url(#leftWingGrad)" />

    <!-- Left Wing 3D Bevel Facet -->
    <path d="M 0 68 L 40 102 L 40 284 L 0 248 Z" 
          fill="url(#leftDownFacet)" 
          opacity="0.85" />

    <!-- Golden Edge Line on the Inner Crevice (Left edge of V) -->
    <line x1="84" y1="18" x2="84" y2="152" stroke="url(#goldCrevice)" stroke-width="3" stroke-linecap="round" />
    <line x1="84" y1="152" x2="40" y2="224" stroke="url(#goldCrevice)" stroke-width="3" stroke-linecap="round" />

    <!-- CENTER DIAGONAL ASCENT OF 'M' -->
    <path d="M 94 158 L 132 80 L 132 284 L 88 284 L 88 234 Z" 
          fill="url(#centerBladeGrad)" />

    <!-- Golden Highlight in the Center Slit -->
    <line x1="94" y1="160" x2="132" y2="84" stroke="url(#goldCrevice)" stroke-width="2.5" stroke-linecap="round" />

    <!-- RIGHT PILLAR OF 'M': Standing Monolith Column -->
    <path d="M 142 34 L 196 66 L 196 248 L 142 284 Z" 
          fill="url(#rightPillarGrad)" />

    <!-- Right Pillar Top Chamfer Light Bevel -->
    <polygon points="142,34 196,66 186,74 142,48" fill="#FFFFFF" opacity="0.6" />
  </g>
</svg>"""

with open(f"{concepts_dir}/monolith_masterpiece.svg", "w") as f:
    f.write(svg_master)

subprocess.run(["rsvg-convert", "-w", "1024", "-h", "1024", f"{concepts_dir}/monolith_masterpiece.svg", "-o", f"{concepts_dir}/monolith_masterpiece_1024.png"], check=True)
subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", f"{concepts_dir}/monolith_masterpiece.svg", "-o", f"{concepts_dir}/monolith_masterpiece_512.png"], check=True)
print("Masterpiece vector generated and rendered at 1024 and 512!")
