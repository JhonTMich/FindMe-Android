package com.example.findme.widget

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Sizes for the signed-out widget: "Near", the question "¿Dónde está tu gente?", a short invitation and
 * an "Iniciar sesión" pill on the left, the searching mascot anchored to the bottom-right corner.
 * Like [WidgetLayout], it is estimated from the real widget size and font scale, and unit tested.
 */
data class SignedOutLayout(
    val paddingVertical: Float,
    val paddingStart: Float,
    val columnWidth: Float,
    val artWidth: Float,
    val artHeight: Float,
    val brandSize: Float,
    val titleSize: Float,
    val titleLines: Int,
    val title: String,
    val bodySize: Float,
    /** Null hides the invitation line when the cell is short. */
    val body: String?,
    val bodyLines: Int,
    val buttonSize: Float,
    val buttonHeight: Float,
    /** Estimated height of everything in the column; never above the room it has. */
    val contentHeight: Float,
    val room: Float
) {
    companion object {
        const val TITLE = "¿Dónde está tu gente?"
        const val BODY = "Inicia sesión para compartir tu día."
        const val BUTTON = "Iniciar sesión →"
        private const val ART_RATIO = 574f / 614f
        private const val CHAR_WIDTH = 0.56f
        private const val LINE_HEIGHT = 1.34f
        private const val MIN_TEXT_WIDTH = 160f
        private const val GAP = 4f
        private const val BUTTON_GAP = 8f

        fun fit(width: Float, height: Float, fontScale: Float): SignedOutLayout {
            val scale = fontScale.coerceIn(0.85f, 2f)
            fun line(sp: Float) = sp * scale * LINE_HEIGHT
            fun chars(widthDp: Float, sp: Float) = max(1, floor(widthDp / (sp * scale * CHAR_WIDTH)).toInt())

            val roomy = height >= 160f
            val paddingVertical = if (roomy) 12f else 8f
            val paddingStart = 18f
            var artWidth = min(height * 0.94f * ART_RATIO, width * 0.46f)
            artWidth = min(artWidth, width - paddingStart - MIN_TEXT_WIDTH)
            if (artWidth < 50f) artWidth = 0f
            val columnWidth = width - artWidth
            val textWidth = columnWidth - paddingStart - 6f

            val brandSize = if (roomy) 15f else 13f
            val bodySize = if (roomy) 12.5f else 11.5f
            val buttonSize = if (roomy) 13f else 12f
            val buttonHeight = if (roomy) 34f else 28f
            val room = height - 2 * paddingVertical
            val fixed = line(brandSize) + GAP + BUTTON_GAP + buttonHeight

            val bodyLines = WidgetLayout.wrap(BODY, chars(textWidth, bodySize)).size.coerceAtMost(2)
            val bodyBlock = GAP + bodyLines * line(bodySize)
            val sizes = (if (roomy) 24 downTo 15 else 20 downTo 13).map { it.toFloat() }
            fun titleLines(size: Float) = WidgetLayout.wrap(TITLE, chars(textWidth, size)).size
            // The question first at the biggest size that fits with the invitation, then without it.
            val withBody = sizes.firstOrNull { titleLines(it) <= 2 && fixed + titleLines(it) * line(it) + bodyBlock <= room }
            val titleSize = withBody ?: sizes.firstOrNull { titleLines(it) <= 2 && fixed + titleLines(it) * line(it) <= room } ?: sizes.last()
            val lines = min(2, titleLines(titleSize))
            val body = if (withBody != null) WidgetLayout.fitText(BODY, chars(textWidth, bodySize), bodyLines) else null
            val content = fixed + lines * line(titleSize) + if (body != null) bodyBlock else 0f
            return SignedOutLayout(
                paddingVertical = paddingVertical, paddingStart = paddingStart, columnWidth = columnWidth,
                artWidth = artWidth, artHeight = if (artWidth > 0f) artWidth / ART_RATIO else 0f,
                brandSize = brandSize, titleSize = titleSize, titleLines = lines,
                // Two lines break as "¿Dónde está / tu gente?", like the design, when that first half fits.
                title = if (lines == 2 && "¿Dónde está".length <= chars(textWidth, titleSize)) "¿Dónde está\ntu gente?"
                    else WidgetLayout.fitText(TITLE, chars(textWidth, titleSize), lines),
                bodySize = bodySize, body = body, bodyLines = if (body != null) bodyLines else 0,
                buttonSize = buttonSize, buttonHeight = buttonHeight,
                contentHeight = min(content, room), room = room
            )
        }
    }
}
