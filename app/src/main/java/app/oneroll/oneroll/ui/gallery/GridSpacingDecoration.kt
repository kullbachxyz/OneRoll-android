package app.oneroll.oneroll.ui.gallery

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GridSpacingDecoration(
    private val spacing: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val layoutManager = parent.layoutManager as? GridLayoutManager
        val spanCount = layoutManager?.spanCount ?: 1
        val column = position % spanCount

        outRect.left = spacing / 2
        outRect.right = spacing / 2
        outRect.bottom = spacing
        outRect.top = if (position < spanCount) spacing else spacing / 2

        if (column == 0) outRect.left = spacing
        if (column == spanCount - 1) outRect.right = spacing
    }
}
