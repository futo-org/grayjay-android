package com.futo.platformplayer.tv.search

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.FilterGroup
import com.futo.platformplayer.tv.views.TvPlayerPanel
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp

internal class TvSearchFilters(
    context: Context,
    private val catalog: List<TvSearchProvider>,
    private val kind: TvSearchKind,
    initial: TvSearchOptions,
    private val apply: (TvSearchOptions) -> Unit
) : Dialog(context) {
    private var draft = initial
    private val panel = TvPlayerPanel(context, ::dismiss)

    init {
        setContentView(panel)
        showRoot()
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
            setLayout(context.dp(380), context.resources.displayMetrics.heightPixels - context.dp(40))
            attributes = attributes.apply { x = context.dp(20) }
        }
    }

    @Deprecated("Uses the app's existing back dispatch configuration")
    override fun onBackPressed() {
        if (!panel.back()) super.onBackPressed()
    }

    private fun showRoot(focus: String? = null) {
        panel.title(context.getString(R.string.tv_search_filters))
        panel.onBack = null
        val menu = panel.menu()
        val selected = catalog.filter { draft.sourceIds == null || it.id in draft.sourceIds.orEmpty() }
        menu.addView(
            panel.option(
                context.getString(R.string.tv_sources),
                if (selected.size == catalog.size) context.getString(R.string.tv_search_all_sources)
                else selected.joinToString { it.name }) {
                showSources()
            }.apply {
                id = R.id.tv_search_filter_sources
                tag = "sources"
            })
        val caps = draft.capabilities(catalog, kind)
        if (caps.sorts.isNotEmpty())
            menu.addView(
                panel.option(context.getString(R.string.tv_sort), sortLabel(context, draft.sort)) {
                    showSort()
                }.apply {
                    id = R.id.tv_search_filter_sort
                    tag = "sort"
                })
        caps.filters.forEach { group ->
            val values = group.filters.filter { it.idOrName in draft.filters[group.idOrName].orEmpty() }
            menu.addView(
                panel.option(
                    group.name,
                    values.takeIf { it.isNotEmpty() }?.joinToString { it.name }
                        ?: context.getString(R.string.tv_search_any)) {
                    showGroup(group)
                }.apply { tag = group.idOrName })
        }
        menu.addView(
            TvUi.button(context, context.getString(R.string.tv_search_reset_filters)) {
                draft = TvSearchOptions()
                showRoot()
            }.apply { id = R.id.tv_search_filter_reset })
        menu.addView(
            TvUi.button(context, context.getString(R.string.tv_search_apply_filters)) {
                apply(draft.sanitized(catalog, kind))
                dismiss()
            }.apply {
                id = R.id.tv_search_filter_apply
                isEnabled = draft.providers(catalog, kind).isNotEmpty()
            })
        if (draft.providers(catalog, kind).isEmpty())
            menu.addView(TvUi.text(context, context.getString(R.string.tv_search_select_source), 13f, true))
        (focus?.let { menu.findViewWithTag<View>(it) } ?: menu.getChildAt(0))?.requestFocus()
    }

    private fun showSources(focus: String? = null) {
        panel.title(context.getString(R.string.tv_sources), true)
        panel.onBack = {
            showRoot("sources")
            true
        }
        val menu = panel.menu()
        catalog.forEach { provider ->
            val enabled = draft.sourceIds == null || provider.id in draft.sourceIds.orEmpty()
            menu.addView(
                panel.option(
                    provider.name,
                    if (kind in provider.kinds) null
                    else context.getString(R.string.tv_search_kind_unavailable, context.getString(kind.label)),
                    enabled) {
                    val ids = (draft.sourceIds ?: catalog.map { it.id }.toSet()).toMutableSet()
                    if (enabled) ids.remove(provider.id) else ids.add(provider.id)
                    draft = draft.copy(sourceIds = if (ids.size == catalog.size) null else ids)
                        .sanitized(catalog, kind)
                    showSources(provider.id)
                }.apply { tag = provider.id })
        }
        (focus?.let { menu.findViewWithTag<View>(it) } ?: menu.getChildAt(0))?.requestFocus()
    }

    private fun showSort() {
        panel.title(context.getString(R.string.tv_sort), true)
        panel.onBack = {
            showRoot("sort")
            true
        }
        val menu = panel.menu()
        val options = listOf(null) + draft.capabilities(catalog, kind).sorts
        options.forEach { sort ->
            menu.addView(
                panel.option(sortLabel(context, sort), checked = sort == draft.sort) {
                    draft = draft.copy(sort = sort)
                    showRoot("sort")
                })
        }
        menu.getChildAt(options.indexOf(draft.sort).coerceAtLeast(0))?.requestFocus()
    }

    private fun showGroup(group: FilterGroup, focus: Int = 0) {
        panel.title(group.name, true)
        panel.onBack = {
            showRoot(group.idOrName)
            true
        }
        val menu = panel.menu()
        val selected = draft.filters[group.idOrName].orEmpty()
        menu.addView(
            panel.option(context.getString(R.string.tv_search_any), checked = selected.isEmpty()) {
                draft = draft.copy(filters = draft.filters - group.idOrName)
                showRoot(group.idOrName)
            })
        group.filters.forEachIndexed { index, filter ->
            menu.addView(
                panel.option(filter.name, checked = filter.idOrName in selected) {
                    val values = if (group.isMultiSelect) selected.toMutableList() else mutableListOf()
                    if (filter.idOrName in values) values.remove(filter.idOrName) else values.add(filter.idOrName)
                    draft = draft.copy(filters = draft.filters + (group.idOrName to values))
                    if (group.isMultiSelect) showGroup(group, index + 1) else showRoot(group.idOrName)
                })
        }
        menu.getChildAt(focus)?.requestFocus()
    }

    companion object {
        fun sortLabel(context: Context, value: String?): String =
            when (value?.uppercase()) {
                null -> context.getString(R.string.tv_search_default_sort)
                "RELEVANCE" -> context.getString(R.string.tv_search_relevance)
                "CHRONOLOGICAL" -> context.getString(R.string.tv_search_upload_date)
                "POPULAR",
                "POPULARITY" -> context.getString(R.string.tv_popular)
                "VIEWS",
                "VIEW_COUNT" -> context.getString(R.string.tv_search_view_count)
                else -> value.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
            }
    }
}
