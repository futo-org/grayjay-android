package com.futo.platformplayer.activities

import android.content.Context
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.MenuBottomBarSetting
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.fragment.mainactivity.bottombar.MenuBottomBarFragment
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.ItemMoveCallback
import com.futo.platformplayer.views.adapters.viewholders.TabViewHolder
import com.futo.platformplayer.views.adapters.viewholders.TabViewHolderData
import java.util.*

class ManageTabsActivity : AppCompatActivity() {
    private lateinit var _buttonBack: ImageButton;
    private lateinit var _listTabs: AnyAdapterView<TabViewHolderData, TabViewHolder>;
    private lateinit var _recyclerTabs: RecyclerView;
    private lateinit var _touchHelper: ItemTouchHelper;

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_tabs);
        setNavigationBarColorAndIcons();

        _buttonBack = findViewById(R.id.button_back);

        val callback = ItemMoveCallback();
        _touchHelper = ItemTouchHelper(callback);
        _recyclerTabs = findViewById(R.id.recycler_tabs);
        _touchHelper.attachToRecyclerView(_recyclerTabs);

        val itemsRemoved = Settings.instance.tabs.removeIf { MenuBottomBarFragment.buttonDefinitions.none { d -> it.id == d.id } }

        var itemsAdded = false
        for (buttonDefinition in MenuBottomBarFragment.buttonDefinitions) {
            if (Settings.instance.tabs.none { it.id == buttonDefinition.id }) {
                Settings.instance.tabs.add(MenuBottomBarSetting(buttonDefinition.id, true))
                itemsAdded = true
            }
        }

        if (itemsAdded || itemsRemoved) {
            Settings.instance.save()
        }

        val items = ArrayList(Settings.instance.tabs.mapNotNull {
            val buttonDefinition = MenuBottomBarFragment.buttonDefinitions.find { d -> it.id == d.id } ?: return@mapNotNull null
            TabViewHolderData(buttonDefinition, it.enabled)
        });

        _listTabs = _recyclerTabs.asAny(items) {
            it.onDragDrop.subscribe { vh ->
                _touchHelper.startDrag(vh);
            };
            it.onEnableChanged.subscribe { enabled ->
                val d = it.data ?: return@subscribe
                Settings.instance.tabs.find { def -> d.buttonDefinition.id == def.id }?.enabled = enabled
                Settings.instance.onTabsChanged.emit()
                Settings.instance.save()
            };
        };

        callback.onRowMoved.subscribe { fromPosition, toPosition ->
            if (fromPosition < toPosition) {
                for (i in fromPosition until toPosition) {
                    Collections.swap(items, i, i + 1)
                    Collections.swap(Settings.instance.tabs, i, i + 1)
                }

                Settings.instance.onTabsChanged.emit()
                Settings.instance.save()
            } else {
                for (i in fromPosition downTo toPosition + 1) {
                    Collections.swap(items, i, i - 1)
                    Collections.swap(Settings.instance.tabs, i, i - 1)
                }

                Settings.instance.onTabsChanged.emit()
                Settings.instance.save()
            }

            _listTabs.adapter.notifyItemMoved(fromPosition, toPosition);
        };

        _buttonBack.setOnClickListener {
            onBackPressed();
        };
    }

    companion object {
        private const val TAG = "ManageTabsActivity";
    }
}