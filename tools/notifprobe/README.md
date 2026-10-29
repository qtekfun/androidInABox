# NotifProbe

App de prueba para medir cómo publica el motor las notificaciones de una app invitada.
Paquete `dev.probe.notif`, targetSdk 34.

Al abrirla:
1. Crea el canal `probe_msgs`.
2. Publica dos notificaciones con el mismo id (7) y tags distintos (`tagA`, `tagB`).
   Cada una lleva un icono de recurso propio, estilo MessagingStyle, una acción de
   abrir (PendingIntent de actividad) y dos acciones de respuesta con RemoteInput:
   `Resp(BR)` (broadcast) y `Resp(SVC)` (servicio).

Sin extras se ejecuta el caso `base`. Para otros casos (uno o varios separados por coma):

```bash
adb shell am start -n dev.probe.notif/.Main --es case tag,group
```

| Caso | Qué hace | Salida esperada (sistema real) |
|---|---|---|
| `base` | Lo descrito arriba | 2 entradas `tagA`/`tagB`, id 7 |
| `tag` | `notify(tagA,7)` y `notify(tagB,7)` | 2 entradas |
| `cancel` | `cancel(tagA,7)` | queda solo `tagB` |
| `cancelall` | `cancelAll()` | desaparecen todas las del paquete; no las de otras apps ni la del motor |
| `group` | 3 notificaciones `setGroup` + resumen | `group=probe_group` en las 4 |
| `largeicon` | `largeIcon` propio (bitmap magenta) | `android.largeIcon` conservado |
| `shortcut` | `setShortcutId` inexistente + MessagingStyle | se publica sin error |
| `parcel` | `Parcelable` propio en extras de la notificación y en los intents de `Resp(BR)`/`Resp(SVC)` | sin `BadParcelableException`; al responder, `REPLY_BR_OK text=... custom=br` y `REPLY_SVC_OK text=... custom=svc` |
| `perm` | Publica `PERM post=<checkSelfPermission> enabled=<areNotificationsEnabled>` | `post=true enabled=true` con el permiso concedido |
| `samechannel` | Publica por el canal `default` | un canal `default` por app y perfil |
| `alarm` | Alarma a 10 s (PendingIntent de broadcast) | `ALARM_OK` a los ~10 s |

Si un caso lanza una excepción publica `CASE_FAIL <caso> <excepción>`.

Cuando algo funciona, publica una notificación de confirmación con el título
`CONTENT_INTENT_OK`, `REPLY_BR_OK text=...`, `ALARM_OK` o `REPLY_SVC_OK text=...`. Hace falta
porque dentro del motor los `Log.i` del invitado no llegan a logcat.

Compilar (sin Gradle):

```bash
ANDROID_HOME=~/Android/Sdk JAVA_HOME=/ruta/jdk-21 tools/notifprobe/build.sh
```

Comprobar el resultado:

```bash
adb shell dumpsys notification --noredact | grep -E "android.title=|NotificationRecord.*blackbox"
```
