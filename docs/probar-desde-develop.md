# Probar desde develop

El backend compartido ya esta desplegado. Para probar Android no hay que crear otro proyecto Supabase, aplicar SQL ni desplegar funciones.

## Configuracion del equipo

1. Instalar Android Studio, Android SDK 35 y configurar Gradle con JDK 17.
2. Clonar la rama develop y abrir la carpeta raiz en Android Studio.
3. Copiar el archivo local.properties recibido a la raiz, junto a settings.gradle.kts. Contiene solo SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY. Android Studio configura sdk.dir con la ubicacion del SDK de ese equipo. Si ya existe local.properties, conservar su sdk.dir y agregar las dos propiedades recibidas.
4. Sincronizar Gradle y compilar:

```powershell
.\gradlew.bat :app:assembleDebug :wear:assembleDebug
```

La app no carga archivos .env automaticamente. Para compilar por terminal tambien se pueden definir SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY como variables de entorno; sdk.dir o ANDROID_HOME sigue siendo necesario.

## Prueba

- Instalar ambas apps compiladas en el mismo equipo: telefono y reloj deben tener la misma firma de desarrollo y applicationId. No mezclar un APK compilado por Jorge con otro compilado por otra persona.
- Crear una cuenta desde la app con un correo propio y confirmar el correo si se solicita. Tambien se puede usar una cuenta de prueba creada especificamente para el amigo.
- En Ajustes descargar el modelo Espanol o English por Wi-Fi. Grabar y transcribir localmente no requiere cuenta.
- Para probar nube/IA, iniciar sesion y aceptar sincronizacion. Gemini ya esta configurado en el servidor: no se necesita una clave propia de Gemini.
- Los datos de cada cuenta estan aislados: una cuenta nueva no vera las grabaciones de Jorge.
- Consultar device-implementation.md para el recorrido de grabacion, fotos, transcripcion, IA y reloj.

## Archivos y secretos

El repositorio contiene codigo, migraciones, ejemplos de configuracion y guias. local.properties queda fuera de Git. Para probar el backend existente basta la configuracion publica compartida y una cuenta de usuario de la app.

La contrasena de Postgres, claves service_role/sb_secret, clave Gemini y tokens administrativos permanecen en el servidor o en el equipo del propietario. No forman parte de la entrega para pruebas.
