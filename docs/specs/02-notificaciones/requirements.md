# Spec 02: notificaciones de las apps invitadas. Requisitos

Estado: Borrador

Base: [veredicto](../../fase0/veredicto.md) (R3 y R4 de [riesgos-android](../../fase0/riesgos-android.md)),
[arquitectura-motor §6](../../fase0/arquitectura-motor.md) y
[resultados-dispositivo](../../fase0/resultados-dispositivo.md). Pasos e, f y g del
[plan de pruebas](../../fase0/plan-pruebas.md). Hito M2 del [ROADMAP](../ROADMAP.md).

## Historias de usuario

- Como usuario, veo en la bandeja del sistema real de qué app y de qué perfil es cada
  notificación, con su icono, aunque la cabecera diga el nombre del host.
- Como usuario, puedo responder a un mensaje desde la notificación (y desde el reloj)
  y la respuesta sale de la cuenta del box.
- Como usuario, nunca se activa una app real instalada fuera del box con el mismo
  nombre de paquete.

## Fuera de alcance

- `NotificationListenerService` dentro del box, `getActiveNotifications` para el invitado,
  badges de icono, burbujas y `fullScreenIntent`.
- Cambiar el nombre de la cabecera: exige `SUBSTITUTE_NOTIFICATION_APP_NAME`, permiso de
  sistema que el host no puede obtener.

## A. Presentación

**R02.1** WHEN la app invitada publica una notificación THE motor SHALL poner el
`subText` como `<App> · <Perfil>`, anteponiéndolo al `subText` propio de la app si lo
tiene.
- Verificación: NotifProbe en el perfil 0 y en un segundo perfil;
  `dumpsys notification --noredact | grep android.subText=`. Plan de pruebas, paso e.

**R02.2** WHEN la notificación no trae `largeIcon` THE motor SHALL usar el icono de la
app invitada como `largeIcon`. IF trae uno propio (foto de contacto) THEN THE motor SHALL
conservarlo y dejar la identificación en el `subText` y el icono pequeño.
- Verificación: NotifProbe sin y con `largeIcon`; `dumpsys notification` (`android.largeIcon`).

**R02.3** WHEN la notificación referencia iconos de recurso del invitado (`smallIcon`,
`largeIcon`, iconos de acciones, `Person` de `MessagingStyle`, `EXTRA_PICTURE_ICON`) THE
motor SHALL convertirlos a `Bitmap` en el proceso invitado, escalados a un máximo
razonable (pequeño 96 px, grande 256 px), antes de enviarlos al host.
- Verificación: NotifProbe; `dumpsys notification` ya no muestra `Icon(typ=RESOURCE pkg=<host>`
  para esos campos.

**R02.4** IF la conversión de un icono falla THEN THE motor SHALL publicar la notificación
con el icono del host y registrar el fallo, sin lanzar excepción en la app invitada.
- Verificación: test unitario del conversor con un recurso inexistente.

**R02.5** THE motor SHALL eliminar de `Notification.extras` los valores `Parcelable` cuya
clase no pertenezca al framework antes de salir del proceso invitado.
- Verificación: caso nuevo de NotifProbe con un `Parcelable` propio en los extras; la
  notificación se publica y no hay `BadParcelableException` en logcat.

**R02.6** WHERE el ajuste "identificar en el título" esté activo THE motor SHALL anteponer
`[<App> · <Perfil>]` al título.
- Verificación: ajuste activado y paso g (el reloj no suele mostrar `subText`).

## B. Identidad, tag, cancelación y canales

**R02.7** THE motor SHALL publicar con el `tag` y el `id` del invitado dentro de un espacio
propio (`tag` real = `bb:<userId>:<paquete>:<tag>`), de modo que `notify("tagA",7)` y
`notify("tagB",7)` sean dos notificaciones y dos apps con el mismo `id` no colisionen.
- Verificación: NotifProbe: dos entradas en `dumpsys notification`. Paso e.

**R02.8** WHEN el invitado llama a `cancel(tag,id)` THE motor SHALL cancelar solo esa
notificación; WHEN llama a `cancelAll` THE motor SHALL cancelar solo las de ese paquete y
perfil, nunca las del host ni las de otras apps.
- Verificación: caso nuevo de NotifProbe (tag/cancel); `dumpsys notification` antes y después,
  con la notificación de servicio del motor intacta.

**R02.9** WHEN se desinstala una app del box o se borran sus datos THE motor SHALL cancelar
sus notificaciones y borrar sus canales y grupos reales.
- Verificación: desinstalar NotifProbe; `cmd notification list` sin entradas `bb:`.

**R02.10** THE motor SHALL crear un canal real por (paquete, perfil, canal) con id
`bb:<userId>:<paquete>:<canal>`, de forma que dos apps con el canal `default` no lo compartan.
- Verificación: dos apps de prueba con el mismo id de canal; `dumpsys notification | grep "bb:"`.

**R02.11** THE motor SHALL dar a cada canal real el nombre `<App> (<Perfil>) · <nombre del
canal>` y agrupar los canales sin grupo propio en un grupo `<App> (<Perfil>)`.
- Verificación: Ajustes del sistema > Notificaciones > host: los canales se distinguen por app y perfil.

**R02.12** WHEN el proceso `:black` se reinicia THE motor SHALL seguir resolviendo los
canales ya creados (el invitado ve los mismos `getNotificationChannel(s)` con su importancia
actual, incluidos los cambios del usuario en Ajustes) sin recrearlos.
- Verificación: cambiar la importancia de un canal en Ajustes, matar `:black`
  (`adb shell am kill`), llamar a `getNotificationChannels` desde NotifProbe.

**R02.13** WHEN el invitado agrupa notificaciones (`setGroup`, resumen de grupo) THE motor
SHALL conservar el agrupado dentro de (paquete, perfil) sin mezclarlo con otras apps.
- Verificación: caso nuevo de NotifProbe (3 notificaciones + resumen); `dumpsys notification` (`group=`).

**R02.14** WHERE la app usa `MessagingStyle` con `Person` y `shortcutId` THE motor SHALL
publicar la notificación sin error; IF el atajo no existe en el host THEN THE motor SHALL
quitar el `shortcutId` y mostrarla como notificación de mensajería normal.
- Verificación: NotifProbe (ya usa `MessagingStyle`) y un caso con `setShortcutId`.

## C. PendingIntent y respuesta

**R02.15** WHEN el invitado crea un `PendingIntent` de broadcast, servicio o servicio en
primer plano THE motor SHALL sustituirlo por uno del host hacia un receptor stub, guardando
el intent original, y WHEN se dispara THE motor SHALL entregarlo dentro del box, en el perfil
correcto.
- Verificación: NotifProbe `Resp(BR)` y `Resp(SVC)` publican `REPLY_BR_OK` y `REPLY_SVC_OK`. Paso f.

**R02.16** WHEN el sistema entrega el `PendingIntent` con datos de relleno (extras,
`ClipData`, resultados de `RemoteInput`) THE motor SHALL fusionarlos en el intent original
de modo que `RemoteInput.getResultsFromIntent` devuelva el texto escrito.
- Verificación: `REPLY_BR_OK text=<lo escrito>`.

**R02.17** THE motor SHALL crear el `PendingIntent` del host `FLAG_MUTABLE` cuando el del
invitado lo sea o lleve `RemoteInput`, e inmutable en otro caso.
- Verificación: test instrumentado con flags de entrada y `dumpsys activity intents`.

**R02.18** THE motor SHALL garantizar que dos `PendingIntent` del invitado distintos por
(perfil, paquete, tipo, `requestCode`, `filterEquals`) nunca se fusionen en el sistema, y que
`FLAG_NO_CREATE`, `FLAG_CANCEL_CURRENT`, `FLAG_UPDATE_CURRENT` y `cancel()` sigan funcionando.
- Verificación: test instrumentado: dos PI con igual `requestCode` y distinto paquete; `getBroadcast(..., NO_CREATE)`.

**R02.19** THE motor SHALL transportar el intent original sin desmarshalarlo en procesos
del host que no tengan las clases del invitado.
- Verificación: caso nuevo de NotifProbe con un `Parcelable` propio en el intent de la acción; la entrega llega.

**R02.20** IF el destino de un `PendingIntent` reescrito no se puede resolver dentro del box
(paquete no instalado en ese perfil, registro ilegible) THEN THE motor SHALL descartar la
entrega, registrar el motivo y no enviar el intent original al sistema real.
- Verificación: instalar la app real `dev.probe.notif` fuera del box, responder desde la
  notificación del box y comprobar que la real no recibe nada (`dumpsys activity broadcasts`,
  sin `REPLY_*` en la real). Paso f.

**R02.21** THE host SHALL declarar los receptores y servicios stub de `PendingIntent` con
`exported=false`.
- Verificación: `dumpsys package` del host; un `am broadcast` externo al stub falla.

## D. Permiso y reloj

**R02.22** WHILE el sistema sea Android 13 o superior THE host SHALL comprobar
`POST_NOTIFICATIONS` y, IF está denegado THEN THE host SHALL mostrar una pantalla que
explique el motivo y abra los ajustes de notificaciones del host; el estado visible para el
invitado SHALL ser el del host.
- Verificación: Android 16 con el permiso revocado (`pm revoke`); NotifProbe consulta
  `checkSelfPermission` y `areNotificationsEnabled`. El diálogo automático con targetSdk 28 es (sin verificar).

**R02.23** WHERE Huawei Health tenga activo el host THE sistema SHALL mostrar en el Huawei
Watch GT Runner 2 cada notificación del box con texto que identifique app y perfil (R02.6 si
el `subText` no se muestra).
- Verificación: plan de pruebas, paso g.

**R02.24** WHERE el reloj Huawei ofrezca respuesta rápida THE motor SHALL entregar esa
respuesta por el mismo camino que R02.15 y R02.16. Si el reloj la ofrece es (sin verificar).
- Verificación: paso g con `Resp(BR)`; si no hay respuesta en el reloj, el criterio se marca "no aplica".
