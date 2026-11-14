package eu.kanade.tachiyomi.ui.reader

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import kotlin.math.min

/**
 * Dev visual skin for the nine *existing* V9 translation actions. No new menu
 * commands are created, and all clicks still use ReaderActivity's existing
 * onOptionsItemSelected/MenuItem IDs. If the overflow view cannot be located,
 * the ordinary Android overflow continues to work unchanged.
 */
internal object NeonReaderTranslationMenu {
    private val actions = listOf(
        R.id.action_translate_chapter to R.drawable.ic_translate_24dp,
        R.id.action_translation_auto to R.drawable.ic_refresh_24dp,
        R.id.action_translation_edit to R.drawable.ic_edit_24dp,
        R.id.action_translation_diagnostics to R.drawable.ic_language_24dp,
        R.id.action_translation_page to R.drawable.ic_book_open_variant_24dp,
        R.id.action_translation_toggle to R.drawable.ic_swap_vert_24dp,
        R.id.action_translation_appearance to R.drawable.ic_edit_24dp,
        R.id.action_translation_retry to R.drawable.ic_refresh_24dp,
        R.id.action_translation_stop to R.drawable.ic_close_24dp,
    )

    fun attach(toolbar: Toolbar, menu: Menu, dispatch: (MenuItem) -> Unit) {
        // The reader uses an AppCompat ActionMenuView. Run after menu inflation,
        // without changing the menu XML, its IDs, or the standard flavor.
        toolbar.post {
            val actionMenu = (0 until toolbar.childCount)
                .map(toolbar::getChildAt)
                .filterIsInstance<ActionMenuView>()
                .firstOrNull() ?: return@post
            val overflow = (0 until actionMenu.childCount)
                .map(actionMenu::getChildAt)
                .firstOrNull { it is ImageView } ?: return@post
            overflow.setOnClickListener {
                if (overflow.windowToken != null) show(overflow, menu, dispatch)
            }
        }
    }

    private fun show(anchor: View, menu: Menu, dispatch: (MenuItem) -> Unit) {
        val context = anchor.context
        val dp = context.resources.displayMetrics.density
        fun dip(value: Int) = (value * dp + .5f).toInt()
        val width = min(context.resources.displayMetrics.widthPixels - dip(24), dip(350))
        val popup = PopupWindow(context).apply {
            this.width = width
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            isFocusable = true
            isOutsideTouchable = true
            elevation = dip(9).toFloat()
            setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.v11_neon_translation_popup))
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(12), dip(14), dip(12), dip(14))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val entries = actions.mapNotNull { (id, icon) ->
            menu.findItem(id)?.takeIf { it.isVisible }?.let { item -> item to icon }
        }
        if (entries.isEmpty()) return
        entries.forEachIndexed { index, (menuItem, iconRes) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dip(10), dip(3), dip(10), dip(3))
                setBackgroundResource(R.drawable.v11_neon_translation_row)
                minimumHeight = dip(52)
                isClickable = true
                isFocusable = true
                contentDescription = menuItem.title
                setOnClickListener {
                    popup.dismiss()
                    dispatch(menuItem)
                }
            }
            val icon = ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(
                    Color.parseColor(if (index % 2 == 0) "#EF45F4" else "#22DAFA"),
                )
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val iconParams = LinearLayout.LayoutParams(dip(28), dip(28)).apply {
                marginStart = dip(8)
                marginEnd = dip(10)
            }
            row.addView(icon, iconParams)
            val title = TextView(context).apply {
                text = menuItem.title
                setTextColor(Color.WHITE)
                textSize = 15f
                maxLines = 2
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dip(5) })
        }
        val scroller = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            addView(container)
        }
        popup.contentView = scroller
        // The real reader toolbar still owns the overflow placement, back button,
        // and menu actions. This visual panel never overlays/changes manga pixels.
        popup.showAsDropDown(anchor, 0, 0, Gravity.END)
    }
}
