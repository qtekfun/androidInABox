# Spec 06: Wear OS. Diseño

- Estado: Borrador
- Fecha: 2026-10-29
- Requisitos: [requirements.md](requirements.md)
- Rutas relativas a `engine/`; el código del motor está en
  `Bcore/src/main/java/top/niunaijun/blackbox/` (abreviado `<bb>/`).

## 1. Fuentes consultadas

- Capa de datos de Wearable (parte de Google Play services, es decir, del GMS):
  https://developer.android.com/training/wearables/data/overview
- Emparejamiento con `CompanionDeviceManager` (asociación a nombre de la app llamante,
  diálogo de selección del sistema, perfiles, presencia en Android 16):
  https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing
- Alta del reloj con la app Wear OS (exige Bluetooth, permisos de dispositivos cercanos
  y ubicación; los pasos varían por fabricante y por versión de Wear OS):
  https://support.google.com/wearos/answer/6056630
- Apps independientes o dependientes del móvil:
  https://developer.android.com/training/wearables/apps/standalone-apps
- Estado de microG: sin soporte de Wear OS (foro de usuarios, antiguo, sin verificar
  hoy): https://forum.fairphone.com/t/android-wear-without-google-possible/36427
- Foro de ayuda de Wear OS: la app antigua "Wear OS by Google" se restringe en
  versiones nuevas de Android para quien no la tenía instalada; los fabricantes dirigen
  a su propia app (agosto de 2026):
  https://support.google.com/wearos/thread/459584310/no-wearos-on-newer-android-phones

Conclusión de las fuentes: la capa de datos vive en GMS. Sin GMS no hay Wear OS en el
móvil; por eso la spec depende de la 03. Los datos no oficiales (foros) se marcan como
"sin verificar".

## 2. Estado actual del motor (lectura del código)

- **Bluetooth:** no hay proxy. `fake/service/` no contiene ninguno de Bluetooth,
  `CompanionDeviceManager` ni USB. Las llamadas del invitado salen al sistema con lo que
  el motor haya dejado en el contexto.
- **Identidad:** `fake/service/IAttributionSourceProxy.java` ya reescribe
  `AttributionSource` con el UID y el paquete del host (comentario: "UID mismatch
  prevention"). Es la base para Bluetooth, que desde Android 12 valida la
  `AttributionSource` contra el UID llamante (sin verificar en Android 16/17).
- **Manifiesto del host:** ya declara `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`,
  `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES` y las variantes
  `com.google.android.gms.permission.*`. No declara `REQUEST_COMPANION_PROFILE_WATCH`,
  `REQUEST_COMPANION_*` ni ningún servicio de escucha de notificaciones. Con
  `targetSdk` 28, `BLUETOOTH_CONNECT` y `BLUETOOTH_SCAN` se conceden de forma implícita
  si se tiene `BLUETOOTH` (permisos divididos; sin verificar en Android 16/17), pero el
  escaneo BLE sigue necesitando ubicación concedida.
- **Notificaciones:** `fake/service/INotificationManagerProxy.java` cubre `enqueue`,
  `cancel` y canales. `core/system/notification/BNotificationManagerService.java` guarda
  las notificaciones del box. No existe `registerListener` ni
  `getActiveNotifications`.
- **Procesos:** un UID real para todo (ver
  [arquitectura-motor.md](../../fase0/arquitectura-motor.md)). Los sockets y conexiones
  Bluetooth pertenecen por tanto al UID del host, lo que simplifica la propiedad pero no
  la comprobación de paquete.

## 3. Arquitectura propuesta

```
 Reloj Wear OS <==BLE/RFCOMM==> Bluetooth del sistema real
                                        ^
                                        | identidad del host
   +-------------- host (UID real) -----+----------------------+
   | HostNotificationListener (NLS real, opcional)             |
   | CdmBridge   BluetoothProxy   PermissionBridge (UI host)    |
   +------------------+----------------------------------------+
                      | binder propio
   +------------- :black (servidor del box) -------------------+
   | BNotificationListenerService  <- BNotificationManagerService|
   | mapa CDM: paquete invitado -> asociaciones                  |
   +------------------+----------------------------------------+
                      |
   +---- procesos :pN (invitados) ----------------------------+
   | GMS (Wearable)   App compañera   NLS invitado            |
   +----------------------------------------------------------+
```

### 3.1 BluetoothProxy (nuevo)

- Servicios a enganchar: `bluetooth_manager` (`IBluetoothManager`), y los binders que
  devuelve (`IBluetooth`, `IBluetoothGatt`, `IBluetoothSocketManager`), más los
  callbacks. Mecanismo `BinderInvocationStub` como el resto de
  `<bb>/fake/service/I*Proxy.java`, registrado en `fake/hook/HookManager.java`.
- Reescritura: nombre de paquete y `AttributionSource` por los del host en cada método
  con parámetros de identidad (misma lógica que `MethodParameterUtils.replaceAllAppPkg`
  y `IAttributionSourceProxy`). Los nombres exactos de métodos y la firma por versión se
  fijan en T06.2 (sin verificar para Android 16/17).
- Entrega de resultados: los callbacks de escaneo y GATT se entregan al proceso
  invitado; no se hace nada especial porque el binder del callback lo crea el propio
  invitado.
- RFCOMM/L2CAP: el `BluetoothSocket` es un descriptor de fichero recibido del sistema,
  no necesita redirección de IO (ver "Redirección de IO" en
  [arquitectura-motor.md](../../fase0/arquitectura-motor.md)).
- Permisos: `PermissionBridge` en el host. `checkSelfPermission` de los permisos de
  Bluetooth y ubicación devuelve la concesión real del host (R06.4, R06.5). La UI del
  host lanza la petición del sistema (el host tiene `targetSdk` 28, así que la petición
  en tiempo de ejecución para `BLUETOOTH_*` puede no mostrarse y habrá que
  comprobarlo, T06.3).

### 3.2 CompanionDeviceManager (nuevo)

- Servicio `companiondevice`. Proxy que reescribe el paquete a host en `associate`,
  `getAssociations`, `disassociate`, `hasNotificationAccess`,
  `requestNotificationAccess`, `startObservingDevicePresence`.
- La asociación queda **a nombre del host**. El diálogo del sistema mostrará el nombre
  del host, no el de la app compañera (limitación asumida; mitigable con una pantalla
  previa del box que explique qué se va a asociar).
- Mapa en `:black` paquete invitado → ids de asociación, para que
  `getMyAssociations` devuelva solo las de cada invitado y para decidir a qué
  `CompanionDeviceService` invitado se vincula cuando el sistema avise de presencia
  (R06.7). El sistema vincula servicios del **host**: el host declara un
  `CompanionDeviceService` genérico que reenvía el evento al box.
- Perfil `WATCH` (Android 12 o superior): concede permisos a la app asociada. El host
  necesita `REQUEST_COMPANION_PROFILE_WATCH` en el manifiesto. Si el perfil concede el
  acceso a notificaciones al host (sin verificar), el puente real (3.4) no exigiría ir a
  Ajustes.
- En Android 16 la presencia usa `startObservingDevicePresence(ObservingDevicePresenceRequest)`
  y `onDevicePresenceEvent`; el proxy debe cubrir ambas formas.

### 3.3 Servicio de escucha de notificaciones del box (nuevo)

- `BNotificationListenerService` en `:black`: sustituye, para el invitado, al servicio
  `INotificationManager` real en las llamadas de escucha (`registerListener`,
  `unregisterListener`, `getActiveNotifications`, `cancelNotification*`, `snoozeNotification`,
  `getCurrentRanking`, `requestInterruptionFilter`).
- Fuente de datos: `BNotificationManagerService` ya ve cada `enqueue` y `cancel`. Cada
  evento se convierte en un `StatusBarNotification` con el paquete e icono del
  invitado (depende de la corrección de nombre de app, spec 02) y se envía a los
  escuchas.
- Los servicios `NotificationListenerService` del invitado se arrancan **dentro del box**
  con el mecanismo normal de servicios (`ProxyService$Pn` + `AppServiceDispatcher`), y
  `onBind` devuelve el `INotificationListener` que `NotificationListenerService` espera.
  El motor le entrega el `INotificationListener` de su lado, sin pasar por el sistema.
- Estado de acceso (R06.10): se guarda en `:black` (lista de componentes autorizados por
  usuario virtual). Se responde a `Settings.Secure.enabled_notification_listeners`
  mediante el proxy de ajustes existente (`ISettingsProviderProxy`), a
  `NotificationManager.isNotificationListenerAccessGranted` y a la apertura de
  `ACTION_NOTIFICATION_LISTENER_SETTINGS`, que se redirige a una actividad del host.

### 3.4 Puente del sistema real (nuevo, opcional)

- Hace falta porque en un móvil sin GMS la mayoría de lo que el usuario quiere ver en
  el reloj (WeChat, SMS, llamadas) está en el sistema real y no en el box.
- El host declara `HostNotificationListener` (servicio real con
  `BIND_NOTIFICATION_LISTENER_SERVICE`). El usuario le concede "Acceso a notificaciones"
  en los ajustes del sistema (en ROM chinas ese ajuste existe, pero puede requerir un
  paso extra de autoarranque; sin verificar en los dos móviles).
- Cada `StatusBarNotification` real elegida se envía a `:black` y se entrega a los
  escuchas invitados autorizados. El `PendingIntent` real viaja dentro del
  `StatusBarNotification` parcelado (la referencia es del sistema y es válida para el
  mismo UID), por lo que disparar las acciones del reloj funciona sin reimplementación
  (R06.13).
- Filtros: lista blanca de apps de origen; se excluyen las notificaciones que el propio
  motor reenvió desde el box al sistema real (marca en extras) para evitar duplicados;
  nada se guarda en disco ni se registra (R06.12).
- Coste: requiere que el proceso del host esté vivo (spec 04).

### 3.5 Persistencia

Una conexión Wear OS sobrevive solo si el proceso de GMS (Wearable) y la sesión
Bluetooth siguen activos. Se apoya en spec 04 (servicio en primer plano real y proceso
persistente para GMS). Para el sistema, `CompanionDeviceManager` con perfil `WATCH`
permite arrancar la app desde segundo plano (`REQUEST_COMPANION_RUN_IN_BACKGROUND` y
afines, según la documentación citada).

## 4. Flujos

**Emparejamiento**
1. El usuario abre la app compañera en el box. Pide permisos de Bluetooth y ubicación
   (`PermissionBridge`).
2. La app llama a `CompanionDeviceManager.associate`. El proxy lo pasa al sistema real
   con el paquete del host. El sistema muestra el selector.
3. El usuario elige el reloj. El sistema crea la asociación a nombre del host; el proxy
   anota el mapa invitado → asociación.
4. La app hace `createBond`/GATT. El sistema muestra el diálogo de PIN. GMS (Wearable)
   abre los sockets.
5. El alta pide la cuenta de Google: la sirve el gestor de cuentas del box (spec 03).

**Notificación del box al reloj**
1. WhatsApp del box llama a `enqueueNotificationWithTag`.
2. `BNotificationManagerService` la registra y la entrega a `BNotificationListenerService`.
3. Este llama a `onNotificationPosted` del servicio de escucha de la app compañera, que
   la envía por la capa de datos al reloj.

**Respuesta desde el reloj**
1. El reloj envía el texto a la capa de datos.
2. La compañera rellena el `RemoteInput` y dispara el `PendingIntent` de la acción.
3. Si es del box: lo resuelve el motor (spec 02). Si es del sistema real: lo ejecuta
   el sistema.

## 5. Bloqueos duros posibles (lo que hay que descartar primero)

| # | Bloqueo | Por qué importa | Cómo se mide | Salida |
|---|---|---|---|---|
| B1 | GMS (Wearable) no arranca estable en el box | Sin él no hay capa de datos | T06.6 con spec 03 | Abandonar |
| B2 | El sistema rechaza Bluetooth por identidad | Sin Bluetooth no hay reloj | T06.2 | Pivotar a proxy más profundo o abandonar |
| B3 | La app compañera exige ser app de sistema o privilegiada | No se puede imitar | T06.1 (manifiesto) y T06.6 | Probar otra compañera del fabricante, o abandonar |
| B4 | La compañera valida firma/instalador y el UID compartido la confunde | GMS comprueba llamantes por UID | T06.6, relacionado con spec 03 | Abandonar o parche específico |
| B5 | El acceso a notificaciones del host no se puede conceder en la ROM | Sin puente real | T06.9 en los dos móviles | Solo notificaciones del box |
| B6 | Diálogos del sistema (selector, PIN) no se muestran bien sobre la pila virtual | Sin emparejamiento | T06.8 | Lanzar el flujo desde una actividad real del host |
| B7 | La ROM mata procesos y se pierde el reloj | Conexión intermitente | T06.12 | Depende de spec 04 |
| B8 | El reloj o su app solo funcionan con GMS y servicios propios del fabricante (p. ej. Samsung) | Limita modelos | T06.1 | Documentar y limitar modelos |

## 6. Fabricantes (a completar con el modelo real)

| Familia | App compañera | Notas | Estado |
|---|---|---|---|
| Wear OS 2 y similares | Wear OS by Google (`com.google.android.wearable.app`) | Restringida en Android nuevo para quien no la tenía (foro de ayuda); el APK lo aporta el usuario | sin verificar |
| Pixel Watch | Pixel Watch (`com.google.android.apps.wear.companion`) | Usa Fast Pair y GMS; requiere GMS completo | sin verificar |
| Samsung Galaxy Watch | Galaxy Wearable y complementos | Funciones completas pensadas para móviles Samsung | sin verificar |
| Mobvoi, Fossil, Montblanc, etc. | App del fabricante más la de Google | Varía por modelo | sin verificar |
| Xiaomi, OPPO, OnePlus (Wear OS) | App propia del fabricante | Podría funcionar en el sistema real sin box si no exige GMS | sin verificar |

El último caso importa: si el reloj es de un fabricante cuya app ya funciona en la ROM
china, el box no es necesario para ese reloj (informar al propietario).

## 7. Alternativas descartadas o aplazadas

1. **App compañera en el sistema real, solo con puente de notificaciones.** Sin GMS no
   hay capa de datos: descartada.
2. **Reimplementar capa de datos y compañera.** Sale del alcance; microG tampoco lo
   cubre (fuente de foro, sin verificar).
3. **Gadgetbridge u otra app abierta con su propio protocolo.** No habla con Wear OS;
   solo sirve para otras familias de relojes. Fuera.
4. **Proxy de Bluetooth mínimo frente a completo.** Se empieza mínimo (adaptador, BLE,
   GATT, RFCOMM) y se amplía solo si T06.6 falla por una llamada concreta.
5. **Solo notificaciones del box, sin puente real.** Es el plan de menor riesgo. El
   puente real queda como fase posterior (T06.9), pero es probablemente lo que más
   valor da en un móvil chino.

## 8. Riesgos

- **Alta:** B1 y B4 (dependen de spec 03).
- **Media:** el proxy de Bluetooth cubre menos métodos de los que la compañera usa y los
  fallos aparecen de uno en uno.
- **Media:** privacidad del puente real (contenido de otros apps pasando por el box).
  Mitigación: R06.12.
- **Media:** cambio de API en Android 17 (AIDL de Bluetooth y CDM cambian de firma
  entre versiones). El proxy debe resolver los métodos por nombre, no por posición.
- **Baja:** el diálogo de asociación muestra el nombre del host.

## 9. Plan de pruebas

| Prueba | Requisitos | Dónde |
|---|---|---|
| WearProbe: BLE, GATT, RFCOMM, CDM, escucha | R06.3 a R06.7, R06.9, R06.10, R06.19 | Android 16 y 17, periférico BLE cualquiera |
| Test instrumentado de permisos | R06.4, R06.5 | Ambos móviles |
| NotifProbe → escucha → reloj | R06.9, R06.13, R06.14 | Reloj real |
| 60 minutos de inactividad | R06.15 | Ambos móviles con reloj |
| Revisión de logs | R06.12, R06.19 | `scripts/fase0/diag.sh` |

Criterios de salida del spike (se rellenan en `spike-informe.md`):

| Criterio | Continuar | Pivotar | Abandonar |
|---|---|---|---|
| Bluetooth desde el box (T06.2, T06.3) | BLE y RFCOMM funcionan | Funciona con proxy ampliado | Rechazo del sistema sin salida |
| CDM (T06.4) | Asociación a nombre del host | Solo con flujo en actividad del host | No asocia |
| Escucha de notificaciones (T06.5) | Recibe las del box | Solo con servicio en el host | No entrega |
| Compañera en el box (T06.6) | Arranca y escanea | Otra compañera arranca | Ninguna arranca |
| Reloj real (T06.8) | Empareja | Empareja con pasos manuales | No empareja |
