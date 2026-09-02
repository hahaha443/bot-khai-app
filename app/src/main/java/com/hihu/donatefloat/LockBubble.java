package com.hihu.donatefloat;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** Nút khoá/mở khoá độc lập — KHÔNG bao giờ tự khoá chính nó, nên dù 2
 * bảng đang bị khoá tương tác (chơi game không đụng trúng) vẫn bấm được
 * nút này để mở khoá lại. Dùng đếm tham chiếu (refCount) vì cả 2 service
 * (Báo cáo, QR) đều có thể gọi hiện/ẩn cùng lúc. */
public final class LockBubble {

    private static WindowManager wm;
    private static TextView bubble;
    private static int refCount = 0;

    private LockBubble() {}

    public static synchronized void acquire(Context ctx) {
        refCount++;
        if (bubble != null) return;
        show(ctx.getApplicationContext());
    }

    public static synchronized void release() {
        refCount--;
        if (refCount <= 0) {
            hide();
            refCount = 0;
        }
    }

    public static synchronized void refresh(Context ctx) {
        if (bubble != null) {
            bubble.setText(Prefs.locked(ctx) ? "🔒" : "🔓");
        }
    }

    private static void show(Context ctx) {
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        bubble = new TextView(ctx);
        bubble.setText(Prefs.locked(ctx) ? "🔒" : "🔓");
        bubble.setTextSize(16);
        bubble.setGravity(android.view.Gravity.CENTER);
        bubble.setBackground(ctx.getResources().getDrawable(R.drawable.bg_resize_handle));

        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40,
                ctx.getResources().getDisplayMetrics());

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // KHÔNG có FLAG_NOT_TOUCHABLE — luôn bấm được
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = 12;
        lp.y = 180;

        bubble.setOnClickListener(v -> LockManager.toggle(ctx));

        try {
            wm.addView(bubble, lp);
        } catch (Exception ignored) {}
    }

    private static void hide() {
        if (wm != null && bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
        }
        bubble = null;
    }
}
