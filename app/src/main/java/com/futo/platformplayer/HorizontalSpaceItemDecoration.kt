package com.futo.platformplayer

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class HorizontalSpaceItemDecoration(private val startSpace: Int, private val betweenSpace: Int, private val endSpace: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.left = betweenSpace

        val position = parent.getChildAdapterPosition(view)
        if (position == 0) {
            outRect.left = startSpace
        }

        else if (position == state.itemCount - 1) {
            outRect.right = endSpace
        }
    }
}