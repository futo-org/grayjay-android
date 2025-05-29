package com.futo.platformplayer.views.overlays

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.shapes.Shape
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.PresetImages
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.IWithResultLauncher
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.dp
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.viewholders.CreatorBarViewHolder
import com.futo.platformplayer.views.adapters.viewholders.SelectableCreatorBarViewHolder
import com.futo.platformplayer.views.buttons.BigButton
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import java.io.File

class ImageVariableOverlay: ConstraintLayout {
    private val _buttonGallery: BigButton;
    private val _imageGallerySelected: ImageView;
    private val _imageGallerySelectedContainer: LinearLayout;
    private val _buttonSelect: TextView;
    private val _topbar: OverlayTopbar;
    private val _recyclerPresets: AnyAdapterView<PresetImage, PresetViewHolder>;
    private val _recyclerCreators: AnyAdapterView<SelectableCreatorBarViewHolder.Selectable, SelectableCreatorBarViewHolder>;

    private val _creators: ArrayList<SelectableCreatorBarViewHolder.Selectable> = arrayListOf();
    private val _presets: ArrayList<PresetImage> =
        ArrayList(PresetImages.images.map { PresetImage(it.value, it.key, false) });

    private var _selected: ImageVariable? = null;
    private var _selectedFile: String? = null;

    val onSelected = Event1<ImageVariable>();
    val onClose = Event0();

    constructor(context: Context, creatorFilters: List<String>? = null): super(context) {
        val subs = StateSubscriptions.instance.getSubscriptions();
        if(creatorFilters != null) {
            _creators.addAll(subs
                .filter { creatorFilters.contains(it.channel.url) }
                .map { SelectableCreatorBarViewHolder.Selectable(it.channel, false) });
        }
        else
            _creators.addAll(subs
                .map { SelectableCreatorBarViewHolder.Selectable(it.channel, false) });
        _recyclerCreators.notifyContentChanged();
    }
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs) { }
    init {
        inflate(context, R.layout.overlay_image_variable, this);
        _topbar = findViewById(R.id.topbar);
        _buttonGallery = findViewById(R.id.button_gallery);
        _imageGallerySelected = findViewById(R.id.gallery_selected);
        _imageGallerySelectedContainer = findViewById(R.id.gallery_selected_container);
        _buttonSelect = findViewById(R.id.button_select);
        _recyclerPresets = findViewById<RecyclerView>(R.id.recycler_presets).asAny(_presets, RecyclerView.HORIZONTAL) {
            it.onClick.subscribe {
                _selected = ImageVariable.fromPresetName(it.name);
                updateSelected();
            };
        };
        val dp6 = 6.dp(resources);
        _recyclerCreators = findViewById<RecyclerView>(R.id.recycler_creators).asAny(_creators, RecyclerView.HORIZONTAL) { creatorView ->
            creatorView.itemView.setPadding(0, dp6, 0, dp6);
            creatorView.onClick.subscribe {
                if(it.channel.thumbnail == null) {
                    UIDialogs.toast(context, "No thumbnail found");
                    return@subscribe;
                }
                val channelUrl = it.channel.url;
                _selected = ImageVariable(it.channel.thumbnail).let {
                    it.subscriptionUrl = channelUrl;
                    return@let it;
                }
                updateSelected();
            };
        };
        _recyclerCreators.view.layoutManager = GridLayoutManager(context, 5).apply {
            this.orientation = LinearLayoutManager.VERTICAL;
        };

        _buttonGallery.onClick.subscribe {
            val context = StateApp.instance.contextOrNull;
            if(context is IWithResultLauncher && context is MainActivity) {
                ImagePicker.with(context)
                    .compress(512)
                    .maxResultSize(750, 500)
                    .createIntent {
                        context.launchForResult(it, 888) {
                            if(it.resultCode == Activity.RESULT_OK) {
                                cleanupLastFile();
                                val fileUri = it.data?.data;
                                if(fileUri != null) {
                                    val file = fileUri.toFile();
                                    val ext = file.extension;
                                    val persistFile = StateApp.instance.getPersistFile(ext);
                                    file.copyTo(persistFile);
                                    _selectedFile = persistFile.toUri().toString();
                                    _selected = ImageVariable(_selectedFile);
                                    updateSelected();
                                }
                            }
                        };
                    };
            }
        };
        _imageGallerySelectedContainer.setOnClickListener {
            if(_selectedFile != null) {
                _selected = ImageVariable(_selectedFile);
                updateSelected();
            }
        }
        _buttonSelect.setOnClickListener {
            _selected?.let {
                select(it);
            }
        };
        _topbar.onClose.subscribe {
            onClose.emit();
        }
        updateSelected();
    }

    fun updateSelected() {
        val id = _selected?.resId;
        val name = _selected?.presetName;
        val url = _selected?.url;
        _presets.forEach { p -> p.active = p.name == name };
        _recyclerPresets.notifyContentChanged();
        _creators.forEach { p -> p.active = p.channel.thumbnail == url };
        _recyclerCreators.notifyContentChanged();

        if(_selectedFile != null) {
            _imageGallerySelectedContainer.visibility = View.VISIBLE;
            Glide.with(_imageGallerySelected)
                .load(_selectedFile)
                .into(_imageGallerySelected);
        }
        else
            _imageGallerySelectedContainer.visibility = View.GONE;

        if(_selected?.url == _selectedFile)
            _imageGallerySelectedContainer.setBackgroundColor(resources.getColor(R.color.colorPrimary, null));
        else
            _imageGallerySelectedContainer.setBackgroundColor(resources.getColor(R.color.transparent, null));

        if(_selected != null)
            _buttonSelect.alpha = 1f;
        else
            _buttonSelect.alpha = 0.5f;
    }
    fun cleanupLastFile() {
        _selectedFile?.let {
            val file = File(it);
            if(file.exists())
                file.delete();
            _selectedFile = null;
        }
    }


    fun select(variable: ImageVariable) {
        if(_selected?.url != _selectedFile)
            cleanupLastFile();
        onSelected.emit(variable);
        onClose.emit();
    }

    class PresetViewHolder(viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<PresetImage>(LinearLayout(viewGroup.context)) {
        private val view = _view as LinearLayout;
        private val imageView = ShapeableImageView(viewGroup.context);

        private var value: PresetImage = PresetImage(0, "", false);

        val onClick = Event1<PresetImage>();
        init {
            view.addView(imageView);
            val dp2 = 2.dp(viewGroup.context.resources);
            val dp6 = 6.dp(viewGroup.context.resources);
            view.setPadding(dp2, dp2, dp2, dp2);
            imageView.setOnClickListener {
                onClick.emit(value);
            }
            imageView.layoutParams = LinearLayout.LayoutParams(110.dp(viewGroup.context.resources), 70.dp(viewGroup.context.resources)).apply {
                //this.rightMargin = dp6
            }
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, dp6.toFloat())
                .build()
        }

        override fun bind(value: PresetImage) {
            imageView.setImageResource(value.id);
            this.value = value;
            setActive(value.active);
        }

        fun setActive(active: Boolean) {
            if(active)
                _view.setBackgroundColor(view.context.resources.getColor(R.color.colorPrimary, null));
            else
                _view.setBackgroundColor(view.context.resources.getColor(R.color.transparent, null));
        }
    }

    data class PresetImage(var id: Int, var name: String, var active: Boolean);
}