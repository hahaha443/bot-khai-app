package com.hihu.donatefloat;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Gắn vào vùng NỘI DUNG bên trong menu (không phải thanh kéo ở trên) —
 * chạm nhẹ liên tiếp 3 lần để khoá/mở khoá RIÊNG panel này. Tách biệt
 * khỏi việc kéo di chuyển (nằm ở thanh ngang riêng). */
public class TapLockListener implements View.OnTouchListener {

    private static final long TAP_WINDOW_MS = 1200;
    private static final int TAP_SLOP_PX = 18;
    private static final int TAPS_TO_TOGGLE = 3;

    private final Context ctx;
    private final String lockKey;
    private final Runnable onToggled;

    private float downX, downY;
    private int tapCount = 0;
    private long lastTapTime = 0;

    public TapLockListener(Context ctx, String lockKey, Runnable onToggled) {
        this.ctx = ctx;
        this.lockKey = lockKey;
        this.onToggled = onToggled;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                return true;
            case MotionEvent.ACTION_UP:
                float dx = Math.abs(event.getRawX() - downX);
                float dy = Math.abs(event.getRawY() - downY);
                if (dx < TAP_SLOP_PX && dy < TAP_SLOP_PX) {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastTapTime > TAP_WINDOW_MS) tapCount = 0;
                    tapCount++;
                    lastTapTime = now;
                    if (tapCount >= TAPS_TO_TOGGLE) {
                        tapCount = 0;
                        Prefs.setPanelLocked(ctx, lockKey, !Prefs.panelLocked(ctx, lockKey));
                        LockVisuals.bounce(v);
                        if (onToggled != null) onToggled.run();
                    }
                }
                return true;
        }
        return false;
    }
}
