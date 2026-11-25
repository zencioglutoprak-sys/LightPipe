package com.hafif.tube;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Alttan kayan sayfa (bottom sheet). */
final class Sheet {
    private static final class MaxScroll extends ScrollView {
        final int max;
        MaxScroll(Context c, int max) { super(c); this.max = max; }
        @Override protected void onMeasure(int w, int h) {
            int cap = Math.min(max, View.MeasureSpec.getSize(h));
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST));
        }
    }

    final Activity act;
    final Dialog d;
    final LinearLayout list;
    private final View scrim;
    private final LinearLayout sheet;
    private boolean closing;
    private int count;

    Sheet(Activity a, String title) {
        act = a;
        d = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0));
        w.setLayout(-1, -1);
        w.setGravity(Gravity.BOTTOM);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setWindowAnimations(0);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.setCancelable(false);

        FrameLayout root = new FrameLayout(a);
        scrim = new View(a);
        scrim.setBackgroundColor(0xB0000000);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { close(); }
        });
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        float r = Ui.dp(a, 26);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.PANEL);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        g.setStroke(Math.max(1, Ui.dp(a, 1)), Ui.STROKE);
        sheet.setBackground(g);
        sheet.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 12));
        sheet.setClickable(true);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(sheet, sp);

        View handle = new View(a);
        handle.setBackground(Ui.box(a, Ui.HANDLE, 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, hp);

        if (title != null) {
            TextView t = Ui.text(a, title, 18, Ui.TEXT, true);
            t.setPadding(Ui.dp(a, 22), Ui.dp(a, 16), Ui.dp(a, 22), Ui.dp(a, 8));
            sheet.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        MaxScroll sc = new MaxScroll(a, a.getResources().getDisplayMetrics().heightPixels * 62 / 100);
        sc.setVerticalScrollBarEnabled(false);
        sc.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 4));
        sc.addView(list, new ScrollView.LayoutParams(-1, -2));
        sheet.addView(sc, new LinearLayout.LayoutParams(-1, -2));
        d.setContentView(root);
        d.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
            @Override public boolean onKey(android.content.DialogInterface di, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) { close(); return true; }
                return code == KeyEvent.KEYCODE_BACK;
            }
        });
    }

    View root() { return sheet; }

    void header(String s) {
        TextView t = Ui.text(act, s, 12, Ui.RED, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 6));
        list.addView(t);
    }

    /** İkonlu satır; sub boş olabilir. */
    View item(int icon, String text, String sub, final Runnable r) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.box(act, Ui.PANEL2, Ui.STROKE, 16));
        row.setPadding(Ui.dp(act, 14), Ui.dp(act, 13), Ui.dp(act, 14), Ui.dp(act, 13));
        if (icon >= 0) {
            FrameLayout tile = new FrameLayout(act);
            tile.setBackground(Ui.box(act, Ui.REDSOFT, 12));
            Ic ic = new Ic(act, icon, 20, Ui.RED_HI);
            tile.addView(ic, new FrameLayout.LayoutParams(Ui.dp(act, 20), Ui.dp(act, 20), Gravity.CENTER));
            row.addView(tile, new LinearLayout.LayoutParams(Ui.dp(act, 38), Ui.dp(act, 38)));
        }
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView a = Ui.text(act, text, 15, Ui.TEXT, true);
        col.addView(a);
        if (sub != null && !sub.isEmpty()) {
            TextView b = Ui.text(act, sub, 12, Ui.SUB, false);
            b.setPadding(0, Ui.dp(act, 3), 0, 0);
            col.addView(b);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = icon >= 0 ? Ui.dp(act, 12) : 0;
        row.addView(col, cp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(act, 8);
        list.addView(row, lp);
        Ui.press(row);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { close(); if (r != null) r.run(); }
        });
        count++;
        return row;
    }

    void add(View v) { list.addView(v); }

    void show() {
        d.show();
        scrim.animate().alpha(1f).setDuration(260).setStartDelay(0).setListener(null).start();
        sheet.setTranslationY(act.getResources().getDisplayMetrics().heightPixels);
        Ui.an(sheet, 380, 0, new DecelerateInterpolator(2.2f)).translationY(0f).start();
        for (int i = 0; i < list.getChildCount() && i < 14; i++) {
            View c = list.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(Ui.dp(act, 18));
            Ui.an(c, 360, 120 + i * 35L, new DecelerateInterpolator(2f)).alpha(1f).translationY(0f).start();
        }
    }

    void close() {
        if (closing) return;
        closing = true;
        scrim.animate().alpha(0f).setDuration(220).setStartDelay(0).setListener(null).start();
        Ui.an(sheet, 240, 0, new AccelerateInterpolator(1.6f)).translationY(sheet.getHeight() + Ui.dp(act, 20))
                .withEndAction(new Runnable() {
                    @Override public void run() { try { d.dismiss(); } catch (Exception ignored) {} }
                }).start();
    }
}
