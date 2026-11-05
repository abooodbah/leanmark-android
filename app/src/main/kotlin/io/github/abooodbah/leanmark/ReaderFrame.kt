package io.github.abooodbah.leanmark

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

/**
 * The activity's root view. Every touch also goes to [detector]; once two
 * fingers start a pinch, the WebView gets a cancel and the rest of that
 * gesture goes only to the detector, so the page neither scrolls nor
 * magnifies while the reader's own zoom changes.
 */
@SuppressLint("ViewConstructor")
class ReaderFrame(context: Context, private val detector: ScaleGestureDetector) : FrameLayout(context) {
    private var pinching = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) pinching = false
        if (!pinching && detector.isInProgress) {
            pinching = true
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        if (pinching) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) pinching = false
            return true
        }
        return super.dispatchTouchEvent(event)
    }
}
