package com.futo.platformplayer.views.overlays

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.shapes.Shape
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.IWithResultLauncher
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.viewholders.CreatorBarViewHolder
import com.futo.platformplayer.views.buttons.BigButton
import com.google.android.material.imageview.ShapeableImageView

class ImageVariableOverlay: ConstraintLayout {
    private val _buttonGallery: BigButton;
    private val _buttonSelect: Button;
    private val _recyclerPresets: AnyAdapterView<Int, PresetViewHolder>;
    private val _recyclerCreators: AnyAdapterView<IPlatformChannel, CreatorBarViewHolder>;

    private val _creators: ArrayList<IPlatformChannel> = arrayListOf();
    private val _presets: ArrayList<Int> = arrayListOf();

    private var _selected: ImageVariable? = null;

    val onSelected = Event1<ImageVariable>();

    constructor(context: Context): super(context) {
        inflate(context, R.layout.overlay_image_variable, this);
    }
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs) {
        inflate(context, R.layout.overlay_image_variable, this);
    }
    init {
        _buttonGallery = findViewById(R.id.button_gallery);
        _buttonSelect = findViewById(R.id.button_select);
        _recyclerPresets = findViewById<RecyclerView>(R.id.recycler_presets).asAny(_presets, RecyclerView.HORIZONTAL) {
            it.onClick.subscribe {
                _selected = ImageVariable(null, it);
                updateSelected();
            };
        };
        _recyclerCreators = findViewById<RecyclerView>(R.id.recycler_creators).asAny(_creators, RecyclerView.HORIZONTAL) { creatorView ->
            creatorView.onClick.subscribe {
                if(it.thumbnail == null) {
                    UIDialogs.toast(context, "No thumbnail found");
                    return@subscribe;
                }
                _selected = ImageVariable(it.thumbnail);
                updateSelected();
            };
        };

        _buttonGallery.setOnClickListener {
            val context = StateApp.instance.contextOrNull;
            if(context is IWithResultLauncher) {
                val intent = Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);

                context.launchForResult(intent, 888) {
                    if(it.resultCode == 888) {
                        val url = it.data?.data ?: return@launchForResult;
                        //TODO: Write to local storage
                        _selected = ImageVariable(url.toString());
                        updateSelected();
                    }
                };
            }
        };
        _buttonSelect.setOnClickListener {
            _selected?.let {
                select(it);
            }
        };
    }

    fun updateSelected() {
        if(_selected != null)
            _buttonSelect.alpha = 1f;
        else
            _buttonSelect.alpha = 0.5f;
    }

    fun select(variable: ImageVariable) {
        onSelected.emit(variable);
    }

    class PresetViewHolder(context: Context) : AnyAdapter.AnyViewHolder<Int>(ShapeableImageView(context)) {
        private val view = _view as ShapeableImageView;

        private var value: Int = 0;

        val onClick = Event1<Int>();
        init {
            view.setOnClickListener {
                onClick.emit(value);
            }
        }

        override fun bind(value: Int) {
            view.setImageResource(value);
            this.value = value;
        }
    }
}