# Spec 06: Wear OS. Tareas

- Estado: Borrador
- Fecha: 2026-10-29
- Requisitos: [requirements.md](requirements.md). Diseño: [design.md](design.md)

Cada tarea cabe en un día o menos. Las pruebas en dispositivo las ejecuta el propietario.
Rutas relativas a `engine/` salvo `tools/` y `docs/` (raíz del repo).

## Fase A. Spike (estimado: 5,5 días de trabajo, más la espera del reloj)

Criterio de salida: ver la tabla del [diseño](design.md), sección 9. Si alguna fila
llega a "Abandonar", se detiene el spike, se escribe el informe y se cierra la spec.

### T06.1 Inventario estático de la app compañera
- Cubre: R06.2, R06.17.
- Qué: con la app Wear OS (o la del fabricante) que aporte el usuario, listar con
  `aapt`/`apkanalyzer`: permisos, servicios (`NotificationListenerService`,
  `CompanionDeviceService`, `WearableListenerService`), si es privilegiada o exige firma
  del sistema, y qué binders de sistema toca. Nada de APKs en el repo.
- Verificación: tabla en `docs/specs/06-wear-os/spike-informe.md` (sección inventario).
- Estimación: 0,5 día.

### T06.2 Proxy mínimo de Bluetooth y WearProbe
- Cubre: R06.3, R06.19.
- Qué: crear `tools/wearprobe` (patrón de `tools/notifprobe`) y el proxy
  `fake/service/IBluetoothManagerProxy.java` (adaptador, escaneo BLE, GATT cliente,
  RFCOMM) con identidad del host, registrado en `fake/hook/HookManager.java`.
- Verificación: WearProbe en el box lista un periférico BLE cualquiera y conecta por GATT
  en Android 16. Registro de fallos sin MAC completas.
- Estimación: 1 día.

### T06.3 Permisos reales
- Cubre: R06.4, R06.5.
- Qué: `PermissionBridge` en el host; `checkSelfPermission` y petición para
  `BLUETOOTH_*`, `NEARBY_WIFI_DEVICES` y ubicación según la concesión real; comprobar si
  el `targetSdk` 28 del host muestra o no el diálogo.
- Verificación: test instrumentado (revocar/conceder) y WearProbe antes y después.
- Estimación: 0,5 día.

### T06.4 Proxy de CompanionDeviceManager
- Cubre: R06.6, R06.7.
- Qué: `fake/service/ICompanionDeviceManagerProxy.java`; manifiesto del host con
  `REQUEST_COMPANION_PROFILE_WATCH` y `CompanionDeviceService` genérico; mapa
  invitado → asociaciones en `:black`.
- Verificación: WearProbe asocia el periférico; `dumpsys companiondevice` lo muestra bajo
  el host; `onDeviceAppeared` llega al servicio invitado.
- Estimación: 1 día.

### T06.5 Servicio de escucha del box (prototipo)
- Cubre: R06.9 (parte), R06.10.
- Qué: `BNotificationListenerService` en `core/system/notification/` con conexión,
  `onNotificationPosted/Removed` y `getActiveNotifications`; respuesta de
  `isNotificationListenerAccessGranted` y `enabled_notification_listeners`; pantalla de
  concesión en el host. Se apoya en la spec 02 para nombre de app e icono.
- Verificación: WearProbe (servicio de escucha) recibe las notificaciones de NotifProbe.
- Estimación: 1 día.

### T06.6 App compañera dentro del box con GMS
- Cubre: R06.2, R06.15 (arranque).
- Qué: con GMS de la spec 03 (si no está, marcar pendiente), instalar la compañera en
  el box y comprobar: arranca, pide permisos, escanea, no pide privilegios que falten.
  Capturar `logcat` filtrado con `scripts/fase0/diag.sh`.
- Verificación: informe con lista de llamadas fallidas por permiso o identidad.
- Estimación: 1 día.

### T06.7 Informe y decisión
- Cubre: R06.1, R06.2, R06.17.
- Qué: rellenar la tabla de criterios de salida; decidir continuar, pivotar o abandonar;
  ADR si cambia el alcance.
- Verificación: `spike-informe.md` revisado por el propietario.
- Estimación: 0,5 día.

## Fase B. Funcionalidad (solo si T06.7 = continuar). Estimado: 10,5 días + depuración

### T06.8 Emparejamiento completo con el reloj
- Cubre: R06.8, R06.18 (parte).
- Qué: flujo completo con el reloj real: selector, PIN, GATT, alta; arreglar diálogos
  sobre la pila virtual; si hace falta, lanzar el flujo desde actividad del host.
- Verificación: el reloj aparece emparejado en la compañera, en Android 16.
- Estimación: 1 día.

### T06.9 Puente del sistema real: servicio y transporte
- Cubre: R06.11.
- Qué: `HostNotificationListener` en el manifiesto del host; canal hacia `:black`;
  entrega a los escuchas invitados.
- Verificación: notificación de una app del sistema real visible en el servicio de
  escucha de WearProbe.
- Estimación: 1 día.

### T06.10 Puente real: filtros, privacidad e indicador
- Cubre: R06.12.
- Qué: lista blanca de apps, desactivado por defecto, sin persistencia ni logs de
  contenido, indicador en la UI del host, exclusión de duplicados del propio motor.
- Verificación: revisión de código y de `logcat` con una notificación de prueba.
- Estimación: 1 día.

### T06.11 Acciones, respuesta y descarte
- Cubre: R06.13, R06.14.
- Qué: `cancelNotification`, disparo de `PendingIntent` con `RemoteInput`, sincronización
  de descarte sin bucles; para notificaciones del box depende de spec 02.
- Verificación: respuesta desde el reloj llega a WhatsApp del box; descarte en ambos
  sentidos.
- Estimación: 1 día.

### T06.12 Estabilidad de la capa de datos
- Cubre: R06.15.
- Qué: con spec 04, mantener GMS (Wearable) vivo; medir pérdida de conexión y latencia.
- Verificación: 60 minutos de inactividad sin pérdida y mensajes en menos de 5 s.
- Estimación: 1 día.

### T06.13 Cuenta de Google para el alta
- Cubre: R06.16.
- Qué: comprobar el alta con la cuenta del gestor de cuentas del box; corregir lo que falte.
- Verificación: el reloj termina el alta con la cuenta.
- Estimación: 0,5 día.

### T06.14 Escucha de notificaciones completa
- Cubre: R06.9.
- Qué: ranking, `snoozeNotification`, filtros de interrupción, varios perfiles virtuales.
- Verificación: tests unitarios del servicio y WearProbe.
- Estimación: 1 día.

### T06.15 Bluetooth ampliado
- Cubre: R06.3.
- Qué: publicidad BLE, servidor GATT, L2CAP, perfiles que la compañera use según T06.6.
- Verificación: WearProbe con cada API.
- Estimación: 1 día.

### T06.16 Android 17 y tabla de fabricantes
- Cubre: R06.17, R06.18.
- Qué: repetir en Android 17 los pasos de T06.8 a T06.12; completar la tabla de
  fabricantes con el modelo recibido.
- Verificación: plan de pruebas en ambos móviles; tabla actualizada.
- Estimación: 1 día.

## Resumen de esfuerzo

Spike: 5,5 días. Funcionalidad: 10,5 días. Depuración y márgenes: 10 a 20 días (un proxy
de Bluetooth rara vez cubre todo a la primera). Total: 26 a 36 días, sin garantía y
condicionado a las specs 03 y 04. La guía de usuario se escribe al aprobar la spec.
