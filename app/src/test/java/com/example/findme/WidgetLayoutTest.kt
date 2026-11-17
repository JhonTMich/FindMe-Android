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

    /** Sizes a launcher gives to a 4×2 cell (landscape to large phones). */
    private val sizes4x2 = listOf(250f to 102f, 554f to 112f, 276f to 150f, 276f to 185f, 276f to 220f)
    /** Taller cells, kept as a safety net for launchers with unusual grids. */
    private val taller = listOf(276f to 260f, 276f to 337f)

    @Test fun everyTextFitsInEverySupportedSize() {
        for ((w, h) in sizes4x2 + taller) for (scale in listOf(0.85f, 1f, 1.3f)) for (texts in listOf(short, long)) {
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
        assertTrue(tall.titleSize >= compact.titleSize)
        assertNotNull(tall.detail)
        assertEquals("Próxima actividad", tall.nextLabel)
    }

    @Test fun phone4x2KeepsEveryLine() {
        // POCO Launcher's 4×2 cell on a 1220×2712 phone.
        val layout = WidgetLayout.fit(359f, 190f, 1f, short)
        assertNotNull(layout.label)
        assertEquals("Prácticas", layout.title)
        assertTrue(layout.titleSize >= 22f)
        assertNotNull(layout.detail)
        assertEquals("Próxima actividad", layout.nextLabel)
        assertTrue(layout.contentHeight <= layout.middleHeight + 0.01f)
        // The illustration spans header and status, so it is well above the old ~67dp.
        assertTrue("art ${layout.artWidth}", layout.artWidth >= 95f)
    }

    @Test fun phone4x2WrapsLongTitlesAndKeepsDetail() {
        for (title in listOf("Redes de Computadoras", "Proyecto de Investigación Aplicada")) {
            val layout = WidgetLayout.fit(359f, 190f, 1f, short.copy(title = title))
            assertEquals(title, layout.title)
            assertEquals(2, layout.titleLines)
            assertTrue(layout.titleSize >= 18f)
            assertNotNull(layout.detail)
            assertTrue(layout.contentHeight <= layout.middleHeight + 0.01f)
        }
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
        assertEquals("Próxima: IoT · 7:00 a. m.", WidgetLayout.fit(359f, 150f, 1f, short.copy(next = "IoT · 7:00 a. m.")).next)
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
