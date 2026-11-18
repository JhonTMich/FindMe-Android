# Notificaciones

Near avisa de tu gente aunque la app esté cerrada (Firebase Cloud Messaging) y te recuerda tus propias
actividades antes de que empiecen (alarmas del teléfono). El envío requiere que Firebase y la función de Supabase estén configurados.

## Qué se notifica

| Aviso | Cuándo | Cómo llega |
|---|---|---|
| Inicio de actividades | Una persona conectada empieza una actividad que comparte contigo | Push (pg_cron cada minuto) |
| Cambios de estado | Alguien comparte, cambia o quita su estado, o este vence | Push (disparador y pg_cron) |
| Mensajes recibidos | Te llega un mensaje rápido | Push (disparador) |
| Antes de tus actividades | 10 min, 30 min o 1 h antes de cada actividad tuya | Alarma local, sin red |

- Cada aviso de tu gente solo llega si esa persona comparte esa información contigo y no la silenciaste.
- Los interruptores de Perfil → Notificaciones se aplican en el teléfono: el servidor envía avisos de
  datos y la app decide si mostrarlos. Con Near en pantalla, la propia app avisa y el push se ignora.
- El recordatorio está apagado por defecto. Llega a la hora exacta si Android permite alarmas exactas;
  si no, puede retrasarse unos minutos (la app ofrece permitirlas).

## Piezas

- `app`: `push/` (token y servicio de FCM), `reminders/` (alarma del recordatorio), configuración de
  Firebase generada en compilación para el identificador `com.example.near`.
- `supabase/migrations/20261117210000_notificaciones_push.sql`: tabla `push_tokens`, RPC
  `register_push_token`/`unregister_push_token`, audiencia y disparadores.
- `supabase/migrations/20261117210100_programar_avisos_push.sql`: `pg_net`, `pg_cron` y el trabajo
  `near-push-due-events` (cada minuto).
- `supabase/migrations/20261117220000_secreto_avisos_push.sql`: genera en Vault el secreto compartido y
  la URL de la función.
- `supabase/functions/send-push`: firma con la cuenta de servicio de Firebase y envía por la API v1 de FCM.

## Despliegue (una vez)

El proyecto FindMeDB ya tiene las tres migraciones de push. Despliega la versión de `send-push` de esta rama antes de publicar el APK, porque el cliente valida el destinatario que incluye cada aviso.

Usa el registro existente de `com.example.near` en el proyecto Firebase `near-c8d8c`. Descarga su
`google-services.json` y guárdalo en `app/google-services.json` para compilar localmente. En GitHub,
configura el secreto `NEAR_FIREBASE_CONFIG` con el contenido completo de ese JSON. Gradle busca
la entrada de `com.example.near`.
Una compilación sin esa configuración conserva los recordatorios locales y los avisos en primer plano.

1. **Migraciones**: `npx supabase db push --remote --skip-vault` para aplicar las migraciones pendientes del lanzamiento.
2. **Función**: `npx supabase functions deploy send-push --remote=ymqsgxmpqushzghkoncg --use-api`
   (`verify_jwt = false` viene de `supabase/config.toml`).
3. **Secreto compartido**: lo genera la propia base de datos en Vault (`near_push_secret`, junto con la
   URL `near_push_url`); la función lo lee con la clave de servicio mediante `push_webhook_secret()`.
   No hay que crearlo ni copiarlo. `PUSH_WEBHOOK_SECRET` es opcional y solo lo reemplazaría.
4. **Clave de Firebase**: en Supabase → Edge Functions → Secrets, crear `FCM_SERVICE_ACCOUNT` con el JSON
   completo de la cuenta de servicio (Firebase → Configuración del proyecto → Cuentas de servicio →
   Generar nueva clave privada). **Nunca** se guarda en el repositorio.

Mientras falte la clave de Firebase, la función responde 503 y no se envía nada; nada falla en la app.

## Límites conocidos

- El proyecto gratuito de Supabase se pausa tras una semana sin uso; mientras esté pausado no hay avisos.
- En Xiaomi/POCO conviene activar *Inicio automático* para Near y quitarle el ahorro de batería.
- Si el cron de un minuto se salta una ejecución, ese inicio de actividad no se notifica.
- El workflow valida `com.example.near` en los PR y en el APK firmado de cada lanzamiento.

## Verificación del primer APK con el nuevo paquete

Instala el APK con `com.example.near` desde GitHub e inicia sesión con tu cuenta. Comprueba los horarios
y contactos sincronizados, elige tus ajustes de avisos y vuelve a añadir tus widgets. Cuando termine
la transición, verifica las siguientes actualizaciones instalando un APK firmado sobre otro con el
mismo paquete `com.example.near`.

Con dos cuentas vinculadas en dispositivos con Google Play, comprueba un mensaje y un cambio de
estado en segundo plano. Abre Near y confirma que el mismo aviso no vuelve a sonar. Prueba el
silencio, la desvinculación y que un recordatorio abra Horario. Repite el recordatorio con y sin
el permiso de alarmas exactas.
