package com.hihu.donatefloat;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Cho phép kéo resize từ 1 trong 4 góc, neo góc đối diện đứng yên —
 * không cần icon/nút hiển thị, chỉ cần 1 vùng chạm nhỏ trong suốt đặt ở
 * góc tương ứng. Nếu panel đang bị khoá thì bỏ qua thao tác KÉO (không
 * resize), nhưng vẫn nhận CHẠM NHẸ để cộng dồn vào bộ đếm khoá/mở khoá
 * dùng chung của panel (counter) — nhờ vậy chạm 3 lần ở góc cũng khoá/mở
 * khoá được y như chạm ở vùng nội dung. */
public class CornerResizeListener implements View.OnTouchListener {

    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public interface OnResized {
        void onResized(int widthPx, int heightPx);
    }

    private static final int TAP_SLOP_PX = 18;

    private final WindowManager.LayoutParams params;
    private final WindowManager wm;
    private final View target;
    private final Corner corner;
    private final int minPx;
    private final OnResized onResized;
    private final Context ctx;
    private final String lockKey;
    private final LockToggleCounter counter;

    private int initialWidth, initialHeight, initialX, initialY;
    private float initialTouchX, initialTouchY;

    public CornerResizeListener(WindowManager.LayoutParams params, WindowManager wm, View target,
                                 Corner corner, int minPx, OnResized onResized) {
        this(params, wm, target, corner, minPx, onResized, null, null, null);
    }

    public CornerResizeListener(WindowManager.LayoutParams params, WindowManager wm, View target,
                                 Corner corner, int minPx, OnResized onResized,
                                 Context ctx, String lockKey, LockToggleCounter counter) {
        this.params = params;
        this.wm = wm;
        this.target = target;
        this.corner = corner;
        this.minPx = minPx;
        this.onResized = onResized;
        this.ctx = ctx;
        this.lockKey = lockKey;
        this.counter = counter;
    }

    private boolean isLocked() {
        return lockKey != null && ctx != null && Prefs.panelLocked(ctx, lockKey);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialWidth = params.width;
                initialHeight = params.height;
                initialX = params.x;
                initialY = params.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (isLocked()) return true;
                float dx = event.getRawX() - initialTouchX;
                float dy = event.getRawY() - initialTouchY;

                int newWidth = initialWidth;
                int newHeight = initialHeight;
                int newX = initialX;
                int newY = initialY;

                switch (corner) {
                    case BOTTOM_RIGHT:
                        newWidth = (int) (initialWidth + dx);
                        newHeight = (int) (initialHeight + dy);
                        break;
                    case BOTTOM_LEFT:
                        newWidth = (int) (initialWidth - dx);
                        newHeight = (int) (initialHeight + dy);
                        newX = (int) (initialX + dx);
                        break;
                    case TOP_RIGHT:
                        newWidth = (int) (initialWidth + dx);
                        newHeight = (int) (initialHeight - dy);
                        newY = (int) (initialY + dy);
                        break;
                    case TOP_LEFT:
                        newWidth = (int) (initialWidth - dx);
                        newHeight = (int) (initialHeight - dy);
                        newX = (int) (initialX + dx);
                        newY = (int) (initialY + dy);
                        break;
                }

                if (newWidth < minPx) {
                    if (corner == Corner.BOTTOM_LEFT || corner == Corner.TOP_LEFT) {
                        newX -= (minPx - newWidth);
                    }
                    newWidth = minPx;
                }
                if (newHeight < minPx) {
                    if (corner == Corner.TOP_LEFT || corner == Corner.TOP_RIGHT) {
                        newY -= (minPx - newHeight);
                    }
                    newHeight = minPx;
                }

                params.width = newWidth;
                params.height = newHeight;
                params.x = newX;
                params.y = newY;
                try { wm.updateViewLayout(target, params); } catch (Exception ignored) {}
                return true;
            }

            case MotionEvent.ACTION_UP: {
                float dx = Math.abs(event.getRawX() - initialTouchX);
                float dy = Math.abs(event.getRawY() - initialTouchY);
                if (dx < TAP_SLOP_PX && dy < TAP_SLOP_PX) {
                    if (counter != null) counter.registerTap();
                } else if (!isLocked() && onResized != null) {
                    onResized.onResized(params.width, params.height);
                }
                return true;
            }
        }
        return false;
    }
}
