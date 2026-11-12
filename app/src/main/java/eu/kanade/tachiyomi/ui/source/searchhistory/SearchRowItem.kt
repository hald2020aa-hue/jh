package eu.kanade.tachiyomi.ui.source.searchhistory

import android.view.View
import androidx.core.view.isVisible
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SearchHistoryItemBinding
import eu.kanade.tachiyomi.util.view.makeContainerShape

/**
 * One row shared by both the "Recent searches" and "Saved searches" sections - [entry] is a
 * saved search when [SearchHistoryEntry.name] is set, a plain recent search otherwise, the same
 * way blank/non-blank [SearchHistoryEntry.query] already distinguishes a snapshot from a real
 * search elsewhere in this feature.
 */
class SearchRowItem(
    val entry: SearchHistoryEntry,
    private val isTopOfGroup: Boolean,
    private val isBottomOfGroup: Boolean,
    private val onFillClicked: (String) -> Unit,
    private val onTrailingClicked: (SearchHistoryEntry) -> Unit,
) : AbstractItem<FastAdapter.ViewHolder<SearchRowItem>>() {
    override val type: Int = R.id.history_card

    override val layoutRes: Int = R.layout.search_history_item

    override var identifier = entry.id

    override fun getViewHolder(v: View): FastAdapter.ViewHolder<SearchRowItem> = ViewHolder(v)

    class ViewHolder(
        view: View,
    ) : FastAdapter.ViewHolder<SearchRowItem>(view),
        ISwipeableViewHolder {
        private val binding = SearchHistoryItemBinding.bind(view)

        override val swipeableView: View = binding.historyCard
        override val leftBackView: View = binding.leftBackView
        override val rightBackView: View = binding.rightBackView

        override fun bindView(
            item: SearchRowItem,
            payloads: List<Any>,
        ) {
            val entry = item.entry
            val isSaved = entry.name != null
            binding.historyCard.translationX = 0f
            binding.backView.isVisible = false
            binding.historyIcon.setImageResource(if (isSaved) R.drawable.ic_star_24dp else R.drawable.ic_history_24dp)
            // a filter-only snapshot has a blank query - nothing to show as a title then
            binding.title.isVisible = isSaved || entry.query.isNotBlank()
            binding.title.text = entry.name ?: entry.query
            val shape =
                binding.historyCard.makeContainerShape(
                    item.isTopOfGroup,
                    item.isBottomOfGroup,
                    clipContentTo = binding.frontView,
                )
            binding.historyCard.shapeAppearanceModel = shape
            binding.backView.shapeAppearanceModel = shape

            // a recent row's title already shows the query, so repeating it in the subtitle would
            // be redundant - a saved row's title is its name instead, so the query only shows here
            val filtersText =
                entry.filters
                    .takeIf { it.isNotEmpty() }
                    ?.let { binding.root.context.getString(R.string.parenthesis, it.joinToString { f -> f.filterName }) }
            val summary =
                if (isSaved) {
                    listOfNotNull(entry.query.takeIf { it.isNotBlank() }, filtersText).joinToString(" ")
                } else {
                    filtersText.orEmpty()
                }
            binding.subtitle.isVisible = summary.isNotBlank()
            binding.subtitle.text = summary.unitalicizeArrows()

            binding.fillButton.isVisible = entry.filters.isEmpty()
            binding.fillButton.setOnClickListener { item.onFillClicked(entry.query) }

            binding.saveButton.contentDescription =
                binding.root.context.getString(if (isSaved) R.string.edit else R.string.save)
            binding.saveButton.setIconResource(if (isSaved) R.drawable.ic_edit_24dp else R.drawable.ic_outline_save_24dp)
            binding.saveButton.setOnClickListener { item.onTrailingClicked(entry) }
        }

        override fun unbindView(item: SearchRowItem) {
            binding.title.text = null
            binding.fillButton.setOnClickListener(null)
            binding.saveButton.setOnClickListener(null)
            // so a recycled holder doesn't reappear mid-swipe from whatever row it last showed
            binding.historyCard.translationX = 0f
            binding.backView.isVisible = false
        }
    }
}

public interface ISwipeableViewHolder {
    public abstract val swipeableView: View
    public abstract val leftBackView: View
    public abstract val rightBackView: View
}
