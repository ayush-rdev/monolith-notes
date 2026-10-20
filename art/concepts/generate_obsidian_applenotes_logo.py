import subprocess
import os

concepts_dir = "/home/rdev/Monolith/art/concepts"

# Design Concept:
# "The Monolith Codex" - Obsidian Crystal meets Apple Notes.
# An isometric 3D faceted crystalline monolith journal inside an AMOLED squircle.
# Features:
# 1. Dark faceted obsidian stone slabs forming an open or floating isometric notebook.
# 2. Apple Notes iconic warm amber-gold header band (#FFD60A / #E5A93C) and folded leaf facet.
# 3. Obsidian crystalline geometric facets with subtle violet (#8B5CF6) and brushed titanium edges.
# 4. Minimalist glowing notebook ruling lines (markdown thought lines).
# 5. Faceted 3D floating stylus / golden bookmark shard.

svg_code = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <!-- Background AMOLED Squircle with deep vignette -->
    <radialGradient id="bgVignette" cx="50%" cy="40%" r="65%">
      <stop offset="0%" stop-color="#141419" />
      <stop offset="55%" stop-color="#08080B" />
      <stop offset="100%" stop-color="#010102" />
    </radialGradient>

    <!-- Squircle Border Rim -->
    <linearGradient id="rimGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#3A3A4A" stop-opacity="0.8" />
      <stop offset="50%" stop-color="#1E1E26" stop-opacity="0.4" />
      <stop offset="100%" stop-color="#0D0D12" stop-opacity="0.6" />
    </linearGradient>

    <!-- Apple Notes Iconic Amber Gold Header Accent -->
    <linearGradient id="appleGoldBand" x1="0%" y1="0%" x2="100%" y2="50%">
      <stop offset="0%" stop-color="#FFE57F" />
      <stop offset="35%" stop-color="#FFD60A" />
      <stop offset="75%" stop-color="#F59E0B" />
      <stop offset="100%" stop-color="#D97706" />
    </linearGradient>

    <!-- Obsidian Violet-to-Amber Core Fusion Glow -->
    <linearGradient id="crystalGlow" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#A78BFA" />
      <stop offset="40%" stop-color="#8B5CF6" />
      <stop offset="75%" stop-color="#F59E0B" />
      <stop offset="100%" stop-color="#FFD60A" />
    </linearGradient>

    <!-- Top Notebook Surface (Deep Obsidian Matte Slate) -->
    <linearGradient id="topSlateGrad" x1="20%" y1="0%" x2="80%" y2="100%">
      <stop offset="0%" stop-color="#262633" />
      <stop offset="50%" stop-color="#1B1B24" />
      <stop offset="100%" stop-color="#121218" />
    </linearGradient>

    <!-- Left Notebook Spine / Page Block -->
    <linearGradient id="leftSpineGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#4A4A5E" />
      <stop offset="40%" stop-color="#2D2D3A" />
      <stop offset="100%" stop-color="#16161D" />
    </linearGradient>

    <!-- Right Base Thickness / Edge Facet -->
    <linearGradient id="rightBaseGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#1F1F2A" />
      <stop offset="60%" stop-color="#14141C" />
      <stop offset="100%" stop-color="#0B0B10" />
    </linearGradient>

    <!-- Folded Corner Top Facet (Apple Notes Fold) -->
    <linearGradient id="foldedTop" x1="0%" y1="100%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.9" />
      <stop offset="40%" stop-color="#D4D4E8" />
      <stop offset="100%" stop-color="#8C8CA4" />
    </linearGradient>

    <!-- Folded Corner Underside Shadow -->
    <linearGradient id="foldedShadow" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#000000" stop-opacity="0.9" />
      <stop offset="100%" stop-color="#000000" stop-opacity="0.1" />
    </linearGradient>

    <!-- 3D Floating Stylus / Crystal Shard (Obsidian Pencil Shard) -->
    <linearGradient id="stylusBody" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" />
      <stop offset="30%" stop-color="#E2E8F0" />
      <stop offset="70%" stop-color="#94A3B8" />
      <stop offset="100%" stop-color="#475569" />
    </linearGradient>

    <linearGradient id="stylusTip" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FFE066" />
      <stop offset="100%" stop-color="#F59E0B" />
    </linearGradient>

    <!-- Ambient Shadow Filter -->
    <filter id="ambientDepth" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="24" stdDeviation="28" flood-color="#000000" flood-opacity="0.95" />
      <feDropShadow dx="0" dy="4" stdDeviation="12" flood-color="#000000" flood-opacity="0.7" />
      <feDropShadow dx="0" dy="0" stdDeviation="16" flood-color="#F59E0B" flood-opacity="0.12" />
    </filter>

    <filter id="shardGlow" x="-30%" y="-30%" width="160%" height="160%">
      <feDropShadow dx="0" dy="8" stdDeviation="12" flood-color="#000000" flood-opacity="0.8" />
      <feDropShadow dx="0" dy="0" stdDeviation="6" flood-color="#FFD60A" flood-opacity="0.3" />
    </filter>
  </defs>

  <!-- 1. Apple-grade Squircle Container -->
  <rect x="24" y="24" width="464" height="464" rx="108" fill="url(#bgVignette)" stroke="url(#rimGrad)" stroke-width="2.5" />

  <!-- 2. Main 3D Isometric Monolith Notebook Entity -->
  <g transform="translate(256, 260)" filter="url(#ambientDepth)">

    <!-- Isometric Coordinate Projection -->
    <!--
      Center origin at (0, 0)
      Top Face Points:
      Top corner:       (0, -140)
      Right corner:     (120, -70)
      Folded cut corner:(100, -82) -> fold cut at (72, -98)
      Bottom corner:    (0, 70)
      Left corner:      (-120, -10)
    -->

    <!-- BOTTOM THICKNESS / PAGES BLOCK (Left side) -->
    <!-- From Left (-120, -10) to Bottom (0, 70), extruded down 36px to (-120, 26) and (0, 106) -->
    <path d="M -120 -10 L 0 70 L 0 108 L -120 28 Z" fill="url(#leftSpineGrad)" />

    <!-- Page Layers Grooves (Subtle tactile lines representing notebook paper stack) -->
    <line x1="-120" y1="2" x2="0" y2="82" stroke="#6B6B82" stroke-width="1.2" opacity="0.4" />
    <line x1="-120" y1="14" x2="0" y2="94" stroke="#484858" stroke-width="1.2" opacity="0.4" />

    <!-- RIGHT THICKNESS (Right side) -->
    <!-- From Bottom (0, 70) to Right (120, -10), extruded down 38px to (0, 108) and (120, 28) -->
    <path d="M 0 70 L 120 -10 L 120 28 L 0 108 Z" fill="url(#rightBaseGrad)" />
    <line x1="0" y1="82" x2="120" y2="2" stroke="#2E2E3E" stroke-width="1.2" opacity="0.5" />
    <line x1="0" y1="94" x2="120" y2="14" stroke="#1D1D26" stroke-width="1.2" opacity="0.5" />

    <!-- PRIMARY SLATE SURFACE (Main Top Surface with Folded Corner) -->
    <!--
      Polygon:
      Left: (-120, -10)
      Top: (0, -140)
      Fold start on top-right edge: (72, -98)
      Fold end on right edge: (120, -42)
      Right: (120, -10)
      Bottom: (0, 70)
    -->
    <path d="M -120 -10 
             L 0 -140 
             L 72 -98 
             L 120 -42 
             L 120 -10 
             L 0 70 Z" 
          fill="url(#topSlateGrad)" 
          stroke="#424254" 
          stroke-width="1.2" />

    <!-- APPLE NOTES HEADER ACCENT BAND (Isometric top header across the notebook) -->
    <!-- Runs along the top edge from Left to Fold cut -->
    <path d="M -120 -10 
             L -96 -24 
             L 0 -80 
             L 96 -24 
             L 72 -10 
             L 0 -52 
             L -72 0 Z" 
          fill="url(#appleGoldBand)" 
          opacity="0.95" />

    <!-- Subtle crystalline facet line cutting through the gold header band (Obsidian facet) -->
    <line x1="0" y1="-140" x2="0" y2="70" stroke="url(#crystalGlow)" stroke-width="1.8" stroke-linecap="round" opacity="0.8" />

    <!-- MINIMALIST NOTEPAD RULING LINES (Markdown thought lines) -->
    <!-- Clean, elegant horizontal lines in isometric projection -->
    <g opacity="0.55">
      <!-- Line 1: Short title line / heading indicator -->
      <line x1="-60" y1="-28" x2="10" y2="12" stroke="#A1A1B5" stroke-width="3" stroke-linecap="round" />
      <circle cx="-68" cy="-33" r="3" fill="#FFD60A" />

      <!-- Line 2: Checklist item 1 (Check bubble + text) -->
      <circle cx="-56" cy="-10" r="3.5" fill="none" stroke="#FFD60A" stroke-width="1.5" />
      <line x1="-44" y1="-3" x2="48" y2="50" stroke="#71718A" stroke-width="2.5" stroke-linecap="round" />

      <!-- Line 3: Checklist item 2 -->
      <circle cx="-44" cy="13" r="3.5" fill="#FFD60A" />
      <line x1="-32" y1="20" x2="28" y2="55" stroke="#71718A" stroke-width="2.5" stroke-linecap="round" opacity="0.6" />
    </g>

    <!-- FOLDED CORNER (The iconic Apple Notes tactile fold infused with Obsidian crystal facet) -->
    <!-- Triangle beneath fold (Shadow drop) -->
    <polygon points="72,-98 120,-42 72,-42" fill="#050508" opacity="0.85" />

    <!-- The Folded Flap itself -->
    <polygon points="72,-98 120,-70 120,-42" fill="url(#foldedTop)" stroke="#A0A0B8" stroke-width="0.8" />
    <!-- Golden specular edge on the crease -->
    <line x1="72" y1="-98" x2="120" y2="-42" stroke="url(#appleGoldBand)" stroke-width="2" stroke-linecap="round" />

    <!-- 3. THE 3D FACETED OBSIDIAN STYLUS / CRYSTAL PEN (Floating diagonally beside the notebook) -->
    <g transform="translate(68, 18) rotate(-28)" filter="url(#shardGlow)">
      <!-- Stylus Upper Hex Body -->
      <path d="M -7 -110 L 7 -110 L 9 30 L 0 54 L -9 30 Z" fill="url(#stylusBody)" />
      <!-- Left shaded facet -->
      <path d="M -7 -110 L 0 -110 L 0 54 L -9 30 Z" fill="#2D3748" opacity="0.6" />
      <!-- Center bright facet -->
      <path d="M 0 -110 L 7 -110 L 9 30 L 0 54 Z" fill="#F8FAFC" opacity="0.4" />
      <!-- Gold Nib Tip (Writing Point) -->
      <polygon points="-5,38 5,38 0,58" fill="url(#stylusTip)" />
      <!-- Amber glowing halo ring on the pen barrel (like Apple Pencil band) -->
      <rect x="-8" y="-70" width="16" height="5" rx="1.5" fill="#FFD60A" />
    </g>

  </g>
</svg>'''

svg_file = f"{concepts_dir}/concept_codex_fusion.svg"
png_file = f"{concepts_dir}/concept_codex_fusion.png"

with open(svg_file, "w") as f:
    f.write(svg_code)

subprocess.run(["rsvg-convert", "-w", "1024", "-h", "1024", svg_file, "-o", png_file], check=True)
print(f"Generated {png_file} successfully!")
