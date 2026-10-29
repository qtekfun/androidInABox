package dev.probe.notif;

import android.app.RemoteInput;
import android.content.*;
import android.os.Bundle;
import android.util.Log;

public class ReplyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Bundle r = RemoteInput.getResultsFromIntent(i);
        Main.ack(c, "REPLY_BR_OK text=" + (r == null ? null : r.getCharSequence("reply")));
    }
}
