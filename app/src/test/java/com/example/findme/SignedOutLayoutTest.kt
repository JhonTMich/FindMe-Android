package com.example.findme

import com.example.findme.widget.SignedOutLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedOutLayoutTest {
    @Test fun phone4x2ShowsTheWholeInvitation() {
        // POCO Launcher's 4×2 cell on a 1220×2712 phone.
        val layout = SignedOutLayout.fit(359f, 190f, 1f)
        assertEquals("¿Dónde está\ntu gente?", layout.title)
        assertEquals(2, layout.titleLines)
        assertTrue("title ${layout.titleSize}", layout.titleSize >= 20f)
        assertNotNull(layout.body)
        assertTrue("art ${layout.artWidth}", layout.artWidth >= 140f)
        assertTrue(layout.contentHeight <= layout.room + 0.01f)
    }

    @Test fun everySizeFitsAndKeepsTheQuestion() {
        for ((w, h) in listOf(250f to 102f, 554f to 112f, 276f to 150f, 276f to 185f, 359f to 190f, 276f to 220f))
            for (scale in listOf(0.85f, 1f, 1.3f)) {
                val layout = SignedOutLayout.fit(w, h, scale)
                assertTrue("${w}x$h@$scale overflows", layout.contentHeight <= layout.room + 0.01f)
                assertTrue("${w}x$h@$scale title ${layout.titleSize}", layout.titleSize >= 13f)
                assertTrue("${w}x$h@$scale column ${layout.columnWidth}", layout.columnWidth >= 160f)
            }
    }
}
