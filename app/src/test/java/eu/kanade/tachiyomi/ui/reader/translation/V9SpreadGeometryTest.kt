package eu.kanade.tachiyomi.ui.reader.translation

import org.junit.Assert.*
import org.junit.Test

class V9SpreadGeometryTest {
    @Test fun `RTL spread projects primary on right with hinge gap`() {
        val spread = V9SpreadGeometry.create(100,200,100,200,false,10)!!
        assertEquals(110f/210f, spread.primary.left, .00001f)
        assertEquals(0f, spread.secondary.left, .00001f)
        val b = TranslatedTextBlock("A","أ",0f,0f,1f,1f)
        val first = spread.primary.project(b)
        assertEquals(1f, first.right, .00001f)
        assertTrue(first.left > .50f)
    }
    @Test fun `LTR spread maps second to right without crossing text`() {
        val spread = V9SpreadGeometry.create(100,200,100,200,true,10)!!
        val a = spread.primary.project(TranslatedTextBlock("a","أ",.2f,.2f,.5f,.5f))
        val b = spread.secondary.project(TranslatedTextBlock("b","ب",.2f,.2f,.5f,.5f))
        assertTrue(a.right < b.left)
    }
    @Test fun `invalid dimensions do not project`() {
        assertNull(V9SpreadGeometry.create(0,100,100,100,false,0))
    }
}
