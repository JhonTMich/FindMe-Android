# Plan de Mejora Visual e Interactividad del Widget (`FindMe · Mi gente`)

Este documento especifica el plan de rediseño visual, simplificación de contenido, gestión de imágenes personalizadas e integración del envío de mensajes rápidos directamente desde el widget de la pantalla de inicio de Android.

---

## 1. Visión General y Objetivos

1. **Simplificación del Contenido**: La tarjeta del widget debe ser directa y concisa. Solo mostrará:
   - **Nombre de la persona** ("Tú", "Juan", "María").
   - **Qué está haciendo / Estado actual** ("En clase", "Trabajando", "Tiempo libre", "En camino").
2. **Sistema de Imágenes Personalizadas**:
   - Sustituir los gráficos vectoriales básicos por un catálogo de imágenes personalizadas (PNG, WebP o Drawables vectoriales de alta calidad) que el desarrollador/diseñador puede agregar fácilmente en la carpeta `res/drawable/`.
   - Mapear cada estado de actividad a una imagen específica.
3. **Envío de Mensajes Rápidos desde el Widget**:
   - Permitir enviar mensajes rápidos preconfigurados en la app FindMe a la persona conectada mediante botones interactivos directamente en el widget.

---

## 2. Rediseño Minimalista del Contenido de las Tarjetas

### Estructura de cada Tarjeta

Cada tarjeta (propietario "Tú" y persona conectada "Amigo/Pareja") tendrá una interfaz limpia:

```
+---------------------------------------------------+
|  Juan                                             |
|  ● En clase                                       |
|                                     [ Imagen ]    |
|  +---------------------------------------------+  |
|  | 💬 Enviar: "¿A qué hora sales?"            |  |
|  +---------------------------------------------+  |
+---------------------------------------------------+
```

- **Lado Izquierdo**:
  - Nombre en texto destacado (`16.sp`, Negrita).
  - Píldora de estado actual con punto de color (`● En clase`, `● Trabajando`, `● Tiempo libre`, `● En camino`).
  - Botón de acción rápida para enviar mensaje (si hay un mensaje preconfigurado).
- **Lado Derecho**:
  - Imagen representativa recortada/escalada según la actividad.

---

## 3. Guía para Añadir y Cambiar Imágenes Personalizadas

### Paso 1: Guardar las imágenes en el proyecto

Coloca los archivos de imagen (formatos recomendados: `.png`, `.webp` con fondo transparente o `.xml` para vectores) dentro de la carpeta:
`app/src/main/res/drawable/`

Nombres sugeridos para los archivos de imagen:
- `img_status_class.png` — Imagen/ilustración para clases o universidad.
- `img_status_work.png` — Imagen/ilustración para trabajo o oficina.
- `img_status_free.png` — Imagen/ilustración para tiempo libre o estar en casa.
- `img_status_transit.png` — Imagen/ilustración para estar en camino o en la calle.
- `img_status_default.png` — Imagen por defecto si no hay categoría asignada.

### Paso 2: Vincular las imágenes en el código Glance

En `[ConnectionWidget.kt](file:///C:/Users/jhont/Documents/Work/FindMe-Android/app/src/main/java/com/example/findme/widget/ConnectionWidget.kt)`, las imágenes se invocan mediante `ImageProvider(R.drawable.nombre_de_imagen)`:

```kotlin
val imageResId = when (activityCategory) {
    ActivityCategory.UNIVERSITY -> R.drawable.img_status_class
    ActivityCategory.WORK -> R.drawable.img_status_work
    ActivityCategory.FREE -> R.drawable.img_status_free
    else -> R.drawable.img_status_default
}

Image(
    provider = ImageProvider(imageResId),
    contentDescription = "Estado actual",
    contentScale = ContentScale.Fit,
    modifier = GlanceModifier
        .height(80.dp)
        .width(80.dp)
)
```

---

## 4. Envío de Mensajes Rápidos desde el Widget

### Flujo Técnico

1. **Consulta de Mensajes**: En `provideGlance()`, el widget obtiene los mensajes rápidos creados por el usuario mediante `AccountRepository.quickMessages()`.
2. **Selección del Mensaje Activo**: El widget toma el mensaje configurado o el primer mensaje rápido disponible para la persona seleccionada.
3. **Ejecución de la Acción**: Al presionar el botón `💬 Enviar` en el widget, se dispara la clase `ActionCallback`:

```kotlin
class SendWidgetQuickMessageAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = AccountRepository()
        repo.auth.awaitInitialization()
        val selection = ConnectionWidgetSettings.read(context, repo.userId()) ?: return
        val messages = repo.quickMessages()
        val targetMessage = messages.firstOrNull { it.id == selection.messageId } ?: messages.firstOrNull() ?: return
        
        // Envía la señal/mensaje a la persona conectada mediante Supabase
        repo.sendSignal(selection.personId, targetMessage.message)
        
        // Muestra notificación/Toast de confirmación en el dispositivo
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Mensaje enviado: \"${targetMessage.label}\"", Toast.LENGTH_SHORT).show()
        }
    }
}
```

---

## 5. Fases de Implementación

### Fase 1: Limpieza Visual del Layout
- Remover textos secundarios (subtítulos complejos, códigos de materia o etiquetas cargadas).
- Dejar únicamente **Nombre** y **Qué está haciendo**.

### Fase 2: Soporte de Assets de Imagen
- Crear el mapeo dinámico de imágenes en `ConnectionWidget.kt`.
- Proporcionar las rutas para reemplazar las imágenes por archivos `.png` / `.webp` personalizados.

### Fase 3: Integración de Mensajes Rápidos
- Agregar el botón de envío directo en la tarjeta del widget.
- Conectar la llamada a `repo.sendSignal()` con respuesta mediante Toast.

---
