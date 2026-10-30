# Cuentas y conexiones — primera implementación

Rama: `feature/cuentas-conexiones`.

## Incluido

- Inicio de sesión con usuario y contraseña mediante la función `username-login`, que valida la contraseña con Supabase Auth. El correo permanece asociado a Auth para altas y recuperación; no se solicita en la pantalla de acceso. En esta etapa las cuentas se crean manualmente en Supabase y no hay formulario público de registro.
- Restauración de la sesión; la contraseña no se guarda en el estado de la pantalla.
- Perfil propio con zona horaria del dispositivo al crearlo. Las cuentas sin nombre o usuario completan su perfil al ingresar; ambos se pueden editar en Cuenta. El usuario es único, ignora mayúsculas y admite de 3 a 24 letras ASCII, números o guion bajo.
- Crear, editar y eliminar actividades semanales, con categoría, modalidad y lugar opcional.
- Estados manuales configurables con prioridad sobre el horario: por ejemplo, «En camino a casa» puede reemplazar una clase todavía programada. Al vencer o quitar el estado vuelve el cálculo por horario, tanto en la app como en el widget. El flujo anterior conserva su regla de prioridad.
- Invitación aleatoria de ocho caracteres sin letras o dígitos ambiguos, presentada como `ABCD-EFGH`, de un solo uso y duración de 24 horas. Generar otra reemplaza la anterior. Se mantienen compatibles las invitaciones antiguas.
- Conexiones mutuas entre cuentas, sin categorías de pareja/familiar ni roles de administrador.
- Aceptar una invitación autoriza explícitamente a ambos a consultar horarios y estados. Ninguno puede modificar los datos del otro.
- Desvinculación con confirmación. Retira el acceso a datos futuros; no puede borrar información que alguien ya haya visto.
- Mensajes rápidos configurables dentro de «Mis mensajes y widget», separados de los estados. Un campo define el mensaje; la etiqueta del botón se deriva automáticamente. Envían mensajes persistidos que se consultan en Personas al actualizar.
- Widget `FindMe · Mi gente` con tu estado, el de una persona conectada, hora de consulta y un botón de mensaje configurable desde Personas. La selección se comparte entre las instancias del widget en ese dispositivo y se comprueba contra la cuenta activa.
- Acceso separado a la versión anterior desde la pantalla de inicio de sesión.

## Preparar Supabase

El esquema está en `supabase/account-model.sql`. **Aplicado al proyecto personal existente el 29 de octubre de 2026**, mediante la migración remota `crear_cuentas_horarios_y_conexiones`.
El archivo local no es una migración generada por la CLI: la CLI no está instalada en este entorno. No volver a ejecutarlo en este proyecto; antes de publicarlo se debe incorporar el historial remoto al flujo de migraciones de la CLI.

Comprobaciones remotas después del despliegue: siete tablas nuevas con RLS y políticas; ninguna permite SELECT al rol `anon`; consultar perfiles por la API sin sesión devuelve HTTP 401. Los asesores de seguridad solo señalaron problemas en las tablas anteriores. Se conservaron un perfil, 11 actividades y 26 estados anteriores. En ese despliegue inicial no se crearon cuentas ni se reasignó el horario de Michael.

La última comprobación del 30 de octubre encontró el registro por correo habilitado y confirmación obligatoria. Para esta etapa cerrada, **falta desactivar `Allow new users to sign up` en Authentication → Sign In / Providers**; ocultar el registro en Android no cierra la API de Auth. El conector no expone esa configuración. Falta probar entrega de correos y el registro desde Android. Para pruebas controladas sin configurar SMTP, se pueden crear dos usuarios confirmados desde el panel de Authentication y usar sus credenciales en la app; esto prueba el inicio de sesión, pero no el registro por correo.

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

`profiles`, `schedule` y `status_override` se conservan intactas. La revisión remota encontró RLS desactivado en las tres: el esquema nuevo **no corrige esa exposición**. Habilitar RLS en ellas sin políticas rompería la app 1.2.2 y el panel actual. Se debe acordar su retirada o políticas compatibles antes de lanzar el modelo de cuentas.

El perfil de Michael no se asigna automáticamente a quien se registre con ese nombre. El 30 de octubre, con autorización explícita del propietario, se copiaron sus 11 actividades a la cuenta identificada y se conservó una actividad que ya tenía: 12 en total. La copia no elimina ni cambia el horario anterior. No se reclama un horario mediante el nombre visible ni mediante el código familiar antiguo.

## Siguientes entregas

- Configuración independiente de cada instancia de widget, varios botones y opciones de apariencia. Los dos widgets anteriores siguen siendo del modelo familiar anterior.
- Notificaciones push mediante FCM y una función de servidor, con tokens privados, control de envíos repetidos y preferencias del destinatario. Guardar un mensaje en Supabase todavía no envía una notificación Android.
- Sincronización Realtime filtrada, caché por cuenta y estado de última actualización. En esta entrega la pantalla se actualiza manualmente; solo el cálculo del paso del tiempo se repite cada 15 segundos.
- Recuperación de contraseña y enlaces de autenticación en Android.
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

Aplicada la migración remota `agregar_usuario_para_inicio_de_sesion`, cuyo SQL está en `supabase/upgrades/username-login.sql`. Añade `account_profiles.username` sin recrear usuarios. Conserva las contraseñas, sesiones y conexiones existentes. Para entornos nuevos, ejecutar primero el esquema base y después las dos actualizaciones de `supabase/upgrades/`.

La Edge Function `username-login` está desplegada. Sus fuentes están en `supabase/functions/username-login/`. Se publica con `verify_jwt = false` porque recibe credenciales antes de existir una sesión y realiza su propia autenticación: resuelve el usuario exclusivamente en el servidor y solicita a Supabase Auth una sesión mediante contraseña. La clave de servicio solo existe en el entorno del servidor. No hay una API pública que traduzca usuarios a correos. Los errores de usuario inexistente y contraseña incorrecta usan el mismo mensaje. La función no registra contraseñas, cuerpos ni tokens y devuelve `Cache-Control: no-store`.

Los límites persistidos permiten ocho intentos por usuario en cinco minutos y 60 intentos globales por minuto. Los contadores están en un esquema privado, con RLS, sin políticas para clientes y con acceso exclusivamente de servicio; el aviso informativo del asesor sobre esa ausencia de políticas es intencional. Los errores del limitador cierran el acceso en lugar de omitir el límite. Esto no reemplaza la protección contra ataques a la API nativa de Auth.

Se prepararon los usuarios `JhonTMich` (Michael) y `Sanely` (Sandra) en las dos cuentas existentes, sin cambiar contraseñas. Los identificadores se guardan en minúsculas y aceptan cualquier combinación de mayúsculas al entrar. Para otra cuenta creada manualmente, asignar un usuario único a su perfil antes de cerrar su sesión si aún no lo tiene. Las sesiones restauradas de cuentas antiguas muestran Completa tu perfil cuando falta ese dato.

Verificación: `node --test scripts/test_username_login.mjs` (5 pruebas); `node scripts/test_account_schema.mjs` (46 comprobaciones); 33 pruebas JVM, lint, compilación y 7 pruebas de interfaz en emulador. Una llamada real con usuario existente y contraseña incorrecta devuelve 401 sin revelar el correo. El acceso con las contraseñas reales queda para la prueba del propietario; no se solicitaron ni modificaron. Los problemas previamente documentados de RLS de las tablas antiguas siguen pendientes.

## Cómo dar acceso a una persona nueva ahora

**Todavía no existe una lista de correos autorizados ni registro desde Android.** El alta es manual: crear una cuenta en Authentication no es agregar un correo a una lista de autorizados. El registro por lista de correos sería una entrega posterior.

1. En el panel de Supabase, abrir **Authentication → Users → Add user → Create new user** y crear la cuenta con su correo y contraseña. Para esta prueba controlada, puede marcarse como confirmada desde el panel; eso no prueba la confirmación por correo.
2. En **SQL Editor**, crear su perfil usando el correo exacto del paso anterior. Sustituir los valores de ejemplo antes de ejecutar:

```sql
insert into public.account_profiles (id, display_name, username, timezone)
select id, 'Nombre visible', 'usuario_nuevo', 'America/Lima'
from auth.users
where lower(email) = lower('nuevo@ejemplo.com')
on conflict (id) do update
set display_name = excluded.display_name,
    username = excluded.username;
```

La contraseña no se escribe en este SQL. El usuario debe ser único, en minúsculas, y tener entre 3 y 24 letras sin tildes, números o guion bajo. Si el correo no existe en Auth, el SELECT no crea ningún perfil: comprobar que el editor indique una fila insertada o actualizada.

3. Entrar en la nueva compilación de Android con `usuario_nuevo` y la contraseña de esa cuenta. Crear una cuenta no la conecta con ninguna persona: la conexión requiere el código de invitación y su aceptación desde Personas.

Para impedir altas por la API pública de Auth, desactivar **Authentication → Sign In / Providers → Allow new users to sign up**. Esto conserva el acceso de las cuentas existentes y el alta administrativa desde el panel. La última comprobación documentada encontró esa opción habilitada; no se ha cambiado automáticamente.
