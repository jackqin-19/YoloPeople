package com.example.yolopeople;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ImageView ivResult;
    private Button btnRun;

    private PeopleDetector peopleDetector;

    // 绘制相关的 Paint 对象（作为成员变量复用，避免频繁创建）
    private Paint boxPaint;           // 检测框主线条
    private Paint shadowPaint;       // 发光阴影层
    private Paint labelBgPaint;      // 标签背景
    private Paint labelTextPaint;    // 标签文字

    // dp/sp 换算后的像素值（在初始化时计算一次）
    private float boxStrokeWidthDp = 5f;      // 框线宽度 5dp
    private float shadowRadiusDp = 8f;        // 阴影半径 8dp
    private float labelPaddingDp = 6f;        // 标签内边距 6dp
    private float labelTextSizeSp = 12f;      // 标签文字大小 12sp

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivResult = findViewById(R.id.iv_result);
        btnRun = findViewById(R.id.btn_run_detector);

        // 初始化检测器
        peopleDetector = new PeopleDetector(getApplicationContext());

        // 初始化绘制相关的 Paint 对象
        initPaintObjects();

        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runDetectionOnTestImage();
            }
        });
    }

    /**
     * 初始化所有 Paint 对象，只调用一次（在 onCreate 中）
     * 使用 dp/sp 换算，适配不同分辨率
     */
    private void initPaintObjects() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;
        float scaledDensity = metrics.scaledDensity;

        // 将 dp 转换为 px
        float boxStrokeWidthPx = boxStrokeWidthDp * density;
        float shadowRadiusPx = shadowRadiusDp * density;
        float labelPaddingPx = labelPaddingDp * density;
        float labelTextSizePx = labelTextSizeSp * scaledDensity;

        // 1. 检测框主线条 Paint（青色）
        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(boxStrokeWidthPx);
        boxPaint.setColor(Color.parseColor("#00FFFF")); // 青色（Cyan）

        // 2. 阴影层 Paint（用于绘制半透明外框，增强发光感）
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeWidth(boxStrokeWidthPx * 2.0f); // 更粗，形成外发光效果
        shadowPaint.setColor(Color.parseColor("#6000FFFF")); // 半透明青色，形成发光边缘

        // 3. 标签背景 Paint（黑色半透明）
        labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelBgPaint.setStyle(Paint.Style.FILL);
        labelBgPaint.setColor(Color.parseColor("#CC000000")); // 80% 透明度的黑色

        // 4. 标签文字 Paint（白色）
        labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelTextPaint.setStyle(Paint.Style.FILL);
        labelTextPaint.setColor(Color.WHITE);
        labelTextPaint.setTextSize(labelTextSizePx);
        labelTextPaint.setFakeBoldText(true); // 加粗文字，更醒目
    }

    private void runDetectionOnTestImage() {
        new Thread(() -> {
            // 1. 加载 Bitmap（注意：不要在子线程直接操作 UI）
            Bitmap origin = null;
            
            // 先尝试从 drawable 加载
            try {
                int resId = getResources().getIdentifier("classroom", "drawable", getPackageName());
                if (resId != 0) {
                    origin = BitmapFactory.decodeResource(getResources(), resId);
                }
            } catch (Exception e) {
                Log.d("MainActivity", "无法从 drawable 加载图片: " + e.getMessage());
            }
            
            // 如果 drawable 中没有，尝试从 assets 加载
            if (origin == null) {
                try {
                    origin = BitmapFactory.decodeStream(getAssets().open("classroom.jpg"));
                } catch (Exception e) {
                    try {
                        origin = BitmapFactory.decodeStream(getAssets().open("classroom.png"));
                    } catch (Exception e2) {
                        Log.d("MainActivity", "无法从 assets 加载图片: " + e2.getMessage());
                    }
                }
            }
            
            if (origin == null) {
                runOnUiThread(() -> 
                    Toast.makeText(this, "无法加载测试图片\n请添加 classroom.jpg 到 drawable 或 assets 目录", Toast.LENGTH_LONG).show()
                );
                return;
            }

            // 2. 检测
            List<PeopleDetector.Box> boxes = peopleDetector.detectPeople(origin);
            Log.d("MainActivity", "YOLO 检测框数量 = " + boxes.size());

            // 3. 画框（美化版）
            Bitmap mutable = origin.copy(Bitmap.Config.ARGB_8888, true);
            origin.recycle();
            
            Canvas canvas = new Canvas(mutable);
            
            // 绘制所有检测框
            for (PeopleDetector.Box b : boxes) {
                Log.d("MainActivity", "box = (" +
                        b.left + "," + b.top + "," + b.right + "," + b.bottom + "), conf=" + b.confidence);
                
                RectF rect = new RectF(b.left, b.top, b.right, b.bottom);
                
                // 绘制检测框（带发光效果）
                drawDetectionBox(canvas, rect);
                
                // 绘制标签和置信度
                drawLabel(canvas, rect, b.confidence);
            }

            // 4. 回到主线程更新 UI
            runOnUiThread(() -> {
                ivResult.setImageBitmap(mutable);
                Toast.makeText(this, "检测到人数：" + boxes.size(), Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    /**
     * 绘制美化的检测框（带发光效果）
     * @param canvas 画布
     * @param rect 检测框矩形
     */
    private void drawDetectionBox(Canvas canvas, RectF rect) {
        // 先画一层半透明外框（形成发光边缘效果）
        canvas.drawRect(rect, shadowPaint);
        
        // 再画主框（醒目的青色实线）
        canvas.drawRect(rect, boxPaint);
    }

    /**
     * 在检测框左上角绘制标签和置信度
     * @param canvas 画布
     * @param boxRect 检测框矩形
     * @param confidence 置信度（0-1）
     */
    private void drawLabel(Canvas canvas, RectF boxRect, float confidence) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float density = metrics.density;
        float labelPaddingPx = labelPaddingDp * density;

        // 格式化置信度文本（保留两位小数）
        String labelText = String.format("Person %.2f", confidence);

        // 测量文字尺寸
        Rect textBounds = new Rect();
        labelTextPaint.getTextBounds(labelText, 0, labelText.length(), textBounds);
        float textWidth = labelTextPaint.measureText(labelText);
        float textHeight = textBounds.height();

        // 计算标签背景矩形的位置和大小
        float labelX = boxRect.left;
        float labelY = boxRect.top;
        float labelWidth = textWidth + labelPaddingPx * 2;
        float labelHeight = textHeight + labelPaddingPx * 2;

        // 绘制半透明背景
        RectF labelBgRect = new RectF(
                labelX,
                labelY,
                labelX + labelWidth,
                labelY + labelHeight
        );
        canvas.drawRect(labelBgRect, labelBgPaint);

        // 绘制文字（垂直居中，左边距 padding）
        float textX = labelX + labelPaddingPx;
        float textY = labelY + labelHeight - labelPaddingPx - textBounds.bottom; // 基线位置
        canvas.drawText(labelText, textX, textY, labelTextPaint);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (peopleDetector != null) {
            peopleDetector.close();
        }
    }
}

