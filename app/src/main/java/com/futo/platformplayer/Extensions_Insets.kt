package com.futo.platformplayer

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.math.max

fun View.applyBottomOverlayInsets(types: Int) {
    var lastInsets: WindowInsetsCompat? = null;

    fun apply() {
        val insets = lastInsets ?: ViewCompat.getRootWindowInsets(this) ?: return;
        val inset = insets.getInsets(types).bottom;
        val location = IntArray(2);
        getLocationInWindow(location);
        val below = rootView.height - (location[1] + height);
        updatePadding(bottom = max(inset - below, 0));
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        lastInsets = insets;
        apply();
        insets;
    }

    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() };
}
