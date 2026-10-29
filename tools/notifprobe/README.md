# NotifProbe

App de prueba para medir cómo publica el motor las notificaciones de una app invitada.
Paquete `dev.probe.notif`, targetSdk 34.

Al abrirla:
1. Crea el canal `probe_msgs`.
2. Publica dos notificaciones con el mismo id (7) y tags distintos (`tagA`, `tagB`).
   Cada una lleva un icono de recurso propio, estilo MessagingStyle, una acción de
   abrir (PendingIntent de actividad) y dos acciones de respuesta con RemoteInput:
   `Resp(BR)` (broadcast) y `Resp(SVC)` (servicio).

Cuando algo funciona, publica una notificación de confirmación con el título
`CONTENT_INTENT_OK`, `REPLY_BR_OK text=...` o `REPLY_SVC_OK text=...`. Hace falta
porque dentro del motor los `Log.i` del invitado no llegan a logcat.

Compilar (sin Gradle):

```bash
ANDROID_HOME=~/Android/Sdk JAVA_HOME=/ruta/jdk-21 tools/notifprobe/build.sh
```

Comprobar el resultado:

```bash
adb shell dumpsys notification --noredact | grep -E "android.title=|NotificationRecord.*blackbox"
```
