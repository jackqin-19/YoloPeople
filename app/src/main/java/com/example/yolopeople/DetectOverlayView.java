package com.example.yolopeople;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DetectOverlayView extends View {

    private final Paint boxPaint;
    private final Paint shadowPaint;
    private final Paint labelBgPaint;
    private final Paint labelTextPaint;

    // 当前图片的原始尺寸（PeopleDetector 返回的 box 基于这个）
    private int imageWidth = 0;
    private int imageHeight = 0;

    // 检测结果
    private List<PeopleDetector.Box> boxes = new ArrayList<>();

    // 一些 dp/sp
    private final float labelPaddingDp = 0.5f;
    private final float labelTextSizeSp = 5f;

    public DetectOverlayView(Context context) {
        this(context, null);
    }

    public DetectOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DetectOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;
        float scaledDensity = metrics.scaledDensity;

        // 线宽直接按 dp 算，跟屏幕相关，跟图片大小无关
        float boxStrokeWidthPx = 2f * density;   // 2dp
        float shadowStrokeWidthPx = 4f * density; // 4dp
        float labelPaddingPx = labelPaddingDp * density;
        float labelTextSizePx = labelTextSizeSp * scaledDensity;

        // 框主线
        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(boxStrokeWidthPx);
        boxPaint.setColor(Color.parseColor("#00FFFF"));

        // 外发光
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeWidth(shadowStrokeWidthPx);
        shadowPaint.setColor(Color.parseColor("#6000FFFF"));

        // 标签背景
        labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelBgPaint.setStyle(Paint.Style.FILL);
        labelBgPaint.setColor(Color.parseColor("#CC000000"));

        // 标签文字
        labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelTextPaint.setStyle(Paint.Style.FILL);
        labelTextPaint.setColor(Color.WHITE);
        labelTextPaint.setTextSize(labelTextSizePx);
        labelTextPaint.setFakeBoldText(true);
    }

    /** 由 Activity 调用，把结果和图片尺寸塞进来 */
    public void setResults(List<PeopleDetector.Box> boxes, int imageWidth, int imageHeight) {
        this.boxes = (boxes != null) ? boxes : new ArrayList<>();
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        invalidate(); // 触发重绘
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (boxes == null || boxes.isEmpty() || imageWidth == 0 || imageHeight == 0) {
            return;
        }

        float viewW = getWidth();
        float viewH = getHeight();

        // ImageView 用的是 fitCenter：保持比例缩放，居中
        float scale = Math.min(viewW / imageWidth, viewH / imageHeight);
        float dx = (viewW - imageWidth * scale) / 2f;
        float dy = (viewH - imageHeight * scale) / 2f;

        for (PeopleDetector.Box b : boxes) {
            // 把原图坐标映射到 View 坐标
            float left = b.left * scale + dx;
            float top = b.top * scale + dy;
            float right = b.right * scale + dx;
            float bottom = b.bottom * scale + dy;

            RectF rect = new RectF(left, top, right, bottom);

            // 先画阴影，再画主框
            canvas.drawRect(rect, shadowPaint);
            canvas.drawRect(rect, boxPaint);

            // 标签
            drawLabel(canvas, rect, b.confidence);
        }
    }

    private void drawLabel(Canvas canvas, RectF boxRect, float confidence) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;
        float labelPaddingPx = labelPaddingDp * density;

        String labelText = String.format("Person %.2f", confidence);
        Rect textBounds = new Rect();
        labelTextPaint.getTextBounds(labelText, 0, labelText.length(), textBounds);
        float textWidth = labelTextPaint.measureText(labelText);
        float textHeight = textBounds.height();

        float labelX = boxRect.left;
        float labelY = boxRect.top;
        float labelWidth = textWidth + labelPaddingPx * 2;
        float labelHeight = textHeight + labelPaddingPx * 2;

        RectF bg = new RectF(labelX, labelY, labelX + labelWidth, labelY + labelHeight);
        canvas.drawRect(bg, labelBgPaint);

        float textX = labelX + labelPaddingPx;
        float textY = labelY + labelHeight - labelPaddingPx - textBounds.bottom;
        canvas.drawText(labelText, textX, textY, labelTextPaint);
    }
}

