# FindMe para Android

Aplicación Kotlin con Jetpack Compose para consultar la actividad de una persona vinculada mediante un código. Esta versión concentra la interfaz en la persona que monitorea.

Versión actual: **1.2.1** (`versionCode = 3`).

## Flujo de trabajo

`main` exige un pull request para incorporar cambios. El ruleset activo no tiene actores con bypass y bloquea force-push y borrado de la rama. No exige aprobaciones externas para permitir trabajar en un repositorio personal; las conversaciones de revisión deben estar resueltas. La configuración está documentada en `.github/main-ruleset.json`; ese archivo por sí solo no aplica reglas, que se administran en GitHub.

## Estructura

- `MainActivity`: vinculación, permisos y aviso de actualización.
- `MainViewModel`: estado actual, horario semanal, sincronización y preferencias.
- `StatusRepository`: consulta de perfiles y estados en Supabase; cálculo de la actividad en curso.
- `ScheduleRepository`: descarga de `schedule` y caché local Room. Una descarga correcta reemplaza el horario de la persona en una transacción; una descarga fallida conserva la caché.
- `UserPreferencesRepository`: vínculo y preferencias de notificaciones con DataStore.
- `ScheduleNotifier` / `ClassStartWorker`: avisos de inicio mediante WorkManager.

## Interfaz

**Hoy** utiliza la fecha, hora y zona horaria del dispositivo. Es independiente del día seleccionado en **Horario**, que permite consultar los siete días de la semana. Los días vacíos no generan actividades de ejemplo. Una actividad programada no confirma la ubicación física de la persona.

El estado principal prioriza la actividad cuyo intervalo contiene la hora actual (inicio incluido, fin excluido). Por ejemplo, el jueves a las 17:00 una actividad de 09:00 a 18:00 aparece en curso aunque exista un aviso manual de viaje. Los avisos manuales vigentes se muestran cuando no hay una actividad en curso. El cálculo se repite cada 15 segundos independientemente de los eventos de Realtime; las horas con fracciones de segundo también son válidas.

**Perfil** incluye datos del vínculo, ajustes independientes para avisos de inicio y cambios de estado, acceso a permisos de Android, actualizaciones y desvinculación con confirmación. Las preferencias afectan este dispositivo.

## Actualizaciones desde GitHub

Al iniciar una instancia de la app, se consulta `https://api.github.com/repos/JhonTMich/FindMe-Android/releases/latest`, en segundo plano y con tiempos de espera. También se puede comprobar manualmente desde Perfil. Una actualización disponible muestra un único aviso breve con el botón Actualizar, sin notas de parche ni enlaces al repositorio. Al aceptarlo, la app descarga el APK y muestra el progreso en ese mismo aviso. Antes de abrir el instalador de Android comprueba el tamaño, el SHA-256 publicado por GitHub cuando está disponible, el identificador del paquete, la versión y la firma del APK.

La primera vez Android puede pedir autorizar a FindMe para instalar aplicaciones; al regresar se continúa con el APK ya descargado. La instalación requiere la confirmación que Android solicita. Si la app está en segundo plano, espera a que vuelva a estar visible para abrir el instalador. No requiere permisos generales de almacenamiento y solo comparte el APK mediante un FileProvider limitado a su carpeta de actualizaciones. Una descarga interrumpida se puede reintentar.

Las instalaciones de la versión 1.2 todavía contienen el flujo anterior. Para habilitar este nuevo actualizador hay que instalar esta corrección una primera vez; las versiones siguientes podrán descargarse desde la app.

Para distribuir una nueva versión:

1. Incrementar `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Compilar y firmar el APK con la misma clave de las versiones anteriores.
3. Publicar un release estable y público con una etiqueta numérica como `v1.2` y adjuntar el APK.

El APK de la primera publicación 1.2 se firma con el certificado local de desarrollo ya usado en los APK de prueba, para conservar la compatibilidad de actualización. La clave privada no se incorpora al repositorio. Para una distribución de producción se debe definir y custodiar una clave de firma apropiada.

La etiqueta debe corresponder al `versionName` del APK. Se comparan componentes numéricos: `1.10.0` es posterior a `1.9.0`; `1.0` y `1.0.0` son equivalentes. Los borradores y prereleases no se anuncian. Sin releases públicos o con el repositorio privado, la comprobación sin credenciales no puede obtener una actualización. No se incluyen tokens de GitHub en el APK.

Referencia: [API de releases de GitHub](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).

## Verificación

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Las pruebas de dispositivo requieren un emulador o teléfono conectado. Incluyen ajustes del perfil, confirmación de desvinculación, presentación del estado y selector semanal. Las pruebas unitarias cubren comparación de versiones y límites de las actividades.

## Aspectos pendientes del proyecto

- El monitoreo mutuo y la interfaz de la persona monitoreada quedan para una siguiente etapa.
- Los cambios de estado se observan mientras vive el proceso de la app. Para avisos de esos cambios con la app cerrada se necesita una solución de entrega push.
- WorkManager no garantiza avisos al segundo exacto; los avisos actuales de actividad son trabajos de una ejecución que se programan al cargar/sincronizar el horario.
- El código conserva funciones previas de administrador y widgets. La contraseña local de administrador existente no constituye autorización en el servidor; una futura gestión de roles necesita autenticación y políticas de acceso en Supabase.
