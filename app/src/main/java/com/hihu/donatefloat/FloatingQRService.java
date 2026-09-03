package com.hihu.donatefloat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

/** Floating menu #2: QR TĨNH của tài khoản — không addInfo, không mã cố
 * định. Kéo góc bất kỳ để resize. Chạm 4 lần liên tiếp trên dải mỏng đỉnh
 * panel để khoá/mở khoá RIÊNG panel này. */
public class FloatingQRService extends Service {

    private static boolean running = false;
    private static FloatingQRService instance;

    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams params;
    private ImageView qrImage;
    private TextView statusText;
    
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static boolean isRunning() { return running; }

    public static void updateSize(Context ctx) {
        if (instance != null) instance.applySize();
    }

    public static void updateOpacity(Context ctx) {
        if (instance != null) instance.applyOpacity();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        startForegroundWithNotification();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_qr, null);
        qrImage = floatView.findViewById(R.id.imageQr);
        statusText = floatView.findViewById(R.id.textQrStatus);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                dp(Prefs.qrSizeDp(this)), dp(Prefs.qrHeightDp(this)),
                type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 340;

        applyOpacity();
        wm.addView(floatView, params);

        floatView.findViewById(R.id.dragHandleQr)
                .setOnTouchListener(new DragLockListener(this, params, wm, floatView, "qr"));

        wireCornerResize();
        loadStaticQr();
    }

    private void wireCornerResize() {
        int min = dp(80);
        CornerResizeListener.OnResized onResized = (w, h) -> {
            Prefs.setQrSizeDp(this, pxToDp(w));
            Prefs.setQrHeightDp(this, pxToDp(h));
        };
        bindCorner(R.id.resizeQrTL, CornerResizeListener.Corner.TOP_LEFT, min, onResized);
        bindCorner(R.id.resizeQrTR, CornerResizeListener.Corner.TOP_RIGHT, min, onResized);
        bindCorner(R.id.resizeQrBL, CornerResizeListener.Corner.BOTTOM_LEFT, min, onResized);
        bindCorner(R.id.resizeQrBR, CornerResizeListener.Corner.BOTTOM_RIGHT, min, onResized);
    }

    private void bindCorner(int viewId, CornerResizeListener.Corner corner, int min,
                             CornerResizeListener.OnResized onResized) {
        View v = floatView.findViewById(viewId);
        v.setOnTouchListener(new CornerResizeListener(params, wm, floatView, corner, min, onResized, this, "qr"));
    }

    private void loadStaticQr() {
        statusText.setText("Đang tải QR...");
        ApiClient.getConfig(this, new ApiClient.Callback<ApiClient.BankConfig>() {
            @Override
            public void onSuccess(ApiClient.BankConfig cfg) {
                String url = ApiClient.buildStaticQrUrl(cfg);
                ApiClient.downloadBitmap(url, new ApiClient.Callback<Bitmap>() {
                    @Override
                    public void onSuccess(Bitmap result) {
                        handler.post(() -> {
                            Object old = qrImage.getDrawable();
                            if (old instanceof android.graphics.drawable.BitmapDrawable) {
                                Bitmap oldBmp = ((android.graphics.drawable.BitmapDrawable) old).getBitmap();
                                if (oldBmp != null && oldBmp != result && !oldBmp.isRecycled()) oldBmp.recycle();
                            }
                            qrImage.setImageBitmap(result);
                            statusText.setVisibility(View.GONE);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        handler.post(() -> {
                            statusText.setVisibility(View.VISIBLE);
                            statusText.setText("Lỗi tải QR: " + message);
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                handler.post(() -> statusText.setText("Lỗi lấy cấu hình: " + message));
            }
        });
    }

    private void applySize() {
        if (params == null || floatView == null) return;
        params.width = dp(Prefs.qrSizeDp(this));
        params.height = dp(Prefs.qrHeightDp(this));
        wm.updateViewLayout(floatView, params);
    }

    private void applyOpacity() {
        if (floatView == null) return;
        floatView.setAlpha(Prefs.qrOpacity(this) / 100f);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private int pxToDp(int px) {
        return (int) (px / getResources().getDisplayMetrics().density);
    }

    private void startForegroundWithNotification() {
        String channelId = "donate_qr_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Menu QR donate", NotificationManager.IMPORTANCE_MIN);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Menu QR đang bật")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
        startForeground(2002, notification);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        instance = null;
        if (wm != null && floatView != null) wm.removeView(floatView);
    }
}
