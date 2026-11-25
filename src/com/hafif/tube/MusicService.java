package com.hafif.tube;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.IBinder;

/** Müzik çalarken uygulama arka plana gidince de çalmaya devam etsin diye bildirimli ön plan servisi. */
public class MusicService extends Service {
    private static final String CH = "music";
    private static final int ID = 7;
    private boolean fg;

    private final Music.Listener ml = new Music.Listener() {
        @Override public void onChange() { refresh(); }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(CH, "Müzik", NotificationManager.IMPORTANCE_LOW));
        Music.listen(ml);
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        String a = i == null ? null : i.getStringExtra("a");
        if ("toggle".equals(a)) Music.toggle();
        else if ("next".equals(a)) Music.next();
        else if ("prev".equals(a)) Music.prev();
        else if ("stop".equals(a)) { Music.stop(); return START_NOT_STICKY; }
        refresh();
        return START_NOT_STICKY;
    }

    private PendingIntent act(String a, int code) {
        Intent i = new Intent(this, MusicService.class).putExtra("a", a);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification build() {
        Music.Track t = Music.current();
        boolean pl = Music.playing();
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MusicActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(t == null ? "LightPipe" : Lib.title(t.name))
                .setContentText(L.t(this, pl ? "Çalıyor" : "Duraklatıldı"))
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(pl)
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_previous), L.t(this, "Önceki"), act("prev", 1)).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this,
                        pl ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play), L.t(this, pl ? "Duraklat" : "Çal"), act("toggle", 2)).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_next), L.t(this, "Sonraki"), act("next", 3)).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), L.t(this, "Kapat"), act("stop", 4)).build())
                .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private void refresh() {
        try {
            Notification n = build();
            if (!fg) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                fg = true;
            } else {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(ID, n);
            }
            if (!Music.has()) { stopForeground(true); stopSelf(); fg = false; }
        } catch (Exception ignored) {}
    }

    @Override public void onDestroy() {
        Music.unlisten(ml);
        super.onDestroy();
    }
}
