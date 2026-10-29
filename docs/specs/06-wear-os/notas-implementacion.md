# Spec 06: Wear OS. Notas de implementación (trabajo sin dispositivo)

- Rama: `feat/06-wear-os` (sin empujar). Rutas relativas a `engine/`; `<bb>/` es
  `Bcore/src/main/java/top/niunaijun/blackbox/`.
- Alcance: tareas que se pueden hacer sin el reloj. Nada de esto se ha ejecutado en un
  móvil; la compilación y los tests JVM pasan, el resto está marcado "sin verificar".

## 1. Qué se hizo

### 1.1 Escucha de notificaciones para invitados (T06.5 y parte de T06.9)

- `<bb>/core/system/notification/BNotificationListenerService.java`: en `:black`. Cuando el
  usuario autoriza un `NotificationListenerService` invitado, lo enlaza dentro del box (stub
  `ProxyService$Pn`, igual que cualquier servicio invitado) y le entrega
  `onListenerConnected`, `onNotificationPosted` y `onNotificationRemoved`.
  - `ListenerChannel` habla con el `INotificationListener` del invitado por reflexión
    (interfaz oculta; los argumentos se construyen por tipo porque la firma cambia por versión).
    El holder del `StatusBarNotification` es un `Binder` propio que responde a `get()`.
  - `GuestStatusBarNotification` construye el `StatusBarNotification` con paquete, id y tag
    del invitado (no los reales con prefijo `bb:`).
  - Fuente de datos: `BNotificationManagerService` avisa en `enqueue`, `cancel`, `cancelAll`
    y al borrar un paquete. La copia que se entrega se toma antes de reescribir canal y grupo.
  - Las activas viven en memoria; al arrancar `:black` se reenlazan los oyentes autorizados.
- Estado de acceso: `ListenerAccessStore` (fichero `bb_nls_access` en el directorio de datos
  del host, por usuario virtual). Solo el host puede conceder (`setListenerAccessGranted`
  ignora a los procesos invitados).
- Lado invitado (`INotificationManagerProxy`): `getActiveNotificationsFromListener`,
  `cancelNotificationFromListener`, `registerListener/unregisterListener` (no-op),
  `isNotificationListenerAccessGranted[ForUser]`, `getEnabledNotificationListener[Package]s`.
  Estos métodos no reescriben el paquete, porque llevan el de otra app.
  `Settings.Secure.enabled_notification_listeners` sale de `ISettingsProviderProxy`.
- Puente opcional del sistema real (R06.12): `proxy/HostNotificationListener` (servicio real del
  host con `BIND_NOTIFICATION_LISTENER_SERVICE`, declarado en el manifiesto de `Bcore`) y
  `RealBridgeSettings` (apagado por defecto + lista blanca de paquetes; nunca reenvía las del
  propio host, que incluyen las que el motor publicó desde el box). No guarda ni registra
  contenido. El reenvío entra por `onRealNotificationPosted/Removed` (solo desde el host).
- Pendiente (UI): no hay pantalla en la app para conceder un oyente ni para activar el puente.
  La API existe (`BNotificationManager.setListenerAccessGranted`, `RealBridgeSettings`);
  la pantalla se hace con T06.9/T06.10.

### 1.2 CompanionDeviceManager (T06.4, parte de motor)

- `fake/service/ICompanionDeviceManagerProxy`: servicio `companiondevice`; el paquete llamante
  pasa a ser el del host, así que el sistema muestra el diálogo de asociación del host.
  - `associate`: anota los ids que ya existían. `getAssociations`: filtra a las del invitado.
    `disassociate`: solo las propias. `hasNotificationAccess`: responde el box.
- Mapa invitado -> asociaciones en `:black`: `BCompanionDeviceService` +
  `CompanionAssociationStore`. El callback de `associate` es un binder del invitado que no
  se puede interponer, por eso la atribución es por diferencia de conjuntos (ids nuevos sin
  dueño pasan al invitado pendiente más reciente; caduca a los 5 minutos). Con dos invitados
  asociando a la vez puede equivocarse.
- Registrado solo si el servicio existe (Android 8 o superior).
- No hecho: `REQUEST_COMPANION_PROFILE_WATCH` en el manifiesto ni el `CompanionDeviceService`
  genérico que reenvía la presencia al invitado (R06.7).

### 1.3 Permisos Bluetooth (T06.3)

- Manifiesto de `Bcore` (confirmado): `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`,
  `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES`, ubicación fina y gruesa.
- `IPackageManagerProxy`: `checkPermission/checkSelfPermission` de los cuatro permisos de
  dispositivos cercanos devuelven la concesión real del host (`NearbyPermissions`).
- Identidad: `ContextCompat.fix` ya reescribía paquete, `opPackageName` y `AttributionSource`
  en aplicación y actividades; ahora se aplica también a cada servicio invitado
  (`AppServiceDispatcher`), que es donde corre la compañera y GMS Wearable.
- Petición en tiempo de ejecución: no se añadió código. El `requestPermissions` del invitado
  lanza el diálogo del sistema para el host. Con `targetSdk` 28, `BLUETOOTH_SCAN/CONNECT/
  ADVERTISE` se conceden por permiso dividido (desde `BLUETOOTH`/`BLUETOOTH_ADMIN`) y no
  muestran diálogo; el que sí hace falta es ubicación (escaneo BLE). Sin verificar en Android 16.

## 2. Cómo probarlo cuando llegue el reloj

Tests JVM (hechos): `ListenerSettingsTest`, `NearbyPermissionsTest` y los de compilación.

En el móvil (Android 16, una prueba por línea; logs con `scripts/fase0/diag.sh`):

1. Etiquetas de log: `BNotifListener`, `ListenerChannel`, `GuestSBN`,
   `ICompanionDeviceManagerProxy`.
2. Oyente del box: instalar una app de prueba con un `NotificationListenerService` en el box,
   conceder acceso con `BNotificationManager.setListenerAccessGranted` (por ahora desde una
   pantalla de depuración) y publicar desde otra app del box. Esperado: `onListenerConnected`
   y `onNotificationPosted` con el paquete del publicador. Si `ListenerChannel` registra
   "Sin NotificationRankingUpdate vacío", el oyente falla al aplicarlo: revisar la firma.
3. Puente real: conceder "Acceso a notificaciones" al host; activar `RealBridgeSettings` y
   añadir un paquete; llegar una notificación de ese paquete. Comprobar que sin el paquete en la
   lista no llega nada.
4. CDM: con la compañera, `associate` debe mostrar el selector del sistema a nombre del host;
   `dumpsys companiondevice` lo muestra bajo el paquete del host; `getAssociations` desde la
   compañera devuelve solo la suya.
5. Bluetooth: WearProbe con BLE, GATT y RFCOMM; comprobar `checkSelfPermission` de los cuatro
   permisos antes y después de conceder ubicación.
6. Reloj: emparejar, ver una notificación del box en el reloj y responder (spec 02 para
   `RemoteInput`).

## 3. Riesgos abiertos

- **Reflexión sobre `INotificationListener`:** firmas ocultas (`onNotificationRemoved` ha
  cambiado de 2 a 4 parámetros). Si el servicio invitado lanza una excepción en su hilo binder,
  solo se ve en su logcat.
- **`NotificationRankingUpdate` vacío:** el constructor `(Ranking[])` es de Android 11 o
  posterior. En versiones antiguas se pasa `null` y el oyente fallará.
- **Atribución de CDM por diferencia de conjuntos:** frágil con asociaciones simultáneas.
- **`ContextCompat.fix` en servicios:** cambia `mBasePackageName` del contexto del servicio.
  Podría afectar a una compañera que lea recursos o `getPackageName()` desde el servicio;
  sin verificar.
- **`ISettingsProviderProxy`:** asume que el primer argumento es la clave; el ajuste real puede
  llegar por `ContentResolver.call` y no pasar por aquí.
- **Reenvío real:** requiere proceso del host vivo (spec 04) y el ajuste de acceso a notificaciones
  en la ROM china (B5); las acciones del reloj sobre notificaciones reales dependen de que el
  `PendingIntent` siga siendo válido tras el AIDL.
- **Hueco:** no hay UI de concesión/puente ni `CompanionDeviceService` del host.
- **Colisión con la rama de Android Auto:** los cambios de Bluetooth aquí son solo permisos e
  identidad; no hay proxy de Bluetooth. Archivos compartidos probables: `HookManager`,
  `ServiceManager`, `BlackBoxSystem`, `AndroidManifest.xml` de `Bcore`, `IPackageManagerProxy`.
