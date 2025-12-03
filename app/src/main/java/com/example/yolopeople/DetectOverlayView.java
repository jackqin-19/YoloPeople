package com.example.yolopeople;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DetectOverlayView extends View {

    // 模型框画笔（青色）
    private final Paint boxPaint;
    private final Paint shadowPaint;
    private final Paint labelBgPaint;
    private final Paint labelTextPaint;

    // 手动画框画笔（绿色）
    private final Paint manualBoxPaint;
    private final Paint manualShadowPaint;

    // 当前图片的原始尺寸（PeopleDetector 返回的 box 基于这个）
    private int imageWidth = 0;
    private int imageHeight = 0;

    // 模型检测结果（可删除）
    private List<PeopleDetector.Box> boxes = new ArrayList<>();
    // 模型框备份（用于恢复）
    private List<PeopleDetector.Box> originalBoxes = new ArrayList<>();

    // 手动画框列表（存储为原图坐标）
    private List<RectF> manualBoxes = new ArrayList<>();

    // 监听器
    public interface OverlayListener {
        void onRequestDeleteBox(boolean isManual, int index);
        void onManualBoxAdded();
    }
    private OverlayListener overlayListener;

    // 触摸相关
    private float startX, startY;
    private boolean isDrawing = false;
    private RectF currentDrawingBox = null;

    // 长按检测
    private static final long LONG_PRESS_TIME = 500; // 500ms
    private long pressStartTime = 0;
    private boolean isLongPress = false;
    private Runnable longPressRunnable;

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

        // 手动画框画笔（绿色）
        manualBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        manualBoxPaint.setStyle(Paint.Style.STROKE);
        manualBoxPaint.setStrokeWidth(boxStrokeWidthPx);
        manualBoxPaint.setColor(Color.parseColor("#00FF00")); // 绿色

        manualShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        manualShadowPaint.setStyle(Paint.Style.STROKE);
        manualShadowPaint.setStrokeWidth(shadowStrokeWidthPx);
        manualShadowPaint.setColor(Color.parseColor("#6000FF00")); // 半透明绿色
    }

    public void setOverlayListener(OverlayListener listener) {
        this.overlayListener = listener;
    }

    /** 由 Activity 调用，把结果和图片尺寸塞进来 */
    public void setResults(List<PeopleDetector.Box> boxes, int imageWidth, int imageHeight) {
        this.boxes = (boxes != null) ? new ArrayList<>(boxes) : new ArrayList<>();
        this.originalBoxes = (boxes != null) ? new ArrayList<>(boxes) : new ArrayList<>();
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.manualBoxes.clear(); // 清除之前的手动画框
        invalidate(); // 触发重绘
    }

    /**
     * 从外部传入完整状态（再次编辑时恢复用）
     */
    public void setResultsWithState(List<PeopleDetector.Box> currentBoxes,
                                    List<PeopleDetector.Box> originalBoxes,
                                    List<RectF> manualBoxes,
                                    int imageWidth,
                                    int imageHeight) {
        this.boxes = (currentBoxes != null) ? new ArrayList<>(currentBoxes) : new ArrayList<>();
        this.originalBoxes = (originalBoxes != null) ? new ArrayList<>(originalBoxes) : new ArrayList<>();
        this.manualBoxes = (manualBoxes != null) ? new ArrayList<>(manualBoxes) : new ArrayList<>();

        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;

        invalidate();
    }

    /** 清除所有手动画框 */
    public void clearManualBoxes() {
        manualBoxes.clear();
        invalidate();
    }

    /** 恢复所有模型框 */
    public void restoreModelBoxes() {
        boxes = new ArrayList<>(originalBoxes);
        invalidate();
    }

    /** 删除一个框 */
    public void deleteBox(boolean isManual, int index) {
        if (isManual) {
            if (index >= 0 && index < manualBoxes.size()) {
                manualBoxes.remove(index);
            }
        } else {
            if (index >= 0 && index < boxes.size()) {
                boxes.remove(index);
            }
        }
        invalidate();
    }

    /** 获取当前模型框数量 */
    public int getModelBoxCount() {
        return boxes.size();
    }

    /** 获取手动画框数量 */
    public int getManualBoxCount() {
        return manualBoxes.size();
    }

    /** 当前图片原始宽 */
    public int getImageWidth() {
        return imageWidth;
    }

    /** 当前图片原始高 */
    public int getImageHeight() {
        return imageHeight;
    }

    /** 拿到当前"模型框"的一份拷贝 */
    public ArrayList<PeopleDetector.Box> getCurrentModelBoxesCopy() {
        ArrayList<PeopleDetector.Box> list = new ArrayList<>();
        for (PeopleDetector.Box b : boxes) {
            list.add(new PeopleDetector.Box(b));
        }
        return list;
    }

    /** 拿到"初始模型框"一份拷贝（用于恢复按钮） */
    public ArrayList<PeopleDetector.Box> getOriginalModelBoxesCopy() {
        ArrayList<PeopleDetector.Box> list = new ArrayList<>();
        for (PeopleDetector.Box b : originalBoxes) {
            list.add(new PeopleDetector.Box(b));
        }
        return list;
    }

    /** 拿到当前手动画框列表的一份拷贝 */
    public ArrayList<RectF> getManualBoxesCopy() {
        ArrayList<RectF> list = new ArrayList<>();
        for (RectF r : manualBoxes) {
            list.add(new RectF(r));
        }
        return list;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (imageWidth == 0 || imageHeight == 0) {
            return;
        }

        float viewW = getWidth();
        float viewH = getHeight();

        // ImageView 用的是 fitCenter：保持比例缩放，居中
        float scale = Math.min(viewW / imageWidth, viewH / imageHeight);
        float dx = (viewW - imageWidth * scale) / 2f;
        float dy = (viewH - imageHeight * scale) / 2f;

        // 先画模型框（青色）
        if (boxes != null && !boxes.isEmpty()) {
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

        // 再画手动画框（绿色）
        if (manualBoxes != null && !manualBoxes.isEmpty()) {
            for (RectF manualBox : manualBoxes) {
                // 手动画框已经是原图坐标，需要映射到 View 坐标
                float left = manualBox.left * scale + dx;
                float top = manualBox.top * scale + dy;
                float right = manualBox.right * scale + dx;
                float bottom = manualBox.bottom * scale + dy;

                RectF rect = new RectF(left, top, right, bottom);

                canvas.drawRect(rect, manualShadowPaint);
                canvas.drawRect(rect, manualBoxPaint);
            }
        }

        // 正在绘制的框
        if (currentDrawingBox != null) {
            float left = currentDrawingBox.left * scale + dx;
            float top = currentDrawingBox.top * scale + dy;
            float right = currentDrawingBox.right * scale + dx;
            float bottom = currentDrawingBox.bottom * scale + dy;

            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRect(rect, manualShadowPaint);
            canvas.drawRect(rect, manualBoxPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (imageWidth == 0 || imageHeight == 0) {
            return false;
        }

        float viewW = getWidth();
        float viewH = getHeight();
        float scale = Math.min(viewW / imageWidth, viewH / imageHeight);
        float dx = (viewW - imageWidth * scale) / 2f;
        float dy = (viewH - imageHeight * scale) / 2f;

        float x = event.getX();
        float y = event.getY();

        // 将 View 坐标转换为原图坐标
        float imageX = (x - dx) / scale;
        float imageY = (y - dy) / scale;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = imageX;
                startY = imageY;
                isDrawing = false;
                isLongPress = false;
                pressStartTime = System.currentTimeMillis();

                // 取消之前的 long press 检测
                if (longPressRunnable != null) {
                    removeCallbacks(longPressRunnable);
                }

                // 保存坐标用于长按检测
                final float checkX = imageX;
                final float checkY = imageY;

                // 检查是否点击在已有框上（用于长按删除）
                longPressRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (!isDrawing && !isLongPress && overlayListener != null) {
                            // 检查模型框
                            for (int i = 0; i < boxes.size(); i++) {
                                PeopleDetector.Box b = boxes.get(i);
                                if (checkX >= b.left && checkX <= b.right &&
                                    checkY >= b.top && checkY <= b.bottom) {
                                    isLongPress = true;
                                    overlayListener.onRequestDeleteBox(false, i);
                                    return;
                                }
                            }
                            // 检查手动画框
                            for (int i = 0; i < manualBoxes.size(); i++) {
                                RectF mb = manualBoxes.get(i);
                                if (checkX >= mb.left && checkX <= mb.right &&
                                    checkY >= mb.top && checkY <= mb.bottom) {
                                    isLongPress = true;
                                    overlayListener.onRequestDeleteBox(true, i);
                                    return;
                                }
                            }
                        }
                    }
                };
                postDelayed(longPressRunnable, LONG_PRESS_TIME);

                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaX = Math.abs(imageX - startX);
                float deltaY = Math.abs(imageY - startY);
                if (deltaX > 10 || deltaY > 10) { // 移动超过阈值，开始绘制
                    isDrawing = true;
                    currentDrawingBox = new RectF(
                        Math.min(startX, imageX),
                        Math.min(startY, imageY),
                        Math.max(startX, imageX),
                        Math.max(startY, imageY)
                    );
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 取消长按检测
                if (longPressRunnable != null) {
                    removeCallbacks(longPressRunnable);
                    longPressRunnable = null;
                }

                if (isDrawing && currentDrawingBox != null) {
                    // 确保框在图片范围内
                    currentDrawingBox.left = Math.max(0, Math.min(currentDrawingBox.left, imageWidth));
                    currentDrawingBox.top = Math.max(0, Math.min(currentDrawingBox.top, imageHeight));
                    currentDrawingBox.right = Math.max(0, Math.min(currentDrawingBox.right, imageWidth));
                    currentDrawingBox.bottom = Math.max(0, Math.min(currentDrawingBox.bottom, imageHeight));

                    // 确保框有最小尺寸
                    if (currentDrawingBox.width() > 20 && currentDrawingBox.height() > 20) {
                        manualBoxes.add(currentDrawingBox);
                        if (overlayListener != null) {
                            overlayListener.onManualBoxAdded();
                        }
                    }
                    currentDrawingBox = null;
                    invalidate();
                }
                isDrawing = false;
                return true;
        }

        return super.onTouchEvent(event);
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

