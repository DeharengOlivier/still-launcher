package app.olauncher.listener

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSwipeTest {

    private val threshold = 100f

    @Test
    fun `a right-to-left swipe opens the app drawer`() {
        assertEquals(HomeSwipe.OPEN_DRAWER, HomeSwipe.decide(dx = -200f, dy = 20f, threshold))
    }

    @Test
    fun `a left-to-right swipe does nothing`() {
        assertEquals(HomeSwipe.NONE, HomeSwipe.decide(dx = 200f, dy = 10f, threshold))
    }

    @Test
    fun `a vertical drag is a scroll, never a drawer swipe`() {
        assertEquals(HomeSwipe.NONE, HomeSwipe.decide(dx = -150f, dy = 300f, threshold))
        assertEquals(HomeSwipe.NONE, HomeSwipe.decide(dx = 0f, dy = -400f, threshold))
    }

    @Test
    fun `a left-to-right swipe in the app list goes back home`() {
        assertEquals(true, HomeSwipe.isBackToHome(dx = 200f, dy = 30f, threshold))
        assertEquals(false, HomeSwipe.isBackToHome(dx = -200f, dy = 30f, threshold))
        assertEquals(false, HomeSwipe.isBackToHome(dx = 150f, dy = 400f, threshold))
    }

    @Test
    fun `a short drag is not a swipe`() {
        assertEquals(HomeSwipe.NONE, HomeSwipe.decide(dx = -60f, dy = 0f, threshold))
    }
}

class HomeScrollRangeTest {
    @org.junit.Test
    fun `a list that fits never scrolls`() =
        org.junit.Assert.assertEquals(0, HomeScrollRange.max(rowsBottom = 1900, viewport = 2196))

    @org.junit.Test
    fun `a longer list scrolls exactly to its last row`() =
        org.junit.Assert.assertEquals(300, HomeScrollRange.max(rowsBottom = 2496, viewport = 2196))
}
