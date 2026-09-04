package com.hihu.donatefloat;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** Nút khoá/mở khoá RIÊNG cho 1 panel, nằm ở 1 cửa sổ overlay tách biệt.
 * Khi panel chính bị khoá, cửa sổ panel được gắn FLAG_NOT_TOUCHABLE nên
 * mọi chạm xuyên thẳng xuống app/game bên dưới — nhưng nút khoá này nằm
 * ở cửa sổ riêng nên LUÔN bấm được để mở khoá lại, không cần thao tác gì
 * đặc biệt hay chạm nhiều lần. */
public class LockButtonWindow {

    private final Context ctx;
    private final WindowManager wm;
    private final String lockKey;
    private final View mainPanel;
    private final WindowManager.LayoutParams mainParams;

    private TextView buttonView;
    private WindowManager.LayoutParams buttonParams;
    private boolean added = false;

    public LockButtonWindow(Context ctx, WindowManager wm, String lockKey,
                             View mainPanel, WindowManager.LayoutParams mainParams) {
        this.ctx = ctx;
        this.wm = wm;
        this.lockKey = lockKey;
        this.mainPanel = mainPanel;
        this.mainParams = mainParams;
    }

    public void create() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        buttonView = new TextView(ctx);
        buttonView.setTextSize(14);
        buttonView.setGravity(Gravity.CENTER);
        buttonView.setBackgroundResource(R.drawable.bg_lock_button);
        buttonView.setElevation(dp(4));

        int sizePx = dp(30);
        buttonParams = new WindowManager.LayoutParams(
                sizePx, sizePx, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        buttonParams.gravity = Gravity.TOP | Gravity.START;

        refreshIcon();
        updatePosition();

        try {
            wm.addView(buttonView, buttonParams);
            added = true;
        } catch (Exception ignored) {}

        buttonView.setOnClickListener(v -> toggle());

        // Áp trạng thái khoá đã lưu trước đó (ví dụ sau khi khởi động lại menu)
        applyTouchableFromPref();
    }

    private void toggle() {
        boolean newLocked = !Prefs.panelLocked(ctx, lockKey);
        Prefs.setPanelLocked(ctx, lockKey, newLocked);
        applyTouchableFromPref();
        refreshIcon();
        vibrate();
    }

    private void applyTouchableFromPref() {
        boolean locked = Prefs.panelLocked(ctx, lockKey);
        if (locked) {
            mainParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            mainParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        try { wm.updateViewLayout(mainPanel, mainParams); } catch (Exception ignored) {}
    }

    private void refreshIcon() {
        boolean locked = Prefs.panelLocked(ctx, lockKey);
        buttonView.setText(locked ? "🔒" : "🔓");
    }

    /** Gọi mỗi khi panel chính di chuyển/đổi kích thước để nút khoá luôn
     * bám theo góc trên-phải của panel. */
    public void updatePosition() {
        if (buttonParams == null) return;
        buttonParams.x = mainParams.x + mainParams.width - buttonParams.width + dp(4);
        buttonParams.y = mainParams.y - buttonParams.height - dp(4);
        if (added) {
            try { wm.updateViewLayout(buttonView, buttonParams); } catch (Exception ignored) {}
        }
    }

    public void destroy() {
        if (added) {
            try { wm.removeView(buttonView); } catch (Exception ignored) {}
            added = false;
        }
    }

    private int dp(int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
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
