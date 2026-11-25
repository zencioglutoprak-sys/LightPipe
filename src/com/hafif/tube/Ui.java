package com.hafif.tube;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Typeface;

/** Tasarım sistemi: renkler, şekiller, animasyon yardımcıları. */
final class Ui {
    private Ui() {}

    // Renkler temaya göre değişir (Themes.apply). Varsayılan: Kırmızı.
    static int BG = 0xff0B0708, PANEL = 0xff150E10, PANEL2 = 0xff1E1417, STROKE = 0xff2E1D20,
            RED = 0xffEF4444, RED_HI = 0xffFF5A52, RED_LO = 0xffC4222F, TEXT = 0xffF7EFF0,
            SUB = 0xffAA9B9D, DIM = 0xff6F6062, REDSOFT = 0x33EF4444,
            STROKE2 = 0xff4A2328, SNACK = 0xF0241618, HANDLE = 0xff3A2428, TOPTINT = 0xff2A1015, STATUSTOP = 0xff1A0B0E;
    static final int GREEN = 0xff4ADE80;

    static int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    static int dp(Context c, float v) { return U.dp(c, v); }

    static GradientDrawable box(Context c, int fill, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, rDp));
        return g;
    }

    static GradientDrawable box(Context c, int fill, int stroke, float rDp) {
        GradientDrawable g = box(c, fill, rDp);
        g.setStroke(Math.max(1, dp(c, 1)), stroke);
        return g;
    }

    static GradientDrawable redBox(Context c, float rDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{RED_HI, RED_LO});
        g.setCornerRadius(dp(c, rDp));
        return g;
    }

    static GradientDrawable circle(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    static GradientDrawable redCircle() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{RED_HI, RED_LO});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        L.isEn(c); // dil tercihini yükle
        TextView t = new TextView(c) {
            @Override public void setText(CharSequence text, BufferType type) {
                super.setText(L.tr(text), type);
            }
        };
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(false);
        return t;
    }

    static void clipRound(View v, final float rPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rPx);
            }
        });
        v.setClipToOutline(true);
    }

    static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(L.t(c, hint));
        e.setHintTextColor(DIM);
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setBackground(null);
        e.setSaveEnabled(false);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
        try { e.setTextCursorDrawable(R.drawable.cursor); } catch (Throwable ignored) {}
        return e;
    }

    // ------------------------------------------------------------ animasyon

    static android.view.ViewPropertyAnimator an(View v, long dur, long delay, TimeInterpolator ip) {
        return v.animate().setDuration(dur).setStartDelay(delay).setInterpolator(ip).setListener(null);
    }

    static void reset(View v) {
        v.animate().cancel();
        v.setAlpha(1f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
        v.setScaleX(1f);
        v.setScaleY(1f);
    }

    static void rise(View v, long delay) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 24));
        an(v, 420, delay, new DecelerateInterpolator(2f)).alpha(1f).translationY(0f).start();
    }

    static void fadeIn(View v, long delay) {
        v.animate().cancel();
        v.setAlpha(0f);
        an(v, 350, delay, new DecelerateInterpolator()).alpha(1f).start();
    }

    static void popIn(View v, long delay) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setScaleX(0.6f);
        v.setScaleY(0.6f);
        an(v, 380, delay, new OvershootInterpolator(2f)).alpha(1f).scaleX(1f).scaleY(1f).start();
    }

    static void press(View v) { press(v, 0.96f); }

    static void press(View v, final float to) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View x, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    an(x, 75, 0, new DecelerateInterpolator()).scaleX(to).scaleY(to).start();
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    an(x, 180, 0, new OvershootInterpolator(2.5f)).scaleX(1f).scaleY(1f).start();
                }
                return false;
            }
        });
    }

    static void shake(View v) {
        float d = dp(v.getContext(), 12);
        ObjectAnimator o = ObjectAnimator.ofFloat(v, "translationX", 0, -d, d, -d * 0.7f, d * 0.7f, -d * 0.3f, 0);
        o.setDuration(380);
        o.start();
    }

    /** Sonsuz yumuşak nabız (alpha). Dönen animatörü iptal etmek için döndürür. */
    static ObjectAnimator pulse(View v, float from, float to, long ms) {
        ObjectAnimator o = ObjectAnimator.ofFloat(v, "alpha", from, to);
        o.setDuration(ms);
        o.setRepeatCount(ValueAnimator.INFINITE);
        o.setRepeatMode(ValueAnimator.REVERSE);
        o.start();
        return o;
    }

    static ObjectAnimator spinForever(View v, long ms) {
        ObjectAnimator o = ObjectAnimator.ofFloat(v, "rotation", 0f, 360f);
        o.setDuration(ms);
        o.setInterpolator(new LinearInterpolator());
        o.setRepeatCount(ValueAnimator.INFINITE);
        o.start();
        return o;
    }

    /** Arka plan GradientDrawable'ının dolgu rengini animasyonla değiştirir. */
    static void tweenFill(final GradientDrawable g, int from, int to, long ms) {
        ValueAnimator a = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        a.setDuration(ms);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) { g.setColor((Integer) va.getAnimatedValue()); }
        });
        a.start();
    }

    static void tweenText(final TextView t, int from, int to, long ms) {
        ValueAnimator a = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        a.setDuration(ms);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) { t.setTextColor((Integer) va.getAnimatedValue()); }
        });
        a.start();
    }

    // ------------------------------------------------------------ bileşenler

    /** Seçilebilir hap. Rengi animasyonla değişir. */
    static TextView chip(Context c, String s, boolean sel) {
        TextView t = text(c, s, 13, sel ? 0xffffffff : SUB, true);
        t.setGravity(Gravity.CENTER);
        int ph = dp(c, 16), pv = dp(c, 9);
        t.setPadding(ph, pv, ph, pv);
        GradientDrawable g = box(c, sel ? RED : PANEL2, sel ? RED : STROKE, 20);
        t.setBackground(g);
        t.setTag(g);
        press(t, 0.93f);
        return t;
    }

    static void setChip(Context c, TextView t, boolean sel, boolean animate) {
        GradientDrawable g = (GradientDrawable) t.getTag();
        int from = sel ? PANEL2 : RED, to = sel ? RED : PANEL2;
        if (animate) {
            tweenFill(g, from, to, 220);
            tweenText(t, sel ? SUB : 0xffffffff, sel ? 0xffffffff : SUB, 220);
        } else {
            g.setColor(to);
            t.setTextColor(sel ? 0xffffffff : SUB);
        }
        g.setStroke(Math.max(1, dp(c, 1)), sel ? RED : STROKE);
    }

    /** Kırmızı gradyan ana düğme. */
    static TextView redButton(Context c, String s) {
        TextView t = text(c, s, 15, 0xffffffff, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(redBox(c, 16));
        t.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        t.setElevation(dp(c, 4));
        press(t);
        return t;
    }

    static TextView ghostButton(Context c, String s) {
        TextView t = text(c, s, 14, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(box(c, PANEL2, STROKE, 16));
        t.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        press(t);
        return t;
    }

    static ViewGroup.MarginLayoutParams lp(int w, int h, int l, int t, int r, int b, Context c) {
        android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** Alttan kayan küçük bildirim hapı. */
    static void snack(Activity a, String msg) {
        View host = a.findViewById(android.R.id.content);
        if (!(host instanceof FrameLayout)) return;
        final FrameLayout fl = (FrameLayout) host;
        final TextView t = text(a, msg, 14, TEXT, true);
        t.setBackground(box(a, SNACK, STROKE2, 20));
        t.setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 12));
        t.setGravity(Gravity.CENTER);
        t.setElevation(dp(a, 10));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        p.bottomMargin = dp(a, 112);
        p.leftMargin = p.rightMargin = dp(a, 24);
        fl.addView(t, p);
        t.setAlpha(0f);
        t.setTranslationY(dp(a, 28));
        an(t, 300, 0, new OvershootInterpolator(1.6f)).alpha(1f).translationY(0f).start();
        t.postDelayed(new Runnable() {
            @Override public void run() {
                an(t, 240, 0, new DecelerateInterpolator()).alpha(0f).translationY(dp(t.getContext(), 16))
                        .withEndAction(new Runnable() {
                            @Override public void run() { fl.removeView(t); }
                        }).start();
            }
        }, 2400);
    }

    static String time(int ms) {
        int s = Math.max(0, ms / 1000);
        int h = s / 3600, m = (s / 60) % 60, x = s % 60;
        if (h > 0) return h + ":" + (m < 10 ? "0" : "") + m + ":" + (x < 10 ? "0" : "") + x;
        return m + ":" + (x < 10 ? "0" : "") + x;
    }

    static String size(long s) {
        if (s >= 1_000_000_000L) return String.format(java.util.Locale.US, "%.2f GB", s / 1e9);
        if (s >= 1_000_000L) return String.format(java.util.Locale.US, "%.1f MB", s / 1e6);
        return (s / 1000) + " KB";
    }

    /** "… %42 …" gibi durum metninden yüzdeyi çıkarır, yoksa -1. */
    static int pct(String s) {
        int i = s.indexOf(" %");
        if (i < 0) return -1;
        int j = i + 2, v = 0, n = 0;
        while (j < s.length() && Character.isDigit(s.charAt(j)) && n < 3) { v = v * 10 + (s.charAt(j) - '0'); j++; n++; }
        return n == 0 ? -1 : Math.min(100, v);
    }
}
