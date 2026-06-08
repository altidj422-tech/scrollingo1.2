package com.scrollingo.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Draws the blocking quiz on top of whatever app is in the foreground using a
 * TYPE_APPLICATION_OVERLAY window. The overlay stays up until the user answers
 * correctly, then it calls [onSolved].
 */
class QuizOverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var firstAttemptHandled = false

    /**
     * Context used to inflate the overlay and resolve its colors. Because the
     * overlay can be shown from the accessibility *service* (not an Activity),
     * AppCompat's night-mode default doesn't reach it — so we wrap the base
     * context with the user's chosen light/dark setting and use that instead.
     */
    private var ui: Context = context

    val isShowing: Boolean get() = overlayView != null

    fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /**
     * @param onResult invoked once with whether the learner's FIRST tap was
     *   correct (used for spaced-repetition stats); retries afterwards don't fire it.
     * @param onSolved invoked when the correct answer is eventually picked.
     */
    @SuppressLint("InflateParams")
    fun show(
        language: String,
        quiz: Quiz,
        onResult: ((Boolean) -> Unit)? = null,
        onSolved: () -> Unit
    ) {
        if (overlayView != null) return
        if (!canDrawOverlays()) return

        ui = themedContext()
        firstAttemptHandled = false
        val view = LayoutInflater.from(ui).inflate(R.layout.overlay_quiz, null)
        view.findViewById<TextView>(R.id.quiz_lang).text = language
        view.findViewById<TextView>(R.id.quiz_word).text = quiz.word
        view.findViewById<TextView>(R.id.quiz_prompt).text = quiz.prompt

        val feedbackBar = view.findViewById<LinearLayout>(R.id.feedback_bar)
        val feedback = view.findViewById<TextView>(R.id.quiz_feedback)
        val container = view.findViewById<LinearLayout>(R.id.options_container)

        quiz.options.forEachIndexed { index, option ->
            val optionView = buildOption(option)
            optionView.setOnClickListener {
                val isCorrect = index == quiz.correctIndex
                if (!firstAttemptHandled) {
                    firstAttemptHandled = true
                    onResult?.invoke(isCorrect)
                }
                if (isCorrect) {
                    optionView.setBackgroundResource(R.drawable.answer_correct)
                    optionView.setTextColor(ui.getColor(R.color.purple_dark))
                    setOptionsEnabled(container, false)
                    feedbackBar.setBackgroundResource(R.color.purple_pale)
                    feedback.setTextColor(ui.getColor(R.color.purple_dark))
                    feedback.text = "Correct! Keep scrolling 🎉"
                    feedbackBar.visibility = View.VISIBLE
                    mainHandler.postDelayed({
                        dismiss()
                        onSolved()
                    }, 750)
                } else {
                    optionView.setBackgroundResource(R.drawable.answer_wrong)
                    optionView.setTextColor(ui.getColor(R.color.red_dark))
                    optionView.isEnabled = false
                    feedbackBar.setBackgroundResource(R.color.red_light)
                    feedback.setTextColor(ui.getColor(R.color.red_dark))
                    feedback.text = "Not quite — try another"
                    feedbackBar.visibility = View.VISIBLE
                }
            }
            container.addView(optionView)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    fun dismiss() {
        val view = overlayView ?: return
        overlayView = null
        runCatching { windowManager.removeView(view) }
    }

    private fun buildOption(text: String): TextView {
        return TextView(ui).apply {
            this.text = text
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(ui.getColor(R.color.text_dark))
            setBackgroundResource(R.drawable.answer_option)
            setPadding(dp(16), dp(18), dp(16), dp(20))
            isClickable = true
            isFocusable = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(12)
            layoutParams = lp
        }
    }

    private fun setOptionsEnabled(container: LinearLayout, enabled: Boolean) {
        for (i in 0 until container.childCount) {
            container.getChildAt(i).isEnabled = enabled
        }
    }

    /**
     * Wraps the base context with the user's chosen light/dark setting. The
     * overlay can be shown from the accessibility *service* (a plain Service),
     * which never receives AppCompat's night-mode default — so we build a
     * Configuration carrying the right UI_MODE_NIGHT_* flag and inflate against
     * that. When the user follows the system, we just mirror the current config.
     */
    private fun themedContext(): Context {
        val nightMask = when (Prefs.getThemeMode(context)) {
            Prefs.THEME_DARK -> Configuration.UI_MODE_NIGHT_YES
            Prefs.THEME_LIGHT -> Configuration.UI_MODE_NIGHT_NO
            else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }
        val config = Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMask
        return context.createConfigurationContext(config)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
}
