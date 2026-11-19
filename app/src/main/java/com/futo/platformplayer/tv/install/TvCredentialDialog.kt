package com.futo.platformplayer.tv.install

import android.app.Dialog
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.tv.sync.TvPluginCredentials
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.models.ImageVariable
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class TvCredentialDialog(private val activity: AppCompatActivity) : DefaultLifecycleObserver {
    private var _dialog: Dialog? = null
    private var _operation: Job? = null

    init {
        activity.lifecycle.addObserver(this)
    }

    fun show(key: String, name: String, onFinished: () -> Unit) {
        if (_dialog?.isShowing == true || _operation?.isActive == true) return
        _operation = activity.lifecycleScope.launch {
            progress(R.string.tv_credentials_checking)
            try {
                val plugins = TvPluginCredentials.choices(key)
                _dialog?.dismiss()
                if (plugins.isEmpty()) {
                    message(R.string.tv_credentials_empty)
                } else {
                    choose(key, name, plugins, onFinished)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message((error as? TvPluginCredentials.Failure)?.messageId ?: R.string.tv_credentials_failed)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        _operation?.cancel()
        _dialog?.dismiss()
        _dialog = null
    }

    private fun choose(key: String, name: String, plugins: List<TvPluginCredentials.Plugin>, onFinished: () -> Unit) {
        val dialog = BottomSheetDialog(activity, R.style.TvCredentialsSheet)
        val content = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_tv_credentials, null)
        val rows = content.findViewById<LinearLayout>(R.id.tv_credentials_rows)
        val next = content.findViewById<MaterialButton>(R.id.tv_credentials_continue)
        val all = content.findViewById<MaterialButton>(R.id.tv_credentials_all)
        val selected = linkedSetOf<TvPluginCredentials.Plugin>()
        val checkboxes = mutableListOf<MaterialCheckBox>()
        content.findViewById<TextView>(R.id.tv_credentials_destination).text = activity.getString(R.string.tv_credentials_to, name)
        next.isEnabled = false
        next.alpha = 0.45f
        for (plugin in plugins.sortedBy { !it.available }) {
            val row = LayoutInflater.from(dialog.context).inflate(R.layout.view_tv_credential_plugin, rows, false)
            val icon = row.findViewById<ImageView>(R.id.tv_credentials_icon)
            val image = StatePlugins.instance.getPluginIconOrNull(plugin.id)
                ?: StatePlugins.instance.getPlugin(plugin.id)?.config?.absoluteIconUrl?.let { ImageVariable.fromUrl(it) }
            if (image != null) image.setImageView(icon, R.drawable.ic_sources)
            else icon.setImageResource(R.drawable.ic_sources)
            row.findViewById<TextView>(R.id.tv_credentials_plugin_name).text = plugin.name
            row.findViewById<View>(R.id.tv_credentials_plugin_status).isVisible = !plugin.available
            row.alpha = if (plugin.available) 1f else 0.5f
            val checkbox = row.findViewById<MaterialCheckBox>(R.id.tv_credentials_checked).apply {
                contentDescription = plugin.name
                isEnabled = plugin.available
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(plugin) else selected.remove(plugin)
                    next.isEnabled = selected.isNotEmpty()
                    next.alpha = if (next.isEnabled) 1f else 0.45f
                    next.text = if (selected.isEmpty()) activity.getString(R.string.tv_install_continue)
                        else activity.getString(R.string.tv_credentials_continue, selected.size)
                    all.setText(if (selected.size == plugins.count { it.available }) R.string.tv_credentials_clear else R.string.tv_credentials_all)
                }
            }
            if (plugin.available) row.setOnClickListener { checkbox.toggle() }
            checkboxes.add(checkbox)
            rows.addView(row)
        }
        all.isEnabled = plugins.any { it.available }
        all.setOnClickListener {
            val select = checkboxes.any { it.isEnabled && !it.isChecked }
            checkboxes.filter { it.isEnabled }.forEach { it.isChecked = select }
        }
        val scroll = content.findViewById<NestedScrollView>(R.id.tv_credentials_scroll)
        scroll.layoutParams.height = minOf((plugins.size * 72 * activity.resources.displayMetrics.density).toInt(),
            (activity.resources.displayMetrics.heightPixels * 0.45f).toInt())
        content.findViewById<View>(R.id.tv_credentials_cancel).setOnClickListener { dialog.dismiss() }
        next.setOnClickListener {
            dialog.dismiss()
            confirm(key, name, selected.toList(), onFinished)
        }
        dialog.setContentView(content)
        display(dialog)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
    }

    private fun confirm(key: String, name: String, plugins: List<TvPluginCredentials.Plugin>, onFinished: () -> Unit) {
        display(UIDialogs.showDialog(activity, R.drawable.ic_lock,
            activity.getString(R.string.tv_credentials_choose, name), activity.getString(R.string.tv_credentials_warning), null, 0,
            UIDialogs.Action(activity.getString(R.string.cancel), {}, UIDialogs.ActionStyle.ACCENT),
            UIDialogs.Action(activity.getString(R.string.tv_credentials_send), { send(key, plugins, onFinished) }, UIDialogs.ActionStyle.PRIMARY)))
    }

    private fun send(key: String, plugins: List<TvPluginCredentials.Plugin>, onFinished: () -> Unit) {
        _operation = activity.lifecycleScope.launch {
            progress(R.string.tv_credentials_sending, false)
            try {
                val results = TvPluginCredentials.send(key, plugins)
                val text = plugins.joinToString("\n") { plugin ->
                    val status = when (results.first { it.id == plugin.id }.status) {
                        "saved" -> R.string.tv_credentials_saved
                        "restart" -> R.string.tv_credentials_restart
                        else -> R.string.tv_credentials_not_saved
                    }
                    activity.getString(status, plugin.name)
                }
                result(text, results.none { it.status == "failed" }, onFinished)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message((error as? TvPluginCredentials.Failure)?.messageId ?: R.string.tv_credentials_unconfirmed)
            }
        }
    }

    private fun result(text: String, success: Boolean, onFinished: () -> Unit) {
        display(UIDialogs.showDialog(activity, R.drawable.device_sync,
            activity.getString(R.string.tv_credentials_result), text, null, -2,
            UIDialogs.Action(activity.getString(android.R.string.ok), {
                if (success) onFinished()
            }, UIDialogs.ActionStyle.PRIMARY)).apply {
            findViewById<TextView>(R.id.dialog_text_details).apply {
                maxHeight = (activity.resources.displayMetrics.heightPixels * 0.35f).toInt()
                movementMethod = ScrollingMovementMethod.getInstance()
            }
        })
    }

    private fun progress(message: Int, cancelable: Boolean = true) {
        val actions = if (cancelable) arrayOf(UIDialogs.Action(activity.getString(R.string.cancel),
            { _operation?.cancel() }, UIDialogs.ActionStyle.ACCENT)) else emptyArray()
        display(UIDialogs.showDialog(activity, R.drawable.ic_loader_animated, true,
            activity.getString(message), null, null, if (cancelable) 0 else -2, *actions))
    }

    private fun message(message: Int) {
        display(UIDialogs.showDialog(activity, R.drawable.device_sync,
            activity.getString(R.string.tv_credentials_title), activity.getString(message), null, 0,
            UIDialogs.Action(activity.getString(android.R.string.ok), {}, UIDialogs.ActionStyle.PRIMARY)))
    }

    private fun display(dialog: Dialog) {
        _dialog?.dismiss()
        _dialog = dialog
        dialog.show()
    }
}
