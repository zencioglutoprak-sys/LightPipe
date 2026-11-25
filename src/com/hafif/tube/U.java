package com.hafif.tube;

import android.content.Context;

final class U {
    private U() {}

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
