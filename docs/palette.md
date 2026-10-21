# Paleta onvexa_recorder — Nebula

Colores de marca (Onvexa Nebula): `#1A0F3D` índigo profundo, `#6A11F2` púrpura vibrante,
`#8B5CF6` violeta nebula, `#EC4899` rosa energético, `#E9D5FF` lavanda suave.
Los marcados como "derivado" se calcularon a partir de ellos para el modo oscuro tipo HUD.
Contraste según WCAG 2.x.

| Token | Hex | Origen | Uso |
|---|---|---|---|
| `background` | `#0E0724` | derivado | Fondo (índigo casi negro) |
| `surface` | `#1A0F3D` | marca | Tarjetas |
| `surfaceHigh` | `#251757` | derivado | Tarjetas elevadas, hojas, diálogos |
| `primary` | `#6A11F2` | marca | Rellenos y bordes |
| `onPrimary` | `#FFFFFF` | — | Texto sobre `primary` |
| `primaryLight` | `#A78BFA` | derivado | Texto, íconos y glow sobre fondo oscuro |
| `secondary` | `#8B5CF6` | marca | Gráficos, ramas del mapa mental (no texto pequeño) |
| `recording` | `#EC4899` | marca | Botón grabar, pausa, estado activo |
| `onRecording` | `#0E0724` | derivado | Texto/ícono sobre `recording` |
| `text` | `#E9D5FF` | marca | Texto principal |
| `textMuted` | `#B4A5D6` | derivado | Texto secundario |
| `accent` | `#F5A623` | funcional | Bookmarks, resaltados, avisos (sin equivalente cálido en la marca) |

Degradado de marca para onda de audio y glow: `#1A0F3D` → `#6A11F2` → `#EC4899`.

## Contraste

| Combinación | Ratio | AA texto |
|---|---|---|
| `text` / `background` | 14.4 | sí |
| `text` / `surface` | 13.1 | sí |
| `text` / `surfaceHigh` | 11.5 | sí |
| `textMuted` / `surface` | 7.9 | sí |
| `primaryLight` / `surface` | 6.5 | sí |
| `primaryLight` / `surfaceHigh` | 5.8 | sí |
| `onPrimary` / `primary` | 7.1 | sí |
| `recording` / `surface` | 5.1 | sí |
| `onRecording` / `recording` | 5.5 | sí |
| `accent` / `surface` | 8.8 | sí |
| `secondary` / `surface` | 4.2 | solo gráficos e íconos |
| `primary` / `surface` | 2.5 | no: solo relleno |
| blanco / `recording` | 3.5 | no: por eso `onRecording` es oscuro |
