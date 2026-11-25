package com.hafif.tube;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Dönen plak. */
final class Disc extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int sizePx;
    private ObjectAnimator spin;

    Disc(Context c, float sizeDp) {
        super(c);
        sizePx = U.dp(c, sizeDp);
        spin = ObjectAnimator.ofFloat(this, "rotation", 0f, 360f);
        spin.setDuration(9000);
        spin.setInterpolator(new LinearInterpolator());
        spin.setRepeatCount(ValueAnimator.INFINITE);
    }

    void setSpinning(boolean on) {
        if (on) { if (!spin.isStarted()) spin.start(); else spin.resume(); }
        else if (spin.isStarted()) spin.pause();
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(sizePx, sizePx); }

    @Override protected void onDetachedFromWindow() {
        spin.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, R = cx - 2;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, R, new int[]{0xff2A1A1D, 0xff120A0C, 0xff000000}, new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        for (int i = 0; i < 9; i++) {
            float rr = R * (0.46f + i * 0.055f);
            p.setColor(i % 2 == 0 ? 0x22FFFFFF : 0x12FFFFFF);
            c.drawCircle(cx, cy, rr, p);
        }
        // ışık yansıması
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(R * 0.08f);
        p.setColor(0x14FFFFFF);
        r.set(cx - R * 0.8f, cy - R * 0.8f, cx + R * 0.8f, cy + R * 0.8f);
        c.drawArc(r, 200, 50, false, p);
        c.drawArc(r, 20, 50, false, p);
        // etiket
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, R * 0.4f, new int[]{Ui.RED_HI, Ui.RED_LO}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R * 0.38f, p);
        p.setShader(null);
        p.setColor(0x33000000);
        c.drawCircle(cx, cy, R * 0.30f, p);
        p.setColor(0xffffffff);
        // küçük nota: sarmal çizgiler yerine basit işaret
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(R * 0.035f);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(cx - R * 0.08f, cy - R * 0.14f, cx - R * 0.08f, cy + R * 0.1f, p);
        c.drawLine(cx + R * 0.1f, cy - R * 0.18f, cx + R * 0.1f, cy + R * 0.06f, p);
        c.drawLine(cx - R * 0.08f, cy - R * 0.14f, cx + R * 0.1f, cy - R * 0.18f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx - R * 0.13f, cy + R * 0.1f, R * 0.05f, p);
        c.drawCircle(cx + R * 0.05f, cy + R * 0.06f, R * 0.05f, p);
        p.setColor(Ui.BG);
        c.drawCircle(cx, cy + R * 0.27f, R * 0.035f, p); // mil deliği ipucu
    }
}
