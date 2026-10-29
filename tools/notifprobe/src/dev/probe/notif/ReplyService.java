package dev.probe.notif;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.util.Log;

public class ReplyService extends Service {
    @Override public int onStartCommand(Intent i, int f, int id) {
        Bundle r = RemoteInput.getResultsFromIntent(i);
        Main.ack(this, "REPLY_SVC_OK text=" + (r == null ? null : r.getCharSequence("reply")) + Main.customOf(i));
        stopSelf();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
