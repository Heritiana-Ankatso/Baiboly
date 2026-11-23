package mg.baiboly.app

import mg.baiboly.app.ui.workTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Reported: "6,000 days' wages" is hard to grasp; it is also told as years and months of work. */
class WorkTimeTest {
    @Test fun wagesAsALengthOfWork() {
        assertEquals(Triple(19, 2, 0), workTime(6000.0), "a talent: 1,000 six-day weeks")
        assertEquals(Triple(0, 3, 25), workTime(100.0), "a mina")
        assertEquals(Triple(0, 0, 29), workTime(25.0))
        assertNull(workTime(1.0), "a day's wage is told in days")
    }
}
