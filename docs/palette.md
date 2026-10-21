# Paleta onvexa_recorder — Nebula

Colores de marca (Onvexa Nebula): `#1A0F3D` índigo profundo, `#6A11F2` púrpura vibrante,
`#8B5CF6` violeta nebula, `#EC4899` rosa energético, `#E9D5FF` lavanda suave.
Los marcados como "derivado" se calcularon a partir de ellos para el modo oscuro tipo HUD.
Contraste según WCAG 2.x. Los valores viven en `core/design/.../PaletteHex.kt` y
`ContrastTest` los valida en cada corrida de pruebas.

| Token | Hex | Origen | Uso |
|---|---|---|---|
| `background` | `#120A2E` | derivado | Fondo (índigo morado oscuro) |
| `surface` | `#1A0F3D` | marca | Tarjetas |
| `surfaceHigh` | `#2A1A63` | derivado | Tarjetas elevadas, hojas, diálogos |
| `outline` | `#4A3A8C` | derivado | Bordes decorativos de tarjetas |
| `primary` | `#6A11F2` | marca | Rellenos y bordes |
| `onPrimary` | `#FFFFFF` | — | Texto sobre `primary` |
| `primaryLight` | `#A78BFA` | derivado | Texto, íconos y glow sobre fondo oscuro |
| `secondary` | `#8B5CF6` | marca | Gráficos, ramas del mapa mental |
| `onSecondary` | `#0B0620` | derivado | Texto sobre `secondary` |
| `recording` | `#EC4899` | marca | Botón grabar, pausa, estado activo |
| `onRecording` | `#120A2E` | derivado | Texto/ícono sobre `recording` |
| `accent` | `#F0ABFC` | derivado | Bookmarks, resaltados, avisos (fucsia claro entre violeta y rosa) |
| `text` | `#E9D5FF` | marca | Texto principal |
| `textMuted` | `#B4A5D6` | derivado | Texto secundario |
| `error` | `#FF8A6B` | derivado | Errores (coral, distinto del rosa de grabación) |
| `onError` | `#120A2E` | derivado | Texto sobre `error` |

Degradado de la onda y el glow: `secondary` → `recording`.
Degradado de marca para encabezados: `surface` → `primary` → `recording`.
Ramas del mapa mental: `#8B5CF6`, `#EC4899`, `#A78BFA`, `#F0ABFC`, `#6A11F2`.

## Contraste

| Combinación | Ratio | AA texto |
|---|---|---|
| `text` / `background` | 13.9 | sí |
| `text` / `surface` | 13.1 | sí |
| `text` / `surfaceHigh` | 10.8 | sí |
| `textMuted` / `surface` | 7.9 | sí |
| `textMuted` / `surfaceHigh` | 6.5 | sí |
| `primaryLight` / `surface` | 6.5 | sí |
| `primaryLight` / `surfaceHigh` | 5.4 | sí |
| `accent` / `surface` | 10.1 | sí |
| `accent` / `surfaceHigh` | 8.4 | sí |
| `recording` / `surface` | 5.0 | sí |
| `error` / `surface` | 7.7 | sí |
| `onPrimary` / `primary` | 7.1 | sí |
| `onSecondary` / `secondary` | 4.7 | sí |
| `onRecording` / `recording` | 5.4 | sí |
| `onError` / `error` | 8.2 | sí |
| `secondary` / `background` | 4.5 | gráficos e íconos |
| `secondary` / `surface` | 4.2 | gráficos e íconos |
| `recording` / `surfaceHigh` | 4.2 | gráficos e íconos |
| `primary` / `surface` | 2.5 | no: solo relleno |
| blanco / `recording` | 3.5 | no: por eso `onRecording` es oscuro |

`surface` y `background` casi no se distinguen entre sí (1.1:1), así que las tarjetas se separan
con `outline` y un brillo sutil, no solo con el relleno.
