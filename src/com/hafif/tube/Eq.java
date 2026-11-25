package com.hafif.tube;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Çalan parça göstergesi: dans eden 4 çubuk. */
final class Eq extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int sizePx;
    private ValueAnimator anim;
    private float t;
    private boolean active = true;

    Eq(Context c, float sizeDp, int color) {
        super(c);
        sizePx = U.dp(c, sizeDp);
        p.setColor(color);
    }

    void setActive(boolean a) { active = a; invalidate(); }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(sizePx, sizePx); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1400);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                t = (Float) a.getAnimatedValue();
                if (active) invalidate();
            }
        });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float bw = w / 7f;
        for (int i = 0; i < 4; i++) {
            float f = active
                    ? 0.25f + 0.75f * (float) (Math.sin((t * (2 + i * 0.6)) * 2 * Math.PI + i * 1.7) * 0.5 + 0.5)
                    : 0.2f;
            float bh = h * f;
            float x = bw * (0.5f + i * 1.75f);
            r.set(x, h - bh, x + bw, h);
            c.drawRoundRect(r, bw / 2, bw / 2, p);
        }
    }
}
