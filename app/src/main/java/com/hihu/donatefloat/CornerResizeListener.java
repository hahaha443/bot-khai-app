package com.hihu.donatefloat;

import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Cho phép kéo resize từ 1 trong 4 góc, neo góc đối diện đứng yên.
 * Khi panel bị khoá, cả cửa sổ không nhận chạm nên không cần tự kiểm
 * tra khoá ở đây. onMoved (có thể null) được gọi mỗi lần kích thước
 * thay đổi để cập nhật vị trí nút khoá đi theo panel. */
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
    private final Runnable onMoved;

    private int initialWidth, initialHeight, initialX, initialY;
    private float initialTouchX, initialTouchY;

    public CornerResizeListener(WindowManager.LayoutParams params, WindowManager wm, View target,
                                 Corner corner, int minPx, OnResized onResized) {
        this(params, wm, target, corner, minPx, onResized, null);
    }

    public CornerResizeListener(WindowManager.LayoutParams params, WindowManager wm, View target,
                                 Corner corner, int minPx, OnResized onResized, Runnable onMoved) {
        this.params = params;
        this.wm = wm;
        this.target = target;
        this.corner = corner;
        this.minPx = minPx;
        this.onResized = onResized;
        this.onMoved = onMoved;
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
                if (onMoved != null) onMoved.run();
                return true;
            }

            case MotionEvent.ACTION_UP:
                if (onResized != null) onResized.onResized(params.width, params.height);
                return true;
        }
        return false;
    }
}
