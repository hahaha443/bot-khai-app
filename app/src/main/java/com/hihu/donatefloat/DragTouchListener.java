package com.hihu.donatefloat;

import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class DragTouchListener implements View.OnTouchListener {
    private final WindowManager.LayoutParams params;
    private final WindowManager wm;
    private final View target;
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    public DragTouchListener(WindowManager.LayoutParams params, WindowManager wm, View target) {
        this.params = params;
        this.wm = wm;
        this.target = target;
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
        }
        return false;
    }
}
