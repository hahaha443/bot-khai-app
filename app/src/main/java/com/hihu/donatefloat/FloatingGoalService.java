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
import android.widget.ProgressBar;
import android.widget.TextView;

/** Floating menu #4: thẻ đo mục tiêu donate, viền gradient. Poll thưa 30s
 * cho nhẹ pin/CPU. Chạm 4 lần liên tiếp lên dải mỏng đỉnh để khoá/mở khoá
 * riêng menu này. */
public class FloatingGoalService extends Service {

    private static boolean running = false;
    private static FloatingGoalService instance;

    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams params;
    private TextView titleView, currentView, targetView, subtitleView, percentBadge;
    private ProgressBar progressBar;
    private PanelLockStrip lockStrip;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poller = this::poll;

    public static boolean isRunning() { return running; }

    public static void updateSize(Context ctx) {
        if (instance != null) instance.applySize();
    }

    public static void updateOpacity(Context ctx) {
        if (instance != null) instance.applyOpacity();
    }

    public static void updateConfig(Context ctx) {
        if (instance != null) instance.applyConfig();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        startForegroundWithNotification();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_goal, null);
        titleView = floatView.findViewById(R.id.goalTitleView);
        currentView = floatView.findViewById(R.id.goalCurrentView);
        targetView = floatView.findViewById(R.id.goalTargetView);
        subtitleView = floatView.findViewById(R.id.goalSubtitleView);
        percentBadge = floatView.findViewById(R.id.goalPercentBadge);
        progressBar = floatView.findViewById(R.id.goalProgressBar);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                dp(Prefs.goalSizeDp(this)), dp(Prefs.goalHeightDp(this)),
                type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 780;

        applyConfig();
        applyOpacity();
        wm.addView(floatView, params);

        lockStrip = new PanelLockStrip(this, wm, floatView, params, "goal");
        lockStrip.attach();

        int min = dp(100);
        CornerResizeListener.OnResized onResized = (w, h) -> {
            Prefs.setGoalSizeDp(this, pxToDp(w));
            Prefs.setGoalHeightDp(this, pxToDp(h));
            if (lockStrip != null) lockStrip.syncAfterResize();
        };
        bindCorner(R.id.resizeGoalTL, CornerResizeListener.Corner.TOP_LEFT, min, onResized);
        bindCorner(R.id.resizeGoalTR, CornerResizeListener.Corner.TOP_RIGHT, min, onResized);
        bindCorner(R.id.resizeGoalBL, CornerResizeListener.Corner.BOTTOM_LEFT, min, onResized);
        bindCorner(R.id.resizeGoalBR, CornerResizeListener.Corner.BOTTOM_RIGHT, min, onResized);

        handler.post(poller);
    }

    private void bindCorner(int viewId, CornerResizeListener.Corner corner, int min,
                             CornerResizeListener.OnResized onResized) {
        View v = floatView.findViewById(viewId);
        v.setOnTouchListener(new CornerResizeListener(params, wm, floatView, corner, min, onResized));
    }

    private void applyConfig() {
        titleView.setText(Prefs.goalTitle(this));
        titleView.setTextColor(Prefs.textColor(this));
        targetView.setText(String.format("/ %,d đ", Prefs.goalAmount(this)));
    }

    private void applySize() {
        if (params == null || floatView == null) return;
        params.width = dp(Prefs.goalSizeDp(this));
        params.height = dp(Prefs.goalHeightDp(this));
        wm.updateViewLayout(floatView, params);
        if (lockStrip != null) lockStrip.syncAfterResize();
    }

    private void applyOpacity() {
        if (floatView == null) return;
        floatView.setAlpha(Prefs.goalOpacity(this) / 100f);
    }

    private void poll() {
        ApiClient.getStats(this, "today", new ApiClient.Callback<ApiClient.Stats>() {
            @Override
            public void onSuccess(ApiClient.Stats result) {
                handler.post(() -> {
                    long goal = Math.max(1, Prefs.goalAmount(FloatingGoalService.this));
                    currentView.setText(String.format("%,d đ", result.total));
                    subtitleView.setText(result.count + " giao dịch hôm nay");
                    int percent = (int) Math.min(100, (result.total * 100L) / goal);
                    percentBadge.setText(percent + "%");
                    progressBar.setProgress((int) Math.min(1000, (result.total * 1000L) / goal));
                });
            }

            @Override
            public void onError(String message) {
            }
        });
        handler.postDelayed(poller, 30_000);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private int pxToDp(int px) {
        return (int) (px / getResources().getDisplayMetrics().density);
    }

    private void startForegroundWithNotification() {
        String channelId = "donate_goal_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Menu thanh đo mục tiêu", NotificationManager.IMPORTANCE_MIN);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Thanh đo mục tiêu đang bật")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
        startForeground(2004, notification);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        instance = null;
        handler.removeCallbacks(poller);
        if (lockStrip != null) lockStrip.detach();
        if (wm != null && floatView != null) wm.removeView(floatView);
    }
}
