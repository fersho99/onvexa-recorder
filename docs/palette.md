# Paleta onvexa_recorder (propuesta)

Base de marca Onvexa: navy `#1B2A3D` y teal `#0F6B5C` (monograma O+N).
El resto se deriva de esos dos para el estilo oscuro tipo HUD. Contraste calculado con WCAG 2.x.

| Token | Hex | Uso |
|---|---|---|
| `background` | `#0B121B` | Fondo (navy muy oscuro) |
| `surface` | `#1B2A3D` | Tarjetas (navy de marca) |
| `surfaceHigh` | `#243649` | Tarjetas elevadas, hojas, diálogos |
| `primary` | `#0F6B5C` | Rellenos y bordes (teal de marca) |
| `onPrimary` | `#FFFFFF` | Texto sobre `primary` |
| `primaryLight` | `#3FD1B5` | Texto, íconos, onda y glow sobre fondo oscuro |
| `secondary` | `#7C9CC0` | Acentos fríos, ramas del mapa mental |
| `text` | `#E8EEF4` | Texto principal |
| `textMuted` | `#9FB1C4` | Texto secundario |
| `accent` | `#F5A623` | Bookmarks, resaltados, avisos |
| `recording` | `#FF4D1A` | Botón grabar, pausa, estado activo |
| `onRecording` | `#0B121B` | Texto/ícono sobre `recording` |

## Contraste

| Combinación | Ratio | AA |
|---|---|---|
| `text` / `background` | 16.1 | sí |
| `text` / `surface` | 12.4 | sí |
| `textMuted` / `surface` | 6.6 | sí |
| `primaryLight` / `surface` | 7.6 | sí |
| `accent` / `surface` | 7.2 | sí |
| `secondary` / `surface` | 5.1 | sí |
| `onPrimary` / `primary` | 6.4 | sí |
| `onRecording` / `recording` | 5.7 | sí |
| `recording` / `background` | 5.7 | sí |
| `primary` / `surface` | 2.3 | no: solo relleno, nunca texto |
| `recording` / `surface` | 4.4 | solo íconos y relleno, no texto |
| blanco / `recording` | 3.3 | no: por eso `onRecording` es oscuro |
