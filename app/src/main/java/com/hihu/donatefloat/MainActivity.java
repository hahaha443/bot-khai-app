package com.hihu.donatefloat;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;

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

        // Tự dẫn tới đúng màn hình cấp quyền — không bắt người dùng tự mò trong Cài đặt
        findViewById(R.id.btnGrantOverlay).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        findViewById(R.id.btnGrantNotif).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            } else {
                Toast.makeText(this, "Máy này không cần cấp quyền thông báo riêng", Toast.LENGTH_SHORT).show();
            }
        });

        SeekBar reportSize = findViewById(R.id.seekReportSize);
        reportSize.setProgress(Prefs.reportSizeDp(this));
        reportSize.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setReportSizeDp(this, v);
            FloatingReportService.updateSize(this);
        }));

        SeekBar qrSize = findViewById(R.id.seekQrSize);
        qrSize.setProgress(Prefs.qrSizeDp(this));
        qrSize.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setQrSizeDp(this, v);
            FloatingQRService.updateSize(this);
        }));

        findViewById(R.id.btnToggleReport).setOnClickListener(v -> {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "Cấp quyền hiển thị nổi trước đã", Toast.LENGTH_SHORT).show();
                return;
            }
            toggleService(FloatingReportService.class, FloatingReportService.isRunning());
        });

        findViewById(R.id.btnToggleQR).setOnClickListener(v -> {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "Cấp quyền hiển thị nổi trước đã", Toast.LENGTH_SHORT).show();
                return;
            }
            toggleService(FloatingQRService.class, FloatingQRService.isRunning());
        });

        statusText = findViewById(R.id.textStatus);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        statusText.setText(" thị nổi: " + (canDrawOverlays() ? "ĐÃ CẤP" : "CHƯA CẤP"));
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
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            onValue.run(seekBar.getProgress());
        }
    }
}
