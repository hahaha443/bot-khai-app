package com.hihu.donatefloat;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import rikka.shizuku.Shizuku;

public class RamCpuActivity extends AppCompatActivity {

    private static final int SHIZUKU_REQUEST_CODE = 2001;
    private static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";
    private static final long AUTO_REFRESH_INTERVAL_MS = 5000;

    private enum Tab { USER, SYSTEM }

    private TextView statusText;
    private TextView emptyText;
    private Button btnGrant;
    private Button btnBatchStop;
    private EditText editSearch;
    private CheckBox checkAutoRefresh;
    private RecyclerView recyclerView;
    private AppUsageAdapter adapter;

    private final List<RamCpuChecker.AppUsage> allData = new ArrayList<>();   // toàn bộ, chưa lọc
    private final List<RamCpuChecker.AppUsage> shownData = new ArrayList<>(); // đang hiển thị sau khi lọc

    private RamCpuChecker.SortBy currentSort = RamCpuChecker.SortBy.RAM;
    private Tab currentTab = Tab.USER;
    private boolean isLoading = false;

    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadData();
            autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
        }
    };

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
        btnBatchStop = findViewById(R.id.btnBatchStop);
        editSearch = findViewById(R.id.editSearch);
        checkAutoRefresh = findViewById(R.id.checkAutoRefresh);
        recyclerView = findViewById(R.id.recyclerAppUsage);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppUsageAdapter(shownData, this::confirmForceStop, this::onSelectionChanged);
        recyclerView.setAdapter(adapter);

        btnGrant.setOnClickListener(v -> handleGrantClick());
        btnBatchStop.setOnClickListener(v -> confirmBatchStop());

        findViewById(R.id.btnRefreshRamCpu).setOnClickListener(v -> loadData());
        findViewById(R.id.btnSortRam).setOnClickListener(v -> { currentSort = RamCpuChecker.SortBy.RAM; loadData(); });
        findViewById(R.id.btnSortCpu).setOnClickListener(v -> { currentSort = RamCpuChecker.SortBy.CPU; loadData(); });

        findViewById(R.id.btnTabUser).setOnClickListener(v -> {
            currentTab = Tab.USER;
            adapter.clearSelection();
            applyFilters();
        });
        findViewById(R.id.btnTabSystem).setOnClickListener(v -> {
            currentTab = Tab.SYSTEM;
            adapter.clearSelection();
            applyFilters();
        });

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilters(); }
        });

        checkAutoRefresh.setOnCheckedChangeListener((btn, checked) -> {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            if (checked) autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL_MS);
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
    protected void onPause() {
        super.onPause();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener);
        } catch (Throwable ignored) {
        }
    }

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
            if (allData.isEmpty()) loadData();
        }
    }

    private void loadData() {
        if (!RamCpuChecker.isReady()) {
            Toast.makeText(this, "Chưa có quyền Shizuku", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isLoading) return;
        isLoading = true;

        new Thread(() -> {
            try {
                List<RamCpuChecker.AppUsage> list = RamCpuChecker.getUsageList(this, 200, currentSort);
                runOnUiThread(() -> {
                    allData.clear();
                    allData.addAll(list);
                    applyFilters();
                    isLoading = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "❌ Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    isLoading = false;
                });
            }
        }).start();
    }

    /** Lọc allData theo tab (user/system) + từ khoá tìm kiếm, rồi hiển thị lên list. */
    private void applyFilters() {
        String query = editSearch.getText().toString().trim().toLowerCase(Locale.getDefault());

        List<RamCpuChecker.AppUsage> filtered = new ArrayList<>();
        for (RamCpuChecker.AppUsage a : allData) {
            boolean matchesTab = currentTab == Tab.USER ? !a.isSystemApp : a.isSystemApp;
            if (!matchesTab) continue;
            boolean matchesQuery = query.isEmpty()
                    || a.label.toLowerCase(Locale.getDefault()).contains(query)
                    || a.packageName.toLowerCase(Locale.getDefault()).contains(query);
            if (matchesQuery) filtered.add(a);
        }

        shownData.clear();
        shownData.addAll(filtered);
        adapter.notifyDataSetChanged();
        adapter.pruneSelection();
        emptyText.setVisibility(shownData.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void onSelectionChanged(Set<String> selected) {
        int n = selected.size();
        if (n == 0) {
            btnBatchStop.setVisibility(View.GONE);
        } else {
            btnBatchStop.setVisibility(View.VISIBLE);
            btnBatchStop.setText(String.format(Locale.getDefault(), "🛑 Buộc dừng đã chọn (%d)", n));
        }
    }

    private void confirmForceStop(RamCpuChecker.AppUsage app) {
        String warning = app.isSystemApp
                ? "\n\n⚠️ Đây là app/tiến trình HỆ THỐNG — buộc dừng có thể gây treo hoặc khởi động lại máy."
                : "";
        new AlertDialog.Builder(this)
                .setTitle("Buộc dừng " + app.label + "?")
                .setMessage(app.packageName + warning)
                .setPositiveButton("Buộc dừng", (d, w) -> doForceStop(java.util.Collections.singletonList(app.packageName)))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void confirmBatchStop() {
        Set<String> selected = adapter.getSelected();
        if (selected.isEmpty()) return;

        boolean anySystem = false;
        for (RamCpuChecker.AppUsage a : allData) {
            if (selected.contains(a.packageName) && a.isSystemApp) { anySystem = true; break; }
        }
        String warning = anySystem
                ? "\n\n⚠️ Trong danh sách đã chọn có app HỆ THỐNG — buộc dừng có thể gây treo hoặc khởi động lại máy."
                : "";

        new AlertDialog.Builder(this)
                .setTitle("Buộc dừng " + selected.size() + " app đã chọn?")
                .setMessage("Sẽ dừng toàn bộ app trong danh sách đã tick." + warning)
                .setPositiveButton("Buộc dừng tất cả", (d, w) -> doForceStop(new ArrayList<>(selected)))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void doForceStop(List<String> packages) {
        new Thread(() -> {
            int okCount = 0;
            for (String pkg : packages) {
                try {
                    RamCpuChecker.forceStopApp(pkg);
                    okCount++;
                } catch (Exception ignored) {
                }
            }
            int finalOk = okCount;
            runOnUiThread(() -> {
                Toast.makeText(this, "Đã buộc dừng " + finalOk + "/" + packages.size() + " app", Toast.LENGTH_SHORT).show();
                adapter.clearSelection();
                loadData();
            });
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
