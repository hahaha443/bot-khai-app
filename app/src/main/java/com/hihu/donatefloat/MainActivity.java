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
    private boolean notifPrompted = false;

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

        TextView lockHint = findViewById(R.id.textLockHint);
        lockHint.setText("🔒 Chạm liên tiếp 4 lần lên đỉnh 1 menu nổi để khoá/mở khoá riêng menu đó (không ảnh hưởng menu khác).");

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

        // ─── Màu chữ Báo cáo (riêng) ───
        findViewById(R.id.btnColorReport).setOnClickListener(v -> showFullColorPicker(
                Prefs.reportTextColor(this), c -> {
                    Prefs.setReportTextColor(this, c);
                    FloatingReportService.updateTextColor(this);
                }));

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

        findViewById(R.id.btnColorNote).setOnClickListener(v -> showFullColorPicker(
                Prefs.noteTextColor(this), c -> {
                    Prefs.setNoteTextColor(this, c);
                    FloatingNoteService.updateTextColor(this);
                }));

        SeekBar noteBgOpacity = findViewById(R.id.seekNoteBgOpacity);
        noteBgOpacity.setProgress(Prefs.noteBgOpacity(this));
        noteBgOpacity.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setNoteBgOpacity(this, v);
            FloatingNoteService.updateBgOpacity(this);
        }));

        SeekBar noteContentOpacity = findViewById(R.id.seekNoteContentOpacity);
        noteContentOpacity.setProgress(Prefs.noteContentOpacity(this));
        noteContentOpacity.setOnSeekBarChangeListener(new SimpleSeek(v -> {
            Prefs.setNoteContentOpacity(this, v);
            FloatingNoteService.updateContentOpacity(this);
        }));

        renderNoteList();

        // ─── Menu Thanh đo mục tiêu (menu rời #4) ───
        EditText goalTitleEdit = findViewById(R.id.editGoalTitle);
        EditText goalAmountEdit = findViewById(R.id.editGoalAmount);
        EditText goalNoteEdit = findViewById(R.id.editGoalNote);
        goalTitleEdit.setText(Prefs.goalTitle(this));
        goalAmountEdit.setText(String.valueOf(Prefs.goalAmount(this)));
        goalNoteEdit.setText(Prefs.goalNote(this));

        findViewById(R.id.btnSaveGoal).setOnClickListener(v -> {
            Prefs.setGoalTitle(this, goalTitleEdit.getText().toString().trim());
            Prefs.setGoalNote(this, goalNoteEdit.getText().toString().trim());
            try {
                Prefs.setGoalAmount(this, Long.parseLong(goalAmountEdit.getText().toString().trim()));
            } catch (NumberFormatException ignored) {}
            FloatingGoalService.updateConfig(this);
            Toast.makeText(this, "Đã lưu mục tiêu", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnColorGoal).setOnClickListener(v -> showFullColorPicker(
                Prefs.goalTextColor(this), c -> {
                    Prefs.setGoalTextColor(this, c);
                    FloatingGoalService.updateConfig(this);
                }));

        findViewById(R.id.btnToggleGoal).setOnClickListener(v -> {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "Chưa cấp quyền hiển thị nổi", Toast.LENGTH_SHORT).show();
                checkAndRequestOverlay();
                return;
            }
            toggleService(FloatingGoalService.class, FloatingGoalService.isRunning());
        });

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
        if (!notifPrompted) {
            notifPrompted = true;
            requestNotifThenOverlay();
        } else {
            checkAndRequestOverlay();
        }

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

    /** Xin quyền THÔNG BÁO trước — chỉ sau khi có kết quả (đồng ý/từ chối)
     * mới chuyển tiếp qua xin quyền HIỂN THỊ NỔI, đúng thứ tự yêu cầu. */
    private void requestNotifThenOverlay() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            // checkAndRequestOverlay() sẽ được gọi tiếp trong onRequestPermissionsResult
        } else {
            checkAndRequestOverlay();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            checkAndRequestOverlay();
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
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dpToPx(8));
        v.setBackground(bg);
    }

    private interface OnColorChosen { void onChosen(int color); }

    /** Bảng chọn màu HSV liên tục — hơn 16 triệu màu (256^3), không giới
     * hạn vài màu định sẵn nữa. Dùng chung cho Báo cáo/Ghi chú/Mục tiêu,
     * mỗi nơi tự lưu màu RIÊNG của mình. */
    private void showFullColorPicker(int initialColor, OnColorChosen onChosen) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(initialColor, hsv);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(16);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(240)));

        HsvSquareView square = new HsvSquareView(this);
        square.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 5));
        square.setHue(hsv[0]);
        square.setSatVal(hsv[1], hsv[2]);

        HueBarView hueBar = new HueBarView(this);
        LinearLayout.LayoutParams hueLp = new LinearLayout.LayoutParams(dpToPx(40), LinearLayout.LayoutParams.MATCH_PARENT);
        hueLp.leftMargin = dpToPx(8);
        hueBar.setLayoutParams(hueLp);
        hueBar.setHue(hsv[0]);

        row.addView(square);
        row.addView(hueBar);
        root.addView(row);

        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams previewRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        previewRowLp.topMargin = dpToPx(14);
        previewRow.setLayoutParams(previewRowLp);

        View preview = new View(this);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48));
        preview.setLayoutParams(previewLp);
        android.graphics.drawable.GradientDrawable previewBg = new android.graphics.drawable.GradientDrawable();
        previewBg.setCornerRadius(dpToPx(8));
        previewBg.setColor(android.graphics.Color.HSVToColor(hsv));
        preview.setBackground(previewBg);

        TextView hexView = new TextView(this);
        hexView.setPadding(dpToPx(12), 0, 0, 0);
        hexView.setTextSize(15);
        hexView.setText(String.format("#%06X", (0xFFFFFF & android.graphics.Color.HSVToColor(hsv))));

        previewRow.addView(preview);
        previewRow.addView(hexView);
        root.addView(previewRow);

        Runnable updatePreview = () -> {
            int c = android.graphics.Color.HSVToColor(hsv);
            previewBg.setColor(c);
            hexView.setText(String.format("#%06X", (0xFFFFFF & c)));
        };

        hueBar.setListener(h -> {
            hsv[0] = h;
            square.setHue(h);
            updatePreview.run();
        });
        square.setListener((s, v) -> {
            hsv[1] = s;
            hsv[2] = v;
            updatePreview.run();
        });

        new android.app.AlertDialog.Builder(this)
                .setTitle("Chọn màu chữ")
                .setView(root)
                .setPositiveButton("Chọn", (d, w) -> onChosen.onChosen(android.graphics.Color.HSVToColor(hsv)))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
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
