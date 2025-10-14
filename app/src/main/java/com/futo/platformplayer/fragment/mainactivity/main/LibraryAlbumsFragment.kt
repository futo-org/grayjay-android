package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout.GONE
import android.widget.LinearLayout.VISIBLE
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.structures.AdhocPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.states.StateLibrary
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.SubscriptionAdapter
import com.futo.platformplayer.views.adapters.viewholders.SelectablePlaylist
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.platform.PlatformIndicator

class LibraryAlbumsFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;


    var view: FragView? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = FragView(this, inflater);
        this.view = view;
        return view;
    }

    override fun onShown(parameter: Any?, isBack: Boolean) {
        super.onShown(parameter, isBack)
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        view?.onShown();
    }

    override fun onDestroyMainView() {
        view = null;
        super.onDestroyMainView();
    }

    companion object {
        fun newInstance() = LibraryAlbumsFragment().apply {}
    }

    class FragView : FeedView<LibraryAlbumsFragment, Album, Album, IPager<Album>, AlbumViewHolder> {
        override val feedStyle: FeedStyle = FeedStyle.THUMBNAIL; //R.layout.list_creator;

        constructor(fragment: LibraryAlbumsFragment, inflater: LayoutInflater) : super(fragment, inflater)

        fun onShown() {
            val initialAlbums = StateLibrary.instance.getAlbums();
            Logger.i(TAG, "Initial album count: " + initialAlbums.size);

            setPager(AdhocPager<Album>({ listOf(); }, initialAlbums));
        }

        override fun createAdapter(recyclerResults: RecyclerView, context: Context, dataset: ArrayList<Album>): InsertedViewAdapterWithLoader<AlbumViewHolder> {
            return InsertedViewAdapterWithLoader(context, arrayListOf(), arrayListOf(),
                childCountGetter = { dataset.size },
                childViewHolderBinder = { viewHolder, position -> viewHolder.bind(dataset[position]); },
                childViewHolderFactory = { viewGroup, _ ->
                    val holder = AlbumViewHolder(viewGroup);
                    holder.onClick.subscribe { c -> fragment.navigate<LibraryAlbumFragment>(c) };
                    return@InsertedViewAdapterWithLoader holder;
                }
            );
        }

        override fun updateSpanCount(){ }

        override fun createLayoutManager(recyclerResults: RecyclerView, context: Context): GridLayoutManager {
            val glmResults = GridLayoutManager(context, 1)

            _swipeRefresh.layoutParams = (_swipeRefresh.layoutParams as MarginLayoutParams?)?.apply {
                rightMargin = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8.0f,
                    context.resources.displayMetrics
                ).toInt()
            }

            return glmResults
        }

        companion object {
            private const val TAG = "LibraryAlbumsFragmentsView";
        }
    }

    class AlbumViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<Album>(
        LayoutInflater.from(_viewGroup.context).inflate(R.layout.list_album,
            _viewGroup, false)) {

        val onClick = Event1<Album?>();

        protected var _album: Album? = null;
        protected val _imageThumbnail: ImageView
        protected val _textName: TextView
        protected val _textMetadata: TextView

        init {
            _imageThumbnail = _view.findViewById(R.id.image_thumbnail);
            _textName = _view.findViewById(R.id.text_name);
            _textMetadata = _view.findViewById(R.id.text_metadata);

            _view.setOnClickListener { onClick.emit(_album) };
        }


        override fun bind(album: Album) {
            _album = album;
            _imageThumbnail?.let {
                if (album.thumbnail != null)
                    Glide.with(it)
                        .load(album.thumbnail)
                        .placeholder(R.drawable.placeholder_channel_thumbnail)
                        .into(it)
                else
                    Glide.with(it).load(R.drawable.placeholder_channel_thumbnail).into(it);
            };

            _textName.text = album.name;
            _textMetadata.text = album.artist ?: "";
        }

    }
}