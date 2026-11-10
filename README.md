# FindMe para Android

Aplicación Kotlin con Jetpack Compose para organizar horarios y compartir actividades y estados entre personas conectadas. El registro requiere un correo autorizado. [Configuración de cuentas y conexiones](docs/cuentas-conexiones.md).

Versión actual: **1.5** (`versionCode = 1005000`).

La versión 1.5 incorpora turnos nocturnos, un widget de estado configurable por persona, la identidad azul del nuevo icono, un acceso más claro y la próxima actividad en Hoy.

## Flujo de trabajo

`main` exige un pull request para incorporar cambios. El ruleset activo no tiene actores con bypass y bloquea force-push y borrado de la rama. No exige aprobaciones externas para permitir trabajar en un repositorio personal; las conversaciones de revisión deben estar resueltas. La configuración está documentada en `.github/main-ruleset.json`; ese archivo por sí solo no aplica reglas, que se administran en GitHub.

## Estructura

- `MainActivity`: entrada al flujo de cuentas, confirmación de correo, permisos y actualizaciones.
- `AccountViewModel` y `AccountRepository`: cuentas, horarios, conexiones, estados y mensajes en Supabase.
- `StatusRepository`: cálculo de la actividad en curso y vigencia del estado manual.
- `AccountSettingsRepository`: preferencias de avisos y tema con DataStore.

## Interfaz

**Hoy** muestra las actividades del día según la zona horaria del perfil. **Horario** permite consultar los siete días de la semana. Un estado manual vigente tiene prioridad sobre la actividad programada y, al vencer, la app vuelve a mostrar el horario.

**Personas** permite compartir horarios mediante conexiones mutuas. **Perfil** incluye ajustes de avisos, tema, permisos de Android y actualizaciones.

## Actualizaciones desde GitHub

Al iniciar una instancia de la app, se consulta `https://api.github.com/repos/JhonTMich/FindMe-Android/releases/latest`, en segundo plano y con tiempos de espera. También se puede comprobar manualmente desde Perfil. Una actualización disponible muestra un único aviso breve con el botón Actualizar, sin notas de parche ni enlaces al repositorio. Al aceptarlo, la app descarga el APK y muestra el progreso en ese mismo aviso. Antes de abrir el instalador de Android comprueba el tamaño, el SHA-256 publicado por GitHub cuando está disponible, el identificador del paquete, la versión y la firma del APK.

La primera vez Android puede pedir autorizar a FindMe para instalar aplicaciones; al regresar se continúa con el APK ya descargado. La instalación requiere la confirmación que Android solicita. Si la app está en segundo plano, espera a que vuelva a estar visible para abrir el instalador. No requiere permisos generales de almacenamiento y solo comparte el APK mediante un FileProvider limitado a su carpeta de actualizaciones. Una descarga interrumpida se puede reintentar.

Las instalaciones de la versión 1.2 todavía contienen el flujo anterior. Para habilitar este nuevo actualizador hay que instalar esta corrección una primera vez; las versiones siguientes podrán descargarse desde la app.

## Publicar desde la terminal

Después de fusionar los cambios en `main`, basta con crear y subir un tag nuevo. No necesitas compilar, descargar ni adjuntar el APK manualmente:

```powershell
git fetch origin
git tag v1.2.3 origin/main
git push origin v1.2.3
```

Usa un número superior al último release publicado. Los tags admiten versiones estables como `v1.2.3` o `v1.3`; no se aceptan sufijos de prerelease. El tag debe señalar un commit que ya esté integrado en `main` y que contenga el workflow.

El workflow `.github/workflows/release.yml` comprueba el tag, instala Java 25 y el SDK `platforms;android-37.0`, ejecuta las pruebas unitarias y lint, compila el APK de release y lo firma. Después publica un release estable marcado como Latest con `FindMe-VERSION.apk` y `SHA256SUMS.txt`. El release lleva únicamente un mensaje breve, sin notas de parche generadas.

La versión del APK sale del tag mediante las propiedades Gradle `releaseVersionName` y `releaseVersionCode`. Por ejemplo, `v1.2.3` produce `versionName = 1.2.3` y `versionCode = 1002003`. Se calcula `major * 1000000 + minor * 1000 + patch` para que las actualizaciones mantengan un código creciente y reproducible. Minor y patch admiten valores hasta 999. Los valores de `defaultConfig` se conservan como respaldo para compilaciones locales sin esas propiedades.

Los cuatro secretos de firma de este repositorio ya están configurados en GitHub Actions:

| Secreto | Contenido |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Archivo de firma codificado en Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del archivo |
| `ANDROID_KEY_ALIAS` | Alias de la clave |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave |

Se utiliza el mismo certificado local de desarrollo de los APK publicados hasta ahora para conservar la compatibilidad de actualización. La clave privada no se incorpora al repositorio ni a los artefactos. Para una distribución de producción se debe definir y custodiar una estrategia de firma apropiada.

Los PR solo ejecutan comprobaciones y compilación sin acceso a los secretos de firma. La firma y publicación están reservadas a tags integrados en `main`. Se puede consultar el progreso en [Actions](https://github.com/JhonTMich/FindMe-Android/actions/workflows/release.yml).

Si falla una ejecución por una incidencia temporal, puedes volver a ejecutarla desde Actions. Los borradores incompletos se completan antes de publicarse; un release ya publicado no se sobrescribe y requiere un tag nuevo.

Referencia: [API de releases de GitHub](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).

## Widget de estado actual

Añade **FindMe · Estado actual** desde el selector de widgets de Android. Al añadirlo, elige **Mi estado** o una persona conectada. Puedes tener varios widgets con personas distintas y cambiar la selección tocando el nombre o **⋮**. El widget anterior «Mi gente» se actualiza al nuevo diseño y conserva su persona seleccionada; los mensajes rápidos se envían desde Personas.

Muestra el estado manual vigente o la actividad del horario, la próxima actividad y la fecha de los últimos datos. El tamaño ampliado añade ubicación y hora de fin. El botón **↻** consulta los datos sin abrir la app. Sin conexión se conserva la última información y se siguen calculando las actividades y caducidades con esos datos.

Los cambios sincronizados o guardados en la app actualizan la caché del widget. Con la app cerrada se consulta periódicamente cada 15 minutos y se programa un recálculo local para el siguiente comienzo, fin o vencimiento conocido. Android puede retrasar estos trabajos por ahorro de batería; no se promete actualización instantánea de cambios remotos con la app cerrada. No se necesitan permisos de alarmas exactas.

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

La versión 1.3 incorpora cuentas con registro por correos autorizados, acceso con usuario y contraseña, horarios propios, conexiones mutuas, ajustes de avisos y colores, y mensajes en vivo con la app abierta. Los mensajes enviados caducan a las 24 horas. La configuración del servidor y las limitaciones actuales están en [cuentas y conexiones](docs/cuentas-conexiones.md).

## Identidad visual

La interfaz usa el nuevo símbolo de FindMe: azul noche, azul acero y blanco suave. Acceso, navegación, estados, categorías, horarios, ajustes y widgets comparten esta paleta sin acentos fluorescentes. En **Perfil → Apariencia** se puede elegir Azul, Hielo o Acero; las preferencias guardadas conservan sus identificadores anteriores para mantener la compatibilidad.

El símbolo de la cabecera reutiliza los trazados del icono adaptativo, sin el margen de seguridad del launcher. La cabecera muestra un único logo. La recuperación del correo de confirmación se ofrece desde el registro y desde la pantalla posterior a crear la cuenta. En **Hoy**, una tarjeta presenta la siguiente actividad que empieza en la fecha y zona del perfil, o «Nada más por hoy», y permite abrir Horario.

La prueba `brandScreensAcrossNavigationAndAppearance` recorre las pantallas y guarda capturas en `Pictures/FindMeTests` del emulador para revisar la presentación.
