package com.hihu.donatefloat;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Kéo để di chuyển floating window. Nếu truyền onQuadTap: chạm nhẹ (không
 * kéo) liên tiếp 4 lần trong 1.2s sẽ gọi callback đó — dùng để khoá nhanh
 * ngay trên menu mà không cần với tới nút khoá góc màn hình. */
public class DragTouchListener implements View.OnTouchListener {

    public interface OnQuadTap { void onQuadTap(); }

    private static final long TAP_WINDOW_MS = 1200;
    private static final int TAP_SLOP_PX = 18;

    private final WindowManager.LayoutParams params;
    private final WindowManager wm;
    private final View target;
    private final OnQuadTap onQuadTap;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private int tapCount = 0;
    private long lastTapTime = 0;

    public DragTouchListener(WindowManager.LayoutParams params, WindowManager wm, View target) {
        this(params, wm, target, null);
    }

    public DragTouchListener(WindowManager.LayoutParams params, WindowManager wm, View target, OnQuadTap onQuadTap) {
        this.params = params;
        this.wm = wm;
        this.target = target;
        this.onQuadTap = onQuadTap;
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
                params.x = initialX + (int) (event.getRawX() - initialTouchX);
                params.y = initialY + (int) (event.getRawY() - initialTouchY);
                wm.updateViewLayout(target, params);
                return true;
            case MotionEvent.ACTION_UP:
                float dx = Math.abs(event.getRawX() - initialTouchX);
                float dy = Math.abs(event.getRawY() - initialTouchY);
                if (onQuadTap != null && dx < TAP_SLOP_PX && dy < TAP_SLOP_PX) {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastTapTime > TAP_WINDOW_MS) tapCount = 0;
                    tapCount++;
                    lastTapTime = now;
                    if (tapCount >= 4) {
                        tapCount = 0;
                        onQuadTap.onQuadTap();
                    }
                }
                return true;
        }
        return false;
    }
}
