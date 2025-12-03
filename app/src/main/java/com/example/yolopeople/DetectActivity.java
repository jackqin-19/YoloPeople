package com.example.yolopeople;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DetectActivity extends AppCompatActivity {

    private static final String TAG = "DetectActivity";

    public static final String EXTRA_IMAGE_URI = "image_uri";
    public static final String EXTRA_RESULT_TOTAL_COUNT = "result_total_count";
    public static final String EXTRA_DELETE_PHOTO = "delete_photo";

    // ★ 新增：状态传递用
    public static final String EXTRA_BOXES_MODEL = "boxes_model";
    public static final String EXTRA_BOXES_MODEL_ORIG = "boxes_model_orig";
    public static final String EXTRA_BOXES_MANUAL = "boxes_manual";
    public static final String EXTRA_IMAGE_WIDTH = "image_width";
    public static final String EXTRA_IMAGE_HEIGHT = "image_height";

    private ImageView ivResult;
    private DetectOverlayView overlayView;
    private ZoomLayout zoomContainer;
    private TextView tvModelCount, tvManualCount, tvDeletedCount, tvTotalCount;
    private TextView tvConfLabel;
    private SeekBar seekConf;
    private Button btnClearManual, btnRestoreModel, btnBack, btnConfirm, btnZoomMode, btnDeletePhoto;

    private PeopleDetector peopleDetector;
    private int originalModelCount = 0;   // 初始模型框数量（用来算删了多少）

    private String imageUriStr;           // 当前图片的 Uri 字符串
    private boolean isZoomMode = false;

    // 置信度阈值 & 原图缓存（用于重新检测）
    private float confThreshold = PeopleDetector.DEFAULT_CONF_THRESHOLD;
    private Bitmap originalBitmap;

    // 是否是“再次编辑”进来的，用于决定改阈值时是否弹确认
    private boolean hasInitialStateFromOutside = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);

        zoomContainer   = findViewById(R.id.zoom_container);
        ivResult        = findViewById(R.id.iv_result);
        overlayView     = findViewById(R.id.detect_overlay);
        tvModelCount    = findViewById(R.id.tv_model_count);
        tvManualCount   = findViewById(R.id.tv_manual_count);
        tvDeletedCount  = findViewById(R.id.tv_deleted_count);
        tvTotalCount    = findViewById(R.id.tv_total_count);
        tvConfLabel     = findViewById(R.id.tv_conf_label);
        seekConf        = findViewById(R.id.seek_conf);
        btnClearManual  = findViewById(R.id.btn_clear_manual);
        btnRestoreModel = findViewById(R.id.btn_restore_model);
        btnBack         = findViewById(R.id.btn_back);
        btnConfirm      = findViewById(R.id.btn_confirm);
        btnZoomMode     = findViewById(R.id.btn_zoom_mode);
        btnDeletePhoto  = findViewById(R.id.btn_delete_photo);

        updateZoomModeUI(false);

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

        // 缩放模式切换
        btnZoomMode.setOnClickListener(v -> {
            isZoomMode = !isZoomMode;
            updateZoomModeUI(true);
        });

        // 返回按钮：保存并返回结果
        btnBack.setOnClickListener(v -> finishWithResult());

        // 确认按钮：回传统计结果（保留兼容性，功能与返回按钮相同）
        btnConfirm.setOnClickListener(v -> finishWithResult());

        btnDeletePhoto.setOnClickListener(v -> showDeletePhotoDialog());

        // 从 Intent 拿到图片 Uri
        imageUriStr = getIntent().getStringExtra(EXTRA_IMAGE_URI);
        if (imageUriStr == null) {
            Toast.makeText(this, "没有收到图片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Uri imageUri = Uri.parse(imageUriStr);

        // 判断是不是"带状态再次编辑"
        boolean hasState = getIntent().hasExtra(EXTRA_IMAGE_WIDTH);
        hasInitialStateFromOutside = hasState;

        // 初始化置信度 UI
        if (seekConf != null) {
            seekConf.setProgress(thresholdToProgress(confThreshold));
            updateConfLabel();
            seekConf.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    confThreshold = progressToThreshold(progress);
                    updateConfLabel();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // ignore
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // 用户松手 -> 重新跑检测
                    onConfThresholdChanged();
                }
            });
        }

        if (hasState) {
            // 再次编辑：恢复旧状态
            restoreStateAndShow(imageUri);
        } else {
            // 第一次：正常跑检测
            loadAndDetect(imageUri);
        }
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

                // 保存原图，便于后续在不同阈值下重新检测
                originalBitmap = bitmap;

                // 跑 YOLO（使用当前置信度阈值）
                List<PeopleDetector.Box> boxes = peopleDetector.detectPeople(bitmap, confThreshold);
                originalModelCount = boxes.size();
                Log.d(TAG, "YOLO 检测框数量 = " + boxes.size());

                runOnUiThread(() -> {
                    isZoomMode = false;
                    updateZoomModeUI(false);
                    if (zoomContainer != null) {
                        zoomContainer.resetZoom();
                    }
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

    /** 当前这张图的总人数 = 模型框 + 手动画框 */
    private int getCurrentTotalCount() {
        int currentModel = overlayView.getModelBoxCount();
        int manual = overlayView.getManualBoxCount();
        return currentModel + manual;
    }

    /** 带状态地结束当前页 */
    private void finishWithResult() {
        int total = getCurrentTotalCount();

        Intent resultIntent = new Intent();
        String uriStr = getIntent().getStringExtra(EXTRA_IMAGE_URI);
        if (uriStr != null) {
            resultIntent.putExtra(EXTRA_IMAGE_URI, uriStr);
        }
        resultIntent.putExtra(EXTRA_RESULT_TOTAL_COUNT, total);

        // ★ 把当前状态全带回去
        resultIntent.putExtra(EXTRA_IMAGE_WIDTH, overlayView.getImageWidth());
        resultIntent.putExtra(EXTRA_IMAGE_HEIGHT, overlayView.getImageHeight());
        resultIntent.putParcelableArrayListExtra(
                EXTRA_BOXES_MODEL,
                overlayView.getCurrentModelBoxesCopy()
        );
        resultIntent.putParcelableArrayListExtra(
                EXTRA_BOXES_MODEL_ORIG,
                overlayView.getOriginalModelBoxesCopy()
        );
        resultIntent.putParcelableArrayListExtra(
                EXTRA_BOXES_MANUAL,
                overlayView.getManualBoxesCopy()
        );

        setResult(RESULT_OK, resultIntent);
        finish();
    }

    /** 再次编辑：恢复之前保存的框状态 */
    private void restoreStateAndShow(Uri uri) {
        new Thread(() -> {
            try {
                // 把图片加载出来显示（和以前一样）
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

                // 保存原图，便于后续在不同阈值下重新检测
                originalBitmap = bitmap;

                // 从 Intent 中取出之前保存的框状态
                ArrayList<PeopleDetector.Box> currentBoxes =
                        getIntent().getParcelableArrayListExtra(EXTRA_BOXES_MODEL);
                ArrayList<PeopleDetector.Box> originalBoxes =
                        getIntent().getParcelableArrayListExtra(EXTRA_BOXES_MODEL_ORIG);
                ArrayList<RectF> manualBoxes =
                        getIntent().getParcelableArrayListExtra(EXTRA_BOXES_MANUAL);
                int imgW = getIntent().getIntExtra(EXTRA_IMAGE_WIDTH, bitmap.getWidth());
                int imgH = getIntent().getIntExtra(EXTRA_IMAGE_HEIGHT, bitmap.getHeight());

                originalModelCount = (originalBoxes != null) ? originalBoxes.size() : 0;

                runOnUiThread(() -> {
                    isZoomMode = false;
                    updateZoomModeUI(false);
                    if (zoomContainer != null) {
                        zoomContainer.resetZoom();
                    }
                    ivResult.setImageBitmap(bitmap);
                    overlayView.setResultsWithState(currentBoxes, originalBoxes, manualBoxes, imgW, imgH);
                    updateCounts();
                    Toast.makeText(this, "已恢复上次编辑状态", Toast.LENGTH_SHORT).show();
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
        int total = getCurrentTotalCount();

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

    /** 删除整张图片（本次统计忽略） */
    private void showDeletePhotoDialog() {
        new AlertDialog.Builder(this)
                .setTitle("删除图片")
                .setMessage("确定从本次统计中删除这张图片吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(EXTRA_IMAGE_URI, imageUriStr);
                    resultIntent.putExtra(EXTRA_DELETE_PHOTO, true);
                    setResult(RESULT_FIRST_USER, resultIntent);
                    finish();
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

    private void updateZoomModeUI(boolean showToast) {
        if (zoomContainer != null) {
            zoomContainer.setZoomEnabled(isZoomMode);
        }
        if (btnZoomMode != null) {
            btnZoomMode.setText(isZoomMode ? "切到编辑模式" : "进入缩放模式");
        }
        if (showToast) {
            String msg = isZoomMode
                    ? "已进入“缩放模式”：可双指缩放/拖动，无法画框"
                    : "已进入“编辑模式”：可画框/删除，不再改变缩放";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    // ===== 置信度阈值相关逻辑 =====

    /** SeekBar progress(0~80) -> 阈值(0.10~0.90) */
    private float progressToThreshold(int progress) {
        float min = 0.10f;
        float max = 0.90f;
        float ratio = progress / 80f;
        return min + (max - min) * ratio;
    }

    /** 阈值(0.10~0.90) -> SeekBar progress(0~80) */
    private int thresholdToProgress(float threshold) {
        float min = 0.10f;
        float max = 0.90f;
        float t = Math.max(min, Math.min(max, threshold));
        return Math.round((t - min) / (max - min) * 80f);
    }

    /** 更新 TextView 显示当前置信度阈值 */
    private void updateConfLabel() {
        if (tvConfLabel != null) {
            tvConfLabel.setText(String.format("置信度阈值：%.2f", confThreshold));
        }
    }

    /** 当用户调整置信度阈值后触发 */
    private void onConfThresholdChanged() {
        if (originalBitmap == null) return;

        // 再次编辑 + 已经有手动画框或模型框被改动 -> 提醒用户
        if (hasInitialStateFromOutside &&
                (overlayView.getManualBoxCount() > 0 ||
                        overlayView.getModelBoxCount() != originalModelCount)) {

            new AlertDialog.Builder(this)
                    .setTitle("重新检测")
                    .setMessage("修改置信度会重新跑检测，并清空当前的编辑结果，是否继续？")
                    .setPositiveButton("继续", (d, w) -> redoDetectWithNewThreshold())
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            redoDetectWithNewThreshold();
        }
    }

    /** 按当前阈值重新跑一次检测 */
    private void redoDetectWithNewThreshold() {
        if (originalBitmap == null) return;

        new Thread(() -> {
            // 如果担心 Bitmap 很大，可以在这里做 copy/缩放再跑，这里直接用原图
            List<PeopleDetector.Box> newBoxes =
                    peopleDetector.detectPeople(originalBitmap, confThreshold);
            originalModelCount = newBoxes.size();

            runOnUiThread(() -> {
                overlayView.setResults(newBoxes,
                        originalBitmap.getWidth(),
                        originalBitmap.getHeight());
                updateCounts();
                Toast.makeText(this,
                        "已按新置信度重新检测，人数：" + newBoxes.size(),
                        Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
}

