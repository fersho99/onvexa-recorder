# Backend: implementacion y estado

Rama local `codex/supabase-backend`, basada en `origin/develop`. Proyecto Supabase `onvexa-recorder`, referencia `uvjkebvnqyuoyaypynez`. Actualizado el 24 de octubre de 2026, hora de Hermosillo.

## Implementado y desplegado

- Esquema inicial, migracion 0004 de integridad/seguridad, cuotas atomicas, cache privada e idempotencia. Las 21 tablas publicas tienen RLS. Las relaciones verifican que ambos extremos pertenezcan al usuario.
- Auth por correo y contrasena. Sesion Android cifrada con Keystore y renovacion de tokens. La biblioteca se vincula a una cuenta: cambiar a otra exige exportar y vaciar primero los datos locales.
- Sincronizacion de 15 tablas locales con PostgREST y WorkManager. Solo se habilita tras consentimiento. Wi-Fi por defecto; las grabaciones abiertas y los ejemplos no se suben. ACK compara la version enviada para conservar ediciones hechas durante la peticion.
- Paginacion compuesta por updated_at y PK, preservando precision remota. Ventana solapada de cinco minutos; una transaccion que tarde mas requiere resincronizacion completa. No se presenta como CDC con orden de commit garantizado.
- Audio e imagenes privados, subidas TUS reanudables y descarga autenticada. Audio con verificacion SHA-256. Limites Storage: 50 MiB audio y 25 MiB adjuntos; la grabadora limita archivos a 48 MiB.
- Exportacion ZIP de metadatos y archivos locales; no descarga toda la nube al exportar. Eliminacion de cuenta bloquea escrituras, elimina Storage recursivamente y finalmente Auth/filas relacionadas. Un fallo permite reintentar.
- Funcion `ai`: resumen y tareas, mapa, preguntas, vision y correo. Usa Gemini generateContent; modelo por variable `LLM_MODEL`, predeterminado `gemini-3.5-flash-lite`. No depende de ChatGPT Plus. Aplica JWT, pertenencia, limites de contexto/imagen, validacion JSON/citas, cuotas e idempotencia.
- IA automatica opcional apagada por defecto. Fotos y borradores tienen interfaz. El correo requiere revisar el borrador y enviarlo desde la app de correo.
- Funcion `maintenance` con autenticacion administrativa propia: service_role o token aleatorio de cron guardado en esquema privado. No admite sesiones normales. El token y la clave Gemini nunca se incluyen en APK/Git.
- Retencion de 30 dias para datos borrados. Primero elimina archivos mediante Storage API; solo tras exito elimina filas. Conserva IDs minimos privados con RLS logico por usuario para impedir resurrecciones. El cliente relee estos IDs antes de sincronizar. Una cuenta eliminada tambien elimina sus marcadores.
- Cron activo: cache expirada cada hora, metricas mayores de 90 dias diariamente y retencion diariamente a las 04:37 UTC.

La grabacion, transcripcion local y enlace de reloj se describen en [device-implementation.md](device-implementation.md).

## Configuracion y despliegue reproducible

`local.properties` esta ignorado por Git y contiene sdk.dir, SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY. Gradle admite tambien las dos variables publicas en el entorno. Solo el telefono contiene esa configuracion; el reloj usa Data Layer.

En Edge Function Secrets: `LLM_API_KEY`, opcionalmente `LLM_MODEL` y `AI_DAILY_LIMIT` (20 por defecto). Las variables SUPABASE_URL/ANON_KEY/SERVICE_ROLE_KEY provienen del entorno del servidor. No usar claves administrativas en Android.

Para instalaciones nuevas, aplicar 0001–0004 y las migraciones con fecha en orden. Desplegar ai, delete-account y maintenance con `supabase/deploy-functions.ps1`. Programar `supabase/ops/maintenance.sql` y `supabase/ops/retention_schedule.sql`; adaptar la URL del segundo archivo si cambia el proyecto.

Este proyecto ya esta desplegado: no repetir la instalacion inicial. Las migraciones originales se aplicaron manualmente; antes de adoptar `supabase db push`, conciliar el historial mediante `supabase migration repair`. Los nombres remotos incluyen backend_hardening, ai_cache_maintenance, retention_cleanup, retention_schedule y restrict_tombstone_rpc. Los archivos locales representan esos cambios aunque el timestamp remoto de aplicacion difiera.

## Verificacion realizada

- APK debug del telefono y reloj compilados con JDK 17/Android SDK 35. No se instalaron en hardware durante esta sesion.
- Android Lint aprobado sin errores en ambas apps; quedan advertencias de versiones, recursos y estilo. Se verificaron firmas validas e identicas de los dos APK.
- 36 pruebas JVM/Android: modelo, busqueda, sincronizacion, purga local, proteccion de grabacion activa, reloj de captura, PCM y agrupacion de hablantes.
- 7 pruebas Deno y revision de tipos de las tres funciones. Incluye credenciales falsas y no finalizar purga si falla Storage.
- Dos rutas SQL probadas con Postgres embebido: migraciones desde cero e instalador inicial seguido de actualizaciones. Incluye aislamiento, FK, busqueda, cuotas, cascadas, retencion y rechazo de resurreccion.
- Prueba RLS sobre las dos cuentas reales del proyecto, dentro de una transaccion revertida: cada cuenta ve solo su grabacion de prueba, la referencia cruzada se rechaza y un usuario no puede ejecutar mantenimiento. Script: `supabase/ops/verify_rls.sql`.
- Gemini probado en el servidor con un texto ficticio y una cuenta temporal creada/eliminada para la prueba. Respuesta HTTP 200 y resumen correcto. Se confirmo que no quedaron cuentas de diagnostico.
- Cron de retencion invocado correctamente: HTTP 200, cero datos elegibles y cero fallos. Las llamadas sin credenciales reciben 401. No se borraron grabaciones reales.

Comandos locales:

```powershell
.\gradlew.bat :app:assembleDebug :wear:assembleDebug :app:testDebugUnitTest :core:audio:testDebugUnitTest :core:database:testDebugUnitTest :core:model:test
npm.cmd --prefix supabase/tests test
```

Desde `supabase/functions`: `npx.cmd --yes deno task check` y `npx.cmd --yes deno task test`.

## Lo que requiere entorno real

Microfono, bateria, calidad ASR, camara y transferencia Bluetooth deben probarse en los dispositivos emparejados. Falta verificar una subida TUS interrumpida sobre red real y una migracion Room sobre datos reales anteriores. Las pruebas automatizadas no sustituyen esas comprobaciones.

La configuracion existente de correo/confirmacion/SMTP y redirects se conserva. La conexion administrativa disponible no expone cambios de Auth ni configuracion de facturacion. El asesor de Supabase aun informa [proteccion frente a contrasenas filtradas desactivada](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection). Las tablas account_deletions y ai_response_cache carecen intencionalmente de politicas de cliente: son exclusivas del servidor.

No se configuro un destino externo de respaldos ni alertas comerciales. `supabase/ops/backup.ps1` prepara un respaldo Postgres con credenciales del entorno; requiere pg_dump y acceso administrativo proporcionado por el propietario. No incluye objetos de Storage: la exportacion de la app incluye los archivos disponibles localmente.
