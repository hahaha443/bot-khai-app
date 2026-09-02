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
 * chuyển. Tự poll /transactions mỗi 5s + hiện popup thông báo nổi khi có
 * giao dịch mới. */
public class FloatingReportService extends Service {

    private static boolean running = false;
    private static FloatingReportService instance;

    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams params;
    private LinearLayout list;
    private View alertView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poller = this::poll;

    public static boolean isRunning() { return running; }

    public static void updateSize(Context ctx) {
        if (instance != null) instance.applySize();
    }

    public static void updateOpacity(Context ctx) {
        if (instance != null) instance.applyOpacity();
    }

    /** Bơm 2 dòng dữ liệu giả để canh size/độ mờ, không đụng last_seen_id
     * thật nên không ảnh hưởng dữ liệu thật khi poll tiếp. */
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

        applyOpacity();
        wm.addView(floatView, params);
        handler.post(poller);
    }

    private void applySize() {
        if (params == null || floatView == null) return;
        params.width = dp(Prefs.reportSizeDp(this));
        wm.updateViewLayout(floatView, params);
    }

    private void applyOpacity() {
        if (floatView == null) return;
        floatView.setAlpha(Prefs.reportOpacity(this) / 100f);
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
                        addRow(t.amount, t.description);
                        showAlert(t.amount, t.description);
                        Prefs.setLastSeenId(FloatingReportService.this, t.id);
                    }
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

    private void doInjectDemo() {
        addRow(50000, "NGUYEN VAN A CHUYEN QUA DEMO TEST");
        addRow(120000, "TRAN THI B UNG HO STREAM DEMO");
        showAlert(120000, "TRAN THI B UNG HO STREAM DEMO");
    }

    private void addRow(long amount, String description) {
        TextView tv = new TextView(this);
        tv.setText(String.format("+%,d đ\n%s", amount, description));
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(12);
        tv.setPadding(dp(6), dp(4), dp(6), dp(4));
        list.addView(tv);
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
