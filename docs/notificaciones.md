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
- `supabase/functions/send-push`: firma con la cuenta de servicio de Firebase y envía por la API v1 de FCM.

## Despliegue (una vez)

1. **Migraciones**: aplicar las dos migraciones en orden (SQL Editor o `supabase db push`).
2. **Función**: desplegar `send-push` con `verify_jwt = false` (ya está en `supabase/config.toml`):
   `npx supabase functions deploy send-push --project-ref ymqsgxmpqushzghkoncg`.
3. **Secreto compartido**: generar uno, por ejemplo
   `python -c "import secrets; print(secrets.token_urlsafe(32))"`.
4. **Secretos de la función** (Edge Functions → Secrets):
   - `PUSH_WEBHOOK_SECRET`: el secreto del paso 3.
   - `FCM_SERVICE_ACCOUNT`: el JSON completo de la cuenta de servicio de Firebase
     (Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada).
     **Nunca** se guarda en el repositorio.
5. **Vault** (SQL Editor), con el mismo secreto del paso 3:
   ```sql
   select vault.create_secret('https://ymqsgxmpqushzghkoncg.supabase.co/functions/v1/send-push', 'near_push_url');
   select vault.create_secret('<secreto del paso 3>', 'near_push_secret');
   ```

Mientras falte cualquiera de los dos secretos de Vault, la base de datos no envía nada (no falla).

## Límites conocidos

- El proyecto gratuito de Supabase se pausa tras una semana sin uso; mientras esté pausado no hay avisos.
- En Xiaomi/POCO conviene activar *Inicio automático* para Near y quitarle el ahorro de batería.
- Si el cron de un minuto se salta una ejecución, ese inicio de actividad no se notifica.
- El cambio de paquete a `com.example.near` instala Near como app nueva: hay que iniciar sesión otra
  vez y volver a agregar los widgets; la versión anterior (`com.example.findme`) se puede desinstalar.
