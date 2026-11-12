package eu.kanade.tachiyomi.ui.source.searchhistory

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.util.system.toInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A source-independent snapshot of one changed [Filter]'s state, keyed by [name] (not position)
 * so it can be matched against a *different* source's filter list later.
 */
@Serializable
sealed class SavedFilter {
    abstract val name: String
    abstract val filterName: String
    open val copyName: String get() = filterName

    @Serializable
    @SerialName("checkbox")
    data class CheckBox(
        override val name: String,
        val checked: Boolean,
    ) : SavedFilter() {
        override val filterName: String get() = name
    }

    @Serializable
    @SerialName("tristate")
    data class TriState(
        override val name: String,
        val state: Int,
    ) : SavedFilter() {
        override val filterName: String get() = name
    }

    @Serializable
    @SerialName("text")
    data class Text(
        override val name: String,
        val text: String,
    ) : SavedFilter() {
        override val filterName: String get() = text
    }

    @Serializable
    @SerialName("select")
    data class Select(
        override val name: String,
        val value: String,
    ) : SavedFilter() {
        override val filterName: String get() = "$name: $value"
    }

    @Serializable
    @SerialName("sort")
    data class Sort(
        override val name: String,
        val value: String,
        val ascending: Boolean,
    ) : SavedFilter() {
        override val filterName: String get() = "${if (ascending) ASCENDING_ARROW else DESCENDING_ARROW}$value"
        override val copyName: String get() = value
    }

    @Serializable
    @SerialName("group")
    data class Group(
        override val name: String,
        val children: List<SavedFilter>,
    ) : SavedFilter() {
        override val filterName: String get() = children.joinToString { it.filterName }
        override val copyName: String get() = children.joinToString { it.copyName }
    }

    companion object {
        const val ASCENDING_ARROW = '↑'
        const val DESCENDING_ARROW = '↓'
    }
}

private class BoldUprightSpan : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) = replace(tp)

    override fun updateMeasureState(tp: TextPaint) = replace(tp)

    private fun replace(tp: TextPaint) {
        tp.typeface = Typeface.create(tp.typeface, Typeface.BOLD)
    }
}

fun CharSequence.unitalicizeArrows(): CharSequence {
    if (SavedFilter.ASCENDING_ARROW !in this && SavedFilter.DESCENDING_ARROW !in this) return this
    return SpannableString(this).apply {
        indices.forEach { i ->
            if (this[i] == SavedFilter.ASCENDING_ARROW || this[i] == SavedFilter.DESCENDING_ARROW) {
                setSpan(BoldUprightSpan(), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}

private fun matchesType(
    filter: Filter<*>,
    saved: SavedFilter,
): Boolean =
    when (saved) {
        is SavedFilter.CheckBox -> filter is Filter.CheckBox
        is SavedFilter.TriState -> filter is Filter.TriState
        is SavedFilter.Text -> filter is Filter.Text
        is SavedFilter.Select -> filter is Filter.Select<*>
        is SavedFilter.Sort -> filter is Filter.Sort
        is SavedFilter.Group -> filter is Filter.Group<*>
    }

private fun looselyMatchesType(
    filter: Filter<*>,
    saved: SavedFilter,
): Boolean =
    when (saved) {
        is SavedFilter.CheckBox, is SavedFilter.TriState ->
            filter is Filter.CheckBox || filter is Filter.TriState
        is SavedFilter.Text, is SavedFilter.Select, is SavedFilter.Sort ->
            filter is Filter.Text || filter is Filter.Select<*> || filter is Filter.Sort || filter is Filter.Group<*>
        is SavedFilter.Group ->
            filter is Filter.Group<*> || filter is Filter.Text || filter is Filter.Select<*> || filter is Filter.Sort
    }

/** Punctuation/case/spacing-insensitive, and true if either string contains the other. */
private fun String.looselyMatches(other: String): Boolean {
    val a = normalizedForLooseMatch()
    val b = other.normalizedForLooseMatch()
    if (a.isEmpty() || b.isEmpty()) return false
    return a == b || if (a.length <= b.length) b.contains(a) else a.contains(b)
}

private fun String.normalizedForLooseMatch(): String = filter { it.isLetterOrDigit() }.lowercase()

private fun Filter<*>.toSavedFilter(default: Filter<*>?): SavedFilter? =
    when (this) {
        is Filter.CheckBox -> if (state != (default as? Filter.CheckBox)?.state) SavedFilter.CheckBox(name, state) else null
        is Filter.TriState -> if (state != (default as? Filter.TriState)?.state) SavedFilter.TriState(name, state) else null
        is Filter.Text -> if (state != (default as? Filter.Text)?.state) SavedFilter.Text(name, state) else null
        is Filter.Select<*> -> {
            if (state != (default as? Filter.Select<*>)?.state) {
                values.getOrNull(state)?.toString()?.let { SavedFilter.Select(name, it) }
            } else {
                null
            }
        }
        is Filter.Sort -> {
            if (state != (default as? Filter.Sort)?.state) {
                state?.let { selection -> values.getOrNull(selection.index)?.let { SavedFilter.Sort(name, it, selection.ascending) } }
            } else {
                null
            }
        }
        is Filter.Group<*> -> {
            val defaultGroup = default as? Filter.Group<*>
            val children =
                state.mapIndexedNotNull { index, child ->
                    (child as? Filter<*>)?.toSavedFilter(defaultGroup?.state?.getOrNull(index) as? Filter<*>)
                }
            children.takeIf { it.isNotEmpty() }?.let { SavedFilter.Group(name, it) }
        }
        // Header/Separator aren't interactive, nothing to diff
        else -> null
    }

/**
 * The filters in `this` that differ from the [default], compared by position, like
 * [eu.kanade.tachiyomi.ui.source.browse.BrowseSourcePresenter]'s own default-tracking does.
 */
fun FilterList.diffFromDefault(default: FilterList): List<SavedFilter> =
    mapIndexedNotNull { index, filter -> filter.toSavedFilter(default.getOrNull(index)) }

private fun SavedFilter.applyTo(filter: Filter<*>): Boolean =
    when (this) {
        is SavedFilter.CheckBox -> {
            val target = filter as? Filter.CheckBox ?: return false
            target.state = checked
            true
        }
        is SavedFilter.TriState -> {
            val target = filter as? Filter.TriState ?: return false
            target.state = state
            true
        }
        is SavedFilter.Text -> {
            val target = filter as? Filter.Text ?: return false
            target.state = text
            true
        }
        is SavedFilter.Select -> {
            val select = filter as? Filter.Select<*> ?: return false
            val index = select.values.indexOfFirst { it.toString().equals(value, ignoreCase = true) }
            if (index == -1) return false
            select.state = index
            true
        }
        is SavedFilter.Sort -> {
            val sort = filter as? Filter.Sort ?: return false
            val index = sort.values.indexOfFirst { it.equals(value, ignoreCase = true) }
            if (index == -1) return false
            sort.state = Filter.Sort.Selection(index, ascending)
            true
        }
        is SavedFilter.Group -> {
            val group = filter as? Filter.Group<*> ?: return false
            val groupChildren = group.state.filterIsInstance<Filter<*>>()
            children.isNotEmpty() &&
                children.all { saved ->
                    val target = groupChildren.find { it.name == saved.name && matchesType(it, saved) }
                    target != null && saved.applyTo(target)
                }
        }
    }

/**
 * Sets [value] as [target]'s state for whichever "one value out of many" kind it is, including a
 * [Filter.Group] (matched against a child's name). [ascending] only matters for a [Filter.Sort].
 */
private fun applyValueLoosely(
    value: String,
    target: Filter<*>,
    ascending: Boolean? = null,
): Boolean =
    when (target) {
        is Filter.Text -> {
            target.state = value
            true
        }
        is Filter.Select<*> -> {
            val index = target.values.indexOfFirst { it.toString().looselyMatches(value) }
            if (index == -1) return false
            target.state = index
            true
        }
        is Filter.Sort -> {
            val index = target.values.indexOfFirst { it.looselyMatches(value) }
            if (index == -1) return false
            target.state = Filter.Sort.Selection(index, ascending ?: target.state?.ascending ?: true)
            true
        }
        is Filter.Group<*> -> {
            when (val child = target.state.filterIsInstance<Filter<*>>().find { it.name.looselyMatches(value) }) {
                is Filter.CheckBox -> {
                    child.state = true
                    true
                }
                is Filter.TriState -> {
                    child.state = Filter.TriState.STATE_INCLUDE
                    true
                }
                else -> false
            }
        }
        else -> false
    }

/** The one child switched "on" in this group, if exactly one is - else there's no single value to hand off. */
private val SavedFilter.Group.singleIncludedChildName: String?
    get() =
        children
            .mapNotNull { child ->
                when (child) {
                    is SavedFilter.CheckBox -> child.name.takeIf { child.checked }
                    is SavedFilter.TriState -> child.name.takeIf { child.state == Filter.TriState.STATE_INCLUDE }
                    else -> null
                }
            }.singleOrNull()

/** [filters] is needed so a group child that doesn't fit [filter] can still search the rest of the list. */
private fun SavedFilter.looselyApplyTo(
    filter: Filter<*>,
    filters: FilterList,
): Boolean =
    when (this) {
        is SavedFilter.CheckBox -> {
            when (filter) {
                is Filter.CheckBox -> {
                    filter.state = checked
                    true
                }
                is Filter.TriState -> {
                    filter.state = checked.toInt()
                    true
                }
                else -> false
            }
        }
        is SavedFilter.TriState -> {
            when (filter) {
                is Filter.TriState -> {
                    filter.state = state
                    true
                }
                is Filter.CheckBox -> {
                    filter.state = state == Filter.TriState.STATE_INCLUDE
                    true
                }
                else -> false
            }
        }
        is SavedFilter.Text -> applyValueLoosely(text, filter)
        is SavedFilter.Select -> applyValueLoosely(value, filter)
        is SavedFilter.Sort -> applyValueLoosely(value, filter, ascending)
        is SavedFilter.Group -> {
            when (filter) {
                is Filter.Group<*> -> {
                    val groupChildren = filter.state.filterIsInstance<Filter<*>>()
                    children.isNotEmpty() &&
                        children.all { saved ->
                            val exact = groupChildren.find { it.name.equals(saved.name, ignoreCase = true) && matchesType(it, saved) }
                            val loose =
                                groupChildren.find { it.name.looselyMatches(saved.name) && looselyMatchesType(it, saved) }
                            when {
                                exact != null && saved.applyTo(exact) -> true
                                loose != null && saved.looselyApplyTo(loose, filters) -> true
                                // this child didn't fit the group [filter] matched on name alone -
                                // it may live under a differently-named group or flat filter
                                else -> saved.anyValue?.let { applyValueAnywhere(it, filters) } ?: false
                            }
                        }
                }
                is Filter.Text, is Filter.Select<*>, is Filter.Sort ->
                    singleIncludedChildName?.let { applyValueLoosely(it, filter) } ?: false
                else -> false
            }
        }
    }

/**
 * The one value this filter represents - a checked/included name, a text/select/sort's value, or
 * a [SavedFilter.Group]'s [singleIncludedChildName]. `null` when there's nothing to look for.
 */
private val SavedFilter.anyValue: String?
    get() =
        when (this) {
            is SavedFilter.CheckBox -> name.takeIf { checked }
            is SavedFilter.TriState -> name.takeIf { state == Filter.TriState.STATE_INCLUDE }
            is SavedFilter.Text -> text
            is SavedFilter.Select -> value
            is SavedFilter.Sort -> value
            is SavedFilter.Group -> singleIncludedChildName
        }

/**
 * Last resort: ignore names and look for [value] anywhere in [filters], the same way a
 * manga's tags get matched against a filter list regardless of which filter they live in.
 */
private fun applyValueAnywhere(
    value: String,
    filters: FilterList,
    ascending: Boolean? = null,
): Boolean {
    for (filter in filters) {
        val applied =
            when (filter) {
                is Filter.CheckBox -> filter.name.looselyMatches(value).also { if (it) filter.state = true }
                is Filter.TriState -> filter.name.looselyMatches(value).also { if (it) filter.state = Filter.TriState.STATE_INCLUDE }
                // too aggressive to stuff an arbitrary value into an unrelated free-text field
                is Filter.Text -> false
                else -> applyValueLoosely(value, filter, ascending)
            }
        if (applied) return true
    }
    return false
}

/** How many of a saved set of filters actually found a match to apply to. */
enum class FilterApplyResult {
    ALL,
    SOME,
    NONE,
}

/**
 * Applies saved filters to [filters], which may belong to a different source. Mutates matching
 * filters' `.state` in place, mirroring how
 * [eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController.searchGenres] restores a genre
 * filter by name.
 *
 * @param strict skip the loose/anywhere fallbacks - only an exact match counts. For when [filters]
 * is are from the same source.
 */
fun List<SavedFilter>.applyTo(
    filters: FilterList,
    strict: Boolean = false,
): FilterApplyResult {
    if (isEmpty()) return FilterApplyResult.ALL
    var appliedCount = 0
    for (saved in this) {
        val target =
            filters.find { it.name.equals(saved.name, ignoreCase = true) && matchesType(it, saved) }
        val applied =
            if (target != null && saved.applyTo(target)) {
                true
            } else if (strict) {
                false
            } else {
                val looseTarget = filters.find { it.name.looselyMatches(saved.name) && looselyMatchesType(it, saved) }
                val looseApplied = looseTarget != null && saved.looselyApplyTo(looseTarget, filters)
                when {
                    looseApplied -> true
                    // if no group even loosely matches, anywhere-search each child its own
                    saved is SavedFilter.Group ->
                        saved.children.isNotEmpty() &&
                            saved.children.all { child -> child.anyValue?.let { applyValueAnywhere(it, filters) } ?: false }
                    else -> saved.anyValue?.let { applyValueAnywhere(it, filters, (saved as? SavedFilter.Sort)?.ascending) } ?: false
                }
            }
        if (applied) appliedCount++
    }
    return when (appliedCount) {
        size -> FilterApplyResult.ALL
        0 -> FilterApplyResult.NONE
        else -> FilterApplyResult.SOME
    }
}
