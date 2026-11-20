package app.still.listener

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ScrollView
import kotlin.math.abs

/** What a finished drag on the home screen means. */
enum class HomeSwipe {
    OPEN_DRAWER, NONE;

    companion object {
        /** Right-to-left and mostly horizontal opens the drawer; anything vertical is a scroll. */
        fun decide(dx: Float, dy: Float, threshold: Float): HomeSwipe =
            if (dx < -threshold && abs(dx) > 2 * abs(dy)) OPEN_DRAWER else NONE

        /** The mirror gesture, used in the app list: left-to-right and mostly horizontal. */
        fun isBackToHome(dx: Float, dy: Float, threshold: Float): Boolean =
            decide(-dx, dy, threshold) == OPEN_DRAWER

        /** A downward, mostly vertical drag that the list could not use opens notifications. */
        fun isPullDown(dx: Float, dy: Float, threshold: Float, couldScrollUp: Boolean): Boolean =
            !couldScrollUp && dy > threshold && dy > 2 * abs(dx)
    }
}

/** How far the home list may scroll: only as much as its real rows overflow, never more. */
object HomeScrollRange {
    fun max(rowsBottom: Int, viewport: Int): Int = (rowsBottom - viewport).coerceAtLeast(0)
}

/**
 * The scrolling home list. Vertical drags scroll it. It watches touches without consuming them,
 * so rows keep their own taps, and reports a right-to-left swipe and a long press on empty space.
 */
class HomeScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {

    var onSwipeLeft: () -> Unit = {}
    var onLongPressEmpty: () -> Unit = {}
    var onPullDown: () -> Unit = {}

    private val threshold = 60 * resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var couldScrollUpAtDown = false
    private var dragging = false
    private var lastY = 0f
    private var velocity: android.view.VelocityTracker? = null
    private val longPress = Runnable { if (!moved) onLongPressEmpty() }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (handleDrag(event)) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                couldScrollUpAtDown = scrollY > 0
                if (!hitsRow(event.x, event.y)) postDelayed(longPress, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    moved = true
                    removeCallbacks(longPress)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (HomeSwipe.decide(event.x - downX, event.y - downY, threshold) == HomeSwipe.OPEN_DRAWER)
                    onSwipeLeft()
                else if (HomeSwipe.isPullDown(event.x - downX, event.y - downY, threshold, couldScrollUpAtDown))
                    onPullDown()
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * Scrolls the list when a mostly vertical drag passes the touch slop and the rows overflow.
     * Once dragging, the rows get a cancel so they do not also see a tap or a long press.
     */
    private fun handleDrag(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                lastY = event.y
                velocity?.recycle()
                velocity = android.view.VelocityTracker.obtain().apply { addMovement(event) }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                if (!dragging) {
                    val dy = abs(event.y - downY)
                    if (maxScroll() > 0 && dy > touchSlop && dy > abs(event.x - downX)) {
                        dragging = true
                        removeCallbacks(longPress)
                        lastY = event.y
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                    } else return false
                }
                scrollTo(0, scrollY - (event.y - lastY).toInt())
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                velocity?.let {
                    it.addMovement(event)
                    it.computeCurrentVelocity(1000)
                    fling(-it.yVelocity.toInt())
                }
                velocity?.recycle()
                velocity = null
                dragging = false
                return true
            }
        }
        return false
    }

    /**
     * The layout's own height includes empty centring space, so ScrollView would let a list that
     * fits move anyway. The limit is computed from the bottom of the last visible row instead.
     */
    private fun maxScroll(): Int {
        val content = getChildAt(0) as? ViewGroup ?: return 0
        val rowsBottom = rows(content).maxOfOrNull { bottomIn(content, it) } ?: return 0
        return HomeScrollRange.max(rowsBottom + content.paddingBottom, height - paddingTop - paddingBottom)
    }

    private fun bottomIn(ancestor: View, view: View): Int {
        var bottom = view.bottom
        var parent = view.parent as? View
        while (parent != null && parent !== ancestor) {
            bottom += parent.top
            parent = parent.parent as? View
        }
        return bottom
    }

    // ScrollView's own interception never started a drag over the home rows (measured: it
    // returned false on every move although the list overflowed), so dragging is handled in
    // dispatchTouchEvent below and the platform path is switched off.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    override fun onTouchEvent(ev: MotionEvent): Boolean =
        if (maxScroll() > 0) super.onTouchEvent(ev) else true

    override fun scrollTo(x: Int, y: Int) = super.scrollTo(x, y.coerceIn(0, maxScroll()))

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val limit = maxScroll()
        if (t > limit) super.scrollTo(0, limit)
    }

    /** True when the point is on a visible row, which handles its own long press. */
    private fun hitsRow(x: Float, y: Float): Boolean {
        val content = getChildAt(0) as? ViewGroup ?: return false
        val rect = Rect()
        return rows(content).any { row ->
            row.getGlobalVisibleRect(rect)
            val origin = IntArray(2).also { getLocationOnScreen(it) }
            rect.contains((x + origin[0]).toInt(), (y + origin[1]).toInt())
        }
    }

    private fun rows(group: ViewGroup): List<View> = (0 until group.childCount)
        .map { group.getChildAt(it) }
        .filter { it.visibility == View.VISIBLE }
        .flatMap { if (it is ViewGroup) rows(it) else listOf(it) }
}
