package com.futo.platformplayer.views.adapters

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2

class ItemMoveCallback : ItemTouchHelper.Callback {
    var onRowMoved = Event2<Int, Int>();
    var onRowSelected = Event1<ViewHolder>();
    var onRowClear = Event1<ViewHolder>();

    constructor() : super() { }

    override fun isLongPressDragEnabled(): Boolean { return true; }
    override fun isItemViewSwipeEnabled(): Boolean { return false; }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN;
        return makeMovementFlags(dragFlags, 0);
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder): Boolean {
        onRowMoved.emit(viewHolder.absoluteAdapterPosition, target.absoluteAdapterPosition);
        return true;
    }

    override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder != null) {
                onRowSelected.emit(viewHolder);
            }
        }

        super.onSelectedChanged(viewHolder, actionState);
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
        super.clearView(recyclerView, viewHolder);
        onRowClear.emit(viewHolder);
    }

    override fun onSwiped(viewHolder: ViewHolder, direction: Int) {

    }
}
