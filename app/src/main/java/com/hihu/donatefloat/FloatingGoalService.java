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

/** Floating menu #4: thẻ đo mục tiêu donate, viền gradient. Số lượt donate
 * hiện ở góc (badge), % hiện ngay TRONG thanh tiến trình và trượt theo
 * đúng vị trí donate tới đâu, dưới thanh là dòng ghi chú tự do người
 * dùng tự viết. Poll thưa 30s cho nhẹ pin/CPU. */
public class FloatingGoalService extends Service {

    private static boolean running = false;
    private static FloatingGoalService instance;

    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams params;
    private TextView titleView, subtitleView, countBadge, percentInBar, currentView;
    private ProgressBar progressBar;
    private LockButtonWindow lockButton;
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
        Prefs.setGoalSizeDp(ctx, 300);
        Prefs.setGoalHeightDp(ctx, 110);
        if (instance != null) instance.applySize();
    }

    public static void updateOpacity(Context ctx) {
        if (instance != null) instance.applyOpacity();
    }

    public static void updateConfig(Context ctx) {
        if (instance != null) instance.applyConfig();
    }

    public static void refreshNow(Context ctx) {
        if (instance != null) instance.refreshImmediate();
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
        subtitleView = floatView.findViewById(R.id.goalSubtitleView);
        currentView = floatView.findViewById(R.id.goalCurrentView);
        countBadge = floatView.findViewById(R.id.goalCountBadge);
        percentInBar = floatView.findViewById(R.id.goalPercentInBar);
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
        if (Prefs.overlaysHidden(this)) floatView.setVisibility(View.GONE);

        View dragHandleGoal = floatView.findViewById(R.id.dragHandleGoal);
        lockButton = new LockButtonWindow(this, wm, "goal", floatView, params);
        dragHandleGoal.setOnTouchListener(new DragLockListener(this, params, wm, floatView,
                () -> lockButton.updatePosition()));
        lockButton.create();

        int min = dp(120);
        CornerResizeListener.OnResized onResized = (w, h) -> {
            Prefs.setGoalSizeDp(this, pxToDp(w));
            Prefs.setGoalHeightDp(this, pxToDp(h));
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
        v.setOnTouchListener(new CornerResizeListener(params, wm, floatView, corner, min, onResized,
                () -> lockButton.updatePosition()));
    }

    private void applyConfig() {
        titleView.setText(Prefs.goalTitle(this));
        titleView.setTextColor(Prefs.goalTextColor(this));
        // (đã bỏ hiển thị số tiền hiện tại/mục tiêu dạng chữ theo yêu cầu — chỉ còn % trong thanh)
        String note = Prefs.goalNote(this);
        subtitleView.setText(note);
        subtitleView.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void applySize() {
        if (params == null || floatView == null) return;
        params.width = dp(Prefs.goalSizeDp(this));
        params.height = dp(Prefs.goalHeightDp(this));
        wm.updateViewLayout(floatView, params);
        if (lockButton != null) lockButton.updatePosition();
    }

    private void applyOpacity() {
        if (floatView == null) return;
        floatView.setAlpha(Prefs.goalOpacity(this) / 100f);
    }

    private void poll() {
        doFetch();
        handler.removeCallbacks(poller);
        handler.postDelayed(poller, 30_000);
    }

    /** Gọi ngay khi Báo cáo báo có giao dịch mới — không đụng vào lịch
     * postDelayed định kỳ, tránh nhân đôi bộ đếm. */
    public void refreshImmediate() {
        doFetch();
    }

    private void doFetch() {
        ApiClient.getStats(this, "today", new ApiClient.Callback<ApiClient.Stats>() {
            @Override
            public void onSuccess(ApiClient.Stats result) {
                handler.post(() -> {
                    long goal = Math.max(1, Prefs.goalAmount(FloatingGoalService.this));
                    currentView.setText(String.format("%,d đ  •  Mục tiêu %,d đ", result.total, goal));
                    countBadge.setText(result.count + " lượt");
                    int percent = (int) Math.min(100, (result.total * 100L) / goal);
                    progressBar.setProgress((int) Math.min(1000, (result.total * 1000L) / goal));
                    percentInBar.setText(percent + "%");
                });
            }

            @Override
            public void onError(String message) {
            }
        });
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
        if (lockButton != null) lockButton.destroy();
        if (wm != null && floatView != null) wm.removeView(floatView);
    }
}
