package com.futo.platformplayer.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.HorizontalSpaceItemDecoration
import com.futo.platformplayer.R
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.viewholders.StoreItemViewHolder
import com.futo.platformplayer.views.platform.PlatformIndicator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoreItem(
    val url: String,
    val name: String,
    val image: String
);

class MonetizationView : LinearLayout {
    private val _buttonSupport: LinearLayout;
    private val _buttonStore: LinearLayout;
    private val _buttonMembership: LinearLayout;
    private val _membershipPlatform: PlatformIndicator;
    private var _membershipUrl: String? = null;

    private val _textMerchandise: TextView;
    private val _recyclerMerchandise: RecyclerView;
    private val _loaderViewMerchandise: LoaderView;
    private val _layoutMerchandise: FrameLayout;
    private var _merchandiseAdapterView: AnyAdapterView<StoreItem, StoreItemViewHolder>? = null;

    private val _root: LinearLayout;

    private val _taskLoadMerchandise = TaskHandler<String, List<StoreItem>>(StateApp.instance.scopeGetter, { url ->
        val client = ManagedHttpClient();
        val result = client.get("https://storecache.grayjay.app/StoreData?url=$url")
        if (!result.isOk) {
            throw Exception("Failed to retrieve store data.");
        }

        return@TaskHandler result.body?.let { Json.decodeFromString<List<StoreItem>>(it.string()); } ?: listOf();
    })
    .success { setMerchandise(it) }
    .exception<Throwable> {
        Logger.w(TAG, "Failed to load merchandise profile.", it);
    };

    val onSupportTap = Event0();
    val onStoreTap = Event0();
    val onUrlTap = Event1<String>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_monetization, this);
        _buttonSupport = findViewById(R.id.button_support);
        _buttonStore = findViewById(R.id.button_store);
        _buttonMembership = findViewById(R.id.button_membership);
        _membershipPlatform = findViewById(R.id.membership_platform);
        _buttonMembership.setOnClickListener {
            _membershipUrl?.let {
                /*
                val uri = Uri.parse(it);
                val intent = Intent(Intent.ACTION_VIEW);
                intent.data = uri;
                context.startActivity(intent);*/
                onUrlTap.emit(it);
            }
        }

        _textMerchandise = findViewById(R.id.text_merchandise);
        _recyclerMerchandise = findViewById(R.id.recycler_merchandise);
        _loaderViewMerchandise = findViewById(R.id.loader_merchandise);
        _layoutMerchandise = findViewById(R.id.layout_merchandise);

        _root = findViewById(R.id.root);

        _recyclerMerchandise.addItemDecoration(HorizontalSpaceItemDecoration(30, 16, 30))
        _merchandiseAdapterView = _recyclerMerchandise.asAny(orientation = RecyclerView.HORIZONTAL);

        _buttonSupport.setOnClickListener { onSupportTap.emit(); }
        _buttonStore.setOnClickListener { onStoreTap.emit(); }
        _buttonMembership.visibility = View.GONE;
        setMerchandise(null);
    }

    fun setPlatformMembership(pluginId: String?, url: String? = null) {
        if(pluginId.isNullOrEmpty() || url.isNullOrEmpty()) {
            _buttonMembership.visibility = GONE;
            _membershipUrl = null;
        }
        else {
            _membershipUrl = url;
            _membershipPlatform.setPlatformFromClientID(pluginId);
            _buttonMembership.visibility = VISIBLE;
        }
    }

    private fun setMerchandise(items: List<StoreItem>?) {
        _loaderViewMerchandise.stop();

        if (items == null) {
            _textMerchandise.visibility = View.GONE;
            _recyclerMerchandise.visibility = View.GONE;
            _layoutMerchandise.visibility = View.GONE;
        } else {
            _textMerchandise.visibility = View.VISIBLE;
            _recyclerMerchandise.visibility = View.VISIBLE;
            _layoutMerchandise.visibility = View.VISIBLE;
            _merchandiseAdapterView?.adapter?.setData(items.shuffled());
        }
    }

    fun setPolycentricProfile(cachedPolycentricProfile: PolycentricCache.CachedPolycentricProfile?) {
        val profile = cachedPolycentricProfile?.profile;
        if (profile != null) {
            if (profile.systemState.store.isNotEmpty()) {
                _buttonStore.visibility = View.VISIBLE;
            } else {
                _buttonStore.visibility = View.GONE;
            }

            if(profile.systemState.donationDestinations.isNotEmpty() ||
                profile.systemState.membershipUrls.isNotEmpty() ||
                profile.systemState.store.isNotEmpty() ||
                profile.systemState.promotion.isNotEmpty())
                _buttonSupport.isVisible = true;
            else
                _buttonSupport.isVisible = false;

            _root.visibility = View.VISIBLE;
        } else {
            _root.visibility = View.GONE;
            _buttonSupport.isVisible = false;
        }

        setMerchandise(null);
        val storeData = profile?.systemState?.storeData;
        if (storeData != null) {
            try {
                val storeItems = Json.decodeFromString<List<StoreItem>>(storeData);
                setMerchandise(storeItems);
            } catch (_: Throwable) {
                try {
                    val uri = Uri.parse(storeData);
                    if (uri.isAbsolute) {
                        _taskLoadMerchandise.run(storeData);
                        _loaderViewMerchandise.start();
                    } else {
                        Logger.i(TAG, "Merchandise not loaded, not URL nor JSON")
                    }
                } catch (_: Throwable) {

                }
            }
        }
    }

    companion object {
        const val TAG = "MonetizationView";
    }
}