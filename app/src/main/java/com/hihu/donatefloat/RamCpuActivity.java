package com.hihu.donatefloat;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public class RamCpuActivity extends AppCompatActivity {

    private static final int SHIZUKU_REQUEST_CODE = 2001;
    private static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";

    private TextView statusText;
    private TextView emptyText;
    private Button btnGrant;
    private RecyclerView recyclerView;
    private AppUsageAdapter adapter;
    private final List<RamCpuChecker.AppUsage> data = new ArrayList<>();
    private RamCpuChecker.SortBy currentSort = RamCpuChecker.SortBy.RAM;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_REQUEST_CODE) runOnUiThread(this::refreshUi);
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ram_cpu);

        statusText = findViewById(R.id.textShizukuStatus);
        emptyText = findViewById(R.id.textEmpty);
        btnGrant = findViewById(R.id.btnGrantShizuku);
        recyclerView = findViewById(R.id.recyclerAppUsage);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppUsageAdapter(data, this::confirmForceStop);
        recyclerView.setAdapter(adapter);

        btnGrant.setOnClickListener(v -> handleGrantClick());

        findViewById(R.id.btnRefreshRamCpu).setOnClickListener(v -> loadData());
        findViewById(R.id.btnSortRam).setOnClickListener(v -> {
            currentSort = RamCpuChecker.SortBy.RAM;
            loadData();
        });
        findViewById(R.id.btnSortCpu).setOnClickListener(v -> {
            currentSort = RamCpuChecker.SortBy.CPU;
            loadData();
        });

        try {
            Shizuku.addRequestPermissionResultListener(permissionListener);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener);
        } catch (Throwable ignored) {
        }
    }

    /** Dẫn thẳng người dùng qua đúng bước cần làm tiếp theo, tùy trạng thái hiện tại. */
    private void handleGrantClick() {
        if (!RamCpuChecker.isShizukuAppInstalled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Chưa cài Shizuku")
                    .setMessage("Cần cài app Shizuku (miễn phí) để đọc RAM/CPU của app khác " +
                            "mà không cần root máy.\n\nSau khi cài xong, quay lại đây bấm nút này lần nữa.")
                    .setPositiveButton("Mở trang cài Shizuku", (d, w) -> openShizukuInstallPage())
                    .setNegativeButton("Để sau", null)
                    .show();
            return;
        }

        if (!RamCpuChecker.isShizukuInstalledAndRunning()) {
            new AlertDialog.Builder(this)
                    .setTitle("Shizuku chưa được bật")
                    .setMessage("Đã cài Shizuku nhưng service chưa chạy. Cần mở app Shizuku và " +
                            "kích hoạt nó (qua ADB dây USB, hoặc Wireless debugging trên Android 11+) " +
                            "— chỉ cần làm 1 lần.\n\nBấm OK để mở app Shizuku.")
                    .setPositiveButton("Mở app Shizuku", (d, w) -> openShizukuApp())
                    .setNegativeButton("Để sau", null)
                    .show();
            return;
        }

        // Đã cài + đang chạy -> xin quyền trực tiếp, hệ thống sẽ tự hiện dialog cấp quyền
        RamCpuChecker.requestPermission(SHIZUKU_REQUEST_CODE);
    }

    private void refreshUi() {
        if (!RamCpuChecker.isShizukuAppInstalled(this)) {
            statusText.setText("❌ Chưa cài Shizuku trên máy");
            btnGrant.setText("Cài đặt Shizuku");
        } else if (!RamCpuChecker.isShizukuInstalledAndRunning()) {
            statusText.setText("⚠️ Đã cài Shizuku nhưng chưa bật service");
            btnGrant.setText("Mở Shizuku để bật");
        } else if (!RamCpuChecker.isReady()) {
            statusText.setText("🔒 Shizuku đang chạy — cần cấp quyền cho app này");
            btnGrant.setText("Cấp quyền Shizuku");
        } else {
            statusText.setText("✅ Đã sẵn sàng");
            btnGrant.setText("Đã cấp quyền ✓");
            loadData();
        }
    }

    private void loadData() {
        if (!RamCpuChecker.isReady()) {
            Toast.makeText(this, "Chưa có quyền Shizuku", Toast.LENGTH_SHORT).show();
            return;
        }
        emptyText.setVisibility(android.view.View.GONE);

        new Thread(() -> {
            try {
                List<RamCpuChecker.AppUsage> list = RamCpuChecker.getUsageList(this, 30, currentSort);
                runOnUiThread(() -> {
                    data.clear();
                    data.addAll(list);
                    adapter.notifyDataSetChanged();
                    emptyText.setVisibility(data.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "❌ Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void confirmForceStop(RamCpuChecker.AppUsage app) {
        String warning = app.isSystemApp
                ? "\n\n⚠️ Đây là app/tiến trình HỆ THỐNG — buộc dừng có thể gây treo hoặc khởi động lại máy."
                : "";
        new AlertDialog.Builder(this)
                .setTitle("Buộc dừng " + app.label + "?")
                .setMessage(app.packageName + warning)
                .setPositiveButton("Buộc dừng", (d, w) -> doForceStop(app))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void doForceStop(RamCpuChecker.AppUsage app) {
        new Thread(() -> {
            try {
                RamCpuChecker.forceStopApp(app.packageName);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Đã buộc dừng " + app.label, Toast.LENGTH_SHORT).show();
                    loadData();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "❌ Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void openShizukuApp() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PKG);
        if (launch != null) {
            startActivity(launch);
        } else {
            openShizukuInstallPage();
        }
    }

    private void openShizukuInstallPage() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + SHIZUKU_PKG)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + SHIZUKU_PKG)));
        }
    }
}
