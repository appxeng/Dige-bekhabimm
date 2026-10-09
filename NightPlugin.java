package com.hasand.digebekhabim;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Base64;
import android.view.WindowManager;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileOutputStream;

@CapacitorPlugin(name = "Night", permissions = {
        @Permission(alias = "mic", strings = {Manifest.permission.RECORD_AUDIO}),
        @Permission(alias = "notif", strings = {"android.permission.POST_NOTIFICATIONS"})
})
public class NightPlugin extends Plugin {

    @Override
    public void load() {
        MonitorService.listener = (type, level, text) -> {
            if (getActivity() == null) return;
            final JSObject o = new JSObject();
            o.put("type", type);
            o.put("level", level);
            if (text != null) o.put("text", text);
            getActivity().runOnUiThread(() -> notifyListeners("monitor", o));
        };
    }

    @PluginMethod
    public void pattern(PluginCall call) {
        try {
            JSArray a = call.getArray("pattern");
            if (a == null) { call.reject("no pattern"); return; }
            long[] p = new long[a.length()];
            for (int i = 0; i < p.length; i++) p[i] = a.getLong(i);
            Util.vibrate(getContext(), p);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.toString());
        }
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        Util.cancelVib(getContext());
        call.resolve();
    }

    @PluginMethod
    public void settings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_SOUND_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void awake(PluginCall call) {
        final boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        getActivity().runOnUiThread(() -> {
            if (on) getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
        call.resolve();
    }

    @PluginMethod
    public void dim(PluginCall call) {
        final boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        getActivity().runOnUiThread(() -> {
            WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
            lp.screenBrightness = on ? 0.02f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getActivity().getWindow().setAttributes(lp);
        });
        call.resolve();
    }

    @PluginMethod
    public void monitorStart(PluginCall call) {
        if (getPermissionState("mic") != PermissionState.GRANTED) {
            requestPermissionForAliases(new String[]{"mic", "notif"}, call, "permCb");
            return;
        }
        startMon(call);
    }

    @PermissionCallback
    private void permCb(PluginCall call) {
        if (getPermissionState("mic") == PermissionState.GRANTED) startMon(call);
        else call.reject("mic denied");
    }

    private void startMon(PluginCall call) {
        int sens = call.getInt("sens", 5);
        int rest = call.getInt("rest", 45);
        int vsec = call.getInt("vsec", 30);
        Intent i = new Intent(getContext(), MonitorService.class);
        i.putExtra("sens", sens);
        i.putExtra("rest", rest);
        i.putExtra("vsec", vsec);
        i.putExtra("vtype", call.getString("vtype", "heart"));
        Util.startFg(getContext(), i);
        call.resolve();
    }

    @PluginMethod
    public void monitorStop(PluginCall call) {
        getContext().startService(new Intent(getContext(), MonitorService.class).setAction("STOP"));
        call.resolve();
    }

    @PluginMethod
    public void alarmSet(PluginCall call) {
        try {
            Context c = getContext();
            SharedPreferences.Editor e = Util.prefs(c).edit();
            e.putBoolean("on", true);
            e.putInt("h", call.getInt("h", 7));
            e.putInt("m", call.getInt("m", 0));
            e.putBoolean("rep", Boolean.TRUE.equals(call.getBoolean("rep", false)));
            e.putString("days", call.getString("days", "0123456"));
            e.putString("tune", call.getString("tune", ""));
            String data = call.getString("data", "");
            File f = new File(c.getFilesDir(), "alarm_custom");
            if (data != null && data.contains(",")) {
                byte[] b = Base64.decode(data.substring(data.indexOf(',') + 1), Base64.DEFAULT);
                FileOutputStream o = new FileOutputStream(f);
                o.write(b);
                o.close();
                e.putString("file", f.getAbsolutePath());
            } else {
                f.delete();
                e.putString("file", "");
            }
            e.apply();
            Util.schedule(c);
            call.resolve();
        } catch (Exception ex) {
            call.reject(ex.toString());
        }
    }

    @PluginMethod
    public void alarmCancel(PluginCall call) {
        Util.prefs(getContext()).edit().putBoolean("on", false).apply();
        Util.schedule(getContext());
        call.resolve();
    }

    @PluginMethod
    public void alarmRinging(PluginCall call) {
        JSObject o = new JSObject();
        o.put("ringing", AlarmService.ringing);
        o.put("on", Util.prefs(getContext()).getBoolean("on", false));
        call.resolve(o);
    }

    @PluginMethod
    public void alarmStop(PluginCall call) {
        getContext().startService(new Intent(getContext(), AlarmService.class).setAction("STOP"));
        call.resolve();
    }
}
