package com.example.yolopeople;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PeopleDetector {

    private static final String TAG = "PeopleDetector";

    private static final String MODEL_FILE_NAME = "yolov5s-fp16.tflite";

    private static final int INPUT_SIZE = 640;
    private static final int NUM_BOXES = 25200;
    private static final int NUM_CLASSES = 80;
    private static final int OUTPUT_DIM = 5 + NUM_CLASSES; // 85

    private static final float CONF_THRESHOLD = 0.30f;
    private static final float NMS_IOU_THRESHOLD = 0.5f;

    private final Interpreter interpreter;

    public static class Box {
        public float left, top, right, bottom;
        public float confidence;
    }

    public PeopleDetector(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(Runtime.getRuntime().availableProcessors());
            interpreter = new Interpreter(modelBuffer, options);

            // 简单校验输入输出 shape
            int[] inShape = interpreter.getInputTensor(0).shape();
            int[] expectedInShape = new int[]{1, INPUT_SIZE, INPUT_SIZE, 3};
            if (!Arrays.equals(inShape, expectedInShape)) {
                throw new IllegalStateException("输入 shape 不匹配, got " +
                        Arrays.toString(inShape) + " expected " + Arrays.toString(expectedInShape));
            }

            int[] outShape = interpreter.getOutputTensor(0).shape();
            int[] expectedOutShape = new int[]{1, NUM_BOXES, OUTPUT_DIM};
            if (!Arrays.equals(outShape, expectedOutShape)) {
                throw new IllegalStateException("输出 shape 不匹配, got " +
                        Arrays.toString(outShape) + " expected " + Arrays.toString(expectedOutShape));
            }

            Log.d(TAG, "模型加载成功, in=" + Arrays.toString(inShape)
                    + " out=" + Arrays.toString(outShape));

        } catch (IOException e) {
            throw new RuntimeException("加载模型失败: " + e.getMessage(), e);
        }
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        try (AssetFileDescriptor fd = context.getAssets().openFd(MODEL_FILE_NAME);
             FileInputStream inputStream = new FileInputStream(fd.getFileDescriptor());
             FileChannel fileChannel = inputStream.getChannel()) {
            long startOffset = fd.getStartOffset();
            long declaredLength = fd.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    /**
     * 对 Bitmap 进行人检测。返回的 Box 坐标是基于原图尺寸。
     */
    public List<Box> detectPeople(Bitmap originBitmap) {
        if (originBitmap == null) return new ArrayList<>();

        int origW = originBitmap.getWidth();
        int origH = originBitmap.getHeight();

        // 1. 缩放到 640x640
        Bitmap resized = Bitmap.createScaledBitmap(originBitmap, INPUT_SIZE, INPUT_SIZE, true);

        // 2. 构造输入张量
        float[][][][] input = new float[1][INPUT_SIZE][INPUT_SIZE][3];
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = resized.getPixel(x, y);
                float r = (Color.red(pixel) & 0xFF) / 255.0f;
                float g = (Color.green(pixel) & 0xFF) / 255.0f;
                float b = (Color.blue(pixel) & 0xFF) / 255.0f;
                input[0][y][x][0] = r;
                input[0][y][x][1] = g;
                input[0][y][x][2] = b;
            }
        }

        // 3. 输出张量
        float[][][] output = new float[1][NUM_BOXES][OUTPUT_DIM];

        long t0 = System.currentTimeMillis();
        interpreter.run(input, output);
        long t1 = System.currentTimeMillis();
        Log.d(TAG, "TFLite 推理耗时: " + (t1 - t0) + " ms");

        // 4. 解析检测结果（只保留 person 类）
        List<Box> all = parseDetections(output);

        // 5. NMS
        List<Box> nms = nonMaxSuppression(all, NMS_IOU_THRESHOLD);
        Log.d(TAG, "最终保留人数 = " + nms.size());

        // 6. 把 640x640 坐标映射回原图坐标
        float ratioX = origW / (float) INPUT_SIZE;
        float ratioY = origH / (float) INPUT_SIZE;
        for (Box b : nms) {
            b.left   = b.left   * ratioX;
            b.right  = b.right  * ratioX;
            b.top    = b.top    * ratioY;
            b.bottom = b.bottom * ratioY;
        }

        resized.recycle();

        return nms;
    }

    private List<Box> parseDetections(float[][][] output) {
        List<Box> result = new ArrayList<>();

        float[][] preds = output[0];
        for (int i = 0; i < NUM_BOXES; i++) {
            float[] p = preds[i];
            if (p == null || p.length < OUTPUT_DIM) continue;

            // ⭐ 关键改动：很多 TFLite 导出的 YOLOv5 是 0~1 归一化坐标，这里先 * INPUT_SIZE
            float cx = p[0] * INPUT_SIZE;
            float cy = p[1] * INPUT_SIZE;
            float w  = p[2] * INPUT_SIZE;
            float h  = p[3] * INPUT_SIZE;

            float objConf = p[4];

            // 找最大类别
            float maxClassConf = 0f;
            int maxClassId = -1;
            for (int c = 0; c < NUM_CLASSES; c++) {
                float classConf = p[5 + c];
                if (classConf > maxClassConf) {
                    maxClassConf = classConf;
                    maxClassId = c;
                }
            }

            float finalConf = objConf * maxClassConf;

            // 只保留 person（类别 ID 为 0）
            if (maxClassId != 0) {
                continue;   // 只要人
            }

            // 阈值稍微高一点，避免太乱
            if (finalConf < 0.30f) {
                continue;
            }

            float x1 = cx - w / 2f;
            float y1 = cy - h / 2f;
            float x2 = cx + w / 2f;
            float y2 = cy + h / 2f;

            x1 = clamp(x1, 0, INPUT_SIZE);
            y1 = clamp(y1, 0, INPUT_SIZE);
            x2 = clamp(x2, 0, INPUT_SIZE);
            y2 = clamp(y2, 0, INPUT_SIZE);

            if (x2 <= x1 || y2 <= y1) continue;

            Box box = new Box();
            box.left = x1;
            box.top = y1;
            box.right = x2;
            box.bottom = y2;
            box.confidence = finalConf;

            result.add(box);
        }

        Log.d(TAG, "原始检测框数(只保留 person) = " + result.size());
        return result;
    }

    private List<Box> nonMaxSuppression(List<Box> boxes, float iouThreshold) {
        List<Box> keep = new ArrayList<>();
        if (boxes == null || boxes.isEmpty()) return keep;

        // 简单 O(n^2) NMS，人数一般不多没问题
        boolean[] removed = new boolean[boxes.size()];
        for (int i = 0; i < boxes.size(); i++) {
            if (removed[i]) continue;
            Box a = boxes.get(i);
            keep.add(a);
            for (int j = i + 1; j < boxes.size(); j++) {
                if (removed[j]) continue;
                Box b = boxes.get(j);
                float iou = calcIoU(a, b);
                if (iou > iouThreshold) {
                    removed[j] = true;
                }
            }
        }
        return keep;
    }

    private float calcIoU(Box a, Box b) {
        float interLeft   = Math.max(a.left,   b.left);
        float interTop    = Math.max(a.top,    b.top);
        float interRight  = Math.min(a.right,  b.right);
        float interBottom = Math.min(a.bottom, b.bottom);

        float interW = Math.max(0, interRight - interLeft);
        float interH = Math.max(0, interBottom - interTop);
        float interArea = interW * interH;

        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        float union = areaA + areaB - interArea;
        if (union <= 0) return 0f;
        return interArea / union;
    }

    private float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}

