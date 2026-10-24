package com.ipxtream.tv.ui.theme

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Lets phones and tablets use the TV interface by touch.
 *
 * androidx.tv.material3 buttons, cards and surfaces react only to the remote's OK key, so a tap on
 * them did nothing. Each Activity passes its touch events through [dispatch]: a still, short tap on a
 * clickable focuses it and runs its click, a long press runs its long click. Drags (list scrolling)
 * and text fields go to Compose untouched. TVs send no touch events, so
 * this never runs there.
 */
class TouchBridge(private val activity: Activity) {

    private val slop = ViewConfiguration.get(activity).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var target: SemanticsNode? = null
    private var tracking = false
    private var longPressDone = false
    private val longPress = Runnable {
        val node = target ?: return@Runnable
        val action = node.config.getOrNull(SemanticsActions.OnLongClick)?.action ?: return@Runnable
        longPressDone = true
        node.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
        action.invoke()
    }

    /** Returns true when the bridge handled the event; otherwise pass it on to the Activity. */
    fun dispatch(event: MotionEvent, superDispatch: (MotionEvent) -> Boolean): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                target = clickableAt(event.x, event.y)
                if (target != null) {
                    tracking = true
                    downX = event.x; downY = event.y; downAt = SystemClock.uptimeMillis()
                    root()?.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> if (tracking &&
                (Math.abs(event.x - downX) > slop || Math.abs(event.y - downY) > slop)) {
                // A drag: it scrolls the list underneath, not a click.
                reset()
            }
            MotionEvent.ACTION_POINTER_DOWN -> reset()
            MotionEvent.ACTION_UP -> if (tracking) {
                val node = target
                val handled = longPressDone
                reset()
                if (!handled && node != null) {
                    node.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
                    tapping = true
                    try { node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() }
                    finally { tapping = false }
                }
                // Compose saw the DOWN; cancel instead of UP so nothing else also clicks.
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                superDispatch(cancel)
                cancel.recycle()
                return true
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return superDispatch(event)
    }

    private fun reset() {
        root()?.removeCallbacks(longPress)
        tracking = false
        longPressDone = false
        target = null
    }

    private fun root(): View? = activity.window?.decorView

    companion object {
        /** True while a click runs because of a tap, for controls whose remote and touch behaviour differ. */
        var tapping = false
            private set
    }

    /**
     * The smallest enabled clickable under the finger. Ordinary Compose clickables go through here
     * too: the bridge cancels the touch it handled, so nothing clicks twice.
     */
    private fun clickableAt(x: Float, y: Float): SemanticsNode? {
        val compose = findCompose(root() ?: return null) ?: return null
        val point = Offset(x, y)
        return allNodes(compose.semanticsOwner.rootSemanticsNode)
            .filter { it.config.contains(SemanticsActions.OnClick) }
            .filter { !it.config.contains(SemanticsProperties.Disabled) }
            .filter { !it.config.contains(SemanticsActions.SetText) }  // text fields handle touch
            .filter { it.boundsInWindow.contains(point) }
            .minByOrNull { it.boundsInWindow.width * it.boundsInWindow.height }
    }

    private fun allNodes(node: SemanticsNode): Sequence<SemanticsNode> =
        sequenceOf(node) + node.children.asSequence().flatMap { allNodes(it) }

    private fun findCompose(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findCompose(view.getChildAt(i))?.let { return it }
        return null
    }
}
