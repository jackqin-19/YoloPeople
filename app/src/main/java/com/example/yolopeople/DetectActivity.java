package com.example.yolopeople;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.List;

public class DetectActivity extends AppCompatActivity {

    private static final String TAG = "DetectActivity";

    public static final String EXTRA_IMAGE_URI = "image_uri";

    private ImageView ivResult;
    private DetectOverlayView overlayView;
    private TextView tvModelCount, tvManualCount, tvDeletedCount, tvTotalCount;
    private Button btnClearManual, btnRestoreModel, btnBack;

    private PeopleDetector peopleDetector;
    private int originalModelCount = 0;   // 初始模型框数量（用来算删了多少）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);

        ivResult        = findViewById(R.id.iv_result);
        overlayView     = findViewById(R.id.detect_overlay);
        tvModelCount    = findViewById(R.id.tv_model_count);
        tvManualCount   = findViewById(R.id.tv_manual_count);
        tvDeletedCount  = findViewById(R.id.tv_deleted_count);
        tvTotalCount    = findViewById(R.id.tv_total_count);
        btnClearManual  = findViewById(R.id.btn_clear_manual);
        btnRestoreModel = findViewById(R.id.btn_restore_model);
        btnBack         = findViewById(R.id.btn_back);

        peopleDetector = new PeopleDetector(getApplicationContext());

        // 监听 Overlay 的操作：长按删框 & 新增手动画框
        overlayView.setOverlayListener(new DetectOverlayView.OverlayListener() {
            @Override
            public void onRequestDeleteBox(boolean isManual, int index) {
                showDeleteDialog(isManual, index);
            }

            @Override
            public void onManualBoxAdded() {
                updateCounts();
            }
        });

        btnClearManual.setOnClickListener(v -> {
            overlayView.clearManualBoxes();
            updateCounts();
        });

        btnRestoreModel.setOnClickListener(v -> {
            overlayView.restoreModelBoxes();
            updateCounts();
        });

        btnBack.setOnClickListener(v -> finish());

        // 从 Intent 拿到图片 Uri
        String uriStr = getIntent().getStringExtra(EXTRA_IMAGE_URI);
        if (uriStr == null) {
            Toast.makeText(this, "没有收到图片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Uri imageUri = Uri.parse(uriStr);
        loadAndDetect(imageUri);
    }

    /** 加载图片并做检测 */
    private void loadAndDetect(Uri uri) {
        new Thread(() -> {
            try {
                ContentResolver cr = getContentResolver();
                InputStream is = cr.openInputStream(uri);
                if (is == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();

                if (bitmap == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "图片解码失败", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // 跑 YOLO
                List<PeopleDetector.Box> boxes = peopleDetector.detectPeople(bitmap);
                originalModelCount = boxes.size();
                Log.d(TAG, "YOLO 检测框数量 = " + boxes.size());

                runOnUiThread(() -> {
                    ivResult.setImageBitmap(bitmap);
                    overlayView.setResults(boxes, bitmap.getWidth(), bitmap.getHeight());
                    updateCounts();
                    Toast.makeText(this, "检测到人数：" + boxes.size(), Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    /** 更新人数统计 */
    private void updateCounts() {
        int currentModel = overlayView.getModelBoxCount();
        int manual = overlayView.getManualBoxCount();
        int deletedModel = originalModelCount - currentModel;
        if (deletedModel < 0) deletedModel = 0;
        int total = currentModel + manual;

        tvModelCount.setText("模型识别: " + currentModel);
        tvManualCount.setText("手动画框: " + manual);
        tvDeletedCount.setText("删除模型框: " + deletedModel);
        tvTotalCount.setText("总人数: " + total);
    }

    /** 删除框对话框 */
    private void showDeleteDialog(boolean isManual, int index) {
        String typeText = isManual ? "手动画框" : "模型识别框";
        new AlertDialog.Builder(this)
                .setTitle("删除人物框")
                .setMessage("确定要删除这个" + typeText + "吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    overlayView.deleteBox(isManual, index);
                    updateCounts();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (peopleDetector != null) {
            peopleDetector.close();
        }
    }
}

