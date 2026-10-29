package dev.probe.notif;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.util.Log;

public class Main extends Activity {
    static final String TAG = "PROBE";
    static final String CH = "probe_msgs";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Main.log("Main.onCreate pkg=" + getPackageName() + " clicked=" + getIntent().getBooleanExtra("clicked", false));
        if (getIntent().getBooleanExtra("clicked", false)) { ack(this, "CONTENT_INTENT_OK"); finish(); return; }
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        post(this);
        finish();
    }

    static void log(String m) { System.err.println("PROBE " + m); }

    static void ack(Context c, String m) {
        log(m);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.notify("ack", 99, new Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_probe)
                .setContentTitle(m).build());
    }

    static void post(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "Mensajes probe", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(c, Main.class).putExtra("clicked", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pOpen = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pBr = PendingIntent.getBroadcast(c, 1, new Intent(c, ReplyReceiver.class), PendingIntent.FLAG_MUTABLE);
        PendingIntent pSv = PendingIntent.getService(c, 2, new Intent(c, ReplyService.class), PendingIntent.FLAG_MUTABLE);
        RemoteInput ri = new RemoteInput.Builder("reply").setLabel("Responder").build();
        Notification.Action aBr = new Notification.Action.Builder(null, "Resp(BR)", pBr).addRemoteInput(ri).build();
        Notification.Action aSv = new Notification.Action.Builder(null, "Resp(SVC)", pSv).addRemoteInput(ri).build();
        Notification.MessagingStyle st = new Notification.MessagingStyle(new Person.Builder().setName("Yo").build())
                .addMessage("hola desde probe", System.currentTimeMillis(), new Person.Builder().setName("Ana").build());
        for (String tag : new String[]{"tagA", "tagB"}) {
            Notification n = new Notification.Builder(c, CH)
                    .setSmallIcon(R.drawable.ic_probe)
                    .setContentTitle("Probe " + tag).setContentText("mensaje " + tag)
                    .setStyle(st).setContentIntent(pOpen).addAction(aBr).addAction(aSv)
                    .setAutoCancel(true).build();
            nm.notify(tag, 7, n);
            Main.log("posted tag=" + tag + " id=7");
        }
    }
}
