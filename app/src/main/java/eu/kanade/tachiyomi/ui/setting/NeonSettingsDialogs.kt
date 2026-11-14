package eu.kanade.tachiyomi.ui.setting

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.withOriginalWidth

/** Settings-only dialog presentation. The builder and its callbacks remain unchanged. */
internal fun Context.neonSettingsDialog(): MaterialAlertDialogBuilder =
    if (BuildConfig.FLAVOR == "dev") {
        MaterialAlertDialogBuilder(withOriginalWidth(), R.style.ThemeOverlay_J2K_NeonSettingsDialog)
    } else {
        materialAlertDialog()
    }
