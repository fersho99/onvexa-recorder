# onvexa_recorder

Grabadora con IA para Galaxy Watch 8 (Wear OS) y Android. Graba conversaciones desde el reloj o el celular, transcribe en el dispositivo y genera resumen, puntos de acción y mapa mental.

## Estado

Fase 0 completa: base del proyecto, datos, diseño y esqueletos de ambas apps.
Siguiente: Fase 1 (grabación en el reloj, sincronización, transcripción y resumen).

## Requisitos

- Android Studio reciente (Ladybug o posterior).
- **JDK 21** (o 17) para Gradle. Con otras versiones, como la 25, el build falla.
  - Android Studio: Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK = 21 (el JDK embebido sirve).
  - Terminal: `JAVA_HOME` apuntando al JDK 21 antes de `./gradlew`.
- Android SDK 35 (se instala desde Android Studio).
- Gradle 8.9: lo descarga el wrapper (`./gradlew`).

## Compilar y probar

```bash
./gradlew :core:database:test :core:design:test   # pruebas unitarias
./gradlew :app:assembleDebug :wear:assembleDebug  # compilar ambas apps
```

Ambas apps comparten `applicationId` (`com.onvexa.recorder`) y deben firmarse con la misma llave,
requisito de la Wearable Data Layer. En desarrollo basta con la llave debug de tu equipo.

## Estructura

| Módulo | Contenido |
|---|---|
| `:app` | App del celular (Compose, Hilt) |
| `:wear` | App del reloj (Wear Compose, Hilt), funciona de forma autónoma |
| `:core:design` | Tokens Nebula, temas M3 y Wear, componentes base |
| `:core:database` | Room (entidades, DAOs, búsqueda FTS4) y módulo Hilt |
| `supabase/migrations` | Esquema SQL, RLS y Storage |
| `docs` | Diagrama ER y paleta de colores |

## Ramas

- `main`: estable.
- `develop`: trabajo en curso.
