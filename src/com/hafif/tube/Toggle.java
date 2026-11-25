package com.hafif.tube;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Animasyonlu anahtar. */
final class Toggle extends View {
    interface Listener { void on(boolean v); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int w, h;
    private boolean on;
    private float t;
    private Listener l;

    Toggle(Context c, boolean on) {
        super(c);
        w = U.dp(c, 50);
        h = U.dp(c, 28);
        this.on = on;
        t = on ? 1f : 0f;
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { set(!Toggle.this.on, true); if (l != null) l.on(Toggle.this.on); }
        });
        Ui.press(this, 0.92f);
    }

    void setListener(Listener x) { l = x; }
    boolean isOn() { return on; }

    void set(boolean v, boolean anim) {
        on = v;
        if (!anim) { t = v ? 1f : 0f; invalidate(); return; }
        ValueAnimator a = ValueAnimator.ofFloat(t, v ? 1f : 0f);
        a.setDuration(320);
        a.setInterpolator(new OvershootInterpolator(1.4f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) { t = (Float) va.getAnimatedValue(); invalidate(); }
        });
        a.start();
    }

    @Override protected void onMeasure(int a, int b) { setMeasuredDimension(w, h); }

    @Override protected void onDraw(Canvas c) {
        float f = Math.max(0f, Math.min(1f, t));
        int col = (Integer) new ArgbEvaluator().evaluate(f, Ui.PANEL2, Ui.RED);
        p.setColor(col);
        r.set(0, 0, w, h);
        c.drawRoundRect(r, h / 2f, h / 2f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, U.dp(getContext(), 1)));
        p.setColor(f > 0.5f ? Ui.RED : Ui.STROKE);
        r.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        c.drawRoundRect(r, h / 2f, h / 2f, p);
        p.setStyle(Paint.Style.FILL);
        float pad = h * 0.14f, rad = h / 2f - pad;
        float cx = pad + rad + (w - 2 * pad - 2 * rad) * t;
        p.setColor(0xffffffff);
        c.drawCircle(cx, h / 2f, rad, p);
    }
}
