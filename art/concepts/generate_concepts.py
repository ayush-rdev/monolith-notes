import subprocess
import os

concepts_dir = "/home/rdev/Monolith/art/concepts"

# Concept 1: The Monolith "M" (Architectural Pillars with Chamfered Chisel)
svg1 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <!-- Deep obsidian squircle background -->
    <linearGradient id="bgGrad1" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#141418" />
      <stop offset="100%" stop-color="#070709" />
    </linearGradient>
    <!-- Outer hairline rim -->
    <linearGradient id="rimGrad1" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#3A3A48" />
      <stop offset="50%" stop-color="#1E1E26" />
      <stop offset="100%" stop-color="#0F0F14" />
    </linearGradient>
    <!-- Monolith left pillar -->
    <linearGradient id="pillarLeft" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#FFFFFF" />
      <stop offset="100%" stop-color="#D0D0D8" />
    </linearGradient>
    <!-- Monolith right pillar (subtle shadow) -->
    <linearGradient id="pillarRight" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#E2E2EA" />
      <stop offset="100%" stop-color="#9A9AA6" />
    </linearGradient>
    <!-- Center chevron -->
    <linearGradient id="centerCore" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#F2A63B" />
      <stop offset="100%" stop-color="#D97706" />
    </linearGradient>
  </defs>

  <!-- Squircle Base -->
  <rect x="24" y="24" width="464" height="464" rx="108" fill="url(#bgGrad1)" stroke="url(#rimGrad1)" stroke-width="2.5" />

  <!-- Monolith M Emblem (Pure, bold, mathematical geometric vector) -->
  <g transform="translate(136, 126)">
    <!-- Left Pillar -->
    <rect x="0" y="0" width="56" height="260" rx="16" fill="url(#pillarLeft)" />
    
    <!-- Right Pillar -->
    <rect x="184" y="0" width="56" height="260" rx="16" fill="url(#pillarRight)" />
    
    <!-- Center Monolith Obelisk (Chiseled Diamond V-Chevron) -->
    <path d="M 56 60 L 120 180 L 184 60 L 184 130 L 120 250 L 56 130 Z" fill="#F4F4F8" />
    
    <!-- Subtle Golden Core Highlight in Center Notch -->
    <circle cx="120" cy="100" r="14" fill="url(#centerCore)" />
  </g>
</svg>"""

# Concept 2: The Monolith Slab & Stylus Cut (Ultra-minimalist, sleek, iconic)
svg2 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="bgGrad2" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#121215" />
      <stop offset="100%" stop-color="#050507" />
    </linearGradient>
    <linearGradient id="rimGrad2" x1="20%" y1="0%" x2="80%" y2="100%">
      <stop offset="0%" stop-color="#2E2E38" />
      <stop offset="100%" stop-color="#101014" />
    </linearGradient>
    <linearGradient id="monolithFace" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#24242E" />
      <stop offset="100%" stop-color="#16161D" />
    </linearGradient>
    <linearGradient id="monolithRim" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.9" />
      <stop offset="40%" stop-color="#A0A0B0" stop-opacity="0.4" />
      <stop offset="100%" stop-color="#404050" stop-opacity="0.1" />
    </linearGradient>
    <linearGradient id="goldLaser" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FFD60A" />
      <stop offset="100%" stop-color="#FF9F0A" />
    </linearGradient>
  </defs>

  <!-- Squircle Base -->
  <rect x="24" y="24" width="464" height="464" rx="108" fill="url(#bgGrad2)" stroke="url(#rimGrad2)" stroke-width="2" />

  <!-- The Monolith Tablet (Standing Slab with Beveled Chamfer) -->
  <g transform="translate(156, 116)">
    <!-- Main Standing Monolith Body -->
    <path d="M 28 0 L 140 0 C 155 0, 172 17, 172 32 L 172 248 C 172 266, 154 280, 136 280 L 36 280 C 18 280, 0 266, 0 248 L 0 28 C 0 12, 12 0, 28 0 Z" 
          fill="url(#monolithFace)" 
          stroke="url(#monolithRim)" 
          stroke-width="2.5" />

    <!-- Center Precision Laser Notch (Vault Line) -->
    <line x1="86" y1="44" x2="86" y2="180" stroke="url(#goldLaser)" stroke-width="6" stroke-linecap="round" />

    <!-- Three Minimalist Horizontal Data Ticks (Note Metaphor) -->
    <line x1="48" y1="210" x2="124" y2="210" stroke="#FFFFFF" stroke-opacity="0.75" stroke-width="4" stroke-linecap="round" />
    <line x1="48" y1="230" x2="100" y2="230" stroke="#FFFFFF" stroke-opacity="0.35" stroke-width="4" stroke-linecap="round" />
  </g>
</svg>"""

# Concept 3: The Pure Obsidian Monolith Geometric Mark (Stark Bauhaus / Modern Luxury)
# Clean, bold, iconic silhouette: An isometric standing monolith with light on one facet,
# creating an unforgettable symbol that works at any scale.
svg3 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="bgGrad3" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#111114" />
      <stop offset="100%" stop-color="#040405" />
    </linearGradient>
    <linearGradient id="facetTop" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" />
      <stop offset="100%" stop-color="#DCDCE2" />
    </linearGradient>
    <linearGradient id="facetLeft" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#A5A5B2" />
      <stop offset="100%" stop-color="#6E6E7A" />
    </linearGradient>
    <linearGradient id="facetRight" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#3C3C46" />
      <stop offset="100%" stop-color="#1C1C22" />
    </linearGradient>
    <linearGradient id="glowPoint" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFD60A" />
      <stop offset="100%" stop-color="#E59B00" />
    </linearGradient>
  </defs>

  <!-- Squircle Base -->
  <rect x="24" y="24" width="464" height="464" rx="108" fill="url(#bgGrad3)" stroke="#222228" stroke-width="2" />

  <!-- Isometric Monolith Block (Standing Totem of Knowledge & Security) -->
  <g transform="translate(256, 256)">
    <!-- Top Diamond Facet -->
    <path d="M 0 -130 L 76 -86 L 0 -42 L -76 -86 Z" fill="url(#facetTop)" />

    <!-- Left Facet (Light grey / mid-tone) -->
    <path d="M -76 -86 L 0 -42 L 0 134 L -76 90 Z" fill="url(#facetLeft)" />

    <!-- Right Facet (Dark obsidian) -->
    <path d="M 0 -42 L 76 -86 L 76 90 L 0 134 Z" fill="url(#facetRight)" />

    <!-- Golden Precision Core Line down center vertex -->
    <line x1="0" y1="-38" x2="0" y2="130" stroke="url(#glowPoint)" stroke-width="5" stroke-linecap="round" />
  </g>
</svg>"""

with open(f"{concepts_dir}/concept1.svg", "w") as f:
    f.write(svg1)
with open(f"{concepts_dir}/concept2.svg", "w") as f:
    f.write(svg2)
with open(f"{concepts_dir}/concept3.svg", "w") as f:
    f.write(svg3)

print("SVGs generated. Converting to PNG...")
for i in [1, 2, 3]:
    subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", f"{concepts_dir}/concept{i}.svg", "-o", f"{concepts_dir}/concept{i}.png"], check=True)

print("Done!")
