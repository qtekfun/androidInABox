# Spec 07: Android Auto. Notas de implementación (cimientos del motor)

- Estado: Borrador
- Fecha: 2026-10-29
- Rama del motor: `feat/07-android-auto` (commits T07.1 a T07.3 de esta tanda)
- Rutas relativas a `engine/Bcore/src/main/`

Esta tanda deja el motor preparado para que Android Auto (`com.google.android.projection.gearhead`,
instalado dentro del box desde Play o por fichero) pueda recibir un accesorio USB y proyectar.
No hay verificación en dispositivo todavía.

## Qué se hizo

### T07.1 Accesorio USB (cable)
- `java/top/niunaijun/blackbox/core/system/usb/UsbAccessoryActivity.java`: actividad translúcida del host
  con el filtro `USB_ACCESSORY_ATTACHED`. Busca en cada perfil del box la primera actividad que declare ese
  filtro (`BPackageManager.queryIntentActivities`) y le reenvía el intent completo, con el extra
  `UsbAccessory`, mediante `BlackBoxCore.startActivity` (pila de actividades del box).
- `res/xml/usb_accessory_filter.xml` y entrada en `AndroidManifest.xml`. El filtro lista
  fabricante `Android` / modelo `Android Auto` y `Google, Inc.` / `Android Auto` (sin verificar: no se ha
  inspeccionado el manifiesto de gearhead; el protocolo AOA de Android Auto usa "Android" / "Android Auto").
  Si el filtro no encaja, el sistema no ofrece el host al conectar la unidad principal.
- Se sustituyó el falso `uses-permission android.hardware.usb.host` por `uses-feature` (accessory y host,
  `required=false`).
- `fake/service/IUsbManagerProxy.java` (servicio `usb`, registrado en `HookManager`): reescribe el nombre de
  paquete en `requestAccessoryPermission` y `requestDevicePermission`; `hasAccessoryPermission` y
  `openAccessory` pasan al servicio real (el permiso es por UID y el UID es el del host). Reflexión en
  `black/android/hardware/usb/IUsbManager.java`.

### T07.2 Bluetooth (solo análisis, sin código)
- `utils/compat/ContextCompat.java` ya reescribe el `AttributionSource` del contexto del invitado con
  paquete y UID del host (`AttributionSourceUtils`, `IAttributionSourceProxy`). En Android 12 o posterior
  `BluetoothAdapter`/`BluetoothManager` toman la identidad de ahí, por lo que el descubrimiento y los
  sockets RFCOMM de Android Auto no deberían exigir un proxy nuevo (sin verificar).
- Un proxy de `bluetooth_manager` solo haría falta si en dispositivo aparece `SecurityException` por
  paquete/UID. Ese proxy es el de la spec 06 (T06.2); no se duplica aquí.
- Permisos: el host tiene `targetSdk` 28 y declara los `BLUETOOTH_*`; la concesión en Android 12+ y la
  ubicación para el escaneo siguen sin verificar.

### T07.3 Bind entre apps del box (CarAppService, notificaciones)
- Lectura del código: `IActivityManagerProxy.BindService` envía la petición a `ActiveServices.bindService`,
  que resuelve el servicio con `BPackageManagerService.resolveService` en el perfil del llamador y no filtra
  por paquete llamante. Por tanto un invitado puede enlazar con un servicio de otro paquete del mismo perfil.
- Huecos conocidos: (1) si el servicio no está en el mismo perfil, `bindService` devuelve el intent y el
  sistema real intenta el enlace y falla sin error claro; (2) el servicio de escucha de notificaciones (que
  Android Auto necesita para `MessagingStyle`) no existe todavía: depende de spec 06 R06.9; (3) las
  comprobaciones de firma de la API de coche (`CarAppService` valida el paquete del host por firma de
  `PackageManager`) se ven contra el PM del box y no se han probado.

## Cómo probar con el DHU (propietario, vehículo parado)
1. Instalar la APK del host y Android Auto dentro del box (Play Store del box o fichero).
2. En la app Android Auto del box: tocar la versión 10 veces, menú de desarrollador, "Start head unit server".
3. En el PC: `adb forward tcp:5277 tcp:5277`, luego `~/Android/Sdk/extras/google/auto/desktop-head-unit`.
4. Comprobar en `logcat` (etiquetas `UsbAccessoryActivity`, `IUsbManagerProxy`) y registrar cualquier
   excepción de permiso o de API del sistema (tabla de T07.3 del spike).
5. Cable (T07.4): segundo dispositivo Android como unidad principal por AOA; al conectar, el sistema debe
   ofrecer el host; esperar `accesorio USB reenviado a ...` en el log y `openAccessory -> descriptor abierto`.

## Riesgos abiertos
- El filtro de accesorio es una suposición; si gearhead usa otro fabricante/modelo, no llega el evento.
- Android Auto exige normalmente ser app de sistema y la instalación procedente de Play (verificaciones de
  integridad); puede negarse a ejecutarse (R07.17).
- `requestAccessoryPermission` lleva un `PendingIntent` del invitado; su entrega al receptor del box no está
  verificada. Si el sistema ya concedió el permiso al host al elegirlo en el diálogo, no se necesita.
- Gearhead puede abrir el accesorio desde otro proceso del box en el momento del evento; el UID es el mismo,
  pero la actividad destino debe poder arrancar en segundo plano.
- Inalámbrico: RFCOMM, unión a Wi-Fi de la unidad y llamadas dependen de spec 06 y de privilegios que el box no tiene.
- Sin compilar contra un APK real de gearhead; todo marcado como "sin verificar" hasta la prueba en dispositivo.

## Prueba en dispositivo (OPPO, Android 16, 2026-10-29)

- Android Auto 18.1 se clona del sistema al box (perfil 1, 4 splits) y **arranca**: proceso
  `com.google.android.projection.gearhead:projection` y actividad
  `com.google.android.gearhead.vanagon.VnLaunchPadActivity`, sin crash.
- El simulador DHU está instalado en el equipo de desarrollo (`extras;google;auto`).
- **Siguiente:**
  - abrir los ajustes de desarrollador de Android Auto dentro del box para activar
    "Start head unit server". Hace falta que la UI del box pueda lanzar actividades
    no principales, o un acceso directo de depuración;
  - `adb forward tcp:5277 tcp:5277` y lanzar el DHU.
- Depende de que GMS tenga la sesión iniciada (Android Auto la exige para la
  configuración inicial).
