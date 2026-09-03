package com.hihu.donatefloat;

import android.content.Context;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Kéo thanh ngang trên đỉnh để di chuyển panel — chỉ hoạt động khi
 * panel CHƯA bị khoá. Việc khoá/mở khoá nằm riêng ở TapLockListener gắn
 * trong vùng nội dung.
 *
 * Vị trí mới được gộp qua Choreographer và chỉ đẩy xuống WindowManager
 * đúng 1 lần mỗi khung hình (thay vì mỗi sự kiện ACTION_MOVE, vốn có thể
 * bắn nhanh hơn tốc độ khung hình) để việc kéo mượt hơn, đỡ giật. */
public class DragLockListener implements View.OnTouchListener {

    private final Context ctx;
    private final WindowManager.LayoutParams params;
    private final WindowManager wm;
    private final View target;
    private final String lockKey;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    private boolean frameScheduled = false;
    private int pendingX, pendingY;
    private boolean dragging = false;

    private final Choreographer.FrameCallback frameCallback = frameTimeNanos -> {
        frameScheduled = false;
        if (!dragging) return;
        params.x = pendingX;
        params.y = pendingY;
        try { wm.updateViewLayout(target, params); } catch (Exception ignored) {}
    };

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
                dragging = !Prefs.panelLocked(ctx, lockKey);
                initialX = params.x;
                initialY = params.y;
                pendingX = initialX;
                pendingY = initialY;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    pendingX = initialX + (int) (event.getRawX() - initialTouchX);
                    pendingY = initialY + (int) (event.getRawY() - initialTouchY);
                    if (!frameScheduled) {
                        frameScheduled = true;
                        Choreographer.getInstance().postFrameCallback(frameCallback);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
        }
        return false;
    }
}
