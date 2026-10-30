# Cuentas y conexiones — primera implementación

Rama: `feature/cuentas-conexiones`.

## Incluido

- Inicio de sesión con usuario y contraseña mediante la función `username-login`, que valida la contraseña con Supabase Auth. El correo permanece asociado a Auth para altas y recuperación; no se solicita en la pantalla de acceso. Registro desde Android limitado a correos autorizados: cada persona elige su nombre, usuario y contraseña; el administrador solo habilita el correo. La entrega de confirmaciones a usuarios externos requiere configurar SMTP.
- Restauración de la sesión; la contraseña no se guarda en el estado de la pantalla.
- Perfil propio con zona horaria del dispositivo al crearlo. Las cuentas sin nombre o usuario completan su perfil al ingresar; ambos se pueden editar en Cuenta. El usuario es único, ignora mayúsculas y admite de 3 a 24 letras ASCII, números o guion bajo.
- Crear, editar y eliminar actividades semanales, con categoría, modalidad y lugar opcional.
- Estados manuales configurables con prioridad sobre el horario: por ejemplo, «En camino a casa» puede reemplazar una clase todavía programada. Al vencer o quitar el estado vuelve el cálculo por horario, tanto en la app como en el widget. El flujo anterior conserva su regla de prioridad.
- Invitación aleatoria de ocho caracteres sin letras o dígitos ambiguos, presentada como `ABCD-EFGH`, de un solo uso y duración de 24 horas. Generar otra reemplaza la anterior. Se mantienen compatibles las invitaciones antiguas.
- Conexiones mutuas entre cuentas, sin categorías de pareja/familiar ni roles de administrador.
- Aceptar una invitación autoriza explícitamente a ambos a consultar horarios y estados. Ninguno puede modificar los datos del otro.
- Desvinculación con confirmación. Retira el acceso a datos futuros; no puede borrar información que alguien ya haya visto.
- Mensajes rápidos configurables dentro de «Mis mensajes y widget», separados de los estados. Un campo define el mensaje; la etiqueta del botón se deriva automáticamente. Envían mensajes persistidos que aparecen automáticamente con la app abierta y caducan a las 24 horas.
- Widget `FindMe · Mi gente` con tu estado, el de una persona conectada, hora de consulta y un botón de mensaje configurable desde Personas. La selección se comparte entre las instancias del widget en ese dispositivo y se comprueba contra la cuenta activa.
- Perfil y ajustes: avisos de actividades, estados y mensajes por cuenta; acceso a permisos de Android; búsqueda de actualizaciones; acentos índigo, menta y rosa guardados en el dispositivo.
- Acceso separado a la versión anterior desde la pantalla de inicio de sesión.

## Preparar Supabase

El esquema está en `supabase/account-model.sql`. **Aplicado al proyecto personal existente el 29 de octubre de 2026**, mediante la migración remota `crear_cuentas_horarios_y_conexiones`.
El esquema inicial es anterior al uso de la CLI. El 30 de octubre se utilizó la CLI 2.122.0 mediante npm para generar la migración de registro; su archivo se alineó con la versión registrada por el despliegue remoto. No volver a ejecutar el esquema inicial en este proyecto. Antes de usar `db push`, se debe incorporar el resto del historial remoto al flujo de migraciones local.

Comprobaciones remotas después del despliegue: siete tablas nuevas con RLS y políticas; ninguna permite SELECT al rol `anon`; consultar perfiles por la API sin sesión devuelve HTTP 401. Los asesores de seguridad solo señalaron problemas en las tablas anteriores. Se conservaron un perfil, 11 actividades y 26 estados anteriores. En ese despliegue inicial no se crearon cuentas ni se reasignó el horario de Michael.

La comprobación del 30 de octubre confirmó `disable_signup = false` y `mailer_autoconfirm = false`: registro habilitado y confirmación obligatoria. El acceso al registro se controla ahora con la tabla `signup_allowed_emails` y los triggers de Auth descritos más abajo. **No desactivar `Allow new users to sign up` para este flujo**, porque impediría también registrarse a los correos autorizados. Falta configurar SMTP, una URL de confirmación válida y probar la entrega y el registro real con un correo autorizado. Las dos cuentas existentes se conservan sin cambios.

El 30 de octubre se aplicó la migración `acortar_codigos_de_invitacion` (`supabase/upgrades/short-invitations.sql`), que amplía la validación para códigos de ocho caracteres y acepta códigos escritos en minúsculas, con espacios o guiones. No modifica conexiones ni elimina invitaciones existentes.

La interfaz de cuentas recupera el estilo de la app anterior: tarjetas oscuras, acentos índigo, cabecera, iconos, panorama semanal y actividades en tarjetas. Los formularios de estado, actividad, conexión y mensajes se abren en paneles inferiores para mantener las pantallas centradas en consultar el día.

1. Para entornos nuevos, elegir un proyecto de desarrollo de Supabase.
2. En un proyecto nuevo, ejecutar una sola vez el archivo SQL completo. Usa una transacción y falla si las tablas ya existen, evitando sobrescribir datos. En el proyecto personal ya está aplicado.
3. Habilitar el proveedor Email de Auth. Para confirmaciones de cuentas reales, configurar SMTP y las URLs permitidas; alternativamente elegir otro proveedor antes del lanzamiento. El correo integrado de Supabase tiene restricciones de destinatarios y frecuencia.
4. Probar dos cuentas y una tercera sin vínculo: registro, confirmación, inicio, edición de horario, invitación, acceso mutuo, mensaje y revocación.
5. Ejecutar los asesores de seguridad y comprobar las políticas como `authenticated` y `anon`.
6. Cambiar la URL/clave publicable del cliente si se utiliza otro proyecto de desarrollo.

Las tablas nuevas son `account_profiles`, `personal_schedule`, `personal_status`, `person_connections`, `connection_invitations`, `quick_messages` y `connection_signals`. Todas tienen RLS y permisos explícitos. Las invitaciones ajenas no se pueden consultar; una función con privilegios limitados en el esquema privado consume la invitación y crea la conexión de forma atómica. La API solo expone un wrapper `SECURITY INVOKER` para usuarios autenticados.

## Datos anteriores

Las tablas `profiles`, `schedule` y `status_override` del modelo anterior siguen existiendo, pero sus registros se eliminaron por solicitud del propietario durante el reinicio de pruebas del 30 de octubre. Las cuentas anteriores de Auth y sus perfiles, horarios, conexiones, mensajes y sesiones también se eliminaron. Se conservó únicamente la cuenta recién registrada. No se borraron tablas, políticas ni funciones del modelo nuevo.

Las tablas antiguas todavía tienen RLS desactivado; ya no contienen los datos anteriores. Antes de volver a usarlas o publicar la transición, deben retirarse o recibir políticas compatibles. No se reasignan datos por nombre visible ni mediante el código familiar antiguo.

## Siguientes entregas

- Configuración independiente de cada instancia de widget, varios botones y opciones de apariencia. Los dos widgets anteriores siguen siendo del modelo familiar anterior.
- Notificaciones push mediante FCM y una función de servidor, con tokens privados, control de envíos repetidos y preferencias del destinatario. Guardar un mensaje en Supabase todavía no envía una notificación Android.
- Caché persistente por cuenta para consultar sin conexión. La sincronización en primer plano ya usa Realtime y muestra la hora de la última consulta correcta; la caché actual del modelo de cuentas es solo en memoria.
- Recuperación de contraseña en Android. El retorno desde confirmación de registro ya abre la app, pero no inicia sesión automáticamente.
- Apodos por conexión, permisos independientes para horario/estado y silenciamiento.
- Cancelaciones específicas de actividades, actividades nocturnas y reglas de solapamiento.

No publicar esta rama como reemplazo de 1.2.2 hasta desplegar y probar el esquema, resolver la transición de las tablas anteriores y verificar los flujos en un dispositivo.

## Verificación local del esquema

```powershell
npm.cmd install --prefix build/schema-check --no-save --ignore-scripts @electric-sql/pglite@0.3.14
node scripts/test_account_schema.mjs
```

Ejecuta PostgreSQL embebido, sin conexión con Supabase, y crea un sustituto mínimo de `auth.users` y `auth.uid()` para probar RLS con tres usuarios y acceso anónimo. Comprueba 46 casos: aislamiento, edición ajena, consumo/vencimiento de invitaciones, actualización a códigos cortos, mensajes y revocación. No sustituye las pruebas del proveedor de Auth ni de Realtime en el entorno de desarrollo.

`AccountUiTest` comprueba en Android el estado manual durante una actividad, la edición del horario, el envío de mensajes y el flujo de códigos cortos, con capturas de las pantallas y formularios.

## Acceso por nombre de usuario — 30 de octubre de 2026

Aplicada la migración remota `agregar_usuario_para_inicio_de_sesion`, cuyo SQL está en `supabase/upgrades/username-login.sql`. Añade `account_profiles.username` sin recrear usuarios. Conserva las contraseñas, sesiones y conexiones existentes. Para entornos nuevos, ejecutar primero el esquema base, después las dos actualizaciones de `supabase/upgrades/` y finalmente la migración de registro de `supabase/migrations/`.

La Edge Function `username-login` está desplegada. Sus fuentes están en `supabase/functions/username-login/`. Se publica con `verify_jwt = false` porque recibe credenciales antes de existir una sesión y realiza su propia autenticación: resuelve el usuario exclusivamente en el servidor y solicita a Supabase Auth una sesión mediante contraseña. La clave de servicio solo existe en el entorno del servidor. No hay una API pública que traduzca usuarios a correos. Los errores de usuario inexistente y contraseña incorrecta usan el mismo mensaje. La función no registra contraseñas, cuerpos ni tokens y devuelve `Cache-Control: no-store`.

Los límites persistidos permiten ocho intentos por usuario en cinco minutos y 60 intentos globales por minuto. Los contadores están en un esquema privado, con RLS, sin políticas para clientes y con acceso exclusivamente de servicio; el aviso informativo del asesor sobre esa ausencia de políticas es intencional. Los errores del limitador cierran el acceso en lugar de omitir el límite. Esto no reemplaza la protección contra ataques a la API nativa de Auth.

Los identificadores de acceso se guardan en minúsculas y aceptan cualquier combinación de mayúsculas al entrar. Las nuevas cuentas se registran desde Android y su perfil se crea automáticamente. Las sesiones restauradas de cuentas antiguas muestran Completa tu perfil cuando falta algún dato. Las cuentas previas a la prueba de registro se eliminaron en el reinicio posterior descrito arriba.

Verificación: `node --test scripts/test_username_login.mjs` (5 pruebas); `node scripts/test_account_schema.mjs` (46 comprobaciones); 33 pruebas JVM, lint, compilación y 7 pruebas de interfaz en emulador. Una llamada real con usuario existente y contraseña incorrecta devuelve 401 sin revelar el correo. El acceso con las contraseñas reales queda para la prueba del propietario; no se solicitaron ni modificaron. Los problemas previamente documentados de RLS de las tablas antiguas siguen pendientes.

## Registro por correos autorizados

Desplegados la migración `20261030151510_registro_por_correos_autorizados.sql` y la Edge Function `authorized-signup`. El formulario recoge correo, nombre visible, usuario, contraseña y confirmación de contraseña. El inicio de sesión sigue usando solo usuario y contraseña. Las contraseñas no se guardan en el estado del ViewModel ni en preferencias.

### Autorizar un correo

En **Supabase → Table Editor → `signup_allowed_emails` → Insert row**, rellenar únicamente `email` en minúsculas. Dejar los demás campos con sus valores predeterminados. No crear una cuenta en Authentication ni escribir contraseñas o nombres ajenos.

También se puede autorizar desde **SQL Editor**, sustituyendo el correo del ejemplo:

```sql
insert into public.signup_allowed_emails (email)
values (lower(trim('nuevo@ejemplo.com')))
on conflict (email) do update
set enabled = true, expires_at = now() + interval '7 days'
where signup_allowed_emails.used_at is null;
```

El permiso dura siete días y solo permite una cuenta. Para renovar uno vencido que no se haya usado, ejecutar otra vez el mismo SQL. Para retirarlo antes del registro, cambiar `enabled` a `false`. Retirar este permiso no bloquea cuentas ya creadas. `used_at` indica que se creó la cuenta, no que el usuario confirmó el correo.

La lista tiene RLS y no permite lectura ni modificación desde clientes anónimos o autenticados. Solo el propietario desde el panel y los procesos internos de Auth/servidor tienen los permisos necesarios. No se enviaron invitaciones ni se añadieron correos a esta tabla durante el desarrollo.

### Lo que hace la persona

1. Abrir la nueva compilación de FindMe y pulsar **Crear cuenta**.
2. Escribir el correo autorizado y elegir su propio nombre visible, usuario y contraseña.
3. Abrir el enlace del correo de confirmación y regresar a FindMe para iniciar sesión con usuario y contraseña.
4. Si hace falta, pulsar **No recibí mi confirmación** y solicitar otro correo. No necesita proporcionar la contraseña para reenviarlo.

Cada cuenta nueva empieza sin conexiones. Compartir horarios requiere una invitación de Personas aceptada por la otra cuenta.

### Configuración de correo y retorno a Android

El propietario configuró Gmail SMTP desde el panel de Supabase, con contraseña de aplicación y verificación en dos pasos. Ninguna contraseña SMTP se recibió en el chat ni se guardó en el repositorio. El propietario confirmó la recepción del correo y el retorno a FindMe con un registro real.

En **Authentication → Email → SMTP Settings**, configurar un proveedor SMTP y un remitente autorizado por ese proveedor. Mantener **Confirm Email** activado y **Allow new users to sign up** activado. En **URL Configuration**, definir `findme://auth/confirmed` como **Site URL** y añadir exactamente la misma dirección a **Redirect URLs**. El propietario confirmó que guardó ambas opciones. Instalar la nueva compilación antes de pedir el correo: las versiones anteriores no reconocen ese enlace.

El SMTP integrado de Supabase solo entrega a miembros del equipo del proyecto; esa restricción es independiente de `signup_allowed_emails`. Añadir un correo a la tabla no cambia los destinatarios admitidos por ese servicio. Documentación: https://supabase.com/docs/guides/auth/auth-smtp

### Validación en servidor

La Edge Function normaliza y valida los datos, limita intentos, comprueba el permiso y solicita el registro a la API nativa de Auth. No usa el API administrativo para asignar contraseñas ni marcar correos como confirmados. Se despliega con `verify_jwt = false` porque aún no existe una sesión; la autorización efectiva se comprueba con la lista y en la base de datos. Rechaza proyectos con confirmación desactivada.

Dos triggers `SECURITY INVOKER`, con `search_path` vacío y permisos limitados al rol de Auth, comprueban el correo antes de insertar en `auth.users` y crean el perfil después. La comprobación se aplica también a solicitudes directas a la API nativa: el cliente no puede omitirla. La fila de autorización se bloquea durante la operación; perfil y consumo se realizan en la misma transacción. Los datos de `raw_user_meta_data` se usan únicamente para validar y crear el perfil elegido, nunca para decidir quién está autorizado.

Si el perfil no puede crearse, se revierte el alta y se conserva el permiso. Un error posterior de entrega puede requerir reenviar confirmación; el formulario no informa éxito si el servidor reporta un fallo de correo. El reenvío admite autorizaciones ya consumidas y no crea una segunda cuenta.

Pruebas locales:

```powershell
node scripts/test_registration_schema.mjs
node --test scripts/test_authorized_signup.mjs scripts/test_username_login.mjs
```

Comprobaciones: 19 casos del esquema de registro, 13 pruebas de las funciones de acceso y registro, 35 pruebas JVM y 9 pruebas de interfaz, incluyendo formulario y confirmación. Compilación y lint correctos. En el proyecto remoto se verificaron el rechazo de registro no autorizado por la función (403), el rechazo por Auth directo y la lectura anónima de la lista (401). Durante esas pruebas se conservaron las cuentas existentes y no se crearon usuarios ficticios; posteriormente el propietario solicitó el reinicio de datos descrito arriba. Los asesores no señalaron problemas nuevos del registro; las incidencias de las tablas antiguas siguen documentadas arriba.


La función `authorized-signup` fija `redirect_to=findme://auth/confirmed` tanto para registro como para reenvío. Android reconoce únicamente ese esquema, host y ruta. El enlace muestra el acceso o un aviso de enlace fallido; no importa tokens del Intent ni cambia la cuenta activa. La persona entra con usuario y contraseña, que vuelven a validarse con Auth. No se muestran detalles remotos sin validar ni se retienen fragmentos en el Intent de la actividad.

Se verificaron apertura en frío, recepción en la actividad ya abierta y aviso de enlace vencido en emulador, además de pruebas JVM del parser, compilación y lint. El propietario confirmó después que el correo llega y su enlace abre FindMe en el teléfono.


## Perfil, avisos y mensajes en vivo — 30 de octubre de 2026

El Perfil del modelo de cuentas recupera notificaciones, permisos de Android y actualizaciones, además de nombre y usuario. Los avisos de actividades, estados y mensajes se guardan por cuenta en DataStore. El acento de color (índigo, menta o rosa) se guarda por dispositivo y cambia los controles y componentes compartidos con una transición breve; los colores semánticos de las categorías de actividad se conservan. Cerrar sesión en el flujo anterior ya no borra estos ajustes.

Mientras la pantalla de cuentas está visible, `AccountLiveUpdates` escucha seis tablas de la publicación `supabase_realtime`: perfiles, conexiones, horario, estado, mensajes rápidos y mensajes enviados. Los datos siempre se vuelven a consultar mediante RLS; los eventos no se utilizan para omitir permisos. Los eventos se agrupan para evitar consultas repetidas por ráfagas. Hay una consulta de respaldo por minuto y una consulta al terminar la suscripción para cubrir los cambios ocurridos entre la carga inicial y la conexión.

La observación se cancela al salir de la pantalla de cuentas, pasar la app a segundo plano o cambiar de sesión; los canales se retiran al cancelar. Un reloj local revisa cambios de actividad, estado y caducidad cada 15 segundos sin consultar la base de datos. Los primeros datos de un contacto no generan avisos retrospectivos. Los mensajes nuevos producen avisos locales si la preferencia y el permiso de Android lo permiten; desactivar y activar los avisos no reproduce los mensajes anteriores. Pulsar un aviso de mensaje abre Personas.

**No hay notificaciones push con la app cerrada.** Cuando se vuelva a abrir, se consultan los mensajes que sigan vigentes. Implementar FCM continúa siendo una entrega pendiente.

### Única restricción nueva: conservar mensajes enviados durante 24 horas

Por elección del propietario, no se añadieron límites diarios ni pausas entre envíos. Se conserva la validación de texto que ya existía. La retención corresponde a `connection_signals`; no elimina los textos configurados como botones en `quick_messages` ni horarios, estados o cuentas.

Migraciones remotas aplicadas:

- `20261030165333_mensajes_en_vivo_y_retencion_diaria.sql`: índice por fecha, política RLS que oculta mensajes de 24 horas o más y publicación de las tablas para Realtime.
- `20261030165335_programar_limpieza_mensajes.sql`: instala `pg_cron` y programa `findme-purge-messages` cada cinco minutos. El trabajo elimina mensajes vencidos aun si nadie abre la app y conserva solo siete días de su propio historial de ejecución.

Los mensajes dejan de ser accesibles por API al cumplir 24 horas. El borrado físico puede tardar hasta cinco minutos adicionales hasta el siguiente ciclo. La lista de Personas consulta los últimos 20 mensajes vigentes para limitar el tamaño de cada lectura; ese tamaño de página no es un límite de envío. Los mensajes pueden vencer aunque no se hayan leído.

Verificación del servidor: `node scripts/test_message_retention.mjs` comprueba caducidad, borrado físico, permisos, revocación y ausencia de límites nuevos de envío (9 comprobaciones). Se verificaron las seis tablas publicadas, el trabajo activo y una ejecución programada satisfactoria. La recepción Realtime entre dos teléfonos con cuentas reales debe verificarse con el propietario; no se crearon usuarios ficticios para probarla.

Verificación Android de esta entrega: 40 pruebas JVM; 11 pruebas de interfaz de cuentas y una prueba de persistencia por cuenta en emulador, con comprobaciones finales enfocadas del selector de color. Compilación y lint correctos. Se revisó una captura de la vista con el acento menta.
