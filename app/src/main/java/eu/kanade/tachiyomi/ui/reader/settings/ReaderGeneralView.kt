package eu.kanade.tachiyomi.ui.reader.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderGeneralLayoutBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.lang.withSubtitle
import eu.kanade.tachiyomi.widget.BaseReaderSettingsView
import kotlin.collections.toMutableSet
import kotlin.math.roundToInt

class ReaderGeneralView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : BaseReaderSettingsView<ReaderGeneralLayoutBinding>(context, attrs) {
        lateinit var sheet: TabbedReaderSettingsSheet

        override fun inflateBinding() = ReaderGeneralLayoutBinding.bind(this)

        override fun initGeneralPreferences() {
            binding.viewerSeries.onItemSelectedListener = { position ->
                val readingModeType = ReadingModeType.fromSpinner(position)
                (context as ReaderActivity).viewModel.setMangaReadingMode(readingModeType.flagValue)

                val mangaViewer = activity.viewModel.getMangaReadingMode()
                if (mangaViewer == ReadingModeType.WEBTOON.flagValue || mangaViewer == ReadingModeType.CONTINUOUS_VERTICAL.flagValue) {
                    initWebtoonPreferences()
                } else {
                    initPagerPreferences()
                }
                val selectedModes = preferences.readerVerticalSeekbarModes().get()
                binding.verticalSeekbarExtraSettings.isVisible = currentModeSelected(selectedModes)
            }
            binding.viewerSeries.setSelection(
                (context as? ReaderActivity)?.viewModel?.state?.value?.manga?.readingModeType?.let {
                    ReadingModeType.fromPreference(it).prefValue
                } ?: 0,
            )
            binding.rotationMode.onItemSelectedListener = { position ->
                val rotationType = OrientationType.fromSpinner(position)
                (context as ReaderActivity).viewModel.setMangaOrientationType(rotationType.flagValue)
            }
            binding.rotationMode.setSelection(
                (context as ReaderActivity).viewModel.manga?.orientationType?.let {
                    OrientationType.fromPreference(it).prefValue
                } ?: 0,
            )

            binding.backgroundColor.setEntries(
                ReaderBackgroundColor.entries
                    .map { context.getString(it.stringRes) },
            )
            val selection = ReaderBackgroundColor.indexFromPref(preferences.readerTheme().get())
            binding.backgroundColor.setSelection(selection)
            binding.backgroundColor.onItemSelectedListener = { position ->
                val backgroundColor = ReaderBackgroundColor.entries[position]
                preferences.readerTheme().set(backgroundColor.prefValue)
            }
            binding.showPageNumber.bindToPreference(preferences.showPageNumber())
            binding.fullscreen.bindToPreference(preferences.fullscreen())
            binding.keepscreen.bindToPreference(preferences.keepScreenOn())
            binding.alwaysShowChapterTransition.bindToPreference(preferences.alwaysShowChapterTransition())

            initFlashPreferences()
            initVerticalSeekbarPreferences()
        }

        private fun initFlashPreferences() {
            val flashPref = preferences.flashOnPageChange()
            binding.flashExtraSettings.isVisible = flashPref.get()
            binding.flashOnPageChange.bindToPreference(flashPref) { isChecked ->
                binding.flashExtraSettings.isVisible = isChecked
            }

            val durationPref = preferences.flashDurationMillis()
            binding.flashDuration.value = durationPref.get().toFloat()
            binding.flashDuration.setLabelFormatter { value -> "${value.roundToInt()}ms" }
            updateFlashDurationText(durationPref.get())
            binding.flashDuration.addOnChangeListener { _, value, fromUser ->
                updateFlashDurationText(value.roundToInt())
                if (fromUser) durationPref.set(value.roundToInt())
            }

            val intervalPref = preferences.flashPageInterval()
            binding.flashInterval.value = intervalPref.get().toFloat()
            binding.flashInterval.setLabelFormatter { value ->
                context.resources.getQuantityString(R.plurals.pages_plural, value.roundToInt(), value.roundToInt())
            }
            updateFlashIntervalText(intervalPref.get())
            binding.flashInterval.addOnChangeListener { _, value, fromUser ->
                updateFlashIntervalText(value.roundToInt())
                if (fromUser) intervalPref.set(value.roundToInt())
            }

            binding.flashColor.setEntries(
                listOf(
                    context.getString(R.string.flash_black),
                    context.getString(R.string.flash_white),
                    context.getString(R.string.flash_white_then_black),
                ),
            )
            binding.flashColor.bindToPreference(preferences.flashColor())
        }

        private fun updateFlashDurationText(ms: Int) {
            binding.flashDurationText.text =
                context
                    .getString(R.string.flash_duration)
                    .withSubtitle(context, "${ms}ms")
        }

        private fun updateFlashIntervalText(interval: Int) {
            binding.flashIntervalText.text =
                context
                    .getString(R.string.flash_page_interval)
                    .withSubtitle(context, context.resources.getQuantityString(R.plurals.pages_plural, interval, interval))
        }

        fun currentModeSelected(modes: Set<String>): Boolean {
            val activity = context as? ReaderActivity ?: return true
            val currentMode = ReadingModeType.fromPreference(activity.viewModel.getMangaReadingMode())
            return currentMode.prefValue.toString() in modes
        }

        private fun initVerticalSeekbarPreferences() {
            val modesPref = preferences.readerVerticalSeekbarModes()
            val selectedModes = modesPref.get()

            ReadingModeType.entries.filter { it != ReadingModeType.DEFAULT }.forEach { mode ->
                val chip =
                    LayoutInflater.from(context).inflate(
                        R.layout.filter_button,
                        binding.verticalSeekbarModes,
                        false,
                    ) as MaterialButton
                chip.id = generateViewId()
                chip.setText(mode.stringRes)
                chip.isChecked = mode.prefValue.toString() in selectedModes
                chip.addOnCheckedChangeListener { _, isChecked ->
                    val current = modesPref.get().toMutableSet()
                    val key = mode.prefValue.toString()
                    if (isChecked) current.add(key) else current.remove(key)
                    modesPref.set(current)
                    binding.verticalSeekbarExtraSettings.isVisible = currentModeSelected(current)
                }
                binding.verticalSeekbarModes.addView(chip)
            }
            binding.verticalSeekbarExtraSettings.isVisible = currentModeSelected(selectedModes)

            val heightPref = preferences.readerVerticalSeekbarHeightPercent()
            binding.verticalSeekbarHeight.value = heightPref.get().toFloat()
            binding.verticalSeekbarHeight.setLabelFormatter { value -> "${value.roundToInt()}%" }
            updateVerticalSeekbarHeightText(heightPref.get())
            binding.verticalSeekbarHeight.addOnChangeListener { _, value, fromUser ->
                updateVerticalSeekbarHeightText(value.roundToInt())
                if (fromUser) heightPref.set(value.roundToInt())
            }

            val dockLeftPref = preferences.readerVerticalSeekbarDockLeft()
            binding.verticalSeekbarPlacement.check(
                if (dockLeftPref.get()) binding.placeLeftButton.id else binding.placeRightButton.id,
            )
            binding.verticalSeekbarPlacement.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                dockLeftPref.set(checkedId == binding.placeLeftButton.id)
            }
        }

        private fun updateVerticalSeekbarHeightText(percent: Int) {
            binding.verticalSeekbarHeightText.text =
                context
                    .getString(R.string.vertical_seekbar_height)
                    .withSubtitle(context, "$percent%")
        }

        /**
         * Init the preferences for the webtoon reader.
         */
        private fun initWebtoonPreferences() {
            sheet.updateTabs(true)
        }

        private fun initPagerPreferences() {
            sheet.updateTabs(false)
        }
    }
