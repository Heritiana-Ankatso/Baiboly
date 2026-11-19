package mg.baiboly.core

import mg.baiboly.core.list.ServiceList
import mg.baiboly.core.ref.Passage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceListTest {
    private val a = Passage(43, 3, 16, 16)
    private val b = Passage(49, 2, 8, 9)
    private val c = Passage(19, 23)

    @Test fun addSetsCurrentOnFirstItemOnly() {
        val l = ServiceList().add(listOf(a)).add(listOf(b, c))
        assertEquals(0, l.current)
        assertEquals(3, l.items.size)
    }

    @Test fun navigation() {
        var l = ServiceList().add(listOf(a, b, c))
        l = l.next().next().next()
        assertEquals(2, l.current)
        l = l.previous()
        assertEquals(1, l.current)
        assertEquals(0, l.select(0).current)
    }

    @Test fun moveKeepsCurrentItem() {
        val l = ServiceList().add(listOf(a, b, c)).select(1)
        val m = l.moveEarlier(1)
        assertEquals(b, m.currentItem!!.passage)
        val n = l.move(0, 2)
        assertEquals(b, n.currentItem!!.passage)
    }

    @Test fun deleteAdjustsCurrent() {
        val l = ServiceList().add(listOf(a, b, c)).select(2)
        assertEquals(c, l.delete(0).currentItem!!.passage)
        assertEquals(b, l.delete(2).currentItem!!.passage)
        assertTrue(ServiceList().add(listOf(a)).delete(0).currentItem == null)
    }

    @Test fun sideTag() {
        val l = ServiceList().add(listOf(a, b)).toggleSide(1)
        assertTrue(l.items[1].isSide)
        assertTrue(!l.toggleSide(1).items[1].isSide)
    }
}
