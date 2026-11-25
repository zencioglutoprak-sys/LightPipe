package com.hafif.tube;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

/** İndirilen videoyu uygulamanın içinde oynatır. */
public class PlayActivity extends Activity {
    private VPlayer vp;
    private boolean land;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Themes.init(this);
        getWindow().setStatusBarColor(0xff000000);
        getWindow().setNavigationBarColor(0xff000000);
        String u = getIntent().getStringExtra("uri");
        if (u == null) { finish(); return; }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);
        vp = new VPlayer(this);
        vp.topBar(getIntent().getStringExtra("name"), new Runnable() {
            @Override public void run() { finish(); }
        });
        vp.setHandlers(new Runnable() {
            @Override public void run() {
                setRequestedOrientation(land ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        }, new Runnable() {
            @Override public void run() { Ui.snack(PlayActivity.this, "Bu video oynatılamadı"); }
        });
        root.addView(vp, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        applyBars(getResources().getConfiguration());
        vp.load(null, Uri.parse(u), null, 0, true);
    }

    private void applyBars(Configuration c) {
        land = c.orientation == Configuration.ORIENTATION_LANDSCAPE;
        vp.setFullIcon(land);
        getWindow().getDecorView().setSystemUiVisibility(land
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                : 0);
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyBars(c);
    }

    @Override protected void onPause() {
        if (vp != null) vp.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (vp != null) vp.release();
        super.onDestroy();
    }
}
