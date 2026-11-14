package eu.kanade.tachiyomi.ui.setting

import android.animation.ArgbEvaluator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import java.util.WeakHashMap
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.Preference
import androidx.preference.PreferenceController
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.BackHandlerControllerInterface
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import kotlinx.coroutines.MainScope
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

abstract class SettingsController :
    PreferenceController(),
    BackHandlerControllerInterface {
    var preferenceKey: String? = null
    val preferences: PreferencesHelper = Injekt.get()
    val viewScope = MainScope()
    private var neonSettingsStyler: NeonSettingsStyler? = null
    private var leasedSettingsToolbar: View? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        listView.layoutManager = LinearLayoutManagerAccurateOffset(view.context)
        if (BuildConfig.FLAVOR == "dev") {
            neonSettingsStyler = NeonSettingsStyler(listView, this is SettingsMainController)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedInstanceState: Bundle?,
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        view.backgroundColor = if (BuildConfig.FLAVOR == "dev") Color.rgb(6, 16, 35)
            else view.context.getResourceColor(R.attr.background)
        // The shared expanded app bar contains the settings title and (on the main
        // settings screen) an extra search field. Its visual overlay is taller than
        // the toolbar height measured by scrollViewWith. Without extra top padding,
        // the first Preference card starts behind the opaque header and is clipped.
        // Apply the gap AFTER scrollViewWith recalculates its insets (including on
        // rotation). The callback receives a fresh base padding, so it cannot stack.
        scrollViewWith(listView, padBottom = true, afterInsets = {
            if (BuildConfig.FLAVOR == "dev") {
                listView.updatePaddingRelative(top = listView.paddingTop + 112.dpToPx)
            }
        })
        return view
    }

    override fun onDestroyView(view: View) {
        restoreSettingsToolbar()
        neonSettingsStyler?.detach()
        neonSettingsStyler = null
        super.onDestroyView(view)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        preferenceKey?.let { prefKey ->
            val adapter = listView.adapter
            scrollToPreference(prefKey)

            listView.post {
                if (adapter is PreferenceGroup.PreferencePositionCallback) {
                    val pos = adapter.getPreferenceAdapterPosition(prefKey)
                    listView.findViewHolderForAdapterPosition(pos)?.let {
                        animatePreferenceHighlight(it.itemView)
                    }
                    preferenceKey = null
                }
            }
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val screen = preferenceManager.createPreferenceScreen(getThemedContext())
        preferenceScreen = screen
        setupPreferenceScreen(screen)
    }

    abstract fun setupPreferenceScreen(screen: PreferenceScreen): PreferenceScreen

    open fun onActionViewExpand(item: MenuItem?) { }

    open fun onActionViewCollapse(item: MenuItem?) { }

    private fun getThemedContext(): Context {
        val tv = TypedValue()
        activity!!.theme.resolveAttribute(R.attr.preferenceTheme, tv, true)
        return ContextThemeWrapper(activity, tv.resourceId)
    }

    private fun animatePreferenceHighlight(view: View) {
        // A search jump must not replace the neon card's background permanently.
        val originalForeground = view.foreground
        val highlight = ColorDrawable(Color.TRANSPARENT)
        view.foreground = highlight
        ValueAnimator.ofObject(
            ArgbEvaluator(),
            Color.TRANSPARENT,
            view.context.getResourceColor(R.attr.colorControlHighlight),
        ).apply {
            duration = 500L
            repeatCount = 2
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator -> highlight.color = animator.animatedValue as Int }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.foreground = originalForeground
                }
                override fun onAnimationCancel(animation: Animator) {
                    view.foreground = originalForeground
                }
            })
            start()
        }
    }

    open fun getTitle(): String? = preferenceScreen?.title?.toString()

    open fun getSearchTitle(): String? =
        if (this is FloatingSearchInterface) {
            searchTitle(preferenceScreen?.title?.toString()?.lowercase(Locale.ROOT))
        } else {
            null
        }

    fun setTitle() {
        var parentController = parentController
        while (parentController != null) {
            if (parentController is BaseController<*> && parentController.getTitle() != null) {
                return
            }
            parentController = parentController.parentController
        }

        (activity as? AppCompatActivity)?.title = getTitle()
        (activity as? MainActivity)?.searchTitle = getSearchTitle()
        activityBinding?.bigIconLayout?.isVisible = false
    }

    // The app bar belongs to MainActivity, not this controller. Multiple settings pages can
    // overlap during a Conductor transition, so only restore its original background when
    // the LAST settings controller exits. This prevents a lingering neon toolbar elsewhere.
    private fun decorateSettingsToolbar() {
        if (BuildConfig.FLAVOR != "dev" || leasedSettingsToolbar != null) return
        val toolbar = activityBinding?.toolbar ?: return
        val current = settingsToolbars[toolbar]
        if (current == null) {
            val neon = AppCompatResources.getDrawable(toolbar.context, R.drawable.v13_settings_toolbar) ?: return
            settingsToolbars[toolbar] = ToolbarLease(toolbar.background, neon, 1)
            toolbar.background = neon
        } else {
            current.owners += 1
            if (toolbar.background !== current.neon) toolbar.background = current.neon
        }
        leasedSettingsToolbar = toolbar
    }

    private fun restoreSettingsToolbar() {
        val toolbar = leasedSettingsToolbar ?: return
        leasedSettingsToolbar = null
        val lease = settingsToolbars[toolbar] ?: return
        lease.owners -= 1
        if (lease.owners <= 0) {
            if (toolbar.background === lease.neon) toolbar.background = lease.original
            settingsToolbars.remove(toolbar)
        }
    }

    private class ToolbarLease(
        val original: Drawable?,
        val neon: Drawable,
        var owners: Int,
    )

    private companion object {
        val settingsToolbars = WeakHashMap<View, ToolbarLease>()
    }

    override fun onChangeStarted(
        handler: ControllerChangeHandler,
        type: ControllerChangeType,
    ) {
        if (type.isEnter && isControllerVisible) {
            setTitle()
        } else if (type.isEnter) {
            view?.alpha = 0f
        }
        if (type.isEnter) decorateSettingsToolbar() else restoreSettingsToolbar()
        setHasOptionsMenu(type.isEnter && isControllerVisible)
        super.onChangeStarted(handler, type)
    }

    inline fun <T> Preference.visibleIf(
        preference: com.fredporciuncula.flow.preferences.Preference<T>,
        crossinline block: (T) -> Boolean,
    ) {
        preference.asImmediateFlowIn(viewScope) { isVisible = block(it) }
    }
}
