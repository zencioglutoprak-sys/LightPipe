package com.hafif.tube;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

/** 10 renk teması. Seçim kalıcıdır; Ui renkleri buradan doldurulur. */
final class Themes {
    private Themes() {}

    static final String[] NAMES = {"Kırmızı", "AMOLED", "Okyanus", "Orman", "Mor Gece", "Gün Batımı", "Pembe", "Turkuaz", "Altın", "Grafit"};
    // her tema için ana ton (derece) ve doygunluk/parlaklık ayarı; {ton, vurgu parlaklığı, arka plan doygunluğu}
    private static final float[][] P = {
            {355, .92f, .36f},  // 0 kırmızı (özel değerlerle)
            {0, .92f, 0f},      // 1 amoled (özel)
            {208, .90f, .45f},  // 2 okyanus
            {140, .70f, .40f},  // 3 orman
            {268, .88f, .42f},  // 4 mor gece
            {24, .94f, .50f},   // 5 gün batımı
            {330, .90f, .42f},  // 6 pembe
            {178, .74f, .45f},  // 7 turkuaz
            {42, .86f, .50f},   // 8 altın
            {215, .62f, .10f},  // 9 grafit
    };

    private static int cur = -1;

    static int count() { return NAMES.length; }
    static int current() { return cur < 0 ? 0 : cur; }

    /** Süreç başına bir kez: kayıtlı temayı yükler ve pencere arka planını ayarlar. */
    static void init(Activity a) {
        if (cur < 0) apply(Api.prefs(a).getInt("theme", 0));
        try {
            a.getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
            a.getWindow().setStatusBarColor(Ui.BG);
            a.getWindow().setNavigationBarColor(Ui.BG);
        } catch (Exception ignored) {}
    }

    static void save(Context c, int i) {
        Api.prefs(c).edit().putInt("theme", i).apply();
        apply(i);
    }

    private static int hsv(float h, float s, float v) { return Color.HSVToColor(new float[]{h, Math.max(0f, Math.min(1f, s)), Math.max(0f, Math.min(1f, v))}); }

    static int swatch(int i) { return i == 0 ? 0xffEF4444 : i == 1 ? 0xff111111 : hsv(P[i][0], .80f, P[i][1]); }

    static void apply(int i) {
        if (i < 0 || i >= P.length) i = 0;
        cur = i;
        if (i == 0) {
            Ui.BG = 0xff0B0708; Ui.PANEL = 0xff150E10; Ui.PANEL2 = 0xff1E1417; Ui.STROKE = 0xff2E1D20;
            Ui.RED = 0xffEF4444; Ui.RED_HI = 0xffFF5A52; Ui.RED_LO = 0xffC4222F; Ui.TEXT = 0xffF7EFF0;
            Ui.SUB = 0xffAA9B9D; Ui.DIM = 0xff6F6062; Ui.REDSOFT = 0x33EF4444;
            Ui.STROKE2 = 0xff4A2328; Ui.SNACK = 0xF0241618; Ui.HANDLE = 0xff3A2428; Ui.TOPTINT = 0xff2A1015; Ui.STATUSTOP = 0xff1A0B0E;
            return;
        }
        if (i == 1) { // AMOLED: saf siyah, kırmızı vurgu
            Ui.BG = 0xff000000; Ui.PANEL = 0xff0A0A0A; Ui.PANEL2 = 0xff141414; Ui.STROKE = 0xff262626;
            Ui.RED = 0xffEF4444; Ui.RED_HI = 0xffFF5A52; Ui.RED_LO = 0xffC4222F; Ui.TEXT = 0xffF5F5F5;
            Ui.SUB = 0xffA3A3A3; Ui.DIM = 0xff6B6B6B; Ui.REDSOFT = 0x33EF4444;
            Ui.STROKE2 = 0xff3A3A3A; Ui.SNACK = 0xF01A1A1A; Ui.HANDLE = 0xff333333; Ui.TOPTINT = 0xff1A1A1A; Ui.STATUSTOP = 0xff000000;
            return;
        }
        float h = P[i][0], av = P[i][1], bs = P[i][2];
        Ui.BG = hsv(h, bs, .045f);
        Ui.PANEL = hsv(h, bs * .95f, .085f);
        Ui.PANEL2 = hsv(h, bs * .90f, .125f);
        Ui.STROKE = hsv(h, bs * .75f, .19f);
        Ui.STROKE2 = hsv(h, bs * .85f, .27f);
        Ui.HANDLE = hsv(h, bs * .80f, .24f);
        Ui.SNACK = (0xF0 << 24) | (hsv(h, bs * .90f, .14f) & 0x00FFFFFF);
        Ui.TOPTINT = hsv(h, bs * .90f, .17f);
        Ui.STATUSTOP = hsv(h, bs * .90f, .09f);
        Ui.TEXT = hsv(h, .04f, .97f);
        Ui.SUB = hsv(h, .09f, .70f);
        Ui.DIM = hsv(h, .09f, .46f);
        Ui.RED = hsv(h, .80f, av);
        Ui.RED_HI = hsv(h, .68f, Math.min(1f, av + .06f));
        Ui.RED_LO = hsv(h, .88f, av * .78f);
        Ui.REDSOFT = (0x33 << 24) | (Ui.RED & 0x00FFFFFF);
    }
}
