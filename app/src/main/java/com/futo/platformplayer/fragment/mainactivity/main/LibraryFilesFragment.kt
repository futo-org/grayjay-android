package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.structures.AdhocPager
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fragment.mainactivity.topbar.FilesTopBarFragment
import com.futo.platformplayer.states.FileEntry
import com.futo.platformplayer.states.StateLibrary
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.NoResultsView
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.viewholders.FileViewHolder
import com.futo.platformplayer.views.buttons.BigButton

class LibraryFilesFragment : MainFragment() {
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
        view?.onShown(parameter);
    }

    override fun onDestroyMainView() {
        view = null;
        super.onDestroyMainView();
    }

    companion object {
        fun newInstance() = LibraryFilesFragment().apply {}
    }

    class FragView : FeedView<LibraryFilesFragment, FileEntry, FileEntry, IPager<FileEntry>, FileViewHolder> {
        override val feedStyle: FeedStyle = FeedStyle.THUMBNAIL; //R.layout.list_creator;

        val navStack = mutableListOf<FileStack>()
        var buttonUp: BigButton? = null;
        var buttonAdd: BigButton? = null;

        private var root: FileEntry? = null;

        constructor(fragment: LibraryFilesFragment, inflater: LayoutInflater) : super(fragment, inflater) {
        }

        fun onShown(parameter: Any? = null) {
            this.root = if(parameter is FileEntry) parameter else null;
            loadTop();
        }
        fun loadTop() {
            var initialDirectories = listOf<FileEntry>();
            if(root == null) {
                initialDirectories = StateLibrary.instance.getFileDirectories();
                if (initialDirectories.size == 0) {
                    setEmptyPager(true);
                    setPager(EmptyPager());
                    buttonAdd?.let {
                        it.isVisible = false;
                    }
                    buttonUp?.let {
                        it.isVisible = false;
                    }
                    return;
                } else
                    setEmptyPager(false);
            }
            else {
                buttonAdd?.let {
                    it.isVisible = false;
                }
                buttonUp?.let {
                    it.isVisible = false;
                }
                initialDirectories = root?.getSubFiles() ?: listOf();
            }
            navStack.clear();
            val entry = FileStack("", initialDirectories);
            navStack.add(entry);
            openDirectory(navStack.last());
            fragment.topBar?.let {
                if(it is FilesTopBarFragment) {
                    it.setUpNavigate(null);
                    it.setTitle(entry);
                }
            }
        }
        fun leaveDirectory() {
            if(navStack.size > 1) {
                navStack.removeLast();
                openDirectory(navStack.last());
            }
            else {}
        }
        fun openDirectory(stack: FileStack, addToStack: Boolean = false) {
            if(addToStack)
                navStack.add(stack);

            fragment.topBar?.let {
                if(it is FilesTopBarFragment) {
                    it.setTitle(stack);
                }
            }

            buttonAdd?.let {
                it.isVisible = navStack.size < 2
            }
            buttonUp?.let {
                it.isVisible = navStack.size > 1;
            }
            setPager(AdhocPager<FileEntry>({ listOf(); }, stack.files));
            setLoading(false);

            fragment.topBar?.let {
                if(it is FilesTopBarFragment) {
                    if(navStack.size > 1)
                        it.setUpNavigate{
                          leaveDirectory();
                        };
                    else it.setUpNavigate(null);
                    it.setTitle(stack);
                }
            }
        }

        fun setBack() {
            fragment.topBar?.view
        }

        override fun getEmptyPagerView(): View? {
            return NoResultsView(context, "No Directories Added",
                "To see files in Grayjay you have to add directories to view",
                R.drawable.ic_library, listOf(
                    BigButton(context, "Add Directory", "Select a directory to add", R.drawable.ic_add, {
                        StateLibrary.instance.addFileDirectory({
                            loadTop();
                        }, true);
                    })
                ))
        }

        override fun createAdapter(recyclerResults: RecyclerView, context: Context, dataset: ArrayList<FileEntry>): InsertedViewAdapterWithLoader<FileViewHolder> {
            /*
            val buttonUp = BigButton(fragment.requireContext(), "Go up", "Go up a directory", R.drawable.ic_move_up) {
                if(navStack.size > 1)
                    leaveDirectory();
            }
            val buttonAdd = BigButton(fragment.requireContext(), "Add Directory", "Select a directory to add", R.drawable.ic_add) {
                StateLibrary.instance.addFileDirectory {
                    loadTop();
                };
            }
            */
            //this.buttonUp = buttonUp;
            //this.buttonAdd = buttonAdd;
            return InsertedViewAdapterWithLoader(context, arrayListOf(), arrayListOf(),
                childCountGetter = { dataset.size },
                childViewHolderBinder = { viewHolder, position -> viewHolder.bind(dataset[position]); },
                childViewHolderFactory = { viewGroup, _ ->
                    val holder = FileViewHolder(viewGroup);
                    holder.onClick.subscribe { c ->
                        if (c != null) {
                            if(c.isDirectory) {
                                openDirectory(FileStack(c.path, c.getSubFiles()), true);
                            } else {
                                fragment.navigate<VideoDetailFragment>(c.path)
                            }
                        }
                    };
                    holder.onDelete.subscribe { c ->
                        if(c != null) {
                            StateLibrary.instance.deleteFileDirectory(c.path);
                            loadTop();
                        }
                    }
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
    class FileStack(
        val path: String,
        val files: List<FileEntry>
    )

}