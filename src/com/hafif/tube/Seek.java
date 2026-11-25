package com.hafif.tube;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Kırmızı arama çubuğu: sürüklerken tutamaç büyür. */
final class Seek extends View {
    interface Listener { void onSeek(float frac, boolean done); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float frac, grow;
    private boolean drag;
    private Listener l;
    private final float h3, th;

    Seek(Context c) {
        super(c);
        h3 = U.dp(c, 4);
        th = U.dp(c, 7);
    }

    void setListener(Listener x) { l = x; }
    boolean dragging() { return drag; }

    void setFrac(float f) {
        if (drag) return;
        frac = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    private void growTo(float to) {
        ValueAnimator a = ValueAnimator.ofFloat(grow, to);
        a.setDuration(160);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) { grow = (Float) va.getAnimatedValue(); invalidate(); }
        });
        a.start();
    }

    private float fx(MotionEvent e) {
        float pad = th * 1.6f;
        return Math.max(0f, Math.min(1f, (e.getX() - pad) / Math.max(1f, getWidth() - 2 * pad)));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                drag = true;
                frac = fx(e);
                growTo(1f);
                if (l != null) l.onSeek(frac, false);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                frac = fx(e);
                if (l != null) l.onSeek(frac, false);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                frac = fx(e);
                drag = false;
                growTo(0f);
                if (l != null) l.onSeek(frac, true);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), cy = getHeight() / 2f, pad = th * 1.6f;
        float x0 = pad, x1 = w - pad, x = x0 + (x1 - x0) * frac;
        float hh = h3 * (1f + 0.35f * grow);
        p.setShader(null);
        p.setColor(0x33FFFFFF);
        r.set(x0, cy - hh / 2, x1, cy + hh / 2);
        c.drawRoundRect(r, hh, hh, p);
        p.setShader(new LinearGradient(x0, 0, Math.max(x0 + 1, x), 0, Ui.RED_LO, Ui.RED_HI, Shader.TileMode.CLAMP));
        r.set(x0, cy - hh / 2, Math.max(x0, x), cy + hh / 2);
        c.drawRoundRect(r, hh, hh, p);
        p.setShader(null);
        float rad = th * (1f + 0.45f * grow);
        p.setColor(Ui.alpha(Ui.RED, 0x44));
        c.drawCircle(x, cy, rad * (1.5f + grow * 0.5f), p);
        p.setColor(0xffffffff);
        c.drawCircle(x, cy, rad, p);
        p.setColor(Ui.RED);
        c.drawCircle(x, cy, rad * 0.55f, p);
    }
}
