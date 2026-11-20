package app.still.data

/** Where a home row being dragged would land. */
object HomeDrag {

    /**
     * [centres] are the vertical centres of the rows at rest, [from] is the dragged row and
     * [draggedCentre] its current centre. The landing index is the number of other rows whose
     * centre is above the dragged one.
     */
    fun target(centres: List<Float>, from: Int, draggedCentre: Float): Int =
        centres.withIndex().count { (index, centre) -> index != from && centre < draggedCentre }
}
