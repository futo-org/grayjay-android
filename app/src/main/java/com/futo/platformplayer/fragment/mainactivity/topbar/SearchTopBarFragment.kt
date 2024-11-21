package com.futo.platformplayer.fragment.mainactivity.topbar

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fragment.mainactivity.main.SuggestionsFragment
import com.futo.platformplayer.fragment.mainactivity.main.SuggestionsFragmentData
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.SearchType
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.SearchHistoryStorage

class SearchTopBarFragment : TopFragment() {
    @Suppress("PrivatePropertyName")
    private val TAG = "SearchTopBarFragment"

    private var _editSearch: EditText? = null;
    private var _buttonClearSearch: ImageButton? = null;
    private var _buttonFilter: ImageButton? = null;
    private var _buttonBack: ImageButton? = null;
    private var _inputMethodManager: InputMethodManager? = null;
    private var _shouldFocus = false;
    private var _searchType: SearchType = SearchType.VIDEO;
    private var _channelUrl: String? = null;

    private var _lastQuery = "";

    private val _textChangeListener = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = Unit
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val text = _editSearch?.text.toString();
            if (text.isBlank())
                _buttonClearSearch?.visibility = EditText.INVISIBLE;
            else
                _buttonClearSearch?.visibility = EditText.VISIBLE;
            onTextChange.emit(text);
        }
    };

    private val _searchDoneListener = object : TextView.OnEditorActionListener {
        override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
            val isEnterPress = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId != EditorInfo.IME_ACTION_DONE && !isEnterPress)
                return false

            onDone()
            return true
        }
    };

    val onFilterClick = Event0();
    val onSearch = Event1<String>();
    val onTextChange = Event1<String>();

    override fun onAttach(context: Context) {
        super.onAttach(context);
        _inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;
    }

    override fun onDetach() {
        super.onDetach();
        _inputMethodManager = null;
    }

    override fun onShown(parameter: Any?) {
        if (parameter is String) {
            this.setText(parameter);
            _channelUrl = null;
        } else if (parameter is SearchType) {
            _searchType = parameter;
            _channelUrl = null;
        } else if (parameter is SuggestionsFragmentData) {
            this.setText(parameter.query);
            _searchType = parameter.searchType;
            _channelUrl = parameter.channelUrl;
        }

        if(currentMain is SuggestionsFragment)
            this.focus();
        else
            this.clearFocus();
    }
    override fun onHide() {
        clearFocus();
    }

    fun focus() {
        val editSearch = _editSearch;
        val inputMethodManager = _inputMethodManager;
        if (editSearch != null && inputMethodManager != null) {
            _editSearch?.requestFocus();
            _inputMethodManager?.showSoftInput(_editSearch, 0);
            _shouldFocus = false;
        } else {
            _shouldFocus = true;
        }
    }
    fun clear() {
        _editSearch?.text?.clear();
        if (currentMain !is SuggestionsFragment) {
            navigate<SuggestionsFragment>(SuggestionsFragmentData("", _searchType, _channelUrl), false);
        } else {
            onSearch.emit("");
        }
    }
    fun clearFocus(){
        _editSearch?.clearFocus();
        _inputMethodManager?.hideSoftInputFromWindow(_editSearch?.windowToken, 0);
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_search_top_bar, container, false);

        val buttonClearSearch: ImageButton = view.findViewById(R.id.button_clear_search);
        val editSearch: EditText = view.findViewById(R.id.edit_search);
        val buttonBack: ImageButton = view.findViewById(R.id.button_back);
        _buttonFilter = view.findViewById(R.id.button_filter);

        editSearch.setOnEditorActionListener(_searchDoneListener);
        editSearch.addTextChangedListener(_textChangeListener);

        buttonClearSearch.setOnClickListener {
            clear();
            focus();
        }

        buttonBack.setOnClickListener {
            close();
        };

        _buttonFilter?.setOnClickListener {
            onFilterClick.emit();
        };
        setFilterButtonVisible(false);

        _buttonClearSearch = buttonClearSearch;
        _editSearch = editSearch;

        return view;
    }

    override fun onResume() {
        super.onResume();

        //TODO: Supposed to be in onCreateView, but EditText Lifecycle appears broken there.
        setText(_lastQuery);

        if (_shouldFocus) {
            focus();
        }
    }

    override fun onDestroyView() {
        super.onDestroyView();

        _buttonClearSearch?.setOnClickListener(null);
        _buttonClearSearch = null;
        _editSearch?.removeTextChangedListener(_textChangeListener);
        _editSearch?.setOnClickListener(null);
        _editSearch = null;
        _buttonBack?.setOnClickListener(null);
        _buttonBack = null;
        _buttonFilter?.setOnClickListener(null);
        _buttonFilter = null;
    }

    fun setText(text: String) {
        _lastQuery = text;
        val editSearch = _editSearch ?: return;
        editSearch.text.clear();
        editSearch.text.append(text);
    }

    fun setFilterButtonVisible(visible: Boolean) {
        _buttonFilter?.visibility = if (visible) View.VISIBLE else View.GONE;
    }

    private fun onDone() {
        val editSearch = _editSearch
        if (editSearch != null) {
            val text = editSearch.text.toString()
            if (text.isEmpty()) {
                UIDialogs.toast(getString(R.string.please_use_at_least_1_character))
                return
            }

            editSearch.clearFocus()
            _inputMethodManager?.hideSoftInputFromWindow(editSearch.windowToken, 0)

            if (Settings.instance.search.searchHistory) {
                val storage = FragmentedStorage.get<SearchHistoryStorage>()
                storage.add(text)
            }

            if (_searchType == SearchType.CREATOR) {
                onSearch.emit(text)
            } else {
                onSearch.emit(text)
            }
        } else {
            Logger.w(
                TAG,
                "Unexpected condition happened where done is edit search is null but done is triggered."
            )
        }
    }

    companion object {
        fun newInstance() = SearchTopBarFragment().apply { }
    }
}