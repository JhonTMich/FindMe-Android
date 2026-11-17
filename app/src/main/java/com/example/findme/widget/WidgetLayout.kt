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
 * Glance has no auto-sizing text, so the fit is estimated from the real widget size and the system
 * font scale: titles shrink before they wrap, wrap before they are cut, and secondary lines are the
 * first thing dropped when a 4×2 cell is short. Nothing here touches Android, so it is unit tested.
 */
data class WidgetLayout(
    val roomy: Boolean,
    val paddingVertical: Float,
    val paddingStart: Float,
    val paddingEnd: Float,
    /** Refresh and settings buttons, at the right end of the next-activity card. */
    val iconSize: Float,
    val iconPadding: Float,
    val headerSize: Float,
    val header: String,
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
    /** 0 hides the illustration, which spans from the top edge down to the card, beside header and status. */
    val artWidth: Float,
    /** Space between the header line and the status block. */
    val headerGap: Float,
    val gap: Float,
    val cardPadding: Float,
    val calendarSize: Float,
    /** "Próxima actividad" caption, only when there is room for a two-line card. */
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
        /** The title only shrinks to make room for the detail line while it stays at least this big (sp). */
        private const val MIN_TITLE_WITH_DETAIL = 22f
        /** From this height on (dp) the 4×2 widget uses its full arrangement; below it (landscape cells) it tightens. */
        const val ROOMY_HEIGHT = 160f
        private const val CHIP_PADDING = 6f
        private const val CHIP_SPACING = 4f
        private const val DETAIL_SPACING = 2f
        /** Share of the width the illustration may take, from preferred to the least it gives up to. */
        private val ART_FRACTIONS = listOf(0.38f, 0.3f, 0.24f)

        fun fit(width: Float, height: Float, fontScale: Float, texts: WidgetTexts): WidgetLayout {
            val scale = fontScale.coerceIn(0.85f, 2f)
            fun line(sp: Float) = sp * scale * LINE_HEIGHT
            fun charsPerLine(widthDp: Float, sp: Float) = max(1, floor(widthDp / (sp * scale * CHAR_WIDTH)).toInt())

            val roomy = height >= ROOMY_HEIGHT
            val paddingVertical = when { roomy -> 12f; height < 120f -> 6f; else -> 9f }
            val paddingStart = 16f
            val paddingEnd = 10f
            val iconSize = if (roomy) 32f else 26f
            val iconPadding = if (roomy) 6f else 5f
            val headerSize = if (roomy) 16f else 14f
            val headerGap = if (roomy) 4f else 2f
            val gap = if (roomy) 6f else 5f
            val cardPadding = if (roomy) 6f else 4f
            val calendarSize = if (roomy) 22f else 18f
            val nextLabelSize = 13f
            val nextSize = if (roomy) 12.5f else 11.5f

            val innerWidth = width - paddingStart - paddingEnd
            val headerHeight = line(headerSize)
            val cardHeight = 2 * cardPadding + max(iconSize,
                if (roomy) line(nextLabelSize) + line(nextSize) else max(line(nextSize), calendarSize))
            // Header + status sit on the left of this block; the illustration takes its full height on the right.
            val topHeight = max(0f, height - 2 * paddingVertical - gap - cardHeight)
            val middleHeight = max(0f, topHeight - headerHeight - headerGap)

            // Chip: shrink, then hide, until a minimal title still fits under it.
            val minTitle = if (roomy) 18f else 15f
            var chipSize = if (roomy) 13f else 11.5f
            var label: String? = texts.label
            fun chipBlock() = if (label == null) 0f else line(chipSize) + CHIP_PADDING + CHIP_SPACING
            if (middleHeight - chipBlock() < line(minTitle)) chipSize = 10f
            if (middleHeight - chipBlock() < line(minTitle)) label = null

            // Title: biggest size on one line, then two lines, then smaller. If it still does not fit,
            // the illustration gives up width before the title is cut.
            val detailSize = if (roomy) 13f else 12f
            val titleRoom = middleHeight - chipBlock()
            val maxTitle = if (roomy) 32f else 26f
            val comfortable = if (roomy) 22f else 19f
            val maxTitleLines = when {
                roomy && titleRoom >= 3 * line(minTitle) -> 3
                titleRoom >= 2 * line(minTitle) -> 2
                else -> 1
            }
            // Room kept for one detail line ("Hasta 1:00 p. m."): the title gives up size for it, but
            // only while it stays at [MIN_TITLE_WITH_DETAIL]; below that the detail is dropped instead.
            val keepDetail = if (texts.detail != null) line(detailSize) + DETAIL_SPACING else 0f
            val candidates = buildList {
                for (s in steps(maxTitle, comfortable)) add(s to 1)
                for (s in steps(maxTitle - 4f, minTitle)) add(s to 2)
                if (maxTitleLines == 3) for (s in steps(maxTitle - 8f, minTitle)) add(s to 3)
                for (s in steps(comfortable, minTitle)) add(s to 1)
            }
            fun artFor(fraction: Float) = min(innerWidth * fraction, topHeight * ART_RATIO).let { if (it < 44f) 0f else it }
            fun textWidthFor(art: Float) = innerWidth - art - if (art > 0f) 6f else 0f
            fun fits(textWidth: Float, size: Float, lines: Int, reserve: Float) =
                wrap(texts.title, charsPerLine(textWidth, size)).size <= lines && lines * line(size) + reserve <= titleRoom
            val choice = ART_FRACTIONS.asSequence().flatMap { fraction ->
                val art = artFor(fraction)
                val textWidth = textWidthFor(art)
                listOf(keepDetail, 0f).distinct().asSequence().flatMap { reserve ->
                    candidates.asSequence().filter { (s, _) -> reserve == 0f || s >= MIN_TITLE_WITH_DETAIL }
                        .filter { (s, l) -> fits(textWidth, s, l, reserve) }
                        .map { (s, l) -> Triple(art, s, l) }
                }
            }.firstOrNull()
            val (artWidth, titleSize, titleLines) = choice ?: run {
                val art = artFor(ART_FRACTIONS.last())
                val size = steps(minTitle, 12f).firstOrNull { maxTitleLines * line(it) <= titleRoom } ?: 12f
                Triple(art, size, maxTitleLines)
            }
            val textWidth = textWidthFor(artWidth)
            label = label?.let { fitText(it, charsPerLine(textWidth - 21f, chipSize), 1) }
            val title = fitText(texts.title, charsPerLine(textWidth, titleSize), titleLines)

            // Detail: whatever room is left, at most two lines on 4×3 and one on 4×2.
            val detailRoom = titleRoom - titleLines * line(titleSize) - DETAIL_SPACING
            val detailLines = if (texts.detail == null) 0
                else min(if (roomy) 2 else 1, max(0, floor(detailRoom / line(detailSize)).toInt()))
            val detail = texts.detail?.takeIf { detailLines > 0 }?.let { fitText(it, charsPerLine(textWidth, detailSize), detailLines) }
            val usedDetailLines = detail?.let { min(detailLines, wrap(it, charsPerLine(textWidth, detailSize)).size) } ?: 0

            val contentHeight = chipBlock() + titleLines * line(titleSize) +
                if (usedDetailLines > 0) DETAIL_SPACING + usedDetailLines * line(detailSize) else 0f

            // Header and next-activity card are single lines that lose their tail when needed.
            val header = fitText(texts.header, charsPerLine(textWidth, headerSize), 1)
            val cardTextWidth = innerWidth - 16f - calendarSize - 21f - 2 * iconSize
            val nextChars = charsPerLine(cardTextWidth, nextSize)
            // On 4×2 the card has no caption, so "Próxima:" leads the line unless it would push the activity out.
            val prefixed = "Próxima: ${texts.next}"
            val nextText = if (!roomy && texts.nextIsActivity && prefixed.length <= nextChars) prefixed else texts.next
            val next = fitText(nextText, nextChars, 1)

            return WidgetLayout(
                roomy = roomy, paddingVertical = paddingVertical, paddingStart = paddingStart, paddingEnd = paddingEnd,
                iconSize = iconSize, iconPadding = iconPadding, headerSize = headerSize, header = header,
                chipSize = chipSize, label = label,
                titleSize = titleSize, titleLines = titleLines, title = title,
                detailSize = detailSize, detailLines = usedDetailLines, detail = detail,
                artWidth = artWidth, headerGap = headerGap, gap = gap, cardPadding = cardPadding, calendarSize = calendarSize,
                nextLabel = if (roomy) "Próxima actividad" else null, nextLabelSize = nextLabelSize,
                nextSize = nextSize, next = next,
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
