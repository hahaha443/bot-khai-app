package com.hihu.donatefloat;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Phản hồi khi khoá/mở khoá một panel — CHỈ dùng rung nhẹ, không đổi
 * màu/hiện icon gì trên màn hình, để tránh lộ cơ chế khi đang livestream
 * hoặc chia sẻ màn hình. Người dùng cảm nhận qua tay cầm máy, người xem
 * không thấy gì khác lạ. */
public final class LockVisuals {
    private LockVisuals() {}

    public static void applyState(android.view.View dragHandle, Context ctx, String lockKey) {
        // Cố ý không đổi giao diện của dragHandle — khoá là thao tác ngầm.
    }

    public static void bounce(android.view.View v) {
        if (v == null) return;
        try {
            Vibrator vib = (Vibrator) v.getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(35);
            }
        } catch (Exception ignored) {}
    }
}
