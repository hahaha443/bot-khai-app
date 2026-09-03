package com.hihu.donatefloat;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Kéo dải mỏng để di chuyển panel (chỉ khi CHƯA khoá). Chạm nhẹ liên
 * tiếp 4 lần (dù đang khoá hay không) để đảo trạng thái khoá RIÊNG panel
 * này — khoá xong panel vẫn hiện rõ 100% (không mờ đi), chỉ đơn giản là
 * bỏ qua thao tác kéo/resize để khỏi lỡ tay khi chơi game. */
public class DragLockListener implements View.OnTouchListener {

    private static final long TAP_WINDOW_MS = 1200;
    private static final int TAP_SLOP_PX = 18;

    private final Context ctx;
    private final WindowManager.LayoutParams params;
    private final WindowManager wm;
    private final View target;
    private final String lockKey;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private int tapCount = 0;
    private long lastTapTime = 0;

    public DragLockListener(Context ctx, WindowManager.LayoutParams params, WindowManager wm,
                             View target, String lockKey) {
        this.ctx = ctx;
        this.params = params;
        this.wm = wm;
        this.target = target;
        this.lockKey = lockKey;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = params.x;
                initialY = params.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!Prefs.panelLocked(ctx, lockKey)) {
                    params.x = initialX + (int) (event.getRawX() - initialTouchX);
                    params.y = initialY + (int) (event.getRawY() - initialTouchY);
                    try { wm.updateViewLayout(target, params); } catch (Exception ignored) {}
                }
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
                        Prefs.setPanelLocked(ctx, lockKey, !Prefs.panelLocked(ctx, lockKey));
                    }
                }
                return true;
        }
        return false;
    }
}
