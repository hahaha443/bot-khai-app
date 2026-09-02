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
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** Floating menu #3: Ghi chú nổi — hiện nguyên văn nội dung chữ mà mày tự
 * viết trong app (mục tiêu donate, thông báo, luật lệ stream, v.v.). Kéo
 * góc bất kỳ để resize, khoá tương tác qua nút 🔒 chung. */
public class FloatingNoteService extends Service {

    private static boolean running = false;
    private static FloatingNoteService instance;

    private WindowManager wm;
    private View floatView;
    private TextView contentView;
    private WindowManager.LayoutParams params;

    public static boolean isRunning() { return running; }

    public static void updateSize(Context ctx) {
        if (instance != null) instance.applySize();
    }

    public static void updateOpacity(Context ctx) {
        if (instance != null) instance.applyOpacity();
    }

    public static void updateLocked(Context ctx) {
        if (instance != null) instance.applyLocked();
    }

    public static void updateText(Context ctx) {
        if (instance != null) instance.applyText(ctx);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        startForegroundWithNotification();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_note, null);
        contentView = floatView.findViewById(R.id.noteContent);
        contentView.setText(Prefs.noteText(this));

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (Prefs.locked(this)) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        params = new WindowManager.LayoutParams(
                dp(Prefs.noteSizeDp(this)), dp(Prefs.noteHeightDp(this)),
                type, flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 560;

        floatView.findViewById(R.id.dragHandleNote)
                .setOnTouchListener(new DragTouchListener(params, wm, floatView));

        int min = dp(60);
        CornerResizeListener.OnResized onResized = (w, h) -> {
            Prefs.setNoteSizeDp(this, pxToDp(w));
            Prefs.setNoteHeightDp(this, pxToDp(h));
        };
        bindCorner(R.id.resizeNoteTL, CornerResizeListener.Corner.TOP_LEFT, min, onResized);
        bindCorner(R.id.resizeNoteTR, CornerResizeListener.Corner.TOP_RIGHT, min, onResized);
        bindCorner(R.id.resizeNoteBL, CornerResizeListener.Corner.BOTTOM_LEFT, min, onResized);
        bindCorner(R.id.resizeNoteBR, CornerResizeListener.Corner.BOTTOM_RIGHT, min, onResized);

        applyOpacity();
        wm.addView(floatView, params);
        applyLocked();
        LockBubble.acquire(this);
    }

    private void bindCorner(int viewId, CornerResizeListener.Corner corner, int min,
                             CornerResizeListener.OnResized onResized) {
        View v = floatView.findViewById(viewId);
        v.setOnTouchListener(new CornerResizeListener(params, wm, floatView, corner, min, onResized));
    }

    private void applySize() {
        if (params == null || floatView == null) return;
        params.width = dp(Prefs.noteSizeDp(this));
        params.height = dp(Prefs.noteHeightDp(this));
        wm.updateViewLayout(floatView, params);
    }

    private void applyOpacity() {
        if (floatView == null) return;
        floatView.setAlpha(Prefs.noteOpacity(this) / 100f);
    }

    private void applyText(Context ctx) {
        if (contentView != null) contentView.setText(Prefs.noteText(ctx));
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
        TextView handle = floatView.findViewById(R.id.dragHandleNote);
        if (handle != null) {
            handle.setText(locked ? "🔒 (đã khoá)" : "≡  Ghi chú");
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private int pxToDp(int px) {
        return (int) (px / getResources().getDisplayMetrics().density);
    }

    private void startForegroundWithNotification() {
        String channelId = "donate_note_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Menu ghi chú nổi", NotificationManager.IMPORTANCE_MIN);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Menu ghi chú đang bật")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
        startForeground(2003, notification);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        instance = null;
        LockBubble.release();
        if (wm != null && floatView != null) wm.removeView(floatView);
    }
}
