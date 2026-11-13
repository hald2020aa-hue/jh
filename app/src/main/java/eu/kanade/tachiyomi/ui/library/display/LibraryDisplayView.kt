package eu.kanade.tachiyomi.ui.library.display

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.google.android.material.slider.Slider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.ui.enhanced.NeonGridMode
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.LibraryDisplayLayoutBinding
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.lang.addBetaTag
import eu.kanade.tachiyomi.util.lang.withSubtitle
import eu.kanade.tachiyomi.util.system.bottomCutoutInset
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isLandscape
import eu.kanade.tachiyomi.util.system.topCutoutInset
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.util.view.numberOfRowsForValue
import eu.kanade.tachiyomi.util.view.rowsForValue
import eu.kanade.tachiyomi.widget.BaseLibraryDisplayView
import kotlin.math.roundToInt

class LibraryDisplayView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : BaseLibraryDisplayView<LibraryDisplayLayoutBinding>(context, attrs) {
        var mainView: View? = null

        override fun inflateBinding() = LibraryDisplayLayoutBinding.bind(this)

        override fun initGeneralPreferences() {
            binding.displayGroup.bindToPreference(preferences.libraryLayout())
            binding.v5DisplayModes.visibility = if (resources.getBoolean(R.bool.v4_neon_mode)) View.VISIBLE else View.GONE
            binding.v5DisplayModes.setOnClickListener {
                val modes = arrayOf("قائمة", "كبير — عمودان", "متوسط — 3 أعمدة", "صغير — 5 أعمدة")
                val previous = if (preferences.libraryLayout().get() == LibraryItem.LAYOUT_LIST) 0 else when (NeonGridMode.getColumns(context)) {
                    NeonGridMode.LARGE -> 1
                    NeonGridMode.SMALL -> 3
                    else -> 2
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle("طريقة عرض المانجا")
                    .setSingleChoiceItems(modes, previous) { dialog, choice ->
                        if (choice == 0) {
                            preferences.libraryLayout().set(LibraryItem.LAYOUT_LIST)
                        } else {
                            NeonGridMode.setColumns(context, when (choice) {
                                1 -> NeonGridMode.LARGE
                                3 -> NeonGridMode.SMALL
                                else -> NeonGridMode.MEDIUM
                            })
                            preferences.libraryLayout().set(LibraryItem.LAYOUT_COMFORTABLE_GRID)
                            controller?.applyNeonGridMode()
                        }
                        dialog.dismiss()
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }
            binding.uniformGrid.bindToPreference(preferences.uniformGrid()) {
                binding.staggeredGrid.isEnabled = !it
            }
            binding.outlineOnCovers.bindToPreference(preferences.outlineOnCovers())
            binding.staggeredGrid.text = context.getString(R.string.use_staggered_grid).addBetaTag(context)
            binding.staggeredGrid.isEnabled = !preferences.uniformGrid().get()
            binding.staggeredGrid.bindToPreference(preferences.useStaggeredGrid())
            binding.gridSeekbar.value = ((preferences.gridSize().get() + .5f) * 2f).roundToInt().toFloat()
            binding.resetGridSize.setOnClickListener {
                binding.gridSeekbar.value = 3f
            }

            binding.gridSeekbar.setLabelFormatter {
                val view = controller?.activity?.window?.decorView ?: mainView ?: this@LibraryDisplayView
                val mainText = (mainView ?: this@LibraryDisplayView).rowsForValue(it).toString()
                val mainOrientation =
                    context.getString(
                        if (context.isLandscape()) {
                            R.string.landscape
                        } else {
                            R.string.portrait
                        },
                    )
                val alt =
                    (
                        if (view.measuredHeight >= 720.dpToPx) {
                            view.measuredHeight - 72.dpToPx
                        } else {
                            view.measuredHeight
                        }
                    ) -
                        (view.rootWindowInsets?.topCutoutInset() ?: 0) -
                        (view.rootWindowInsets?.bottomCutoutInset() ?: 0)
                val altText = alt.numberOfRowsForValue(it).toString()
                val altOrientation =
                    context.getString(
                        if (context.isLandscape()) {
                            R.string.portrait
                        } else {
                            R.string.landscape
                        },
                    )
                "$mainOrientation: $mainText • $altOrientation: $altText"
            }
            binding.gridSeekbar.addOnChangeListener { _, value, fromUser ->
                if (!fromUser) {
                    preferences.gridSize().set((value / 2f) - .5f)
                }
                setGridText(value)
            }
            binding.gridSeekbar.addOnSliderTouchListener(
                object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {}

                    override fun onStopTrackingTouch(slider: Slider) {
                        preferences.gridSize().set((slider.value / 2f) - .5f)
                        setGridText(slider.value)
                    }
                },
            )
        }

        override fun onFinishInflate() {
            super.onFinishInflate()
            checkHeightThen {
                setGridText(binding.gridSeekbar.value)
            }
        }

        private fun setGridText(progress: Float) {
            with(binding.gridSizeText) {
                val rows = (mainView ?: this@LibraryDisplayView).rowsForValue(progress)
                val titleText = context.getString(R.string.grid_size)
                val subtitleText = context.getString(R.string._per_row, rows)
                text = titleText.withSubtitle(context, subtitleText)
            }
        }
    }
