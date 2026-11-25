package com.hafif.tube;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** İlerleme çubuğu: hedefe doğru yumuşak kayar, üstünden parlama geçer. */
final class Bar extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float shown, shine;
    private boolean indet;
    private ValueAnimator move, loop;
    private int color = Ui.RED_HI;

    Bar(Context c) { super(c); }

    void setBad(boolean bad) { color = bad ? 0xff888888 : Ui.RED_HI; invalidate(); }

    /** v: 0..1, negatif = belirsiz (kayan parlama). */
    void set(float v, boolean animate) {
        indet = v < 0;
        if (indet) { invalidate(); return; }
        v = Math.min(1f, v);
        if (move != null) move.cancel();
        if (!animate) { shown = v; invalidate(); return; }
        move = ValueAnimator.ofFloat(shown, v);
        move.setDuration(450);
        move.setInterpolator(new DecelerateInterpolator());
        move.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { shown = (Float) a.getAnimatedValue(); invalidate(); }
        });
        move.start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loop = ValueAnimator.ofFloat(0f, 1f);
        loop.setDuration(1300);
        loop.setRepeatCount(ValueAnimator.INFINITE);
        loop.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { shine = (Float) a.getAnimatedValue(); invalidate(); }
        });
        loop.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (loop != null) loop.cancel();
        if (move != null) move.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setShader(null);
        p.setColor(0x22FFFFFF);
        r.set(0, 0, w, h);
        c.drawRoundRect(r, h / 2, h / 2, p);
        float end = indet ? w * 0.35f : w * shown;
        float start = indet ? (w * 1.35f) * shine - w * 0.35f : 0;
        float x1 = Math.min(w, start + end);
        float x0 = Math.max(0, start);
        if (x1 - x0 < 1) return;
        p.setShader(new LinearGradient(x0, 0, x1, 0, Ui.RED_LO, color, Shader.TileMode.CLAMP));
        r.set(x0, 0, x1, h);
        c.drawRoundRect(r, h / 2, h / 2, p);
        if (!indet) { // parlama
            float sx = (w + h * 6) * shine - h * 3;
            p.setShader(new LinearGradient(sx - h * 3, 0, sx + h * 3, 0,
                    new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.save();
            c.clipRect(x0, 0, x1, h);
            r.set(x0, 0, x1, h);
            c.drawRoundRect(r, h / 2, h / 2, p);
            c.restore();
        }
        p.setShader(null);
    }
}
