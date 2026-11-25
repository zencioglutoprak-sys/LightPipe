package com.hafif.tube;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Kırmızı dönen yay. */
final class Spin extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int sizePx;
    private ValueAnimator anim;
    private float t;

    Spin(Context c, float sizeDp) {
        super(c);
        sizePx = U.dp(c, sizeDp);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(3, sizePx / 9f));
        p.setColor(Ui.RED);
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(sizePx, sizePx); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1100);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float pad = p.getStrokeWidth();
        r.set(pad, pad, getWidth() - pad, getHeight() - pad);
        // yay uzunluğu 40°..250° arasında nefes alır
        float breathe = (float) (Math.sin(t * 2 * Math.PI) * 0.5 + 0.5);
        float sweep = 50 + 200 * breathe;
        c.drawArc(r, t * 720f - 90f, sweep, false, p);
    }
}
