package com.hihu.donatefloat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
