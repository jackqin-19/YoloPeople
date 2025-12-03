package com.example.yolopeople;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 统计页：管理多张图片的识别结果 + 总人数
 */
public class StatsActivity extends AppCompatActivity {

    private static final String TAG = "StatsActivity";

    private static final int REQ_CAMERA = 1001;
    private static final int REQ_GALLERY = 1002;
    private static final int REQ_PERMISSIONS = 2001;

    private static final int REQ_DETECT_NEW  = 3001; // 新增图片时编辑
    private static final int REQ_DETECT_EDIT = 3002; // 重新编辑已有图片

    private Button btnCamera;
    private Button btnGallery;
    private TextView tvTotalCount;
    private RecyclerView rvPhotos;

    // 每张图片的统计结果
    private static class PhotoResult {
        String imageUri;   // 图片 Uri
        int totalCount;    // 这张图最终人数

        // ★ 新增：用于再次编辑时恢复
        ArrayList<PeopleDetector.Box> currentModelBoxes;
        ArrayList<PeopleDetector.Box> originalModelBoxes;
        ArrayList<RectF> manualBoxes;
        int imageWidth;
        int imageHeight;
    }

    private final List<PhotoResult> photoResults = new ArrayList<>();
    private PhotoResultAdapter adapter;

    // 当前拍照保存的文件路径
    private String currentPhotoPath;

    // 记录当前是编辑哪一项
    private int editingIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);   // 新布局

        btnCamera  = findViewById(R.id.btn_camera);
        btnGallery = findViewById(R.id.btn_gallery);
        tvTotalCount = findViewById(R.id.tv_total_count);
        rvPhotos = findViewById(R.id.rv_photos);

        // 列表初始化
        adapter = new PhotoResultAdapter(photoResults);
        rvPhotos.setLayoutManager(new LinearLayoutManager(this));
        rvPhotos.setAdapter(adapter);

        // 进来时先检查/申请权限
        requestAllPermissionsIfNeeded();

        // 拍照添加一张图片
        btnCamera.setOnClickListener(v -> {
            if (!checkPermissionsGranted()) {
                requestAllPermissionsIfNeeded();
                return;
            }
            openCamera();
        });

        // 相册选图添加一张图片
        btnGallery.setOnClickListener(v -> {
            if (!checkPermissionsGranted()) {
                requestAllPermissionsIfNeeded();
                return;
            }
            openGallery();
        });
    }

    // ========= 权限相关 ============

    private boolean checkPermissionsGranted() {
        boolean cameraOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        boolean readOk;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            readOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            readOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return cameraOk && readOk;
    }

    private void requestAllPermissionsIfNeeded() {
        if (checkPermissionsGranted()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.READ_MEDIA_IMAGES
                    },
                    REQ_PERMISSIONS
            );
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    REQ_PERMISSIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_PERMISSIONS) {
            if (!checkPermissionsGranted()) {
                Toast.makeText(this, "需要相机和读取图片权限才能使用", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ========= 打开相机 / 相册 ============

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File photoFile = createImageFile();
            if (photoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(
                        this,
                        "com.example.yolopeople.fileprovider",
                        photoFile
                );
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(intent, REQ_CAMERA);
            } else {
                Toast.makeText(this, "无法创建照片文件", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "没有找到相机应用", Toast.LENGTH_SHORT).show();
        }
    }

    /** 创建用于保存照片的文件 */
    private File createImageFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";

            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (storageDir == null) {
                storageDir = getCacheDir();
            }
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            File imageFile = File.createTempFile(
                    imageFileName,
                    ".jpg",
                    storageDir
            );
            currentPhotoPath = imageFile.getAbsolutePath();
            return imageFile;
        } catch (IOException e) {
            Log.e(TAG, "创建照片文件失败: " + e.getMessage());
            return null;
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_GALLERY);
    }

    // ========= 识别页跳转 ============

    /** 新增图片：拍照/相册拿到 Uri 后，直接进 DetectActivity */
    private void openDetectForNewPhoto(Uri uri) {
        Intent intent = new Intent(this, DetectActivity.class);
        intent.putExtra(DetectActivity.EXTRA_IMAGE_URI, uri.toString());
        startActivityForResult(intent, REQ_DETECT_NEW);
    }

    /** 再次编辑已有图片 */
    private void openDetectForEdit(int index) {
        if (index < 0 || index >= photoResults.size()) return;
        PhotoResult pr = photoResults.get(index);
        Intent intent = new Intent(this, DetectActivity.class);
        intent.putExtra(DetectActivity.EXTRA_IMAGE_URI, pr.imageUri);

        // ★ 把当前状态传过去，DetectActivity 就能恢复
        intent.putExtra(DetectActivity.EXTRA_IMAGE_WIDTH, pr.imageWidth);
        intent.putExtra(DetectActivity.EXTRA_IMAGE_HEIGHT, pr.imageHeight);
        intent.putParcelableArrayListExtra(
                DetectActivity.EXTRA_BOXES_MODEL,
                pr.currentModelBoxes
        );
        intent.putParcelableArrayListExtra(
                DetectActivity.EXTRA_BOXES_MODEL_ORIG,
                pr.originalModelBoxes
        );
        intent.putParcelableArrayListExtra(
                DetectActivity.EXTRA_BOXES_MANUAL,
                pr.manualBoxes
        );

        startActivityForResult(intent, REQ_DETECT_EDIT);
        editingIndex = index;
    }

    // ========= 结果回调：相机/相册 + 识别页 =========

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == REQ_CAMERA) {
            if (currentPhotoPath != null) {
                File photoFile = new File(currentPhotoPath);
                if (photoFile.exists()) {
                    Uri uri = FileProvider.getUriForFile(
                            this,
                            "com.example.yolopeople.fileprovider",
                            photoFile
                    );
                    openDetectForNewPhoto(uri);
                } else {
                    Toast.makeText(this, "照片文件不存在", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "未找到照片路径", Toast.LENGTH_SHORT).show();
            }

        } else if (requestCode == REQ_GALLERY) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    openDetectForNewPhoto(uri);
                } else {
                    Toast.makeText(this, "选图失败", Toast.LENGTH_SHORT).show();
                }
            }

        } else if (requestCode == REQ_DETECT_NEW && data != null) {
            int total = data.getIntExtra(DetectActivity.EXTRA_RESULT_TOTAL_COUNT, 0);
            String uriStr = data.getStringExtra(DetectActivity.EXTRA_IMAGE_URI);

            ArrayList<PeopleDetector.Box> currentBoxes =
                    data.getParcelableArrayListExtra(DetectActivity.EXTRA_BOXES_MODEL);
            ArrayList<PeopleDetector.Box> originalBoxes =
                    data.getParcelableArrayListExtra(DetectActivity.EXTRA_BOXES_MODEL_ORIG);
            ArrayList<RectF> manualBoxes =
                    data.getParcelableArrayListExtra(DetectActivity.EXTRA_BOXES_MANUAL);
            int imgW = data.getIntExtra(DetectActivity.EXTRA_IMAGE_WIDTH, 0);
            int imgH = data.getIntExtra(DetectActivity.EXTRA_IMAGE_HEIGHT, 0);

            if (uriStr != null) {
                PhotoResult pr = new PhotoResult();
                pr.imageUri = uriStr;
                pr.totalCount = total;

                pr.currentModelBoxes = (currentBoxes != null) ? currentBoxes : new ArrayList<>();
                pr.originalModelBoxes = (originalBoxes != null) ? originalBoxes : new ArrayList<>();
                pr.manualBoxes = (manualBoxes != null) ? manualBoxes : new ArrayList<>();
                pr.imageWidth = imgW;
                pr.imageHeight = imgH;

                photoResults.add(pr);
                adapter.notifyItemInserted(photoResults.size() - 1);
                updateTotalCount();
            }

        } else if (requestCode == REQ_DETECT_EDIT && data != null) {
            int total = data.getIntExtra(DetectActivity.EXTRA_RESULT_TOTAL_COUNT, 0);
            String uriStr = data.getStringExtra(DetectActivity.EXTRA_IMAGE_URI);

            ArrayList<PeopleDetector.Box> currentBoxes =
                    data.getParcelableArrayListExtra(DetectActivity.EXTRA_BOXES_MODEL);
            ArrayList<PeopleDetector.Box> originalBoxes =
                    data.getParcelableArrayListExtra(DetectActivity.EXTRA_BOXES_MODEL_ORIG);
            ArrayList<RectF> manualBoxes =
                    data.getParcelableArrayListExtra(DetectActivity.EXTRA_BOXES_MANUAL);
            int imgW = data.getIntExtra(DetectActivity.EXTRA_IMAGE_WIDTH, 0);
            int imgH = data.getIntExtra(DetectActivity.EXTRA_IMAGE_HEIGHT, 0);

            if (editingIndex >= 0 && editingIndex < photoResults.size() && uriStr != null) {
                PhotoResult pr = photoResults.get(editingIndex);
                pr.imageUri = uriStr;
                pr.totalCount = total;

                pr.currentModelBoxes = (currentBoxes != null) ? currentBoxes : new ArrayList<>();
                pr.originalModelBoxes = (originalBoxes != null) ? originalBoxes : new ArrayList<>();
                pr.manualBoxes = (manualBoxes != null) ? manualBoxes : new ArrayList<>();
                pr.imageWidth = imgW;
                pr.imageHeight = imgH;

                adapter.notifyItemChanged(editingIndex);
                updateTotalCount();
            }
            editingIndex = -1;
        }
    }

    /** 重新计算所有图片的总人数 */
    private void updateTotalCount() {
        int sum = 0;
        for (PhotoResult pr : photoResults) {
            sum += pr.totalCount;
        }
        tvTotalCount.setText("教室总人数：" + sum);
    }

    // ========= RecyclerView 适配器（简单版） ============

    private class PhotoResultAdapter extends RecyclerView.Adapter<PhotoResultViewHolder> {

        private final List<PhotoResult> data;

        PhotoResultAdapter(List<PhotoResult> data) {
            this.data = data;
        }

        @Override
        public PhotoResultViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater()
                    .inflate(R.layout.item_photo_result, parent, false);
            return new PhotoResultViewHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoResultViewHolder holder, int position) {
            PhotoResult pr = data.get(position);
            holder.bind(pr, position);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private class PhotoResultViewHolder extends RecyclerView.ViewHolder {

        private final android.widget.ImageView ivThumb;
        private final android.widget.TextView tvInfo;
        private final android.widget.Button btnEdit;

        PhotoResultViewHolder(android.view.View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_thumb);
            tvInfo  = itemView.findViewById(R.id.tv_info);
            btnEdit = itemView.findViewById(R.id.btn_edit);
        }

        void bind(PhotoResult pr, int position) {
            Uri uri = Uri.parse(pr.imageUri);
            ivThumb.setImageURI(uri);  // 简单直接用 URI，够 demo 用

            tvInfo.setText("图片 " + (position + 1) + "  人数：" + pr.totalCount);

            btnEdit.setOnClickListener(v -> openDetectForEdit(position));
        }
    }
}

