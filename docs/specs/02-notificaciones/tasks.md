# Spec 02: notificaciones de las apps invitadas. Tareas

Estado: Borrador

Cada tarea: ≤ 1 día, un commit pequeño, CI en verde. Rutas relativas a `engine/`
(`B` = `Bcore/src/main/java/top/niunaijun/blackbox/`). Las pruebas en dispositivo las
ejecuta el propietario y devuelve la carpeta de `scripts/fase0/diag.sh`.

## Pruebas primero

**T02.1** Ampliar NotifProbe (`tools/notifprobe/src/dev/probe/notif/Main.java`): botones o
extras de intent para publicar `tag/cancel`, `cancelAll`, grupo con resumen, `largeIcon`
propio, `setShortcutId`, `Parcelable` propio en extras de la notificación y en el intent de
`Resp(BR)`, y mostrar `checkSelfPermission(POST_NOTIFICATIONS)`/`areNotificationsEnabled`.
Añadir al `README` de NotifProbe la tabla de casos y la salida esperada.
- Cubre: R02.5, R02.7, R02.8, R02.13, R02.14, R02.19, R02.22.
- Verificación: compila con `tools/notifprobe/build.sh`; en el sistema real cada caso publica lo esperado.
- Estimación: 1 día.

**T02.2** Spike: punto de entrega al invitado (opción A de §6.2 del diseño). Localizar cómo
`BActivityManager.scheduleBroadcastReceiver` y `startService` llegan al proceso invitado
(`B core/system/am/BroadcastManager.java`, `ActiveServices.java`, `B app/BActivityThread`)
y decidir A o B. Criterio de salida: *continuar con A* si se puede entregar una acción
especial sin AIDL; *pivotar a B* si no; documentar en el diseño.
- Cubre: R02.15, R02.16, R02.19.
- Verificación: nota en `design.md` §6.2 y prototipo que entregue un `byte[]` al invitado.
- Estimación: 1 día.

## Presentación

**T02.3** `NotificationPresenter` (nuevo) con conversión de iconos a `Bitmap` y escalado
(smallIcon, largeIcon, iconos de acciones, `Person`), y fallback R02.4. Test unitario del conversor.
- Cubre: R02.3, R02.4.
- Verificación: NotifProbe; `dumpsys notification` sin `Icon(typ=RESOURCE pkg=<host>`. Paso e.
- Estimación: 1 día.

**T02.4** `subText` `<App> · <Perfil>`, `largeIcon` por defecto y etiquetas privadas hacia
`:black` (`bb.app_label`, `bb.profile_label`); limpieza de extras no framework (R02.5).
- Cubre: R02.1, R02.2, R02.5.
- Verificación: NotifProbe en perfiles 0 y 1; caso con `Parcelable` propio. Paso e.
- Estimación: 1 día.

**T02.5** Ajuste "identificar en el título" (host `engine/app`, `SettingFragment.kt`) y su
lectura desde el proceso invitado (decidir mecanismo, sin verificar).
- Cubre: R02.6, R02.23.
- Verificación: ajuste activo y NotifProbe: título con prefijo.
- Estimación: 1 día.

## Tag, cancelación y canales

**T02.6** `BNotificationManagerService.enqueueNotificationWithTag` con tag real
`bb:<userId>:<paquete>:<tag>` e id sin hash; `cancelNotificationWithTag`; eliminar `mIds`;
`deletePackageNotification` por prefijo con `getActiveNotifications`.
- Cubre: R02.7, R02.8, R02.9.
- Verificación: NotifProbe `tagA`/`tagB` = 2 entradas; cancelar una deja la otra; desinstalar limpia.
- Estimación: 1 día.

**T02.7** Hooks `cancelAllNotifications`, `areNotificationsEnabled`, `getActiveNotifications`
en `INotificationManagerProxy`, confirmando nombres y firmas AIDL en Android 16 y 17.
- Cubre: R02.8, R02.22.
- Verificación: NotifProbe `cancelAll` con la notificación del motor (`blackbox_daemon_channel`) intacta.
- Estimación: 1 día.

**T02.8** Canales y grupos: ids `bb:<userId>:<paquete>:<canal>`, nombres legibles, grupo por
app y perfil, `getNotificationChannel(s)` leyendo del `NotificationManager` real,
separación de `groupKey` y grupo de canales, borrado de canales antiguos `@black-`.
- Cubre: R02.10, R02.11, R02.12, R02.13.
- Verificación: dos apps con canal `default`; matar `:black` y releer canales; Ajustes legibles.
- Estimación: 1 día.

## PendingIntent y respuesta

**T02.9** `ProxyPendingRecord` v2 con `byte[]`, `mUserId`, `mPackage`, `mKind` y
conversión del caso de actividad (`ProxyPendingActivity`) a la nueva forma.
- Cubre: R02.19.
- Verificación: `CONTENT_INTENT_OK` sigue publicándose; test unitario de ida y vuelta con `Parcelable` propio.
- Estimación: 1 día.

**T02.10** `GetIntentSender`: reescritura de `BROADCAST`, `SERVICE` y `FOREGROUND_SERVICE`
hacia `ProxyPendingReceiver` (nuevo, `exported=false`, declarado en el manifiesto y en
`ProxyManifest`), URI único `bbpi://...` y conservación de flags.
- Cubre: R02.15, R02.17, R02.18, R02.21.
- Verificación: test instrumentado de colisión y `NO_CREATE`; `dumpsys package` muestra `exported=false`.
- Estimación: 1 día.

**T02.11** Despacho en el box: `ProxyPendingReceiver` valida paquete y usuario, envía el
contenedor al invitado y, en el invitado, fusiona el relleno y entrega a
`scheduleBroadcastReceiver`/`startService`.
- Cubre: R02.15, R02.16, R02.20.
- Verificación: `Resp(BR)` → `REPLY_BR_OK text=...`; `Resp(SVC)` → `REPLY_SVC_OK text=...`. Paso f.
- Estimación: 1 día.

**T02.12** Prueba de seguridad: instalar NotifProbe real fuera del box, responder desde el
box, y repetir con el perfil sin la app instalada. Documentar el resultado en la spec.
- Cubre: R02.20.
- Verificación: la app real no recibe nada; el log del motor registra "descartado".
- Estimación: 0,5 día.

**T02.13** Mismo camino para `AlarmManager` del invitado con PI de broadcast: comprobar
que `IAlarmManagerProxy` (hoy `set` devuelve 0 sin hacer nada) no rompe los PI reescritos. Solo
verificación y nota hacia la spec 04.
- Cubre: R02.15, R02.18.
- Verificación: caso nuevo de NotifProbe: alarma exacta a 10 s que publica una notificación.
- Estimación: 0,5 día.

## Permisos y reloj

**T02.14** `WelcomeActivity`: comprobación de `POST_NOTIFICATIONS` y pantalla de ajustes;
decidir el comportamiento de `requestPermissions` del invitado (hook en `IPermissionManagerProxy`).
- Cubre: R02.22.
- Verificación: Android 16 con el permiso revocado; NotifProbe informa el estado correcto.
- Estimación: 1 día.

**T02.15** Aceptación con Huawei Watch GT Runner 2: protocolo, evidencias y resultado en
`docs/specs/02-notificaciones/resultado-reloj.md` (sin datos personales).
- Cubre: R02.23, R02.24.
- Verificación: paso g del plan de pruebas; "no aplica" si el reloj no ofrece respuesta rápida.
- Estimación: 0,5 día (la ejecuta el propietario).

**T02.16** Regresión final en Android 16 y 17: pasos e, f, g completos con NotifProbe y
WhatsApp del box; cierre del hito M2.
- Cubre: todos.
- Verificación: informe con `scripts/fase0/diag.sh`; CI verde.
- Estimación: 1 día.

## Resumen

| Bloque | Días |
|---|---|
| Pruebas primero (T02.1, T02.2) | 2 |
| Presentación (T02.3 a T02.5) | 3 |
| Tag, cancelación y canales (T02.6 a T02.8) | 3 |
| PendingIntent (T02.9 a T02.13) | 4 |
| Permisos, reloj y regresión (T02.14 a T02.16) | 2,5 |
| **Total** | **14,5 días** |

El veredicto estimaba 7 a 9 días de desarrollo para R3 y R4; la diferencia son las pruebas
nuevas, el spike y la verificación en dos versiones.
