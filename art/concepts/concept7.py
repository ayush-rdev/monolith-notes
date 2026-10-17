import subprocess

concepts_dir = "/home/rdev/Monolith/art/concepts"

# Concept 7: The Masterpiece Monolith (Pure Minimalist Luxury)
# A standing obsidian monolith with an origami fold, precision chamfered edge,
# and an ultra-fine vertical golden scribe line.
svg7 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <!-- Background AMOLED Obsidian Gradient -->
    <radialGradient id="bgGrad7" cx="50%" cy="40%" r="70%">
      <stop offset="0%" stop-color="#15151B" />
      <stop offset="60%" stop-color="#08080A" />
      <stop offset="100%" stop-color="#020203" />
    </radialGradient>

    <!-- Squircle Hairline Border -->
    <linearGradient id="sqBorder7" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#323240" />
      <stop offset="50%" stop-color="#181820" />
      <stop offset="100%" stop-color="#0B0B0F" />
    </linearGradient>

    <!-- Monolith Stone Body (Brushed Dark Titanium) -->
    <linearGradient id="stoneBody7" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#282834" />
      <stop offset="50%" stop-color="#1A1A22" />
      <stop offset="100%" stop-color="#101016" />
    </linearGradient>

    <!-- Monolith Precision Specular Rim -->
    <linearGradient id="specularRim7" x1="0%" y1="0%" x2="80%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.95" />
      <stop offset="25%" stop-color="#E5A93C" stop-opacity="0.8" />
      <stop offset="60%" stop-color="#808090" stop-opacity="0.3" />
      <stop offset="100%" stop-color="#202028" stop-opacity="0.05" />
    </linearGradient>

    <!-- Golden Scribe Line -->
    <linearGradient id="goldScribe7" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#FFD60A" />
      <stop offset="100%" stop-color="#FF9F0A" />
    </linearGradient>

    <!-- Fold Chamfer Facet -->
    <linearGradient id="foldFacet7" x1="0%" y1="100%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#14141A" />
      <stop offset="100%" stop-color="#363646" />
    </linearGradient>
  </defs>

  <!-- Deep Obsidian Squircle -->
  <rect x="24" y="24" width="464" height="464" rx="108" fill="url(#bgGrad7)" stroke="url(#sqBorder7)" stroke-width="2.5" />

  <!-- The Monolith (Standing Monument Tablet) -->
  <g transform="translate(162, 112)">
    <!-- Base Tablet Shadow -->
    <path d="M 24 0 L 126 0 L 188 62 L 188 264 C 188 278, 176 288, 162 288 L 26 288 C 12 288, 0 278, 0 264 L 0 24 C 0 10, 10 0, 24 0 Z" 
          fill="url(#stoneBody7)" 
          stroke="url(#specularRim7)" 
          stroke-width="2.5" />

    <!-- Precision Corner Fold (The Note / Document Chamfer) -->
    <path d="M 126 0 L 126 62 L 188 62 Z" fill="url(#foldFacet7)" stroke="url(#specularRim7)" stroke-width="2" />

    <!-- Center Golden Stylus / Vault Scribe -->
    <line x1="94" y1="90" x2="94" y2="230" stroke="url(#goldScribe7)" stroke-width="5" stroke-linecap="round" />
    <circle cx="94" cy="65" r="9" fill="url(#goldScribe7)" />
  </g>
</svg>"""

with open(f"{concepts_dir}/concept7.svg", "w") as f:
    f.write(svg7)

subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", f"{concepts_dir}/concept7.svg", "-o", f"{concepts_dir}/concept7.png"], check=True)
print("Concept 7 generated!")
