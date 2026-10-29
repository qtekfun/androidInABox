# Spec 02: resultados en dispositivo

## OPPO CPH2841, Android 16, ColorOS (ROM EEA), 2026-10-29

| Prueba | Resultado |
|---|---|
| Dos `notify(tag, 7)` con tags distintos | OK: dos notificaciones con `tag=bb:0:dev.probe.notif:tagA` y `...:tagB` |
| Canal por app y perfil | OK: `channel=bb:0:dev.probe.notif:probe_msgs` |
| Identificación | OK: `android.subText = "NotifProbe · Perfil 1"` |
| Respuesta por broadcast (`Resp(BR)`) | OK: `REPLY_BR_OK text=pruebaBR` |
| Respuesta por servicio (`Resp(SVC)`) | OK, tras convertir los PI de servicio a broadcast hacia el receiver del box: `REPLY_SVC_OK text=pruebaSVC` |
| Icono pequeño | **Lo pisa la ROM.** El motor publica `smallIcon=Icon(typ=BITMAP size=84x84)` (log `BLACKNOTIF`), pero ColorOS lo sustituye por el icono de la app emisora, es decir el del host (`OplusNotificationFixHelper: fixAppIcon`). No se puede corregir desde el motor; verificar en las ROM chinas |
| Icono grande por defecto (icono de la app invitada) | **No aplicado:** `android.largeIcon=null`. Pendiente en T02.4 |

Bug encontrado y corregido: recursión infinita en `areNotificationsEnabled`
(`StackOverflowError` en el invitado).
