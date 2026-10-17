package com.pdclockout.helper.download;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class TikWmFallback {
    private static final String SUBMIT = "https://tikwm.com/api/video/task/submit";
    private static final String RESULT = "https://tikwm.com/api/video/task/result?task_id=";
    private static final String UA = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/141.0 Mobile Safari/537.36";

    private TikWmFallback() {}

    public interface Progress {
        void onProgress(int percent, String status);
    }

    public static Result download(String mediaUrl, File outDir, Progress progress, BooleanSupplier cancelled) throws Exception {
        Entry entry = fetchEntry(mediaUrl, cancelled);
        String id = NameTools.sanitize(entry.videoId, "video", 40);
        File dst = new File(outDir, "MEDIA_TikTok_" + id + ".mp4");
        downloadFile(entry.playUrl, dst, progress, cancelled);

        JSONObject meta = new JSONObject();
        meta.put("filepath", dst.getAbsolutePath());
        meta.put("_filename", dst.getAbsolutePath());
        meta.put("title", entry.title.isBlank() ? "TikTok 영상" : entry.title);
        meta.put("extractor_key", "TikTok");
        meta.put("uploader", entry.username);
        meta.put("webpage_url", mediaUrl);
        return new Result(dst, meta);
    }

    private static Entry fetchEntry(String mediaUrl, BooleanSupplier cancelled) throws Exception {
        String taskId = "";
        Exception last = null;
        for (String candidate : candidates(mediaUrl)) {
            if (cancelled.getAsBoolean()) throw new InterruptedException("취소됨");
            try {
                String body = "web=1&url=" + URLEncoder.encode(candidate, "UTF-8");
                JSONObject j = requestJson(SUBMIT, "POST", body);
                if (j.optInt("code", -1) == 0) {
                    JSONObject data = j.optJSONObject("data");
                    if (data != null) taskId = first(data, "task_id", "taskId", "id");
                }
                if (!taskId.isBlank()) break;
                String msg = first(j, "msg", "message");
                if (!msg.isBlank()) last = new IllegalStateException(msg);
            } catch (Exception e) {
                last = e;
            }
        }
        if (taskId.isBlank()) throw last != null ? last : new IllegalStateException("TikTok 보조 엔진 작업 ID 확인 실패");

        String[] identity = identity(mediaUrl);
        for (int i = 0; i < 60; i++) {
            if (cancelled.getAsBoolean()) throw new InterruptedException("취소됨");
            Thread.sleep(1000);
            try {
                JSONObject j = requestJson(RESULT + URLEncoder.encode(taskId, "UTF-8"), "GET", null);
                if (j.optInt("code", -1) != 0) continue;
                JSONObject data = j.optJSONObject("data");
                if (data == null) continue;
                int status = data.optInt("status", 0);
                if (status == 3) {
                    String msg = first(data, "message", "msg", "error");
                    throw new IllegalStateException(msg.isBlank() ? "TikTok 보조 엔진 처리 실패" : msg);
                }
                if (status != 2) continue;

                JSONObject detail = data.optJSONObject("detail");
                if (detail == null) detail = new JSONObject();
                String play = first(detail, "hdplay", "play_url", "url", "play", "wmplay");
                if (play.isBlank()) play = first(data, "hdplay", "play_url", "url", "play");
                if (play.startsWith("//")) play = "https:" + play;
                else if (play.startsWith("/")) play = "https://www.tikwm.com" + play;
                if (play.isBlank()) throw new IllegalStateException("TikTok 보조 엔진 영상 URL 확인 실패");

                JSONObject author = detail.optJSONObject("author");
                if (author == null) author = data.optJSONObject("author");
                if (author == null) author = new JSONObject();
                String username = first(author, "unique_id", "uniqueId", "username", "nickname");
                if (username.isBlank()) username = identity[0];
                String title = first(detail, "title", "desc", "description", "text");
                if (title.isBlank()) title = first(data, "title", "desc", "description", "text");
                String videoId = first(detail, "video_id", "videoId", "id");
                if (videoId.isBlank()) videoId = first(data, "video_id", "videoId", "id");
                if (videoId.isBlank()) videoId = identity[1];
                return new Entry(play, title, username, videoId);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new IllegalStateException("TikTok 보조 엔진 응답 대기 시간 초과");
    }

    private static JSONObject requestJson(String url, String method, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        c.setRequestProperty("Origin", "https://tikwm.com");
        c.setRequestProperty("Referer", "https://tikwm.com/");
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("TikWM HTTP " + code);
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line);
        } finally {
            c.disconnect();
        }
        return new JSONObject(b.toString());
    }

    private static void downloadFile(String mediaUrl, File dst, Progress progress, BooleanSupplier cancelled) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(mediaUrl).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(60_000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "*/*");
        c.setRequestProperty("Referer", "https://www.tiktok.com/");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("TikTok 영상 HTTP " + code);
        long total = c.getContentLengthLong();
        File part = new File(dst.getAbsolutePath() + ".part");
        long written = 0;
        try (BufferedInputStream in = new BufferedInputStream(c.getInputStream());
             FileOutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (cancelled.getAsBoolean()) throw new InterruptedException("취소됨");
                out.write(buf, 0, n);
                written += n;
                int pct = total > 0 ? (int) Math.min(99, written * 100 / total) : 0;
                progress.onProgress(pct, "TikTok 보조 엔진 다운로드 중");
            }
            out.flush();
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw e;
        } finally {
            c.disconnect();
        }
        //noinspection ResultOfMethodCallIgnored
        dst.delete();
        if (!part.renameTo(dst)) throw new IllegalStateException("TikTok 임시 파일 이동 실패");
    }

    private static List<String> candidates(String raw) {
        Set<String> set = new LinkedHashSet<>();
        set.add(UrlTools.firstUrl(raw));
        String[] id = identity(raw);
        if (!id[1].isBlank()) {
            if (!id[0].isBlank()) set.add("https://www.tiktok.com/@" + id[0] + "/video/" + id[1]);
            set.add(id[1]);
            set.add("https://www.tiktok.com/video/" + id[1]);
            set.add("https://www.tiktok.com/@tiktok/video/" + id[1]);
            set.add("https://m.tiktok.com/v/" + id[1] + ".html");
        }
        return new ArrayList<>(set);
    }

    private static String[] identity(String raw) {
        String username = "", videoId = "";
        try {
            URI u = URI.create(UrlTools.firstUrl(raw));
            String[] p = u.getPath().replaceAll("^/+|/+$", "").split("/");
            for (int i = 0; i < p.length; i++) {
                if (p[i].startsWith("@") && p[i].length() > 1) username = p[i].substring(1);
                if ((p[i].equalsIgnoreCase("video") || p[i].equalsIgnoreCase("photo")) && i + 1 < p.length) videoId = p[i + 1];
            }
        } catch (Exception ignored) {}
        return new String[]{username, videoId};
    }

    private static String first(JSONObject o, String... keys) {
        if (o == null) return "";
        for (String k : keys) {
            Object v = o.opt(k);
            if (v != null && v != JSONObject.NULL) {
                String s = String.valueOf(v).trim();
                if (!s.isBlank()) return s;
            }
        }
        return "";
    }

    private static final class Entry {
        final String playUrl, title, username, videoId;
        Entry(String playUrl, String title, String username, String videoId) {
            this.playUrl = playUrl;
            this.title = title;
            this.username = username;
            this.videoId = videoId;
        }
    }

    public static final class Result {
        public final File file;
        public final JSONObject meta;
        Result(File file, JSONObject meta) { this.file = file; this.meta = meta; }
    }
}
