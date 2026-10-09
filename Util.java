package com.hasand.digebekhabim;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.ArrayList;
import java.util.Calendar;

public class Util {
    static final String CH_MON = "mon", CH_ALM = "alm";

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CH_MON, "Snore monitor", NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel(CH_ALM, "Alarm", NotificationManager.IMPORTANCE_HIGH));
        }
    }

    static void startFg(Context c, Intent i) {
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    static Vibrator vib(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager m = (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return m.getDefaultVibrator();
        }
        return (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @SuppressWarnings("deprecation")
    static void vibrate(Context c, long[] p) {
        Vibrator v = vib(c);
        if (v == null || !v.hasVibrator()) return;
        v.cancel();
        if (Build.VERSION.SDK_INT >= 26) {
            AudioAttributes at = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            v.vibrate(VibrationEffect.createWaveform(p, -1), at);
        } else {
            v.vibrate(p, -1);
        }
    }

    static void cancelVib(Context c) {
        Vibrator v = vib(c);
        if (v != null) v.cancel();
    }

    static long[] build(String type, int sec) {
        long[] b;
        if ("soft".equals(type)) b = new long[]{200, 400};
        else if ("wave".equals(type)) b = new long[]{100, 100, 200, 100, 300, 100, 200, 100, 100, 500};
        else if ("strong".equals(type)) b = new long[]{600, 200};
        else b = new long[]{90, 110, 90, 700};
        ArrayList<Long> l = new ArrayList<>();
        l.add(0L);
        long t = 0;
        while (t < sec * 1000L) {
            for (long x : b) { l.add(x); t += x; }
        }
        long[] r = new long[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("alarm", Context.MODE_PRIVATE);
    }

    static long nextTrigger(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("on", false)) return -1;
        int h = p.getInt("h", 7), m = p.getInt("m", 0);
        boolean rep = p.getBoolean("rep", false);
        String days = p.getString("days", "0123456");
        Calendar now = Calendar.getInstance();
        for (int i = 0; i < 8; i++) {
            Calendar t = Calendar.getInstance();
            t.add(Calendar.DAY_OF_YEAR, i);
            t.set(Calendar.HOUR_OF_DAY, h);
            t.set(Calendar.MINUTE, m);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (t.after(now)) {
                int dow = t.get(Calendar.DAY_OF_WEEK) - 1;
                if (!rep || days.indexOf(String.valueOf(dow)) >= 0) return t.getTimeInMillis();
            }
        }
        return -1;
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent op = PendingIntent.getBroadcast(c, 1, new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(op);
        long t = nextTrigger(c);
        if (t < 0) return;
        PendingIntent show = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), op);
    }

    static void afterFire(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("rep", false)) p.edit().putBoolean("on", false).apply();
        schedule(c);
    }
}
