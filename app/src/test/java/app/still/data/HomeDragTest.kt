package app.still.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDragTest {

    // Four rows, 100 px high, centres at 50, 150, 250, 350.
    private val centres = listOf(50f, 150f, 250f, 350f)

    @Test
    fun `a row that has not moved past a neighbour keeps its place`() {
        assertEquals(1, HomeDrag.target(centres, from = 1, draggedCentre = 180f))
    }

    @Test
    fun `dragging past the centre of the row below takes its place`() {
        assertEquals(2, HomeDrag.target(centres, from = 1, draggedCentre = 260f))
    }

    @Test
    fun `dragging to the very top lands first`() {
        assertEquals(0, HomeDrag.target(centres, from = 3, draggedCentre = -40f))
    }

    @Test
    fun `dragging far below lands last`() {
        assertEquals(3, HomeDrag.target(centres, from = 0, draggedCentre = 900f))
    }
}
