package com.example.yolopeople;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 简单的本地 JSON 存储，保存/加载历史记录。
 */
public class HistoryStore {

    private static final String FILE_NAME = "history.json";

    private static HistoryStore sInstance;
    private final File file;
    private final Gson gson = new Gson();

    private List<HistoryEntry> cache = null;

    private HistoryStore(Context context) {
        file = new File(context.getFilesDir(), FILE_NAME);
    }

    public static synchronized HistoryStore getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new HistoryStore(context.getApplicationContext());
        }
        return sInstance;
    }

    /** 读出所有历史记录 */
    public synchronized List<HistoryEntry> loadAll() {
        if (cache != null) {
            return new ArrayList<>(cache);
        }
        if (!file.exists()) {
            cache = new ArrayList<>();
            return new ArrayList<>();
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            Type type = new TypeToken<ArrayList<HistoryEntry>>() {}.getType();
            List<HistoryEntry> list = gson.fromJson(br, type);
            if (list == null) list = new ArrayList<>();
            cache = list;
            return new ArrayList<>(list);
        } catch (Exception e) {
            e.printStackTrace();
            cache = new ArrayList<>();
            return new ArrayList<>();
        }
    }

    private synchronized void saveAllInternal(List<HistoryEntry> list) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            gson.toJson(list, bw);
            bw.flush();
            cache = new ArrayList<>(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void addOrUpdate(HistoryEntry entry) {
        List<HistoryEntry> list = loadAll();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(entry.id)) {
                list.set(i, entry);
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(entry);
        }
        saveAllInternal(list);
    }

    public synchronized void delete(String id) {
        List<HistoryEntry> list = loadAll();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        saveAllInternal(list);
    }

    public synchronized HistoryEntry findById(String id) {
        List<HistoryEntry> list = loadAll();
        for (HistoryEntry he : list) {
            if (he.id.equals(id)) return he;
        }
        return null;
    }
}

