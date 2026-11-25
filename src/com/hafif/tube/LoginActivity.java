package com.hafif.tube;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Google/YouTube girişi (WebView). Giriş bitince çerezler kaydedilir, sonraki açılışlarda da hatırlanır. */
public class LoginActivity extends Activity {
    private static final String UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
    private WebView wv;
    private boolean saved;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Themes.init(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        FrameLayout back = new FrameLayout(this);
        back.addView(new Ic(this, Ic.BACK, 22, Ui.TEXT), new FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22), Gravity.CENTER));
        Ui.press(back, 0.88f);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        TextView t = Ui.text(this, "Giriş", 18, Ui.TEXT, true);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView done = Ui.text(this, "Bitti", 14, 0xffffffff, true);
        done.setBackground(Ui.redBox(this, 18));
        done.setPadding(Ui.dp(this, 18), Ui.dp(this, 9), Ui.dp(this, 18), Ui.dp(this, 9));
        Ui.press(done, 0.92f);
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!check()) Ui.snack(LoginActivity.this, "Giriş tamamlanmadı");
            }
        });
        top.addView(done);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        wv = new WebView(this);
        WebSettings st = wv.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setUserAgentString(UA);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);
        wv.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                if (url != null && url.contains("youtube.com")) check();
            }
        });
        root.addView(wv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        wv.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&hl=tr&continue=https%3A%2F%2Fm.youtube.com%2F");
    }

    private boolean check() {
        if (saved) return true;
        CookieManager cm = CookieManager.getInstance();
        String ck = cm.getCookie("https://www.youtube.com");
        if (ck != null && (ck.contains("SAPISID=") || ck.contains("__Secure-3PAPISID="))) {
            saved = true;
            Auth.save(this, ck);
            cm.flush();
            setResult(RESULT_OK);
            Ui.snack(this, "Giriş yapıldı, oturum kaydedildi");
            h.postDelayed(new Runnable() { @Override public void run() { finish(); } }, 900);
            return true;
        }
        return false;
    }

    @Override public void onBackPressed() {
        if (wv != null && wv.canGoBack()) wv.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (wv != null) wv.destroy();
        super.onDestroy();
    }
}
