package com.hihu.donatefloat;

import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Kéo ô ở góc dưới-phải floating window để chỉnh width/height tự do —
 * to nhỏ tùy ý, không bị bó theo mấy nấc của thanh trượt trong app nữa. */
public class ResizeTouchListener implements View.OnTouchListener {

    public interface OnResized {
        void onResized(int widthPx, int heightPx);
    }

    private final WindowManager.LayoutParams params;
    private final WindowManager wm;
    private final View target;
    private final int minPx;
    private final OnResized onResized;
    private int initialWidth, initialHeight;
    private float initialTouchX, initialTouchY;

    public ResizeTouchListener(WindowManager.LayoutParams params, WindowManager wm, View target,
                                int minPx, OnResized onResized) {
        this.params = params;
        this.wm = wm;
        this.target = target;
        this.minPx = minPx;
        this.onResized = onResized;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialWidth = params.width;
                initialHeight = params.height;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                params.width = Math.max(minPx, initialWidth + (int) (event.getRawX() - initialTouchX));
                params.height = Math.max(minPx, initialHeight + (int) (event.getRawY() - initialTouchY));
                wm.updateViewLayout(target, params);
                return true;
            case MotionEvent.ACTION_UP:
                if (onResized != null) onResized.onResized(params.width, params.height);
                return true;
        }
        return false;
    }
}
