package com.futo.platformplayer.tv.fragments

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.sync.*
import com.futo.platformplayer.tv.views.TvMenuDialog
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvSyncFragment : Fragment() {
    private var snapshot: TvSyncSnapshot? = null
    private var qrBitmap: Bitmap? = null
    private var qrUrl: String? = null
    private var selectedKey: String? = null
    private var foreground = false
    private var changing = false
    private var initialFocus = true
    private var startup: Job? = null
    private var load: Job? = null
    private var polling: Job? = null
    private var unobserve: (() -> Unit)? = null
    private var setupReceiver: TvSyncSetupReceiver? = null
    private var dialog: Dialog? = null
    private val approvals = mutableListOf<TvSyncRequest>()
    private lateinit var pairTitle: TextView
    private lateinit var pairingHint: TextView
    private lateinit var qr: ImageView
    private lateinit var qrFrame: FrameLayout
    private lateinit var pairButton: TextView
    private lateinit var status: TextView
    private lateinit var error: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var deviceCount: TextView
    private lateinit var empty: LinearLayout
    private lateinit var devices: RecyclerView
    private lateinit var adapter: TvSyncDevicesAdapter

    internal var store: TvSyncStore = TvSyncStore.platform()

    private val host
        get() = requireActivity() as TvActivity

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        selectedKey = state?.getString("device")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val c = requireContext()
        val root = TvUi.column(c).apply { setPadding(c.dp(28), c.dp(24), c.dp(32), c.dp(16)) }
        val header = TvUi.row(c)
        val title = TvUi.column(c)
        title.addView(TvUi.heading(c, getString(R.string.sync)).apply { textSize = 28f })
        title.addView(TvUi.text(c, getString(R.string.tv_sync_intro), 14f, true).apply { setPadding(0, c.dp(8), 0, 0) })
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        spinner = ProgressBar(c)
        header.addView(spinner, LinearLayout.LayoutParams(c.dp(18), c.dp(18)).apply { marginEnd = c.dp(12) })
        status = TvUi.text(c, getString(R.string.tv_loading), 13f, true).apply { id = R.id.tv_sync_status }
        header.addView(status)
        header.addView(
            TvUi.iconButton(c, R.drawable.ic_refresh, getString(R.string.tv_refresh)) { reload() }
                .apply { id = R.id.tv_sync_refresh })
        root.addView(header)
        val body = TvUi.row(c).apply { gravity = Gravity.TOP }
        val pairing = TvUi.column(c).apply { gravity = Gravity.CENTER_HORIZONTAL }
        pairTitle = TvUi.heading(c, getString(R.string.tv_sync_pair)).apply { textSize = 20f }
        pairing.addView(pairTitle)
        qrFrame = FrameLayout(c).apply {
            setPadding(c.dp(8), c.dp(8), c.dp(8), c.dp(8))
        }
        qr = ImageView(c).apply {
            id = R.id.tv_sync_qr
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.tv_sync_qr_description)
        }
        qrFrame.addView(qr, FrameLayout.LayoutParams(-1, -1))
        pairing.addView(qrFrame, LinearLayout.LayoutParams(c.dp(224), c.dp(224)).apply { topMargin = c.dp(12) })
        pairingHint = TvUi.text(c, "", 14f, true).apply { gravity = Gravity.CENTER }
        pairing.addView(pairingHint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dp(12) })
        pairButton = TvUi.button(c, getString(R.string.tv_sync_enlarge)) {
            when {
                snapshot == null -> reload()
                snapshot?.enabled != true -> enable()
                qrBitmap != null -> enlarge()
                else -> reload()
            }
        }.apply {
            id = R.id.tv_sync_pair
            isEnabled = false
            setOnKeyListener { _, key, event ->
                when (key) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (event.action == KeyEvent.ACTION_DOWN) host.focusNavigation()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (event.action == KeyEvent.ACTION_DOWN) focusDevice()
                        true
                    }
                    else -> false
                }
            }
        }
        pairing.addView(pairButton, LinearLayout.LayoutParams(-2, -2).apply { topMargin = c.dp(14) })
        body.addView(pairing, LinearLayout.LayoutParams(0, -1, .46f))
        body.addView(
            View(c).apply { setBackgroundColor(0xFF303030.toInt()) },
            LinearLayout.LayoutParams(c.dp(1), -1).apply {
                marginStart = c.dp(28)
                marginEnd = c.dp(28)
            })
        val paired = TvUi.column(c)
        paired.addView(TvUi.heading(c, getString(R.string.tv_sync_devices)).apply { textSize = 20f })
        deviceCount = TvUi.text(c, "", 13f, true)
        paired.addView(
            deviceCount,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = c.dp(8)
                bottomMargin = c.dp(20)
            })
        val listArea = FrameLayout(c)
        adapter = TvSyncDevicesAdapter(::deviceOptions, { selectedKey = it }, { pairButton.requestFocus() })
        devices = RecyclerView(c).apply {
            id = R.id.tv_sync_devices
            layoutManager = LinearLayoutManager(c)
            adapter = this@TvSyncFragment.adapter
            itemAnimator = null
            clipToPadding = false
            setPadding(c.dp(4), c.dp(4), c.dp(4), c.dp(4))
        }
        listArea.addView(devices, FrameLayout.LayoutParams(-1, -1))
        empty = TvUi.column(c).apply {
            gravity = Gravity.CENTER
            addView(
                ImageView(c).apply {
                    setImageResource(R.drawable.ic_device)
                    setColorFilter(TvUi.muted)
                },
                LinearLayout.LayoutParams(c.dp(56), c.dp(56)))
            addView(
                TvUi.text(c, getString(R.string.tv_sync_no_devices), 18f).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, context.dp(20), 0, 0)
                })
            addView(
                TvUi.text(c, getString(R.string.tv_sync_no_devices_hint), 14f, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(context.dp(12), context.dp(10), context.dp(12), 0)
                })
            visibility = View.GONE
        }
        listArea.addView(empty, FrameLayout.LayoutParams(-1, -1))
        paired.addView(listArea, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(paired, LinearLayout.LayoutParams(0, -1, .54f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = c.dp(24) })
        error = TvUi.text(c, "", 12f, true).apply { id = R.id.tv_sync_error }
        root.addView(error, LinearLayout.LayoutParams(-1, c.dp(22)).apply { topMargin = c.dp(8) })
        return root
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        startup = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                observe()
                setupReceiver = TvSyncSetupReceiver().also { it.register(requireContext()) }
                reload()
                polling = launch {
                    while (isActive) {
                        delay(3000)
                        reload(silent = true)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                showError(failure)
            }
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putString("device", selectedKey)
        super.onSaveInstanceState(state)
    }

    override fun onPause() {
        foreground = false
        setupReceiver?.let { requireContext().unregisterReceiver(it) }
        setupReceiver = null
        startup?.cancel()
        polling?.cancel()
        load?.cancel()
        unobserve?.invoke()
        unobserve = null
        dialog?.dismiss()
        dialog = null
        super.onPause()
    }

    override fun onDestroyView() {
        devices.adapter = null
        qr.setImageDrawable(null)
        qrBitmap = null
        qrUrl = null
        super.onDestroyView()
    }

    private fun observe() {
        unobserve?.invoke()
        unobserve = store.observe({ if (foreground) reload(silent = true) }, ::requestPairing)
    }

    private fun reload(silent: Boolean = false) {
        if (!foreground || load?.isActive == true) return
        if (!silent) spinner.visibility = View.VISIBLE
        val size = requireContext().dp(208)
        load = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (current, bitmap) =
                    withContext(Dispatchers.IO) {
                        val current = store.snapshot()
                        val bitmap = current.pairingUrl?.let {
                            if (it == qrUrl) qrBitmap else TvSyncPairingCode.bitmap(it, size)
                        }
                        current to bitmap
                    }
                qrUrl = current.pairingUrl
                qrBitmap = bitmap
                snapshot = current
                error.text = ""
                render(current)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                showError(failure)
            } finally {
                spinner.visibility = View.GONE
            }
        }
    }

    private fun render(current: TvSyncSnapshot) {
        val c = requireContext()
        status.text = getString(
            when {
                !current.enabled -> R.string.tv_sync_off
                current.local || current.relay -> R.string.tv_sync_ready
                else -> R.string.tv_sync_connecting
            })
        pairTitle.text = getString(if (current.enabled) R.string.tv_sync_pair else R.string.tv_sync_enable_title)
        pairingHint.text = getString(if (current.enabled) R.string.tv_sync_instructions else R.string.tv_sync_enable_hint)
        pairButton.text = getString(
            when {
                !current.enabled -> R.string.tv_sync_enable
                qrBitmap != null -> R.string.tv_sync_enlarge
                else -> R.string.tv_retry
            })
        pairButton.isEnabled = true
        pairButton.isClickable = !changing
        if (initialFocus && !host.currentFocusInRail()) {
            initialFocus = false
            pairButton.requestFocus()
        }
        if (qrBitmap != null) {
            qr.clearColorFilter()
            qr.setImageBitmap(qrBitmap)
            qrFrame.background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = c.dp(12).toFloat()
            }
        } else {
            qr.setImageResource(R.drawable.device_sync)
            qrFrame.background = null
        }
        if (current.listenerFailed) error.text = getString(R.string.tv_sync_listener_failed)
        deviceCount.text = if (current.devices.isEmpty()) getString(R.string.tv_sync_devices_hint)
        else resources.getQuantityString(R.plurals.tv_sync_device_count, current.devices.size, current.devices.size)
        val restore = devices.hasFocus() || (adapter.currentList.isEmpty() && selectedKey != null)
        empty.visibility = if (current.devices.isEmpty()) View.VISIBLE else View.GONE
        devices.visibility = if (current.devices.isEmpty()) View.GONE else View.VISIBLE
        val index = adapter.currentList.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
        if (adapter.currentList != current.devices)
            adapter.submitList(current.devices) {
                if (view != null && foreground && dialog?.isShowing != true && restore) focusDevice(index)
            }
    }

    private fun enable() {
        if (changing) return
        changing = true
        pairButton.isClickable = false
        try {
            store.enable(requireActivity())
            observe()
            load?.cancel()
            load = null
            reload()
        } catch (failure: Throwable) {
            showError(failure)
        } finally {
            changing = false
            pairButton.isClickable = true
        }
    }

    private fun showError(failure: Throwable) {
        Logger.w("TvSync", "Sync screen request failed", failure)
        error.text = getString(R.string.tv_sync_load_error)
        status.text = getString(R.string.tv_sync_unavailable)
        spinner.visibility = View.GONE
        pairButton.isEnabled = true
        pairButton.text = getString(R.string.tv_retry)
    }

    private fun enlarge() {
        val url = qrUrl ?: return
        val c = requireContext()
        val bitmap = TvSyncPairingCode.bitmap(url, c.dp(320))
        dialog?.dismiss()
        val large = Dialog(c)
        val content = TvUi.column(c).apply {
            gravity = Gravity.CENTER
            setPadding(c.dp(24), c.dp(20), c.dp(24), c.dp(20))
            addView(TvUi.heading(c, getString(R.string.tv_sync_pair)))
            addView(
                ImageView(c).apply {
                    id = R.id.tv_sync_large_qr
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Color.WHITE)
                    setPadding(c.dp(8), c.dp(8), c.dp(8), c.dp(8))
                    contentDescription = getString(R.string.tv_sync_qr_description)
                },
                LinearLayout.LayoutParams(c.dp(336), c.dp(336)).apply {
                    topMargin = c.dp(16)
                    bottomMargin = c.dp(12)
                })
            addView(
                TvUi.text(c, getString(R.string.tv_sync_instructions), 14f, true).apply {
                    gravity = Gravity.CENTER
                })
            addView(
                TvUi.button(c, getString(R.string.tv_close), large::dismiss).apply {
                    id = R.id.tv_panel_close
                    requestFocus()
                })
        }
        large.setContentView(content)
        large.setOnDismissListener { if (foreground) pairButton.requestFocus() }
        large.show()
        large.window?.apply {
            setBackgroundDrawable(ColorDrawable(TvUi.background))
            setLayout(-1, -1)
        }
        dialog = large
    }

    private fun requestPairing(request: TvSyncRequest) {
        if (!foreground) {
            request.respond(false)
            return
        }
        if (approvals.isNotEmpty()) {
            if (approvals.first().key == request.key) approvals.add(request) else request.respond(false)
            return
        }
        dialog?.dismiss()
        approvals.add(request)
        dialog = TvSyncPairingDialog(requireContext(), request.name ?: getString(R.string.tv_sync_new_device), request.key) { allow ->
            val pending = approvals.toList()
            approvals.clear()
            pending.forEach { it.respond(allow) }
        }.also { it.show() }
    }

    private fun deviceOptions(device: TvSyncDevice) {
        dialog = TvMenuDialog(requireContext()).also { d ->
            d.panel.title(device.name)
            val menu = d.panel.menu()
            menu.addView(
                TvUi.text(
                    requireContext(),
                    getString(if (device.connected) R.string.tv_sync_auto else R.string.tv_sync_reconnect),
                    15f,
                    true).apply {
                    setPadding(context.dp(12), 0, context.dp(12), context.dp(20))
                })
            menu.addView(
                d.panel
                    .option(
                        getString(
                            if (snapshot?.enabled == true) R.string.tv_sync_remove else R.string.tv_sync_enable)) {
                        d.dismiss()
                        if (snapshot?.enabled != true) enable() else confirmRemoval(device)
                    }.apply {
                    id = R.id.tv_sync_remove
                    requestFocus()
                })
            d.show()
        }
    }

    private fun confirmRemoval(device: TvSyncDevice) {
        dialog = TvMenuDialog(requireContext()).also { d ->
            d.confirm(
                getString(R.string.tv_sync_remove),
                getString(R.string.tv_sync_remove_hint, device.name),
                getString(R.string.tv_sync_remove)) {
                if (changing) return@confirm
                changing = true
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { store.remove(device.key) }
                        reload(silent = true)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        Logger.w("TvSync", "Device removal failed", failure)
                        error.text = getString(R.string.tv_sync_remove_error)
                    } finally {
                        changing = false
                    }
                }
            }
            d.show()
        }
    }

    private fun focusDevice(fallback: Int = 0) {
        if (adapter.itemCount == 0) {
            pairButton.requestFocus()
            return
        }
        val index = adapter.currentList.indexOfFirst { it.key == selectedKey }.takeIf { it >= 0 }
            ?: fallback.coerceIn(0, adapter.itemCount - 1)
        devices.findViewHolderForAdapterPosition(index)?.itemView?.let {
            it.requestFocus()
            return
        }
        devices.scrollToPosition(index)
        devices.doOnNextLayout { devices.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus() }
        devices.requestLayout()
    }
}
