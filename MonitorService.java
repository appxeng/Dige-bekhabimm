package com.hasand.digebekhabim;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.ArrayList;

public class MonitorService extends Service {
    interface Listener { void on(String type, double level, String text); }

    static volatile Listener listener;
    static volatile boolean running = false;
    private volatile boolean stop = false;
    private Thread th;
    private PowerManager.WakeLock wl;
    private int sens = 5, rest = 45, vsec = 30;
    private String vtype = "heart";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int id) {
        if (in == null) { stopSelf(); return START_NOT_STICKY; }
        if ("STOP".equals(in.getAction())) { shutdown(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        sens = in.getIntExtra("sens", 5);
        rest = in.getIntExtra("rest", 45);
        vsec = in.getIntExtra("vsec", 30);
        String vt = in.getStringExtra("vtype");
        if (vt != null) vtype = vt;
        Util.channels(this);
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent sp = PendingIntent.getService(this, 3, new Intent(this, MonitorService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, Util.CH_MON) : new Notification.Builder(this);
        b.setContentTitle("دیگه بخوابیم").setContentText("پایش خروپف فعال است")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(pi).setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "توقف", sp);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(11, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE); else startForeground(11, n);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dige:mon");
        wl.acquire(12L * 60 * 60 * 1000);
        running = true;
        stop = false;
        th = new Thread(this::loop);
        th.start();
        return START_NOT_STICKY;
    }

    private void post(String type, double level, String text) {
        Listener l = listener;
        if (l != null) l.on(type, level, text);
    }

    private void sleepMs(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (!stop && System.currentTimeMillis() < end) {
            try { Thread.sleep(250); } catch (InterruptedException e) { return; }
        }
    }

    private void loop() {
        final int SR = 16000;
        final double a1 = 1 - Math.exp(-2 * Math.PI * 900.0 / SR);
        final double a2 = 1 - Math.exp(-2 * Math.PI * 80.0 / SR);
        short[] buf = new short[SR / 10];
        int minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            while (!stop) {
                AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, buf.length * 4));
                if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
                    ar.release();
                    post("err", 0, "میکروفون باز نشد");
                    sleepMs(5000);
                    continue;
                }
                ar.startRecording();
                post("listen", 0, null);
                long end = rest <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + 15000;
                double lp1 = 0, lp2 = 0, floor = -80;
                int warm = 0, tick = 0;
                long inB = 0;
                boolean hit = false;
                ArrayList<Long> bursts = new ArrayList<>();
                while (!stop && System.currentTimeMillis() < end) {
                    int n = ar.read(buf, 0, buf.length);
                    if (n <= 0) { sleepMs(50); continue; }
                    double s = 0;
                    for (int i = 0; i < n; i++) {
                        double x = buf[i] / 32768.0;
                        lp1 += a1 * (x - lp1);
                        lp2 += a2 * (x - lp2);
                        double y = lp1 - lp2;
                        s += y * y;
                    }
                    double db = 10 * Math.log10(s / n + 1e-12);
                    if (warm < 12) {
                        floor = warm == 0 ? db : (floor * warm + db) / (warm + 1);
                        warm++;
                        continue;
                    }
                    double thr = Math.max(floor + (16 - 1.2 * sens), -70);
                    long now = System.currentTimeMillis();
                    if (db > thr) {
                        if (inB == 0) inB = now;
                        if (now - inB > 4000) floor = floor * 0.97 + db * 0.03;
                    } else {
                        if (inB != 0) {
                            long d = now - inB;
                            inB = 0;
                            if (d > 250 && d < 3000) bursts.add(now);
                        }
                        floor = floor * 0.995 + db * 0.005;
                    }
                    for (int i = bursts.size() - 1; i >= 0; i--) {
                        if (now - bursts.get(i) > 15000) bursts.remove(i);
                    }
                    if (++tick % 3 == 0) post("level", Math.max(0, Math.min(1, (db - floor) / 30)), null);
                    if (bursts.size() >= 2) { hit = true; break; }
                }
                ar.stop();
                ar.release();
                if (stop) break;
                if (hit) {
                    post("hit", 0, null);
                    Util.vibrate(this, Util.build(vtype, vsec));
                    sleepMs(vsec * 1000L + 8000);
                } else if (rest > 0) {
                    post("rest", 0, null);
                    sleepMs(rest * 1000L);
                }
            }
        } catch (Exception e) {
            post("err", 0, String.valueOf(e.getMessage()));
            shutdown();
        }
    }

    private void shutdown() {
        stop = true;
        running = false;
        Util.cancelVib(this);
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception e) { }
        stopForeground(true);
        stopSelf();
        post("stopped", 0, null);
    }

    @Override
    public void onDestroy() {
        stop = true;
        running = false;
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception e) { }
        super.onDestroy();
    }
}
