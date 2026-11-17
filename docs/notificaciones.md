# Notificaciones

Near avisa de tu gente aunque la app esté cerrada (Firebase Cloud Messaging) y te recuerda tus propias
actividades antes de que empiecen (alarmas del teléfono). Todo funciona con los planes gratuitos de
Firebase (Spark) y Supabase.

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
  Firebase en `res/values/firebase.xml` (proyecto `near-c8d8c`, paquete `com.example.near`).
- `supabase/migrations/20261117210000_notificaciones_push.sql`: tabla `push_tokens`, RPC
  `register_push_token`/`unregister_push_token`, audiencia y disparadores.
- `supabase/migrations/20261117210100_programar_avisos_push.sql`: `pg_net`, `pg_cron` y el trabajo
  `near-push-due-events` (cada minuto).
- `supabase/migrations/20261117220000_secreto_avisos_push.sql`: genera en Vault el secreto compartido y
  la URL de la función.
- `supabase/functions/send-push`: firma con la cuenta de servicio de Firebase y envía por la API v1 de FCM.

## Despliegue (una vez)

Ya desplegado en el proyecto `ymqsgxmpqushzghkoncg` (FindMeDB) el 17 nov 2026, salvo la clave de Firebase.

1. **Migraciones**: `npx supabase db push --remote` (aplica `..._notificaciones_push.sql`,
   `..._programar_avisos_push.sql` y `..._secreto_avisos_push.sql`).
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
- El cambio de paquete a `com.example.near` instala Near como app nueva: hay que iniciar sesión otra
  vez y volver a agregar los widgets; la versión anterior (`com.example.findme`) se puede desinstalar.
