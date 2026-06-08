package com.scrollingo.app

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

/**
 * Root view for the quiz overlay. Swallows the Back key so the user can't
 * dismiss the quiz without answering it.
 */
class BlockingFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return true // consume, do not let it dismiss the overlay
        }
        return super.dispatchKeyEvent(event)
    }
}
