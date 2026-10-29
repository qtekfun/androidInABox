package dev.probe.notif;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Bundle;

/**
 * Casos por extra de intent: {@code am start -n dev.probe.notif/.Main --es case <caso>[,<caso>...]}.
 * Sin extra se ejecuta "base". Ver README.
 */
public class Main extends Activity {
    static final String TAG = "PROBE";
    static final String CH = "probe_msgs";
    static final String CH2 = "default";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Main.log("Main.onCreate pkg=" + getPackageName() + " clicked=" + getIntent().getBooleanExtra("clicked", false));
        if (getIntent().getBooleanExtra("clicked", false)) { ack(this, "CONTENT_INTENT_OK"); finish(); return; }
        String cases = getIntent().getStringExtra("case");
        if (cases == null) cases = "base";
        for (String k : cases.split(",")) {
            try {
                run(this, k.trim());
            } catch (Throwable t) {
                log("case " + k + " FAIL " + t);
                ack(this, "CASE_FAIL " + k + " " + t.getClass().getSimpleName());
            }
        }
        finish();
    }

    static void log(String m) { System.err.println("PROBE " + m); }

    static String customOf(Intent i) {
        try {
            Custom c = i.getParcelableExtra("custom");
            return c == null ? "" : " custom=" + c.v;
        } catch (Throwable t) {
            return " custom=ERR " + t.getClass().getSimpleName();
        }
    }

    static void ack(Context c, String m) {
        log(m);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.notify("ack", 99, new Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_probe)
                .setContentTitle(m).build());
    }

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "Mensajes probe", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH2, "Default probe", NotificationManager.IMPORTANCE_DEFAULT));
    }

    static void run(Context c, String k) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        channels(c);
        switch (k) {
            case "base": post(c, false); break;
            case "perm":
                // Estado de POST_NOTIFICATIONS y areNotificationsEnabled, visible en una notificacion
                ack(c, "PERM post=" + (c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0)
                        + " enabled=" + nm.areNotificationsEnabled());
                break;
            case "tag": // tagA/tagB con el mismo id: deben ser 2 entradas
                simple(c, nm, "tagA", 7, "tagA");
                simple(c, nm, "tagB", 7, "tagB");
                break;
            case "cancel": // cancela solo tagA; debe quedar tagB
                nm.cancel("tagA", 7);
                log("cancel tagA id=7");
                break;
            case "cancelall": // cancela todo lo del paquete; la notificacion del motor debe quedar
                nm.cancelAll();
                log("cancelAll");
                break;
            case "group": group(c, nm); break;
            case "largeicon":
                nm.notify("large", 20, new Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_probe)
                        .setContentTitle("Probe largeIcon").setContentText("con icono grande propio")
                        .setLargeIcon(Icon.createWithBitmap(bitmap(Color.MAGENTA))).build());
                break;
            case "shortcut": // shortcutId que no existe en el host
                nm.notify("shortcut", 21, new Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_probe)
                        .setContentTitle("Probe shortcut").setContentText("shortcutId inexistente")
                        .setShortcutId("probe_missing_shortcut")
                        .setStyle(new Notification.MessagingStyle(new android.app.Person.Builder().setName("Yo").build())
                                .addMessage("hola", System.currentTimeMillis(), new android.app.Person.Builder().setName("Ana").build()))
                        .build());
                break;
            case "parcel": post(c, true); break; // Parcelable propio en extras y en intents
            case "samechannel": // dos apps con el canal "default": publicar por el canal CH2
                nm.notify("def", 22, new Notification.Builder(c, CH2).setSmallIcon(R.drawable.ic_probe)
                        .setContentTitle("Probe canal default").build());
                break;
            case "alarm": alarm(c); break;
            default: ack(c, "CASE_UNKNOWN " + k);
        }
    }

    static Notification.Builder base(Context c, String title) {
        return new Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_probe).setContentTitle(title);
    }

    static void simple(Context c, NotificationManager nm, String tag, int id, String text) {
        nm.notify(tag, id, base(c, "Probe " + tag).setContentText("mensaje " + text).build());
        log("posted tag=" + tag + " id=" + id);
    }

    static Bitmap bitmap(int color) {
        Bitmap bm = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        new Canvas(bm).drawColor(color);
        return bm;
    }

    static void group(Context c, NotificationManager nm) {
        for (int i = 1; i <= 3; i++) {
            nm.notify("g" + i, 30 + i, base(c, "Probe grupo " + i).setContentText("item " + i)
                    .setGroup("probe_group").build());
        }
        nm.notify("gsum", 30, base(c, "Probe resumen").setContentText("3 items")
                .setGroup("probe_group").setGroupSummary(true).build());
        log("posted group probe_group x3 + summary");
    }

    static void alarm(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 3, new Intent(c, AlarmReceiver.class), PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + 10000;
        try {
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            else am.set(AlarmManager.RTC_WAKEUP, at, pi);
            log("alarm scheduled in 10s");
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
            log("alarm inexact (no exact permission)");
        }
    }

    static void post(Context c, boolean parcel) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        Intent open = new Intent(c, Main.class).putExtra("clicked", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent br = new Intent(c, ReplyReceiver.class);
        Intent sv = new Intent(c, ReplyService.class);
        if (parcel) { br.putExtra("custom", new Custom("br")); sv.putExtra("custom", new Custom("svc")); }
        PendingIntent pOpen = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pBr = PendingIntent.getBroadcast(c, 1, br, PendingIntent.FLAG_MUTABLE);
        PendingIntent pSv = PendingIntent.getService(c, 2, sv, PendingIntent.FLAG_MUTABLE);
        RemoteInput ri = new RemoteInput.Builder("reply").setLabel("Responder").build();
        Notification.Action aBr = new Notification.Action.Builder(null, "Resp(BR)", pBr).addRemoteInput(ri).build();
        Notification.Action aSv = new Notification.Action.Builder(null, "Resp(SVC)", pSv).addRemoteInput(ri).build();
        Notification.MessagingStyle st = new Notification.MessagingStyle(new Person.Builder().setName("Yo").build())
                .addMessage("hola desde probe", System.currentTimeMillis(), new Person.Builder().setName("Ana").build());
        for (String tag : new String[]{"tagA", "tagB"}) {
            Notification.Builder nb = new Notification.Builder(c, CH)
                    .setSmallIcon(R.drawable.ic_probe)
                    .setContentTitle("Probe " + tag).setContentText("mensaje " + tag)
                    .setStyle(st).setContentIntent(pOpen).addAction(aBr).addAction(aSv)
                    .setAutoCancel(true);
            if (parcel) { Bundle e = new Bundle(); e.putParcelable("custom", new Custom("extra")); nb.addExtras(e); }
            nm.notify(tag, 7, nb.build());
            Main.log("posted tag=" + tag + " id=7 parcel=" + parcel);
        }
    }
}
