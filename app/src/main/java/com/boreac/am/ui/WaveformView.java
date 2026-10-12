package com.boreac.am.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public final class WaveformView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean active;
    private double level = 0.18;
    private float phase;

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        line.setColor(0xff47cfff);
        line.setStrokeWidth(2.2f);
        line.setStrokeCap(Paint.Cap.ROUND);
        glow.setColor(0x3539bfff);
    }

    public void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    public void setLevel(double level) {
        this.level = Math.max(0.05, Math.min(1.0, level * 8.0));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cy = h / 2f;

        glow.setShader(new RadialGradient(
                w / 2f, cy, Math.max(w, h) * 0.45f,
                new int[] {0x3339bfff, 0x1039bfff, 0x0039bfff},
                null,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(w / 2f, cy, Math.max(w, h) * 0.42f, glow);

        int bars = 62;
        float step = w / (bars + 1f);
        float amplitude = (float) (h * 0.34 * level);

        for (int i = 0; i < bars; i++) {
            float x = step * (i + 1);
            float centerWeight = 1f - Math.abs((i - bars / 2f) / (bars / 2f));
            float wave = (float) (
                    0.30
                    + 0.70 * Math.abs(Math.sin(i * 0.73 + phase))
            );
            float length = amplitude * centerWeight * wave;
            canvas.drawLine(x, cy - length, x, cy + length, line);
        }

        canvas.drawCircle(w / 2f, cy, 34f, line);
        canvas.drawCircle(w / 2f, cy, 28f, glow);

        if (active) {
            phase += 0.16f;
            postInvalidateDelayed(33);
        }
    }
}
