package com.hihu.donatefloat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
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

/** Floating menu #1: danh sách giao dịch đã nhận, hiện nội dung người
 * chuyển. Tự poll /transactions mỗi 5s. */
public class FloatingReportService extends Service {

    private static boolean running = false;
    private static FloatingReportService instance;

    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams params;
    private LinearLayout list;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poller = this::poll;

    public static boolean isRunning() { return running; }

    public static void updateSize(Context ctx) {
        if (instance != null) instance.applySize();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        startForegroundWithNotification();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_report, null);
        list = floatView.findViewById(R.id.reportList);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                dp(Prefs.reportSizeDp(this)), dp(220),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 100;

        floatView.findViewById(R.id.dragHandleReport)
                .setOnTouchListener(new DragTouchListener(params, wm, floatView));

        wm.addView(floatView, params);
        handler.post(poller);
    }

    private void applySize() {
        if (params == null || floatView == null) return;
        params.width = dp(Prefs.reportSizeDp(this));
        wm.updateViewLayout(floatView, params);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void poll() {
        int since = Prefs.lastSeenId(this);
        ApiClient.getTransactions(this, since, new ApiClient.Callback<ApiClient.Transaction[]>() {
            @Override
            public void onSuccess(ApiClient.Transaction[] result) {
                handler.post(() -> {
                    for (ApiClient.Transaction t : result) {
                        addRow(t);
                        Prefs.setLastSeenId(FloatingReportService.this, t.id);
                    }
                    // Giữ tối đa 30 dòng để menu không phình to
                    while (list.getChildCount() > 30) list.removeViewAt(0);
                });
            }

            @Override
            public void onError(String message) {
                // im lặng bỏ qua lỗi mạng tạm thời, tự thử lại ở lần poll sau
            }
        });
        handler.postDelayed(poller, 5000);
    }

    private void addRow(ApiClient.Transaction t) {
        TextView tv = new TextView(this);
        String note = t.matchedContent != null ? (" [" + t.matchedContent + "]") : "";
        tv.setText(String.format("+%,d đ%s\n%s", t.amount, note, t.description));
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(12);
        tv.setPadding(dp(6), dp(4), dp(6), dp(4));
        list.addView(tv);
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
        if (wm != null && floatView != null) wm.removeView(floatView);
    }
}
