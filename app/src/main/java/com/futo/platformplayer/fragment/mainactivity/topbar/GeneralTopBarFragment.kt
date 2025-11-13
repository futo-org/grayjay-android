package com.futo.platformplayer.fragment.mainactivity.topbar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.fragment.mainactivity.main.CreatorsFragment
import com.futo.platformplayer.fragment.mainactivity.main.LibraryFragment
import com.futo.platformplayer.fragment.mainactivity.main.LibrarySearchFragment
import com.futo.platformplayer.fragment.mainactivity.main.PlaylistFragment
import com.futo.platformplayer.fragment.mainactivity.main.PlaylistsFragment
import com.futo.platformplayer.fragment.mainactivity.main.SuggestionsFragment
import com.futo.platformplayer.fragment.mainactivity.main.SuggestionsFragmentData
import com.futo.platformplayer.models.SearchType
import com.futo.platformplayer.views.casting.CastButton

class GeneralTopBarFragment : TopFragment() {
    private var _buttonSearch: ImageButton? = null;
    private var _buttonCast: CastButton? = null;

    override fun onShown(parameter: Any?) {
        if(currentMain is CreatorsFragment) {
            _buttonSearch?.setImageResource(R.drawable.ic_person_search_300w);
        } else {
            _buttonSearch?.setImageResource(R.drawable.ic_search_300w);
        }
    }
    override fun onHide() {

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_overview_top_bar, container, false);

        view.findViewById<ImageView>(R.id.app_icon).setOnClickListener {
            UIDialogs.toast(getString(R.string.this_app_is_in_development_please_submit_bug_reports_and_understand_that_many_features_are_incomplete), true);
        };

        val buttonSearch: ImageButton = view.findViewById(R.id.button_search);
        _buttonCast = view.findViewById(R.id.button_cast);

        buttonSearch.setOnClickListener {
            if(currentMain is CreatorsFragment) {
                navigate<SuggestionsFragment>(SuggestionsFragmentData("", SearchType.CREATOR));
            } else if (currentMain is PlaylistsFragment || currentMain is PlaylistFragment) {
                navigate<SuggestionsFragment>(SuggestionsFragmentData("", SearchType.PLAYLIST));
            } else if (currentMain is LibraryFragment) {
                navigate<LibrarySearchFragment>();
            } else {
                navigate<SuggestionsFragment>(SuggestionsFragmentData("", SearchType.VIDEO));
            }
        };

        _buttonSearch = buttonSearch;

        return view;
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _buttonSearch?.setOnClickListener(null);
        _buttonSearch = null;
        _buttonCast?.cleanup();
        _buttonCast = null;
    }

    companion object {
        fun newInstance() = GeneralTopBarFragment().apply { }
    }
}