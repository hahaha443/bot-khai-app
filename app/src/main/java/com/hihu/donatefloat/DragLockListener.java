package com.hihu.donatefloat;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Kéo thanh ngang trên đỉnh để di chuyển panel — chỉ hoạt động khi
 * panel CHƯA bị khoá. Việc khoá/mở khoá nằm riêng ở TapLockListener gắn
 * trong vùng nội dung. Cập nhật vị trí trực tiếp mỗi lần chạm di chuyển
 * để đảm bảo luôn bắt được thao tác kéo (không trễ khung hình). */
public class DragLockListener implements View.OnTouchListener {

    private final Context ctx;
    private final WindowManager.LayoutParams params;
    private final WindowManager wm;
    private final View target;
    private final String lockKey;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

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
        }
        return false;
    }
}
