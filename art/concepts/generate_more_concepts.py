import subprocess

concepts_dir = "/home/rdev/Monolith/art/concepts"

# Concept 4: The Chiseled Golden-Ratio Monolith (Pen Nib & Obelisk)
svg4 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="bgGrad4" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#111116" />
      <stop offset="100%" stop-color="#040406" />
    </linearGradient>
    <linearGradient id="monolithBody4" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#2C2C38" />
      <stop offset="100%" stop-color="#14141A" />
    </linearGradient>
    <linearGradient id="edgeGleam4" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.9" />
      <stop offset="100%" stop-color="#8E8E9F" stop-opacity="0.2" />
    </linearGradient>
    <linearGradient id="accentGold4" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FFD60A" />
      <stop offset="100%" stop-color="#FF9F0A" />
    </linearGradient>
  </defs>

  <!-- Squircle Base -->
  <rect x="24" y="24" width="464" height="464" rx="108" fill="url(#bgGrad4)" stroke="#1F1F28" stroke-width="2" />

  <!-- The Monolith: Chiseled Pillar with Scribe Slit -->
  <g transform="translate(166, 116)">
    <!-- Main Left Pillar (Golden Ratio Width 110) -->
    <path d="M 0 60 L 60 0 L 110 0 L 110 280 L 0 280 Z" fill="url(#monolithBody4)" stroke="url(#edgeGleam4)" stroke-width="2" />

    <!-- Right Pillar (Offset Slab) -->
    <path d="M 124 0 L 180 0 L 180 280 L 124 280 Z" fill="#1C1C24" stroke="url(#edgeGleam4)" stroke-width="1.5" />

    <!-- Precision Gold Laser Dot / Scribe Core at Golden Ratio -->
    <circle cx="117" cy="110" r="7" fill="url(#accentGold4)" />
    <line x1="117" y1="125" x2="117" y2="210" stroke="url(#accentGold4)" stroke-width="3" stroke-linecap="round" />
  </g>
</svg>"""

# Concept 5: The Iconic Bauhaus Monolith "M" (Two Minimalist Beveled Slabs)
svg5 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="bgGrad5" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#0E0E12" />
      <stop offset="100%" stop-color="#030304" />
    </linearGradient>
    <linearGradient id="slabLeft5" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" />
      <stop offset="100%" stop-color="#8E8E9A" />
    </linearGradient>
    <linearGradient id="slabRight5" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#D0D0DC" />
      <stop offset="100%" stop-color="#4A4A58" />
    </linearGradient>
    <linearGradient id="coreLine5" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#F5A623" />
      <stop offset="100%" stop-color="#D0021B" />
    </linearGradient>
  </defs>

  <!-- Squircle Base -->
  <rect x="24" y="24" width="464" height="464" rx="108" fill="url(#bgGrad5)" stroke="#262632" stroke-width="2" />

  <!-- Two Intersecting Monolithic Chiseled Pillars forming "M" -->
  <g transform="translate(136, 136)">
    <!-- Left Monolith Slab with 45 deg chamfer -->
    <path d="M 0 50 L 50 0 L 100 0 L 100 240 L 0 240 Z" fill="url(#slabLeft5)" />

    <!-- Right Monolith Slab with inverted 45 deg chamfer -->
    <path d="M 140 0 L 190 0 L 240 50 L 240 240 L 140 240 Z" fill="url(#slabRight5)" />

    <!-- Center Floating Obelisk Diamond connecting them -->
    <polygon points="120,40 148,80 120,180 92,80" fill="#FFFFFF" />
    <circle cx="120" cy="110" r="8" fill="#F5A623" />
  </g>
</svg>"""

# Concept 6: The Ultra-Minimal Monolith Totem (Pristine Black & Titanium Line)
# Purest minimalism: An architectural monolith silhouette with a single sharp diagonal light facet
svg6 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="bgGrad6" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#121216" />
      <stop offset="100%" stop-color="#050507" />
    </linearGradient>
    <linearGradient id="lightFacet" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" />
      <stop offset="50%" stop-color="#C5C5D2" />
      <stop offset="100%" stop-color="#727282" />
    </linearGradient>
    <linearGradient id="darkFacet" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#2A2A36" />
      <stop offset="100%" stop-color="#14141B" />
    </linearGradient>
    <linearGradient id="goldDot" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFD60A" />
      <stop offset="100%" stop-color="#FF9F0A" />
    </linearGradient>
  </defs>

  <!-- Squircle Base -->
  <rect x="24" y="24" width="464" height="464" rx="108" fill="url(#bgGrad6)" stroke="#22222E" stroke-width="2" />

  <!-- Monolith: Two-tone Architectural Obelisk (Standing Monument) -->
  <g transform="translate(186, 120)">
    <!-- Left Illuminated Facet -->
    <path d="M 0 272 L 70 272 L 70 0 L 24 46 Z" fill="url(#lightFacet)" />

    <!-- Right Shadow Facet -->
    <path d="M 70 272 L 140 272 L 116 46 L 70 0 Z" fill="url(#darkFacet)" />

    <!-- Center Spine Hairline -->
    <line x1="70" y1="0" x2="70" y2="272" stroke="#FFFFFF" stroke-opacity="0.3" stroke-width="1.5" />

    <!-- Golden Eye at apex -->
    <circle cx="70" cy="50" r="10" fill="url(#goldDot)" />
  </g>
</svg>"""

with open(f"{concepts_dir}/concept4.svg", "w") as f:
    f.write(svg4)
with open(f"{concepts_dir}/concept5.svg", "w") as f:
    f.write(svg5)
with open(f"{concepts_dir}/concept6.svg", "w") as f:
    f.write(svg6)

for i in [4, 5, 6]:
    subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", f"{concepts_dir}/concept{i}.svg", "-o", f"{concepts_dir}/concept{i}.png"], check=True)

print("Concepts 4, 5, 6 converted to PNG successfully!")
