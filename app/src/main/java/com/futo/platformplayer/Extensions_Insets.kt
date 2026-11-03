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
        // Hidden overlays can retain their previous bounds during a window resize.
        val below = max(rootView.height - (location[1] + height), 0);
        updatePadding(bottom = max(inset - below, 0));
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        lastInsets = insets;
        apply();
        insets;
    }

    // Padding changes must request measurement after the current layout has finished.
    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> post { apply() } };
}
