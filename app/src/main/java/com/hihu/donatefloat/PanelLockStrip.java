package com.hihu.donatefloat;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Mỗi panel nổi có 1 dải mỏng (14dp) là 1 CỬA SỔ RIÊNG, luôn nhận chạm dù
 * panel có bị khoá hay không — vì Android chỉ khoá/mở chạm theo TỪNG cửa
 * sổ, không theo từng vùng bên trong 1 cửa sổ. Dải mỏng lo việc kéo +
 * đếm chạm liên tiếp 4 lần để khoá/mở khoá RIÊNG panel đó (chạm nhẹ, ko
 * kéo). Khi khoá, panel nội dung bật pass-through để không đụng game. */
public class PanelLockStrip {

    private static final long TAP_WINDOW_MS = 1200;
    private static final int TAP_SLOP_PX = 18;

    private final Context ctx;
    private final WindowManager wm;
    private final View contentView;
    private final WindowManager.LayoutParams contentParams;
    private final String lockKey;

    private View stripView;
    private WindowManager.LayoutParams stripParams;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private int tapCount = 0;
    private long lastTapTime = 0;

    public PanelLockStrip(Context ctx, WindowManager wm, View contentView,
                           WindowManager.LayoutParams contentParams, String lockKey) {
        this.ctx = ctx;
        this.wm = wm;
        this.contentView = contentView;
        this.contentParams = contentParams;
        this.lockKey = lockKey;
    }

    public void attach() {
        stripView = new View(ctx);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        stripParams = new WindowManager.LayoutParams(
                contentParams.width, dp(14), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // KHÔNG FLAG_NOT_TOUCHABLE — luôn bấm được
                PixelFormat.TRANSLUCENT);
        stripParams.gravity = Gravity.TOP | Gravity.START;
        stripParams.x = contentParams.x;
        stripParams.y = contentParams.y;

        stripView.setOnTouchListener(this::onStripTouch);

        applyContentLock(Prefs.panelLocked(ctx, lockKey));
        try { wm.addView(stripView, stripParams); } catch (Exception ignored) {}
    }

    private boolean onStripTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = contentParams.x;
                initialY = contentParams.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                int nx = initialX + (int) (event.getRawX() - initialTouchX);
                int ny = initialY + (int) (event.getRawY() - initialTouchY);
                contentParams.x = nx;
                contentParams.y = ny;
                stripParams.x = nx;
                stripParams.y = ny;
                safeUpdate(contentView, contentParams);
                safeUpdate(stripView, stripParams);
                return true;
            case MotionEvent.ACTION_UP:
                float dx = Math.abs(event.getRawX() - initialTouchX);
                float dy = Math.abs(event.getRawY() - initialTouchY);
                if (dx < TAP_SLOP_PX && dy < TAP_SLOP_PX) {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastTapTime > TAP_WINDOW_MS) tapCount = 0;
                    tapCount++;
                    lastTapTime = now;
                    if (tapCount >= 4) {
                        tapCount = 0;
                        boolean newLocked = !Prefs.panelLocked(ctx, lockKey);
                        Prefs.setPanelLocked(ctx, lockKey, newLocked);
                        applyContentLock(newLocked);
                    }
                }
                return true;
        }
        return false;
    }

    /** Gọi sau khi resize góc xong để dải mỏng bám đúng width/x/y mới. */
    public void syncAfterResize() {
        if (stripParams == null) return;
        stripParams.width = contentParams.width;
        stripParams.x = contentParams.x;
        stripParams.y = contentParams.y;
        safeUpdate(stripView, stripParams);
    }

    private void applyContentLock(boolean locked) {
        if (locked) contentParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else contentParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        safeUpdate(contentView, contentParams);
    }

    private void safeUpdate(View v, WindowManager.LayoutParams p) {
        try { wm.updateViewLayout(v, p); } catch (Exception ignored) {}
    }

    public void detach() {
        if (stripView != null) {
            try { wm.removeView(stripView); } catch (Exception ignored) {}
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics());
    }
}
