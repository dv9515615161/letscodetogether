package com.ridescore.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.ridescore.app.domain.model.Decision
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.settings.OverlayMode
import com.ridescore.app.domain.settings.RideScoreSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The floating advisory card.
 *
 * Built from plain Views rather than Compose on purpose: this window is created
 * from a Service, is updated several times a second while an offer is on
 * screen, and must be visible the moment the offer is. Setting text on views
 * that already exist costs microseconds and no recomposition machinery.
 *
 * Placement and behaviour are chosen so it never gets in the way:
 *  - it defaults to the upper part of the screen, clear of the Accept button
 *    both apps put at the bottom,
 *  - it is small, and the driver can drag it anywhere,
 *  - the window is not focusable and not touch-modal, so every touch outside
 *    the card goes straight through to the driver app underneath.
 */
class OverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var card: LinearLayout? = null
    private var headerView: TextView? = null
    private var primaryView: TextView? = null
    private var captionView: TextView? = null
    private var detailViews: List<TextView> = emptyList()
    private var otherViews: List<TextView> = emptyList()
    private var footerView: TextView? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var attached = false

    /** A tap on the card flips detail on for the current session only. */
    private var expandedByTap = false

    private val positionStore = OverlayPositionStore(context)

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    /** True while the card is on screen. */
    val isVisible: Boolean get() = attached

    /**
     * @return false when the overlay permission is missing, so the caller can
     *   tell the driver instead of failing silently.
     */
    fun show(analysis: ScreenAnalysis, settings: RideScoreSettings): Boolean {
        if (!canDraw()) return false

        val effective =
            if (expandedByTap) settings.copy(overlayMode = OverlayMode.DETAILED) else settings
        val content = OverlayPresenter.present(analysis, effective) ?: run {
            hide()
            return true
        }

        val view = card ?: createCard().also { card = it }
        bind(content, effective)

        if (!attached) {
            runCatching { windowManager.addView(view, params(effective)) }
                .onFailure { return false }
            attached = true
        } else {
            runCatching { windowManager.updateViewLayout(view, layoutParams) }
        }
        return true
    }

    fun hide() {
        val view = card ?: return
        if (!attached) return
        runCatching { windowManager.removeView(view) }
        attached = false
    }

    fun destroy() {
        hide()
        card = null
        headerView = null
        primaryView = null
        captionView = null
        detailViews = emptyList()
        otherViews = emptyList()
        footerView = null
    }

    // ------------------------------------------------------------- rendering

    private fun bind(content: OverlayContent, settings: RideScoreSettings) {
        val accent = accentColor(content.decision, content.lowConfidence)

        headerView?.apply {
            text = content.header
            setTextColor(accent)
        }
        val scale = settings.overlayTextScale.coerceIn(0.8f, 2.0f)

        headerView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADER_SP * scale)
        primaryView?.apply {
            maxLines = if (content.decision == Decision.CHECK) 2 else 1
            text = content.primary
            setTextColor(Color.WHITE)
            // The rate is the thing being read at a glance, so it gets the
            // biggest type on the card in both modes.
            setTextSize(TypedValue.COMPLEX_UNIT_SP, PRIMARY_SP * scale)
        }
        captionView?.apply {
            text = content.primaryCaption ?: ""
            visibility = if (content.primaryCaption == null) View.GONE else View.VISIBLE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, CAPTION_SP * scale)
        }
        detailViews.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, DETAIL_SP * scale) }
        otherViews.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, OTHER_SP * scale) }
        footerView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, FOOTER_SP * scale)

        detailViews.forEachIndexed { i, view ->
            val line = content.detailLines.getOrNull(i)
            view.text = line ?: ""
            view.visibility = if (line == null) View.GONE else View.VISIBLE
        }
        otherViews.forEachIndexed { i, view ->
            val line = content.otherOffers.getOrNull(i)
            view.text = line ?: ""
            view.visibility = if (line == null) View.GONE else View.VISIBLE
        }
        footerView?.apply {
            text = content.footer ?: ""
            visibility = if (content.footer == null) View.GONE else View.VISIBLE
        }

        card?.background = cardBackground(accent)
        val width = (if (settings.overlayMode == OverlayMode.QUICK) QUICK_WIDTH_DP else DETAILED_WIDTH_DP) * scale
        card?.minimumWidth = dp(width.toInt())
        val maxWidth = dp((DETAILED_WIDTH_DP * scale).toInt())
        (detailViews + otherViews + listOfNotNull(headerView, primaryView, captionView, footerView))
            .forEach { it.maxWidth = maxWidth }
    }

    private fun accentColor(decision: Decision, lowConfidence: Boolean): Int = when {
        lowConfidence && decision != Decision.CHECK -> COLOR_MAYBE
        decision == Decision.ACCEPT -> COLOR_ACCEPT
        decision == Decision.MAYBE -> COLOR_MAYBE
        decision == Decision.REJECT -> COLOR_REJECT
        else -> COLOR_CHECK
    }

    private fun cardBackground(accent: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(12).toFloat()
        setColor(CARD_BACKGROUND)
        setStroke(dp(2), accent)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCard(): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = cardBackground(COLOR_CHECK)
            minimumWidth = dp(QUICK_WIDTH_DP)
        }

        headerView = textView(HEADER_SP, bold = true).also { root.addView(it) }
        primaryView = textView(PRIMARY_SP, bold = true).also { root.addView(it) }
        captionView = textView(CAPTION_SP, color = Color.parseColor("#9A9A9A"))
            .also { root.addView(it) }
        detailViews = List(5) {
            textView(DETAIL_SP, color = Color.parseColor("#D8D8D8")).also { root.addView(it) }
        }
        otherViews = List(OverlayPresenter.MAX_RANKED_SHOWN - 1) {
            textView(OTHER_SP, color = Color.parseColor("#B0B0B0")).also { root.addView(it) }
        }
        footerView = textView(FOOTER_SP, color = Color.parseColor("#7A7A7A")).also { root.addView(it) }

        root.setOnTouchListener(DragListener())
        return root
    }

    private fun textView(sizeSp: Float, bold: Boolean = false, color: Int = Color.WHITE) =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(DETAILED_WIDTH_DP)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    // ---------------------------------------------------------------- window

    private fun params(settings: RideScoreSettings): WindowManager.LayoutParams {
        val existing = layoutParams
        if (existing != null) return existing

        val metrics = context.resources.displayMetrics
        val defaultX = (metrics.widthPixels - dp(DETAILED_WIDTH_DP) - dp(12)).coerceAtLeast(0)
        val defaultY = dp(96)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                // Best effort only. Stock Android ignores this for overlay
                // windows and hides them behind the keyguard; a few ROMs honour
                // it. Voice is the reliable answer on the lock screen.
                @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = positionStore.x(defaultX)
            y = positionStore.y(defaultY)
        }
        layoutParams = p
        return p
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private inner class DragListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private val slop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val p = layoutParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x
                    startY = p.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = startX + (event.rawX - touchX).roundToInt()
                    p.y = startY + (event.rawY - touchY).roundToInt()
                    runCatching { windowManager.updateViewLayout(v, p) }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - touchX) > slop || abs(event.rawY - touchY) > slop
                    if (moved) {
                        positionStore.save(p.x, p.y)
                    } else {
                        expandedByTap = !expandedByTap
                        v.performClick()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    companion object {
        const val QUICK_WIDTH_DP = 170
        const val DETAILED_WIDTH_DP = 210

        // Base sizes, multiplied by the driver's card-size setting.
        const val HEADER_SP = 14f
        const val PRIMARY_SP = 30f
        const val CAPTION_SP = 10f
        const val DETAIL_SP = 13f
        const val OTHER_SP = 12f
        const val FOOTER_SP = 9f

        private val CARD_BACKGROUND = Color.parseColor("#F0101418")
        private val COLOR_ACCEPT = Color.parseColor("#33D17A")
        private val COLOR_MAYBE = Color.parseColor("#F5C211")
        private val COLOR_REJECT = Color.parseColor("#FF5C5C")
        private val COLOR_CHECK = Color.parseColor("#C0C0C0")
    }
}
