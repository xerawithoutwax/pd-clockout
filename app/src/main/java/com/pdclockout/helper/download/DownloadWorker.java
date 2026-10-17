package com.pdclockout.helper.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pdclockout.helper.PdClockoutApp;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

public class DownloadWorker extends Worker {
    public static final String WORK_NAME = "pdclockout_download_queue";
    public static final String KEY_JOB_PATH = "job_path";
    public static final String KEY_PROGRESS = "progress";
    public static final String KEY_STATUS = "status";
    public static final String KEY_LOG = "log";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_TOTAL = "total";

    private volatile String currentProcessId = null;
    private volatile int lastNotifyProgress = -1;

    public DownloadWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String jobPath = getInputData().getString(KEY_JOB_PATH);
        if (jobPath == null || jobPath.isBlank()) return Result.failure(errorData("작업 파일 경로가 없습니다."));

        try {
            setForegroundAsync(foreground(0, "다운로드 준비 중…", true)).get();
            Engine.ensureInitialized(getApplicationContext());

            JobCodec.Job job = JobCodec.read(new File(jobPath));
            List<DownloadItem> items = job.items;
            int total = items.size();
            if (total == 0) return Result.failure(errorData("다운로드할 URL이 없습니다."));

            Map<Integer, List<Integer>> duplicateGroups = duplicateGroups(items);
            int success = 0;
            int failed = 0;

            File root = new File(getApplicationContext().getExternalFilesDir(null), "pdclockout_work/" + getId());
            deleteTree(root);
            if (!root.mkdirs()) throw new IllegalStateException("임시 작업 폴더 생성 실패: " + root);

            for (DownloadItem item : items) {
                if (isStopped()) break;
                if (item.duplicate) {
                    logProgress(item.index, total, 100, "그림" + item.index + " 중복 · 그림" + item.duplicateOfIndex + "과 동일", "[중복] 다운로드 생략");
                    continue;
                }
                if (!item.supported || !item.error.isBlank()) {
                    failed++;
                    logProgress(item.index, total, 100, "그림" + item.index + " 미지원", "[미지원] 직접 다운로드 필요 · " + item.rawUrl);
                    continue;
                }

                File itemDir = new File(root, "item_" + item.index);
                if (!itemDir.mkdirs()) throw new IllegalStateException("임시 폴더 생성 실패");
                if (!item.note.isBlank()) logProgress(item.index, total, 0, "그림" + item.index + " 준비", "[링크 정리] " + item.note);

                String prefix = titlePrefix(item.index, duplicateGroups.get(item.index), total > 1);
                try {
                    DownloadResult result = downloadWithRecovery(item, itemDir, job.mode, total);
                    String finalName = NameTools.buildFinalName(result.meta, result.file, prefix);
                    Uri saved = MediaPublisher.publish(getApplicationContext(), result.file, finalName);
                    success++;
                    logProgress(item.index, total, 100,
                            "그림" + item.index + "/" + total + " 완료",
                            "[다운로드 완료] " + finalName + " · " + saved);
                } catch (Exception e) {
                    if (isStopped()) break;
                    failed++;
                    logProgress(item.index, total, 100,
                            "그림" + item.index + "/" + total + " 실패",
                            "[실패] " + cleanError(e));
                } finally {
                    currentProcessId = null;
                    deleteTree(itemDir);
                }
            }

            deleteTree(root);
            if (isStopped()) return Result.failure(errorData("사용자 요청으로 다운로드를 취소했습니다."));

            String summary = "완료 · 성공 " + success + " / 실패 " + failed;
            setProgressAsync(new Data.Builder()
                    .putInt(KEY_PROGRESS, 100)
                    .putString(KEY_STATUS, summary)
                    .putString(KEY_LOG, "[일괄 다운로드 완료] " + summary)
                    .putInt(KEY_SUCCESS, success)
                    .putInt(KEY_TOTAL, total)
                    .build());
            notifyProgress(100, summary, false);
            return Result.success(new Data.Builder()
                    .putString(KEY_STATUS, summary)
                    .putInt(KEY_SUCCESS, success)
                    .putInt(KEY_TOTAL, total)
                    .build());
        } catch (Exception e) {
            String msg = cleanError(e);
            notifyProgress(0, "다운로드 실패", false);
            return Result.failure(errorData(msg));
        } finally {
            currentProcessId = null;
            if (jobPath != null && !jobPath.isBlank()) {
                try { new File(jobPath).delete(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onStopped() {
        String pid = currentProcessId;
        if (pid != null) {
            try {
                YoutubeDL.getInstance().destroyProcessById(pid);
            } catch (Exception ignored) {}
        }
        super.onStopped();
    }

    private DownloadResult downloadWithRecovery(DownloadItem item, File itemDir, DownloadMode mode, int total) throws Exception {
        if (UrlTools.isTikTok(item.mediaUrl) && !mode.audioOnly) {
            logProgress(item.index, total, 0, "TikTok 보조 엔진 확인 중", "[엔진] PC V3.4.9와 같은 TikWM 공개 URL 보조 엔진 사용");
            TikWmFallback.Result tik = TikWmFallback.download(
                    item.mediaUrl, itemDir,
                    (pct, status) -> logProgress(item.index, total, pct, "그림" + item.index + "/" + total + " · " + pct + "%", status),
                    this::isStopped
            );
            return new DownloadResult(tik.file, tik.meta);
        }

        Exception last;
        try {
            return runAttempt(item, itemDir, mode, total, List.of());
        } catch (Exception e) {
            last = e;
        }
        if (isStopped()) throw last;

        if (UrlTools.isYouTube(item.mediaUrl)) {
            try {
                logProgress(item.index, total, 0, "YouTube 복구 모드", "[재시도] IPv4 + 대체 플레이어");
                return runAttempt(item, itemDir, mode, total, java.util.Arrays.asList(
                        "--force-ipv4", null,
                        "--extractor-args", "youtube:player_client=android_vr,web_embedded"
                ));
            } catch (Exception e) {
                last = e;
            }
            if (!isStopped()) {
                try {
                    logProgress(item.index, total, 0, "YouTube 최종 재시도", "[재시도] web_safari 플레이어");
                    return runAttempt(item, itemDir, mode, total, java.util.Arrays.asList(
                            "--force-ipv4", null,
                            "--extractor-args", "youtube:player_client=web_safari"
                    ));
                } catch (Exception e) {
                    last = e;
                }
            }
        } else {
            try {
                logProgress(item.index, total, 0, "소셜 링크 재시도", "[재시도] IPv4 호환 모드");
                return runAttempt(item, itemDir, mode, total, java.util.Arrays.asList("--force-ipv4", null));
            } catch (Exception e) {
                last = e;
            }
        }

        if (!isStopped() && coreRecoveryCandidate(last)) {
            try {
                logProgress(item.index, total, 0, "코어 자동 복구", "[코어 자동 복구] yt-dlp nightly 확인");
                YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(), YoutubeDL.UpdateChannel._NIGHTLY);
                return runAttempt(item, itemDir, mode, total, List.of());
            } catch (Exception e) {
                last = e;
            }
        }
        throw last;
    }

    private DownloadResult runAttempt(DownloadItem item, File itemDir, DownloadMode mode, int total, List<String> extra) throws Exception {
        deleteChildren(itemDir);
        YoutubeDLRequest request = RequestFactory.build(item.mediaUrl, itemDir, mode, extra);
        currentProcessId = "pdclockout-" + getId() + "-" + item.index + "-" + System.nanoTime();
        final JSONObject[] metaHolder = new JSONObject[]{null};
        final int[] lastPct = new int[]{-1};

        Function3<Float, Long, String, Unit> callback = (progress, eta, line) -> {
            int p = Math.max(0, Math.min(100, Math.round(progress)));
            JSONObject parsed = line == null ? null : parseMetaLine(line);
            if (parsed != null) metaHolder[0] = parsed;
            if (p != lastPct[0] || parsed != null) {
                lastPct[0] = p;
                String status = "그림" + item.index + "/" + total + " · " + p + "%";
                if (eta != null && eta > 0) status += " · 약 " + eta + "초 남음";
                logProgress(item.index, total, p, status, parsed != null ? "[메타데이터] 파일명 정리 준비" : "");
            }
            return Unit.INSTANCE;
        };

        YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, currentProcessId, callback);
        if (response != null && response.getOut() != null) {
            JSONObject fromOut = parseMetaBlock(response.getOut());
            if (fromOut != null) metaHolder[0] = fromOut;
        }

        File finalFile = null;
        if (metaHolder[0] != null) {
            String path = metaHolder[0].optString("filepath", "");
            if (path.isBlank()) path = metaHolder[0].optString("_filename", "");
            if (!path.isBlank()) {
                File f = new File(path);
                if (f.isFile() && f.length() > 0) finalFile = f;
            }
        }
        if (finalFile == null) finalFile = findDownloadedFile(itemDir);
        if (finalFile == null) throw new IllegalStateException("다운로드 완료 파일을 확인하지 못했습니다.");

        if (metaHolder[0] == null) {
            JSONObject fallback = new JSONObject();
            fallback.put("title", stripExtension(finalFile.getName()));
            fallback.put("webpage_url", item.mediaUrl);
            fallback.put("extractor_key", guessedExtractor(item.mediaUrl));
            metaHolder[0] = fallback;
        }
        return new DownloadResult(finalFile, metaHolder[0]);
    }

    private void logProgress(int itemIndex, int total, int itemPct, String status, String log) {
        int overall = Math.round(((itemIndex - 1) + Math.max(0, Math.min(100, itemPct)) / 100f) * 100f / total);
        Data d = new Data.Builder()
                .putInt(KEY_PROGRESS, overall)
                .putString(KEY_STATUS, status)
                .putString(KEY_LOG, compact(log))
                .putInt(KEY_TOTAL, total)
                .build();
        setProgressAsync(d);
        notifyProgress(overall, status, false);
    }

    private ForegroundInfo foreground(int progress, String status, boolean indeterminate) {
        Notification n = notification(progress, status, indeterminate);
        return new ForegroundInfo(2107, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }

    private void notifyProgress(int progress, String status, boolean indeterminate) {
        if (!indeterminate && progress == lastNotifyProgress) return;
        lastNotifyProgress = progress;
        NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(2107, notification(progress, status, indeterminate));
    }

    private Notification notification(int progress, String status, boolean indeterminate) {
        return new NotificationCompat.Builder(getApplicationContext(), PdClockoutApp.CHANNEL_DOWNLOAD)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("PD칼퇴도우미")
                .setContentText(status)
                .setOnlyAlertOnce(true)
                .setOngoing(progress < 100)
                .setProgress(100, progress, indeterminate)
                .build();
    }

    private static Map<Integer, List<Integer>> duplicateGroups(List<DownloadItem> items) {
        Map<Integer, List<Integer>> out = new HashMap<>();
        for (DownloadItem i : items) {
            if (i.duplicate && i.duplicateOfIndex > 0) {
                out.computeIfAbsent(i.duplicateOfIndex, k -> new ArrayList<>()).add(i.index);
            }
        }
        return out;
    }

    private static String titlePrefix(int index, List<Integer> duplicates, boolean multi) {
        if (!multi) return "";
        if (duplicates == null || duplicates.isEmpty()) return "그림" + index + "_";
        StringBuilder b = new StringBuilder("(그림").append(index);
        for (Integer n : duplicates) b.append(", ").append(n);
        return b.append(")_").toString();
    }

    private static JSONObject parseMetaLine(String line) {
        if (line == null) return null;
        int at = line.indexOf("PDCLOCKOUT_META:");
        if (at < 0) return null;
        String json = line.substring(at + "PDCLOCKOUT_META:".length()).trim();
        try { return new JSONObject(json); } catch (Exception ignored) { return null; }
    }

    private static JSONObject parseMetaBlock(String out) {
        if (out == null) return null;
        JSONObject found = null;
        for (String line : out.split("\\R")) {
            JSONObject x = parseMetaLine(line);
            if (x != null) found = x;
        }
        return found;
    }

    private static File findDownloadedFile(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        File best = null;
        for (File f : files) {
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase();
            if (n.endsWith(".part") || n.endsWith(".ytdl") || n.endsWith(".json") || n.contains(".__")) continue;
            if (best == null || f.length() > best.length()) best = f;
        }
        return best;
    }

    private static boolean coreRecoveryCandidate(Exception e) {
        String s = cleanError(e).toLowerCase();
        return s.contains("403") || s.contains("signature") || s.contains("nsig") || s.contains("extractor") ||
                s.contains("javascript") || s.contains("player") || s.contains("format") || s.contains("unable to extract");
    }

    private static String guessedExtractor(String url) {
        if (UrlTools.isYouTube(url)) return "Youtube";
        if (UrlTools.isInstagram(url)) return "Instagram";
        if (UrlTools.isFacebook(url)) return "Facebook";
        if (UrlTools.isTikTok(url)) return "TikTok";
        return "ETC";
    }

    private static String stripExtension(String n) {
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    private static Data errorData(String msg) {
        return new Data.Builder().putString(KEY_STATUS, msg).putString(KEY_LOG, "[오류] " + msg).build();
    }

    private static String cleanError(Throwable e) {
        String s = e == null ? "알 수 없는 오류" : (e.getMessage() == null ? e.toString() : e.getMessage());
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 600 ? s.substring(s.length() - 600) : s;
    }

    private static String compact(String s) {
        if (s == null) return "";
        s = s.trim().replaceAll("\\s+", " ");
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static void deleteChildren(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) deleteTree(f);
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteTree(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static final class DownloadResult {
        final File file;
        final JSONObject meta;
        DownloadResult(File file, JSONObject meta) { this.file = file; this.meta = meta; }
    }
}
