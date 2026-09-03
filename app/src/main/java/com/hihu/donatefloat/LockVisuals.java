package com.hihu.donatefloat;

import android.content.Context;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Cập nhật màu/hiệu ứng thanh kéo (drag handle) theo trạng thái khoá của
 * panel, để người dùng thấy ngay panel đang khoá hay không mà không cần
 * đoán. Gọi applyState() ngay sau khi tạo view và mỗi khi TapLockListener
 * báo đã đổi trạng thái. */
public final class LockVisuals {
    private LockVisuals() {}

    public static void applyState(View dragHandle, Context ctx, String lockKey) {
        if (dragHandle == null) return;
        boolean locked = Prefs.panelLocked(ctx, lockKey);
        dragHandle.setBackgroundResource(locked ? R.drawable.bg_drag_handle_locked : R.drawable.bg_drag_handle);
        dragHandle.setAlpha(locked ? 0.95f : 1f);
    }

    /** Hiệu ứng nảy nhẹ khi vừa chạm 3 lần để khoá/mở khoá, giúp thao tác
     * có phản hồi rõ ràng thay vì đổi màu đột ngột không cảm giác. */
    public static void bounce(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleY(0.35f);
        v.animate()
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(new OvershootInterpolator(3.2f))
                .start();
    }
}
