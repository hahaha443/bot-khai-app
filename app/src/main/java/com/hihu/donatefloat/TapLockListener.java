package com.hihu.donatefloat;

import android.view.MotionEvent;
import android.view.View;

/** Gắn vào vùng NỘI DUNG bên trong menu — chạm nhẹ (không kéo) sẽ báo
 * cho LockToggleCounter dùng CHUNG của panel. Bộ đếm này cũng được chia
 * sẻ với 4 góc resize, nên chạm 3 lần ở bất kỳ đâu trên panel (nội dung
 * hay góc) đều cộng dồn để khoá/mở khoá. */
public class TapLockListener implements View.OnTouchListener {

    private static final int TAP_SLOP_PX = 18;

    private final LockToggleCounter counter;
    private float downX, downY;

    public TapLockListener(LockToggleCounter counter) {
        this.counter = counter;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                return true;
            case MotionEvent.ACTION_UP:
                float dx = Math.abs(event.getRawX() - downX);
                float dy = Math.abs(event.getRawY() - downY);
                if (dx < TAP_SLOP_PX && dy < TAP_SLOP_PX) counter.registerTap();
                return true;
        }
        return false;
    }
}
