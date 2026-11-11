package com.futo.platformplayer.fragment.mainactivity.main

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.dp
import com.futo.platformplayer.states.StateDownloadSync
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.models.SyncDownloadItem
import com.futo.platformplayer.toHumanBytesSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * "Downloads sync" page: lists the downloads of paired devices and copies them to this device
 * on the same Wi-Fi. Separate from the Sync page. Pull only; delete stays local.
 */
class DownloadsSyncFragment : MainFragment() {
    override val isMainView: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _root: LinearLayout? = null;
    private var _device: String? = null;
    private var _onlyMissing = true;
    private val _selected = HashSet<String>();

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext();
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL;
            val p = 16.dp(resources);
            setPadding(p, p, p, p);
        };
        _root = root;
        return ScrollView(ctx).apply { addView(root) };
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        if (!isBack) {
            _device = null;
            _selected.clear();
        }
        StateDownloadSync.instance.start();
        StateSync.instance.getAll().forEach { StateDownloadSync.instance.requestIndex(it) };
        render();
    }

    override fun onResume() {
        super.onResume();
        StateDownloadSync.instance.onRemoteIndexChanged.subscribe(this) {
            lifecycleScope.launch(Dispatchers.Main) { render() };
        };
    }

    override fun onPause() {
        super.onPause();
        StateDownloadSync.instance.onRemoteIndexChanged.remove(this);
    }

    override fun onBackPressed(): Boolean {
        if (_device != null) {
            _device = null;
            _selected.clear();
            render();
            return true;
        }
        return false;
    }

    private fun render() {
        val root = _root ?: return;
        root.removeAllViews();
        val device = _device;
        if (device == null) renderDevices(root) else renderDevice(root, device);
    }

    private fun deviceName(publicKey: String): String =
        StateSync.instance.getCachedName(publicKey) ?: StateDownloadSync.instance.getRemoteIndex(publicKey)?.index?.deviceName ?: publicKey.take(8);

    private fun deviceStatus(publicKey: String): String {
        val sync = StateDownloadSync.instance;
        return when {
            sync.isSameNetwork(publicKey) -> "Online, same Wi-Fi";
            sync.isOnline(publicKey) -> "Online, not on the same Wi-Fi";
            else -> sync.getRemoteIndex(publicKey)?.let { "Offline, last seen ${ago(it.receivedAt)}" } ?: "Offline";
        };
    }

    private fun renderDevices(root: LinearLayout) {
        root.addView(text("Downloads sync", 22f, bold = true));
        root.addView(text("See the downloads of your paired devices and copy them to this device. Copy needs the same Wi-Fi. Nothing copies by itself, and a delete stays on its device.", 13f, Color.LTGRAY));
        val devices = StateSync.instance.getAll();
        if (devices.isEmpty()) {
            root.addView(text("\nNo paired devices. Pair a device on the Sync page first (Settings > Sync Grayjay).", 14f));
            return;
        }
        for (publicKey in devices) {
            val index = StateDownloadSync.instance.getRemoteIndex(publicKey);
            val count = index?.index?.items?.size;
            val row = text("${deviceName(publicKey)}\n${deviceStatus(publicKey)}" + (if (count != null) " • $count downloads" else " • list not received yet"), 15f).apply {
                setPadding(0, 24.dp(resources), 0, 24.dp(resources));
                setOnClickListener {
                    _device = publicKey;
                    _selected.clear();
                    StateDownloadSync.instance.requestIndex(publicKey);
                    render();
                };
            };
            root.addView(row);
        }
    }

    private fun renderDevice(root: LinearLayout, publicKey: String) {
        val ctx = requireContext();
        val sync = StateDownloadSync.instance;
        val name = deviceName(publicKey);
        val online = sync.isOnline(publicKey);
        val sameNetwork = sync.isSameNetwork(publicKey);

        root.addView(text(name, 22f, bold = true));
        root.addView(text(deviceStatus(publicKey), 13f, Color.LTGRAY));

        val index = sync.getRemoteIndex(publicKey);
        if (index == null) {
            root.addView(text("\nThe download list of $name is not received yet. Open Grayjay on $name.", 14f));
            return;
        }

        val localKeys = sync.getLocalQualityKeys();
        val all = index.index.items;
        val missingHere = all.filter { it.qualityKey !in localKeys };
        root.addView(CheckBox(ctx).apply {
            text = "Show only videos not on this phone (${missingHere.size} of ${all.size})";
            setTextColor(Color.WHITE);
            isChecked = _onlyMissing;
            setOnCheckedChangeListener { _, checked -> _onlyMissing = checked; render() };
        });
        root.addView(Button(ctx).apply {
            text = "Select all missing";
            setOnClickListener {
                _selected.clear();
                _selected.addAll(missingHere.filter { it.available }.map { it.itemId });
                render();
            };
        });

        val shown = if (_onlyMissing) missingHere else all;
        for (item in shown) {
            val onBoth = item.qualityKey in localKeys;
            val badge = when {
                !item.available -> "Missing on $name";
                onBoth -> "On this phone";
                else -> "";
            };
            val height = item.files.filter { it.kind == SyncDownloadItem.KIND_VIDEO }.maxOfOrNull { it.height };
            val info = listOfNotNull(item.author, height?.let { "${it}p" }, item.totalSize.toHumanBytesSize(), item.downloadDate?.let { DateFormat.getDateInstance().format(Date(it)) }).joinToString(" • ");
            root.addView(CheckBox(ctx).apply {
                text = item.name + "\n" + info + (if (badge.isNotEmpty()) "\n[$badge]" else "");
                setTextColor(if (item.available) Color.WHITE else Color.GRAY);
                isEnabled = item.available && !onBoth;
                isChecked = item.itemId in _selected;
                setPadding(0, 8.dp(resources), 0, 8.dp(resources));
                setOnCheckedChangeListener { _, checked ->
                    if (checked) _selected.add(item.itemId) else _selected.remove(item.itemId);
                    render();
                };
            });
        }
        if (shown.isEmpty())
            root.addView(text("\nNothing to show.", 14f));

        val selectedItems = all.filter { it.itemId in _selected };
        val size = selectedItems.sumOf { it.totalSize };
        val free = StateDownloads.instance.getAvailableBytes(StateDownloads.instance.getDownloadsDirectory());
        val enoughSpace = size < free;
        root.addView(text("\n${selectedItems.size} items, ${size.toHumanBytesSize()}. Free in download folder: ${free.toHumanBytesSize()}", 14f));
        val reason = when {
            !online -> "$name is offline. Open Grayjay on $name.";
            !sameNetwork -> "Join the same Wi-Fi as $name to copy.";
            !enoughSpace -> "Not enough free space in the download folder.";
            else -> null;
        };
        if (reason != null)
            root.addView(text(reason, 13f, Color.rgb(255, 170, 0)));
        root.addView(Button(ctx).apply {
            text = "Copy to this phone";
            isEnabled = reason == null && selectedItems.isNotEmpty();
            setOnClickListener { copy(publicKey, selectedItems) };
        });
    }

    private fun copy(publicKey: String, items: List<SyncDownloadItem>) {
        val ctx = context ?: return;
        UIDialogs.showDialogProgress(ctx) { dialog ->
            dialog.setText("Copying (0/${items.size})");
            lifecycleScope.launch(Dispatchers.IO) {
                var last = 0L;
                val result = StateDownloadSync.instance.copyFromDevice(publicKey, items, { i, n, done, total ->
                    val now = System.currentTimeMillis();
                    if (now - last > 200) {
                        last = now;
                        lifecycleScope.launch(Dispatchers.Main) {
                            dialog.setText("Copying (${i + 1}/$n)\n${done.toHumanBytesSize()} of ${total.toHumanBytesSize()}");
                            dialog.setProgress(done.toDouble() / total);
                        };
                    }
                }, { false });
                withContext(Dispatchers.Main) {
                    dialog.dismiss();
                    _selected.clear();
                    render();
                    if (result.error == null)
                        UIDialogs.showDialogOk(ctx, R.drawable.ic_download, "Copied ${result.copied} videos. They are in Downloads.");
                    else
                        UIDialogs.showDialogOk(ctx, R.drawable.ic_warning_yellow, "Copied ${result.copied} of ${result.total} videos, then stopped:\n${result.error.message}\n\nCopy again to continue from where it stopped.");
                };
            };
        };
    }

    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(requireContext()).apply {
            text = value;
            textSize = size;
            setTextColor(color);
            gravity = Gravity.START;
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD);
        };

    private fun ago(ms: Long): String {
        val minutes = (System.currentTimeMillis() - ms) / 60000;
        return when {
            minutes < 1 -> "now";
            minutes < 60 -> "$minutes min ago";
            minutes < 48 * 60 -> "${minutes / 60} h ago";
            else -> "${minutes / (60 * 24)} days ago";
        };
    }

    companion object {
        fun newInstance() = DownloadsSyncFragment().apply {};
    }
}
