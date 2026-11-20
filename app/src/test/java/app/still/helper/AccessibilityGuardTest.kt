package app.still.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityGuardTest {

    private val gesture = "com.h3nc4.hypergesture/com.h3nc4.hypergesture.service.HyperGestureAccessibilityService"
    private val other = "com.example/.OtherService"

    @Test
    fun `suspending removes every enabled service and remembers them`() {
        val plan = AccessibilityGuard.suspend(enabled = "$gesture:$other", alreadySaved = null)!!

        assertEquals("", plan.enabled)
        assertEquals("$gesture:$other", plan.saved)
    }

    @Test
    fun `suspending with nothing enabled changes nothing`() {
        val plan = AccessibilityGuard.suspend(enabled = null, alreadySaved = null)

        assertNull(plan)
    }

    @Test
    fun `suspending twice keeps the services saved the first time`() {
        val plan = AccessibilityGuard.suspend(enabled = other, alreadySaved = gesture)!!

        assertEquals("", plan.enabled)
        assertEquals("$gesture:$other", plan.saved)
    }

    @Test
    fun `restoring brings back the saved services`() {
        val plan = AccessibilityGuard.restore(enabled = "", saved = gesture)!!

        assertEquals(gesture, plan.enabled)
        assertNull(plan.saved)
    }

    @Test
    fun `restoring keeps a service the user enabled meanwhile without duplicates`() {
        val plan = AccessibilityGuard.restore(enabled = "$other:$gesture", saved = gesture)!!

        assertEquals("$other:$gesture", plan.enabled)
    }

    @Test
    fun `restoring with nothing saved does nothing`() {
        assertNull(AccessibilityGuard.restore(enabled = other, saved = null))
        assertNull(AccessibilityGuard.restore(enabled = other, saved = ""))
    }

    @Test
    fun `blank and duplicate entries are ignored`() {
        val plan = AccessibilityGuard.suspend(enabled = " $gesture::$gesture ", alreadySaved = null)!!

        assertEquals(gesture, plan.saved)
    }
}
