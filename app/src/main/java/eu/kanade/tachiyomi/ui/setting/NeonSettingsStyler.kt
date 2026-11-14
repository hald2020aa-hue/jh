package eu.kanade.tachiyomi.ui.setting

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.TextView
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import eu.kanade.tachiyomi.R

/**
 * Presentation-only styling for all existing AndroidX Preference settings screens.
 * Does not replace Preference instances, adapters, keys, values, listeners or persistence.
 * The same adapter continues to perform clicks and bind the original widgets.
 */
internal class NeonSettingsStyler(
    private val list: RecyclerView,
    private val mainMenu: Boolean,
) {
    private val density = list.resources.displayMetrics.density
    private val cyan = Color.rgb(13, 220, 245)
    private val blue = Color.rgb(56, 111, 255)
    private val purple = Color.rgb(154, 78, 245)
    private val pink = Color.rgb(239, 45, 211)
    private val secondary = Color.rgb(177, 194, 217)

    private val spacing = object : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: android.view.View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val pref = preferenceAt(parent.getChildAdapterPosition(view))
            if (pref is PreferenceCategory) {
                if (pref.title.isNullOrBlank()) outRect.set(0, 0, 0, 0)
                else outRect.set(dp(14), dp(9), dp(14), 0)
            } else {
                outRect.set(dp(12), dp(4), dp(12), dp(4))
            }
        }
    }

    private val attachListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: android.view.View) {
            style(view)
        }
        override fun onChildViewDetachedFromWindow(view: android.view.View) = Unit
    }

    init {
        list.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            Color.rgb(6, 16, 35), Color.rgb(8, 15, 31), Color.rgb(17, 10, 37),
        ))
        list.clipToPadding = false
        list.addItemDecoration(spacing)
        list.addOnChildAttachStateChangeListener(attachListener)
        for (i in 0 until list.childCount) style(list.getChildAt(i))
    }

    fun detach() {
        list.removeOnChildAttachStateChangeListener(attachListener)
        list.removeItemDecoration(spacing)
    }

    private fun dp(value: Int): Int = (value * density + .5f).toInt()

    private fun preferenceAt(position: Int): Preference? {
        // PreferenceController may attach or refresh its adapter after view creation.
        val adapter = list.adapter as? PreferenceGroupAdapter ?: return null
        return if (position == RecyclerView.NO_POSITION || position >= adapter.itemCount) null
            else adapter.getItem(position)
    }

    private fun style(view: android.view.View) {
        val preference = preferenceAt(list.getChildAdapterPosition(view)) ?: return
        val category = preference is PreferenceCategory
        val background = NeonPreferenceBackground(density, category)
        view.background = if (category || !preference.isSelectable || !preference.isEnabled) {
            background
        } else {
            RippleDrawable(ColorStateList.valueOf(Color.argb(75, 61, 173, 245)), background, null)
        }
        if (!category) view.minimumHeight = maxOf(view.minimumHeight, dp(56))

        view.findViewById<TextView>(android.R.id.title)?.apply {
            setTextColor(if (category) cyan else Color.WHITE)
            if (category) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        view.findViewById<TextView>(android.R.id.summary)?.setTextColor(secondary)
        // Keep the original icon and click target, but align its visual tint with the app.
        if (mainMenu) {
            view.findViewById<ImageView>(android.R.id.icon)?.imageTintList =
                ColorStateList.valueOf(if (category) pink else cyan)
        }

        // Restore widget state handling to the original Preference; tint only the visuals.
        view.findViewById<MaterialSwitch>(R.id.switchWidget)?.apply {
            val checked = intArrayOf(android.R.attr.state_checked)
            val other = intArrayOf()
            thumbTintList = ColorStateList(arrayOf(checked, other), intArrayOf(Color.WHITE, secondary))
            trackTintList = ColorStateList(arrayOf(checked, other), intArrayOf(purple, Color.rgb(46, 62, 83)))
        }
        view.findViewById<Slider>(R.id.slider)?.apply {
            trackActiveTintList = ColorStateList.valueOf(cyan)
            trackInactiveTintList = ColorStateList.valueOf(Color.rgb(56, 71, 101))
            thumbTintList = ColorStateList.valueOf(pink)
            haloTintList = ColorStateList.valueOf(Color.argb(68, 239, 45, 211))
        }
    }

    private inner class NeonPreferenceBackground(
        density: Float,
        private val category: Boolean,
    ) : Drawable() {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(12, 29, 51)
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (category) 0f else density * 1.2f
        }
        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density * 4.5f
            alpha = 52
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density * 2f
        }
        private val boundsRect = RectF()
        private var lastWidth = -1
        override fun draw(canvas: Canvas) {
            boundsRect.set(bounds)
            if (boundsRect.width() < 2f || boundsRect.height() < 2f) return
            if (lastWidth != bounds.width()) {
                val gradient = LinearGradient(0f, 0f, bounds.width().toFloat(), 0f,
                    intArrayOf(cyan, blue, purple, pink), null, Shader.TileMode.CLAMP)
                borderPaint.shader = gradient
                haloPaint.shader = gradient
                linePaint.shader = gradient
                lastWidth = bounds.width()
            }
            if (category) {
                canvas.drawLine(dp(6).toFloat(), boundsRect.bottom - dp(1),
                    boundsRect.right - dp(6), boundsRect.bottom - dp(1), linePaint)
                return
            }
            val inset = density * .8f
            boundsRect.inset(inset, inset)
            val radius = density * 17f
            canvas.drawRoundRect(boundsRect, radius, radius, backgroundPaint)
            // Tiny blurred-looking rim without expensive blur or bitmap layers.
            canvas.drawRoundRect(boundsRect, radius, radius, haloPaint)
            canvas.drawRoundRect(boundsRect, radius, radius, borderPaint)
        }
        override fun setAlpha(alpha: Int) {
            backgroundPaint.alpha = alpha
            borderPaint.alpha = alpha
            haloPaint.alpha = (alpha * 52 / 255).coerceIn(0, 255)
            linePaint.alpha = alpha
            invalidateSelf()
        }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            backgroundPaint.colorFilter = colorFilter
            borderPaint.colorFilter = colorFilter
            haloPaint.colorFilter = colorFilter
            linePaint.colorFilter = colorFilter
            invalidateSelf()
        }
        @Deprecated("Compatibility with Drawable API")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
