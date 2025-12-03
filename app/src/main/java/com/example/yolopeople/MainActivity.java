package com.example.yolopeople;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int REQ_CAMERA = 1001;
    private static final int REQ_GALLERY = 1002;
    private static final int REQ_PERMISSIONS = 2001;

    private ImageView ivResult;
    private Button btnRun;      // 测试图按钮（可选）
    private Button btnCamera;
    private Button btnGallery;
    private DetectOverlayView overlayView;   // 新增：覆盖层

    private PeopleDetector peopleDetector;
    
    // 当前拍照保存的文件路径
    private String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivResult   = findViewById(R.id.iv_result);
        btnRun     = findViewById(R.id.btn_run_detector);
        btnCamera  = findViewById(R.id.btn_camera);
        btnGallery = findViewById(R.id.btn_gallery);
        overlayView = findViewById(R.id.detect_overlay);  // 找到覆盖层

        // 初始化检测器
        peopleDetector = new PeopleDetector(getApplicationContext());

        // 申请相机 / 相册权限（简单粗暴：一进来就申请）
        requestAllPermissionsIfNeeded();

        // 内置测试图检测（可选）
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runDetectionOnTestImage();
            }
        });

        // 拍照检测
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

        // 相册选图检测
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
            // Android 13+
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

    // ========= 打开相机 / 相册 ============

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            // 创建照片文件
            File photoFile = createImageFile();
            if (photoFile != null) {
                // 获取 FileProvider Uri
                Uri photoUri = FileProvider.getUriForFile(
                        this,
                        "com.example.yolopeople.fileprovider",
                        photoFile
                );
                
                // 指定输出 Uri，让相机保存原图到文件
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
    
    /**
     * 创建用于保存照片的文件
     */
    private File createImageFile() {
        try {
            // 创建文件名（带时间戳）
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            
            // 获取外部存储的 Pictures 目录
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (storageDir == null) {
                // 如果外部存储不可用，使用内部缓存目录
                storageDir = getCacheDir();
            }
            
            // 确保目录存在
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            
            // 创建临时文件
            File imageFile = File.createTempFile(
                    imageFileName,
                    ".jpg",
                    storageDir
            );
            
            // 保存路径，用于后续读取
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

    // ========= 统一的检测入口：对任意 Bitmap 做检测 ============

    private void runDetectionOnBitmap(Bitmap origin) {
        if (origin == null) {
            Toast.makeText(this, "图片无效", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            // 检测
            List<PeopleDetector.Box> boxes = peopleDetector.detectPeople(origin);
            Log.d(TAG, "YOLO 检测框数量 = " + boxes.size());

            for (PeopleDetector.Box b : boxes) {
                Log.d(TAG, "box = (" +
                        b.left + "," + b.top + "," + b.right + "," + b.bottom + "), conf=" + b.confidence);
            }

            // 回到主线程更新 UI
            runOnUiThread(() -> {
                ivResult.setImageBitmap(origin); // 只显示原图
                overlayView.setResults(boxes, origin.getWidth(), origin.getHeight()); // 画框
                Toast.makeText(this, "检测到人数：" + boxes.size(), Toast.LENGTH_SHORT).show();
            });

        }).start();
    }

    /**
     * 保留一个"内置测试图"的入口（你可以以后删掉）
     */
    private void runDetectionOnTestImage() {
        new Thread(() -> {
            Bitmap origin = null;

            // 先尝试从 drawable 加载
            try {
                int resId = getResources().getIdentifier("classroom", "drawable", getPackageName());
                if (resId != 0) {
                    origin = BitmapFactory.decodeResource(getResources(), resId);
                }
            } catch (Exception e) {
                Log.d(TAG, "无法从 drawable 加载图片: " + e.getMessage());
            }

            // 如果 drawable 中没有，尝试从 assets 加载
            if (origin == null) {
                try {
                    origin = BitmapFactory.decodeStream(getAssets().open("classroom.jpg"));
                } catch (Exception e) {
                    try {
                        origin = BitmapFactory.decodeStream(getAssets().open("classroom.png"));
                    } catch (Exception e2) {
                        Log.d(TAG, "无法从 assets 加载图片: " + e2.getMessage());
                    }
                }
            }

            if (origin == null) {
                runOnUiThread(() ->
                        Toast.makeText(this, "无法加载测试图片\n请添加 classroom.jpg 到 drawable 或 assets 目录", Toast.LENGTH_LONG).show()
                );
                return;
            }

            runDetectionOnBitmap(origin);
        }).start();
    }


    // ========= onActivityResult：拿到相机/相册的图片 ============

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == REQ_CAMERA) {
            // 使用 EXTRA_OUTPUT 时，相机会把原图保存到我们指定的文件
            // data 可能为 null，需要从文件读取
            if (currentPhotoPath != null) {
                File photoFile = new File(currentPhotoPath);
                if (photoFile.exists()) {
                    // 从文件解码原图（而不是从 data.getExtras() 获取缩略图）
                    Bitmap fullBitmap = BitmapFactory.decodeFile(currentPhotoPath);
                    if (fullBitmap != null) {
                        runDetectionOnBitmap(fullBitmap);
                    } else {
                        Toast.makeText(this, "无法解码照片文件", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "照片文件不存在", Toast.LENGTH_SHORT).show();
                }
            } else {
                // 降级处理：如果没有使用 EXTRA_OUTPUT，尝试从 extras 获取（缩略图）
                if (data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        Bitmap photo = (Bitmap) extras.get("data");
                        if (photo != null) {
                            Toast.makeText(this, "警告：使用的是缩略图，图片可能较模糊", Toast.LENGTH_LONG).show();
                            runDetectionOnBitmap(photo);
                        } else {
                            Toast.makeText(this, "相机返回图片为空", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Toast.makeText(this, "相机返回数据为空", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQ_GALLERY) {
            // 相册返回的是 Uri
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    try {
                        InputStream is = getContentResolver().openInputStream(uri);
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        if (is != null) is.close();
                        if (bitmap != null) {
                            runDetectionOnBitmap(bitmap);
                        } else {
                            Toast.makeText(this, "无法解码选中的图片", Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "读取图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (peopleDetector != null) {
            peopleDetector.close();
        }
    }
}

