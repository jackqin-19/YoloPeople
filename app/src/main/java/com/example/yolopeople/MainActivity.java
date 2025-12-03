package com.example.yolopeople;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int REQ_CAMERA = 1001;
    private static final int REQ_GALLERY = 1002;
    private static final int REQ_PERMISSIONS = 2001;

    private Button btnCamera;
    private Button btnGallery;

    // 当前拍照保存的文件路径
    private String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnCamera  = findViewById(R.id.btn_camera);
        btnGallery = findViewById(R.id.btn_gallery);

        // 进来时先检查/申请权限
        requestAllPermissionsIfNeeded();

        // 拍照
        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!checkPermissionsGranted()) {
                    requestAllPermissionsIfNeeded();
                    return;
                }
                openCamera();
            }
        });

        // 相册选图
        btnGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!checkPermissionsGranted()) {
                    requestAllPermissionsIfNeeded();
                    return;
                }
                openGallery();
            }
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

    // ========= 结果回调：统一跳到 DetectActivity 做识别 ============

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
                    // 使用 FileProvider 构造 Uri（兼容 Android 7.0+）
                    Uri uri = FileProvider.getUriForFile(
                            this,
                            "com.example.yolopeople.fileprovider",
                            photoFile
                    );
                    openDetectScreen(uri);
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
                    openDetectScreen(uri);
                } else {
                    Toast.makeText(this, "选图失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /** 跳转到识别界面 */
    private void openDetectScreen(Uri uri) {
        Intent intent = new Intent(this, DetectActivity.class);
        intent.putExtra(DetectActivity.EXTRA_IMAGE_URI, uri.toString());
        startActivity(intent);
    }
}
