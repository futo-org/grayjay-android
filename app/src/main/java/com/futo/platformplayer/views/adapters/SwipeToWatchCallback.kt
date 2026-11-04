package com.futo.platformplayer.views.adapters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewParent
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.views.adapters.feedtypes.PreviewVideoViewHolder

/**
 * An [ItemTouchHelper.SimpleCallback] that only allows swiping video preview rows
 * (rows backed by a [PreviewVideoViewHolder]). Headers, loaders and other content
 * types can not be swiped.
 *
 * The allowed swipe direction is resolved lazily through [swipeDirProvider] so that
 * changing the direction in the settings is picked up without recreating the feed.
 *
 * [onSwipe] is invoked when a row was swiped. When it returns false the row is
 * animated back into place instead of being removed.
 */
class SwipeToWatchCallback(
    context: Context,
    private val swipeDirProvider: () -> Int
) : ItemTouchHelper.SimpleCallback(0, 0) {
    var onSwipe: ((viewHolder: RecyclerView.ViewHolder, direction: Int) -> Boolean)? = null;

    private val _background: Drawable = ColorDrawable(Color.parseColor("#2E7D32"));
    private val _icon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_history);

    //Ancestors that had their touch interception disabled while a swipe is active.
    private val _interceptDisallowedParents = arrayListOf<ViewParent>();

    override fun isLongPressDragEnabled(): Boolean = false;
    override fun isItemViewSwipeEnabled(): Boolean = true;

    /**
     * Keeps every ancestor of the list from intercepting the touch for as long as a swipe is
     * active.
     *
     * [ItemTouchHelper] only asks the RecyclerView's direct parent to disallow interception, and
     * that request is silently dropped by any ancestor that already had interception disallowed
     * (the framework then assumes the ancestors above it already know as well). That allows a parent
     * container - such as the pull-to-refresh layout wrapping the feed - to still intercept the
     * gesture halfway through a swipe, which makes the row snap back while the finger is still on
     * the screen.
     */
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if(actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
            for(parent in _interceptDisallowedParents)
                parent.requestDisallowInterceptTouchEvent(false);
            _interceptDisallowedParents.clear();
        } else {
            _interceptDisallowedParents.clear();
            //Start at the RecyclerView's parent; calling it on the RecyclerView itself would make
            //ItemTouchHelper cancel its own swipe through onRequestDisallowInterceptTouchEvent.
            var parent: ViewParent? = (viewHolder?.itemView?.parent as? View)?.parent;
            while(parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
                _interceptDisallowedParents.add(parent);
                parent = (parent as? View)?.parent;
            }
        }
        super.onSelectedChanged(viewHolder, actionState);
    }

    //Default is 0.5 (half the RecyclerView width), which makes a row only dismiss after dragging
    //more than half the screen. 0.25 makes a deliberate but much shorter swipe commit.
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.25f;

    override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int = 0;

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val swipeDirs = swipeDirProvider();
        if (swipeDirs == 0)
            return 0;

        val insertedViewHolder = viewHolder as? InsertedViewHolder<*> ?: return 0;
        if (insertedViewHolder.childViewHolder !is PreviewVideoViewHolder)
            return 0;

        return swipeDirs;
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false;

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val consumed = onSwipe?.invoke(viewHolder, direction) ?: false;
        if (!consumed) {
            // Nothing was done with this row, snap it back into place.
            val position = viewHolder.absoluteAdapterPosition;
            if (position != RecyclerView.NO_POSITION)
                (viewHolder.itemView.parent as? RecyclerView)?.adapter?.notifyItemChanged(position);
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView;
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
            _background.setBounds(
                (if (dX > 0) itemView.left else itemView.right + dX).toInt(),
                itemView.top,
                (if (dX > 0) itemView.left + dX else itemView.right).toInt(),
                itemView.bottom
            );
            _background.draw(c);

            _icon?.let { icon ->
                val iconHeight = icon.intrinsicHeight;
                val iconWidth = icon.intrinsicWidth;
                val iconMargin = (itemView.height - iconHeight) / 2;
                val iconTop = itemView.top + (itemView.height - iconHeight) / 2;
                val iconLeft: Int;
                val iconRight: Int;
                if (dX > 0) {
                    iconLeft = itemView.left + iconMargin;
                    iconRight = iconLeft + iconWidth;
                } else {
                    iconLeft = itemView.right - iconMargin - iconWidth;
                    iconRight = itemView.right - iconMargin;
                }
                icon.setBounds(iconLeft, iconTop, iconRight, iconTop + iconHeight);
                icon.draw(c);
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}
