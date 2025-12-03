# YoloPeople Demo - 人数检测最小可运行版本

这是一个干净的、最小可运行的 YOLOv5 人数检测 Android Demo 工程。

## 功能

- ✅ 加载 TFLite 模型（YOLOv5）
- ✅ 在图片上进行人数检测
- ✅ 在图片上画出红色检测框
- ✅ 显示检测结果

## 设置步骤

### 1. 添加模型文件

将你的 `yolov5s-fp16.tflite` 模型文件复制到：

```
app/src/main/assets/yolov5s-fp16.tflite
```

**重要**：文件名必须完全一致：`yolov5s-fp16.tflite`

### 2. 添加测试图片

准备一张包含人物的测试图片（如教室场景），命名为 `classroom.jpg` 或 `classroom.png`，放到：

```
app/src/main/res/drawable/classroom.jpg
```

或者

```
app/src/main/res/drawable/classroom.png
```

### 3. 构建和运行

1. 在 Android Studio 中打开项目
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 点击 Run 按钮

## 使用说明

1. 启动 App 后，会看到一个按钮和一个 ImageView
2. 点击"运行 YOLOv5 人数检测"按钮
3. App 会：
   - 加载测试图片
   - 使用 YOLOv5 模型进行检测
   - 在图片上画出红色检测框
   - 显示检测到的人数（Toast 提示）
4. 查看 Logcat 可以看到详细的检测日志

## 项目结构

```
app/src/main/
├── assets/
│   └── yolov5s-fp16.tflite  (需要手动添加)
├── java/com/example/yolopeople/
│   ├── MainActivity.java     (主界面)
│   └── PeopleDetector.java   (检测器类)
├── res/
│   ├── drawable/
│   │   └── classroom.jpg     (需要手动添加测试图片)
│   └── layout/
│       └── activity_main.xml (界面布局)
└── AndroidManifest.xml
```

## 依赖

- AndroidX AppCompat
- TensorFlow Lite 2.15.1

## 下一步

当这个最小 Demo 能够正常显示红色检测框后，可以继续添加：

- 相机拍照功能
- 从相册选择图片
- 可编辑的人框
- SQLite 历史记录
- 等等...

## 注意事项

- 确保模型文件名完全一致
- 确保测试图片放在正确的 drawable 目录
- 如果模型加载失败，检查 Logcat 中的错误信息
- 如果检测不到人，可能是图片中没有人物，或者置信度阈值需要调整

