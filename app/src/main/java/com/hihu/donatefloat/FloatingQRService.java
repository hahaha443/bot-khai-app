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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

/** Floating menu #2: nhập nội dung tự do (KHÔNG sinh mã ngẫu nhiên) -> gọi
 * /order -> hiện QR VietQR động ngay trên menu. Khi có ai chuyển khoản
 * đúng nội dung này, giao dịch sẽ tự xuất hiện bên menu Báo cáo. */
public class FloatingQRService extends Service {

    private static boolean running = false;
    private static FloatingQRService instance;

    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams params;
    private ImageView qrImage;
    private EditText contentEdit;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static boolean isRunning() { return running; }

    public static void updateSize(Context ctx) {
        if (instance != null) instance.applySize();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        startForegroundWithNotification();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_qr, null);
        contentEdit = floatView.findViewById(R.id.editContent);
        qrImage = floatView.findViewById(R.id.imageQr);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                dp(Prefs.qrSizeDp(this)), WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 340;

        floatView.findViewById(R.id.dragHandleQr)
                .setOnTouchListener(new DragTouchListener(params, wm, floatView));
        floatView.findViewById(R.id.btnGenerateQr).setOnClickListener(v -> generate());

        wm.addView(floatView, params);
    }

    private void generate() {
        String content = contentEdit.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "Nhập nội dung trước đã", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.createOrder(this, content, new ApiClient.Callback<String>() {
            @Override
            public void onSuccess(String qrUrl) {
                ApiClient.downloadBitmap(qrUrl, new ApiClient.Callback<Bitmap>() {
                    @Override
                    public void onSuccess(Bitmap result) {
                        handler.post(() -> qrImage.setImageBitmap(result));
                    }

                    @Override
                    public void onError(String message) {
                        handler.post(() -> Toast.makeText(FloatingQRService.this,
                                "Lỗi tải QR: " + message, Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onError(String message) {
                handler.post(() -> Toast.makeText(FloatingQRService.this,
                        "Lỗi tạo đơn: " + message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void applySize() {
        if (params == null || floatView == null) return;
        params.width = dp(Prefs.qrSizeDp(this));
        wm.updateViewLayout(floatView, params);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
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
                .setContentTitle("Menu tạo QR đang bật")
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
