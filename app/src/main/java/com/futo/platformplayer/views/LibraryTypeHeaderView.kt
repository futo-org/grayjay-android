package com.futo.platformplayer.views

import android.content.Context
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1

class LibraryTypeHeaderView: ConstraintLayout {

    var selected: SelectedType = SelectedType.Artists;

    val pillArtist: PillV2;
    val pillAlbums: PillV2;
    val textMetadata: TextView;
    val pills: List<PillV2>

    val onSelectedChanged = Event1<SelectedType>();

    constructor(context: Context) : super(context) {
        inflate(context, R.layout.view_library_type_header, this)

        textMetadata = findViewById(R.id.text_metadata);
        pillArtist = findViewById(R.id.pill_artist);
        pillAlbums = findViewById(R.id.pill_albums);

        pillArtist.onClick.subscribe {
            setSelectedType(SelectedType.Artists, true);
        }
        pillAlbums.onClick.subscribe {
            setSelectedType(SelectedType.Albums, true);
        }

        pills = listOf(pillArtist, pillAlbums);

        setSelectedType(SelectedType.Artists, false);
    }

    fun setMetadata(str: String) {
        textMetadata.text = str;
    }

    fun setSelectedType(selected: SelectedType, notify: Boolean = false){
        this.selected = selected;

        pills.forEach { it.setIsEnabled(false) };

        when(selected) {
            SelectedType.Artists -> {
                pillArtist.setIsEnabled(true);
            }
            SelectedType.Albums -> {
                pillAlbums.setIsEnabled(true);
            }
        }

        if(notify)
            onSelectedChanged.emit(selected);
    }



    enum class SelectedType {
        Artists,
        Albums
    }
}