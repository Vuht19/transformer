package com.tohsoft.transformer.custom_view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import com.tohsoft.transformer.R;

public class SquareImageView extends AppCompatImageView {
    int squareMode; // width = 0, height = 1, xy = 2

    public SquareImageView(@NonNull Context context) {
        this(context, null);
    }

    public SquareImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SquareImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.SquareImageView, defStyleAttr, 0);
        squareMode = typedArray.getInt(R.styleable.SquareImageView_mode, 0);

        typedArray.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = widthMeasureSpec;
        if (squareMode == 1) {
            size = heightMeasureSpec;
        } else if (squareMode == 2) {
            size = Math.min(widthMeasureSpec, heightMeasureSpec);
        }
        super.onMeasure(size, size);
    }

}
