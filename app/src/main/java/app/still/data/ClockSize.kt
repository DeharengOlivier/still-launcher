package app.still.data

/** The clock sizes offered in settings, as multipliers of the default clock size. */
object ClockSize {

    val STEPS = listOf(0.6f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f)

    /** The step after [current], wrapping to the smallest; an unknown value goes back to 1.0. */
    fun next(current: Float): Float {
        val index = STEPS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        if (index < 0) return 1.0f
        return STEPS[(index + 1) % STEPS.size]
    }
}
