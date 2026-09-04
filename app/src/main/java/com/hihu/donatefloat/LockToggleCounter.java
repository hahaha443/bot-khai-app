package com.hihu.donatefloat;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Bộ đếm chạm dùng CHUNG cho toàn bộ vùng chạm của một panel — nội dung
 * bên trong LẪN 4 góc resize. Chạm nhẹ liên tiếp 3 lần ở bất kỳ đâu trên
 * panel (kể cả góc) đều cộng dồn vào cùng 1 bộ đếm để khoá/mở khoá RIÊNG
 * panel đó. Phản hồi bằng rung nhẹ, không đổi gì trên giao diện để
 * tránh lộ liễu khi đang livestream/share màn hình. */
public class LockToggleCounter {

    private static final long TAP_WINDOW_MS = 1200;
    private static final int TAPS_TO_TOGGLE = 3;

    private final Context ctx;
    private final String lockKey;

    private int tapCount = 0;
    private long lastTapTime = 0;

    public LockToggleCounter(Context ctx, String lockKey) {
        this.ctx = ctx;
        this.lockKey = lockKey;
    }

    public void registerTap() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTapTime > TAP_WINDOW_MS) tapCount = 0;
        tapCount++;
        lastTapTime = now;
        if (tapCount >= TAPS_TO_TOGGLE) {
            tapCount = 0;
            Prefs.setPanelLocked(ctx, lockKey, !Prefs.panelLocked(ctx, lockKey));
            vibrate();
        }
    }

    private void vibrate() {
        try {
            Vibrator vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(35);
            }
        } catch (Exception ignored) {}
    }
}
