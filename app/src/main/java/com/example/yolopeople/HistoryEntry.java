package com.example.yolopeople;

import java.util.ArrayList;

/**
 * 一次课堂统计的完整记录。
 */
public class HistoryEntry {
    public String id;
    public long createdAt;
    public long updatedAt;
    public String remark;

    public ArrayList<PhotoResult> photos = new ArrayList<>();
    public int totalCount;
}

