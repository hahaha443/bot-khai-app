package com.hihu.donatefloat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Floating menu #3: NHIỀU "ghi chú nổi" cùng lúc, mỗi cái tự kéo/resize
 * và khoá RIÊNG (chạm 4 lần liên tiếp trên dải mỏng của chính nó). */
public class FloatingNoteService extends Service {

    private static boolean running = false;
    private static FloatingNoteService instance;

    private WindowManager wm;
    private final List<View> windows = new ArrayList<>();
    private final List<LockButtonWindow> lockButtons = new ArrayList<>();

    public static boolean isRunning() { return running; }

    public static void setHidden(boolean hidden) {
        if (instance != null) {
            for (View v : instance.windows) v.setVisibility(hidden ? View.GONE : View.VISIBLE);
        }
    }

    public static void updateSize(Context ctx) {
        if (instance != null) instance.applyAllSize();
    }

    public static void resetSize(Context ctx) {
        Prefs.setNoteSizeDp(ctx, 220);
        Prefs.setNoteHeightDp(ctx, 90);
        if (instance != null) instance.applyAllSize();
    }

    public static void updateOpacity(Context ctx) {
        if (instance != null) instance.applyAllOpacity();
    }

    public static void updateBgOpacity(Context ctx) {
        if (instance != null) instance.applyAllOpacity();
    }

    public static void updateContentOpacity(Context ctx) {
        if (instance != null) instance.applyAllOpacity();
    }

    public static void updateTextColor(Context ctx) {
        if (instance != null) instance.applyAllTextColor();
    }

    public static void rebuild(Context ctx) {
        if (instance != null) instance.doRebuild();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        startForegroundWithNotification();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildAllWindows();
    }

    private void doRebuild() {
        removeAllWindows();
        buildAllWindows();
    }

    private void buildAllWindows() {
        List<String[]> notes = readNotes();
        int index = 0;
        for (String[] note : notes) {
            addWindowFor(note[0], note[1], index);
            index++;
        }
    }

    private void addWindowFor(String id, String text, int index) {
        View floatView = LayoutInflater.from(this).inflate(R.layout.floating_note, null);
        TextView contentView = floatView.findViewById(R.id.noteContent);
        contentView.setText(text);
        contentView.setTextColor(Prefs.noteTextColor(this));

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(Prefs.noteSizeDp(this)), dp(Prefs.noteHeightDp(this)),
                type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20 + (index * 24);
        params.y = 560 + (index * 24);
        floatView.setTag(params);
        applyOpacityFor(floatView);

        try {
            wm.addView(floatView, params);
        } catch (Exception ignored) {
            return;
        }
        windows.add(floatView);
        if (Prefs.overlaysHidden(this)) floatView.setVisibility(View.GONE);

        String lockKey = "note_" + id;
        View dragHandleNote = floatView.findViewById(R.id.dragHandleNote);
        LockButtonWindow lockButton = new LockButtonWindow(this, wm, lockKey, floatView, params);
        dragHandleNote.setOnTouchListener(new DragLockListener(this, params, wm, floatView,
                () -> lockButton.updatePosition()));
        lockButton.create();
        lockButtons.add(lockButton);

        int min = dp(60);
        CornerResizeListener.OnResized onResized = (w, h) -> {
            Prefs.setNoteSizeDp(this, pxToDp(w));
            Prefs.setNoteHeightDp(this, pxToDp(h));
        };
        bindCorner(floatView, R.id.resizeNoteTL, CornerResizeListener.Corner.TOP_LEFT, min, params, onResized, lockButton);
        bindCorner(floatView, R.id.resizeNoteTR, CornerResizeListener.Corner.TOP_RIGHT, min, params, onResized, lockButton);
        bindCorner(floatView, R.id.resizeNoteBL, CornerResizeListener.Corner.BOTTOM_LEFT, min, params, onResized, lockButton);
        bindCorner(floatView, R.id.resizeNoteBR, CornerResizeListener.Corner.BOTTOM_RIGHT, min, params, onResized, lockButton);
    }

    private void bindCorner(View floatView, int viewId, CornerResizeListener.Corner corner, int min,
                             WindowManager.LayoutParams params, CornerResizeListener.OnResized onResized,
                             LockButtonWindow lockButton) {
        View v = floatView.findViewById(viewId);
        v.setOnTouchListener(new CornerResizeListener(params, wm, floatView, corner, min, onResized,
                () -> lockButton.updatePosition()));
    }

    private void applyAllSize() {
        for (int i = 0; i < windows.size(); i++) {
            View v = windows.get(i);
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) v.getTag();
            p.width = dp(Prefs.noteSizeDp(this));
            p.height = dp(Prefs.noteHeightDp(this));
            try { wm.updateViewLayout(v, p); } catch (Exception ignored) {}
            if (i < lockButtons.size()) lockButtons.get(i).updatePosition();
        }
    }

    private void applyAllOpacity() {
        for (View v : windows) applyOpacityFor(v);
    }

    /** Tách riêng: mờ NỀN (khung) không đụng chữ, mờ NỘI DUNG (chữ) không đụng nền. */
    private void applyOpacityFor(View floatView) {
        android.view.ViewGroup panel = floatView.findViewById(R.id.notePanel);
        if (panel != null && panel.getBackground() != null) {
            panel.getBackground().mutate().setAlpha((int) (Prefs.noteBgOpacity(this) / 100f * 255));
        }
        TextView tv = floatView.findViewById(R.id.noteContent);
        if (tv != null) tv.setAlpha(Prefs.noteContentOpacity(this) / 100f);
    }

    private void applyAllTextColor() {
        for (View v : windows) {
            TextView tv = v.findViewById(R.id.noteContent);
            if (tv != null) tv.setTextColor(Prefs.noteTextColor(this));
        }
    }

    private void removeAllWindows() {
        for (View v : windows) {
            try { wm.removeView(v); } catch (Exception ignored) {}
        }
        windows.clear();
        for (LockButtonWindow lb : lockButtons) lb.destroy();
        lockButtons.clear();
    }

    private List<String[]> readNotes() {
        List<String[]> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Prefs.notesListRaw(this));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(new String[]{o.getString("id"), o.getString("text")});
            }
        } catch (Exception ignored) {}
        return result;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private int pxToDp(int px) {
        return (int) (px / getResources().getDisplayMetrics().density);
    }

    private void startForegroundWithNotification() {
        String channelId = "donate_note_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Menu ghi chú nổi", NotificationManager.IMPORTANCE_MIN);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Menu ghi chú đang bật")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
        startForeground(2003, notification);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        instance = null;
        removeAllWindows();
    }
}
