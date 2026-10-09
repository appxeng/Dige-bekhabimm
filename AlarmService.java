package com.hasand.digebekhabim;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public class AlarmService extends Service {
    static volatile boolean ringing = false;
    private AudioTrack at;
    private MediaPlayer mp;
    private PowerManager.WakeLock wl;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int id) {
        if (in != null && "STOP".equals(in.getAction())) { finish(); return START_NOT_STICKY; }
        if (ringing) return START_NOT_STICKY;
        ringing = true;
        Util.afterFire(this);
        Util.channels(this);
        PendingIntent show = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stp = PendingIntent.getService(this, 4, new Intent(this, AlarmService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, Util.CH_ALM) : new Notification.Builder(this);
        b.setContentTitle("زنگ بیدارباش").setContentText("برای خاموش کردن بزنید")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(show)
                .setCategory(Notification.CATEGORY_ALARM).setFullScreenIntent(show, true).setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "خاموش", stp);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(12, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK); else startForeground(12, n);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dige:alarm");
        wl.acquire(3 * 60 * 1000L);
        play();
        Util.vibrate(this, Util.build("strong", 120));
        h.postDelayed(this::finish, 120000);
        return START_NOT_STICKY;
    }

    private void play() {
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        String file = Util.prefs(this).getString("file", "");
        if (file.length() > 0) {
            try {
                mp = new MediaPlayer();
                mp.setAudioAttributes(aa);
                mp.setDataSource(file);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                return;
            } catch (Exception e) {
                if (mp != null) { mp.release(); mp = null; }
            }
        }
        String t = Util.prefs(this).getString("tune", "");
        int[] f;
        try {
            String[] parts = t.split(",");
            f = new int[parts.length];
            for (int i = 0; i < parts.length; i++) f[i] = Integer.parseInt(parts[i].trim());
        } catch (Exception e) {
            f = new int[]{523, 659, 784, 659};
        }
        final int SR = 22050;
        int per = (int) (SR * 0.4);
        int total = per * (f.length + 1);
        short[] d = new short[total];
        for (int k = 0; k < f.length; k++) {
            for (int i = 0; i < per; i++) {
                double tt = (double) i / SR;
                double env = Math.min(1.0, i / (SR * 0.02)) * Math.exp(-4.0 * i / per);
                double v = 2 / Math.PI * Math.asin(Math.sin(2 * Math.PI * f[k] * tt)) * env;
                d[k * per + i] = (short) (v * 22000);
            }
        }
        AudioAttributes sa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        at = new AudioTrack.Builder().setAudioAttributes(sa)
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(total * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
        at.write(d, 0, total);
        at.setLoopPoints(0, total, -1);
        at.play();
    }

    private void finish() {
        ringing = false;
        h.removeCallbacksAndMessages(null);
        try { if (at != null) { at.stop(); at.release(); } } catch (Exception e) { }
        at = null;
        try { if (mp != null) { mp.stop(); mp.release(); } } catch (Exception e) { }
        mp = null;
        Util.cancelVib(this);
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception e) { }
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() { ringing = false; super.onDestroy(); }
}
