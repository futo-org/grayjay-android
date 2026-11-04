package com.futo.platformplayer.tv.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.sources.*
import com.futo.platformplayer.tv.views.TvMenuDialog
import com.futo.platformplayer.tv.views.TvPlayerPanel
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvSourceDetailsFragment : Fragment() {
    private var catalog = emptyList<TvSourceInfo>()
    private var page = "main"
    private var advanced = false
    private var busy = false
    private var checkingSubscriptions = false
    private var dialog: TvMenuDialog? = null
    private lateinit var identity: LinearLayout
    private lateinit var panel: TvPlayerPanel

    internal var store: TvSourcesStore? = null

    private val host
        get() = requireActivity() as TvActivity

    private val sourceId
        get() = requireArguments().getString("id")!!

    private val item
        get() = catalog.firstOrNull { it.id == sourceId }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val c = requireContext()
        if (store == null) store = TvSourcesStore.platform(c)
        val root = TvUi.row(c).apply {
            gravity = Gravity.TOP
            setPadding(c.dp(32), c.dp(28), c.dp(32), c.dp(24))
        }
        identity = TvUi.column(c)
        root.addView(identity, LinearLayout.LayoutParams(c.dp(280), -1).apply { marginEnd = c.dp(40) })
        panel = TvPlayerPanel(c) { host.onBackPressedDispatcher.onBackPressed() }.apply { useScrollingPageLayout() }
        root.addView(panel, LinearLayout.LayoutParams(0, -1, 1f))
        return root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        bindIdentity()
        load()
        host.onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (panel.back()) return
                    isEnabled = false
                    host.onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            })
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        super.onDestroyView()
    }

    private fun bindIdentity() {
        identity.removeAllViews()
        val c = requireContext()
        val source = item
        identity.addView(TvUi.text(c, getString(R.string.tv_sources), 14f, true))
        val icon = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        source?.icon?.setImageView(icon, R.drawable.ic_sources) ?: icon.setImageResource(R.drawable.ic_sources)
        identity.addView(
            icon,
            LinearLayout.LayoutParams(c.dp(88), c.dp(88)).apply {
                topMargin = c.dp(36)
                bottomMargin = c.dp(22)
            })
        identity.addView(
            TvUi.heading(c, source?.name ?: requireArguments().getString("name").orEmpty()).apply {
                textSize = 30f
                lines(2)
            })
        source?.let {
            identity.addView(
                TvUi.text(
                    c,
                    getString(
                        if (it.enabled) R.string.tv_enabled
                        else if (it.installed) R.string.tv_disabled else R.string.tv_source_available),
                    14f,
                    true).apply { setPadding(0, c.dp(10), 0, c.dp(24)) })
            it.config?.let { config ->
                identity.addView(TvUi.text(c, config.description, 16f).apply { lines(6) })
                identity.addView(
                    TvUi.text(
                        c,
                        getString(R.string.tv_source_version, config.version) +
                            if (config.author.isNotBlank()) "  ·  ${config.author}" else "",
                        13f,
                        true).apply { setPadding(0, c.dp(22), 0, 0) })
                if (config.authentication != null)
                    identity.addView(
                        TvUi.text(
                            c,
                            getString(
                                if (it.loggedIn) R.string.tv_source_signed_in else R.string.tv_source_signed_out),
                            13f,
                            true).apply { setPadding(0, c.dp(10), 0, 0) })
            }
        }
    }

    private fun load(focus: String? = null, action: (suspend () -> Unit)? = null) {
        if (busy) return
        busy = true
        panel.title(getString(R.string.tv_source_manage))
        panel.onBack = null
        panel.message(getString(if (action == null) R.string.tv_loading else R.string.tv_source_saving))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                if (action != null) withContext(NonCancellable + Dispatchers.IO) { action() }
                catalog = withContext(Dispatchers.IO) { checkNotNull(store).list() }
                check(item != null)
                if (isAdded && view != null) {
                    bindIdentity()
                    render(focus)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvSources", "Source operation failed", error)
                if (isAdded && view != null)
                    panel.message(
                        getString(R.string.tv_source_operation_error), getString(R.string.tv_source_retry_hint)) {
                        load()
                    }
            } finally {
                busy = false
            }
        }
    }

    private fun render(focus: String? = null) {
        val source = item ?: return
        panel.title(
            getString(
                when (page) {
                    "preferences" -> R.string.tv_source_preferences
                    "priority" -> R.string.tv_source_priority
                    "about" -> R.string.tv_about
                    else -> R.string.tv_source_manage
                }),
            page != "main")
        panel.onBack = if (page == "main") null
        else
            ({
                page = "main"
                render()
                true
            })
        val menu = panel.menu()
        fun option(label: Int, value: String? = null, checked: Boolean = false, tag: String, action: () -> Unit): View =
            panel.option(getString(label), value, checked, action).apply {
                this.tag = tag
                menu.addView(this)
            }
        when (page) {
            "preferences" -> {
                source.config
                    ?.settings
                    .orEmpty()
                    .filter { setting ->
                        (advanced || setting.isAdvanced != true) &&
                            (setting.dependency == null || truth(source.values[setting.dependency]))
                    }
                    .forEach { setting ->
                        when (setting.type.lowercase()) {
                            "header" ->
                                menu.addView(
                                    TvUi.heading(requireContext(), setting.name).apply {
                                        textSize = 17f
                                        setPadding(context.dp(12), context.dp(20), 0, context.dp(12))
                                    })
                            "boolean",
                            "dropdown" -> {
                                val value = source.values[setting.variableOrName] ?: setting.default
                                val label = if (setting.type.equals("boolean", true))
                                    getString(if (truth(value)) R.string.tv_enabled else R.string.tv_disabled)
                                else setting.options?.getOrNull(value?.toIntOrNull() ?: 0).orEmpty()
                                menu.addView(
                                    panel.option(setting.name, label) { setting(setting) }
                                        .apply { tag = setting.variableOrName })
                            }
                        }
                    }
                if (source.config?.settings?.any { it.isAdvanced == true } == true)
                    option(R.string.tv_source_advanced, checked = advanced, tag = "advanced") {
                        advanced = !advanced
                        render("advanced")
                    }
                option(R.string.tv_source_reset, tag = "reset") {
                    confirm(
                        getString(R.string.tv_source_reset),
                        getString(R.string.tv_source_reset_hint),
                        getString(R.string.tv_source_reset)) {
                        load("reset") { checkNotNull(store).settings(sourceId, emptyMap()) }
                    }
                }
            }
            "priority" -> {
                val enabled = catalog.filter { it.enabled }
                val index = enabled.indexOfFirst { it.id == sourceId }
                menu.addView(
                    TvUi.text(requireContext(), getString(R.string.tv_source_priority_hint), 15f, true).apply {
                        setPadding(context.dp(12), 0, context.dp(12), context.dp(20))
                    })
                option(R.string.tv_source_move_up, tag = "up") { load("up") { checkNotNull(store).move(sourceId, -1) } }
                    .apply {
                        isEnabled = index > 0
                        alpha = if (isEnabled) 1f else .4f
                    }
                option(R.string.tv_source_move_down, tag = "down") {
                    load("down") { checkNotNull(store).move(sourceId, 1) }
                }.apply {
                    isEnabled = index in 0 until enabled.lastIndex
                    alpha = if (isEnabled) 1f else .4f
                }
                enabled.forEachIndexed { i, entry ->
                    menu.addView(
                        TvUi.text(requireContext(), "${i + 1}. ${entry.name}", 16f).apply {
                            setPadding(context.dp(12), context.dp(8), 0, context.dp(8))
                        })
                }
            }
            "about" -> {
                val config = source.config
                menu.addView(
                    TvUi.text(
                        requireContext(),
                        listOfNotNull(
                            config?.description,
                            config?.platformUrl,
                            config?.authorUrl,
                            config?.getChangelogString(config.version.toString()))
                            .filter { it.isNotBlank() }
                            .joinToString("\n\n"),
                        16f).apply {
                        setPadding(context.dp(12), 0, context.dp(12), context.dp(20))
                        isFocusable = true
                        setBackgroundResource(R.drawable.tv_channel_tab)
                    })
                if (config?.authentication != null && !source.loggedIn)
                    menu.addView(TvUi.text(requireContext(), getString(R.string.tv_source_login_hint), 14f, true))
            }
            else -> {
                if (!source.installed)
                    option(R.string.tv_source_add, getString(R.string.tv_source_bundled), tag = "add") {
                        load("enabled") { checkNotNull(store).install(sourceId) }
                    }.id = R.id.tv_source_add
                else {
                    val last = source.enabled && catalog.count { it.enabled } == 1
                    option(
                        R.string.tv_source_use,
                        getString(
                            if (last) R.string.tv_source_keep_one
                            else if (source.enabled) R.string.tv_enabled else R.string.tv_disabled),
                        source.enabled,
                        "enabled") {
                        toggle()
                    }.apply {
                        id = R.id.tv_source_enabled
                        isEnabled = !last
                        alpha = if (last) .5f else 1f
                    }
                    if (source.config != null) {
                        option(R.string.tv_source_home, checked = source.home, tag = "home") {
                            load("home") { checkNotNull(store).visibility(sourceId, !source.home, source.search) }
                        }.id = R.id.tv_source_home
                        option(R.string.tv_source_search, checked = source.search, tag = "search") {
                            load("search") { checkNotNull(store).visibility(sourceId, source.home, !source.search) }
                        }.id = R.id.tv_source_search
                        if (source.config.settings.isNotEmpty())
                            option(R.string.tv_source_preferences, tag = "preferences") {
                                page = "preferences"
                                render()
                            }.id = R.id.tv_source_preferences
                        if (!source.config.sourceUrl.isNullOrBlank())
                            option(R.string.check_for_updates, tag = "updates") { checkUpdate() }.id = R.id.tv_source_update
                    }
                    if (source.enabled && catalog.count { it.enabled } > 1)
                        option(
                            R.string.tv_source_priority,
                            getString(
                                R.string.tv_source_priority_position,
                                catalog.filter { it.enabled }.indexOfFirst { it.id == sourceId } + 1),
                            tag = "priority") {
                            page = "priority"
                            render()
                        }.id = R.id.tv_source_priority
                }
                if (source.config != null)
                    option(R.string.tv_about, tag = "about") {
                        page = "about"
                        render()
                    }
            }
        }
        val target = focus?.let { menu.findViewWithTag<View>(it) }?.takeIf { it.isEnabled }
        (target ?: (0 until menu.childCount).map { menu.getChildAt(it) }.firstOrNull { it.isFocusable && it.isEnabled })
            ?.requestFocus()
    }

    private fun toggle() {
        if (busy || checkingSubscriptions) return
        val source = item ?: return
        if (!source.enabled) {
            load("enabled") { checkNotNull(store).enable(sourceId, true) }
            return
        }
        checkingSubscriptions = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) { checkNotNull(store).affectedSubscriptions(sourceId) }
                if (!isAdded || view == null) return@launch
                if (count > 0)
                    confirm(
                        getString(R.string.tv_source_disable),
                        getString(R.string.tv_source_subscriptions_warning, count),
                        getString(R.string.tv_source_disable)) {
                        load("enabled") { checkNotNull(store).enable(sourceId, false) }
                    }
                else load("enabled") { checkNotNull(store).enable(sourceId, false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isAdded && view != null)
                    panel.message(getString(R.string.tv_source_operation_error), retry = { load() })
            } finally {
                checkingSubscriptions = false
            }
        }
    }

    private fun setting(setting: SourcePluginConfig.Setting) {
        val source = item ?: return
        val current = source.values[setting.variableOrName] ?: setting.default
        fun save(value: String) {
            val action = {
                load(setting.variableOrName) {
                    checkNotNull(store).settings(sourceId, source.values + (setting.variableOrName to value))
                }
            }
            if (!setting.warningDialog.isNullOrBlank() && truth(value))
                confirm(setting.name, setting.warningDialog, getString(R.string.ok), action)
            else action()
        }
        dialog = TvMenuDialog(requireContext()).also { d ->
            d.panel.title(setting.name)
            val menu = d.panel.menu()
            if (setting.description.isNotBlank())
                menu.addView(
                    TvUi.text(requireContext(), setting.description, 15f, true).apply {
                        setPadding(context.dp(12), 0, context.dp(12), context.dp(20))
                    })
            val values = if (setting.type.equals("boolean", true))
                listOf("false" to getString(R.string.tv_disabled), "true" to getString(R.string.tv_enabled))
            else setting.options.orEmpty().mapIndexed { i, value -> i.toString() to value }
            values.forEach { (value, label) ->
                menu.addView(
                    d.panel.option(
                        label,
                        checked =
                            if (setting.type.equals("boolean", true)) truth(value) == truth(current)
                            else value == (current ?: "0")) {
                        d.dismiss()
                        save(value)
                    })
            }
            menu.getChildAt(if (setting.description.isBlank()) 0 else 1)?.requestFocus()
            d.show()
        }
    }

    private fun checkUpdate() {
        if (busy) return
        busy = true
        panel.message(getString(R.string.tv_loading))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val update = withContext(Dispatchers.IO) { checkNotNull(store).checkUpdate(sourceId) }
                if (!isAdded || view == null) return@launch
                render("updates")
                if (update == null) {
                    val menu = panel.menu()
                    menu.addView(
                        TvUi.heading(requireContext(), getString(R.string.tv_source_up_to_date)).apply {
                            textSize = 22f
                        })
                    menu.addView(
                        TvUi.text(requireContext(), getString(R.string.tv_source_up_to_date_hint), 16f, true).apply {
                            setPadding(0, context.dp(16), 0, context.dp(24))
                        })
                    menu.addView(
                        TvUi.button(requireContext(), getString(R.string.ok)) { render("updates") }
                            .apply { requestFocus() })
                    panel.onBack = {
                        render("updates")
                        true
                    }
                } else
                    confirm(
                        getString(R.string.tv_source_update_title, update.config.version),
                        (listOfNotNull(update.config.getChangelogString(update.config.version.toString())) +
                                update.warnings)
                            .joinToString("\n\n")
                            .ifBlank { getString(R.string.tv_source_update_hint) },
                        getString(R.string.update)) {
                        load("updates") { checkNotNull(store).update(update) }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvSources", "Update check failed", error)
                if (isAdded && view != null)
                    panel.message(getString(R.string.tv_source_update_error), retry = ::checkUpdate)
            } finally {
                busy = false
            }
        }
    }

    private fun confirm(title: String, description: String, action: String, confirmed: () -> Unit) {
        dialog = TvMenuDialog(requireContext()).also {
            it.confirm(title, description, action, confirmed)
            it.show()
        }
    }

    private fun truth(value: String?) = value.equals("true", true) || (value?.toIntOrNull()?.let { it != 0 } == true)
}
