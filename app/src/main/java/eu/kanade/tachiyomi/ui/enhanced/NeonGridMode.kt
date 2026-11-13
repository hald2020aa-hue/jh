package eu.kanade.tachiyomi.ui.enhanced

import android.content.Context
import androidx.preference.PreferenceManager

/** Four independently selectable layouts for browse/library; setting survives app restarts. */
object NeonGridMode {
    private const val KEY = "v5_manga_grid_columns"
    const val AUTO = 0
    const val LARGE = 2
    const val MEDIUM = 3
    const val SMALL = 5

    fun getColumns(context: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(context).getInt(KEY, MEDIUM)
            .takeIf { it == LARGE || it == MEDIUM || it == SMALL } ?: MEDIUM

    fun setColumns(context: Context, columns: Int) {
        require(columns == LARGE || columns == MEDIUM || columns == SMALL)
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(KEY, columns).apply()
    }
}
