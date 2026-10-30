# Cuentas y conexiones — primera implementación

Rama: `feature/cuentas-conexiones`.

## Incluido

- Registro e inicio de sesión con correo y contraseña mediante Supabase Auth.
- Restauración de la sesión; la contraseña no se guarda en el estado de la pantalla.
- Perfil propio con zona horaria del dispositivo al crearlo.
- Crear, editar y eliminar actividades semanales, con categoría, modalidad y lugar opcional.
- Estados temporales configurables. Por ahora mantienen la regla existente: una actividad en curso tiene prioridad.
- Invitación aleatoria de 128 bits, de un solo uso y duración de 24 horas. Generar otra reemplaza la anterior.
- Conexiones mutuas entre cuentas, sin categorías de pareja/familiar ni roles de administrador.
- Aceptar una invitación autoriza explícitamente a ambos a consultar horarios y estados. Ninguno puede modificar los datos del otro.
- Desvinculación con confirmación. Retira el acceso a datos futuros; no puede borrar información que alguien ya haya visto.
- Botones de mensajes rápidos con etiqueta y texto propios. Envían mensajes persistidos que se consultan en Personas al actualizar.
- Widget `FindMe · Mi gente` con tu estado, el de una persona conectada, hora de consulta y un botón de mensaje configurable desde Personas. La selección se comparte entre las instancias del widget en ese dispositivo y se comprueba contra la cuenta activa.
- Acceso separado a la versión anterior desde la pantalla de inicio de sesión.

## Preparar Supabase

El esquema está en `supabase/account-model.sql`. **Aplicado al proyecto personal existente el 29 de octubre de 2026**, mediante la migración remota `crear_cuentas_horarios_y_conexiones`.
El archivo local no es una migración generada por la CLI: la CLI no está instalada en este entorno. No volver a ejecutarlo en este proyecto; antes de publicarlo se debe incorporar el historial remoto al flujo de migraciones de la CLI.

Comprobaciones remotas después del despliegue: siete tablas nuevas con RLS y políticas; ninguna permite SELECT al rol `anon`; consultar perfiles por la API sin sesión devuelve HTTP 401. Los asesores de seguridad solo señalaron problemas en las tablas anteriores. Se conservaron un perfil, 11 actividades y 26 estados anteriores. No se crearon cuentas ni se reasignó el horario de Michael.

Auth permite registro por correo y exige confirmación. Falta probar entrega de correos y el registro desde Android. Para pruebas controladas sin configurar SMTP, se pueden crear dos usuarios confirmados desde el panel de Authentication y usar sus credenciales en la app; esto prueba el inicio de sesión, pero no el registro por correo.

1. Para entornos nuevos, elegir un proyecto de desarrollo de Supabase.
2. En un proyecto nuevo, ejecutar una sola vez el archivo SQL completo. Usa una transacción y falla si las tablas ya existen, evitando sobrescribir datos. En el proyecto personal ya está aplicado.
3. Habilitar el proveedor Email de Auth. Para confirmaciones de cuentas reales, configurar SMTP y las URLs permitidas; alternativamente elegir otro proveedor antes del lanzamiento. El correo integrado de Supabase tiene restricciones de destinatarios y frecuencia.
4. Probar dos cuentas y una tercera sin vínculo: registro, confirmación, inicio, edición de horario, invitación, acceso mutuo, mensaje y revocación.
5. Ejecutar los asesores de seguridad y comprobar las políticas como `authenticated` y `anon`.
6. Cambiar la URL/clave publicable del cliente si se utiliza otro proyecto de desarrollo.

Las tablas nuevas son `account_profiles`, `personal_schedule`, `personal_status`, `person_connections`, `connection_invitations`, `quick_messages` y `connection_signals`. Todas tienen RLS y permisos explícitos. Las invitaciones ajenas no se pueden consultar; una función con privilegios limitados en el esquema privado consume la invitación y crea la conexión de forma atómica. La API solo expone un wrapper `SECURITY INVOKER` para usuarios autenticados.

## Datos anteriores

`profiles`, `schedule` y `status_override` se conservan intactas. La revisión remota encontró RLS desactivado en las tres: el esquema nuevo **no corrige esa exposición**. Habilitar RLS en ellas sin políticas rompería la app 1.2.2 y el panel actual. Se debe acordar su retirada o políticas compatibles antes de lanzar el modelo de cuentas.

El perfil de Michael no se asigna automáticamente a quien se registre con ese nombre. Su horario se importará únicamente después de identificar y autorizar la cuenta propietaria; no se reclama mediante el código familiar antiguo.

## Siguientes entregas

- Configuración independiente de cada instancia de widget, varios botones y opciones de apariencia. Los dos widgets anteriores siguen siendo del modelo familiar anterior.
- Notificaciones push mediante FCM y una función de servidor, con tokens privados, control de envíos repetidos y preferencias del destinatario. Guardar un mensaje en Supabase todavía no envía una notificación Android.
- Sincronización Realtime filtrada, caché por cuenta y estado de última actualización. En esta entrega la pantalla se actualiza manualmente; solo el cálculo del paso del tiempo se repite cada 15 segundos.
- Recuperación de contraseña y enlaces de autenticación en Android.
- Edición del perfil, apodos por conexión, permisos independientes para horario/estado y silenciamiento.
- Excepciones que reemplazan actividades, actividades nocturnas y reglas de solapamiento.

No publicar esta rama como reemplazo de 1.2.2 hasta desplegar y probar el esquema, resolver la transición de las tablas anteriores y verificar los flujos en un dispositivo.

## Verificación local del esquema

```powershell
npm.cmd install --prefix build/schema-check --no-save --ignore-scripts @electric-sql/pglite@0.3.14
node scripts/test_account_schema.mjs
```

Ejecuta PostgreSQL embebido, sin conexión con Supabase, y crea un sustituto mínimo de `auth.users` y `auth.uid()` para probar RLS con tres usuarios y acceso anónimo. Comprueba 30 casos: aislamiento, edición ajena, consumo/vencimiento de invitaciones, mensajes y revocación. No sustituye las pruebas del proveedor de Auth ni de Realtime en el entorno de desarrollo.
