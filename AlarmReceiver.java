package com.hasand.digebekhabim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Util.startFg(c, new Intent(c, AlarmService.class));
    }
}
