package com.hihu.donatefloat;

import android.content.Context;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Cho phép kéo resize từ 1 trong 4 góc, neo góc đối diện đứng yên —
 * không cần icon/nút hiển thị, chỉ cần 1 vùng chạm nhỏ trong suốt đặt ở
 * góc tương ứng. Nếu panel đang bị khoá (lockKey khác null) thì bỏ qua
 * thao tác kéo, không cho resize.
 *
 * Giống DragLockListener, các giá trị width/height/x/y mới được gộp qua
 * Choreographer và chỉ áp dụng 1 lần mỗi khung hình để resize mượt hơn. */
public class CornerResizeListener implements View.OnTouchListener {

    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public interface OnResized {
        void onResized(int widthPx, int heightPx);
    }

    private final WindowManager.LayoutParams params;
    private final WindowManager wm;
    private final View target;
    private final Corner corner;
    private final int minPx;
    private final OnResized onResized;
    private final Context ctx;
    private final String lockKey;

    private int initialWidth, initialHeight, initialX, initialY;
    private float initialTouchX, initialTouchY;

    private boolean frameScheduled = false;
    private boolean resizing = false;
    private int pendingWidth, pendingHeight, pendingX, pendingY;

    private final Choreographer.FrameCallback frameCallback = frameTimeNanos -> {
        frameScheduled = false;
        if (!resizing) return;
        params.width = pendingWidth;
        params.height = pendingHeight;
        params.x = pendingX;
        params.y = pendingY;
        try { wm.updateViewLayout(target, params); } catch (Exception ignored) {}
    };

    public CornerResizeListener(WindowManager.LayoutParams params, WindowManager wm, View target,
                                 Corner corner, int minPx, OnResized onResized) {
        this(params, wm, target, corner, minPx, onResized, null, null);
    }

    public CornerResizeListener(WindowManager.LayoutParams params, WindowManager wm, View target,
                                 Corner corner, int minPx, OnResized onResized,
                                 Context ctx, String lockKey) {
        this.params = params;
        this.wm = wm;
        this.target = target;
        this.corner = corner;
        this.minPx = minPx;
        this.onResized = onResized;
        this.ctx = ctx;
        this.lockKey = lockKey;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (lockKey != null && Prefs.panelLocked(ctx, lockKey)) return true;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                resizing = true;
                initialWidth = params.width;
                initialHeight = params.height;
                initialX = params.x;
                initialY = params.y;
                pendingWidth = initialWidth;
                pendingHeight = initialHeight;
                pendingX = initialX;
                pendingY = initialY;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (!resizing) return true;
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

                pendingWidth = newWidth;
                pendingHeight = newHeight;
                pendingX = newX;
                pendingY = newY;
                if (!frameScheduled) {
                    frameScheduled = true;
                    Choreographer.getInstance().postFrameCallback(frameCallback);
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
                resizing = false;
                params.width = pendingWidth;
                params.height = pendingHeight;
                params.x = pendingX;
                params.y = pendingY;
                try { wm.updateViewLayout(target, params); } catch (Exception ignored) {}
                if (onResized != null) onResized.onResized(params.width, params.height);
                return true;

            case MotionEvent.ACTION_CANCEL:
                resizing = false;
                return true;
        }
        return false;
    }
}
