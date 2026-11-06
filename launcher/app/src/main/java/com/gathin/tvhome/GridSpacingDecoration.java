package com.gathin.tvhome;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * ItemDecoration for grid spacing with consistent gaps.
 */
public class GridSpacingDecoration extends RecyclerView.ItemDecoration {

    private final int columns;
    private final float density;
    private final int spacing;

    GridSpacingDecoration(int columns, float density) {
        this.columns = columns;
        this.density = density;
        this.spacing = (int) (16 * density); // 16dp gap
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position < 0) return;

        int column = position % columns;
        int row = position / columns;

        // Horizontal spacing
        if (column == 0) {
            outRect.left = 0;
        } else {
            outRect.left = spacing / 2;
        }
        if (column == columns - 1) {
            outRect.right = 0;
        } else {
            outRect.right = spacing / 2;
        }

        // Vertical spacing
        if (row == 0) {
            outRect.top = 0;
        } else {
            outRect.top = spacing / 2;
        }
        outRect.bottom = spacing / 2;
    }
}