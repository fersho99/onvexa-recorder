# Implementacion de telefono y reloj

## Funciones implementadas

- Grabacion real en ambos dispositivos: servicio foreground de microfono, permiso Android, aviso previo, pausa/reanudacion, niveles de amplitud, marcadores y notas. Guarda AAC mono 16 kHz/48 kbps en M4A; detiene cada archivo a 48 MiB. No reinicia el microfono tras una interrupcion. Los archivos incompletos quedan conservados y marcados como fallidos.
- Reproduccion real con MediaPlayer, posicion, velocidad y descarga privada con comprobacion SHA-256 del audio.
- Transcripcion Vosk local, sin clave ni costo por minuto, en espanol e ingles. Ajustes descarga modelos por Wi-Fi una sola vez y verifica SHA-256 antes de extraerlos. La cola respeta el ajuste de cargar solamente mientras se carga la bateria.
- Agrupacion local de hablantes con embeddings de Vosk. Es aproximada: las intervenciones cortas pueden quedar sin hablante y no separa conversaciones superpuestas. Permite corregir nombres. El vocabulario conserva la escritura de terminos reconocidos coincidentes; no garantiza reconocimiento de palabras nuevas.
- Data Layer de Wear OS: transfiere audio y metadatos como Assets, verifica tamano/hash y confirma recepcion persistida. La copia local del reloj se conserva. Devuelve resumen y tareas; las tareas completadas en el reloj vuelven al telefono. Reintenta al reconectar y mediante WorkManager.
- Fotos desde camara o selector, reduccion y orientacion EXIF, marca de tiempo, vista previa local/remota y descripcion con IA previa autorizacion.
- Resumen, mapa, preguntas y borrador de correo mediante Gemini. El borrador es editable y abre la app de correo, que requiere la accion final del usuario. El procesamiento automatico de resumen/mapa es opcional y apagado inicialmente.
- Sincronizacion autenticada con consentimiento, archivos privados, exportacion y eliminacion de cuenta. No sube un archivo mientras el microfono lo esta escribiendo.
- Purga diaria de datos borrados hace mas de 30 dias. Borra primero los objetos mediante Storage API y conserva IDs minimos privados para rechazar resurrecciones de clientes antiguos. El cliente relee estos IDs en cada sincronizacion antes de subir filas.

## Instalacion y primera prueba

1. Instalar el APK de telefono y el de reloj; deben compartir applicationId y firma. Emparejar Wear OS con el telefono e instalar Google Play Services compatible.
2. En el telefono, Ajustes > Transcripcion: descargar Espanol o English y esperar con Wi-Fi. El modelo de hablantes se incluye en la descarga. Los archivos comprimidos ocupan aproximadamente 55–65 MB y los modelos necesitan espacio/RAM adicionales al extraerse y ejecutarse.
3. Iniciar sesion y habilitar sincronizacion si se desea nube/IA. No hace falta iniciar sesion para grabar y transcribir localmente. La IA automatica se activa por separado.
4. Grabar una frase, pausar, reanudar, agregar marcador y detener. Abrir detalle para reproducir y revisar transcripcion. Con modelos disponibles se procesa al guardar; Transcribir permite repetirlo.
5. Repetir desde el reloj, desconectarlo durante una grabacion y reconectar. Comprobar que el archivo llega, que el resumen vuelve y que una tarea marcada en el reloj aparece completada en el telefono.

Con depuracion USB/inalambrica autorizada, `scripts/install-devices.ps1 -PhoneSerial SERIAL -WatchSerial SERIAL_RELOJ` instala y abre los APK ya compilados sin desinstalar ni borrar datos. Usa `adb devices` para consultar los identificadores. No habia dispositivos conectados para ejecutarlo durante esta sesion.

## Modelos y licencias

Motor: Vosk Android 0.3.75, licencia Apache 2.0. Repositorio y licencia: https://github.com/alphacep/vosk-api/blob/master/COPYING . Modelos oficiales Apache 2.0: https://alphacephei.com/vosk/models . No se incluyen en Git ni se descargan automaticamente al abrir la app.

| Modelo | SHA-256 del ZIP |
|---|---|
| vosk-model-small-es-0.42 | 09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f |
| vosk-model-small-en-us-0.15 | 30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498 |
| vosk-model-spk-0.4 | a74d8f51144484813e16af689bb0f916b7a111e2347f467c4933c1166097b5a7 |

JNA se distribuye bajo LGPL 2.1 o Apache 2.0 a eleccion: https://github.com/java-native-access/jna/blob/master/LICENSE .

## Limites comprobados

La compilacion y las pruebas locales no demuestran calidad del microfono, exactitud de transcripcion, consumo de bateria ni transferencia Bluetooth real. No habia dispositivos conectados por ADB durante el desarrollo. Estas pruebas requieren telefono y reloj reales. Los APK debug utilizan firma de desarrollo; la firma de distribucion requiere una identidad de firma del propietario.

El proyecto Google debe permanecer en su nivel gratuito para evitar facturacion; la cuota del proveedor puede producir errores 429. Gemini no usa la suscripcion ChatGPT Plus.
