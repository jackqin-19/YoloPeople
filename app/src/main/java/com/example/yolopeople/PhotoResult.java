package com.example.yolopeople;

import android.graphics.RectF;

import java.util.ArrayList;

/**
 * 按图片保存的识别结果，支持再次编辑。
 */
public class PhotoResult {
    public String imageUri;
    public int totalCount;

    public ArrayList<PeopleDetector.Box> currentModelBoxes = new ArrayList<>();
    public ArrayList<PeopleDetector.Box> originalModelBoxes = new ArrayList<>();
    public ArrayList<RectF> manualBoxes = new ArrayList<>();
    public int imageWidth;
    public int imageHeight;
}

