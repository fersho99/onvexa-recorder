# Paleta onvexa_recorder

Interfaz oscura sobria: grafito casi neutro, violeta Onvexa (`#6A11F2`) solo como acento y colores con significado
para estados, origen y ramas. Sin degradados ni brillos. Los valores viven en
`core/design/.../PaletteHex.kt` y `ContrastTest` valida el contraste (WCAG 2.x) en cada corrida de pruebas.

| Token | Hex | Uso |
|---|---|---|
| `background` | `#0F1115` | Fondo |
| `surface` | `#171A21` | Tarjetas, barra inferior |
| `surfaceHigh` | `#1F232C` | Campos, etiquetas, elementos elevados |
| `outline` | `#2A2F3A` | Bordes finos y divisores |
| `outlineStrong` | `#3A4150` | Bordes de campos sin foco |
| `text` | `#ECEEF3` | Texto principal |
| `textMuted` | `#9AA3B2` | Texto secundario |
| `primary` | `#6A11F2` | Botones rellenos (texto blanco) |
| `primaryLight` | `#A99BFF` | Íconos, enlaces y selección sobre fondo oscuro |
| `primaryContainer` | `#241E3F` | Fondo de elementos seleccionados |
| `recording` | `#DC2626` | Botón de grabar (ícono blanco) |
| `recordingText` | `#F87171` | Texto o íconos de "grabando" |
| `success` / `warning` / `info` / `error` | `#34C58A` / `#E8B04B` / `#4DA3FF` / `#FF8A75` | Estados y marcas del reproductor |
| `watch` / `phone` | `#2DD4BF` / `#60A5FA` | Origen de la grabación |

Ramas del mapa mental y hablantes: `#8B7CF6`, `#2DD4BF`, `#F5B84B`, `#5AA9FF`, `#F472B6`, `#4ADE80`.

Tipografía: Inter (licencia en `docs/licenses/inter-OFL.txt`).
