package com.pdclockout.helper;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.pdclockout.helper.download.DownloadItem;
import com.pdclockout.helper.download.DownloadMode;
import com.pdclockout.helper.download.DownloadWorker;
import com.pdclockout.helper.download.Engine;
import com.pdclockout.helper.download.JobCodec;
import com.pdclockout.helper.download.UrlTools;
import com.yausername.youtubedl_android.YoutubeDL;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private EditText inputUrls;
    private Spinner spinnerMode;
    private TextView txtAnalysis;
    private TextView txtStatus;
    private TextView txtLog;
    private ProgressBar progress;
    private Button btnDownload;
    private Button btnCancel;
    private Button btnUpdateCore;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private List<DownloadItem> analyzedItems = new ArrayList<>();
    private String analyzedInput = "";
    private String lastLog = "";

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupModeSpinner();
        setupButtons();
        consumeShareIntent(getIntent());
        requestNotificationPermissionIfNeeded();
        appendLog("[준비] V3.4.9 다운로더 정책 기반 Android Lite");
        appendLog("[저장] Download/PD칼퇴도우미");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeShareIntent(intent);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        inputUrls = findViewById(R.id.inputUrls);
        spinnerMode = findViewById(R.id.spinnerMode);
        txtAnalysis = findViewById(R.id.txtAnalysis);
        txtStatus = findViewById(R.id.txtStatus);
        txtLog = findViewById(R.id.txtLog);
        progress = findViewById(R.id.progress);
        btnDownload = findViewById(R.id.btnDownload);
        btnCancel = findViewById(R.id.btnCancel);
        btnUpdateCore = findViewById(R.id.btnUpdateCore);
    }

    private void setupModeSpinner() {
        List<String> labels = new ArrayList<>();
        for (DownloadMode m : DownloadMode.values()) labels.add(m.label);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(adapter);
        spinnerMode.setSelection(DownloadMode.P1080.ordinal());
    }

    private void setupButtons() {
        findViewById(R.id.btnPaste).setOnClickListener(v -> pasteClipboard());
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            inputUrls.setText("");
            analyzedItems.clear();
            analyzedInput = "";
            txtAnalysis.setText("링크 분석 전");
        });
        findViewById(R.id.btnAnalyze).setOnClickListener(v -> analyze());
        btnDownload.setOnClickListener(v -> startDownload());
        btnCancel.setOnClickListener(v -> {
            WorkManager.getInstance(this).cancelUniqueWork(DownloadWorker.WORK_NAME);
            txtStatus.setText("취소 요청 중…");
            appendLog("[취소 요청] 현재 다운로드 작업을 중단합니다.");
        });
        btnUpdateCore.setOnClickListener(v -> updateCore());
    }

    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text != null) inputUrls.setText(text);
    }

    private void consumeShareIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text == null || text.toString().isBlank()) return;
        String current = inputUrls == null ? "" : inputUrls.getText().toString().trim();
        String shared = text.toString().trim();
        inputUrls.setText(current.isEmpty() ? shared : current + "\n" + shared);
        if (inputUrls != null) inputUrls.setSelection(inputUrls.length());
    }

    private void analyze() {
        String raw = inputUrls.getText().toString().trim();
        analyzedItems = UrlTools.buildItems(raw);
        analyzedInput = raw;
        if (analyzedItems.isEmpty()) {
            txtAnalysis.setText("http/https URL을 찾지 못했습니다.");
            return;
        }

        int supported = 0, unsupported = 0, duplicates = 0;
        for (DownloadItem i : analyzedItems) {
            if (i.duplicate) duplicates++;
            else if (i.supported && i.error.isBlank()) supported++;
            else unsupported++;
        }
        StringBuilder b = new StringBuilder();
        b.append("총 ").append(analyzedItems.size()).append("개 · 지원 ").append(supported)
                .append(" · 미지원 ").append(unsupported).append(" · 중복 ").append(duplicates).append("\n");
        int show = Math.min(analyzedItems.size(), 12);
        for (int n = 0; n < show; n++) {
            DownloadItem i = analyzedItems.get(n);
            b.append("그림").append(i.index).append(" · ");
            if (i.duplicate) b.append("중복(그림").append(i.duplicateOfIndex).append(")");
            else if (!i.supported || !i.error.isBlank()) b.append("직접 다운로드 필요");
            else if (UrlTools.isYouTube(i.mediaUrl)) b.append("YouTube");
            else if (UrlTools.isInstagram(i.mediaUrl)) b.append("Instagram");
            else if (UrlTools.isFacebook(i.mediaUrl)) b.append("Facebook");
            else if (UrlTools.isTikTok(i.mediaUrl)) b.append("TikTok");
            b.append("\n");
        }
        if (analyzedItems.size() > show) b.append("… 외 ").append(analyzedItems.size() - show).append("개");
        txtAnalysis.setText(b.toString().trim());
        appendLog("[구성안 분석] " + analyzedItems.size() + "개 링크 · 미지원 링크도 그림 번호를 유지합니다.");
    }

    private void startDownload() {
        String raw = inputUrls.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "구성안 또는 URL을 붙여넣어 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!raw.equals(analyzedInput) || analyzedItems.isEmpty()) analyze();
        if (analyzedItems.isEmpty()) return;

        try {
            File job = JobCodec.write(new File(getFilesDir(), "jobs"), analyzedItems, spinnerMode.getSelectedItemPosition());
            Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            Data input = new Data.Builder().putString(DownloadWorker.KEY_JOB_PATH, job.getAbsolutePath()).build();
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                    .setInputData(input)
                    .setConstraints(constraints)
                    .addTag(DownloadWorker.WORK_NAME)
                    .build();

            WorkManager wm = WorkManager.getInstance(this);
            wm.enqueueUniqueWork(DownloadWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, req);
            observeWork(req);
            setRunning(true);
            progress.setProgress(0);
            txtStatus.setText("다운로드 대기 중…");
            appendLog("------------------------------------------------------------");
            appendLog("[일괄 다운로드 시작] " + analyzedItems.size() + "개 · " + DownloadMode.fromOrdinalSafe(spinnerMode.getSelectedItemPosition()).label);
        } catch (Exception e) {
            appendLog("[오류] " + e.getMessage());
            Toast.makeText(this, "작업 생성 실패", Toast.LENGTH_SHORT).show();
        }
    }

    private void observeWork(OneTimeWorkRequest req) {
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(req.getId()).observe(this, info -> {
            if (info == null) return;
            Data p = info.getProgress();
            int pct = p.getInt(DownloadWorker.KEY_PROGRESS, progress.getProgress());
            String status = p.getString(DownloadWorker.KEY_STATUS);
            String log = p.getString(DownloadWorker.KEY_LOG);
            progress.setProgress(pct);
            if (status != null && !status.isBlank()) txtStatus.setText(status);
            if (log != null && !log.isBlank() && !log.equals(lastLog)) {
                lastLog = log;
                appendLog(log);
            }

            if (info.getState().isFinished()) {
                setRunning(false);
                String finalStatus = info.getOutputData().getString(DownloadWorker.KEY_STATUS);
                if (finalStatus != null && !finalStatus.isBlank()) txtStatus.setText(finalStatus);
                if (info.getState() == WorkInfo.State.SUCCEEDED) {
                    progress.setProgress(100);
                    appendLog("[저장 완료] Download/PD칼퇴도우미");
                } else if (info.getState() == WorkInfo.State.CANCELLED) {
                    txtStatus.setText("다운로드 취소됨");
                    appendLog("[취소] 작업이 중단되었습니다.");
                } else {
                    String err = info.getOutputData().getString(DownloadWorker.KEY_LOG);
                    if (err != null && !err.isBlank()) appendLog(err);
                }
            }
        });
    }

    private void setRunning(boolean running) {
        btnDownload.setEnabled(!running);
        btnCancel.setEnabled(running);
        btnUpdateCore.setEnabled(!running);
        spinnerMode.setEnabled(!running);
    }

    private void updateCore() {
        btnUpdateCore.setEnabled(false);
        txtStatus.setText("yt-dlp nightly 업데이트 확인 중…");
        appendLog("[코어 점검] yt-dlp nightly 업데이트 확인…");
        executor.execute(() -> {
            try {
                Engine.ensureInitialized(this);
                YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(), YoutubeDL.UpdateChannel._NIGHTLY);
                runOnUiThread(() -> {
                    txtStatus.setText("yt-dlp 코어 업데이트 완료");
                    appendLog("[코어 점검] 완료");
                    btnUpdateCore.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    txtStatus.setText("코어 업데이트 실패");
                    appendLog("[코어 오류] " + e.getMessage());
                    btnUpdateCore.setEnabled(true);
                });
            }
        });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void appendLog(String line) {
        if (line == null || line.isBlank()) return;
        txtLog.append(line.trim() + "\n");
    }
}
