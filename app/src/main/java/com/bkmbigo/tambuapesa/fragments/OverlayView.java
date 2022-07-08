package com.bkmbigo.tambuapesa.fragments;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bkmbigo.tambuapesa.R;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class OverlayView extends View {
    private List<Detection> results = new LinkedList<Detection>();
    private Paint boxPaint = new Paint(),
            textBackgroundPaint = new Paint(),
            textPaint = new Paint();

    private float scaleFactor = 1f;

    public static final int BOUNDING_BOX_RECT_TEXT_PADDING = 8;

    private Rect bounds = new Rect();

    private Context context;

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    private void initPaints(){
        textBackgroundPaint.setColor(Color.BLACK);
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setTextSize(50f);

        textPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(50f);

        boxPaint.setColor(ContextCompat.getColor(context, R.color.bounding_box_color));
        boxPaint.setStrokeWidth(8F);
        boxPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for(Detection result: results){
            RectF boundingbox = result.getBoundingBox();

            float top = boundingbox.top * scaleFactor;
            float bottom = boundingbox.bottom * scaleFactor;
            float left = boundingbox.left * scaleFactor;
            float right = boundingbox.right * scaleFactor;

            RectF drawableRectF = new RectF(left, top, right, bottom);
            canvas.drawRect(drawableRectF, boxPaint);

            String drawableText = result.getCategories().get(0).getLabel() + " " +
                    String.format("%.2f", result.getCategories().get(0).getScore());

            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length(), bounds);

            float textWidth = bounds.width();
            float textHeight = bounds.height();

            canvas.drawRect(left, top,
                    left + textWidth + BOUNDING_BOX_RECT_TEXT_PADDING,
                    top + textHeight + BOUNDING_BOX_RECT_TEXT_PADDING,
                    textBackgroundPaint);

            canvas.drawText(drawableText, left, top + bounds.height(), textPaint);
        }
    }

    public void setResults(List<Detection> detectionResults, int imageHeight, int imageWidth){
        results = detectionResults;

        scaleFactor = Math.max(getWidth() * 1f / imageWidth, getHeight() * 1f / imageHeight);
    }

    public void clear(){
        textPaint.reset();
        textBackgroundPaint.reset();
        boxPaint.reset();
        invalidate();
        initPaints();
    }
}
