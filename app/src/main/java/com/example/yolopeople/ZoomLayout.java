package com.example.yolopeople;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;

public class ZoomLayout extends FrameLayout {

    private final Matrix drawMatrix = new Matrix();      // 缩放/平移矩阵
    private final Matrix inverseMatrix = new Matrix();   // 逆矩阵，用于反变换触摸坐标

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float currentScale = 1f;
    private float minScale = 1f;
    private float maxScale = 4f;

    // ★ 是否启用“缩放模式”
    private boolean zoomEnabled = false;

    public ZoomLayout(Context context) {
        this(context, null);
    }

    public ZoomLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initGesture(context);
        // 初始化一次逆矩阵
        updateInverseAndInvalidate();
    }

    private void initGesture(Context context) {
        // 双指缩放
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float factor = detector.getScaleFactor();
                        float newScale = currentScale * factor;

                        if (newScale < minScale) {
                            factor = minScale / currentScale;
                            newScale = minScale;
                        } else if (newScale > maxScale) {
                            factor = maxScale / currentScale;
                            newScale = maxScale;
                        }

                        currentScale = newScale;

                        // 以双指中心缩放
                        drawMatrix.postScale(
                                factor, factor,
                                detector.getFocusX(),
                                detector.getFocusY()
                        );
                        updateInverseAndInvalidate();
                        return true;
                    }
                });

        // 拖动（平移）
        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                            float distanceX, float distanceY) {
                        // 只有放大后才允许拖动
                        if (currentScale > 1f) {
                            drawMatrix.postTranslate(-distanceX, -distanceY);
                            updateInverseAndInvalidate();
                            return true;
                        }
                        return false;
                    }
                });

        setWillNotDraw(false);
    }

    private void updateInverseAndInvalidate() {
        drawMatrix.invert(inverseMatrix);
        invalidate();
    }

    /** 外部调用：重置缩放状态（换新图时可用） */
    public void resetZoom() {
        currentScale = 1f;
        drawMatrix.reset();
        updateInverseAndInvalidate();
    }

    /** ★ 外部调用：开启/关闭缩放模式 */
    public void setZoomEnabled(boolean enabled) {
        this.zoomEnabled = enabled;
    }

    public boolean isZoomEnabled() {
        return zoomEnabled;
    }

    /** 对子 View 统一应用当前缩放/平移 */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.concat(drawMatrix);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    /**
     * 触摸分发：
     * - 缩放模式 ON：只处理缩放/拖动手势，不把事件传给子 View（无法画框/删框）
     * - 缩放模式 OFF：不再改变矩阵，但会用 inverseMatrix 把事件反变换后给子 View，
     *   这样 DetectOverlayView 继续用“原图坐标系”工作
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (zoomEnabled) {
            // 只进行缩放/拖动，不传给子 View
            scaleDetector.onTouchEvent(ev);
            gestureDetector.onTouchEvent(ev);
            return true; // 事件在缩放层被消费
        } else {
            // 编辑模式：不再改变矩阵，但保持当前缩放状态，
            // 把事件用 inverseMatrix 反变换后传递给子 View
            MotionEvent transformed = MotionEvent.obtain(ev);
            transformed.transform(inverseMatrix);
            boolean handled = super.dispatchTouchEvent(transformed);
            transformed.recycle();
            return handled;
        }
    }
}

