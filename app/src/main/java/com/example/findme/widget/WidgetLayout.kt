package com.example.findme.widget

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What the widget wants to say, before it is fitted to the space it has. */
data class WidgetTexts(
    val header: String,
    val label: String,
    val title: String,
    val detail: String?,
    val next: String,
    /** False when [next] is a hint ("Configura tu widget") instead of an activity. */
    val nextIsActivity: Boolean = true
)

/**
 * Sizes (dp for spacing, sp for text) and already-fitted texts for one render of the status widget.
 *
 * The widget is a text column on the left (header with actions, status chip, title, detail and the
 * next-activity card) over an illustration anchored to the bottom-right corner that takes almost
 * the whole height. Glance has no auto-sizing text, so the fit is estimated from the real widget
 * size and the system font scale: titles shrink before they wrap, wrap before they are cut, and
 * secondary lines are the first thing dropped. Nothing here touches Android, so it is unit tested.
 */
data class WidgetLayout(
    val roomy: Boolean,
    val paddingTop: Float,
    val paddingBottom: Float,
    val paddingStart: Float,
    /** Width of the text column, from the left edge (padding included); the art sits to its right. */
    val columnWidth: Float,
    /** 0 hides the illustration. */
    val artWidth: Float,
    val artHeight: Float,
    val headerSize: Float,
    val header: String,
    /** Refresh and settings buttons, next to the header. */
    val iconSize: Float,
    val iconPadding: Float,
    val headerGap: Float,
    val chipSize: Float,
    /** Null hides the chip because not even a small one fits. */
    val label: String?,
    val titleSize: Float,
    val titleLines: Int,
    val title: String,
    val detailSize: Float,
    val detailLines: Int,
    /** Null hides the detail line. */
    val detail: String?,
    val gap: Float,
    val cardPadding: Float,
    val calendarSize: Float,
    /** First, bold line of the card ("Próxima actividad" or "Próxima: IoT"); null for a one-line card. */
    val nextLabel: String?,
    val nextLabelSize: Float,
    val nextSize: Float,
    val next: String,
    /** Estimated height of the status block; never above [middleHeight]. */
    val contentHeight: Float,
    /** Height left for the status block once header, card and paddings are placed. */
    val middleHeight: Float
) {
    companion object {
        /** Width / height of the illustrations in res/drawable-nodpi (574×614 px). */
        private const val ART_RATIO = 574f / 614f
        /** Average advance of a bold Roboto glyph as a fraction of its size; slightly generous on purpose. */
        private const val CHAR_WIDTH = 0.56f
        /** Height of one text line as a fraction of its size, measured on device (TextView font padding included). */
        private const val LINE_HEIGHT = 1.34f
        /** From this height on (dp) the 4×2 widget uses its full arrangement; below it (landscape cells) it tightens. */
        const val ROOMY_HEIGHT = 160f
        /** Share of the widget height the illustration takes, and the most of the width it may take. */
        private const val ART_HEIGHT = 0.94f
        private const val ART_MAX_WIDTH = 0.48f
        /** The text column never gets narrower than this (dp, without padding); the art gives way first. */
        private const val MIN_TEXT_WIDTH = 170f
        private const val COLUMN_END_GAP = 4f
        private const val CHIP_PADDING = 6f
        private const val CHIP_SPACING = 5f
        private const val DETAIL_SPACING = 2f
        private const val CARD_HORIZONTAL = 20f
        private const val CARD_ICON_GAP = 8f

        fun fit(width: Float, height: Float, fontScale: Float, texts: WidgetTexts): WidgetLayout {
            val scale = fontScale.coerceIn(0.85f, 2f)
            fun line(sp: Float) = sp * scale * LINE_HEIGHT
            fun charsPerLine(widthDp: Float, sp: Float) = max(1, floor(widthDp / (sp * scale * CHAR_WIDTH)).toInt())

            val roomy = height >= ROOMY_HEIGHT
            val paddingVertical = when { roomy -> 10f; height < 120f -> 6f; else -> 8f }
            val paddingStart = 16f

            // Illustration: as tall as the widget allows, but never squeezing the text column.
            var artWidth = min(height * ART_HEIGHT * ART_RATIO, width * ART_MAX_WIDTH)
            artWidth = min(artWidth, width - paddingStart - COLUMN_END_GAP - MIN_TEXT_WIDTH)
            if (artWidth < 50f) artWidth = 0f
            val artHeight = artWidth / ART_RATIO
            val columnWidth = width - artWidth
            val textWidth = columnWidth - paddingStart - COLUMN_END_GAP

            val headerSize = if (roomy) 16f else 14f
            val iconSize = if (roomy) 28f else 24f
            val iconPadding = if (roomy) 5f else 4f
            val headerGap = 2f
            val headerHeight = max(line(headerSize), iconSize)
            val gap = if (roomy) 6f else 4f
            val cardPadding = if (roomy) 6f else 4f
            val calendarSize = if (roomy) 22f else 18f
            val nextLabelSize = 13f
            val nextSize = if (roomy) 12.5f else 11.5f
            val cardTextWidth = textWidth - CARD_HORIZONTAL - calendarSize - CARD_ICON_GAP
            val twoLineCard = 2 * cardPadding + line(nextLabelSize) + line(nextSize)
            val oneLineCard = 2 * cardPadding + max(line(nextSize), calendarSize)
            fun middleFor(card: Float) = max(0f, height - 2 * paddingVertical - headerHeight - headerGap - gap - card)

            val chipSize = if (roomy) 12f else 11f
            val detailSize = if (roomy) 12.5f else 12f
            val maxTitle = if (roomy) 26f else 22f
            val comfortable = if (roomy) 20f else 18f
            val minTitle = if (roomy) 15f else 14f
            val keepDetail = if (texts.detail != null) line(detailSize) + DETAIL_SPACING else 0f
            val candidates = buildList {
                for (s in steps(maxTitle, comfortable)) add(s to 1)
                for (s in steps(maxTitle - 2f, minTitle)) add(s to 2)
                if (roomy) for (s in steps(maxTitle - 4f, minTitle)) add(s to 3)
                for (s in steps(comfortable, minTitle)) add(s to 1)
            }
            fun chipBlock(withChip: Boolean) = if (withChip) line(chipSize) + CHIP_PADDING + CHIP_SPACING else 0f
            fun fits(size: Float, lines: Int, room: Float, reserve: Float) =
                wrap(texts.title, charsPerLine(textWidth, size)).size <= lines && lines * line(size) + reserve <= room

            // A status block is a card height, whether the chip and detail stay, and a title size/lines.
            // Preference: the chip, then the full card with the detail, then dropping the detail, then a
            // one-line card. The title only keeps the detail while it stays at [comfortable] - 2 or more.
            data class Choice(val twoLineCard: Boolean, val chip: Boolean, val detail: Boolean, val size: Float, val lines: Int)
            val attempts = listOf(true to true, true to false, false to true, false to false)
            val choice = listOf(true, false).asSequence().flatMap { withChip ->
                attempts.asSequence().mapNotNull { (twoLines, withDetail) ->
                    if (withDetail && keepDetail == 0f) return@mapNotNull null
                    val room = middleFor(if (twoLines) twoLineCard else oneLineCard) - chipBlock(withChip)
                    val reserve = if (withDetail) keepDetail else 0f
                    candidates.firstOrNull { (s, l) ->
                        (!withDetail || s >= comfortable - 2f) && fits(s, l, room, reserve)
                    }?.let { (s, l) -> Choice(twoLines, withChip, withDetail, s, l) }
                }
            }.firstOrNull() ?: run {
                val room = middleFor(oneLineCard)
                val lines = if (room >= 2 * line(minTitle)) 2 else 1
                Choice(false, false, false, steps(minTitle, 12f).firstOrNull { lines * line(it) <= room } ?: 12f, lines)
            }
            val cardHeight = if (choice.twoLineCard) twoLineCard else oneLineCard

            val middleHeight = middleFor(cardHeight)
            val label = if (choice.chip) fitText(texts.label, charsPerLine(textWidth - 20f, chipSize), 1) else null
            val title = fitText(texts.title, charsPerLine(textWidth, choice.size), choice.lines)
            val detailRoom = middleHeight - chipBlock(choice.chip) - choice.lines * line(choice.size) - DETAIL_SPACING
            val detailLines = if (texts.detail == null || !choice.detail) 0
                else min(if (roomy) 2 else 1, max(0, floor(detailRoom / line(detailSize)).toInt()))
            val detail = texts.detail?.takeIf { detailLines > 0 }?.let { fitText(it, charsPerLine(textWidth, detailSize), detailLines) }
            val usedDetailLines = detail?.let { min(detailLines, wrap(it, charsPerLine(textWidth, detailSize)).size) } ?: 0
            val contentHeight = chipBlock(choice.chip) + choice.lines * line(choice.size) +
                if (usedDetailLines > 0) DETAIL_SPACING + usedDetailLines * line(detailSize) else 0f

            val header = fitText(texts.header, charsPerLine(textWidth - 2 * iconSize, headerSize), 1)

            // Card: "Próxima actividad" over the whole activity when it fits on one line; otherwise the
            // activity name and its time on separate lines, so the time is never cut off.
            val labelChars = charsPerLine(cardTextWidth, nextLabelSize)
            val nextChars = charsPerLine(cardTextWidth, nextSize)
            val (nextLabel, next) = when {
                !choice.twoLineCard -> {
                    val prefixed = "Próxima: ${texts.next}"
                    null to fitText(if (texts.nextIsActivity && prefixed.length <= nextChars) prefixed else texts.next, nextChars, 1)
                }
                !texts.nextIsActivity -> {
                    // A hint ("Configura tu widget") spreads over both lines instead of being cut.
                    val lines = wrap(texts.next, labelChars)
                    if (lines.size <= 1) null to texts.next
                    else lines.first() to fitText(lines.drop(1).joinToString(" "), nextChars, 1)
                }
                texts.next.length <= nextChars -> "Próxima actividad" to texts.next
                " · " in texts.next -> {
                    val activity = texts.next.substringBefore(" · ")
                    val time = texts.next.substringAfter(" · ")
                    val prefixed = "Próxima: $activity"
                    fitText(if (prefixed.length <= labelChars) prefixed else activity, labelChars, 1) to fitText(time, nextChars, 1)
                }
                else -> "Próxima actividad" to fitText(texts.next, nextChars, 1)
            }

            return WidgetLayout(
                roomy = roomy, paddingTop = paddingVertical, paddingBottom = paddingVertical, paddingStart = paddingStart,
                columnWidth = columnWidth, artWidth = artWidth, artHeight = artHeight,
                headerSize = headerSize, header = header, iconSize = iconSize, iconPadding = iconPadding, headerGap = headerGap,
                chipSize = chipSize, label = label,
                titleSize = choice.size, titleLines = choice.lines, title = title,
                detailSize = detailSize, detailLines = usedDetailLines, detail = detail,
                gap = gap, cardPadding = cardPadding, calendarSize = calendarSize,
                nextLabel = nextLabel, nextLabelSize = nextLabelSize, nextSize = nextSize, next = next,
                contentHeight = min(contentHeight, middleHeight), middleHeight = middleHeight
            )
        }

        private fun steps(from: Float, to: Float): List<Float> =
            if (from < to) emptyList() else generateSequence(from) { it - 1f }.takeWhile { it >= to }.toList()

        /** Greedy word wrap, splitting words that are longer than a whole line. */
        internal fun wrap(text: String, charsPerLine: Int): List<String> {
            val lines = mutableListOf<String>()
            var current = ""
            for (word in text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                var rest = word
                while (rest.isNotEmpty()) {
                    val candidate = if (current.isEmpty()) rest else "$current $rest"
                    when {
                        candidate.length <= charsPerLine -> { current = candidate; rest = "" }
                        current.isNotEmpty() -> { lines += current; current = "" }
                        else -> { lines += rest.take(charsPerLine); rest = rest.drop(charsPerLine) }
                    }
                }
            }
            if (current.isNotEmpty()) lines += current
            return lines
        }

        /** The text itself when it fits in [maxLines]; otherwise the part that fits, ending in "…". */
        internal fun fitText(text: String, charsPerLine: Int, maxLines: Int): String {
            val lines = wrap(text, charsPerLine)
            if (lines.size <= maxLines) return text.trim()
            val kept = lines.take(maxLines)
            val last = kept.last().take(max(1, charsPerLine - 1)).trimEnd(' ', '·', ',', '-')
            return (kept.dropLast(1) + "$last…").joinToString(" ")
        }
    }
}
