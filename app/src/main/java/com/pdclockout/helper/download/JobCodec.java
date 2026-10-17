package com.pdclockout.helper.download;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class JobCodec {
    private JobCodec() {}

    public static File write(File dir, List<DownloadItem> items, int modeOrdinal) throws Exception {
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("작업 폴더 생성 실패");
        JSONObject root = new JSONObject();
        root.put("mode", modeOrdinal);
        JSONArray arr = new JSONArray();
        for (DownloadItem i : items) arr.put(i.toJson());
        root.put("items", arr);
        File f = new File(dir, "job_" + System.currentTimeMillis() + ".json");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        return f;
    }

    public static Job read(File f) throws Exception {
        byte[] bytes;
        try (FileInputStream in = new FileInputStream(f)) {
            bytes = new byte[(int) f.length()];
            int off = 0;
            while (off < bytes.length) {
                int n = in.read(bytes, off, bytes.length - off);
                if (n < 0) break;
                off += n;
            }
            if (off != bytes.length) throw new IllegalStateException("작업 파일 읽기 실패");
        }
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        List<DownloadItem> items = new ArrayList<>();
        JSONArray arr = root.getJSONArray("items");
        for (int i = 0; i < arr.length(); i++) items.add(DownloadItem.fromJson(arr.getJSONObject(i)));
        return new Job(items, DownloadMode.fromOrdinalSafe(root.optInt("mode", 2)));
    }

    public static final class Job {
        public final List<DownloadItem> items;
        public final DownloadMode mode;
        Job(List<DownloadItem> items, DownloadMode mode) {
            this.items = items;
            this.mode = mode;
        }
    }
}
