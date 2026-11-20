package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockSizeTest {

    @Test
    fun `each tap moves to the next size`() {
        assertEquals(1.2f, ClockSize.next(1.0f), 0f)
        assertEquals(0.8f, ClockSize.next(0.6f), 0f)
    }

    @Test
    fun `the largest size wraps to the smallest`() {
        assertEquals(0.6f, ClockSize.next(2.0f), 0f)
    }

    @Test
    fun `an unknown stored value resets to the default`() {
        assertEquals(1.0f, ClockSize.next(1.37f), 0f)
    }
}
