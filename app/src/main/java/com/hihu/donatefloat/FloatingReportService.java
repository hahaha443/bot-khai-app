package com.hihu.donatefloat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Floating menu #1: danh sách giao dịch. Mỗi dòng tự biến mất sau 25s,
 * tối đa hiện 2 dòng cùng lúc (dòng cũ nhất bị đẩy ra khi có dòng thứ 3).
 * Độ mờ tách riêng: mờ NỀN (khung) và mờ NỘI DUNG (chữ), có thể mờ tới
 * trong suốt hẳn. Kéo góc dưới-phải để resize tự do, có thể khoá tương
 * tác để không đụng trúng lúc chơi game. */
public class FloatingReportService extends Service {

    private static final int MAX_VISIBLE_ROWS = 2;
    private static final long ROW_LIFETIME_MS = 25_000;

    private static boolean running = false;
    private static FloatingReportService instance;

    private WindowManager wm;
    private View floatView;
    private LinearLayout panel;
    private WindowManager.LayoutParams params;
    private LinearLayout list;
    private View alertView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poller = this::poll;

    public static boolean isRunning() { return running; }

    public static void updateSize(Context ctx) {
        if (instance != null) instance.applySize();
    }

    public static void updateBgOpacity(Context ctx) {
        if (instance != null) instance.applyBgOpacity();
    }

    public static void updateContentOpacity(Context ctx) {
        if (instance != null) instance.applyContentOpacity();
    }

    public static void updateLocked(Context ctx) {
        if (instance != null) instance.applyLocked();
    }

    public static void injectDemo() {
        if (instance != null) instance.doInjectDemo();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        startForegroundWithNotification();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_report, null);
        panel = floatView.findViewById(R.id.reportPanel);
        list = floatView.findViewById(R.id.reportList);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (Prefs.locked(this)) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        params = new WindowManager.LayoutParams(
                dp(Prefs.reportSizeDp(this)), dp(Prefs.reportHeightDp(this)),
                type, flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 100;

        floatView.findViewById(R.id.dragHandleReport)
                .setOnTouchListener(new DragTouchListener(params, wm, floatView));

        View resizeHandle = floatView.findViewById(R.id.resizeHandleReport);
        resizeHandle.setOnTouchListener(new ResizeTouchListener(params, wm, floatView, dp(60), (w, h) -> {
            Prefs.setReportSizeDp(this, pxToDp(w));
            Prefs.setReportHeightDp(this, pxToDp(h));
        }));

        wm.addView(floatView, params);
        applyBgOpacity();
        applyContentOpacity();
        applyLocked();
        handler.post(poller);
    }

    private void applySize() {
        if (params == null || floatView == null) return;
        params.width = dp(Prefs.reportSizeDp(this));
        params.height = dp(Prefs.reportHeightDp(this));
        wm.updateViewLayout(floatView, params);
    }

    /** Chỉ làm mờ NỀN (khung) — chữ/nội dung bên trong vẫn giữ nguyên độ rõ. */
    private void applyBgOpacity() {
        if (panel == null) return;
        Drawable bg = panel.getBackground();
        if (bg != null) {
            bg.mutate().setAlpha((int) (Prefs.reportBgOpacity(this) / 100f * 255));
        }
    }

    /** Chỉ làm mờ NỘI DUNG (từng dòng chữ) — nền/khung vẫn giữ nguyên độ rõ. */
    private void applyContentOpacity() {
        if (list == null) return;
        float alpha = Prefs.reportContentOpacity(this) / 100f;
        for (int i = 0; i < list.getChildCount(); i++) {
            list.getChildAt(i).setAlpha(alpha);
        }
    }

    private void applyLocked() {
        if (params == null || floatView == null || wm == null) return;
        boolean locked = Prefs.locked(this);
        if (locked) {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        wm.updateViewLayout(floatView, params);
        TextView handle = floatView.findViewById(R.id.dragHandleReport);
        if (handle != null) {
            handle.setText(locked ? "🔒 (đã khoá)" : "≡  Báo cáo giao dịch");
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private int pxToDp(int px) {
        return (int) (px / getResources().getDisplayMetrics().density);
    }

    private void poll() {
        int since = Prefs.lastSeenId(this);
        ApiClient.getTransactions(this, since, new ApiClient.Callback<ApiClient.Transaction[]>() {
            @Override
            public void onSuccess(ApiClient.Transaction[] result) {
                handler.post(() -> {
                    for (ApiClient.Transaction t : result) {
                        addRow(t.amount, t.description);
                        showAlert(t.amount, t.description);
                        Prefs.setLastSeenId(FloatingReportService.this, t.id);
                    }
                });
            }

            @Override
            public void onError(String message) {
                // im lặng bỏ qua lỗi mạng tạm thời, tự thử lại ở lần poll sau
            }
        });
        handler.postDelayed(poller, 5000);
    }

    private void doInjectDemo() {
        addRow(50000, "NGUYEN VAN A CHUYEN QUA DEMO TEST");
        addRow(120000, "TRAN THI B UNG HO STREAM DEMO");
        showAlert(120000, "TRAN THI B UNG HO STREAM DEMO");
    }

    /** Thêm 1 dòng — tối đa MAX_VISIBLE_ROWS dòng hiện cùng lúc (dòng cũ
     * nhất bị đẩy ra khi vượt), và mỗi dòng tự biến mất sau ROW_LIFETIME_MS
     * nếu chưa bị đẩy ra trước đó. */
    private void addRow(long amount, String description) {
        TextView tv = new TextView(this);
        tv.setText(String.format("+%,d đ\n%s", amount, description));
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(12);
        tv.setPadding(dp(6), dp(4), dp(6), dp(4));
        tv.setAlpha(Prefs.reportContentOpacity(this) / 100f);
        list.addView(tv);

        while (list.getChildCount() > MAX_VISIBLE_ROWS) list.removeViewAt(0);

        handler.postDelayed(() -> {
            if (tv.getParent() != null) list.removeView(tv);
        }, ROW_LIFETIME_MS);
    }

    private void showAlert(long amount, String content) {
        if (alertView != null) {
            try { wm.removeView(alertView); } catch (Exception ignored) {}
            alertView = null;
        }
        alertView = LayoutInflater.from(this).inflate(R.layout.floating_alert, null);
        TextView amountTv = alertView.findViewById(R.id.alertAmount);
        TextView contentTv = alertView.findViewById(R.id.alertContent);
        amountTv.setText(String.format("+%,d đ", amount));
        contentTv.setText(content);
        alertView.setAlpha(Prefs.alertOpacity(this) / 100f);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams alertParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        alertParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        alertParams.y = 60;

        final View toRemove = alertView;
        wm.addView(alertView, alertParams);
        handler.postDelayed(() -> {
            if (toRemove.getWindowToken() != null) {
                try { wm.removeView(toRemove); } catch (Exception ignored) {}
            }
            if (alertView == toRemove) alertView = null;
        }, 4000);
    }

    private void startForegroundWithNotification() {
        String channelId = "donate_report_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Menu báo cáo giao dịch", NotificationManager.IMPORTANCE_MIN);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Đang theo dõi giao dịch")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
        startForeground(2001, notification);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        instance = null;
        handler.removeCallbacks(poller);
        if (alertView != null) {
            try { wm.removeView(alertView); } catch (Exception ignored) {}
        }
        if (wm != null && floatView != null) wm.removeView(floatView);
    }
}
