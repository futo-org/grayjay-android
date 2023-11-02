package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.fragment.mainactivity.topbar.ImportTopBarFragment
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.viewholders.ImportSubscriptionViewHolder
import com.futo.platformplayer.views.adapters.viewholders.SelectableIPlatformChannel
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlatform

class ImportSubscriptionsFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: ImportSubscriptionsView? = null;


    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown(parameter, isBack);
    }

    override fun onHide() {
        super.onHide();

        val tb = this.topBar as ImportTopBarFragment?;
        tb?.onImport?.remove(this);
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = ImportSubscriptionsView(this, inflater);
        _view = view;
        return view;
    }
    
    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view?.cleanup();
        _view = null;
    }

    @SuppressLint("ViewConstructor")
    class ImportSubscriptionsView : LinearLayout {
        private val _fragment: ImportSubscriptionsFragment;

        private var _spinner: ImageView;
        private var _textSelectDeselectAll: TextView;
        private var _textNothingToImport: TextView;
        private var _textCounter: TextView;
        private var _adapterView: AnyAdapterView<SelectableIPlatformChannel, ImportSubscriptionViewHolder>;
        private var _links: List<String> = listOf();
        private val _items: ArrayList<SelectableIPlatformChannel> = arrayListOf();
        private var _currentLoadIndex = 0;

        private var _taskLoadChannel: TaskHandler<String, IPlatformChannel>;
        private var _counter: Int = 0;
        private var _limitToastShown = false;

        constructor(fragment: ImportSubscriptionsFragment, inflater: LayoutInflater) : super(inflater.context) {
            _fragment = fragment;
            inflater.inflate(R.layout.fragment_import, this);

            _textNothingToImport = findViewById(R.id.nothing_to_import);
            _textSelectDeselectAll = findViewById(R.id.text_select_deselect_all);
            _textCounter = findViewById(R.id.text_select_counter);
            _spinner = findViewById(R.id.channel_loader);

            _adapterView = findViewById<RecyclerView>(R.id.recycler_import).asAny( _items) {
                it.onSelectedChange.subscribe { c ->
                    updateSelected();
                };
            };

            _textSelectDeselectAll.setOnClickListener {
                val itemsSelected = _items.count { i -> i.selected };
                if (itemsSelected > 0) {
                    for (i in _items) {
                        i.selected = false;
                    }
                } else {
                    for (i in _items) {
                        i.selected = true;
                    }
                }

                _adapterView.adapter.notifyContentChanged();
                updateSelected();
            };

            setLoading(false);

            _taskLoadChannel = TaskHandler<String, IPlatformChannel>({_fragment.lifecycleScope}, { link ->
                _counter++;
                val channel: IPlatformChannel = StatePlatform.instance.getChannelLive(link, false);
                return@TaskHandler channel;
            }).success {
                _items.add(SelectableIPlatformChannel(it));
                _adapterView.adapter.notifyItemInserted(_items.size - 1);
                loadNext();
            }.exceptionWithParameter<Throwable> { ex, para ->
                //setLoading(false);
                Logger.w(ChannelFragment.TAG, "Failed to load results.", ex);
                UIDialogs.toast(context, context.getString(R.string.failed_to_fetch) + "\n${para}", false)
                //UIDialogs.showDataRetryDialog(layoutInflater, { load(); });
                loadNext();
            };
        }

        fun cleanup() {
            _taskLoadChannel.cancel();
        }

        fun onShown(parameter: Any ?, isBack: Boolean) {
            _counter = 0;
            _limitToastShown = false;
            updateSelected();

            val itemsRemoved = _items.size;
            _items.clear();
            _adapterView?.adapter?.notifyItemRangeRemoved(0, itemsRemoved);

            _links = (parameter as List<String>).filter { i -> !StateSubscriptions.instance.isSubscribed(i) }.toList();
            _currentLoadIndex = 0;
            if (_links.isNotEmpty()) {
                load();
                _textNothingToImport.visibility = View.GONE;
            } else {
                setLoading(false);
                _textNothingToImport.visibility = View.VISIBLE;
            }

            val tb = _fragment.topBar as ImportTopBarFragment?;
            tb?.let {
                it.title = context.getString(R.string.import_subscriptions);
                it.onImport.subscribe(this) {
                    val subscriptionsToImport = _items.filter { i -> i.selected }.toList();
                    for (subscriptionToImport in subscriptionsToImport) {
                        StateSubscriptions.instance.addSubscription(subscriptionToImport.channel);
                    }

                    UIDialogs.toast("${subscriptionsToImport.size} " + context.getString(R.string.subscriptions_imported));
                    _fragment.closeSegment();
                };
            }
        }

        private fun load() {
            setLoading(true);
            if (_counter >= MAXIMUM_BATCH_SIZE) {
                if (!_limitToastShown) {
                    _limitToastShown = true;
                    UIDialogs.toast(context, "Stopped after {requestCount} to avoid rate limit, re-enter to import rest".replace("{requestCount}", MAXIMUM_BATCH_SIZE.toString()));
                }

                setLoading(false);
                return;
            }
            _taskLoadChannel.run(_links[_currentLoadIndex]);
        }

        private fun loadNext() {
            _currentLoadIndex++;
            if (_currentLoadIndex < _links.size) {
                load();
            } else {
                setLoading(false);
            }
        }

        private fun updateSelected() {
            val itemsSelected = _items.count { i -> i.selected };
            if (itemsSelected > 0) {
                _textSelectDeselectAll.text = context.getString(R.string.deselect_all);
                _textCounter.text = context.getString(R.string.index_out_of_size_selected).replace("{index}", itemsSelected.toString()).replace("{size}", _items.size.toString());
                (_fragment.topBar as ImportTopBarFragment?)?.setImportEnabled(true);
            } else {
                _textSelectDeselectAll.text = context.getString(R.string.select_all);
                _textCounter.text = "";
                (_fragment.topBar as ImportTopBarFragment?)?.setImportEnabled(false);
            }
        }

        private fun setLoading(isLoading: Boolean) {
            if(isLoading){
                (_spinner.drawable as Animatable?)?.start();
                _spinner.visibility = View.VISIBLE;
            }
            else {
                _spinner.visibility = View.GONE;
                (_spinner.drawable as Animatable?)?.stop();
            }
        }
    }

    companion object {
        val TAG = "ImportSubscriptionsFragment";
        private const val MAXIMUM_BATCH_SIZE = 90;
        fun newInstance() = ImportSubscriptionsFragment().apply {}
    }
}