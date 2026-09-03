package com.hihu.donatefloat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Ô vuông chọn Saturation (ngang) / Value (dọc) cho 1 hue cố định —
 * hơn 500k màu khả dụng liên tục thay vì vài màu định sẵn. */
public class HsvSquareView extends View {

    public interface OnPicked { void onPicked(float sat, float val); }

    private float hue = 0f, sat = 1f, val = 1f;
    private OnPicked listener;
    private final Paint paint = new Paint();

    public HsvSquareView(Context ctx) { super(ctx); }

    public void setHue(float h) { this.hue = h; invalidate(); }
    public void setSatVal(float s, float v) { this.sat = s; this.val = v; invalidate(); }
    public void setListener(OnPicked l) { this.listener = l; }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        paint.setShader(null);
        paint.setColor(Color.HSVToColor(new float[]{hue, 1f, 1f}));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(new LinearGradient(0, 0, w, 0, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(new LinearGradient(0, 0, 0, h, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(sat * w, (1 - val) * h, 14, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = Math.max(0, Math.min(getWidth(), event.getX()));
        float y = Math.max(0, Math.min(getHeight(), event.getY()));
        sat = getWidth() == 0 ? 0 : x / getWidth();
        val = getHeight() == 0 ? 0 : 1 - (y / getHeight());
        invalidate();
        if (listener != null) listener.onPicked(sat, val);
        return true;
    }
}
