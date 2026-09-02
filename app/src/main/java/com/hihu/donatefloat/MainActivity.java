package com.hihu.donatefloat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;
    private boolean overlayPrompted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText serverUrlEdit = findViewById(R.id.editServerUrl);
        EditText apiTokenEdit = findViewById(R.id.editApiToken);
        serverUrlEdit.setText(Prefs.serverUrl(this));
        apiTokenEdit.setText(Prefs.apiToken(this));

        findViewById(R.id.btnSaveConfig).setOnClickListener(v -> {
            Prefs.setServerUrl(this, serverUrlEdit.getText().toString().trim());
            Prefs.setApiToken(this, apiTokenEdit.getText().toString().trim());
            Toast.makeText(this, "Đã lưu cấu hình", Toast.LENGTH_SHORT).show();
        });

        statusText = findViewById(R.id.textStatus);

        CheckBox autoStart = findViewById(R.id.checkAutoStart);
        autoStart.setChecked(Prefs.autoStartMenus(this));
        autoStart.setOnCheckedChangeListener((btn, checked) -> Prefs.setAutoStartMenus(this, checked));

        CheckBox lockedBox = findViewById(R.id.checkLocked);
        lockedBox.setChecked(Prefs.locked(this));
        lockedBox.setOnCheckedChangeListener((btn, checked) -> {
            Prefs.setLocked(this, checked);
            FloatingReportService.updateLocked(this);
            FloatingQRService.updateLocked(this);
            Toast.makeText(this, checked ? "Đã khoá — 2 menu sẽ không nhận chạm nữa" : "Đã mở khoá",
                    Toast.LENGTH_SHORT).show();
        });

        // ─── Bật/tắt menu ───
        findViewById(R.id.btnToggleReport).setOnClickListener(v -> {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "Chưa cấp quyền hiển thị nổi", Toast.LENGTH_SHORT).show();
                checkAndRequestOverlay();
                return;
            }
            toggleService(FloatingReportService.class, FloatingReportService.isRunning());
        });

        findViewById(R.id.btnToggleQR).setOnClickListener(v -> {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "Chưa cấp quyền hiển thị nổi", Toast.LENGTH_SHORT).show();
                checkAndRequestOverlay();
                return;
            }
            toggleService(FloatingQRService.class, FloatingQRService.isRunning());
        });

        // ─── Kích thước ───
        SeekBar reportSize = findViewById(R.id.seekReportSize);
        reportSize.setProgress(Prefs.reportSizeDp(this));
        reportSize.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setReportSizeDp(this, Math.max(v, 60));
            FloatingReportService.updateSize(this);
        }));

        SeekBar qrSize = findViewById(R.id.seekQrSize);
        qrSize.setProgress(Prefs.qrSizeDp(this));
        qrSize.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setQrSizeDp(this, Math.max(v, 60));
            FloatingQRService.updateSize(this);
        }));

        // ─── Độ mờ ───
        SeekBar reportBgOpacity = findViewById(R.id.seekReportBgOpacity);
        reportBgOpacity.setProgress(Prefs.reportBgOpacity(this));
        reportBgOpacity.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setReportBgOpacity(this, v);
            FloatingReportService.updateBgOpacity(this);
        }));

        SeekBar reportContentOpacity = findViewById(R.id.seekReportContentOpacity);
        reportContentOpacity.setProgress(Prefs.reportContentOpacity(this));
        reportContentOpacity.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setReportContentOpacity(this, v);
            FloatingReportService.updateContentOpacity(this);
        }));

        SeekBar qrOpacity = findViewById(R.id.seekQrOpacity);
        qrOpacity.setProgress(Prefs.qrOpacity(this));
        qrOpacity.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setQrOpacity(this, Math.max(v, 20));
            FloatingQRService.updateOpacity(this);
        }));

        SeekBar alertOpacity = findViewById(R.id.seekAlertOpacity);
        alertOpacity.setProgress(Prefs.alertOpacity(this));
        alertOpacity.setOnSeekBarChangeListener(new SimpleSeek(v ->
                Prefs.setAlertOpacity(this, Math.max(v, 20))));

        // ─── Demo test ───
        findViewById(R.id.btnDemo).setOnClickListener(v -> {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "Chưa cấp quyền hiển thị nổi", Toast.LENGTH_SHORT).show();
                checkAndRequestOverlay();
                return;
            }
            if (!FloatingReportService.isRunning()) {
                ContextCompat.startForegroundService(this, new Intent(this, FloatingReportService.class));
            }
            if (!FloatingQRService.isRunning()) {
                ContextCompat.startForegroundService(this, new Intent(this, FloatingQRService.class));
            }
            new Handler(Looper.getMainLooper()).postDelayed(FloatingReportService::injectDemo, 600);
            Toast.makeText(this, "Đã bơm dữ liệu demo — kéo/chỉnh 2 bảng nổi để canh cho ưng", Toast.LENGTH_LONG).show();
        });

        // ─── Thống kê ───
        findViewById(R.id.btnRefreshStats).setOnClickListener(v -> refreshStats());

        // ─── Màu chữ ───
        wireColorSwatch(R.id.colorWhite, 0xFFFFFFFF);
        wireColorSwatch(R.id.colorYellow, 0xFFFFEB3B);
        wireColorSwatch(R.id.colorCyan, 0xFF00E5FF);
        wireColorSwatch(R.id.colorGreen, 0xFF4CAF50);
        wireColorSwatch(R.id.colorPink, 0xFFFF4081);
        wireColorSwatch(R.id.colorOrange, 0xFFFF9800);
        wireColorSwatch(R.id.colorRed, 0xFFF44336);

        // ─── Menu Ghi chú (menu rời #3, nhiều ghi chú) ───
        findViewById(R.id.btnAddNote).setOnClickListener(v -> {
            EditText noteEdit = findViewById(R.id.editNoteText);
            String text = noteEdit.getText().toString().trim();
            if (text.isEmpty()) return;
            addNoteToList(text);
            noteEdit.setText("");
            renderNoteList();
            FloatingNoteService.rebuild(this);
        });

        findViewById(R.id.btnToggleNote).setOnClickListener(v -> {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "Chưa cấp quyền hiển thị nổi", Toast.LENGTH_SHORT).show();
                checkAndRequestOverlay();
                return;
            }
            toggleService(FloatingNoteService.class, FloatingNoteService.isRunning());
        });

        SeekBar noteSize = findViewById(R.id.seekNoteSize);
        noteSize.setProgress(Prefs.noteSizeDp(this));
        noteSize.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setNoteSizeDp(this, Math.max(v, 60));
            FloatingNoteService.updateSize(this);
        }));

        SeekBar noteOpacity = findViewById(R.id.seekNoteOpacity);
        noteOpacity.setProgress(Prefs.noteOpacity(this));
        noteOpacity.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setNoteOpacity(this, v);
            FloatingNoteService.updateOpacity(this);
        }));

        renderNoteList();

        // ─── Menu Thanh đo mục tiêu (menu rời #4) ───
        EditText goalTitleEdit = findViewById(R.id.editGoalTitle);
        EditText goalAmountEdit = findViewById(R.id.editGoalAmount);
        goalTitleEdit.setText(Prefs.goalTitle(this));
        goalAmountEdit.setText(String.valueOf(Prefs.goalAmount(this)));

        findViewById(R.id.btnSaveGoal).setOnClickListener(v -> {
            Prefs.setGoalTitle(this, goalTitleEdit.getText().toString().trim());
            try {
                Prefs.setGoalAmount(this, Long.parseLong(goalAmountEdit.getText().toString().trim()));
            } catch (NumberFormatException ignored) {}
            FloatingGoalService.updateConfig(this);
            Toast.makeText(this, "Đã lưu mục tiêu", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnToggleGoal).setOnClickListener(v -> {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "Chưa cấp quyền hiển thị nổi", Toast.LENGTH_SHORT).show();
                checkAndRequestOverlay();
                return;
            }
            toggleService(FloatingGoalService.class, FloatingGoalService.isRunning());
        });

        SeekBar goalSize = findViewById(R.id.seekGoalSize);
        goalSize.setProgress(Prefs.goalSizeDp(this));
        goalSize.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setGoalSizeDp(this, Math.max(v, 80));
            FloatingGoalService.updateSize(this);
        }));

        SeekBar goalOpacity = findViewById(R.id.seekGoalOpacity);
        goalOpacity.setProgress(Prefs.goalOpacity(this));
        goalOpacity.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setGoalOpacity(this, v);
            FloatingGoalService.updateOpacity(this);
        }));

        // ─── Lịch sử ───
        findViewById(R.id.btnHistory).setOnClickListener(v ->
                startActivity(new Intent(this, TransactionHistoryActivity.class)));

        refreshStatus();
        refreshStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        checkAndRequestOverlay();
        checkAndRequestNotif();

        if (Prefs.autoStartMenus(this) && canDrawOverlays()) {
            if (!FloatingReportService.isRunning()) {
                ContextCompat.startForegroundService(this, new Intent(this, FloatingReportService.class));
            }
            if (!FloatingQRService.isRunning()) {
                ContextCompat.startForegroundService(this, new Intent(this, FloatingQRService.class));
            }
            if (!FloatingNoteService.isRunning()) {
                ContextCompat.startForegroundService(this, new Intent(this, FloatingNoteService.class));
            }
            if (!FloatingGoalService.isRunning()) {
                ContextCompat.startForegroundService(this, new Intent(this, FloatingGoalService.class));
            }
        }
    }

    private void refreshStatus() {
        statusText.setText("Quyền hiển thị nổi: " + (canDrawOverlays() ? "ĐÃ CẤP" : "CHƯA CẤP"));
    }

    /** Tự check + tự dẫn thẳng qua màn Settings, không cần bấm nút gì. */
    private void checkAndRequestOverlay() {
        if (!canDrawOverlays() && !overlayPrompted) {
            overlayPrompted = true;
            Toast.makeText(this, "Cần cấp quyền hiển thị nổi để dùng 2 menu", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void checkAndRequestNotif() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void toggleService(Class<?> cls, boolean running) {
        Intent intent = new Intent(this, cls);
        if (running) {
            stopService(intent);
        } else {
            ContextCompat.startForegroundService(this, intent);
        }
    }

    private void refreshStats() {
        fetchStatOne("today", R.id.textStatToday, "Hôm nay");
        fetchStatOne("yesterday", R.id.textStatYesterday, "Hôm qua");
        fetchStatOne("2d_ago", R.id.textStatBeforeYesterday, "Hôm kia");
    }

    private void fetchStatOne(String period, int viewId, String fallbackLabel) {
        ApiClient.getStats(this, period, new ApiClient.Callback<ApiClient.Stats>() {
            @Override
            public void onSuccess(ApiClient.Stats result) {
                runOnUiThread(() -> {
                    TextView tv = findViewById(viewId);
                    tv.setText(String.format("%s: %,d đ (%d giao dịch)", result.label, result.total, result.count));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    TextView tv = findViewById(viewId);
                    tv.setText(fallbackLabel + ": lỗi tải (" + message + ")");
                });
            }
        });
    }

    private void wireColorSwatch(int viewId, int color) {
        View v = findViewById(viewId);
        v.setOnClickListener(x -> {
            Prefs.setTextColor(this, color);
            FloatingReportService.updateTextColor(this);
            FloatingNoteService.updateTextColor(this);
            FloatingGoalService.updateConfig(this);
        });
    }

    /** Ghi chú lưu dạng JSON [{id,text}] trong Prefs — dùng org.json có sẵn. */
    private org.json.JSONArray loadNotesJson() {
        try {
            return new org.json.JSONArray(Prefs.notesListRaw(this));
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    private void addNoteToList(String text) {
        org.json.JSONArray arr = loadNotesJson();
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("id", String.valueOf(System.currentTimeMillis()));
            o.put("text", text);
            arr.put(o);
            Prefs.setNotesListRaw(this, arr.toString());
        } catch (Exception ignored) {}
    }

    private void removeNoteFromList(String id) {
        org.json.JSONArray arr = loadNotesJson();
        org.json.JSONArray result = new org.json.JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            try {
                org.json.JSONObject o = arr.getJSONObject(i);
                if (!o.getString("id").equals(id)) result.put(o);
            } catch (Exception ignored) {}
        }
        Prefs.setNotesListRaw(this, result.toString());
    }

    private void renderNoteList() {
        LinearLayout container = findViewById(R.id.noteListContainer);
        container.removeAllViews();
        org.json.JSONArray arr = loadNotesJson();
        for (int i = 0; i < arr.length(); i++) {
            try {
                org.json.JSONObject o = arr.getJSONObject(i);
                String id = o.getString("id");
                String text = o.getString("text");

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 8, 0, 8);

                TextView tv = new TextView(this);
                tv.setText(text);
                tv.setTextSize(12);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                Button del = new Button(this);
                del.setText("Xoá");
                del.setTextSize(10);
                del.setOnClickListener(v -> {
                    removeNoteFromList(id);
                    renderNoteList();
                    FloatingNoteService.rebuild(this);
                });

                row.addView(tv);
                row.addView(del);
                container.addView(row);
            } catch (Exception ignored) {}
        }
    }

    private interface OnValue {
        void run(int v);
    }

    private static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        private final OnValue onValue;

        SimpleSeek(OnValue onValue) {
            this.onValue = onValue;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) onValue.run(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
