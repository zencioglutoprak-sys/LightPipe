package com.hafif.tube;

import android.app.Activity;
import android.view.View;

/** Ana ekran sekmelerinin ortak tabanı. */
abstract class Page {
    final Activity a;
    View root;

    Page(Activity a) { this.a = a; }

    /** Sayfa görünür olduğunda (giriş animasyonları dahil). */
    void resume() {}
    void pause() {}
    void destroy() {}
}
