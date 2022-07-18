package com.bkmbigo.tambuapesa;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.bkmbigo.tambuapesa.R;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class OverlayView extends View {
    private List<Detection> results = new LinkedList<Detection>();
    private Paint boxPaint = new Paint(),
            textBackgroundPaint = new Paint(),
            textPaint = new Paint();

    private float scaleFactor = 1f;

    public static final int BOUNDING_BOX_RECT_TEXT_PADDING = 8;

    private Rect bounds = new Rect();

    private Context context;

    private boolean isShowingTextOnly = false, isShowingConfidenceLevels = false;

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        isShowingTextOnly = !sharedPreferences.getString("display_mode", "1").equals("1");

        isShowingConfidenceLevels = !sharedPreferences.getString("show_confidence", "1").equals("1");
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
            RectF boundingBox = result.getBoundingBox();

            float top = Math.max(boundingBox.top * scaleFactor, 0f);
            float bottom = boundingBox.bottom * scaleFactor;
            float left = Math.max(boundingBox.left * scaleFactor, 0f);
            float right = boundingBox.right * scaleFactor;

            String drawableText;

            if(isShowingConfidenceLevels){
                drawableText = result.getCategories().get(0).getLabel() + " " +
                        String.format(Locale.getDefault(),"%.2f", result.getCategories().get(0).getScore());
            }else{
                drawableText = result.getCategories().get(0).getLabel();
            }

            if(!isShowingTextOnly){
                RectF drawableRectF = new RectF(left, top, right, bottom);
                canvas.drawRect(drawableRectF, boxPaint);

                textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length(), bounds);

                float textWidth = bounds.width();
                float textHeight = bounds.height();

                canvas.drawRect(left, top,
                        left + textWidth + BOUNDING_BOX_RECT_TEXT_PADDING,
                        top + textHeight + BOUNDING_BOX_RECT_TEXT_PADDING,
                        textBackgroundPaint);

                canvas.drawText(drawableText, left, top + bounds.height(), textPaint);
            }else{
                canvas.drawText(drawableText, (left + right) / 2, (top + bottom) / 2,textPaint);
            }
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
