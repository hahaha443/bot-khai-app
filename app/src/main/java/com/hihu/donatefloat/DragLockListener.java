package com.hihu.donatefloat;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Kéo thanh ngang trên đỉnh để di chuyển panel. Việc khoá/mở khoá panel
 * giờ dùng nút riêng (LockButtonWindow) — khi panel bị khoá, cả cửa sổ
 * panel không nhận chạm nữa (xuyên thẳng xuống app/game bên dưới) nên
 * không cần tự kiểm tra khoá ở đây nữa. onMoved (có thể null) được gọi
 * sau mỗi lần cập nhật vị trí, dùng để dịch nút khoá đi theo panel. */
public class DragLockListener implements View.OnTouchListener {

    private final Context ctx;
    private final WindowManager.LayoutParams params;
    private final WindowManager wm;
    private final View target;
    private final Runnable onMoved;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    public DragLockListener(Context ctx, WindowManager.LayoutParams params, WindowManager wm,
                             View target, Runnable onMoved) {
        this.ctx = ctx;
        this.params = params;
        this.wm = wm;
        this.target = target;
        this.onMoved = onMoved;
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
                try { wm.updateViewLayout(target, params); } catch (Exception ignored) {}
                if (onMoved != null) onMoved.run();
                return true;
        }
        return false;
    }
}
