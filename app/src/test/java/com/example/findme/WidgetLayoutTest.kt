package com.example.findme

import com.example.findme.widget.WidgetLayout
import com.example.findme.widget.WidgetTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    private val short = WidgetTexts("Mi estado", "Trabajando", "Prácticas", "Hasta 1:00 p. m. · Laboratorio 3", "IoT · mañana 7:00 a. m.")
    private val long = WidgetTexts("Sandra Milagros Fernández", "En clase virtual",
        "Proyecto de Investigación Aplicada", "Hasta 11:30 p. m. de mañana · Pabellón B, aula 204",
        "Taller de Desarrollo de Aplicaciones Móviles · lun 7:00 p. m.")

    /** Sizes a launcher gives to a 4×3 cell (landscape to large phones). */
    private val sizes4x3 = listOf(554f to 173f, 276f to 190f, 276f to 220f, 320f to 280f, 276f to 337f)
    /** Shorter cells the widget no longer allows, kept as a safety net for odd launchers. */
    private val shorter = listOf(250f to 102f, 276f to 110f, 320f to 150f)

    @Test fun everyTextFitsInEverySupportedSize() {
        for ((w, h) in sizes4x3 + shorter) for (scale in listOf(0.85f, 1f, 1.3f)) for (texts in listOf(short, long)) {
            val layout = WidgetLayout.fit(w, h, scale, texts)
            assertTrue("${w}x$h@$scale overflows", layout.contentHeight <= layout.middleHeight + 0.01f)
            assertTrue("${w}x$h@$scale title too small: ${layout.titleSize}", layout.titleSize >= 12f)
        }
    }

    @Test fun shortTitleStaysBigOnOneLine() {
        val compact = WidgetLayout.fit(276f, 150f, 1f, short)
        assertEquals(1, compact.titleLines)
        assertEquals("Prácticas", compact.title)
        assertTrue(compact.titleSize >= 22f)
        val tall = WidgetLayout.fit(276f, 220f, 1f, short)
        assertTrue(tall.roomy)
        assertTrue(tall.titleSize > compact.titleSize)
        assertNotNull(tall.detail)
        assertEquals("Próxima actividad", tall.nextLabel)
    }

    @Test fun longTitleShrinksOrWrapsInsteadOfBeingCut() {
        val tall = WidgetLayout.fit(276f, 260f, 1f, long)
        assertEquals("Proyecto de Investigación Aplicada", tall.title)
        assertTrue(tall.titleLines == 2 || tall.titleSize < 26f)
    }

    @Test fun shortCellDropsSecondaryLinesFirst() {
        val tiny = WidgetLayout.fit(250f, 102f, 1.3f, long)
        assertNull(tiny.detail)
        assertNull(tiny.nextLabel)
        assertTrue(tiny.next.startsWith("Taller"))
        assertTrue(tiny.next.endsWith("…"))
        assertEquals("Próxima: IoT · 7:00 a. m.", WidgetLayout.fit(276f, 150f, 1f, short.copy(next = "IoT · 7:00 a. m.")).next)
        assertFalse(tiny.roomy)
    }

    @Test fun longNamesAreEllipsized() {
        val layout = WidgetLayout.fit(250f, 110f, 1f, long)
        assertTrue(layout.header.length < long.header.length)
        assertTrue(layout.header.endsWith("…"))
    }

    @Test fun hintIsNotPrefixedAsAnActivity() {
        val layout = WidgetLayout.fit(276f, 120f, 1f, short.copy(next = "Configura tu widget", nextIsActivity = false))
        assertEquals("Configura tu widget", layout.next)
    }
}
