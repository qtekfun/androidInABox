package dev.probe.notif;

import android.content.*;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Main.ack(c, "ALARM_OK");
    }
}
