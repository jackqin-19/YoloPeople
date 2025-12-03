package com.example.yolopeople;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 统计页：管理多张图片的识别结果 + 总人数，可以保存/恢复历史。
 */
public class StatsActivity extends AppCompatActivity {

    private static final String TAG = "StatsActivity";
    public static final String EXTRA_HISTORY_ID = "extra_history_id";

    private static final int REQ_CAMERA = 1001;
    private static final int REQ_GALLERY = 1002;
    private static final int REQ_PERMISSIONS = 2001;

    private static final int REQ_DETECT_NEW = 3001;
    private static final int REQ_DETECT_EDIT = 3002;

    private Button btnCamera;
    private Button btnGallery;
    private Button btnSaveHistory;
    private TextView tvTotalCount;
    private RecyclerView rvPhotos;

    private final List<PhotoResult> photoResults = new ArrayList<>();
    private PhotoResultAdapter adapter;

    private String currentPhotoPath;
    private int editingIndex = -1;

    private String currentHistoryId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        btnCamera = findViewById(R.id.btn_camera);
        btnGallery = findViewById(R.id.btn_gallery);
        btnSaveHistory = findViewById(R.id.btn_save_history);
        tvTotalCount = findViewById(R.id.tv_total_count);
        rvPhotos = findViewById(R.id.rv_photos);

        adapter = new PhotoResultAdapter(photoResults);
        rvPhotos.setLayoutManager(new LinearLayoutManager(this));
        rvPhotos.setAdapter(adapter);

        requestAllPermissionsIfNeeded();

        btnCamera.setOnClickListener(v -> {
            if (!checkPermissionsGranted()) {
                requestAllPermissionsIfNeeded();
                return;
            }
            openCamera();
        });

        btnGallery.setOnClickListener(v -> {
            if (!checkPermissionsGranted()) {
                requestAllPermissionsIfNeeded();
                return;
            }
            openGallery();
        });

        btnSaveHistory.setOnClickListener(v -> showSaveHistoryDialog());

        currentHistoryId = getIntent().getStringExtra(EXTRA_HISTORY_ID);
        if (currentHistoryId != null) {
            loadHistorySession(currentHistoryId);
        }
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

    private Uri copyImageFromGalleryToApp(Uri srcUri) {
        try {
            InputStream in = getContentResolver().openInputStream(srcUri);
            if (in == null) return null;

            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (storageDir == null) {
                storageDir = getCacheDir();
            }
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String imageFileName = "GALLERY_" + timeStamp + "_";
            File dstFile = File.createTempFile(
                    imageFileName,
                    ".jpg",
                    storageDir
            );

            OutputStream out = new FileOutputStream(dstFile);
            byte[] buf = new byte[8 * 1024];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
            in.close();
            out.close();

            return FileProvider.getUriForFile(
                    this,
                    "com.example.yolopeople.fileprovider",
                    dstFile
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ========= 识别页跳转 ============

    private void openDetectForNewPhoto(Uri uri) {
        Intent intent = new Intent(this, DetectActivity.class);
        intent.putExtra(DetectActivity.EXTRA_IMAGE_URI, uri.toString());
        startActivityForResult(intent, REQ_DETECT_NEW);
    }

    private void openDetectForEdit(int index) {
        if (index < 0 || index >= photoResults.size()) return;
        PhotoResult pr = photoResults.get(index);
        Intent intent = new Intent(this, DetectActivity.class);
        intent.putExtra(DetectActivity.EXTRA_IMAGE_URI, pr.imageUri);
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

    // ========= 结果回调 =========

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CAMERA) {
            if (resultCode != RESULT_OK) {
                return;
            }
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
            if (resultCode != RESULT_OK) {
                return;
            }
            if (data != null) {
                Uri srcUri = data.getData();
                if (srcUri != null) {
                    Uri appUri = copyImageFromGalleryToApp(srcUri);
                    if (appUri != null) {
                        openDetectForNewPhoto(appUri);
                    } else {
                        Toast.makeText(this, "复制图片失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "选图失败", Toast.LENGTH_SHORT).show();
                }
            }

        } else if (requestCode == REQ_DETECT_NEW) {
            handleDetectNewResult(resultCode, data);

        } else if (requestCode == REQ_DETECT_EDIT) {
            handleDetectEditResult(resultCode, data);
        }
    }

    private void handleDetectNewResult(int resultCode, @Nullable Intent data) {
        if (data == null) {
            return;
        }

        if (resultCode == RESULT_FIRST_USER &&
                data.getBooleanExtra(DetectActivity.EXTRA_DELETE_PHOTO, false)) {
            return;
        }

        if (resultCode != RESULT_OK) {
            return;
        }

        PhotoResult pr = new PhotoResult();
        if (fillPhotoResultFromIntent(pr, data)) {
            photoResults.add(pr);
            adapter.notifyItemInserted(photoResults.size() - 1);
            updateTotalCount();
        }
    }

    private void handleDetectEditResult(int resultCode, @Nullable Intent data) {
        if (data == null) {
            editingIndex = -1;
            return;
        }

        if (resultCode == RESULT_FIRST_USER &&
                data.getBooleanExtra(DetectActivity.EXTRA_DELETE_PHOTO, false)) {
            if (editingIndex >= 0 && editingIndex < photoResults.size()) {
                photoResults.remove(editingIndex);
                adapter.notifyItemRemoved(editingIndex);
                updateTotalCount();
            }
            editingIndex = -1;
            return;
        }

        if (resultCode != RESULT_OK) {
            editingIndex = -1;
            return;
        }

        if (editingIndex >= 0 && editingIndex < photoResults.size()) {
            PhotoResult target = photoResults.get(editingIndex);
            if (fillPhotoResultFromIntent(target, data)) {
                adapter.notifyItemChanged(editingIndex);
                updateTotalCount();
            }
        }
        editingIndex = -1;
    }

    private boolean fillPhotoResultFromIntent(PhotoResult pr, Intent data) {
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

        if (uriStr == null) {
            Toast.makeText(this, "未获取到图片地址", Toast.LENGTH_SHORT).show();
            return false;
        }

        pr.imageUri = uriStr;
        pr.totalCount = total;
        pr.currentModelBoxes = currentBoxes != null ? currentBoxes : new ArrayList<>();
        pr.originalModelBoxes = originalBoxes != null ? originalBoxes : new ArrayList<>();
        pr.manualBoxes = manualBoxes != null ? manualBoxes : new ArrayList<>();
        pr.imageWidth = imgW;
        pr.imageHeight = imgH;
        return true;
    }

    private void updateTotalCount() {
        tvTotalCount.setText("教室总人数：" + calcTotalCount());
    }

    private int calcTotalCount() {
        int sum = 0;
        for (PhotoResult pr : photoResults) {
            sum += pr.totalCount;
        }
        return sum;
    }

    // ========= 历史记录 =========

    private void showSaveHistoryDialog() {
        if (photoResults.isEmpty()) {
            Toast.makeText(this, "当前没有任何图片，无法保存历史记录", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText et = new EditText(this);
        et.setHint("请输入备注，例如：301 教室 第三节课");

        if (currentHistoryId != null) {
            HistoryEntry old = HistoryStore.getInstance(this).findById(currentHistoryId);
            if (old != null && old.remark != null) {
                et.setText(old.remark);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("保存为历史记录")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String remark = et.getText().toString().trim();
                    saveHistorySession(remark);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveHistorySession(String remark) {
        HistoryStore store = HistoryStore.getInstance(this);
        long now = System.currentTimeMillis();

        HistoryEntry entry;
        if (currentHistoryId != null) {
            entry = store.findById(currentHistoryId);
            if (entry == null) {
                entry = new HistoryEntry();
                entry.id = currentHistoryId;
                entry.createdAt = now;
            }
        } else {
            entry = new HistoryEntry();
            entry.id = UUID.randomUUID().toString();
            entry.createdAt = now;
            currentHistoryId = entry.id;
        }

        entry.updatedAt = now;
        entry.remark = remark;
        entry.totalCount = calcTotalCount();
        entry.photos = clonePhotoResults(photoResults);

        store.addOrUpdate(entry);
        Toast.makeText(this, "已保存到历史记录", Toast.LENGTH_SHORT).show();
    }

    private void loadHistorySession(String historyId) {
        HistoryEntry entry = HistoryStore.getInstance(this).findById(historyId);
        if (entry == null) {
            Toast.makeText(this, "历史记录不存在或已删除", Toast.LENGTH_SHORT).show();
            currentHistoryId = null;
            return;
        }

        photoResults.clear();
        photoResults.addAll(clonePhotoResults(entry.photos));
        adapter.notifyDataSetChanged();
        updateTotalCount();

        String remark = entry.remark == null ? "" : entry.remark;
        Toast.makeText(this, "已打开历史记录：" + remark, Toast.LENGTH_SHORT).show();
    }

    private ArrayList<PhotoResult> clonePhotoResults(List<PhotoResult> source) {
        ArrayList<PhotoResult> list = new ArrayList<>();
        if (source == null) return list;

        for (PhotoResult src : source) {
            if (src == null) continue;
            PhotoResult copy = new PhotoResult();
            copy.imageUri = src.imageUri;
            copy.totalCount = src.totalCount;
            copy.imageWidth = src.imageWidth;
            copy.imageHeight = src.imageHeight;
            copy.currentModelBoxes = cloneBoxes(src.currentModelBoxes);
            copy.originalModelBoxes = cloneBoxes(src.originalModelBoxes);
            copy.manualBoxes = cloneRectFs(src.manualBoxes);
            list.add(copy);
        }
        return list;
    }

    private ArrayList<PeopleDetector.Box> cloneBoxes(List<PeopleDetector.Box> source) {
        ArrayList<PeopleDetector.Box> list = new ArrayList<>();
        if (source == null) return list;
        for (PeopleDetector.Box box : source) {
            if (box == null) continue;
            list.add(new PeopleDetector.Box(box));
        }
        return list;
    }

    private ArrayList<RectF> cloneRectFs(List<RectF> source) {
        ArrayList<RectF> list = new ArrayList<>();
        if (source == null) return list;
        for (RectF rect : source) {
            if (rect == null) continue;
            list.add(new RectF(rect));
        }
        return list;
    }

    // ========= RecyclerView ============

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
        private final android.widget.Button btnDelete;

        PhotoResultViewHolder(android.view.View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_thumb);
            tvInfo = itemView.findViewById(R.id.tv_info);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }

        void bind(PhotoResult pr, int position) {
            Uri uri = Uri.parse(pr.imageUri);
            ivThumb.setImageURI(uri);
            tvInfo.setText("图片 " + (position + 1) + "  人数：" + pr.totalCount);
            btnEdit.setOnClickListener(v -> openDetectForEdit(position));
            btnDelete.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(StatsActivity.this)
                        .setTitle("删除图片")
                        .setMessage("确定从本次统计中删除这张图片吗？")
                        .setPositiveButton("删除", (dialog, which) -> {
                            int pos = getAdapterPosition();
                            if (pos != RecyclerView.NO_POSITION) {
                                photoResults.remove(pos);
                                adapter.notifyItemRemoved(pos);
                                updateTotalCount();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }
    }
}

