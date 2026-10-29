# Spec 02: notificaciones de las apps invitadas. Diseño

Estado: Borrador

Rutas relativas a `engine/`. `B` = `Bcore/src/main/java/top/niunaijun/blackbox/`.
Lo no comprobado en código o en dispositivo lleva "(sin verificar)".

## 1. Situación actual (código)

| Pieza | Fichero | Comportamiento |
|---|---|---|
| Hook en el invitado | `B fake/service/INotificationManagerProxy.java` | `EnqueueNotificationWithTag` lee `tag` (índice 2) e `id` (3) y llama a `BNotificationManager.enqueueNotificationWithTag(id, tag, n)`. Solo hay hooks de canales, grupos, `cancelNotificationWithTag` y `enqueue`. No hay hook de `cancelAllNotifications`, `getActiveNotifications` ni `areNotificationsEnabled` |
| Cliente binder | `B fake/frameworks/BNotificationManager.java` | Reenvía a `:black` añadiendo `BActivityThread.getUserId()` |
| Servicio | `B core/system/notification/BNotificationManagerService.java` | Id real = `hash(paquete+userId+id)`; **ignora `tag`**; canal = `<canal>@black-<userId>` sin paquete; `getRealChannelId` rompe con `split` si el canal contiene el separador; ids vivos en `NotificationRecord.mIds` (memoria) |
| Registro | `B core/system/notification/NotificationRecord.java` | Canales y grupos en memoria: se pierden al reiniciar `:black` |
| PendingIntent | `B fake/service/IActivityManagerProxy.java` (`GetIntentSender`) | Solo `INTENT_SENDER_ACTIVITY` se reescribe hacia `ProxyPendingActivity$P<pid>` |
| Registro de PI | `B proxy/record/ProxyPendingRecord.java` | Guarda `Intent` objetivo como `Parcelable` extra |
| Receptor | `B proxy/ProxyBroadcastReceiver.java` | En `:black`; llama `scheduleBroadcastReceiver(record.mIntent, ...)`. Es para `sendBroadcast` del invitado, no para PI |

Medido en el OPPO CPH2841 (Android 16): cabecera "BlackBox", tags pisados, icono de
recurso equivocado, `Resp(BR)` sin traza, `Resp(SVC)` con `Unable to start service`.

Dos hallazgos del código que la spec corrige:
- `replaceAllAppPkg` sustituye el paquete invitado por el del host en los métodos sin
  hook. Un `cancelAllNotifications` del invitado llegaría al sistema con el paquete del
  host y cancelaría **todas** las notificaciones del host, incluida la del servicio del
  motor (sin verificar en dispositivo).
- `getBlackGroupId` se usa a la vez para el grupo de canales y para el `groupKey` de la
  notificación. Son conceptos distintos; se separan.

## 2. Reparto de trabajo entre procesos

```
 proceso invitado :pN                     :black (BNotificationManagerService)       sistema real
 ─────────────────────                    ─────────────────────────────────          ────────────
 notify(tag,id,n)
  └ NotificationPresenter (nuevo)         mapea canal/grupo/tag a ids reales
     · iconos → Bitmap (hay Resources)    publica con NotificationManager del host ─► bandeja
     · subText, título opcional
     · limpia extras no framework
     · escribe etiqueta de app en
       extras privados
  └ binder ─────────────────────────────► (solo toca canal, grupo, tag, id)
```

Decisión D1: **todo lo que necesita `Resources` o clases del invitado se hace en el
proceso invitado**; `:black` no abre `extras` (un `Bundle` con clases del invitado no se
desmarshala en el host). `:black` solo cambia `mChannelId`, `mGroupKey`, tag e id, que son
campos directos de `Notification`.

## 3. Presentación (R02.1 a R02.6)

Clase nueva `B fake/frameworks/notification/NotificationPresenter.java` (nombre propuesto),
invocada desde `EnqueueNotificationWithTag.hook` antes de `BNotificationManager`:

1. Etiqueta de app: `Context.getPackageManager().getApplicationLabel(...)` en el proceso
   invitado (usa el APK virtual). Perfil: `Perfil <userId+1>` salvo que
   `BUserManagerService` exponga un nombre editable (sin verificar). Se pasa a `:black`
   en extras privados `bb.app_label` y `bb.profile_label`, que `:black` lee y elimina.
2. `subText`: `Notification.EXTRA_SUB_TEXT` = `"<App> · <Perfil>"` (+ `" · " + subText propio`).
3. Iconos: `Icon.loadDrawable(guestContext)` → `Bitmap` (alfa) → `Icon.createWithBitmap`.
   - Pequeño: hidden `Notification.setSmallIcon(Icon)` / campo `mSmallIcon` (sin verificar
     en Android 16). El campo `icon` (int) se deja en un recurso del host no nulo, porque
     el sistema puede validar `icon != 0` (sin verificar).
   - Grande: si falta, icono de la app (`ApplicationInfo.loadIcon` → bitmap 256 px).
   - Acciones: `Notification.Action.getIcon()`; las acciones se reconstruyen con
     `Notification.Action.Builder(icon, title, pi)` conservando `RemoteInput`s y extras.
   - `Person` de `MessagingStyle`: `extras["android.messages"]`, `android.messages.historic`,
     `android.messagingUser`, `android.people.list`: se reconstruye cada `Person` con
     `toBuilder().setIcon(bitmap)`.
4. Bitmaps acotados para no pasar del límite de la transacción binder (1 MB): 96 px
   pequeño, 256 px grande. Si el total estimado supera ~700 KB se omite el `largeIcon`.
5. Extras: se recorren recursivamente `Bundle`, `Bundle[]` y listas; se borran los valores
   cuya clase la cargue un `ClassLoader` distinto del de arranque (R02.5).
6. Título opcional (R02.6): ajuste del host leído desde el proceso invitado
   (`SharedPreferences` del host no son accesibles; se usa `BlackBoxCore` + un `ContentProvider`
   del host o un valor en `AppConfig`; sin verificar cuál es más barato, ver T02.5).

Alternativa descartada: resolver iconos en `:black` con `BPackageManager.getResourcesForApplication`.
Obliga a abrir recursos de cualquier invitado en un proceso compartido y no resuelve los
`Person` en `extras` (que `:black` no debe desmarshalar).

Limitación asumida: la cabecera de la notificación seguirá diciendo el nombre del host. El
parche de `EXTRA_SUBSTITUTE_APP_NAME` queda descartado por permiso.

## 4. Identidad, tag, cancelación (R02.7 a R02.9)

En `BNotificationManagerService.enqueueNotificationWithTag`:
- `tag real = "bb:" + userId + ":" + paquete + ":" + (tag == null ? "" : tag)` y
  `id real = id` del invitado. Se abandona el hash (que además colisiona). Se publica
  con `mRealNotificationManager.notify(tagReal, id, n)`.
- Se elimina `NotificationRecord.mIds`. Cancelar sin estado propio:
  - `cancelNotificationWithTag(id, tag)` → `cancel(tagReal, id)`.
  - `cancelAll(paquete, userId)` → `getActiveNotifications()` (API 23) filtrado por prefijo
    `bb:<userId>:<paquete>:` y `cancel(tag, id)` de cada una.
  - `deletePackageNotification` usa el mismo filtro y borra canales por prefijo (§5).
- Hooks nuevos en `INotificationManagerProxy`: `cancelAllNotifications`,
  `areNotificationsEnabled` (devuelve el estado real del host), `getActiveNotifications`
  (devuelve vacío por ahora, fuera de alcance), `setNotificationsEnabledForPackage`
  (ignorar). Los nombres exactos de los métodos AIDL en Android 16 están (sin verificar).
- `getActiveNotifications` desde `:black` requiere que el proceso sea el del host: lo es.

## 5. Canales y grupos (R02.10 a R02.13)

- Id real de canal: `bb:<userId>:<paquete>:<canal>`. Es inequívoco: el paquete no contiene
  `:`, el `userId` es un entero y el resto es el id del invitado. `getRealChannelId` parte
  con `split(":", 4)`. Se eliminan `CHANNEL_BLACK` y `GROUP_BLACK`.
- Nombre: `<App> (<Perfil>) · <nombre>` (la etiqueta llega en el `NotificationChannel`
  desde el invitado vía un campo auxiliar, o `:black` la obtiene con `BPackageManagerService`
  porque son datos del APK instalado y no extras de la notificación).
- Grupo de canales: id `bb:<userId>:<paquete>` y nombre `<App> (<Perfil>)`; los canales
  con grupo propio usan `bb:<userId>:<paquete>:<grupo>` y nombre `<App> (<Perfil>) · <grupo>`.
- Fuente de la verdad tras reinicio: el propio `NotificationManager` real. `getNotificationChannels`
  y `getNotificationChannel` leen `mRealNotificationManager.getNotificationChannels()`, filtran por
  prefijo, y devuelven copias con el id del invitado. Así se conservan los ajustes del
  usuario y no hace falta persistir objetos `NotificationChannel`. La "tabla persistida" se
  reduce a la convención del id (R02.12). Si T02.8 descubre que falta algún dato, se añade
  `notification_labels.json` en `BEnvironment.getSystemDir()`.
- Canales antiguos (`@black-`): se borran en el primer arranque tras actualizar. No hay
  versión publicada, así que no se migran ajustes.
- `Notification.mGroupKey` (agrupado de notificaciones, §R02.13) = `bb:<userId>:<paquete>:<grupo>`;
  separado del grupo de canales. `sortKey` igual.
- `NotificationChannelManager.APP_CHANNEL` (canal propio del host) no se toca.

## 6. PendingIntent (R02.15 a R02.21)

### 6.1 Puntos de entrada

`IActivityManagerProxy.GetIntentSender` (con `GetIntentSenderWithFeature` y
`GetIntentSenderWithSourceToken` como subclases) se ejecuta en el proceso invitado, donde
se crea el PI. Se extiende el `switch`:

| Tipo (`ActivityManagerCompat.INTENT_SENDER_*`) | Stub del host | Despacho en el box |
|---|---|---|
| `ACTIVITY` (ya existe) | `ProxyPendingActivity$P<pid>` | `startActivity` |
| `BROADCAST` (1) | `ProxyPendingReceiver` (nuevo) | `BActivityManager.scheduleBroadcastReceiver` |
| `SERVICE` (4) | `ProxyPendingReceiver` | `BActivityManager.startService(..., requireForeground=false)` |
| `FOREGROUND_SERVICE` (5) | `ProxyPendingReceiver` | `startService(..., requireForeground=true)` |

Se usa un receptor también para servicios: lanzar un servicio en primer plano directamente
desde un PI de servicio en segundo plano está restringido desde Android 12 (el envío de
un PI por el sistema concede una exención temporal de arranque al receptor, no a un
servicio; sin verificar). El receptor decide cuándo arrancar el servicio virtual.

### 6.2 Registro del intent original

`ProxyPendingRecord` pasa a guardar:
- `byte[] mTargetBytes`: el `Intent` original serializado con `Parcel.marshall()`.
- `int mUserId`, `String mPackage`, `int mKind`.

El `Intent` del stub lleva solo `byte[]` y tipos primitivos, que el host sabe leer sin
las clases del invitado (R02.19, D2). El `Intent` original solo se reconstruye con
`Parcel.unmarshall` en el proceso invitado de destino. Esto corrige también el caso de
actividad (`ProxyPendingActivity` hoy hace `getParcelableExtra("_B_|_P_target_")`).

Apertura al dispatch (punto a decidir en T02.2, sin verificar): `ProxyPendingReceiver`
vive en `:black` y no puede abrir `mTargetBytes`. Opciones:
- **A (propuesta):** `:black` envuelve los bytes y el relleno en un `Intent` con acción
  `bb.PENDING` y los entrega al proceso invitado por el mismo canal de
  `scheduleBroadcastReceiver`; el desenvolver y `target.fillIn(...)` ocurren en el invitado
  (nuevo método en `B app/BActivityThread` o en el despachador de broadcasts). Cero
  cambios de AIDL si el canal admite una acción especial.
- **B:** añadir `deliverPending(byte[] target, Intent fillIn, int kind, int userId)` a
  `IBActivityManagerService` (cambio de AIDL).
- **C (descartada):** hacer `fillIn` en `:black` sobre un `Bundle` con clases del invitado:
  `Bundle.putAll` y `putExtra` desempaquetan el `Bundle` y fallan por `ClassNotFoundException`.

### 6.3 Relleno (fill-in) y RemoteInput

El sistema entrega al stub el intent del stub fusionado con el de relleno. En el receptor:
1. Extraer del intent recibido: extras sin las claves `_B_|_P_*`, `ClipData`, `sourceBounds`.
2. Construir `fill = new Intent()` con solo eso. No se copian `action`, `data`, `component` ni
   `type`, porque son los del stub (`data` es el URI único, §6.4).
3. En el invitado: `target.fillIn(fill, Intent.FILL_IN_CLIP_DATA | Intent.FILL_IN_SOURCE_BOUNDS)`,
   más `target.putExtras(extras)`. `RemoteInput.getResultsFromIntent(target)` lee el
   `ClipData` y funciona.

### 6.4 Colisión de PendingIntent

El sistema identifica un PI por `filterEquals` (acción, data, type, component, categorías,
sin extras) + `requestCode` + paquete del creador. Todos los stubs serían iguales.
`shadow.setData(Uri.parse("bbpi://<userId>/<paquete>/<tipo>/<requestCode>/<hash>/<i>"))` donde
`hash = Integer.toHexString(target.filterHashCode())` e `i` es el índice en `Intent[]` de
`getActivities`. Con eso:
- `FLAG_NO_CREATE`/`CANCEL_CURRENT`/`UPDATE_CURRENT` y `PendingIntent.cancel()` mantienen su
  semántica, porque dos invitados que se solaparían ya no se solapan en el sistema.
- `UPDATE_CURRENT` actualiza los extras del stub, que contienen el `byte[]`.
`requestCode` se obtiene de `args` por tipo (índice a validar en Android 14 a 16, sin
verificar; se mantiene la búsqueda por `instanceof Intent[]` de `getIntentsIndex`).

### 6.5 Flags

Se conservan los `flags` originales del invitado. Si el invitado pide `FLAG_IMMUTABLE`
y lleva `RemoteInput`, el sistema ya lo rechaza para el original; no se reescribe. Si pide
`MUTABLE` o nada, el host con targetSdk 28 obtiene PI mutable por defecto, que es lo que
necesita el relleno. En Android 14+ un PI mutable con intent implícito está restringido; el
stub es explícito (`setComponent`), así que no aplica.

### 6.6 Seguridad (R02.20, R02.21)

- El stub siempre lleva `setComponent` explícito del host. El `Intent` original no se
  envía nunca con `sendBroadcast`/`startService` del `Context` del host.
- Si el paquete destino no está instalado en ese `userId` del box o los bytes no se
  pueden leer, se descarta con log (`Slog`). No hay "reintento contra el sistema".
- `ProxyPendingReceiver` y los servicios stub: `android:exported="false"`. El PI creado por
  el host se puede disparar por el sistema aunque el componente no esté exportado.
- Se añade `mPackage` y `mUserId` al registro y se verifica que `mPackage` esté instalado
  antes del despacho.
- Riesgo residual: un PI del invitado cuyo creador no pasó por `GetIntentSender` (llega
  por binder desde otra app). Se registra y se descarta si no tiene estos extras.

### 6.7 Manifiesto

`Bcore/src/main/AndroidManifest.xml` y `ProxyManifest` (`B proxy/ProxyManifest.java`): añadir
`ProxyPendingReceiver` en `:black` (un solo componente basta porque el despacho es
vía `:black`). No hace falta un receptor por proceso `pN` salvo que la opción A pida
entregar desde el stub. `ProxyManifest.getProxyPendingReceiver()` nuevo.

## 7. Permiso POST_NOTIFICATIONS (R02.22)

El host (`engine/app`, `applicationId com.qtekfun.androidinabox`) tiene targetSdk 28 y el
manifiesto de `Bcore` ya declara `POST_NOTIFICATIONS`. Con targetSdk < 33, Android 13+ muestra el
diálogo de forma automática la primera vez que la app crea un canal (sin verificar en
el OPPO). Diseño:
- `WelcomeActivity` (host) comprueba `NotificationManagerCompat.areNotificationsEnabled()` en
  arranque y tras volver de Ajustes; si falla, pantalla con botón a
  `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.
- En el invitado, hook de `checkSelfPermission` para `POST_NOTIFICATIONS` y de
  `requestPermissions`: la existencia del hook en `IPermissionManagerProxy` y su
  comportamiento actual están (sin verificar); T02.14 lo decide.

## 8. Reloj Huawei (R02.23, R02.24)

Huawei Health actúa como `NotificationListenerService` y filtra por **paquete emisor**
(host). No se puede elegir por app invitada (decisión 3 del veredicto: todo en bloque). El
texto que ve el reloj sale de título y texto; el `subText` suele no verse (sin verificar),
por eso existe R02.6. La respuesta rápida depende del reloj: el listener de Huawei debe
exponer la acción con `RemoteInput` y el reloj mostrarla (sin verificar); si existe, usa los
`PendingIntent` ya reescritos y no requiere código extra.

## 9. Nuevos casos de NotifProbe

Ver la tarea T02.1 en [tasks.md](tasks.md): `tag/cancel/cancelAll`, `Parcelable`
propio en extras y en intent, grupo con resumen, `setShortcutId`, `largeIcon` propio,
`checkSelfPermission(POST_NOTIFICATIONS)`, notificación con la app real instalada fuera
del box.

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Hidden API `setSmallIcon(Icon)` / `mSmallIcon` cambia en Android 17 | Acceso con `BRNotification` y prueba en CI de dispositivo; spec 05 |
| Bitmaps grandes superan 1 MB de binder | Límites de §3.4 y prueba con `MessagingStyle` de 10 mensajes |
| Alteraciones del orden de argumentos de `enqueueNotificationWithTag` en 14 a 17 | Buscar por tipo (`Notification`, `int`) en vez de índice fijo |
| `cancelAll` desde `:black` por prefijo pierde notificaciones si `getActiveNotifications` falla | Fallback: lista en memoria de `(tag,id)`; fallo visible en log |
| Opción A de §6.2 depende de cómo se entregan los broadcasts al invitado | T02.2 es un spike de 1 día con criterio de salida |
| La app real con el mismo paquete recibe acciones por error | R02.20 + prueba explícita con NotifProbe real fuera del box |
| El reloj no muestra el `subText` ni ofrece respuesta | R02.6; la respuesta queda `WHERE` |

## 11. Plan de pruebas

- Unitarios (JVM/Robolectric si encaja; si no, instrumentados): construcción de ids
  (`bb:` ida y vuelta con paquetes, tags y canales con `:` y `@`), sanitizado de extras,
  conversor de iconos.
- Instrumentados en dispositivo: reescritura de PI (colisión y flags).
- NotifProbe ampliado: pasos e, f, g del plan de pruebas; evidencia con
  `scripts/fase0/diag.sh`.
- Regresión: actividad desde notificación (`CONTENT_INTENT_OK`) sigue funcionando.
