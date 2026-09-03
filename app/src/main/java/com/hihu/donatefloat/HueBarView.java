package com.hihu.donatefloat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

public class HueBarView extends View {

    public interface OnPicked { void onPicked(float hue); }

    private float hue = 0f;
    private OnPicked listener;
    private final Paint paint = new Paint();

    public HueBarView(Context ctx) { super(ctx); }

    public void setHue(float h) { this.hue = h; invalidate(); }
    public void setListener(OnPicked l) { this.listener = l; }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        int[] colors = new int[13];
        for (int i = 0; i <= 12; i++) colors[i] = Color.HSVToColor(new float[]{i * 30f, 1f, 1f});
        paint.setShader(new LinearGradient(0, 0, 0, h, colors, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.WHITE);
        float my = (hue / 360f) * h;
        canvas.drawRect(0, my - 4, w, my + 4, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float y = Math.max(0, Math.min(getHeight(), event.getY()));
        hue = getHeight() == 0 ? 0 : (y / getHeight()) * 360f;
        invalidate();
        if (listener != null) listener.onPicked(hue);
        return true;
    }
}
