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
 * tối đa hiện 2 dòng cùng lúc. Độ mờ tách NỀN/NỘI DUNG. Kéo góc bất kỳ để
 * resize. Chạm liên tiếp 4 lần trên dải mỏng đỉnh panel để khoá/mở khoá
 * RIÊNG panel này (không đụng game khi khoá). */
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

    public static void setHidden(boolean hidden) {
        if (instance != null && instance.floatView != null) {
            instance.floatView.setVisibility(hidden ? View.GONE : View.VISIBLE);
        }
    }

    public static void updateSize(Context ctx) {
        if (instance != null) instance.applySize();
    }

    public static void resetSize(Context ctx) {
        Prefs.setReportSizeDp(ctx, 280);
        Prefs.setReportHeightDp(ctx, 220);
        if (instance != null) instance.applySize();
    }

    public static void updateBgOpacity(Context ctx) {
        if (instance != null) instance.applyBgOpacity();
    }

    public static void updateContentOpacity(Context ctx) {
        if (instance != null) instance.applyContentOpacity();
    }

    public static void updateTextColor(Context ctx) {
        if (instance != null) instance.applyContentOpacity();
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

        params = new WindowManager.LayoutParams(
                dp(Prefs.reportSizeDp(this)), dp(Prefs.reportHeightDp(this)),
                type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 100;

        wm.addView(floatView, params);
        applyBgOpacity();
        applyContentOpacity();
        if (Prefs.overlaysHidden(this)) floatView.setVisibility(View.GONE);

        View dragHandleReport = floatView.findViewById(R.id.dragHandleReport);
        dragHandleReport.setOnTouchListener(new DragLockListener(this, params, wm, floatView, "report"));
        LockToggleCounter lockCounter = new LockToggleCounter(this, "report");
        list.setOnTouchListener(new TapLockListener(lockCounter));

        wireCornerResize(lockCounter);
        handler.post(poller);
    }

    private void wireCornerResize(LockToggleCounter lockCounter) {
        int min = dp(60);
        CornerResizeListener.OnResized onResized = (w, h) -> {
            Prefs.setReportSizeDp(this, pxToDp(w));
            Prefs.setReportHeightDp(this, pxToDp(h));
        };
        bindCorner(R.id.resizeReportTL, CornerResizeListener.Corner.TOP_LEFT, min, onResized, lockCounter);
        bindCorner(R.id.resizeReportTR, CornerResizeListener.Corner.TOP_RIGHT, min, onResized, lockCounter);
        bindCorner(R.id.resizeReportBL, CornerResizeListener.Corner.BOTTOM_LEFT, min, onResized, lockCounter);
        bindCorner(R.id.resizeReportBR, CornerResizeListener.Corner.BOTTOM_RIGHT, min, onResized, lockCounter);
    }

    private void bindCorner(int viewId, CornerResizeListener.Corner corner, int min,
                             CornerResizeListener.OnResized onResized, LockToggleCounter lockCounter) {
        View v = floatView.findViewById(viewId);
        v.setOnTouchListener(new CornerResizeListener(params, wm, floatView, corner, min, onResized, this, "report", lockCounter));
    }

    private void applySize() {
        if (params == null || floatView == null) return;
        params.width = dp(Prefs.reportSizeDp(this));
        params.height = dp(Prefs.reportHeightDp(this));
        wm.updateViewLayout(floatView, params);
    }

    private void applyBgOpacity() {
        if (panel == null) return;
        Drawable bg = panel.getBackground();
        if (bg != null) bg.mutate().setAlpha((int) (Prefs.reportBgOpacity(this) / 100f * 255));
    }

    private void applyContentOpacity() {
        if (list == null) return;
        float alpha = Prefs.reportContentOpacity(this) / 100f;
        int color = Prefs.reportTextColor(this);
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            child.setAlpha(alpha);
            if (child instanceof TextView) ((TextView) child).setTextColor(color);
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
                    if (result.length > 0) {
                        // Có giao dịch mới -> báo luôn menu Mục tiêu cập nhật ngay,
                        // không đợi chu kỳ poll riêng của nó (đỡ delay).
                        FloatingGoalService.refreshNow(FloatingReportService.this);
                    }
                });
            }

            @Override
            public void onError(String message) {
            }
        });
        handler.postDelayed(poller, 3000);
    }

    private void doInjectDemo() {
        addRow(50000, "NGUYEN VAN A CHUYEN QUA DEMO TEST");
        addRow(120000, "TRAN THI B UNG HO STREAM DEMO");
        showAlert(120000, "TRAN THI B UNG HO STREAM DEMO");
    }

    private void addRow(long amount, String description) {
        TextView tv = new TextView(this);
        tv.setText(String.format("+%,d đ\n%s", amount, description));
        tv.setTextColor(Prefs.reportTextColor(this));
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
